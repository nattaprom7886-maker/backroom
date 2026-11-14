package com.backroomsmod.world;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Minecraft-themed set pieces placed in the middle of special hall rooms.
 * Every piece fits a 5x5 footprint: dx and dz run from -2 to 2 around the room centre,
 * +z is the front. get() returns null where the piece changes nothing.
 */
public final class SetPieces {
    public static final int TYPE_UPSIDE_DOWN_HOUSE = 0;
    public static final int TYPE_MEMORIAL = 1;
    public static final int TYPE_SHRINE = 2;
    public static final int TYPE_DIRT_HUT = 3;

    private SetPieces() {}

    public static BlockState get(int type, int dx, int y, int dz) {
        switch (type) {
            case TYPE_UPSIDE_DOWN_HOUSE: return upsideDownHouse(dx, y, dz);
            case TYPE_MEMORIAL: return memorial(dx, y, dz);
            case TYPE_SHRINE: return shrine(dx, y, dz);
            case TYPE_DIRT_HUT: return dirtHut(dx, y, dz);
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

    /** The classic netherrack-and-gold shrine with redstone torches. */
    private static BlockState shrine(int dx, int y, int dz) {
        int ax = Math.abs(dx), az = Math.abs(dz);
        boolean corner = ax == 2 && az == 2;

        if (y == 1) return Blocks.NETHERRACK.defaultBlockState();
        if (y == 2) {
            if (corner) return Blocks.GOLD_BLOCK.defaultBlockState();
            if (ax <= 1 && az <= 1) return Blocks.NETHERRACK.defaultBlockState();
            return null;
        }
        if (y == 3) {
            if (corner) return Blocks.REDSTONE_TORCH.defaultBlockState();
            if (dx == 0 && dz == 0) return Blocks.NETHERRACK.defaultBlockState();
            return null;
        }
        if (y == 4 && dx == 0 && dz == 0) return Blocks.REDSTONE_TORCH.defaultBlockState();
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
}
