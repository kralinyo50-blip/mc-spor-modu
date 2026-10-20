package com.sportcraft.block;

import net.minecraft.world.level.block.CarpetBlock;

/**
 * Thin white field marking. Reuses the vanilla carpet model/collision so the
 * ball rolls over it without any extra physics code.
 */
public class FieldLineBlock extends CarpetBlock {

    public FieldLineBlock(Properties properties) {
        super(properties);
    }
}
