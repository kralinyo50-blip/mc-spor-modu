package com.sportcraft.registry;

import com.sportcraft.SportCraft;
import com.sportcraft.item.SoccerBallItem;
import com.sportcraft.item.WhistleItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, SportCraft.MOD_ID);

    public static final RegistryObject<Item> SOCCER_BALL = ITEMS.register("soccer_ball",
            () -> new SoccerBallItem(new Item.Properties().stacksTo(16)));

    public static final RegistryObject<Item> WHISTLE = ITEMS.register("whistle",
            () -> new WhistleItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> ARTIFICIAL_TURF_ITEM =
            registerBlockItem("artificial_turf", ModBlocks.ARTIFICIAL_TURF);
    public static final RegistryObject<Item> FIELD_LINE_ITEM =
            registerBlockItem("field_line", ModBlocks.FIELD_LINE);
    public static final RegistryObject<Item> RED_GOAL_ITEM =
            registerBlockItem("red_goal", ModBlocks.RED_GOAL);
    public static final RegistryObject<Item> BLUE_GOAL_ITEM =
            registerBlockItem("blue_goal", ModBlocks.BLUE_GOAL);

    private static RegistryObject<Item> registerBlockItem(String name, RegistryObject<Block> block) {
        return ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    private ModItems() {
    }
}
