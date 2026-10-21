package com.sportcraft.entity.ai;

import com.sportcraft.entity.SoccerBallEntity;
import com.sportcraft.entity.SoccerNpcEntity;
import com.sportcraft.match.MatchManager;
import com.sportcraft.match.MatchState;
import com.sportcraft.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

/**
 * Brain of the on-pitch referee. The referee follows the play from a
 * sideline-biased position and turns the server-authoritative match events
 * (kick-off, goals, reported fouls) into visible decisions: a whistle sound,
 * a raised-arm signal animation and a broadcast chat message.
 *
 * <p>All events are produced by {@link MatchManager}; this goal only reacts,
 * so it can never invent a goal or override a score. Events are de-duplicated
 * by score snapshots and the manager's monotonically increasing foul tick.</p>
 */
public class RefereeGoal extends Goal {

    private static final double BALL_SEARCH_RADIUS = 64.0D;
    private static final int REPATH_INTERVAL = 10;
    private static final double SIDELINE_OFFSET = 3.5D;
    private static final double TRAIL_OFFSET = 3.0D;

    private final SoccerNpcEntity npc;
    private SoccerBallEntity ball;
    private int repathCooldown;
    private boolean observed;
    private int lastRedScore;
    private int lastBlueScore;
    private MatchState lastState = MatchState.IDLE;
    private int lastHandledFoulTick = Integer.MIN_VALUE;

    public RefereeGoal(SoccerNpcEntity npc) {
        this.npc = npc;
        setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return npc.isReferee() && !npc.level().isClientSide;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void tick() {
        if (!(npc.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        MatchManager manager = MatchManager.get(serverLevel);
        BlockPos center = manager.getFieldCenter();
        if (center == null) {
            return;
        }

        if (--repathCooldown <= 0 || ball == null || !ball.isAlive()) {
            repathCooldown = REPATH_INTERVAL;
            ball = findBall(serverLevel, center);
        }

        handleDecisions(serverLevel, manager);
        handleMovement(center);
    }

    private void handleDecisions(ServerLevel serverLevel, MatchManager manager) {
        MatchState state = manager.getState();

        // First observation seeds the snapshots; no historical goals or fouls
        // are announced. lastState stays IDLE on purpose so a referee spawned
        // together with kick-off still blows the starting whistle.
        if (!observed) {
            observed = true;
            lastRedScore = manager.getRedScore();
            lastBlueScore = manager.getBlueScore();
            lastHandledFoulTick = manager.getLastFoulTick();
            lastState = MatchState.IDLE;
        }

        if (state == MatchState.RUNNING && lastState != MatchState.RUNNING) {
            whistle(serverLevel);
            npc.raiseSignal();
            broadcast(serverLevel, Component.translatable("message.sportcraft.referee_kickoff")
                    .withStyle(ChatFormatting.YELLOW));
        }

        if (manager.getRedScore() != lastRedScore || manager.getBlueScore() != lastBlueScore) {
            boolean redScored = manager.getRedScore() != lastRedScore;
            lastRedScore = manager.getRedScore();
            lastBlueScore = manager.getBlueScore();
            whistle(serverLevel);
            npc.raiseSignal();
            Component team = Component.translatable((redScored
                    ? com.sportcraft.match.Team.RED : com.sportcraft.match.Team.BLUE).translationKey());
            broadcast(serverLevel, Component.translatable("message.sportcraft.referee_goal",
                            team, manager.getRedScore(), manager.getBlueScore())
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        }

        if (manager.getLastFoulTick() > lastHandledFoulTick) {
            lastHandledFoulTick = manager.getLastFoulTick();
            whistle(serverLevel);
            npc.raiseSignal();
            Component team = Component.translatable(manager.getLastFoulTeam().translationKey());
            broadcast(serverLevel, Component.translatable("message.sportcraft.referee_foul", team)
                    .withStyle(ChatFormatting.RED));
        }

        lastState = state;
    }

    private void handleMovement(BlockPos center) {
        Vec3 target;
        if (ball != null) {
            // Stay behind the ball relative to the centre spot and drift toward
            // the sideline, keeping the whole pitch visible instead of joining
            // the scrum on top of the ball.
            double trailX = Math.signum(ball.getX() - (center.getX() + 0.5D)) * SIDELINE_OFFSET;
            double trailZ = Math.signum(center.getZ() + 0.5D - ball.getZ()) * TRAIL_OFFSET;
            target = new Vec3(ball.getX() + trailX, center.getY(), ball.getZ() + trailZ);
        } else {
            target = Vec3.atBottomCenterOf(center);
        }
        if (npc.distanceToSqr(target) > 1.4D) {
            npc.getNavigation().moveTo(target.x, target.y, target.z, 1.15D);
        } else {
            npc.getNavigation().stop();
        }
        Vec3 look = ball != null ? ball.position() : target;
        npc.getLookControl().setLookAt(look.x, look.y + 1.0D, look.z);
    }

    private SoccerBallEntity findBall(ServerLevel level, BlockPos center) {
        AABB searchArea = new AABB(center).inflate(BALL_SEARCH_RADIUS);
        List<SoccerBallEntity> balls = level.getEntitiesOfClass(SoccerBallEntity.class, searchArea);
        SoccerBallEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (SoccerBallEntity candidate : balls) {
            double distance = npc.distanceToSqr(candidate);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = candidate;
            }
        }
        return nearest;
    }

    private void whistle(ServerLevel serverLevel) {
        serverLevel.playSound(null, npc.blockPosition(), ModSounds.WHISTLE.get(),
                SoundSource.PLAYERS, 1.5F, 1.0F);
    }

    private void broadcast(ServerLevel serverLevel, Component message) {
        for (ServerPlayer player : serverLevel.getServer().getPlayerList().getPlayers()) {
            player.sendSystemMessage(message);
        }
    }
}
