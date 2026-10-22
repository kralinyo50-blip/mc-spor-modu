package com.mcsport.event;

import com.mcsport.McSport;
import com.mcsport.entity.SoccerNpcEntity;
import com.mcsport.registry.ModEntities;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Mod-bus handler that binds the default attribute supplier to the NPC
 * entity type. Required for every LivingEntity registered with Forge.
 */
@Mod.EventBusSubscriber(modid = McSport.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModEntityAttributes {

    private ModEntityAttributes() {
    }

    @SubscribeEvent
    public static void onAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(ModEntities.SOCCER_NPC.get(), SoccerNpcEntity.createAttributes().build());
    }
}
