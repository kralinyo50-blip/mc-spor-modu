package com.sportcraft.match;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Converts the abstract {@link Formation} label (for example "2-1-1") into
 * concrete standing positions on the pitch. Row 0 is the defensive line
 * closest to the team's own goal; each subsequent row advances toward the
 * opponent goal. The same layout drives both kick-off spawn placement and
 * the bots' defensive home positions, so they spread out instead of every
 * bot stacking on the ball.
 */
public final class FormationLayout {

    private FormationLayout() {
    }

    /**
     * @param index    bot index within the team; 0 is always the goalkeeper.
     * @param teamSize full team size including the keeper.
     * @return the centre-of-field standing position for that bot.
     */
    public static Vec3 homePosition(BlockPos fieldCenter, FieldSize size, Formation formation,
                                    Team team, int index, int teamSize, boolean keeper) {
        int halfW = size.getWidth() / 2;
        int halfL = size.getLength() / 2;
        int inward = team == Team.RED ? 1 : -1;
        int goalZ = fieldCenter.getZ() - inward * halfL;

        if (keeper) {
            return Vec3.atBottomCenterOf(fieldCenter.getX(), fieldCenter.getY(),
                    goalZ + inward * 2);
        }

        int[] rows = parseRows(formation);
        int outfieldIndex = index - 1;
        // Round-robin rows so teams larger than the named formation (for example
        // 7v7 with a 2-1-1 shape) still spread out instead of stacking.
        int row = Math.floorMod(outfieldIndex, rows.length);
        int slotInRow = outfieldIndex / rows.length;

        // Defensive line starts 5 blocks off the goal and each row advances 5 more.
        int depth = 5 + row * 5;
        int usableHalfWidth = Math.max(2, halfW - 3);
        // Four-block lateral spacing, centred on the pitch axis.
        int xOffset = slotInRow * 4 - usableHalfWidth;
        xOffset = Math.max(-usableHalfWidth, Math.min(usableHalfWidth, xOffset));

        int x = fieldCenter.getX() + xOffset;
        int z = goalZ + inward * depth;
        return new Vec3(x + 0.5D, fieldCenter.getY(), z + 0.5D);
    }

    private static int[] parseRows(Formation formation) {
        String[] parts = formation.getLabel().split("-");
        int[] rows = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            rows[i] = Math.max(1, Integer.parseInt(parts[i].trim()));
        }
        return rows;
    }
}
