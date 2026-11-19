package com.backroomsmod;

import com.backroomsmod.block.DecalBlock;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

/**
 * Registers the three small decorations (wall outlet, empty mount mark, carpet dent) on its own, so
 * ModBlocks does not need to change. The blocks are created inside the registry event.
 */
@Mod.EventBusSubscriber(modid = WallDecor.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class WallDecor {
    public static final String MODID = "backroomsmod";

    public static volatile Block OUTLET;
    public static volatile Block MOUNT_MARK;
    public static volatile Block CARPET_DENT;

    private static volatile Item outletItem;
    private static volatile Item mountMarkItem;
    private static volatile Item dentItem;

    private WallDecor() {}

    private static synchronized void createBlocks() {
        if (OUTLET != null) return;
        OUTLET = new DecalBlock(DecalBlock.Kind.OUTLET);
        MOUNT_MARK = new DecalBlock(DecalBlock.Kind.MOUNT_MARK);
        CARPET_DENT = new DecalBlock(DecalBlock.Kind.DENT);
    }

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        event.register(ForgeRegistries.Keys.BLOCKS, helper -> {
            createBlocks();
            helper.register(new ResourceLocation(MODID, "wall_outlet"), OUTLET);
            helper.register(new ResourceLocation(MODID, "wall_mount_mark"), MOUNT_MARK);
            helper.register(new ResourceLocation(MODID, "carpet_dent"), CARPET_DENT);
        });
        event.register(ForgeRegistries.Keys.ITEMS, helper -> {
            createBlocks();
            outletItem = new BlockItem(OUTLET, new Item.Properties());
            mountMarkItem = new BlockItem(MOUNT_MARK, new Item.Properties());
            dentItem = new BlockItem(CARPET_DENT, new Item.Properties());
            helper.register(new ResourceLocation(MODID, "wall_outlet"), outletItem);
            helper.register(new ResourceLocation(MODID, "wall_mount_mark"), mountMarkItem);
            helper.register(new ResourceLocation(MODID, "carpet_dent"), dentItem);
        });
    }

    @SubscribeEvent
    public static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS && outletItem != null) {
            event.accept(outletItem);
            event.accept(mountMarkItem);
            event.accept(dentItem);
        }
    }
}
