package com.backroomsmod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
        DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, BackroomsMod.MODID);

    public static final RegistryObject<SoundEvent> LIGHT_HUM = register("light_hum");
    public static final RegistryObject<SoundEvent> LIGHT_FLICKER = register("light_flicker");

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name,
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(BackroomsMod.MODID, name)));
    }
}
