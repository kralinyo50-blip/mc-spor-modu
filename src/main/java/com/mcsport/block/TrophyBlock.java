package com.mcsport.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Championship trophy statue: a three-dimensional cup on a marble plinth.
 * The outline follows the whole model (plinth, stepped base, stem, flared bowl
 * and both handles) so the selection box matches what the player sees, while
 * only the plinth blocks movement — the cup is a statue, not a wall.
 */
public class TrophyBlock extends Block {

    /** Marble plinth plus the gold base step (the part you actually collide with). */
    private static final VoxelShape PLINTH = Shapes.or(
            Block.box(2.0D, 0.0D, 2.0D, 14.0D, 2.0D, 14.0D),
            Block.box(3.0D, 2.0D, 3.0D, 13.0D, 3.0D, 13.0D));
    private static final VoxelShape STEM = Block.box(4.5D, 3.0D, 4.5D, 11.5D, 8.0D, 11.5D);
    private static final VoxelShape BOWL = Block.box(3.25D, 8.0D, 3.25D, 12.75D, 15.75D, 12.75D);
    private static final VoxelShape HANDLES = Block.box(1.2D, 9.8D, 6.5D, 14.8D, 14.2D, 9.5D);
    private static final VoxelShape SHAPE = Shapes.or(PLINTH, STEM, BOWL, HANDLES);

    public TrophyBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        return PLINTH;
    }
}
