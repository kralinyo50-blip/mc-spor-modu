package com.mcsport.registry;

import com.mcsport.McSport;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, McSport.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MCSPORT_TAB = TABS.register("mcsport_tab",
            () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                    .title(Component.translatable("itemGroup.mcsport.mcsport_tab"))
                    .icon(() -> new ItemStack(ModItems.SOCCER_BALL.get()))
                    .displayItems((parameters, output) -> {
                        // Balls and referee gear.
                        output.accept(ModItems.SOCCER_BALL.get());
                        output.accept(ModItems.BASKETBALL.get());
                        output.accept(ModItems.WHISTLE.get());
                        output.accept(ModItems.YELLOW_CARD.get());
                        output.accept(ModItems.RED_CARD.get());
                        // Field surface and markings.
                        output.accept(ModBlocks.ARTIFICIAL_TURF.get());
                        output.accept(ModBlocks.ARTIFICIAL_TURF_DARK.get());
                        output.accept(ModBlocks.FIELD_LINE.get());
                        // Goal construction.
                        output.accept(ModBlocks.GOAL_POST.get());
                        output.accept(ModBlocks.GOAL_NET.get());
                        // Stands and scoreboard.
                        output.accept(ModBlocks.RED_SEAT.get());
                        output.accept(ModBlocks.BLUE_SEAT.get());
                        output.accept(ModBlocks.YELLOW_SEAT.get());
                        output.accept(ModBlocks.GREEN_SEAT.get());
                        output.accept(ModBlocks.PURPLE_SEAT.get());
                        output.accept(ModBlocks.ORANGE_SEAT.get());
                        output.accept(ModBlocks.SCOREBOARD.get());
                        // Grand-stadium construction pieces.
                        output.accept(ModBlocks.STADIUM_WALL.get());
                        output.accept(ModBlocks.STADIUM_ROOF.get());
                        output.accept(ModBlocks.STADIUM_LAMP.get());
                        output.accept(ModBlocks.TROPHY_BLOCK.get());
                    })
                    .build());

    private ModCreativeTabs() {
    }
}
