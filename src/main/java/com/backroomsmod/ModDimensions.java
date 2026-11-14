package com.backroomsmod;

import com.backroomsmod.world.BackroomsChunkGenerator;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import com.mojang.serialization.Codec;
import net.minecraftforge.registries.DeferredRegister;

public class ModDimensions {
    public static final ResourceKey<Level> BACKROOMS = ResourceKey.create(
        Registries.DIMENSION, new ResourceLocation(BackroomsMod.MODID, "backrooms"));

    public static final DeferredRegister<Codec<? extends ChunkGenerator>> CHUNK_GENERATORS =
        DeferredRegister.create(Registries.CHUNK_GENERATOR, BackroomsMod.MODID);

    static {
        CHUNK_GENERATORS.register("backrooms", () -> BackroomsChunkGenerator.CODEC);
    }
}
