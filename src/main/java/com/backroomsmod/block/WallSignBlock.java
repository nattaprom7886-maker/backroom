package com.backroomsmod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A stop sign stuck into a wall. TILT_* lie crooked against the wall; HALF_* are driven into the
 * wall at an angle so only half of the sign sticks out. MIRRORED flips the lettering.
 * FACING is the side the sign sticks out toward (away from the wall).
 */
public class WallSignBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty MIRRORED = BooleanProperty.create("mirrored");
    public static final EnumProperty<Variant> VARIANT = EnumProperty.create("variant", Variant.class);

    public enum Variant implements StringRepresentable {
        TILT_LEFT("tilt_left"), TILT_RIGHT("tilt_right"), HALF_LEFT("half_left"), HALF_RIGHT("half_right");

        private final String name;

        Variant(String name) { this.name = name; }

        @Override
        public String getSerializedName() { return name; }
    }

    public WallSignBlock() {
        super(BlockBehaviour.Properties.of()
            .noCollission()
            .noOcclusion()
            .replaceable()
            .instabreak()
            .sound(SoundType.METAL)
            .pushReaction(PushReaction.DESTROY));
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(MIRRORED, Boolean.FALSE)
            .setValue(VARIANT, Variant.TILT_LEFT));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MIRRORED, VARIANT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction face = ctx.getClickedFace();
        Direction f = face.getAxis().isHorizontal() ? face : ctx.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, f);
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return DecalBlock.wallBox(state.getValue(FACING), 2, 2, 14, 14, 8);
    }
}
