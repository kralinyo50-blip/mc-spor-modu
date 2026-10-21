package com.sportcraft.entity.ai;

import com.sportcraft.entity.SoccerBallEntity;
import com.sportcraft.entity.SoccerNpcEntity;
import com.sportcraft.match.FieldSize;
import com.sportcraft.match.Formation;
import com.sportcraft.match.FormationLayout;
import com.sportcraft.match.MatchManager;
import com.sportcraft.match.MatchSettings;
import com.sportcraft.match.Team;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

/**
 * Server-side football brain with explicit roles instead of a whole team
 * chasing the ball:
 *
 * <ul>
 *   <li><b>Goalkeeper</b> holds the goal line and only rushes out when the
 *       ball comes into the penalty area.</li>
 *   <li><b>One chaser per team</b> — the outfield bot closest to the ball —
 *       presses and kicks it toward the opponent goal.</li>
 *   <li><b>Everyone else</b> holds their {@link FormationLayout} home slot,
 *       edging toward the ball only while it is on their own half.</li>
 * </ul>
 */
public class NpcPlayGoal extends Goal {

    private static final double BALL_SEARCH_RADIUS = 64.0D;
    private static final double KEEPER_RUSH_DISTANCE_SQR = 36.0D;
    private static final double KICK_DISTANCE_SQR = 2.8D;
    private static final double HOME_DRIFT_DISTANCE_SQR = 25.0D;
    private static final int REPATH_INTERVAL = 12;
    private static final int KICK_LOCK_TICKS = 14;

    private final SoccerNpcEntity npc;
    private SoccerBallEntity ball;
    private int repathCooldown;
    private int kickCooldown;
    private int roleCooldown;
    private boolean cachedChaser;

    public NpcPlayGoal(SoccerNpcEntity npc) {
        this.npc = npc;
        setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP, Goal.Flag.LOOK));
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

        if (--roleCooldown <= 0 || ball == null) {
            roleCooldown = 10;
            cachedChaser = isChaser(serverLevel, center);
        }

        MatchSettings settings = manager.getSettings();
        Vec3 target = decideTarget(serverLevel, manager, settings, center, ownGoal);
        double speed = npc.isKeeper() ? 1.12D : (cachedChaser ? 1.28D : 1.05D);
        if (npc.distanceToSqr(target.x, target.y, target.z) > 1.2D) {
            npc.getNavigation().moveTo(target.x, target.y, target.z, speed);
        } else {
            npc.getNavigation().stop();
        }
        npc.getLookControl().setLookAt(target.x, target.y + 1.0D, target.z);

        if (kickCooldown > 0) {
            kickCooldown--;
        }
        if (ball != null && kickCooldown == 0 && npc.distanceToSqr(ball) < KICK_DISTANCE_SQR) {
            Vec3 direction = Vec3.atBottomCenterOf(attackGoal).subtract(ball.position());
            ball.kickFromMob(npc, direction);
            kickCooldown = KICK_LOCK_TICKS;
        }
    }

    private Vec3 decideTarget(ServerLevel level, MatchManager manager, MatchSettings settings,
                              BlockPos center, BlockPos ownGoal) {
        FieldSize size = settings.getFieldSize();
        Formation formation = npc.getSportTeam() == Team.RED
                ? settings.getHomeFormation() : settings.getAwayFormation();
        Vec3 home = FormationLayout.homePosition(center, size, formation, npc.getSportTeam(),
                npc.getSquadIndex(), settings.getTeamSize(), npc.isKeeper());

        if (npc.isKeeper()) {
            int inward = Integer.signum(center.getZ() - ownGoal.getZ());
            Vec3 guard = Vec3.atBottomCenterOf(ownGoal).add(0.0D, 0.0D, inward * 1.5D);
            if (ball != null && npc.distanceToSqr(ball) < KEEPER_RUSH_DISTANCE_SQR) {
                return ball.position();
            }
            return guard;
        }

        if (ball == null) {
            return home;
        }

        // Only the closest outfield bot of the team presses; the rest keep shape.
        if (cachedChaser) {
            return ball.position();
        }

        // Supporting run: drift toward the ball only while it is on our half.
        // Product of the two signed offsets is positive exactly when they
        // point the same way (ball and own goal on the same side of centre).
        boolean ballOnOwnHalf = (ball.getZ() - center.getZ())
                * (ownGoal.getZ() - center.getZ()) > 0.0D;
        if (ballOnOwnHalf && npc.distanceToSqr(ball) > HOME_DRIFT_DISTANCE_SQR) {
            return home.add(ball.position().subtract(home).scale(0.25D));
        }
        return home;
    }

    /** The outfield bot of this team nearest to the ball does the pressing. */
    private boolean isChaser(ServerLevel level, BlockPos center) {
        if (npc.isKeeper() || ball == null) {
            return false;
        }
        AABB searchArea = new AABB(center).inflate(BALL_SEARCH_RADIUS);
        SoccerNpcEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (SoccerNpcEntity candidate : level.getEntitiesOfClass(SoccerNpcEntity.class, searchArea)) {
            if (candidate.getSportTeam() != npc.getSportTeam() || candidate.isKeeper()) {
                continue;
            }
            double distance = candidate.distanceToSqr(ball);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = candidate;
            }
        }
        return nearest == npc;
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
