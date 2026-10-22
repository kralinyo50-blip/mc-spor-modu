package com.mcsport.match;

/**
 * Difficulty preset. Currently drives NPC-related tuning placeholders and is
 * persisted/synchronised so the GUI behaves exactly like the reference mod.
 */
public enum MatchDifficulty {
    EASY,
    NORMAL,
    HARD;

    public MatchDifficulty next() {
        MatchDifficulty[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public MatchDifficulty previous() {
        MatchDifficulty[] values = values();
        return values[Math.floorMod(ordinal() - 1, values.length)];
    }

    public String translationKey() {
        return "difficulty.mcsport." + name().toLowerCase();
    }

    public static MatchDifficulty byOrdinal(int ordinal) {
        MatchDifficulty[] values = values();
        return values[Math.floorMod(ordinal, values.length)];
    }
}
