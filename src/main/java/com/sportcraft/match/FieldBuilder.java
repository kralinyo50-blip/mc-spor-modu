package com.sportcraft.match;

import com.sportcraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.Set;

/**
 * One-click stadium constructor used by the "Sahayı Kur" button.
 *
 * <p>Builds, around the clicked centre block: a striped artificial-turf pitch
 * with full white markings (border, halfway line, centre circle, penalty and
 * goal boxes), two multi-block post-and-net goals with invisible goal sensors,
 * an athletics track ring, advertising boards, corner flags, four floodlight
 * masts, two dugouts, terraced red/blue stands with chair seats and a
 * scoreboard block.</p>
 *
 * <p>Only air, replaceable blocks and common natural ground blocks are
 * overwritten, so an existing structure next to the stadium is preserved.</p>
 */
public final class FieldBuilder {

    private static final Set<Block> GROUND_LIKE = Set.of(
            Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.PODZOL,
            Blocks.ROOTED_DIRT, Blocks.SAND, Blocks.RED_SAND, Blocks.GRAVEL,
            Blocks.MOSS_BLOCK, Blocks.MUD, Blocks.RED_CONCRETE,
            ModBlocks.ARTIFICIAL_TURF.get(), ModBlocks.ARTIFICIAL_TURF_DARK.get(),
            ModBlocks.FIELD_LINE.get());

    private static final int CIRCLE_RADIUS = 4;
    private static final int PENALTY_DEPTH = 6;
    private static final int PENALTY_HALF_WIDTH = 6;
    private static final int GOAL_AREA_DEPTH = 2;
    private static final int GOAL_AREA_HALF_WIDTH = 3;
    private static final int GOAL_HALF_WIDTH = 2;
    private static final int GOAL_HEIGHT = 3;
    private static final int GOAL_DEPTH = 2;
    private static final int TRACK_WIDTH = 3;
    private static final int STAND_ROWS = 4;
    private static final int STAND_HALF_WIDTH = 8;
    private static final int FLOODLIGHT_HEIGHT = 12;

    private FieldBuilder() {
    }

