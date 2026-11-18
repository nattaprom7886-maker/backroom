package com.backroomsmod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A stop sign on a thin pole. Styles: tall (3 blocks high), short, tilted, sunk into the floor,
 * or sunk and tilted. The lettering can be correct or mirrored. Right-click with an empty hand to
 * cycle the style, sneak + right-click with an empty hand to flip the lettering.
 *
 * The tall style is three blocks stacked: PART 0 = bottom, 1 = middle, 2 = top (holds the sign).
 * Every other style is a single block with PART 0.
 */
public class StopSignBlock extends HorizontalDirectionalBlock {
    public enum Style implements StringRepresentable {
        TALL("tall"),
        SHORT("short"),
        TILTED("tilted"),
        SUNK("sunk"),
        SUNK_TILTED("sunk_tilted");

        private final String name;

        Style(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final EnumProperty<Style> STYLE = EnumProperty.create("style", Style.class);
    public static final BooleanProperty MIRRORED = BooleanProperty.create("mirrored");
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 2);

    private static final VoxelShape TALL_POLE_SHAPE = Block.box(6, 0, 6, 10, 16, 10);
    private static final VoxelShape TALL_TOP_SHAPE = Block.box(4, 0, 4, 12, 14, 12);
    private static final VoxelShape SHORT_SHAPE = Block.box(5, 0, 5, 11, 14, 11);
    private static final VoxelShape TILTED_SHAPE = Block.box(2, 0, 2, 14, 24, 14);
    private static final VoxelShape SUNK_SHAPE = Block.box(4, 0, 4, 12, 10, 12);
    private static final VoxelShape SUNK_TILTED_SHAPE = Block.box(3, 0, 3, 13, 10, 13);

    public StopSignBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(STYLE, Style.TALL)
            .setValue(MIRRORED, Boolean.FALSE)
            .setValue(PART, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, STYLE, MIRRORED, PART);
    }

    private static boolean hasRoomAbove(Level level, BlockPos base) {
        if (base.getY() > level.getMaxBuildHeight() - 3) return false;
        return level.getBlockState(base.above()).canBeReplaced()
            && level.getBlockState(base.above(2)).canBeReplaced();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (!hasRoomAbove(context.getLevel(), context.getClickedPos())) return null;
        return defaultBlockState()
            .setValue(FACING, context.getHorizontalDirection().getOpposite())
            .setValue(STYLE, Style.TALL)
            .setValue(MIRRORED, Boolean.FALSE)
            .setValue(PART, 0);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer,
            ItemStack stack) {
        if (state.getValue(STYLE) == Style.TALL && state.getValue(PART) == 0) {
            level.setBlock(pos.above(), state.setValue(PART, 1), Block.UPDATE_ALL);
            level.setBlock(pos.above(2), state.setValue(PART, 2), Block.UPDATE_ALL);
        }
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighborState,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(STYLE) != Style.TALL) return state;
        int part = state.getValue(PART);
        if (dir == Direction.UP && part < 2 && !isTallPart(neighborState, part + 1)) {
            return Blocks.AIR.defaultBlockState();
        }
        if (dir == Direction.DOWN && part > 0 && !isTallPart(neighborState, part - 1)) {
            return Blocks.AIR.defaultBlockState();
        }
        return state;
    }

    private boolean isTallPart(BlockState other, int part) {
        return other.is(this) && other.getValue(STYLE) == Style.TALL && other.getValue(PART) == part;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (!player.getItemInHand(hand).isEmpty()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;

        BlockPos base = pos.below(state.getValue(PART));
        BlockState baseState = level.getBlockState(base);
        if (!baseState.is(this)) return InteractionResult.PASS;
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE; // no neighbour shape updates

        if (player.isShiftKeyDown()) {
            int parts = baseState.getValue(STYLE) == Style.TALL ? 3 : 1;
            for (int i = 0; i < parts; i++) {
                BlockState s = level.getBlockState(base.above(i));
                if (s.is(this)) level.setBlock(base.above(i), s.cycle(MIRRORED), flags);
            }
            return InteractionResult.CONSUME;
        }

        Style[] all = Style.values();
        Style current = baseState.getValue(STYLE);
        Style next = all[(current.ordinal() + 1) % all.length];
        if (next == Style.TALL && !hasRoomAbove(level, base)) {
            next = all[(next.ordinal() + 1) % all.length];
        }
        if (current == Style.TALL) {
            level.setBlock(base.above(), Blocks.AIR.defaultBlockState(), flags);
            level.setBlock(base.above(2), Blocks.AIR.defaultBlockState(), flags);
        }
        BlockState newBase = baseState.setValue(STYLE, next).setValue(PART, 0);
        level.setBlock(base, newBase, flags);
        if (next == Style.TALL) {
            level.setBlock(base.above(), newBase.setValue(PART, 1), flags);
            level.setBlock(base.above(2), newBase.setValue(PART, 2), flags);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        switch (state.getValue(STYLE)) {
            case SHORT: return SHORT_SHAPE;
            case TILTED: return TILTED_SHAPE;
            case SUNK: return SUNK_SHAPE;
            case SUNK_TILTED: return SUNK_TILTED_SHAPE;
            default: return state.getValue(PART) == 2 ? TALL_TOP_SHAPE : TALL_POLE_SHAPE;
        }
    }
}
