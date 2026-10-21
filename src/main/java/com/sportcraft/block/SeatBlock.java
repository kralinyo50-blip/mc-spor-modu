package com.sportcraft.block;

import net.minecraft.world.level.block.Block;

/**
 * Decorative stadium seat. Coloured variants (red/blue) are registered as
 * separate instances; behaviour is intentionally plain so the block can also
 * serve as a solid building piece for stands.
 */
public class SeatBlock extends Block {

    public SeatBlock(Properties properties) {
        super(properties);
    }
}
