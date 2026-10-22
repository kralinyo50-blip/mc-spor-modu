package com.sportcraft.client;

import com.sportcraft.SportCraft;
import com.sportcraft.client.screen.MatchScreen;
import com.sportcraft.network.ModNetwork;
import com.sportcraft.network.network_packet.RequestPassC2S;
import com.sportcraft.network.network_packet.SlideTackleC2S;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-side Forge-bus events. Opens the match screen on key press once per
 * click, and sends the slide-tackle request while no screen blocks input.
 * The server validates and applies every tackle, so this only sends intent.
 */
@Mod.EventBusSubscriber(modid = SportCraft.MOD_ID, value = Dist.CLIENT)
public class ClientForgeEvents {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        while (KeyBindings.OPEN_MATCH_MENU.consumeClick()) {
            if (minecraft.screen == null && minecraft.player != null) {
                minecraft.setScreen(new MatchScreen());
            }
        }
        while (KeyBindings.SLIDE_TACKLE.consumeClick()) {
            if (minecraft.screen == null && minecraft.player != null) {
                ModNetwork.CHANNEL.sendToServer(new SlideTackleC2S());
            }
        }
        while (KeyBindings.REQUEST_PASS.consumeClick()) {
            if (minecraft.screen == null && minecraft.player != null) {
                ModNetwork.CHANNEL.sendToServer(new RequestPassC2S());
            }
        }
    }
}
