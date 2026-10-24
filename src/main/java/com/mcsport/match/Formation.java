package com.mcsport.match;

/**
 * NPC team formations. Stored and synched now; consumed by the NPC AI
 * milestone (documented as the next step in TEST_VE_BILINENLER.md).
 */
public enum Formation {
    F_2_1_1("2-1-1"),
    F_1_2_1("1-2-1"),
    F_1_1_2("1-1-2"),
    // Appended after the original three so saved ordinals keep their meaning.
    // FormationLayout parses every "defence-mid-attack" label generically.
    F_3_1_1("3-1-1"),
    F_2_2_1("2-2-1"),
    F_1_3_1("1-3-1");

    private final String label;

    Formation(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public Formation next() {
        Formation[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public Formation previous() {
        Formation[] values = values();
        return values[Math.floorMod(ordinal() - 1, values.length)];
    }

    public static Formation byOrdinal(int ordinal) {
        Formation[] values = values();
        return values[Math.floorMod(ordinal, values.length)];
    }
}
