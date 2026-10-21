package com.sportcraft.block;

import net.minecraft.world.level.block.Block;

/**
 * Goal netting. Visually a cutout-transparent grid but fully solid for
 * collision, so the custom ball physics treat the back and sides of the
 * goal as a wall the ball cannot leave through.
 */
public class GoalNetBlock extends Block {

    public GoalNetBlock(Properties properties) {
        super(properties);
    }
}
