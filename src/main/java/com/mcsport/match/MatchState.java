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
    /**
     * Half-time intermission. The clock is frozen, the squads regroup and
     * sides are swapped before the second half kicks off. Appended last so
     * saved ordinals of the older states keep their meaning.
     */
    HALF_TIME,
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
