package com.sportcraft.registry;

import com.sportcraft.SportCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SportCraft.MOD_ID);

    public static final RegistryObject<CreativeModeTab> SPORTCRAFT_TAB = TABS.register("sportcraft_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.sportcraft.sportcraft_tab"))
                    .icon(() -> new ItemStack(ModItems.SOCCER_BALL.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.SOCCER_BALL.get());
                        output.accept(ModItems.WHISTLE.get());
                        output.accept(ModBlocks.ARTIFICIAL_TURF.get());
                        output.accept(ModBlocks.FIELD_LINE.get());
                        output.accept(ModBlocks.RED_GOAL.get());
                        output.accept(ModBlocks.BLUE_GOAL.get());
                    })
                    .build());

    private ModCreativeTabs() {
    }
}
