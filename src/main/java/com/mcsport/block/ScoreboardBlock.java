package com.mcsport.block;

import com.mcsport.blockentity.ScoreboardBlockEntity;
import com.mcsport.match.MatchManager;
import com.mcsport.match.MatchState;
import com.mcsport.match.Team;
import com.mcsport.registry.ModBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
import org.jetbrains.annotations.Nullable;

/**
 * Stadium scoreboard block. The panel is directional, so a board can be aimed
 * at the pitch (the auto-built stadium mounts it on the sideline facing west).
 * Right-click prints the current server-authoritative score to the player's
 * action bar; the block entity paints the same live score on the panel for
 * every nearby client.
 */
public class ScoreboardBlock extends HorizontalDirectionalBlock implements EntityBlock {

    private static final VoxelShape NORTH_SOUTH = Block.box(1.0D, 0.0D, 5.0D, 15.0D, 16.0D, 11.0D);
    private static final VoxelShape EAST_WEST = Block.box(5.0D, 0.0D, 1.0D, 11.0D, 16.0D, 15.0D);

    public ScoreboardBlock(Properties properties) {
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

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ScoreboardBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> blockEntityType) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(blockEntityType, ModBlockEntities.SCOREBOARD.get(),
                (tickLevel, tickPos, tickState, board) -> board.serverTick(tickLevel));
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static <E extends BlockEntity, A extends BlockEntity> BlockEntityTicker<A> createTickerHelper(
            BlockEntityType<A> givenType, BlockEntityType<E> expectedType, BlockEntityTicker<? super E> ticker) {
        return expectedType.equals(givenType) ? (BlockEntityTicker<A>) ticker : null;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        MatchManager manager = MatchManager.get((ServerLevel) level);
        Component score = Component.translatable("message.mcsport.scoreboard_line",
                Component.translatable(Team.RED.translationKey()),
                manager.getRedScore(), manager.getBlueScore(),
                Component.translatable(Team.BLUE.translationKey()))
                .withStyle(ChatFormatting.GOLD);
        player.displayClientMessage(score, true);
        if (manager.getState() != MatchState.RUNNING) {
            player.displayClientMessage(
                    Component.translatable("message.mcsport.scoreboard_not_running")
                            .withStyle(ChatFormatting.GRAY), true);
        }
        return InteractionResult.CONSUME;
    }
}
