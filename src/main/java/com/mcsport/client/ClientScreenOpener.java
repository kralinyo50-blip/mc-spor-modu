package com.mcsport.client;

import com.mcsport.client.screen.GuideScreen;
import com.mcsport.client.screen.MatchScreen;
import net.minecraft.client.Minecraft;

/**
 * Small indirection so common code can request client screens without
 * class-loading GUI code on a dedicated server.
 */
public final class ClientScreenOpener {

    private ClientScreenOpener() {
    }

    public static void openMatchScreen() {
        Minecraft.getInstance().setScreen(new MatchScreen());
    }

    public static void openGuideBook() {
        Minecraft.getInstance().setScreen(new GuideScreen());
    }
}
