package com.sportcraft.registry;

import com.sportcraft.SportCraft;
import com.sportcraft.item.CardItem;
import com.sportcraft.item.SportsBallItem;
import com.sportcraft.item.WhistleItem;
import com.sportcraft.match.BallType;
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
            () -> new SportsBallItem(new Item.Properties().stacksTo(16), BallType.SOCCER));

    public static final RegistryObject<Item> BASKETBALL = ITEMS.register("basketball",
            () -> new SportsBallItem(new Item.Properties().stacksTo(16), BallType.BASKETBALL));

    public static final RegistryObject<Item> WHISTLE = ITEMS.register("whistle",
            () -> new WhistleItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> YELLOW_CARD = ITEMS.register("yellow_card",
            () -> new CardItem(new Item.Properties().stacksTo(1), false));

    public static final RegistryObject<Item> RED_CARD = ITEMS.register("red_card",
            () -> new CardItem(new Item.Properties().stacksTo(1), true));

    public static final RegistryObject<Item> ARTIFICIAL_TURF_ITEM =
            registerBlockItem("artificial_turf", ModBlocks.ARTIFICIAL_TURF);
    public static final RegistryObject<Item> ARTIFICIAL_TURF_DARK_ITEM =
            registerBlockItem("artificial_turf_dark", ModBlocks.ARTIFICIAL_TURF_DARK);
    public static final RegistryObject<Item> FIELD_LINE_ITEM =
            registerBlockItem("field_line", ModBlocks.FIELD_LINE);
    public static final RegistryObject<Item> RED_GOAL_ITEM =
            registerBlockItem("red_goal", ModBlocks.RED_GOAL);
    public static final RegistryObject<Item> BLUE_GOAL_ITEM =
            registerBlockItem("blue_goal", ModBlocks.BLUE_GOAL);
    public static final RegistryObject<Item> GOAL_POST_ITEM =
            registerBlockItem("goal_post", ModBlocks.GOAL_POST);
    public static final RegistryObject<Item> GOAL_NET_ITEM =
            registerBlockItem("goal_net", ModBlocks.GOAL_NET);
    public static final RegistryObject<Item> RED_SEAT_ITEM =
            registerBlockItem("red_seat", ModBlocks.RED_SEAT);
    public static final RegistryObject<Item> BLUE_SEAT_ITEM =
            registerBlockItem("blue_seat", ModBlocks.BLUE_SEAT);
    public static final RegistryObject<Item> SCOREBOARD_ITEM =
            registerBlockItem("scoreboard", ModBlocks.SCOREBOARD);
    public static final RegistryObject<Item> YELLOW_SEAT_ITEM =
            registerBlockItem("yellow_seat", ModBlocks.YELLOW_SEAT);
    public static final RegistryObject<Item> GREEN_SEAT_ITEM =
            registerBlockItem("green_seat", ModBlocks.GREEN_SEAT);
    public static final RegistryObject<Item> PURPLE_SEAT_ITEM =
            registerBlockItem("purple_seat", ModBlocks.PURPLE_SEAT);
    public static final RegistryObject<Item> ORANGE_SEAT_ITEM =
            registerBlockItem("orange_seat", ModBlocks.ORANGE_SEAT);
    public static final RegistryObject<Item> STADIUM_WALL_ITEM =
            registerBlockItem("stadium_wall", ModBlocks.STADIUM_WALL);
    public static final RegistryObject<Item> STADIUM_ROOF_ITEM =
            registerBlockItem("stadium_roof", ModBlocks.STADIUM_ROOF);
    public static final RegistryObject<Item> STADIUM_LAMP_ITEM =
            registerBlockItem("stadium_lamp", ModBlocks.STADIUM_LAMP);
    public static final RegistryObject<Item> TROPHY_BLOCK_ITEM =
            registerBlockItem("trophy_block", ModBlocks.TROPHY_BLOCK);

    private static RegistryObject<Item> registerBlockItem(String name, RegistryObject<Block> block) {
        return ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    private ModItems() {
    }
}
