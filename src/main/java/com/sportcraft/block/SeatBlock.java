package com.sportcraft.block;

import com.sportcraft.entity.SeatEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Chair-shaped stadium seat. Four legs, a slab and a backrest; right-clicking
 * mounts the player on an invisible {@link SeatEntity} so the sitting pose is
 * the vanilla riding pose. Coloured variants share this one class.
 */
public class SeatBlock extends Block {

    private static final VoxelShape LEG_NW = Block.box(0.0D, 0.0D, 0.0D, 2.0D, 8.0D, 2.0D);
    private static final VoxelShape LEG_NE = Block.box(14.0D, 0.0D, 0.0D, 16.0D, 8.0D, 2.0D);
    private static final VoxelShape LEG_SW = Block.box(0.0D, 0.0D, 14.0D, 2.0D, 8.0D, 16.0D);
    private static final VoxelShape LEG_SE = Block.box(14.0D, 0.0D, 14.0D, 16.0D, 8.0D, 16.0D);
    private static final VoxelShape SEAT = Block.box(0.0D, 8.0D, 0.0D, 16.0D, 10.0D, 16.0D);
    private static final VoxelShape BACKREST = Block.box(0.0D, 10.0D, 13.0D, 16.0D, 16.0D, 16.0D);

    private static final VoxelShape SHAPE = Shapes.or(LEG_NW, LEG_NE, LEG_SW, LEG_SE, SEAT, BACKREST);

    public SeatBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player.isPassenger()) {
            return InteractionResult.PASS;
        }
        SeatEntity seat = SeatEntity.getOrCreate(level, pos);
        if (seat != null) {
            player.startRiding(seat);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }
}
