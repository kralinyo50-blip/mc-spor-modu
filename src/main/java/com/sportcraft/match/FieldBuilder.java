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
 * One-click field construction used by the "Sahayı kur" button.
 *
 * <p>The layout is built around the clicked centre block: an artificial-turf
 * rectangle sized by {@link FieldSize}, white carpet markings (border, halfway
 * line, centre circle, penalty boxes), a red and a blue goal mouth facing the
 * pitch, spectator seats behind each end and a scoreboard block.</p>
 *
 * <p>Only air, replaceable blocks (plants, snow) and common natural ground
 * blocks are overwritten, so an existing structure next to the pitch is safe.</p>
 */
public final class FieldBuilder {

    private static final Set<Block> GROUND_LIKE = Set.of(
            Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.PODZOL,
            Blocks.ROOTED_DIRT, Blocks.SAND, Blocks.RED_SAND, Blocks.GRAVEL,
            Blocks.MOSS_BLOCK, Blocks.MUD,
            ModBlocks.ARTIFICIAL_TURF.get(), ModBlocks.FIELD_LINE.get());

    private static final int CIRCLE_RADIUS = 4;
    private static final int PENALTY_DEPTH = 6;
    private static final int PENALTY_HALF_WIDTH = 6;
    private static final int SEAT_ROW_LENGTH = 9;

    private FieldBuilder() {
    }

    public static FieldBuildResult build(ServerLevel level, BlockPos center, FieldSize size) {
        int halfW = size.getWidth() / 2;
        int halfL = size.getLength() / 2;
        int groundY = center.getY() - 1;
        int playY = center.getY();
        int placed = 0;

        BlockState turf = ModBlocks.ARTIFICIAL_TURF.get().defaultBlockState();
        BlockState line = ModBlocks.FIELD_LINE.get().defaultBlockState();

        for (int x = -halfW; x <= halfW; x++) {
            for (int z = -halfL; z <= halfL; z++) {
                BlockPos groundPos = new BlockPos(center.getX() + x, groundY, center.getZ() + z);
                if (canReplaceGround(level.getBlockState(groundPos))) {
                    level.setBlock(groundPos, turf, Block.UPDATE_ALL);
                    placed++;
                }
                BlockPos playPos = new BlockPos(center.getX() + x, playY, center.getZ() + z);
                BlockState existing = level.getBlockState(playPos);
                if (existing.canBeReplaced()) {
                    level.setBlock(playPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    if (isMarking(x, z, halfW, halfL)) {
                        level.setBlock(playPos, line, Block.UPDATE_ALL);
                        placed++;
                    }
                }
            }
        }

        BlockPos redGoalPos = new BlockPos(center.getX(), playY, center.getZ() - halfL);
        BlockPos blueGoalPos = new BlockPos(center.getX(), playY, center.getZ() + halfL);
        placed += placeGoal(level, redGoalPos, ModBlocks.RED_GOAL.get().defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
        placed += placeGoal(level, blueGoalPos, ModBlocks.BLUE_GOAL.get().defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));

        placed += placeSeatRow(level, center, -halfL - 2, ModBlocks.RED_SEAT.get().defaultBlockState(), playY);
        placed += placeSeatRow(level, center, halfL + 2, ModBlocks.BLUE_SEAT.get().defaultBlockState(), playY);

        BlockPos scoreboardPos = new BlockPos(center.getX(), groundY, center.getZ() - halfL - 4);
        if (canReplaceGround(level.getBlockState(scoreboardPos))) {
            level.setBlock(scoreboardPos, ModBlocks.SCOREBOARD.get().defaultBlockState(), Block.UPDATE_ALL);
            placed++;
        }

        return new FieldBuildResult(center, size, redGoalPos, blueGoalPos, placed);
    }

    private static boolean isMarking(int x, int z, int halfW, int halfL) {
        if (Math.abs(x) == halfW || Math.abs(z) == halfL || z == 0) {
            return true;
        }
        double distance = Math.sqrt(x * x + z * z);
        if (Math.abs(distance - CIRCLE_RADIUS) < 1.05D || (x == 0 && z == 0)) {
            return true;
        }
        return isPenaltyBoxOutline(x, z, halfL, true) || isPenaltyBoxOutline(x, z, halfL, false);
    }

    private static boolean isPenaltyBoxOutline(int x, int z, int halfL, boolean north) {
        int innerZ = north ? -halfL + PENALTY_DEPTH : halfL - PENALTY_DEPTH;
        int goalLineZ = north ? -halfL : halfL;
        boolean insideX = Math.abs(x) <= PENALTY_HALF_WIDTH;
        boolean withinDepth = north
                ? z >= goalLineZ && z <= innerZ
                : z <= goalLineZ && z >= innerZ;
        if (!insideX || !withinDepth) {
            return false;
        }
        return Math.abs(x) == PENALTY_HALF_WIDTH || z == innerZ;
    }

    private static int placeGoal(ServerLevel level, BlockPos pos, BlockState state) {
        if (level.getBlockState(pos).canBeReplaced()) {
            level.setBlock(pos, state, Block.UPDATE_ALL);
            return 1;
        }
        return 0;
    }

    private static int placeSeatRow(ServerLevel level, BlockPos center, int rowOffset,
                                    BlockState seat, int playY) {
        int count = 0;
        for (int x = -SEAT_ROW_LENGTH / 2; x <= SEAT_ROW_LENGTH / 2; x++) {
            BlockPos pos = new BlockPos(center.getX() + x, playY, center.getZ() + rowOffset);
            if (level.getBlockState(pos).canBeReplaced()) {
                level.setBlock(pos, seat, Block.UPDATE_ALL);
                count++;
            }
        }
        return count;
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
