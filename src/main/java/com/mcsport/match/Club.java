package com.mcsport.match;

import java.util.Locale;

/**
 * The selectable clubs. A match always has two {@link Team} sides
 * (home/away); each side is dressed and named after one of these clubs.
 * Every club ships with two fixed squads — Turkish and English names — and
 * maps to a matching coloured stadium seat block through {@link #seatName()}.
 * The active language is chosen by the server from the players' client
 * locale (see {@link MatchManager#getLanguage()}).
 */
public enum Club {
    RED(0xC02626, "red_seat",
            new String[]{"Selim", "Umut", "Mert", "Emre", "Kaan", "Arda", "Deniz"},
            new String[]{"Jack", "John", "Mike", "Liam", "Noah", "Ethan", "Lucas"}),
    BLUE(0x274FC0, "blue_seat",
            new String[]{"Can", "Burak", "Onur", "Yusuf", "Kerem", "Volkan", "Serkan"},
            new String[]{"Oliver", "James", "Henry", "Arthur", "Leo", "Oscar", "Felix"}),
    YELLOW(0xE0B020, "yellow_seat",
            new String[]{"Efe", "Yiğit", "Toprak", "Musa", "Çınar", "Enes", "Berk"},
            new String[]{"Max", "Ben", "Sam", "Luke", "Adam", "Ryan", "Dylan"}),
    GREEN(0x2FA84F, "green_seat",
            new String[]{"Doğu", "Rüzgar", "Ayaz", "Polat", "Sarp", "Tuna", "Göktuğ"},
            new String[]{"Daniel", "David", "George", "Joseph", "Thomas", "Charles", "Paul"}),
    PURPLE(0x7A3CB0, "purple_seat",
            new String[]{"Doruk", "Kaya", "Bora", "Cem", "Ilgaz", "Mete", "Ozan"},
            new String[]{"Peter", "Mark", "Simon", "Victor", "Hugo", "Miles", "Jasper"}),
    ORANGE(0xE2701C, "orange_seat",
            new String[]{"Aslan", "Poyraz", "Fırat", "Baran", "Yiğit", "Civan", "Demir"},
            new String[]{"Aaron", "Caleb", "Isaac", "Nathan", "Owen", "Theo", "Xavier"});

    private final int kitColor;
    private final String seatName;
    private final String[] squadTr;
    private final String[] squadEn;

    Club(int kitColor, String seatName, String[] squadTr, String[] squadEn) {
        this.kitColor = kitColor;
        this.seatName = seatName;
        this.squadTr = squadTr;
        this.squadEn = squadEn;
    }

    public int getKitColor() {
        return kitColor;
    }

    public String seatName() {
        return seatName;
    }

    /** Fixed squad for the requested language; both arrays are 7-a-side sized. */
    public String[] getSquad(boolean english) {
        return (english ? squadEn : squadTr).clone();
    }

    public String playerName(int squadIndex, boolean english) {
        String[] squad = english ? squadEn : squadTr;
        return squad[Math.floorMod(squadIndex, squad.length)];
    }

    public String translationKey() {
        return "club.mcsport." + name().toLowerCase(Locale.ROOT);
    }

    public Club next() {
        Club[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public Club previous() {
        Club[] values = values();
        return values[Math.floorMod(ordinal() - 1, values.length)];
    }

    public static Club byOrdinal(int ordinal) {
        Club[] values = values();
        return values[Math.floorMod(ordinal, values.length)];
    }
}
