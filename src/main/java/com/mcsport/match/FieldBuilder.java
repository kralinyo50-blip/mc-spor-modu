package com.mcsport.match;

import com.mcsport.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.server.level.ServerLevel;

import java.util.Set;

/**
 * One-click, ten-tier stadium constructor used by the "Sahayı Kur" button.
 *
 * <p>The selected {@link FieldSize} is a full stadium level: low levels are
 * plain neighbourhood pitches with just the lines and goals, and every level
 * unlocks more detail (corner flags, advertising boards, running track,
 * terraced stands on all four sides, floodlights, dugouts, a roof, VIP row,
 * perimeter wall, entrance tunnel, plaza lamps, banners and a trophy) until
 * level 10 becomes a complete enclosed arena. Football and basketball share
 * the decoration pipeline; only the playing surface and goals differ.</p>
 *
 * <p>Only air, replaceable blocks and common natural/building ground blocks
 * are overwritten, so an existing structure next to the stadium is preserved.</p>
 */
public final class FieldBuilder {

    private static final Set<Block> GROUND_LIKE = Set.of(
            Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.PODZOL,
            Blocks.ROOTED_DIRT, Blocks.SAND, Blocks.RED_SAND, Blocks.GRAVEL,
            Blocks.MOSS_BLOCK, Blocks.MUD, Blocks.RED_CONCRETE, Blocks.ORANGE_CONCRETE,
            Blocks.GRAY_CONCRETE, Blocks.WHITE_CONCRETE,
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

    private static final int BB_KEY_HALF_WIDTH = 4;
    private static final int BB_KEY_DEPTH = 5;
    private static final int BB_CIRCLE_RADIUS = 3;
    private static final int BB_HOOP_HEIGHT = 3;

    private FieldBuilder() {
    }

    public static FieldBuildResult build(ServerLevel level, BlockPos center, FieldSize size,
                                         BallType sport, Club homeClub, Club awayClub) {
        return sport == BallType.BASKETBALL
                ? buildBasketballCourt(level, center, size, homeClub, awayClub)
                : buildFootball(level, center, size, homeClub, awayClub);
    }

    // ------------------------------------------------------------------
    // Football
    // ------------------------------------------------------------------

    private static FieldBuildResult buildFootball(ServerLevel level, BlockPos center, FieldSize size,
                                                   Club homeClub, Club awayClub) {
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

        int outer = outerMargin(size);
        for (int x = -halfW - outer; x <= halfW + outer; x++) {
            for (int z = -halfL - outer; z <= halfL + outer; z++) {
                BlockPos groundPos = new BlockPos(center.getX() + x, groundY, center.getZ() + z);
                if (canReplaceGround(level.getBlockState(groundPos))) {
                    BlockState ground;
                    if (Math.abs(x) <= halfW && Math.abs(z) <= halfL) {
                        ground = ((x / 2) & 1) == 0 ? turfLight : turfDark;
                    } else if (size.hasFeature(FieldFeatures.TRACK)
                            && Math.abs(x) <= halfW + TRACK_WIDTH && Math.abs(z) <= halfL + TRACK_WIDTH) {
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

        placed += decorate(level, center, size, halfW, halfL, groundY, playY, homeClub, awayClub);

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

        for (int y = playY; y < playY + GOAL_HEIGHT; y++) {
            count += placeIfReplaceable(level, new BlockPos(cx - GOAL_HALF_WIDTH, y, lineZ), post);
            count += placeIfReplaceable(level, new BlockPos(cx + GOAL_HALF_WIDTH, y, lineZ), post);
        }
        for (int x = cx - GOAL_HALF_WIDTH; x <= cx + GOAL_HALF_WIDTH; x++) {
            count += placeIfReplaceable(level, new BlockPos(x, playY + GOAL_HEIGHT - 1, lineZ), post);
        }

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

        count += placeIfReplaceable(level, mouth, sensor);
        return count;
    }

    // ------------------------------------------------------------------
    // Basketball
    // ------------------------------------------------------------------

    private static FieldBuildResult buildBasketballCourt(ServerLevel level, BlockPos center, FieldSize size,
                                                          Club homeClub, Club awayClub) {
        int halfW = size.getWidth() / 2;
        int halfL = size.getLength() / 2;
        int groundY = center.getY() - 1;
        int playY = center.getY();
        int placed = 0;

        BlockState courtFloor = Blocks.ORANGE_CONCRETE.defaultBlockState();
        BlockState surround = Blocks.GRAY_CONCRETE.defaultBlockState();
        BlockState line = ModBlocks.FIELD_LINE.get().defaultBlockState();
        BlockState post = ModBlocks.GOAL_POST.get().defaultBlockState();
        BlockState net = ModBlocks.GOAL_NET.get().defaultBlockState();
        BlockState board = Blocks.WHITE_CONCRETE.defaultBlockState();

        int margin = 2;
        for (int x = -halfW - margin; x <= halfW + margin; x++) {
            for (int z = -halfL - margin; z <= halfL + margin; z++) {
                BlockPos groundPos = new BlockPos(center.getX() + x, groundY, center.getZ() + z);
                if (canReplaceGround(level.getBlockState(groundPos))) {
                    boolean inside = Math.abs(x) <= halfW && Math.abs(z) <= halfL;
                    BlockState ground = inside ? courtFloor : surround;
                    level.setBlock(groundPos, ground, Block.UPDATE_ALL);
                    placed++;
                }
                BlockPos playPos = new BlockPos(center.getX() + x, playY, center.getZ() + z);
                if (level.getBlockState(playPos).canBeReplaced()
                        && Math.abs(x) <= halfW && Math.abs(z) <= halfL) {
                    level.setBlock(playPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    if (isBasketballMarking(x, z, halfW, halfL)) {
                        level.setBlock(playPos, line, Block.UPDATE_ALL);
                        placed++;
                    }
                }
            }
        }

        BlockPos redSensor = buildHoop(level, center, halfL, -1, playY, post, net, board,
                ModBlocks.RED_GOAL.get().defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
        BlockPos blueSensor = buildHoop(level, center, halfL, 1, playY, post, net, board,
                ModBlocks.BLUE_GOAL.get().defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        placed += 2;

        placed += decorate(level, center, size, halfW, halfL, groundY, playY, homeClub, awayClub);

        return new FieldBuildResult(center, size, redSensor, blueSensor, placed);
    }

    /**
     * Elevated hoop: metal pole behind the baseline, white backboard, net rim
     * block and the invisible score sensor one block inside the court.
     */
    private static BlockPos buildHoop(ServerLevel level, BlockPos center, int halfL, int zDirection,
                                      int playY, BlockState post, BlockState net, BlockState board,
                                      BlockState sensorState) {
        int cx = center.getX();
        int lineZ = center.getZ() + zDirection * halfL;
        int poleZ = lineZ + zDirection;
        int inward = -zDirection;

        for (int y = playY; y <= playY + BB_HOOP_HEIGHT + 1; y++) {
            placeIfReplaceable(level, new BlockPos(cx, y, poleZ), post);
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy < 2; dy++) {
                placeIfReplaceable(level, new BlockPos(cx + dx, playY + BB_HOOP_HEIGHT - 1 + dy, poleZ), board);
            }
        }
        placeIfReplaceable(level, new BlockPos(cx, playY + BB_HOOP_HEIGHT, lineZ), net);
        BlockPos sensorPos = new BlockPos(cx, playY + BB_HOOP_HEIGHT, lineZ + inward);
        placeIfReplaceable(level, sensorPos, sensorState);
        return sensorPos;
    }

    private static boolean isBasketballMarking(int x, int z, int halfW, int halfL) {
        if (Math.abs(x) == halfW || Math.abs(z) == halfL || z == 0) {
            return true;
        }
        double centreDistance = Math.sqrt(x * x + z * z);
        if (Math.abs(centreDistance - BB_CIRCLE_RADIUS) < 1.05D || (x == 0 && z == 0)) {
            return true;
        }
        if (isBasketballKey(x, z, halfL, true) || isBasketballKey(x, z, halfL, false)) {
            return true;
        }
        int arcRadius = halfW - 2;
        for (int sign : new int[]{-1, 1}) {
            double dx = x;
            double dz = z - sign * halfL;
            if (Math.abs(Math.sqrt(dx * dx + dz * dz) - arcRadius) < 1.05D
                    && sign * z < sign * (halfL - 2)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBasketballKey(int x, int z, int halfL, boolean north) {
        int innerZ = north ? -halfL + BB_KEY_DEPTH : halfL - BB_KEY_DEPTH;
        int goalLineZ = north ? -halfL : halfL;
        boolean insideX = Math.abs(x) <= BB_KEY_HALF_WIDTH;
        boolean withinDepth = north
                ? z >= goalLineZ && z <= innerZ
                : z <= goalLineZ && z >= innerZ;
        if (!insideX || !withinDepth) {
            return false;
        }
        if (Math.abs(x) == BB_KEY_HALF_WIDTH || z == innerZ) {
            return true;
        }
        double distance = Math.sqrt(x * x + (z - innerZ) * (z - innerZ));
        return Math.abs(distance - 2) < 1.05D;
    }

    // ------------------------------------------------------------------
    // Level-driven decoration pipeline (shared by football and basketball)
    // ------------------------------------------------------------------

    /** Ground footprint margin reserved for the highest enabled decoration. */
    private static int outerMargin(FieldSize size) {
        int margin = 2;
        if (size.hasFeature(FieldFeatures.TRACK)) {
            margin = TRACK_WIDTH + 2;
        }
        if (size.getStandRows() > 0) {
            margin = Math.max(margin, TRACK_WIDTH + size.getStandRows() + 3);
        }
        if (size.hasPerimeterWall()) {
            margin += 2;
        }
        return margin;
    }

    private static int decorate(ServerLevel level, BlockPos center, FieldSize size,
                                int halfW, int halfL, int groundY, int playY,
                                Club homeClub, Club awayClub) {
        int count = 0;
        if (size.hasFeature(FieldFeatures.CORNER_FLAGS)) {
            count += buildCornerFlags(level, center, halfW, halfL, playY);
        }
        if (size.hasFeature(FieldFeatures.AD_BOARDS)) {
            count += buildAdvertisingBoards(level, center, halfW, halfL, playY);
        }
        if (size.getFloodlightTowers() > 0) {
            count += buildFloodlights(level, center, halfW, halfL, playY, size);
        }
        if (size.hasFeature(FieldFeatures.DUGOUTS)) {
            count += buildDugouts(level, center, halfW, halfL, playY);
        }
        if (size.getStandRows() > 0) {
            count += buildStands(level, center, halfW, halfL, playY, size, homeClub, awayClub);
        }
        if (size.hasPerimeterWall()) {
            count += buildPerimeterWall(level, center, halfW, halfL, groundY, playY, size);
        }
        if (size.hasEntranceTunnel()) {
            count += buildEntranceTunnel(level, center, halfW, halfL, playY, size);
        }
        if (size.hasPlazaLamps()) {
            count += buildPlazaLamps(level, center, halfW, halfL, playY, size);
        }
        if (size.hasBanners()) {
            count += buildBanners(level, center, halfW, halfL, playY, size);
        }
        if (size.hasTrophy()) {
            count += buildTrophy(level, center, halfW, halfL, groundY, playY);
        }
        if (size.hasFeature(FieldFeatures.SCOREBOARD)) {
            int offset = TRACK_WIDTH + size.getStandRows() + 1;
            BlockPos scoreboardPos = new BlockPos(center.getX() + halfW + offset, playY, center.getZ());
            count += placeIfReplaceable(level, scoreboardPos, ModBlocks.SCOREBOARD.get().defaultBlockState());
        }
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
                level.setBlock(new BlockPos(x, playY, z), fence, Block.UPDATE_ALL);
                level.setBlock(new BlockPos(x, playY + 1, z), fence, Block.UPDATE_ALL);
                count += placeIfReplaceable(level, new BlockPos(x, playY + 2, z), flag);
                count += 2;
            }
        }
        return count;
    }

    private static int buildFloodlights(ServerLevel level, BlockPos center, int halfW,
                                        int halfL, int playY, FieldSize size) {
        int count = 0;
        BlockState pole = Blocks.IRON_BLOCK.defaultBlockState();
        BlockState lamp = ModBlocks.STADIUM_LAMP.get().defaultBlockState();
        BlockState cap = Blocks.BLACK_CONCRETE.defaultBlockState();
        int towers = size.getFloodlightTowers();
        int ring = halfL + TRACK_WIDTH + size.getStandRows() + 2;
        for (int i = 0; i < towers; i++) {
            // Evenly space the towers around the four corners of the footprint.
            int sx = (i % 2 == 0) ? -1 : 1;
            int sz = (i < 2) ? -1 : 1;
            int x = center.getX() + sx * (halfW + TRACK_WIDTH + 1);
            int z = center.getZ() + sz * ring;
            for (int y = playY; y < playY + size.getFloodlightHeight(); y++) {
                count += placeIfReplaceable(level, new BlockPos(x, y, z), pole);
            }
            int topY = playY + size.getFloodlightHeight();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    count += placeIfReplaceable(level, new BlockPos(x + dx, topY, z + dz), lamp);
                }
            }
            count += placeIfReplaceable(level, new BlockPos(x, topY + 1, z), cap);
        }
        return count;
    }

    private static int buildDugouts(ServerLevel level, BlockPos center, int halfW,
                                    int halfL, int playY) {
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

    /**
     * Terraced stands. Goal-end stands are always built for tiers that have
     * rows; high tiers also wrap the stands around the sidelines and add a
     * VIP gold row plus a translucent roof over the back rows.
     */
    private static int buildStands(ServerLevel level, BlockPos center, int halfW,
                                   int halfL, int playY, FieldSize size,
                                   Club homeClub, Club awayClub) {
        int count = 0;
        BlockState support = Blocks.STONE.defaultBlockState();
        BlockState homeSeat = ModBlocks.seatFor(homeClub).get().defaultBlockState();
        BlockState awaySeat = ModBlocks.seatFor(awayClub).get().defaultBlockState();
        BlockState vipSeat = Blocks.GOLD_BLOCK.defaultBlockState();
        int rows = size.getStandRows();
        int ring = TRACK_WIDTH + 1;
        int halfWidth = Math.min(halfW + 2, 10);

        // Goal-end stands (north home, south away), growing row by row.
        for (int side = -1; side <= 1; side += 2) {
            BlockState seat = side < 0 ? homeSeat : awaySeat;
            for (int row = 0; row < rows; row++) {
                int z = center.getZ() + side * (halfL + ring + row);
                int seatY = playY + row;
                for (int x = center.getX() - halfWidth; x <= center.getX() + halfWidth; x++) {
                    for (int supportY = playY; supportY < seatY; supportY++) {
                        count += placeIfReplaceable(level, new BlockPos(x, supportY, z), support);
                    }
                    BlockState rowSeat = size.hasVipRow() && row == rows - 1 ? vipSeat : seat;
                    count += placeIfReplaceable(level, new BlockPos(x, seatY, z), rowSeat);
                }
            }
        }

        // Side stands only from the tiers that enclose the pitch on all sides.
        if (size.isAllSideStands()) {
            for (int side = -1; side <= 1; side += 2) {
                BlockState sideSeat = side < 0 ? homeSeat : awaySeat;
                for (int row = 0; row < rows; row++) {
                    int x = center.getX() + side * (halfW + ring + row);
                    int seatY = playY + row;
                    for (int z = center.getZ() - halfL; z <= center.getZ() + halfL; z++) {
                        for (int supportY = playY; supportY < seatY; supportY++) {
                            count += placeIfReplaceable(level, new BlockPos(x, supportY, z), support);
                        }
                        BlockState rowSeat = size.hasVipRow() && row == rows - 1 ? vipSeat : sideSeat;
                        count += placeIfReplaceable(level, new BlockPos(x, seatY, z), rowSeat);
                    }
                }
            }
        }

        if (size.hasStandRoof()) {
            BlockState roof = ModBlocks.STADIUM_ROOF.get().defaultBlockState();
            int roofY = playY + rows + 1;
            for (int side = -1; side <= 1; side += 2) {
                int z = center.getZ() + side * (halfL + ring + rows - 1);
                for (int x = center.getX() - halfWidth; x <= center.getX() + halfWidth; x++) {
                    count += placeIfReplaceable(level, new BlockPos(x, roofY, z), roof);
                }
            }
        }
        return count;
    }

    /** Outer enclosure with a gap left on the sideline for the tunnel. */
    private static int buildPerimeterWall(ServerLevel level, BlockPos center, int halfW,
                                          int halfL, int groundY, int playY, FieldSize size) {
        int count = 0;
        BlockState wall = ModBlocks.STADIUM_WALL.get().defaultBlockState();
        int ring = TRACK_WIDTH + size.getStandRows() + 2;
        int xMin = center.getX() - halfW - ring;
        int xMax = center.getX() + halfW + ring;
        int zMin = center.getZ() - halfL - ring;
        int zMax = center.getZ() + halfL + ring;
        int height = size.getStandRows() + 2;
        int tunnelGap = 2;
        for (int y = playY; y < playY + height; y++) {
            for (int x = xMin; x <= xMax; x++) {
                count += placeIfReplaceable(level, new BlockPos(x, y, zMin), wall);
                count += placeIfReplaceable(level, new BlockPos(x, y, zMax), wall);
            }
            for (int z = zMin; z <= zMax; z++) {
                // Sideline entrance gap at the centre of the +X wall.
                boolean tunnelMouth = size.hasEntranceTunnel() && Math.abs(z - center.getZ()) <= tunnelGap;
                if (tunnelMouth && y < playY + 3) {
                    continue;
                }
                count += placeIfReplaceable(level, new BlockPos(xMin, y, z), wall);
                count += placeIfReplaceable(level, new BlockPos(xMax, y, z), wall);
            }
        }
        return count;
    }

    /** Two lamp-topped pillars and a roof block framing the entrance gap. */
    private static int buildEntranceTunnel(ServerLevel level, BlockPos center, int halfW,
                                           int halfL, int playY, FieldSize size) {
        int count = 0;
        BlockState wall = ModBlocks.STADIUM_WALL.get().defaultBlockState();
        BlockState lamp = ModBlocks.STADIUM_LAMP.get().defaultBlockState();
        int ring = TRACK_WIDTH + size.getStandRows() + 2;
        int x = center.getX() + halfW + ring;
        int height = size.getStandRows() + 2;
        for (int dz : new int[]{-3, 3}) {
            int z = center.getZ() + dz;
            for (int y = playY; y < playY + height; y++) {
                count += placeIfReplaceable(level, new BlockPos(x, y, z), wall);
            }
            count += placeIfReplaceable(level, new BlockPos(x, playY + height, z), lamp);
        }
        count += placeIfReplaceable(level, new BlockPos(x, playY + 3, center.getZ()),
                ModBlocks.STADIUM_ROOF.get().defaultBlockState());
        return count;
    }

    /** Short lamp posts lining the approach plaza outside each sideline. */
    private static int buildPlazaLamps(ServerLevel level, BlockPos center, int halfW,
                                       int halfL, int playY, FieldSize size) {
        int count = 0;
        BlockState pole = Blocks.IRON_BARS.defaultBlockState();
        BlockState lamp = ModBlocks.STADIUM_LAMP.get().defaultBlockState();
        int ring = TRACK_WIDTH + size.getStandRows() + 4;
        for (int side = -1; side <= 1; side += 2) {
            int x = center.getX() + side * (halfW + ring);
            for (int dz = -10; dz <= 10; dz += 5) {
                int z = center.getZ() + dz;
                for (int y = playY; y < playY + 3; y++) {
                    count += placeIfReplaceable(level, new BlockPos(x, y, z), pole);
                }
                count += placeIfReplaceable(level, new BlockPos(x, playY + 3, z), lamp);
            }
        }
        return count;
    }

    /** Alternating club-colour wool banners hung above the goal-end stands. */
    private static int buildBanners(ServerLevel level, BlockPos center, int halfW,
                                    int halfL, int playY, FieldSize size) {
        int count = 0;
        BlockState[] banners = {
                Blocks.RED_WOOL.defaultBlockState(), Blocks.BLUE_WOOL.defaultBlockState(),
                Blocks.YELLOW_WOOL.defaultBlockState(), Blocks.LIME_WOOL.defaultBlockState(),
                Blocks.PURPLE_WOOL.defaultBlockState(), Blocks.ORANGE_WOOL.defaultBlockState()
        };
        int ring = TRACK_WIDTH + 1;
        int bannerY = playY + size.getStandRows() + 1;
        int halfWidth = Math.min(halfW + 2, 10);
        for (int side = -1; side <= 1; side += 2) {
            int z = center.getZ() + side * (halfL + ring);
            int i = 0;
            for (int x = center.getX() - halfWidth; x <= center.getX() + halfWidth; x += 2) {
                BlockState banner = banners[Math.floorMod(i++, banners.length)];
                count += placeIfReplaceable(level, new BlockPos(x, bannerY, z), banner);
            }
        }
        return count;
    }

    /** Gold pedestal with the trophy block beside the centre spot. */
    private static int buildTrophy(ServerLevel level, BlockPos center, int halfW,
                                   int halfL, int groundY, int playY) {
        int count = 0;
        BlockState pedestal = Blocks.QUARTZ_BLOCK.defaultBlockState();
        int x = center.getX() + 3;
        int z = center.getZ();
        count += placeIfReplaceable(level, new BlockPos(x, groundY, z), pedestal);
        count += placeIfReplaceable(level, new BlockPos(x, playY, z),
                ModBlocks.TROPHY_BLOCK.get().defaultBlockState());
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
