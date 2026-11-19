package com.backroomsmod.world;

import com.backroomsmod.ModBlocks;
import com.backroomsmod.WallDecor;
import com.backroomsmod.block.ArchCornerBlock;
import com.backroomsmod.block.BalustradeBlock;
import com.backroomsmod.block.ChairBlock;
import com.backroomsmod.block.DecalBlock;
import com.backroomsmod.block.LightPanelBlock;
import com.backroomsmod.block.StopSignBlock;
import com.backroomsmod.block.WallSignBlock;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

/**
 * Backrooms layout: a grid of CELL x CELL cells. Walls sit on the grid lines and are decided
 * per edge by a hash, so every chunk agrees without needing neighbours.
 *
 * Vertical layout (min_y 0, height 16):
 *   y=0 bedrock, y=1 carpet, y=2..5 open space, y=6 ceiling, y=7..15 bedrock.
 */
public class BackroomsChunkGenerator extends ChunkGenerator {
    public static final Codec<BackroomsChunkGenerator> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource)
        ).apply(instance, instance.stable(BackroomsChunkGenerator::new)));

    public static final int CELL = 6;
    private static final int HALL_REGION = 3;       // halls are 3x3 cells
    private static final double HALL_CHANCE = 0.28;
    private static final double DOOR_CHANCE = 1.0 / 1500.0; // chance per cell of holding the exit door (about 1 door per ~230x230 blocks)
    private static final double SPECIAL_CHANCE = 0.035;    // share of halls that hold a set piece (about 1 set piece per ~180x180 blocks)
    private static final double CHAIR_CHANCE = 0.06;      // chance per cell of holding a chair arrangement
    private static final double SIGN_CHANCE = 0.05;      // chance per cell of holding a stop sign
    // small wall/floor details: kept sparse on purpose so walls stay clean
    private static final double OUTLET_CHANCE = 0.07;  // share of solid wall faces (one per face segment) holding an outlet
    private static final double MOUNT_CHANCE = 0.045;  // share of solid wall faces holding an empty mount mark
    private static final double WALL_SIGN_CHANCE = 0.03; // share of solid wall faces holding a sign stuck in the wall
    private static final double DENT_CHANCE = 0.06;    // chance per cell of holding a furniture footprint of carpet dents
    private static final int ZONE_CELLS = 4;         // lights go out per zone of 4x4 cells (24x24 blocks)
    private static final double DARK_ZONE_CHANCE = 0.2; // share of zones whose lights are all out
    private static final double FAULTY_LIGHT_CHANCE = 0.3; // share of lit cells whose lights flicker
    private static final int REGION_SIZE = HALL_REGION * CELL;
    private static final int CEIL_NORMAL = 6;
    private static final int CEIL_TALL = 14;

    public BackroomsChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    @Override
    protected Codec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    // ---------- deterministic hashing ----------

    public static long mix(long x, long z, long salt) {
        long h = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL ^ salt * 0x165667B19E3779F9L;
        h ^= h >>> 32;
        h *= 0xD6E8FEB86659FD93L;
        h ^= h >>> 32;
        h *= 0xD6E8FEB86659FD93L;
        h ^= h >>> 32;
        return h;
    }

    public static double rand01(long h) {
        return (h >>> 11) / (double) (1L << 53);
    }

    // ---------- layout rules ----------

    /** 0 = open, 1 = wall with a doorway, 2 = solid wall, 3 = arched doorway, 4 = framed doorway. */
    private static int baseEdge(int salt, int cx, int cz) {
        double r = rand01(mix(cx, cz, salt));
        if (r < 0.22) return 2;
        if (r < 0.55) {
            double k = rand01(mix(cx, cz, salt + 100));
            if (k < 0.28) return 3; // arched doorway (wide, rounded top)
            if (k < 0.48) return 4; // door frame without a door (dark wood trim)
            return 1;
        }
        return 0;
    }

    private static boolean cellClosed(int cx, int cz) {
        return baseEdge(1, cx, cz) == 2 && baseEdge(1, cx + 1, cz) == 2
            && baseEdge(2, cx, cz) == 2 && baseEdge(2, cx, cz + 1) == 2;
    }

    private static boolean isHall(int rx, int rz) {
        return rand01(mix(rx, rz, 9)) < HALL_CHANCE;
    }

    /**
     * salt 1: wall on x = cx*CELL, between cells (cx-1,cz) and (cx,cz).
     * salt 2: wall on z = cz*CELL, between cells (cx,cz-1) and (cx,cz).
     */
    private static int edgeType(int salt, int cx, int cz) {
        int ax = salt == 1 ? cx - 1 : cx;
        int az = salt == 1 ? cz : cz - 1;

        int rxA = Math.floorDiv(ax, HALL_REGION), rzA = Math.floorDiv(az, HALL_REGION);
        int rxB = Math.floorDiv(cx, HALL_REGION), rzB = Math.floorDiv(cz, HALL_REGION);
        if (rxA == rxB && rzA == rzB && isHall(rxB, rzB)) return 0;

        int base = baseEdge(salt, cx, cz);
        if (base == 2 && (cellClosed(ax, az) || cellClosed(cx, cz))) return 0; // never seal a cell in
        return base;
    }

    /** -1 = ordinary region, otherwise the SetPieces type placed in the middle of this hall. */
    private static int specialType(int rx, int rz) {
        if (!isHall(rx, rz)) return -1;
        if (rand01(mix(rx, rz, 11)) >= SPECIAL_CHANCE) return -1;
        return (int) Math.floorMod(mix(rx, rz, 12), (long) SetPieces.TYPE_COUNT);
    }

    /** Set pieces get a tall room, except the McDonald's, which keeps an ordinary ceiling. */
    private static boolean tallType(int type) {
        return type >= 0 && type != SetPieces.TYPE_MCDONALDS;
    }

    private static int cellSpecialType(int cx, int cz) {
        return specialType(Math.floorDiv(cx, HALL_REGION), Math.floorDiv(cz, HALL_REGION));
    }

    public static int ceilOfCell(int cx, int cz) {
        return tallType(cellSpecialType(cx, cz)) ? CEIL_TALL : CEIL_NORMAL;
    }

    private static boolean hasDoor(int cx, int cz) {
        if (cellSpecialType(cx, cz) >= 0) return false; // set-piece rooms never hold the exit door
        return rand01(mix(cx, cz, 7)) < DOOR_CHANCE;
    }

    /**
     * Chair arrangements: one spot per cell (never on a wall line, never in the exit-door cell or a
     * set-piece room). Poses: upright, fallen over, sunk into the carpet, or a tall stack.
     */
    private static BlockState chairAt(int cx, int cz, int lx, int lz, int y) {
        if (y < 2 || y > 5) return null;
        if (rand01(mix(cx, cz, 20)) >= CHAIR_CHANCE) return null;
        if (cellSpecialType(cx, cz) >= 0 || hasDoor(cx, cz)) return null;
        if (structureAt(cx * CELL + lx, cz * CELL + lz, cx, cz, lx, lz, 2) != null) return null;
        int px = 1 + (int) Math.floorMod(mix(cx, cz, 21), 5L);
        int pz = 1 + (int) Math.floorMod(mix(cx, cz, 22), 5L);
        if (lx != px || lz != pz) return null;

        double kind = rand01(mix(cx, cz, 24));
        if (kind < 0.82) {
            if (y != 2) return null;
            ChairBlock.Pose pose = kind < 0.40 ? ChairBlock.Pose.UPRIGHT
                : kind < 0.62 ? ChairBlock.Pose.FALLEN : ChairBlock.Pose.SUNK;
            return chairState((mix(cx, cz, 23) & 1L) == 0, (int) (mix(cx, cz, 25) & 3), pose);
        }
        // stack of 3 or 4 chairs, each turned and mixed in type
        int height = 3 + (int) (mix(cx, cz, 26) & 1L);
        if (y >= 2 + height) return null;
        return chairState((mix(cx, cz, 27 + y) & 1L) == 0, (int) (mix(cx, cz, 40 + y) & 3),
            ChairBlock.Pose.UPRIGHT);
    }

    private static BlockState chairState(boolean office, int facing, ChairBlock.Pose pose) {
        Block block = office ? ModBlocks.OFFICE_CHAIR.get() : ModBlocks.WOOD_CHAIR.get();
        return block.defaultBlockState()
            .setValue(ChairBlock.FACING, Direction.from2DDataValue(facing))
            .setValue(ChairBlock.POSE, pose);
    }

    /**
     * Stop signs: one spot per cell, random style (tall, short, tilted, sunk, sunk and tilted),
     * facing and lettering (correct or mirrored). A chair on the same spot wins.
     */
    private static BlockState signAt(int cx, int cz, int lx, int lz, int y) {
        if (y < 2 || y > 4) return null;
        if (rand01(mix(cx, cz, 50)) >= SIGN_CHANCE) return null;
        if (cellSpecialType(cx, cz) >= 0 || hasDoor(cx, cz)) return null;
        if (structureAt(cx * CELL + lx, cz * CELL + lz, cx, cz, lx, lz, 2) != null) return null;
        int px = 1 + (int) Math.floorMod(mix(cx, cz, 51), 5L);
        int pz = 1 + (int) Math.floorMod(mix(cx, cz, 52), 5L);
        if (lx != px || lz != pz) return null;

        StopSignBlock.Style[] styles = StopSignBlock.Style.values();
        StopSignBlock.Style style = styles[(int) Math.floorMod(mix(cx, cz, 53), (long) styles.length)];
        boolean mirrored = rand01(mix(cx, cz, 54)) < 0.5;
        if (style != StopSignBlock.Style.TALL && y != 2) return null; // only the tall sign is 3 blocks high
        if (chairAt(cx, cz, lx, lz, 2) != null) return null;          // a chair on the same spot wins
        return ModBlocks.STOP_SIGN.get().defaultBlockState()
            .setValue(StopSignBlock.FACING, Direction.from2DDataValue((int) (mix(cx, cz, 55) & 3)))
            .setValue(StopSignBlock.STYLE, style)
            .setValue(StopSignBlock.MIRRORED, mirrored)
            .setValue(StopSignBlock.PART, style == StopSignBlock.Style.TALL ? y - 2 : 0);
    }


    // ---------- wall outlets, empty mount marks and carpet dents ----------

    /**
     * Details stuck on the solid wall next to this air column: a low outlet (y=2) or an empty mount
     * mark at eye height (y=3). Each wall face segment holds at most one of them, and only a small
     * share of faces hold anything, so most walls stay clean.
     */
    private static BlockState wallDecorAt(int cx, int cz, int lx, int lz, int y) {
        if (y != 2 && y != 3) return null;
        if (cellSpecialType(cx, cz) >= 0 || hasDoor(cx, cz)) return null;
        BlockState s = null;
        if (lx == 1) s = faceDecor(1, cx, cz, 0, lz, y, Direction.EAST);
        if (s == null && lx == 5) s = faceDecor(1, cx + 1, cz, 1, lz, y, Direction.WEST);
        if (s == null && lz == 1) s = faceDecor(2, cx, cz, 0, lx, y, Direction.SOUTH);
        if (s == null && lz == 5) s = faceDecor(2, cx, cz + 1, 1, lx, y, Direction.NORTH);
        return s;
    }

    /** side 0 = the face looking at the edge's own cell, side 1 = the face looking back at the previous cell. */
    private static BlockState faceDecor(int salt, int ecx, int ecz, int side, int el, int y, Direction facing) {
        if (edgeType(salt, ecx, ecz) != 2) return null; // only plain solid walls
        long key = ecx * 2L + side;
        double r = rand01(mix(key, ecz, 300 + salt));
        if (r < OUTLET_CHANCE) {
            if (y != 2) return null;
            if (el != 1 + (int) Math.floorMod(mix(key, ecz, 310 + salt), 5L)) return null;
            return WallDecor.OUTLET.defaultBlockState().setValue(DecalBlock.FACING, facing);
        }
        if (r < OUTLET_CHANCE + MOUNT_CHANCE) {
            if (y != 3) return null;
            if (el != 2 + (int) Math.floorMod(mix(key, ecz, 320 + salt), 3L)) return null;
            return WallDecor.MOUNT_MARK.defaultBlockState().setValue(DecalBlock.FACING, facing);
        }
        if (r < OUTLET_CHANCE + MOUNT_CHANCE + WALL_SIGN_CHANCE) {
            if (y != 3) return null;
            if (el != 2 + (int) Math.floorMod(mix(key, ecz, 340 + salt), 3L)) return null;
            WallSignBlock.Variant[] variants = WallSignBlock.Variant.values();
            return WallDecor.WALL_SIGN.defaultBlockState()
                .setValue(WallSignBlock.FACING, facing)
                .setValue(WallSignBlock.VARIANT, variants[(int) Math.floorMod(mix(key, ecz, 350 + salt), (long) variants.length)])
                .setValue(WallSignBlock.MIRRORED, rand01(mix(key, ecz, 360 + salt)) < 0.5);
        }
        return null;
    }

    /**
     * Four dents in the carpet at the corners of a 2-3 x 2-3 block footprint, as if a table or a
     * cabinet once stood there. FACING picks which corner of the footprint the dent belongs to.
     */
    private static BlockState dentAt(int cx, int cz, int lx, int lz, int y) {
        if (y != 2) return null;
        if (rand01(mix(cx, cz, 330)) >= DENT_CHANCE) return null;
        if (cellSpecialType(cx, cz) >= 0 || hasDoor(cx, cz)) return null;
        int w = 2 + (int) (mix(cx, cz, 331) & 1L);
        int d = 2 + (int) (mix(cx, cz, 332) & 1L);
        int px = 1 + (int) Math.floorMod(mix(cx, cz, 333), (long) (6 - w));
        int pz = 1 + (int) Math.floorMod(mix(cx, cz, 334), (long) (6 - d));
        boolean west = lx == px, east = lx == px + w - 1;
        boolean north = lz == pz, south = lz == pz + d - 1;
        Direction f;
        if (north && west) f = Direction.NORTH;
        else if (north && east) f = Direction.EAST;
        else if (south && east) f = Direction.SOUTH;
        else if (south && west) f = Direction.WEST;
        else return null;
        return WallDecor.CARPET_DENT.defaultBlockState().setValue(DecalBlock.FACING, f);
    }

    // ---------- lights: dark zones and flickering (used by LightFlicker) ----------

    private static boolean zoneDark(int cx, int cz) {
        return rand01(mix(Math.floorDiv(cx, ZONE_CELLS), Math.floorDiv(cz, ZONE_CELLS), 60)) < DARK_ZONE_CHANCE;
    }

    /** True when the cell holds ceiling lights. */
    public static boolean lightCell(int cx, int cz) {
        return hasLight(cx, cz, 2, 2);
    }

    /** Lights that are on when the chunk is first generated. */
    private static boolean litAtGen(int cx, int cz) {
        return hasDoor(cx, cz) || !zoneDark(cx, cz);
    }

    /** True when a neighbouring zone is dark, so the surviving lights here hum louder. */
    public static boolean nearDarkZone(int cx, int cz) {
        int zx = Math.floorDiv(cx, ZONE_CELLS), zz = Math.floorDiv(cz, ZONE_CELLS);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if ((dx != 0 || dz != 0) && rand01(mix(zx + dx, zz + dz, 60)) < DARK_ZONE_CHANCE) return true;
            }
        }
        return false;
    }

    /** Whether this cell's lights should be on at game time t (stateless, so every chunk agrees). */
    public static boolean lightLitAt(int cx, int cz, long t) {
        if (hasDoor(cx, cz)) return true; // the exit stays lit
        if (zoneDark(cx, cz)) {
            // a dead zone: now and then one light sputters back on for a moment
            if (rand01(mix(cx, cz, 2000 + t / 200)) < 0.04) {
                return rand01(mix(cx, cz, 6000 + t / 3)) > 0.6;
            }
            return false;
        }
        if (rand01(mix(cx, cz, 70)) >= FAULTY_LIGHT_CHANCE) return true; // healthy light
        if (rand01(mix(cx, cz, 1000 + t / 100)) >= 0.35) return true;     // no fit this time
        return rand01(mix(cx, cz, 5000 + t / 3)) > 0.45;                  // flickering burst
    }

    // ---------- damaged walls and wet carpet ----------

    private static boolean dampZone(int x, int z) {
        return rand01(mix(Math.floorDiv(x, 24), Math.floorDiv(z, 24), 61)) < 0.3;
    }

    /**
     * Wall wear is rare and clustered: most walls are clean wallpaper. Only a few small patches
     * (about 8 x 8 blocks, ~14% of the map) are worn, and inside a patch only a minority of blocks
     * are marked, so damage reads as "this corner is old" instead of noise on every wall.
     */
    private static boolean wornPatch(int x, int z) {
        return rand01(mix(Math.floorDiv(x, 8), Math.floorDiv(z, 8), 63)) < 0.14;
    }

    private static BlockState wallBlock(int x, int y, int z) {
        boolean worn = wornPatch(x, z);
        boolean damp = dampZone(x, z);
        double pStain = worn ? (y >= 4 ? 0.10 : 0.03) : 0.002;
        double pMold = worn ? (y <= 2 && damp ? 0.14 : 0.01) : 0.0;
        double pPeel = worn ? 0.05 : 0.002;
        double pScratch = worn ? 0.03 : 0.002;
        double r = rand01(mix(x, z * 16L + y, 62));
        if (r < pStain) return ModBlocks.WALLPAPER_STAINED.get().defaultBlockState();
        r -= pStain;
        if (r < pMold) return ModBlocks.WALLPAPER_MOLDY.get().defaultBlockState();
        r -= pMold;
        if (r < pPeel) return ModBlocks.WALLPAPER_PEELING.get().defaultBlockState();
        r -= pPeel;
        if (r < pScratch) return ModBlocks.WALLPAPER_SCRATCHED.get().defaultBlockState();
        if (y == 3 && rand01(mix(Math.floorDiv(x, 18), Math.floorDiv(z, 18), 67)) < 0.45) {
            return ModBlocks.WALLPAPER_TRIM.get().defaultBlockState(); // wooden chair rail
        }
        return ModBlocks.WALLPAPER.get().defaultBlockState();
    }

    // ---------- ceiling decorations, floor light patches, hall structures ----------

    private static BlockState ceilingDecor(int x, int z, BlockState plain) {
        double r = rand01(mix(x, z, 80));
        if (r < 0.012) return ModBlocks.CEILING_HOLE.get().defaultBlockState();
        if (r < 0.022) return ModBlocks.CEILING_VENT.get().defaultBlockState();
        return plain;
    }

    /** A 2x1 patch of brighter carpet, as if light fell through a hole in the ceiling. */
    private static boolean litPatchAt(int cx, int cz, int lx, int lz) {
        if (rand01(mix(cx, cz, 81)) >= 0.05) return false;
        int px = 1 + (int) Math.floorMod(mix(cx, cz, 82), 3L);
        int pz = 1 + (int) Math.floorMod(mix(cx, cz, 83), 4L);
        return lz == pz && (lx == px || lx == px + 1);
    }

    /** 0 plain, 1 pillar grid, 2 half-wall partitions, 3 long balustrade railing. */
    private static int hallStyle(int rx, int rz) {
        double r = rand01(mix(rx, rz, 90));
        if (r < 0.25) return 0;
        if (r < 0.55) return 1;
        if (r < 0.80) return 2;
        return 3;
    }

    /** Furniture-like structures inside ordinary halls (never in set-piece rooms or the door cell). */
    private static BlockState structureAt(int x, int z, int cx, int cz, int lx, int lz, int y) {
        if (y < 2 || y > 5) return null;
        int rx = Math.floorDiv(cx, HALL_REGION), rz = Math.floorDiv(cz, HALL_REGION);
        if (!isHall(rx, rz) || specialType(rx, rz) >= 0 || hasDoor(cx, cz)) return null;
        switch (hallStyle(rx, rz)) {
            case 1:
                if (lx >= 3 && lx <= 4 && lz >= 3 && lz <= 4 && rand01(mix(cx, cz, 91)) < 0.7) {
                    return wallBlock(x, y, z); // thick 2x2 pillar reaching the ceiling
                }
                return null;
            case 2: {
                if (y != 2 || rand01(mix(cx, cz, 92)) >= 0.55) return null;
                int p = 2 + (int) Math.floorMod(mix(cx, cz, 93), 3L);
                boolean alongX = (mix(cx, cz, 94) & 1L) == 0;
                boolean hit = alongX ? (lz == p && lx >= 1 && lx <= 4) : (lx == p && lz >= 1 && lz <= 4);
                return hit ? ModBlocks.HALF_WALL.get().defaultBlockState() : null;
            }
            case 3: {
                if (y != 2) return null;
                boolean alongX = (mix(rx, rz, 95) & 1L) == 0;
                int hx = Math.floorMod(x, REGION_SIZE), hz = Math.floorMod(z, REGION_SIZE);
                int along = alongX ? hx : hz, across = alongX ? hz : hx;
                if (across != REGION_SIZE / 2 || along < 2 || along > 15 || along == 8 || along == 9) return null;
                return ModBlocks.BALUSTRADE.get().defaultBlockState()
                    .setValue(BalustradeBlock.AXIS, alongX ? Direction.Axis.X : Direction.Axis.Z);
            }
            default:
                return null;
        }
    }

    private static BlockState archCorner(int esalt, int el) {
        Direction facing = esalt == 1 ? (el == 1 ? Direction.SOUTH : Direction.NORTH)
                                      : (el == 1 ? Direction.EAST : Direction.WEST);
        return ModBlocks.ARCH_CORNER.get().defaultBlockState().setValue(ArchCornerBlock.FACING, facing);
    }

    /** Puddles of soaked carpet: round blobs of radius 1-2.3 scattered per 5x5 tile. */
    private static boolean wetCarpetAt(int x, int z) {
        int tx0 = Math.floorDiv(x, 5), tz0 = Math.floorDiv(z, 5);
        for (int tx = tx0 - 1; tx <= tx0 + 1; tx++) {
            for (int tz = tz0 - 1; tz <= tz0 + 1; tz++) {
                double chance = dampZone(tx * 5, tz * 5) ? 0.45 : 0.12;
                if (rand01(mix(tx, tz, 63)) >= chance) continue;
                double bx = tx * 5 + 0.5 + rand01(mix(tx, tz, 64)) * 4;
                double bz = tz * 5 + 0.5 + rand01(mix(tx, tz, 65)) * 4;
                double rad = 1.0 + rand01(mix(tx, tz, 66)) * 1.3;
                double dx = x + 0.5 - bx, dz = z + 0.5 - bz;
                if (dx * dx + dz * dz <= rad * rad) return true;
            }
        }
        return false;
    }

    private static boolean hasLight(int cx, int cz, int lx, int lz) {
        if (lx < 2 || lx > 3 || lz < 2 || lz > 3) return false;
        if (hasDoor(cx, cz)) return true;
        if (cellSpecialType(cx, cz) == SetPieces.TYPE_MCDONALDS) return true; // the restaurant is brightly lit
        return rand01(mix(cx, cz, 3)) < 0.55;
    }

    // ---------- generation ----------

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender,
            RandomState randomState, StructureManager structureManager, ChunkAccess chunk) {
        BlockState bedrock = Blocks.BEDROCK.defaultBlockState();
        BlockState carpet = ModBlocks.CARPET.get().defaultBlockState();
        BlockState wallpaper = ModBlocks.WALLPAPER.get().defaultBlockState();
        BlockState ceiling = ModBlocks.CEILING_TILE.get().defaultBlockState();
        BlockState lightPanel = ModBlocks.LIGHT_PANEL.get().defaultBlockState();
        BlockState lightOff = lightPanel.setValue(LightPanelBlock.LIT, Boolean.FALSE);
        BlockState wetCarpet = ModBlocks.CARPET_WET.get().defaultBlockState();
        BlockState litCarpet = ModBlocks.CARPET_LIT.get().defaultBlockState();
        BlockState frame = ModBlocks.DOOR_FRAME.get().defaultBlockState();
        BlockState window = ModBlocks.WINDOW.get().defaultBlockState();

        Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();

        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = minX + dx;
                int z = minZ + dz;
                int cx = Math.floorDiv(x, CELL);
                int cz = Math.floorDiv(z, CELL);
                int lx = Math.floorMod(x, CELL);
                int lz = Math.floorMod(z, CELL);
                int rx = Math.floorDiv(cx, HALL_REGION);
                int rz = Math.floorDiv(cz, HALL_REGION);

                int type = specialType(rx, rz);
                int ownCeil = tallType(type) ? CEIL_TALL : CEIL_NORMAL;
                boolean lineColumn = lx == 0 || lz == 0;
                boolean post = lx == 0 && lz == 0;

                // walls and openings on the boundary of a tall room reach up to that room's ceiling
                int maxCeil = ownCeil;
                if (lx == 0) maxCeil = Math.max(maxCeil, ceilOfCell(cx - 1, cz));
                if (lz == 0) maxCeil = Math.max(maxCeil, ceilOfCell(cx, cz - 1));
                if (post) maxCeil = Math.max(maxCeil, ceilOfCell(cx - 1, cz - 1));

                boolean wall;
                if (post) {
                    wall = true; // corner post
                } else if (lx == 0) {
                    int t = edgeType(1, cx, cz);
                    wall = t == 2 || (t != 0 && !(lz == 2 || lz == 3));
                } else if (lz == 0) {
                    int t = edgeType(2, cx, cz);
                    wall = t == 2 || (t != 0 && !(lx == 2 || lx == 3));
                } else {
                    wall = false;
                }

                int et = 0, el = 0, esalt = 0;
                if (!post) {
                    if (lx == 0) { esalt = 1; et = edgeType(1, cx, cz); el = lz; }
                    else if (lz == 0) { esalt = 2; et = edgeType(2, cx, cz); el = lx; }
                }
                boolean archEdge = et == 3, frameEdge = et == 4;
                boolean windowEdge = et == 2 && el >= 1 && el <= 4 && rand01(mix(cx, cz, esalt + 200)) < 0.16;

                boolean doorHere = hasDoor(cx, cz) && lx == CELL / 2 && lz == CELL / 2;
                boolean light = hasLight(cx, cz, lx, lz);
                Direction doorFacing = Direction.from2DDataValue((int) (mix(cx, cz, 8) & 3));

                boolean inPiece = false;
                int pdx = 0, pdz = 0;
                if (type >= 0) {
                    pdx = x - (rx * REGION_SIZE + REGION_SIZE / 2);
                    pdz = z - (rz * REGION_SIZE + REGION_SIZE / 2);
                    inPiece = Math.abs(pdx) <= SetPieces.halfX(type) && Math.abs(pdz) <= SetPieces.halfZ(type);
                }

                for (int y = 0; y < 16; y++) {
                    BlockState state;
                    boolean w = wall;
                    BlockState edgeState = null;
                    if (archEdge && (el == 1 || el == 4)) {
                        if (y == 2 || y == 3) w = false;              // wider opening
                        else if (y == 4) edgeState = archCorner(esalt, el); // rounded shoulder
                    } else if (frameEdge) {
                        if ((el == 1 || el == 4) && y >= 2 && y <= 4) edgeState = frame;
                        else if ((el == 2 || el == 3) && y >= 5) { w = true; if (y == 5) edgeState = frame; }
                    }
                    if (windowEdge && w && (y == 4 || y == 5)) edgeState = window;
                    BlockState pieceState = (inPiece && y >= 1) ? SetPieces.get(type, pdx, y, pdz) : null;
                    if (y == 0) {
                        state = bedrock;
                    } else if (pieceState != null) {
                        state = pieceState;
                    } else if (y == 1) {
                        state = wetCarpetAt(x, z) ? wetCarpet : litPatchAt(cx, cz, lx, lz) ? litCarpet : carpet;
                    } else if (w && y < maxCeil) {
                        // pillars in tall rooms glow so the floor is not pitch dark
                        state = edgeState != null ? edgeState
                            : (post && tallType(type) && y == 3) ? lightPanel : wallBlock(x, y, z);
                    } else if (!w && y < ownCeil) {
                        if (doorHere && y == 2) {
                            state = ModBlocks.EXIT_DOOR.get().defaultBlockState()
                                .setValue(DoorBlock.FACING, doorFacing)
                                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
                        } else if (doorHere && y == 3) {
                            state = ModBlocks.EXIT_DOOR.get().defaultBlockState()
                                .setValue(DoorBlock.FACING, doorFacing)
                                .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
                        } else {
                            BlockState chair = structureAt(x, z, cx, cz, lx, lz, y);
                            if (chair == null) chair = chairAt(cx, cz, lx, lz, y);
                            if (chair == null) chair = signAt(cx, cz, lx, lz, y);
                            if (chair == null) chair = wallDecorAt(cx, cz, lx, lz, y);
                            if (chair == null) chair = dentAt(cx, cz, lx, lz, y);
                            if (chair == null) continue; // air
                            state = chair;
                        }
                    } else if (!w && y == ownCeil) {
                        state = light ? (litAtGen(cx, cz) ? lightPanel : lightOff) : ceilingDecor(x, z, ceiling);
                    } else if (y == maxCeil) {
                        state = ceiling;
                    } else if (!w && lineColumn && y > ownCeil && y < maxCeil) {
                        state = wallBlock(x, y, z);
                    } else {
                        state = bedrock;
                    }
                    chunk.setBlockState(pos.set(x, y, z), state, false);
                }
                oceanFloor.update(dx, 15, dz, bedrock);
                worldSurface.update(dx, 15, dz, bedrock);
            }
        }
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState,
            BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunk,
            GenerationStep.Carving step) {
        // no caves
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structureManager,
            RandomState randomState, ChunkAccess chunk) {
        // surface already built in fillFromNoise
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        // no natural mob spawns
    }

    @Override
    public int getGenDepth() {
        return 16;
    }

    @Override
    public int getSeaLevel() {
        return 0;
    }

    @Override
    public int getMinY() {
        return 0;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level,
            RandomState randomState) {
        return 2;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
        return new NoiseColumn(0, new BlockState[] {
            Blocks.BEDROCK.defaultBlockState(),
            ModBlocks.CARPET.get().defaultBlockState()
        });
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState randomState, BlockPos pos) {
        // nothing
    }
}
