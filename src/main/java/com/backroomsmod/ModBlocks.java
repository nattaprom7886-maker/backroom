package com.backroomsmod;

import com.backroomsmod.block.ArchCornerBlock;
import com.backroomsmod.block.BalustradeBlock;
import com.backroomsmod.block.ChairBlock;
import com.backroomsmod.block.ExitDoorBlock;
import com.backroomsmod.block.HalfWallBlock;
import com.backroomsmod.block.LightPanelBlock;
import com.backroomsmod.block.StopSignBlock;
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

    public static final RegistryObject<Block> WALLPAPER_PEELING = block("backrooms_wallpaper_peeling",
        () -> new Block(base(MapColor.COLOR_YELLOW, SoundType.WOOD)));

    public static final RegistryObject<Block> WALLPAPER_STAINED = block("backrooms_wallpaper_stained",
        () -> new Block(base(MapColor.COLOR_YELLOW, SoundType.WOOD)));

    public static final RegistryObject<Block> WALLPAPER_MOLDY = block("backrooms_wallpaper_moldy",
        () -> new Block(base(MapColor.COLOR_YELLOW, SoundType.WOOD)));

    public static final RegistryObject<Block> WALLPAPER_SCRATCHED = block("backrooms_wallpaper_scratched",
        () -> new Block(base(MapColor.COLOR_YELLOW, SoundType.WOOD)));

    public static final RegistryObject<Block> CARPET_WET = block("backrooms_carpet_wet",
        () -> new Block(base(MapColor.SAND, SoundType.WOOL)));

    public static final RegistryObject<Block> CEILING_TILE = block("backrooms_ceiling_tile",
        () -> new Block(base(MapColor.TERRACOTTA_WHITE, SoundType.STONE)));

    public static final RegistryObject<Block> LIGHT_PANEL = block("backrooms_light_panel",
        () -> new LightPanelBlock(base(MapColor.SNOW, SoundType.GLASS)
            .lightLevel(state -> state.getValue(LightPanelBlock.LIT) ? 13 : 0)));

    public static final RegistryObject<Block> WALLPAPER_TRIM = block("backrooms_wallpaper_trim",
        () -> new Block(base(MapColor.COLOR_YELLOW, SoundType.WOOD)));

    public static final RegistryObject<Block> CEILING_HOLE = block("backrooms_ceiling_hole",
        () -> new Block(base(MapColor.TERRACOTTA_WHITE, SoundType.STONE)));

    public static final RegistryObject<Block> CEILING_VENT = block("backrooms_ceiling_vent",
        () -> new Block(base(MapColor.METAL, SoundType.METAL)));

    public static final RegistryObject<Block> CARPET_LIT = block("backrooms_carpet_lit",
        () -> new Block(base(MapColor.SAND, SoundType.WOOL).lightLevel(state -> 5)));

    public static final RegistryObject<Block> WINDOW = block("backrooms_window",
        () -> new Block(base(MapColor.COLOR_YELLOW, SoundType.GLASS)));

    public static final RegistryObject<Block> DOOR_FRAME = block("backrooms_door_frame",
        () -> new Block(base(MapColor.WOOD, SoundType.WOOD)));

    public static final RegistryObject<Block> HALF_WALL = block("backrooms_half_wall",
        () -> new HalfWallBlock(base(MapColor.COLOR_YELLOW, SoundType.WOOD).noOcclusion()));

    public static final RegistryObject<Block> BALUSTRADE = block("backrooms_balustrade",
        () -> new BalustradeBlock(base(MapColor.WOOD, SoundType.WOOD).noOcclusion()));

    public static final RegistryObject<Block> ARCH_CORNER = block("backrooms_arch_corner",
        () -> new ArchCornerBlock(base(MapColor.COLOR_YELLOW, SoundType.WOOD).noOcclusion()));

    public static final RegistryObject<Block> WOOD_CHAIR = block("wood_chair",
        () -> new ChairBlock(chair(MapColor.WOOD, SoundType.WOOD)));

    public static final RegistryObject<Block> OFFICE_CHAIR = block("office_chair",
        () -> new ChairBlock(chair(MapColor.COLOR_GRAY, SoundType.METAL)));

    public static final RegistryObject<Block> STOP_SIGN = block("stop_sign",
        () -> new StopSignBlock(chair(MapColor.COLOR_RED, SoundType.METAL)));

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

    private static BlockBehaviour.Properties chair(MapColor color, SoundType sound) {
        return BlockBehaviour.Properties.of()
            .mapColor(color)
            .strength(0.6F)
            .sound(sound)
            .noOcclusion()
            .noLootTable();
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
