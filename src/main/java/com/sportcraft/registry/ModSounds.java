package com.sportcraft.registry;

import com.sportcraft.SportCraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Custom sound events. The actual mappings in {@code sounds.json} reuse vanilla
 * sound files, so the mod ships no binary audio assets.
 */
public class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, SportCraft.MOD_ID);

    public static final RegistryObject<SoundEvent> KICK = register("kick");
    public static final RegistryObject<SoundEvent> BOUNCE = register("bounce");
    public static final RegistryObject<SoundEvent> GOAL_HORN = register("goal_horn");
    public static final RegistryObject<SoundEvent> WHISTLE = register("whistle");

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name,
                () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(SportCraft.MOD_ID, name)));
    }

    private ModSounds() {
    }
}
