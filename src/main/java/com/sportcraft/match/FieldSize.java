package com.sportcraft.match;

/**
 * Auto-build field presets. Width is the goal-line length, length is the
 * distance between the two goals. Both are odd so a centre block exists.
 */
public enum FieldSize {
    SMALL(17, 25, "kucuk"),
    MEDIUM(27, 41, "orta"),
    LARGE(45, 71, "buyuk");

    private final int width;
    private final int length;
    private final String translationSuffix;

    FieldSize(int width, int length, String translationSuffix) {
        this.width = width;
        this.length = length;
        this.translationSuffix = translationSuffix;
    }

    public int getWidth() {
        return width;
    }

    public int getLength() {
        return length;
    }

    public String getTranslationSuffix() {
        return translationSuffix;
    }

    public FieldSize next() {
        FieldSize[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public FieldSize previous() {
        FieldSize[] values = values();
        return values[Math.floorMod(ordinal() - 1, values.length)];
    }

    public static FieldSize byOrdinal(int ordinal) {
        FieldSize[] values = values();
        return values[Math.floorMod(ordinal, values.length)];
    }
}
