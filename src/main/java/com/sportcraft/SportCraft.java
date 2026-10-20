package com.sportcraft;

import com.mojang.logging.LogUtils;
import com.sportcraft.event.ModGameEvents;
import com.sportcraft.network.ModNetwork;
import com.sportcraft.registry.ModBlockEntities;
import com.sportcraft.registry.ModBlocks;
import com.sportcraft.registry.ModCreativeTabs;
import com.sportcraft.registry.ModEntities;
import com.sportcraft.registry.ModItems;
import com.sportcraft.registry.ModSounds;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * SportCraft main entry point.
 *
 * <p>All registration happens through {@link net.minecraftforge.registries.DeferredRegister}
 * instances on the mod event bus. Game logic handlers live on the Forge event bus.</p>
 */
@Mod(SportCraft.MOD_ID)
public class SportCraft {

    public static final String MOD_ID = "sportcraft";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SportCraft() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModEntities.ENTITIES.register(modEventBus);
        ModSounds.SOUNDS.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);

        modEventBus.addListener(ModNetwork::register);

        MinecraftForge.EVENT_BUS.register(ModGameEvents.class);

        LOGGER.info("SportCraft loaded");
    }
}
