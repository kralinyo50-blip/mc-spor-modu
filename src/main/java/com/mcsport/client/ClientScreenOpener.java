package com.mcsport.client;

import com.mcsport.client.screen.MatchScreen;
import net.minecraft.client.Minecraft;

/**
 * Small indirection so common code can request the match screen without
 * class-loading screen code on a dedicated server.
 */
public final class ClientScreenOpener {

    private ClientScreenOpener() {
    }

    public static void openMatchScreen() {
        Minecraft.getInstance().setScreen(new MatchScreen());
    }
}
