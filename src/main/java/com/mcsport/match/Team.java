package com.mcsport.match;

import java.util.Locale;

/**
 * The two playable teams plus a spectator slot.
 */
public enum Team {
    RED,
    BLUE,
    SPECTATOR;

    /**
     * @return the opposing team, or {@link #SPECTATOR} when called on a spectator.
     */
    public Team opponent() {
        return switch (this) {
            case RED -> BLUE;
            case BLUE -> RED;
            default -> SPECTATOR;
        };
    }

    public String translationKey() {
        return "team.mcsport." + name().toLowerCase(Locale.ROOT);
    }

    public static Team byOrdinal(int ordinal) {
        Team[] values = values();
        return values[Math.floorMod(ordinal, values.length)];
    }
}
