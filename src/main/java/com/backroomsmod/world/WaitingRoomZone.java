package com.backroomsmod.world;

import com.backroomsmod.ModBlocks;
import com.backroomsmod.ModDimensions;
import com.backroomsmod.block.ChairBlock;
import com.backroomsmod.block.LightPanelBlock;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * "Waiting Room": a small zone (one chunk) that appears rarely in Level 0.
 * Built when a player gets near, so the world generator does not need to change.
 */
@Mod.EventBusSubscriber(modid = "backroomsmod")
public class WaitingRoomZone {
    private static final double CHANCE = 0.015;   // about 1 chunk in 65
    private static final int MIN_CHUNK_DIST = 3;  // keep it away from the spawn area
    private static final int SCAN_RADIUS = 4;     // chunks around the player

    private static final String[] PAGES = {
            "\u0e27\u0e31\u0e19\u0e19\u0e35\u0e49\u0e27\u0e31\u0e19\u0e17\u0e35\u0e48\u0e40\u0e17\u0e48\u0e32\u0e44\u0e2b\u0e23\u0e48 \u0e1c\u0e21\u0e19\u0e31\u0e1a\u0e44\u0e21\u0e48\u0e44\u0e14\u0e49\u0e41\u0e25\u0e49\u0e27\n\u0e44\u0e1f\u0e01\u0e30\u0e1e\u0e23\u0e34\u0e1a\u0e15\u0e25\u0e2d\u0e14\u0e40\u0e27\u0e25\u0e32 \u0e40\u0e2a\u0e35\u0e22\u0e07\u0e2b\u0e36\u0e48\u0e07\u0e46 \u0e44\u0e21\u0e48\u0e2b\u0e22\u0e38\u0e14\n\u0e1c\u0e21\u0e19\u0e31\u0e48\u0e07\u0e23\u0e2d\u0e15\u0e23\u0e07\u0e19\u0e35\u0e49\u0e21\u0e32\u0e19\u0e32\u0e19\u0e21\u0e32\u0e01",
            "\u0e17\u0e38\u0e01\u0e04\u0e19\u0e17\u0e35\u0e48\u0e19\u0e31\u0e48\u0e07\u0e23\u0e2d\u0e41\u0e25\u0e49\u0e27\u0e2b\u0e25\u0e31\u0e1a\u0e44\u0e1b\n\u0e15\u0e37\u0e48\u0e19\u0e21\u0e32\u0e01\u0e47\u0e44\u0e21\u0e48\u0e40\u0e04\u0e22\u0e40\u0e2b\u0e21\u0e37\u0e2d\u0e19\u0e40\u0e14\u0e34\u0e21\n\u0e1c\u0e21\u0e40\u0e25\u0e22\u0e44\u0e21\u0e48\u0e01\u0e25\u0e49\u0e32\u0e2b\u0e25\u0e31\u0e1a",
            "\u0e16\u0e49\u0e32\u0e40\u0e08\u0e2d\u0e1b\u0e23\u0e30\u0e15\u0e39\u0e2a\u0e35\u0e02\u0e32\u0e27 \u0e25\u0e39\u0e01\u0e1a\u0e34\u0e14\u0e17\u0e2d\u0e07\n\u0e2d\u0e22\u0e48\u0e32\u0e25\u0e31\u0e07\u0e40\u0e25\u0e19\u0e30\n\u0e2b\u0e49\u0e2d\u0e07\u0e19\u0e35\u0e49\u0e44\u0e21\u0e48\u0e21\u0e35\u0e43\u0e04\u0e23\u0e16\u0e39\u0e01\u0e40\u0e23\u0e35\u0e22\u0e01\u0e0a\u0e37\u0e48\u0e2d\u0e2a\u0e31\u0e01\u0e17\u0e35"
    };

    public static class Built extends SavedData {
        final Set<Long> set = new HashSet<>();

