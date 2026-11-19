package com.backroomsmod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A pile of stacked dining chairs as ONE block (instead of one chair block per level).
 * The model is drawn taller than a single block: PART 0 holds the lower three chairs, and PART 1
 * (the block above) holds the fourth chair of a tall pile.
 *   STYLE 0: twisted pile, every chair turned a different way
 *   STYLE 1: twisted pile with the top two chairs tipped over
 *   STYLE 2: the whole pile leans over
 *   STYLE 3: straight bottom chair, the top of the pile slumps to one side
 *   DARK: black dining chairs, otherwise wooden ones
 */
public class ChairStackBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty STYLE = IntegerProperty.create("style", 0, 3);
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 1);
    public static final BooleanProperty DARK = BooleanProperty.create("dark");

    private static final VoxelShape LOWER = Block.box(1, 0, 1, 15, 29, 15);
    private static final VoxelShape UPPER = Block.box(1, 0, 1, 15, 20, 15);

    public ChairStackBlock() {
        super(BlockBehaviour.Properties.of()
            .strength(0.6F)
            .sound(SoundType.WOOD)
            .noOcclusion()
            .pushReaction(PushReaction.DESTROY));
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(STYLE, 0)
            .setValue(PART, 0)
            .setValue(DARK, Boolean.TRUE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, STYLE, PART, DARK);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(PART) == 0 ? LOWER : UPPER;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    /** The upper half goes away when the lower half is removed. */
    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level,
            BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(PART) == 1 && dir == Direction.DOWN
                && !(neighbor.is(this) && neighbor.getValue(PART) == 0)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }
}
