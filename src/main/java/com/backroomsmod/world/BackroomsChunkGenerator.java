package com.backroomsmod.world;

import com.backroomsmod.ModBlocks;
import com.backroomsmod.block.ChairBlock;
import com.backroomsmod.block.StopSignBlock;
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

    private static long mix(long x, long z, long salt) {
        long h = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL ^ salt * 0x165667B19E3779F9L;
        h ^= h >>> 32;
        h *= 0xD6E8FEB86659FD93L;
        h ^= h >>> 32;
        h *= 0xD6E8FEB86659FD93L;
        h ^= h >>> 32;
        return h;
    }

    private static double rand01(long h) {
        return (h >>> 11) / (double) (1L << 53);
    }

    // ---------- layout rules ----------

    /** 0 = open, 1 = wall with a doorway, 2 = solid wall. */
    private static int baseEdge(int salt, int cx, int cz) {
        double r = rand01(mix(cx, cz, salt));
        if (r < 0.22) return 2;
        if (r < 0.55) return 1;
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

    private static int ceilOfCell(int cx, int cz) {
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
        if (y != 2) return null;
        if (rand01(mix(cx, cz, 50)) >= SIGN_CHANCE) return null;
        if (cellSpecialType(cx, cz) >= 0 || hasDoor(cx, cz)) return null;
        int px = 1 + (int) Math.floorMod(mix(cx, cz, 51), 5L);
        int pz = 1 + (int) Math.floorMod(mix(cx, cz, 52), 5L);
        if (lx != px || lz != pz) return null;

        StopSignBlock.Style[] styles = StopSignBlock.Style.values();
        StopSignBlock.Style style = styles[(int) Math.floorMod(mix(cx, cz, 53), (long) styles.length)];
        boolean mirrored = rand01(mix(cx, cz, 54)) < 0.5;
        return ModBlocks.STOP_SIGN.get().defaultBlockState()
            .setValue(StopSignBlock.FACING, Direction.from2DDataValue((int) (mix(cx, cz, 55) & 3)))
            .setValue(StopSignBlock.STYLE, style)
            .setValue(StopSignBlock.MIRRORED, mirrored);
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
                    wall = t == 2 || (t == 1 && !(lz == 2 || lz == 3));
                } else if (lz == 0) {
                    int t = edgeType(2, cx, cz);
                    wall = t == 2 || (t == 1 && !(lx == 2 || lx == 3));
                } else {
                    wall = false;
                }

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
                    BlockState pieceState = (inPiece && y >= 1) ? SetPieces.get(type, pdx, y, pdz) : null;
                    if (y == 0) {
                        state = bedrock;
                    } else if (pieceState != null) {
                        state = pieceState;
                    } else if (y == 1) {
                        state = carpet;
                    } else if (wall && y < maxCeil) {
                        // pillars in tall rooms glow so the floor is not pitch dark
                        state = (post && tallType(type) && y == 3) ? lightPanel : wallpaper;
                    } else if (!wall && y < ownCeil) {
                        if (doorHere && y == 2) {
                            state = ModBlocks.EXIT_DOOR.get().defaultBlockState()
                                .setValue(DoorBlock.FACING, doorFacing)
                                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
                        } else if (doorHere && y == 3) {
                            state = ModBlocks.EXIT_DOOR.get().defaultBlockState()
                                .setValue(DoorBlock.FACING, doorFacing)
                                .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
                        } else {
                            BlockState chair = chairAt(cx, cz, lx, lz, y);
                            if (chair == null) chair = signAt(cx, cz, lx, lz, y);
                            if (chair == null) continue; // air
                            state = chair;
                        }
                    } else if (!wall && y == ownCeil) {
                        state = light ? lightPanel : ceiling;
                    } else if (y == maxCeil) {
                        state = ceiling;
                    } else if (!wall && lineColumn && y > ownCeil && y < maxCeil) {
                        state = wallpaper;
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
