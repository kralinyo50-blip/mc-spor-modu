package com.sportcraft.client;

import com.sportcraft.SportCraft;
import com.sportcraft.client.screen.MatchScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-side Forge-bus events. Opens the match screen on key press once per
 * click, and only when no other screen is open.
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
    }
}
