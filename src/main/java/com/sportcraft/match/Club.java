package com.sportcraft.match;

import java.util.Locale;

/**
 * The selectable clubs. A match always has two {@link Team} sides
 * (home/away); each side is dressed and named after one of these clubs.
 * Every club ships with a fixed squad name pool and a kit colour, and maps
 * to a matching coloured stadium seat block through {@link #seatName()}.
 */
public enum Club {
    RED(0xC02626, "red_seat",
            "Selim", "Umut", "Mert", "Emre", "Kaan", "Arda", "Deniz"),
    BLUE(0x274FC0, "blue_seat",
            "Can", "Burak", "Onur", "Yusuf", "Kerem", "Volkan", "Serkan"),
    YELLOW(0xE0B020, "yellow_seat",
            "Efe", "Yiğit", "Toprak", "Musa", "Çınar", "Enes", "Berk"),
    GREEN(0x2FA84F, "green_seat",
            "Doğu", "Rüzgar", "Ayaz", "Polat", "Sarp", "Tuna", "Göktuğ"),
    PURPLE(0x7A3CB0, "purple_seat",
            "Doruk", "Kaya", "Bora", "Cem", "Ilgaz", "Mete", "Ozan"),
    ORANGE(0xE2701C, "orange_seat",
            "Aslan", "Poyraz", "Fırat", "Baran", "Yiğit", "Civan", "Demir");

    private final int kitColor;
    private final String seatName;
    private final String[] squad;

    Club(int kitColor, String seatName, String... squad) {
        this.kitColor = kitColor;
        this.seatName = seatName;
        this.squad = squad;
    }

    public int getKitColor() {
        return kitColor;
    }

    public String seatName() {
        return seatName;
    }

    /** Fixed squad; the array is sized for the maximum 7-a-side team. */
    public String[] getSquad() {
        return squad.clone();
    }

    public String playerName(int squadIndex) {
        return squad[Math.floorMod(squadIndex, squad.length)];
    }

    public String translationKey() {
        return "club.sportcraft." + name().toLowerCase(Locale.ROOT);
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
