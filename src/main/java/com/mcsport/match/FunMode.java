package com.mcsport.match;

import java.util.Locale;

/**
 * Optional match-wide fun modifiers chosen on the setup screen. Only one is
 * active at a time so the physics stay predictable; {@link #PARTY} rotates
 * several short-lived events automatically instead of forcing a single one.
 */
public enum FunMode {
    /** Standard rules, no modifier. */
    OFF,
    /** The ball falls and bounces in reduced gravity. */
    LOW_GRAVITY,
    /** An oversized ball with a matching larger collision box. */
    BIG_BALL,
    /** Every kick is charged to a stronger speed multiplier. */
    SUPER_SHOT,
    /** Random short events (low gravity, super shots, bot speed bursts). */
    PARTY;

    public FunMode next() {
        FunMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public FunMode previous() {
        FunMode[] values = values();
        return values[Math.floorMod(ordinal() - 1, values.length)];
    }

    public String translationKey() {
        return "fun_mode.mcsport." + name().toLowerCase(Locale.ROOT);
    }

    public static FunMode byOrdinal(int ordinal) {
        FunMode[] values = values();
        return values[Math.floorMod(ordinal, values.length)];
    }
}
