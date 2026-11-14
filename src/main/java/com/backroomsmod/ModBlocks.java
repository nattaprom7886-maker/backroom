package com.backroomsmod;

import com.backroomsmod.block.ExitDoorBlock;
import java.util.function.Supplier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
        DeferredRegister.create(ForgeRegistries.BLOCKS, BackroomsMod.MODID);
    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(ForgeRegistries.ITEMS, BackroomsMod.MODID);

    public static final RegistryObject<Block> WALLPAPER = block("backrooms_wallpaper",
        () -> new Block(base(MapColor.COLOR_YELLOW, SoundType.WOOD)));

    public static final RegistryObject<Block> CARPET = block("backrooms_carpet",
        () -> new Block(base(MapColor.SAND, SoundType.WOOL)));

    public static final RegistryObject<Block> CEILING_TILE = block("backrooms_ceiling_tile",
        () -> new Block(base(MapColor.TERRACOTTA_WHITE, SoundType.STONE)));

    public static final RegistryObject<Block> LIGHT_PANEL = block("backrooms_light_panel",
        () -> new Block(base(MapColor.SNOW, SoundType.GLASS).lightLevel(state -> 13)));

    public static final RegistryObject<Block> EXIT_DOOR = BLOCKS.register("exit_door",
        () -> new ExitDoorBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.SNOW)
            .strength(-1.0F, 3600000.0F)
            .sound(SoundType.WOOD)
            .noOcclusion()
            .noLootTable()));

    static {
        ITEMS.register("exit_door", () -> new DoubleHighBlockItem(EXIT_DOOR.get(), new Item.Properties()));
    }

    private static BlockBehaviour.Properties base(MapColor color, SoundType sound) {
        return BlockBehaviour.Properties.of()
            .mapColor(color)
            .strength(-1.0F, 3600000.0F)
            .sound(sound)
            .noLootTable();
    }

    private static RegistryObject<Block> block(String name, Supplier<Block> supplier) {
        RegistryObject<Block> registered = BLOCKS.register(name, supplier);
        ITEMS.register(name, () -> new BlockItem(registered.get(), new Item.Properties()));
        return registered;
    }
}
