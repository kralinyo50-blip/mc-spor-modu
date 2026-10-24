package com.mcsport.advancement;

import com.mcsport.McSport;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.resources.ResourceLocation;

/**
 * Registration point for the mod's custom advancement triggers. Each trigger
 * is a {@link CountTrigger} keyed by its own resource location, which is the
 * same id referenced from the advancement JSON under
 * {@code data/mcsport/advancements}.
 */
public final class ModCriteriaTriggers {

    /** Player joined red or blue (value 1). */
    public static final CountTrigger TEAM_JOIN = register("team_join");
    /** Player's running goal total within the current match (1, 3...). */
    public static final CountTrigger PLAYER_GOAL = register("player_goal");
    /** Player finished a match on the winning team (value 1). */
    public static final CountTrigger MATCH_WIN = register("match_win");
    /** Referee showed the player a card (1 = yellow tier, 2 = red tier). */
    public static final CountTrigger CARD_SHOWN = register("card_shown");

    private static CountTrigger register(String name) {
        return CriteriaTriggers.register(new CountTrigger(
                new ResourceLocation(McSport.MOD_ID, name)));
    }

    /** Forces class loading so the static triggers register. */
    public static void init() {
    }

    private ModCriteriaTriggers() {
    }
}
