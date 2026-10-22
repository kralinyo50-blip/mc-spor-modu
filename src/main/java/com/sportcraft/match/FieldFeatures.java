package com.sportcraft.match;

/**
 * Bit-flag stadium features available to the {@link FieldSize} tiers.
 *
 * <p>Kept in a separate class (not on the enum itself) because Java forbids
 * enum constants from forward-referencing static fields declared after them.</p>
 */
public final class FieldFeatures {
    public static final int CORNER_FLAGS = 1;
    public static final int AD_BOARDS = 1 << 1;
    public static final int TRACK = 1 << 2;
    public static final int DUGOUTS = 1 << 3;
    public static final int SCOREBOARD = 1 << 4;

    private FieldFeatures() {
    }
}
