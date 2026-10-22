package com.mcsport.block;

import com.mcsport.match.MatchManager;
import com.mcsport.match.MatchState;
import com.mcsport.match.Team;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Stadium scoreboard block. Right-click reads the current server-authoritative
 * match score and prints it to the player's action bar, so spectators can
 * check the score without opening the setup GUI.
 */
public class ScoreboardBlock extends Block {

    private static final VoxelShape SHAPE = Block.box(1.0D, 0.0D, 6.0D, 15.0D, 14.0D, 10.0D);

    public ScoreboardBlock(Properties properties) {
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
