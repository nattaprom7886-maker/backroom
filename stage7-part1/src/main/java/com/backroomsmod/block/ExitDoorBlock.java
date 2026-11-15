package com.backroomsmod.block;

import com.backroomsmod.BackroomsTeleport;
import com.backroomsmod.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.phys.BlockHitResult;

/** White wooden door with a gold knob. Inside the Backrooms it is the only way out. */
public class ExitDoorBlock extends DoorBlock {
    public ExitDoorBlock(BlockBehaviour.Properties properties) {
        super(properties, BlockSetType.OAK);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.dimension().equals(ModDimensions.BACKROOMS)) {
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                BackroomsTeleport.leave(serverPlayer);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.use(state, level, pos, player, hand, hit);
    }
}
