package com.mcsport.client;

import com.mcsport.McSport;
import com.mcsport.client.renderer.SeatRenderer;
import com.mcsport.client.renderer.SoccerBallRenderer;
import com.mcsport.client.renderer.SoccerNpcRenderer;
import com.mcsport.registry.ModBlocks;
import com.mcsport.registry.ModEntities;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client-only mod-bus registrations: entity renderers, key mapping, HUD overlay
 * and the cutout render layer for the net / field line blocks.
 */
@Mod.EventBusSubscriber(modid = McSport.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.SOCCER_BALL.get(), SoccerBallRenderer::new);
        event.registerEntityRenderer(ModEntities.SEAT.get(), SeatRenderer::new);
        event.registerEntityRenderer(ModEntities.SOCCER_NPC.get(), SoccerNpcRenderer::new);
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(KeyBindings.OPEN_MATCH_MENU);
        event.register(KeyBindings.SLIDE_TACKLE);
    }

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("match_hud", HudOverlay.MATCH_HUD);
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.RED_GOAL.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.BLUE_GOAL.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.FIELD_LINE.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.GOAL_NET.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.RED_SEAT.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.BLUE_SEAT.get(), RenderType.cutout());
        });
    }
}
