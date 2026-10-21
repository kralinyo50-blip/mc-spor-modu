package com.sportcraft.entity.ai;

import com.sportcraft.entity.SoccerBallEntity;
import com.sportcraft.entity.SoccerNpcEntity;
import com.sportcraft.match.MatchManager;
import com.sportcraft.match.Team;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

/**
 * Server-side football brain. Outfield bots chase the nearest ball and kick
 * it toward the opponent goal; keepers guard their own goal line and only
 * rush out when the ball comes close.
 */
public class NpcPlayGoal extends Goal {

    private static final double BALL_SEARCH_RADIUS = 64.0D;
    private static final double KEEPER_RUSH_DISTANCE_SQR = 36.0D;
    private static final double KICK_DISTANCE_SQR = 2.6D;
    private static final int REPATH_INTERVAL = 15;
    private static final int KICK_LOCK_TICKS = 12;

    private final SoccerNpcEntity npc;
    private SoccerBallEntity ball;
    private int repathCooldown;
    private int kickCooldown;

    public NpcPlayGoal(SoccerNpcEntity npc) {
        this.npc = npc;
        setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        return !npc.level().isClientSide;
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

        BlockPos ownGoal = manager.getGoalPosition(npc.getSportTeam());
        BlockPos attackGoal = manager.getGoalPosition(npc.getSportTeam().opponent());
        if (ownGoal == null || attackGoal == null) {
            return;
        }

        Vec3 target = decideTarget(center, ownGoal, ball);
        double speed = npc.isKeeper() ? 1.15D : 1.22D;
        if (npc.distanceToSqr(target.x, target.y, target.z) > 1.4D) {
            npc.getNavigation().moveTo(target.x, target.y, target.z, speed);
        } else {
            npc.getNavigation().stop();
        }

        if (kickCooldown > 0) {
            kickCooldown--;
        }
        if (ball != null && kickCooldown == 0 && npc.distanceToSqr(ball) < KICK_DISTANCE_SQR) {
            Vec3 direction = Vec3.atBottomCenterOf(attackGoal).subtract(ball.position());
            ball.kickFromMob(npc, direction);
            kickCooldown = KICK_LOCK_TICKS;
        }
    }

    private Vec3 decideTarget(BlockPos center, BlockPos ownGoal, SoccerBallEntity activeBall) {
        if (npc.isKeeper()) {
            // Hold the line one and a half blocks in front of the own goal mouth.
            int inward = Integer.signum(center.getZ() - ownGoal.getZ());
            Vec3 guard = Vec3.atBottomCenterOf(ownGoal).add(0.0D, 0.0D, inward * 1.5D);
            if (activeBall != null && npc.distanceToSqr(activeBall) < KEEPER_RUSH_DISTANCE_SQR) {
                return activeBall.position();
            }
            return guard;
        }
        if (activeBall == null) {
            return Vec3.atBottomCenterOf(center);
        }
        return activeBall.position();
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
}
