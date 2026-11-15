package com.backroomsmod.world;

import com.backroomsmod.ModBlocks;
import com.backroomsmod.block.ChairBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Minecraft-themed set pieces placed in the middle of special hall rooms.
 * Most pieces fit a 5x5 footprint (dx and dz run from -2 to 2 around the room centre); the
 * McDonald's is bigger (see halfX / halfZ). +z is the front. get() returns null where the
 * piece changes nothing.
 */
public final class SetPieces {
    public static final int TYPE_UPSIDE_DOWN_HOUSE = 0;
    public static final int TYPE_MEMORIAL = 1;
    public static final int TYPE_SHRINE = 2;
    public static final int TYPE_DIRT_HUT = 3;
    public static final int TYPE_MCDONALDS = 4;
    public static final int TYPE_COUNT = 5;

    private SetPieces() {}

    /** Half the footprint width (x) of a piece, measured from the room centre. */
    public static int halfX(int type) {
        return type == TYPE_MCDONALDS ? 6 : 2;
    }

    /** Half the footprint depth (z) of a piece, measured from the room centre. */
    public static int halfZ(int type) {
        return type == TYPE_MCDONALDS ? 4 : 2;
    }

    public static BlockState get(int type, int dx, int y, int dz) {
        switch (type) {
            case TYPE_UPSIDE_DOWN_HOUSE: return upsideDownHouse(dx, y, dz);
            case TYPE_MEMORIAL: return memorial(dx, y, dz);
            case TYPE_SHRINE: return shrine(dx, y, dz);
            case TYPE_DIRT_HUT: return dirtHut(dx, y, dz);
            case TYPE_MCDONALDS: return mcdonalds(dx, y, dz);
            default: return null;
        }
    }

    /** A villager house hanging from the ceiling: foundation on top, roof pointing down. */
    private static BlockState upsideDownHouse(int dx, int y, int dz) {
        int ax = Math.abs(dx), az = Math.abs(dz);
        boolean ring = ax == 2 || az == 2;
        boolean corner = ax == 2 && az == 2;

        if (y == 13) return Blocks.COBBLESTONE.defaultBlockState();

        if (y >= 10 && y <= 12) {
            if (ring) {
                if (dx == 0 && dz == 2 && (y == 11 || y == 12)) return null; // doorway
                boolean window = y == 11 && ((ax == 2 && dz == 0) || (dx == 0 && dz == -2));
                if (window) return Blocks.GLASS_PANE.defaultBlockState();
                return corner ? Blocks.OAK_LOG.defaultBlockState() : Blocks.OAK_PLANKS.defaultBlockState();
            }
            if (y == 12) {
                if (dx == 0 && dz == 0) {
                    return Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
                }
                if (dx == -1 && dz == -1) return Blocks.CRAFTING_TABLE.defaultBlockState();
                if (dx == 1 && dz == -1) return Blocks.FURNACE.defaultBlockState();
            }
            return null;
        }

        // roof, pointing down
        if (y == 9) return Blocks.DARK_OAK_PLANKS.defaultBlockState();
        if (y == 8 && ax <= 1 && az <= 1) return Blocks.DARK_OAK_PLANKS.defaultBlockState();
        if (y == 7 && dx == 0 && dz == 0) return Blocks.DARK_OAK_PLANKS.defaultBlockState();
        return null;
    }

    /** A quiet pig-headed statue with a gold crown, on a dark pedestal ringed with candles. */
    private static BlockState memorial(int dx, int y, int dz) {
        int ax = Math.abs(dx), az = Math.abs(dz);

        if (y == 2) return Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        if (y == 3) {
            if (ax <= 1 && az <= 1) return Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
            boolean candleSpot = (ax == 2 && az == 2) || (ax == 2 && dz == 0) || (az == 2 && dx == 0);
            if (candleSpot) {
                int count = (ax == 2 && az == 2) ? 3 : 1;
                return Blocks.CANDLE.defaultBlockState()
                    .setValue(CandleBlock.CANDLES, count)
                    .setValue(CandleBlock.LIT, true);
            }
            return null;
        }

        // legs
        if ((y == 4 || y == 5) && ax == 1 && dz == 0) return Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
        // torso, arms and cape
        if (y == 6 || y == 7) {
            if (dz == 0 && ax <= 1) return Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
            if (dz == 0 && ax == 2) return Blocks.PINK_CONCRETE.defaultBlockState();
            if (dz == -1 && ax <= 1) return Blocks.RED_WOOL.defaultBlockState();
            return null;
        }
        // head
        if (y == 8 || y == 9) {
            if (y == 8 && dx == 0 && dz == 2) return Blocks.PINK_TERRACOTTA.defaultBlockState(); // snout
            if (y == 9 && ax == 2 && dz == 0) return Blocks.PINK_CONCRETE.defaultBlockState();   // ears
            if (ax <= 1 && az <= 1) {
                if (y == 9 && dz == 1 && ax == 1) return Blocks.BLACK_CONCRETE.defaultBlockState(); // eyes
                return Blocks.PINK_CONCRETE.defaultBlockState();
            }
            return null;
        }
        // crown
        if (y == 10 && ax <= 1 && az <= 1 && !(dx == 0 && dz == 0)) return Blocks.GOLD_BLOCK.defaultBlockState();
        if (y == 11 && ax == 1 && az == 1) return Blocks.GOLD_BLOCK.defaultBlockState();
        return null;
    }

