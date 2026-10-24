package com.mcsport;

import com.mojang.logging.LogUtils;
import com.mcsport.advancement.ModCriteriaTriggers;
import com.mcsport.event.ModGameEvents;
import com.mcsport.network.ModNetwork;
import com.mcsport.registry.ModBlockEntities;
import com.mcsport.registry.ModBlocks;
import com.mcsport.registry.ModCreativeTabs;
import com.mcsport.registry.ModEntities;
import com.mcsport.registry.ModItems;
import com.mcsport.registry.ModSounds;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * McSport main entry point.
 *
 * <p>All registration happens through {@link net.minecraftforge.registries.DeferredRegister}
 * instances on the mod event bus. Game logic handlers live on the Forge event bus.</p>
 */
@Mod(McSport.MOD_ID)
public class McSport {

    public static final String MOD_ID = "mcsport";
    public static final Logger LOGGER = LogUtils.getLogger();

    public McSport() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModEntities.ENTITIES.register(modEventBus);
        ModSounds.SOUNDS.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);

        modEventBus.addListener(ModNetwork::register);

        // Register custom advancement triggers before any world load.
        ModCriteriaTriggers.init();

        MinecraftForge.EVENT_BUS.register(ModGameEvents.class);

        LOGGER.info("McSport loaded");
    }
}
