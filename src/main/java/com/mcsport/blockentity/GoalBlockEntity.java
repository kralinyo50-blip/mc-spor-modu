package com.mcsport.blockentity;

import com.mcsport.block.SoccerGoalBlock;
import com.mcsport.entity.SoccerBallEntity;
import com.mcsport.match.MatchManager;
import com.mcsport.match.Team;
import com.mcsport.registry.ModBlockEntities;
import com.mcsport.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

/**
 * Server-side goal detector. Only runs on the logical server, performs one
 * small {@link Level#getEntitiesOfClass} query per tick and guards against
 * double scoring with per-block and per-ball cooldowns.
 */
public class GoalBlockEntity extends BlockEntity {

    private int detectionCooldown;

    public GoalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GOAL.get(), pos, state);
    }

    public void serverTick(Level level) {
        if (detectionCooldown > 0) {
            detectionCooldown--;
            return;
        }
        if (!(getBlockState().getBlock() instanceof SoccerGoalBlock goalBlock)) {
            return;
        }
        Direction facing = getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
        AABB goalArea = buildGoalArea(facing);

        List<SoccerBallEntity> balls = level.getEntitiesOfClass(SoccerBallEntity.class, goalArea);
        for (SoccerBallEntity ball : balls) {
            // A ball held in a keeper's hands must never count as a goal.
            if (ball.getGoalCooldown() > 0 || ball.isCarried()) {
                continue;
            }
            registerGoal(level, ball, goalBlock.getTeam());
            break;
        }
    }

    private AABB buildGoalArea(Direction facing) {
        AABB blockBox = new AABB(worldPosition).inflate(0.05D);
        AABB expanded = blockBox.expandTowards(facing.getStepX() * 1.5D, 0.0D, facing.getStepZ() * 1.5D);
        if (facing.getAxis() == Direction.Axis.Z) {
            return expanded.inflate(2.0D, 1.0D, 0.0D);
        }
        return expanded.inflate(0.0D, 1.0D, 2.0D);
    }

    private void registerGoal(Level level, SoccerBallEntity ball, Team defendingTeam) {
        ServerLevel serverLevel = (ServerLevel) level;
        Team scoringTeam = defendingTeam.opponent();

        boolean counted = MatchManager.get(serverLevel).onGoal(scoringTeam);
        MatchManager.get(serverLevel).broadcast(serverLevel.getServer());

        level.playSound(null, worldPosition, ModSounds.GOAL_HORN.get(),
                SoundSource.BLOCKS, 2.0F, counted ? 1.0F : 0.8F);

        // Prefer the centre of the active field; fall back to the world spawn.
        BlockPos spawn = MatchManager.get(serverLevel).getFieldCenter();
        if (spawn == null) {
            spawn = serverLevel.getSharedSpawnPos();
        }
        ball.resetTo(spawn);
        detectionCooldown = 40;
        setChanged();
    }
}
