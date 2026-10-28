package com.mcsport.client;

import com.mcsport.client.screen.AdBoardEditScreen;
import com.mcsport.client.screen.GuideScreen;
import com.mcsport.client.screen.MatchScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * Small indirection so common code can request client screens without
 * class-loading GUI code on a dedicated server.
 */
public final class ClientScreenOpener {

    private ClientScreenOpener() {
    }

    public static void openMatchScreen() {
        // Re-announce the locale so a language changed after login still
        // names the next match's bots correctly.
        ClientLanguageSync.sendNow();
        Minecraft.getInstance().setScreen(new MatchScreen());
    }

    public static void openGuideBook() {
        Minecraft.getInstance().setScreen(new GuideScreen());
    }

    public static void openAdBoard(BlockPos pos) {
        Minecraft.getInstance().setScreen(new AdBoardEditScreen(pos));
    }
}
