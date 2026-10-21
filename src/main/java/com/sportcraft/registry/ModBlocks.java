package com.sportcraft.registry;

import com.sportcraft.SportCraft;
import com.sportcraft.block.ArtificialTurfBlock;
import com.sportcraft.block.FieldLineBlock;
import com.sportcraft.block.ScoreboardBlock;
import com.sportcraft.block.SeatBlock;
import com.sportcraft.block.SoccerGoalBlock;
import com.sportcraft.match.Team;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, SportCraft.MOD_ID);

    public static final RegistryObject<Block> ARTIFICIAL_TURF = BLOCKS.register("artificial_turf",
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

    public static final RegistryObject<Block> RED_SEAT = BLOCKS.register("red_seat",
            () -> new SeatBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED)
                    .strength(0.8F)
                    .sound(SoundType.WOOD)));

    public static final RegistryObject<Block> BLUE_SEAT = BLOCKS.register("blue_seat",
            () -> new SeatBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLUE)
                    .strength(0.8F)
                    .sound(SoundType.WOOD)));

    public static final RegistryObject<Block> SCOREBOARD = BLOCKS.register("scoreboard",
            () -> new ScoreboardBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(1.5F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()));

    private ModBlocks() {
    }
}
