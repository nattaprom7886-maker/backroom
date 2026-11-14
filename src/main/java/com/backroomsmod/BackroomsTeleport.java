package com.backroomsmod;

import com.backroomsmod.world.BackroomsChunkGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

public final class BackroomsTeleport {
    private static final String KEY_DIM = "backroomsmod_return_dim";
    private static final String KEY_X = "backroomsmod_return_x";
    private static final String KEY_Y = "backroomsmod_return_y";
    private static final String KEY_Z = "backroomsmod_return_z";

    private BackroomsTeleport() {}

    public static void enter(ServerPlayer player) {
        MinecraftServer server = player.server;
        ServerLevel target = server.getLevel(ModDimensions.BACKROOMS);
        if (target == null) return;

        if (!player.level().dimension().equals(ModDimensions.BACKROOMS)) {
            CompoundTag data = player.getPersistentData();
            data.putString(KEY_DIM, player.level().dimension().location().toString());
            data.putDouble(KEY_X, player.getX());
            data.putDouble(KEY_Y, player.getY());
            data.putDouble(KEY_Z, player.getZ());
        }

        int cell = BackroomsChunkGenerator.CELL;
        int x = Math.floorDiv(player.getBlockX(), cell) * cell + cell / 2;
        int z = Math.floorDiv(player.getBlockZ(), cell) * cell + cell / 2;
        player.teleportTo(target, x + 0.5, 2.0, z + 0.5, player.getYRot(), player.getXRot());
    }

    public static void leave(ServerPlayer player) {
        MinecraftServer server = player.server;
        CompoundTag data = player.getPersistentData();

        ServerLevel target = null;
        double x = 0, y = 0, z = 0;
        if (data.contains(KEY_DIM)) {
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION,
                new ResourceLocation(data.getString(KEY_DIM)));
            target = server.getLevel(key);
            x = data.getDouble(KEY_X);
            y = data.getDouble(KEY_Y);
            z = data.getDouble(KEY_Z);
        }
        if (target == null) {
            target = server.overworld();
            BlockPos spawn = target.getSharedSpawnPos();
            x = spawn.getX() + 0.5;
            z = spawn.getZ() + 0.5;
            y = target.getHeight(Heightmap.Types.MOTION_BLOCKING, spawn.getX(), spawn.getZ());
        }
        data.remove(KEY_DIM);
        data.remove(KEY_X);
        data.remove(KEY_Y);
        data.remove(KEY_Z);
        player.teleportTo(target, x, y, z, player.getYRot(), player.getXRot());
    }
}
