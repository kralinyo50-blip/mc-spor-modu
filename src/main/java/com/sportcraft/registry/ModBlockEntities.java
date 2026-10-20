package com.sportcraft.registry;

import com.sportcraft.SportCraft;
import com.sportcraft.blockentity.GoalBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, SportCraft.MOD_ID);

    public static final RegistryObject<BlockEntityType<GoalBlockEntity>> GOAL =
            BLOCK_ENTITIES.register("goal",
                    () -> BlockEntityType.Builder.of(GoalBlockEntity::new,
                            ModBlocks.RED_GOAL.get(), ModBlocks.BLUE_GOAL.get()).build(null));

    private ModBlockEntities() {
    }
}