    /**
     * Netherrack shrine: a gold platform with a sunken centre holding a redstone torch, two torches
     * on the back corners and a fire burning on netherrack at the back, with blackstone in the rim.
     */
    private static BlockState shrine(int dx, int y, int dz) {
        int ax = Math.abs(dx), az = Math.abs(dz);
        boolean platform = ax <= 1 && az <= 1;

        if (y == 1) {
            if ((dx == 0 && dz == 2) || (dx == 2 && dz == 0)) return Blocks.BLACKSTONE.defaultBlockState();
            return Blocks.NETHERRACK.defaultBlockState();
        }
        if (y == 2) {
            if (dx == 0 && dz == 0) return Blocks.REDSTONE_TORCH.defaultBlockState(); // sunken centre
            if (dx == 0 && dz == -1) return Blocks.NETHERRACK.defaultBlockState();     // keeps the fire burning
            if (platform) return Blocks.GOLD_BLOCK.defaultBlockState();
            return null;
        }
        if (y == 3 && az == 1 && dz == -1) {
            if (dx == 0) return Blocks.FIRE.defaultBlockState();
            if (ax == 1) return Blocks.REDSTONE_TORCH.defaultBlockState();
        }
        return null;
    }

    /** The tiny dirt shelter somebody dug in on their first night. */
    private static BlockState dirtHut(int dx, int y, int dz) {
        int ax = Math.abs(dx), az = Math.abs(dz);
        boolean ring = ax == 2 || az == 2;

        if (y >= 2 && y <= 4) {
            if (ring) {
                if (dx == 0 && dz == 2 && (y == 2 || y == 3)) return null; // entrance
                return Blocks.DIRT.defaultBlockState();
            }
            if (y == 2) {
                if (dx == -1 && dz == -1) return Blocks.CRAFTING_TABLE.defaultBlockState();
                if (dx == 1 && dz == -1) return Blocks.FURNACE.defaultBlockState();
                if (dx == 0 && dz == -1) return Blocks.TORCH.defaultBlockState();
            }
            return null;
        }
        if (y == 5) return Blocks.DIRT.defaultBlockState();
        return null;
    }

    // ---------- McDonald's ----------
    // Footprint: dx -6..6, dz -4..4. Counter and kitchen at the back (-z), tables toward the front.

    private static final int[][] TABLES = {{-4, 1}, {4, 1}, {0, 2}};

    /** x, z, facing (2D data value: 0 south, 1 west, 2 north, 3 east), office chair (1) or wooden (0). */
    private static final int[][] DINING_CHAIRS = {
        {-4, 0, 0, 0}, {-4, 2, 2, 0}, {-5, 1, 3, 0}, {-3, 1, 1, 0},
        {4, 0, 0, 1}, {4, 2, 2, 1}, {3, 1, 3, 1}, {5, 1, 1, 1},
        {0, 1, 0, 1}, {0, 3, 2, 0}, {-1, 2, 3, 0}, {1, 2, 1, 1},
    };

    private static BlockState chair(boolean office, int facing, ChairBlock.Pose pose) {
        Block block = office ? ModBlocks.OFFICE_CHAIR.get() : ModBlocks.WOOD_CHAIR.get();
        return block.defaultBlockState()
            .setValue(ChairBlock.FACING, Direction.from2DDataValue(facing))
            .setValue(ChairBlock.POSE, pose);
    }

    /** The golden arches: two peaks over a shared middle, three rows high, centred on dx = 0. */
    private static boolean arches(int dx, int y) {
        int ax = Math.abs(dx);
        if (y == 5) return ax == 1;
        if (y == 4) return ax == 2 || ax == 0;
        if (y == 3) return ax == 2;
        return false;
    }

    private static BlockState mcdonalds(int dx, int y, int dz) {
        int ax = Math.abs(dx);

        if (y == 1) return Blocks.SMOOTH_SANDSTONE.defaultBlockState(); // tiled floor
        if (y > 5) return null;

        // back wall: yellow, with a red panel holding the arches and a black menu board on each side
        if (dz == -4) {
            if (y >= 3 && ax <= 3) {
                return arches(dx, y) ? Blocks.YELLOW_CONCRETE.defaultBlockState()
                                     : Blocks.RED_CONCRETE.defaultBlockState();
            }
            if (y >= 3 && y <= 4 && (ax == 4 || ax == 5)) return Blocks.BLACK_CONCRETE.defaultBlockState();
            return Blocks.YELLOW_CONCRETE.defaultBlockState();
        }

        // service counter, open at the east end
        if (dz == -2 && dx >= -5 && dx <= 4) {
            if (y == 2) return Blocks.RED_CONCRETE.defaultBlockState();
            if (y == 3) return Blocks.QUARTZ_SLAB.defaultBlockState();
            return null;
        }

        // kitchen equipment behind the counter (the pillars at dx = +-3 stay)
        if (dz == -3 && y == 2) {
            if (dx == -4 || dx == 2) {
                return Blocks.SMOKER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
            }
            if (dx == -1 || dx == 0) return Blocks.IRON_BLOCK.defaultBlockState();
            return null;
        }

        // one chair has been knocked over
        if (dx == 2 && dz == 3 && y == 2) return chair(true, 1, ChairBlock.Pose.FALLEN);

        if (y == 2 || y == 3) {
            for (int[] t : TABLES) {
                if (t[0] == dx && t[1] == dz) {
                    return y == 2 ? Blocks.DARK_OAK_FENCE.defaultBlockState()
                                  : Blocks.DARK_OAK_PRESSURE_PLATE.defaultBlockState();
                }
            }
        }
        if (y == 2) {
            for (int[] c : DINING_CHAIRS) {
                if (c[0] == dx && c[1] == dz) return chair(c[3] == 1, c[2], ChairBlock.Pose.UPRIGHT);
            }
        }
        return null;
    }
}
