package com.backroomsmod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A chair that can stand, lie on its back, or sit half-sunk into the carpet. */
public class ChairBlock extends HorizontalDirectionalBlock {
    public enum Pose implements StringRepresentable {
        UPRIGHT("upright"),
        FALLEN("fallen"),
        SUNK("sunk");

        private final String name;

        Pose(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final EnumProperty<Pose> POSE = EnumProperty.create("pose", Pose.class);

    private static final VoxelShape UPRIGHT_SHAPE = Block.box(2, 0, 2, 14, 16, 14);
    private static final VoxelShape FALLEN_SHAPE = Block.box(0, 0, 0, 16, 12, 16);
    private static final VoxelShape SUNK_SHAPE = Block.box(1, 0, 1, 15, 10, 15);

    public ChairBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POSE, Pose.UPRIGHT));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POSE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
            .setValue(FACING, context.getHorizontalDirection().getOpposite())
            .setValue(POSE, Pose.UPRIGHT);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        switch (state.getValue(POSE)) {
            case FALLEN: return FALLEN_SHAPE;
            case SUNK: return SUNK_SHAPE;
            default: return UPRIGHT_SHAPE;
        }
    }
}
