package com.mcsport.block;

import com.mcsport.blockentity.AdBoardBlockEntity;
import com.mcsport.client.ClientScreenOpener;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;

/**
 * Double-sided stadium LED advertising panel. Right-click opens a text entry
 * screen on the client; the typed text travels over a validated, operator-gated
 * packet and is stored by {@link AdBoardBlockEntity}. Both faces of the panel
 * display the same scrolling message, so the board reads correctly from either
 * side of the pitch. No ticker is needed: updates are event-driven through the
 * block entity sync packet.
 */
public class AdBoardBlock extends HorizontalDirectionalBlock implements EntityBlock {

    /** Panel body incl. the supporting plinth; thin along the facing axis. */
    private static final VoxelShape NORTH_SOUTH = Block.box(1.0D, 0.0D, 5.0D, 15.0D, 16.0D, 11.0D);
    private static final VoxelShape EAST_WEST = Block.box(5.0D, 0.0D, 1.0D, 11.0D, 16.0D, 15.0D);

    public AdBoardBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.HORIZONTAL_FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,
                context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        return facing.getAxis() == Direction.Axis.X ? EAST_WEST : NORTH_SOUTH;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            // The screen reads the current text and shows whether this player
            // may save; the packet handler re-checks permissions server side.
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ClientScreenOpener.openAdBoard(pos));
            return InteractionResult.SUCCESS;
        }
        if (!player.hasPermissions(2)) {
            player.displayClientMessage(
                    Component.translatable("message.mcsport.ad_board.no_permission")
                            .withStyle(ChatFormatting.GOLD), true);
        }
        return InteractionResult.CONSUME;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AdBoardBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> blockEntityType) {
        // Event-driven sync only; no per-tick work.
        return null;
    }
}
