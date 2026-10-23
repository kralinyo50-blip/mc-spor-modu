package com.mcsport.client;

import com.mcsport.McSport;
import com.mcsport.network.ModNetwork;
import com.mcsport.network.network_packet.LanguageC2S;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Pushes the client's selected language code to the server. The vanilla
 * listener locale field is private in the 1.20.1 Mojang mappings, so the mod
 * carries the value over its own channel: once on world join and again every
 * time the match screen is opened, which also covers language changes made
 * mid-session before kick-off.
 */
@Mod.EventBusSubscriber(modid = McSport.MOD_ID, value = Dist.CLIENT)
public final class ClientLanguageSync {

    private ClientLanguageSync() {
    }

    /** Sends the current client language code when a player connection exists. */
    public static void sendNow() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options == null
                || ModNetwork.CHANNEL == null) {
            return;
        }
        // 1.20.1 Mojmap: Options.languageCode is a plain public String field
        // (the OptionInstance wrapper only arrived in later versions).
        String code = minecraft.options.languageCode;
        if (code != null && !code.isBlank()) {
            ModNetwork.CHANNEL.sendToServer(new LanguageC2S(code));
        }
    }

    @SubscribeEvent
    public static void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        sendNow();
    }
}
