package com.backroomsmod;

import com.backroomsmod.block.LightPanelBlock;
import com.backroomsmod.world.BackroomsChunkGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;

/**
 * Drives the ceiling lights near each player: faulty lights flicker in bursts, whole zones stay
 * dark, and every light that is on hums. Lights close to a dead zone hum louder.
 */
public final class LightFlicker {
    private static final int RADIUS = 4;       // cells around the player whose lights are updated
    private static final int HUM_RADIUS = 3;   // cells around the player whose lights hum

    private LightFlicker() {}

    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.level.isClientSide) return;
        if (!(event.level instanceof ServerLevel level)) return;
        if (!level.dimension().equals(ModDimensions.BACKROOMS)) return;
        long t = level.getGameTime();
        if (t % 2 != 0) return;

        int cell = BackroomsChunkGenerator.CELL;
        for (ServerPlayer player : level.players()) {
            int pcx = Math.floorDiv(player.getBlockX(), cell);
            int pcz = Math.floorDiv(player.getBlockZ(), cell);
            for (int dcx = -RADIUS; dcx <= RADIUS; dcx++) {
                for (int dcz = -RADIUS; dcz <= RADIUS; dcz++) {
                    updateCell(level, pcx + dcx, pcz + dcz, t,
                        Math.abs(dcx) <= HUM_RADIUS && Math.abs(dcz) <= HUM_RADIUS);
                }
            }
        }
    }

    private static void updateCell(ServerLevel level, int cx, int cz, long t, boolean hum) {
        if (!BackroomsChunkGenerator.lightCell(cx, cz)) return;
        int cell = BackroomsChunkGenerator.CELL;
        int y = BackroomsChunkGenerator.ceilOfCell(cx, cz);
        BlockPos first = new BlockPos(cx * cell + 2, y, cz * cell + 2);
        if (!level.hasChunkAt(first)) return;

        boolean shouldBeLit = BackroomsChunkGenerator.lightLitAt(cx, cz, t);
        boolean changedOff = false;
        for (int dx = 0; dx < 2; dx++) {
            for (int dz = 0; dz < 2; dz++) {
                BlockPos pos = first.offset(dx, 0, dz);
                BlockState state = level.getBlockState(pos);
                if (!state.is(ModBlocks.LIGHT_PANEL.get())) continue;
                if (state.getValue(LightPanelBlock.LIT) != shouldBeLit) {
                    level.setBlock(pos, state.setValue(LightPanelBlock.LIT, shouldBeLit), 3);
                    if (!shouldBeLit) changedOff = true;
                }
            }
        }

        double mx = cx * cell + 3.0, mz = cz * cell + 3.0;
        if (changedOff) {
            level.playSound(null, mx, y, mz, ModSounds.LIGHT_FLICKER.get(), SoundSource.BLOCKS, 0.8F,
                0.9F + level.random.nextFloat() * 0.3F);
        }
        // hum: the sound is 2 s long, so each light restarts it every 40 ticks (staggered per cell)
        if (hum && shouldBeLit && Math.floorMod(t + cx * 7L + cz * 13L, 40L) < 2) {
            boolean loud = BackroomsChunkGenerator.nearDarkZone(cx, cz);
            level.playSound(null, mx, y, mz, ModSounds.LIGHT_HUM.get(), SoundSource.BLOCKS,
                loud ? 1.6F : 0.5F, loud ? 1.12F : 1.0F);
        }
    }
}
