package com.mcsport.match;

/**
 * Auto-build stadium tiers. Ten levels grow both the pitch and the
 * surrounding stadium: the first levels are plain neighbourhood pitches,
 * middle levels gain tracks, stands, lights and dugouts, and the top levels
 * become fully enclosed arenas with roofs, VIP rows, perimeter walls, an
 * entrance tunnel, plaza lamps and a trophy pedestal.
 *
 * <p>Width is the goal-line length, length is the distance between the two
 * goals; both are odd so a centre block exists. Every decoration flag is
 * consumed by {@link FieldBuilder}.</p>
 */
public enum FieldSize {
    //            w   l   key    flags                                        rows sides roof towers h   wall  tunnel vip  trophy lamps banners
    LEVEL_1(13, 19, "lv1", 0,                                             0, false, false, 0, 0, false, false, false, false, false, false),
    LEVEL_2(15, 23, "lv2", FieldFeatures.CORNER_FLAGS,                                 0, false, false, 0, 0, false, false, false, false, false, false),
    LEVEL_3(17, 25, "lv3", FieldFeatures.CORNER_FLAGS | FieldFeatures.AD_BOARDS,                     0, false, false, 0, 0, false, false, false, false, false, false),
    LEVEL_4(19, 29, "lv4", FieldFeatures.CORNER_FLAGS | FieldFeatures.AD_BOARDS,                     2, false, false, 2, 10, false, false, false, false, false, false),
    LEVEL_5(23, 35, "lv5", FieldFeatures.CORNER_FLAGS | FieldFeatures.AD_BOARDS | FieldFeatures.TRACK,            3, false, false, 2, 10, false, false, false, false, false, false),
    LEVEL_6(27, 41, "lv6", FieldFeatures.CORNER_FLAGS | FieldFeatures.AD_BOARDS | FieldFeatures.TRACK | FieldFeatures.DUGOUTS | FieldFeatures.SCOREBOARD,
                                                                         4, false, false, 4, 12, false, false, false, false, false, false),
    LEVEL_7(31, 47, "lv7", FieldFeatures.CORNER_FLAGS | FieldFeatures.AD_BOARDS | FieldFeatures.TRACK | FieldFeatures.DUGOUTS | FieldFeatures.SCOREBOARD,
                                                                         5, true, false, 4, 12, false, false, false, false, true, false),
    LEVEL_8(35, 53, "lv8", FieldFeatures.CORNER_FLAGS | FieldFeatures.AD_BOARDS | FieldFeatures.TRACK | FieldFeatures.DUGOUTS | FieldFeatures.SCOREBOARD,
                                                                         6, true, true, 4, 13, false, false, false, false, true, true),
    LEVEL_9(39, 61, "lv9", FieldFeatures.CORNER_FLAGS | FieldFeatures.AD_BOARDS | FieldFeatures.TRACK | FieldFeatures.DUGOUTS | FieldFeatures.SCOREBOARD,
                                                                         7, true, true, 4, 14, true, true, true, false, true, true),
    LEVEL_10(45, 71, "lv10", FieldFeatures.CORNER_FLAGS | FieldFeatures.AD_BOARDS | FieldFeatures.TRACK | FieldFeatures.DUGOUTS | FieldFeatures.SCOREBOARD,
                                                                         8, true, true, 4, 15, true, true, true, true, true, true);

    private final int width;
    private final int length;
    private final String translationSuffix;
    private final int flags;
    private final int standRows;
    private final boolean allSideStands;
    private final boolean standRoof;
    private final int floodlightTowers;
    private final int floodlightHeight;
    private final boolean perimeterWall;
    private final boolean entranceTunnel;
    private final boolean vipRow;
    private final boolean trophy;
    private final boolean plazaLamps;
    private final boolean banners;

    FieldSize(int width, int length, String translationSuffix, int flags,
              int standRows, boolean allSideStands, boolean standRoof,
              int floodlightTowers, int floodlightHeight,
              boolean perimeterWall, boolean entranceTunnel, boolean vipRow,
              boolean trophy, boolean plazaLamps, boolean banners) {
        this.width = width;
        this.length = length;
        this.translationSuffix = translationSuffix;
        this.flags = flags;
        this.standRows = standRows;
        this.allSideStands = allSideStands;
        this.standRoof = standRoof;
        this.floodlightTowers = floodlightTowers;
        this.floodlightHeight = floodlightHeight;
        this.perimeterWall = perimeterWall;
        this.entranceTunnel = entranceTunnel;
        this.vipRow = vipRow;
        this.trophy = trophy;
        this.plazaLamps = plazaLamps;
        this.banners = banners;
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

    public int getLevel() {
        return ordinal() + 1;
    }

    public boolean hasFeature(int featureFlag) {
        return (flags & featureFlag) != 0;
    }

    public int getStandRows() {
        return standRows;
    }

    public boolean isAllSideStands() {
        return allSideStands;
    }

    public boolean hasStandRoof() {
        return standRoof;
    }

    public int getFloodlightTowers() {
        return floodlightTowers;
    }

    public int getFloodlightHeight() {
        return floodlightHeight;
    }

    public boolean hasPerimeterWall() {
        return perimeterWall;
    }

    public boolean hasEntranceTunnel() {
        return entranceTunnel;
    }

    public boolean hasVipRow() {
        return vipRow;
    }

    public boolean hasTrophy() {
        return trophy;
    }

    public boolean hasPlazaLamps() {
        return plazaLamps;
    }

    public boolean hasBanners() {
        return banners;
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