    public static FieldBuildResult build(ServerLevel level, BlockPos center, FieldSize size) {
        int halfW = size.getWidth() / 2;
        int halfL = size.getLength() / 2;
        int groundY = center.getY() - 1;
        int playY = center.getY();
        int placed = 0;

        BlockState turfLight = ModBlocks.ARTIFICIAL_TURF.get().defaultBlockState();
        BlockState turfDark = ModBlocks.ARTIFICIAL_TURF_DARK.get().defaultBlockState();
        BlockState line = ModBlocks.FIELD_LINE.get().defaultBlockState();
        BlockState track = Blocks.RED_CONCRETE.defaultBlockState();
        BlockState post = ModBlocks.GOAL_POST.get().defaultBlockState();
        BlockState net = ModBlocks.GOAL_NET.get().defaultBlockState();

        int margin = TRACK_WIDTH + 2;
        for (int x = -halfW - margin; x <= halfW + margin; x++) {
            for (int z = -halfL - margin; z <= halfL + margin; z++) {
                BlockPos groundPos = new BlockPos(center.getX() + x, groundY, center.getZ() + z);
                if (canReplaceGround(level.getBlockState(groundPos))) {
                    BlockState ground;
                    if (Math.abs(x) <= halfW && Math.abs(z) <= halfL) {
                        ground = ((x / 2) & 1) == 0 ? turfLight : turfDark;
                    } else if (Math.abs(x) <= halfW + TRACK_WIDTH && Math.abs(z) <= halfL + TRACK_WIDTH) {
                        ground = track;
                    } else {
                        continue;
                    }
                    level.setBlock(groundPos, ground, Block.UPDATE_ALL);
                    placed++;
                }
                BlockPos playPos = new BlockPos(center.getX() + x, playY, center.getZ() + z);
                if (level.getBlockState(playPos).canBeReplaced()
                        && Math.abs(x) <= halfW && Math.abs(z) <= halfL) {
                    level.setBlock(playPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    if (isMarking(x, z, halfW, halfL) && !isGoalMouth(x, z, halfL)) {
                        level.setBlock(playPos, line, Block.UPDATE_ALL);
                        placed++;
                    }
                }
            }
        }

        BlockPos redGoalPos = new BlockPos(center.getX(), playY, center.getZ() - halfL);
        BlockPos blueGoalPos = new BlockPos(center.getX(), playY, center.getZ() + halfL);
        placed += buildGoal(level, center, redGoalPos, -1, playY, post, net,
                ModBlocks.RED_GOAL.get().defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
        placed += buildGoal(level, center, blueGoalPos, 1, playY, post, net,
                ModBlocks.BLUE_GOAL.get().defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));

        placed += buildAdvertisingBoards(level, center, halfW, halfL, playY);
        placed += buildCornerFlags(level, center, halfW, halfL, playY);
        placed += buildFloodlights(level, center, halfW, halfL, groundY, playY);
        placed += buildDugouts(level, center, halfW, halfL, groundY, playY);
        placed += buildStands(level, center, halfW, halfL, groundY, playY);

        BlockPos scoreboardPos = new BlockPos(center.getX() + halfW + TRACK_WIDTH + 1, playY, center.getZ());
        if (level.getBlockState(scoreboardPos).canBeReplaced()) {
            level.setBlock(scoreboardPos, ModBlocks.SCOREBOARD.get().defaultBlockState(), Block.UPDATE_ALL);
            placed++;
        }

        return new FieldBuildResult(center, size, redGoalPos, blueGoalPos, placed);
    }

    /**
     * Multi-block goal: white posts and crossbar around a 5x3 mouth, netting on
     * the back, sides and top, plus the invisible sensor at the mouth centre.
     *
     * @param zDirection -1 for the north goal, +1 for the south goal.
     */
    private static int buildGoal(ServerLevel level, BlockPos center, BlockPos mouth, int zDirection,
                                 int playY, BlockState post, BlockState net, BlockState sensor) {
        int count = 0;
        int cx = mouth.getX();
        int lineZ = mouth.getZ();
        int backZ = lineZ + zDirection * (GOAL_DEPTH - 1);

        // Two post columns and the crossbar across the mouth.
        for (int y = playY; y < playY + GOAL_HEIGHT; y++) {
            count += placeIfReplaceable(level, new BlockPos(cx - GOAL_HALF_WIDTH, y, lineZ), post);
            count += placeIfReplaceable(level, new BlockPos(cx + GOAL_HALF_WIDTH, y, lineZ), post);
        }
        for (int x = cx - GOAL_HALF_WIDTH; x <= cx + GOAL_HALF_WIDTH; x++) {
            count += placeIfReplaceable(level, new BlockPos(x, playY + GOAL_HEIGHT - 1, lineZ), post);
        }

        // Back wall, side walls and roof made of net blocks.
        for (int x = cx - GOAL_HALF_WIDTH; x <= cx + GOAL_HALF_WIDTH; x++) {
            for (int y = playY; y < playY + GOAL_HEIGHT; y++) {
                count += placeIfReplaceable(level, new BlockPos(x, y, backZ), net);
            }
            count += placeIfReplaceable(level,
                    new BlockPos(x, playY + GOAL_HEIGHT - 1, backZ), net);
        }
        for (int z = lineZ + zDirection; z != backZ + zDirection; z += zDirection) {
            for (int y = playY; y < playY + GOAL_HEIGHT - 1; y++) {
                count += placeIfReplaceable(level, new BlockPos(cx - GOAL_HALF_WIDTH, y, z), net);
                count += placeIfReplaceable(level, new BlockPos(cx + GOAL_HALF_WIDTH, y, z), net);
            }
        }

        // Invisible ball-detection sensor on the goal line.
        count += placeIfReplaceable(level, mouth, sensor);
        return count;
    }

    private static int buildAdvertisingBoards(ServerLevel level, BlockPos center, int halfW,
                                              int halfL, int playY) {
        int count = 0;
        BlockState[] boards = {
                Blocks.RED_CONCRETE.defaultBlockState(),
                Blocks.BLUE_CONCRETE.defaultBlockState(),
                Blocks.BLACK_CONCRETE.defaultBlockState(),
                Blocks.YELLOW_CONCRETE.defaultBlockState()
        };
        int boardZ = halfL + 1;
        int boardX = halfW + 1;
        for (int x = -halfW; x <= halfW; x++) {
            if (Math.abs(x) <= GOAL_HALF_WIDTH + 1) {
                continue;
            }
            BlockState board = boards[Math.floorMod(x, boards.length)];
            count += placeIfReplaceable(level, new BlockPos(center.getX() + x, playY, center.getZ() - boardZ), board);
            count += placeIfReplaceable(level, new BlockPos(center.getX() + x, playY, center.getZ() + boardZ), board);
        }
        for (int z = -halfL; z <= halfL; z++) {
            BlockState board = boards[Math.floorMod(z, boards.length)];
            count += placeIfReplaceable(level, new BlockPos(center.getX() - boardX, playY, center.getZ() + z), board);
            count += placeIfReplaceable(level, new BlockPos(center.getX() + boardX, playY, center.getZ() + z), board);
        }
        return count;
    }

    private static int buildCornerFlags(ServerLevel level, BlockPos center, int halfW,
                                        int halfL, int playY) {
        int count = 0;
        BlockState fence = Blocks.OAK_FENCE.defaultBlockState();
        BlockState flag = Blocks.YELLOW_WOOL.defaultBlockState();
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                int x = center.getX() + sx * halfW;
                int z = center.getZ() + sz * halfL;
                // Flags intentionally replace the border-line carpet at the corner.
                level.setBlock(new BlockPos(x, playY, z), fence, Block.UPDATE_ALL);
                level.setBlock(new BlockPos(x, playY + 1, z), fence, Block.UPDATE_ALL);
                count += placeIfReplaceable(level, new BlockPos(x, playY + 2, z), flag);
                count += 2;
            }
        }
        return count;
    }

    private static int buildFloodlights(ServerLevel level, BlockPos center, int halfW,
                                        int halfL, int groundY, int playY) {
        int count = 0;
        BlockState pole = Blocks.IRON_BLOCK.defaultBlockState();
        BlockState lamp = Blocks.SEA_LANTERN.defaultBlockState();
        BlockState cap = Blocks.BLACK_CONCRETE.defaultBlockState();
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                int x = center.getX() + sx * (halfW + TRACK_WIDTH + 1);
                int z = center.getZ() + sz * (halfL + TRACK_WIDTH + 1);
                for (int y = playY; y < playY + FLOODLIGHT_HEIGHT; y++) {
                    count += placeIfReplaceable(level, new BlockPos(x, y, z), pole);
                }
                int topY = playY + FLOODLIGHT_HEIGHT;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        count += placeIfReplaceable(level, new BlockPos(x + dx, topY, z + dz), lamp);
                    }
                }
                count += placeIfReplaceable(level, new BlockPos(x, topY + 1, z), cap);
            }
        }
        return count;
    }

    private static int buildDugouts(ServerLevel level, BlockPos center, int halfW,
                                    int halfL, int groundY, int playY) {
        int count = 0;
        BlockState wall = Blocks.GRAY_CONCRETE.defaultBlockState();
        BlockState roof = Blocks.GLASS.defaultBlockState();
        int x = center.getX() + halfW + TRACK_WIDTH;
        for (int baseZ = -7; baseZ <= 7; baseZ += 14) {
            for (int dz = -2; dz <= 2; dz++) {
                count += placeIfReplaceable(level, new BlockPos(x, playY, center.getZ() + baseZ + dz), wall);
                count += placeIfReplaceable(level, new BlockPos(x, playY + 1, center.getZ() + baseZ + dz), wall);
                count += placeIfReplaceable(level, new BlockPos(x, playY + 2, center.getZ() + baseZ + dz), roof);
            }
        }
        return count;
    }

    private static int buildStands(ServerLevel level, BlockPos center, int halfW,
                                   int halfL, int groundY, int playY) {
        int count = 0;
        BlockState support = Blocks.STONE.defaultBlockState();
        BlockState redSeat = ModBlocks.RED_SEAT.get().defaultBlockState();
        BlockState blueSeat = ModBlocks.BLUE_SEAT.get().defaultBlockState();
        for (int side = -1; side <= 1; side += 2) {
            BlockState seat = side < 0 ? redSeat : blueSeat;
            for (int row = 0; row < STAND_ROWS; row++) {
                int z = center.getZ() + side * (halfL + TRACK_WIDTH + 1 + row);
                int seatY = playY + row;
                for (int x = center.getX() - STAND_HALF_WIDTH; x <= center.getX() + STAND_HALF_WIDTH; x++) {
                    for (int supportY = playY; supportY < seatY; supportY++) {
                        count += placeIfReplaceable(level, new BlockPos(x, supportY, z), support);
                    }
                    count += placeIfReplaceable(level, new BlockPos(x, seatY, z), seat);
                }
            }
        }
        return count;
    }

    private static boolean isGoalMouth(int x, int z, int halfL) {
        return Math.abs(x) <= GOAL_HALF_WIDTH && Math.abs(z) == halfL;
    }

    private static boolean isMarking(int x, int z, int halfW, int halfL) {
        if (Math.abs(x) == halfW || Math.abs(z) == halfL || z == 0) {
            return true;
        }
        double distance = Math.sqrt(x * x + z * z);
        if (Math.abs(distance - CIRCLE_RADIUS) < 1.05D || (x == 0 && z == 0)) {
            return true;
        }
        return isBoxOutline(x, z, halfL, PENALTY_DEPTH, PENALTY_HALF_WIDTH, true)
                || isBoxOutline(x, z, halfL, PENALTY_DEPTH, PENALTY_HALF_WIDTH, false)
                || isBoxOutline(x, z, halfL, GOAL_AREA_DEPTH, GOAL_AREA_HALF_WIDTH, true)
                || isBoxOutline(x, z, halfL, GOAL_AREA_DEPTH, GOAL_AREA_HALF_WIDTH, false);
    }

    private static boolean isBoxOutline(int x, int z, int halfL, int depth, int halfWidth, boolean north) {
        int innerZ = north ? -halfL + depth : halfL - depth;
        int goalLineZ = north ? -halfL : halfL;
        boolean insideX = Math.abs(x) <= halfWidth;
        boolean withinDepth = north
                ? z >= goalLineZ && z <= innerZ
                : z <= goalLineZ && z >= innerZ;
        if (!insideX || !withinDepth) {
            return false;
        }
        return Math.abs(x) == halfWidth || z == innerZ;
    }

    private static int placeIfReplaceable(ServerLevel level, BlockPos pos, BlockState state) {
        if (level.getBlockState(pos).canBeReplaced()) {
            level.setBlock(pos, state, Block.UPDATE_ALL);
            return 1;
        }
        return 0;
    }

    private static boolean canReplaceGround(BlockState state) {
        return state.canBeReplaced() || GROUND_LIKE.contains(state.getBlock());
    }

    /**
     * Result of a successful build: where the goals ended up and how many
     * blocks were placed, useful for the operator feedback message.
     */
    public record FieldBuildResult(BlockPos center, FieldSize size,
                                   BlockPos redGoal, BlockPos blueGoal, int blocksPlaced) {
    }
}