        public static Built load(CompoundTag tag) {
            Built b = new Built();
            for (long l : tag.getLongArray("built")) {
                b.set.add(l);
            }
            return b;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            long[] a = new long[set.size()];
            int i = 0;
            for (long l : set) {
                a[i++] = l;
            }
            tag.putLongArray("built", a);
            return tag;
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        if (!level.dimension().equals(ModDimensions.BACKROOMS) || level.getGameTime() % 40L != 0L) {
            return;
        }
        Built built = null;
        for (ServerPlayer p : level.players()) {
            int pcx = Math.floorDiv(p.blockPosition().getX(), 16);
            int pcz = Math.floorDiv(p.blockPosition().getZ(), 16);
            for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
                for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                    int cx = pcx + dx;
                    int cz = pcz + dz;
                    if (!isZoneChunk(level.getSeed(), cx, cz)) {
                        continue;
                    }
                    if (built == null) {
                        built = level.getDataStorage().computeIfAbsent(Built::load, Built::new, "backroomsmod_waiting_rooms");
                    }
                    long key = (((long) cx) << 32) ^ (cz & 0xFFFFFFFFL);
                    if (built.set.contains(key) || level.getChunkSource().getChunkNow(cx, cz) == null) {
                        continue;
                    }
                    built.set.add(key);
                    built.setDirty();
                    if (!blocked(level, cx, cz)) {
                        build(level, cx, cz);
                    }
                }
            }
        }
    }

    private static long mix(long a, long b, long c) {
        long h = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL ^ c * 0x165667B19E3779F9L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return h;
    }

    private static boolean isZoneChunk(long seed, int cx, int cz) {
        if (Math.abs(cx) < MIN_CHUNK_DIST && Math.abs(cz) < MIN_CHUNK_DIST) {
            return false;
        }
        double r = (mix(seed ^ 0x57A17L, cx, cz) >>> 11) * (1.0 / (1L << 53));
        return r < CHANCE;
    }

    /** Skip chunks that touch an existing set piece (house, McDonald's...) or an exit door. */
    private static boolean blocked(ServerLevel level, int cx, int cz) {
        int bx = cx << 4;
        int bz = cz << 4;
        try {
            Method m = BackroomsChunkGenerator.class.getDeclaredMethod("specialType", int.class, int.class);
            m.setAccessible(true);
            for (int rx = Math.floorDiv(bx, 18); rx <= Math.floorDiv(bx + 15, 18); rx++) {
                for (int rz = Math.floorDiv(bz, 18); rz <= Math.floorDiv(bz + 15, 18); rz++) {
                    if ((Integer) m.invoke(null, rx, rz) >= 0) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {
            // if reflection fails we just build anyway
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 2; y <= 3; y++) {
                    if (level.getBlockState(pos.set(bx + x, y, bz + z)).is(ModBlocks.EXIT_DOOR.get())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void put(ServerLevel level, int bx, int bz, int lx, int y, int lz, BlockState state) {
        level.setBlock(new BlockPos(bx + lx, y, bz + lz), state, 2);
    }

    private static BlockState chair(boolean office, Direction facing, ChairBlock.Pose pose) {
        return (office ? ModBlocks.OFFICE_CHAIR : ModBlocks.WOOD_CHAIR).get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, facing)
                .setValue(ChairBlock.POSE, pose);
    }

    private static void build(ServerLevel level, int cx, int cz) {
        int bx = cx << 4;
        int bz = cz << 4;
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState carpet = ModBlocks.CARPET.get().defaultBlockState();
        BlockState wet = ModBlocks.CARPET_WET.get().defaultBlockState();
        BlockState litCarpet = ModBlocks.CARPET_LIT.get().defaultBlockState();
        BlockState tile = ModBlocks.CEILING_TILE.get().defaultBlockState();
        BlockState wall = ModBlocks.WALLPAPER.get().defaultBlockState();
        BlockState stained = ModBlocks.WALLPAPER_STAINED.get().defaultBlockState();
        BlockState panelOn = ModBlocks.LIGHT_PANEL.get().defaultBlockState().setValue(LightPanelBlock.LIT, Boolean.TRUE);
        BlockState panelOff = ModBlocks.LIGHT_PANEL.get().defaultBlockState().setValue(LightPanelBlock.LIT, Boolean.FALSE);

        // open plaza (leave the outer ring of the chunk alone)
        for (int x = 1; x <= 14; x++) {
            for (int z = 1; z <= 14; z++) {
                put(level, bx, bz, x, 1, z, carpet);
                for (int y = 2; y <= 5; y++) {
                    put(level, bx, bz, x, y, z, air);
                }
                put(level, bx, bz, x, 6, z, tile);
            }
        }
        put(level, bx, bz, 2, 6, 2, panelOn);
        put(level, bx, bz, 13, 6, 2, panelOff);
        put(level, bx, bz, 2, 6, 13, panelOn);
        put(level, bx, bz, 13, 6, 13, panelOn);

        // room walls (3..12), doorway on the south side
        for (int x = 3; x <= 12; x++) {
            for (int z = 3; z <= 12; z++) {
                if (x != 3 && x != 12 && z != 3 && z != 12) {
                    continue;
                }
                boolean door = z == 12 && (x == 7 || x == 8);
                for (int y = 2; y <= 5; y++) {
                    if (door && y <= 4) {
                        continue;
                    }
                    boolean stain = ((mix(cx, x * 31L + y, cz + z) >>> 7) & 7L) == 0L;
                    put(level, bx, bz, x, y, z, stain ? stained : wall);
                }
            }
        }

        // chairs: north row facing south, west row facing east, one fallen over
        for (int x = 5; x <= 10; x++) {
            ChairBlock.Pose pose = x == 8 ? ChairBlock.Pose.FALLEN : ChairBlock.Pose.UPRIGHT;
            put(level, bx, bz, x, 2, 4, chair(x % 2 == 0, Direction.SOUTH, pose));
        }
        for (int z = 6; z <= 10; z += 2) {
            put(level, bx, bz, 4, 2, z, chair(false, Direction.EAST, ChairBlock.Pose.UPRIGHT));
        }

        // reception desk in the middle
        BlockState desk = ModBlocks.HALF_WALL.get().defaultBlockState();
        for (int x = 7; x <= 8; x++) {
            for (int z = 7; z <= 8; z++) {
                put(level, bx, bz, x, 2, z, desk);
            }
        }
        put(level, bx, bz, 7, 6, 7, panelOn);
        put(level, bx, bz, 8, 6, 8, panelOff);

        // floor details
        put(level, bx, bz, 9, 1, 9, wet);
        put(level, bx, bz, 9, 1, 10, wet);
        put(level, bx, bz, 10, 1, 9, wet);
        put(level, bx, bz, 5, 1, 9, litCarpet);

        // chest with a note
        BlockPos chestPos = new BlockPos(bx + 11, 2, bz + 4);
        level.setBlock(chestPos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.WEST), 2);
        BlockEntity be = level.getBlockEntity(chestPos);
        if (be instanceof ChestBlockEntity chest) {
            chest.setItem(13, makeBook());
        }
    }

    private static ItemStack makeBook() {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        CompoundTag tag = book.getOrCreateTag();
        tag.putString("title", "\u0e1a\u0e31\u0e19\u0e17\u0e36\u0e01\u0e2b\u0e49\u0e2d\u0e07\u0e23\u0e2d");
        tag.putString("author", "?");
        ListTag pages = new ListTag();
        for (String s : PAGES) {
            pages.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(s))));
        }
        tag.put("pages", pages);
        return book;
    }
}
