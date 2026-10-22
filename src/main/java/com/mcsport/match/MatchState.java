package com.mcsport.match;

import java.util.Locale;

/**
 * Lifecycle of a soccer match.
 */
public enum MatchState {
    /** No match configured yet. */
    IDLE,
    /** Clock running. */
    RUNNING,
    /** Clock temporarily stopped, scores kept. */
    PAUSED,
    /** Clock reached zero. */
    FINISHED;

    public String translationKey() {
        return "match_state.mcsport." + name().toLowerCase(Locale.ROOT);
    }

    public static MatchState byOrdinal(int ordinal) {
        MatchState[] values = values();
        return values[Math.floorMod(ordinal, values.length)];
    }
}
