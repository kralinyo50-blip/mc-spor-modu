package com.mcsport.registry;

import com.mcsport.McSport;
import com.mcsport.block.ArtificialTurfBlock;
import com.mcsport.block.FieldLineBlock;
import com.mcsport.block.GoalNetBlock;
import com.mcsport.block.GoalPostBlock;
import com.mcsport.block.ScoreboardBlock;
import com.mcsport.block.SeatBlock;
import com.mcsport.block.SoccerGoalBlock;
import com.mcsport.match.Club;
import com.mcsport.match.Team;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, McSport.MOD_ID);

    public static final RegistryObject<Block> ARTIFICIAL_TURF = BLOCKS.register("artificial_turf",
            () -> new ArtificialTurfBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GREEN)
                    .strength(0.6F)
                    .sound(SoundType.GRASS)));

    public static final RegistryObject<Block> ARTIFICIAL_TURF_DARK = BLOCKS.register("artificial_turf_dark",
            () -> new ArtificialTurfBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GREEN)
                    .strength(0.6F)
                    .sound(SoundType.GRASS)));

    public static final RegistryObject<Block> FIELD_LINE = BLOCKS.register("field_line",
            () -> new FieldLineBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.SNOW)
                    .strength(0.2F)
                    .sound(SoundType.WOOL)));

    public static final RegistryObject<Block> RED_GOAL = BLOCKS.register("red_goal",
            () -> new SoccerGoalBlock(Team.RED, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED)
                    .strength(2.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()));

    public static final RegistryObject<Block> BLUE_GOAL = BLOCKS.register("blue_goal",
            () -> new SoccerGoalBlock(Team.BLUE, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLUE)
                    .strength(2.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()));

    public static final RegistryObject<Block> GOAL_POST = BLOCKS.register("goal_post",
            () -> new GoalPostBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.5F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()));

    public static final RegistryObject<Block> GOAL_NET = BLOCKS.register("goal_net",
            () -> new GoalNetBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.SNOW)
                    .strength(0.4F)
                    .sound(SoundType.WOOL)
                    .noOcclusion()));

    public static final RegistryObject<Block> RED_SEAT = BLOCKS.register("red_seat",
            () -> new SeatBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED)
                    .strength(0.8F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()));

    public static final RegistryObject<Block> BLUE_SEAT = BLOCKS.register("blue_seat",
            () -> new SeatBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLUE)
                    .strength(0.8F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()));

    public static final RegistryObject<Block> SCOREBOARD = BLOCKS.register("scoreboard",
            () -> new ScoreboardBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(1.5F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()));

    // Extra club-coloured seats for the six selectable teams.
    public static final RegistryObject<Block> YELLOW_SEAT = BLOCKS.register("yellow_seat",
            () -> new SeatBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_YELLOW)
                    .strength(0.8F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()));

    public static final RegistryObject<Block> GREEN_SEAT = BLOCKS.register("green_seat",
            () -> new SeatBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GREEN)
                    .strength(0.8F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()));

    public static final RegistryObject<Block> PURPLE_SEAT = BLOCKS.register("purple_seat",
            () -> new SeatBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(0.8F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()));

    public static final RegistryObject<Block> ORANGE_SEAT = BLOCKS.register("orange_seat",
            () -> new SeatBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE)
                    .strength(0.8F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()));

    // Stadium construction blocks for the high-tier arena builds.
    public static final RegistryObject<Block> STADIUM_WALL = BLOCKS.register("stadium_wall",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(2.0F)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()));

    public static final RegistryObject<Block> STADIUM_ROOF = BLOCKS.register("stadium_roof",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(0.8F)
                    .sound(SoundType.GLASS)
                    .noOcclusion()));

    public static final RegistryObject<Block> STADIUM_LAMP = BLOCKS.register("stadium_lamp",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_YELLOW)
                    .strength(0.6F)
                    .sound(SoundType.GLASS)
                    .lightLevel(state -> 15)));

    public static final RegistryObject<Block> TROPHY_BLOCK = BLOCKS.register("trophy_block",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.GOLD)
                    .strength(1.2F)
                    .sound(SoundType.METAL)
                    .noOcclusion()));

    /** Coloured seat block matching a selectable club. */
    public static RegistryObject<Block> seatFor(Club club) {
        return switch (club) {
            case BLUE -> BLUE_SEAT;
            case YELLOW -> YELLOW_SEAT;
            case GREEN -> GREEN_SEAT;
            case PURPLE -> PURPLE_SEAT;
            case ORANGE -> ORANGE_SEAT;
            case RED -> RED_SEAT;
        };
    }

    private ModBlocks() {
    }
}
