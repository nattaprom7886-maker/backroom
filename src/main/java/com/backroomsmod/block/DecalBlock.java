package com.backroomsmod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Thin decoration with no collision: a wall outlet, an empty mount mark on a wall, or a carpet dent
 * on the floor. FACING means:
 *   OUTLET / MOUNT_MARK: the side the decoration faces (away from the wall it is stuck on).
 *   DENT: which corner of the furniture footprint it is (north = NW, east = NE, south = SE, west = SW).
 */
public class DecalBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public enum Kind { OUTLET, MOUNT_MARK, DENT }

    private final Kind kind;

    public DecalBlock(Kind kind) {
        super(BlockBehaviour.Properties.of()
            .noCollission()
            .noOcclusion()
            .replaceable()
            .instabreak()
            .sound(SoundType.STONE)
            .pushReaction(PushReaction.DESTROY));
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
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
        Direction facing = state.getValue(FACING);
        switch (kind) {
            case OUTLET: return wallBox(facing, 5, 4, 11, 12, 1);
            case MOUNT_MARK: return wallBox(facing, 3, 4, 13, 12, 1);
            default: return Shapes.empty(); // dents never get in the way of the carpet underneath
        }
    }

    /** A box stuck on the wall behind the block; given for a north-facing block and turned to fit. */
    static VoxelShape wallBox(Direction facing, double x1, double y1, double x2, double y2, double depth) {
        switch (facing) {
            case SOUTH: return Block.box(16 - x2, y1, 0, 16 - x1, y2, depth);
            case EAST: return Block.box(0, y1, x1, depth, y2, x2);
            case WEST: return Block.box(16 - depth, y1, 16 - x2, 16, y2, 16 - x1);
            default: return Block.box(x1, y1, 16 - depth, x2, y2, 16);
        }
    }
}
