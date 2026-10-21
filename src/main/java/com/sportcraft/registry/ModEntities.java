package com.sportcraft.registry;

import com.sportcraft.SportCraft;
import com.sportcraft.entity.SeatEntity;
import com.sportcraft.entity.SoccerBallEntity;
import com.sportcraft.entity.SoccerNpcEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, SportCraft.MOD_ID);

    public static final RegistryObject<EntityType<SoccerBallEntity>> SOCCER_BALL =
            ENTITIES.register("soccer_ball",
                    () -> EntityType.Builder.<SoccerBallEntity>of(SoccerBallEntity::new, MobCategory.MISC)
                            .sized(0.5F, 0.5F)
                            .clientTrackingRange(64)
                            .updateInterval(1)
                            .setShouldReceiveVelocityUpdates(true)
                            .build("soccer_ball"));

    public static final RegistryObject<EntityType<SeatEntity>> SEAT =
            ENTITIES.register("seat",
                    () -> EntityType.Builder.<SeatEntity>of(SeatEntity::new, MobCategory.MISC)
                            .sized(0.5F, 0.5F)
                            .clientTrackingRange(16)
                            .updateInterval(3)
                            .build("seat"));

    public static final RegistryObject<EntityType<SoccerNpcEntity>> SOCCER_NPC =
            ENTITIES.register("soccer_npc",
                    () -> EntityType.Builder.<SoccerNpcEntity>of(SoccerNpcEntity::new, MobCategory.MISC)
                            .sized(0.6F, 1.8F)
                            .clientTrackingRange(64)
                            .updateInterval(3)
                            .build("soccer_npc"));

    private ModEntities() {
    }
}
