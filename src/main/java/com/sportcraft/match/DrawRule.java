package com.sportcraft.match;

/**
 * What happens when the clock runs out on a tied score.
 */
public enum DrawRule {
    /** Match finishes with a draw. */
    END_DRAW,
    /** One 2-minute extra-time period is added once, then the match ends regardless. */
    EXTRA_TIME;

    public DrawRule next() {
        DrawRule[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public DrawRule previous() {
        DrawRule[] values = values();
        return values[Math.floorMod(ordinal() - 1, values.length)];
    }

    public String translationKey() {
        return "draw_rule.sportcraft." + name().toLowerCase();
    }

    public static DrawRule byOrdinal(int ordinal) {
        DrawRule[] values = values();
        return values[Math.floorMod(ordinal, values.length)];
    }
}
