package com.backroomsmod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A stop sign on a thin pole. Styles: tall, short, tilted, sunk into the floor, or sunk and tilted.
 * The lettering can be correct or mirrored. Right-click with an empty hand to cycle the style,
 * sneak + right-click with an empty hand to flip the lettering.
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

    private static final VoxelShape TALL_SHAPE = Block.box(4, 0, 4, 12, 16, 12);
    private static final VoxelShape SHORT_SHAPE = Block.box(5, 0, 5, 11, 11, 11);
    private static final VoxelShape TILTED_SHAPE = Block.box(3, 0, 3, 15, 16, 13);
    private static final VoxelShape SUNK_SHAPE = Block.box(4, 0, 4, 12, 10, 12);
    private static final VoxelShape SUNK_TILTED_SHAPE = Block.box(3, 0, 3, 13, 10, 13);

    public StopSignBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(STYLE, Style.TALL)
            .setValue(MIRRORED, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, STYLE, MIRRORED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
            .setValue(FACING, context.getHorizontalDirection().getOpposite())
            .setValue(STYLE, Style.TALL)
            .setValue(MIRRORED, Boolean.FALSE);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (!player.getItemInHand(hand).isEmpty()) {
            return InteractionResult.PASS;
        }
        BlockState next = player.isShiftKeyDown() ? state.cycle(MIRRORED) : state.cycle(STYLE);
        level.setBlock(pos, next, Block.UPDATE_ALL);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        switch (state.getValue(STYLE)) {
            case SHORT: return SHORT_SHAPE;
            case TILTED: return TILTED_SHAPE;
            case SUNK: return SUNK_SHAPE;
            case SUNK_TILTED: return SUNK_TILTED_SHAPE;
            default: return TALL_SHAPE;
        }
    }
}
