package com.backroomsmod;

import com.mojang.logging.LogUtils;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(BackroomsMod.MODID)
public class BackroomsMod {
    public static final String MODID = "backroomsmod";
    private static final Logger LOGGER = LogUtils.getLogger();

    public BackroomsMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.BLOCKS.register(bus);
        ModBlocks.ITEMS.register(bus);
        ModDimensions.CHUNK_GENERATORS.register(bus);
        ModCreativeTabs.TABS.register(bus);
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
        LOGGER.info("Backrooms mod loaded");
    }

    // Test commands (op only). Real entry methods come in later stages.
    private void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("backrooms")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("enter").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    BackroomsTeleport.enter(player);
                    return 1;
                }))
                .then(Commands.literal("leave").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    BackroomsTeleport.leave(player);
                    return 1;
                }))
        );
    }
}
