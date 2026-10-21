package com.sportcraft.entity.ai;

import com.sportcraft.entity.SoccerBallEntity;
import com.sportcraft.entity.SoccerNpcEntity;
import com.sportcraft.match.BallType;
import com.sportcraft.match.FieldSize;
import com.sportcraft.match.Formation;
import com.sportcraft.match.FormationLayout;
import com.sportcraft.match.MatchManager;
import com.sportcraft.match.MatchSettings;
import com.sportcraft.match.Team;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

/**
 * Server-side sports brain with explicit roles instead of a whole team
 * chasing the ball:
 *
 * <ul>
 *   <li><b>Goalkeeper</b> holds the goal line and only rushes out when the
 *       ball comes into the penalty area, then clears it long.</li>
 *   <li><b>One chaser per team</b> — the outfield bot closest to the ball —
 *       presses, passes under pressure, shoots and performs slide tackles.</li>
 *   <li><b>Everyone else</b> holds their {@link FormationLayout} home slot,
 *       edging toward the ball only while it is on their own half.</li>
 * </ul>
 *
 * <p>Basketball swaps the kick logic for a dribble-carry: the chaser bounces
 * the ball forward while running, performs lateral crossover sidesteps past
 * opponents and finishes with an arced shot at the elevated hoop.</p>
 */
public class NpcPlayGoal extends Goal {

    private static final double BALL_SEARCH_RADIUS = 64.0D;
    private static final double KEEPER_RUSH_DISTANCE_SQR = 36.0D;
    private static final double KICK_DISTANCE_SQR = 2.8D;
    private static final double HOME_DRIFT_DISTANCE_SQR = 25.0D;
    private static final int REPATH_INTERVAL = 12;
    private static final int KICK_LOCK_TICKS = 14;

    private static final double TACKLE_TRIGGER_DIST_SQR = 9.0D;
    private static final double TACKLE_BALL_REACH_SQR = 2.25D;
    private static final double PRESSURE_RADIUS = 4.0D;
    private static final int TACKLE_COOLDOWN_TICKS = 50;
    private static final int DRIBBLE_BOUNCE_INTERVAL = 10;
    private static final int CROSSOVER_INTERVAL = 55;
    private static final double BASKETBALL_SHOT_DIST_SQR = 64.0D;
    private static final double BASKETBALL_CONTROL_DIST_SQR = 4.0D;

    private final SoccerNpcEntity npc;
    private SoccerBallEntity ball;
    private int repathCooldown;
    private int kickCooldown;
    private int roleCooldown;
    private int tackleCooldown;
    private int dribbleCooldown;
    private int crossoverCooldown;
    private boolean cachedChaser;

    public NpcPlayGoal(SoccerNpcEntity npc) {
        this.npc = npc;
        setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // The referee entity runs RefereeGoal instead of the team brain.
        return !npc.level().isClientSide && !npc.isReferee();
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

        decrementCooldowns();

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

        // A sliding bot keeps its lunge velocity; only contact/foul logic runs.
        if (npc.isSliding()) {
            tickSlide(serverLevel, manager, attackGoal);
            return;
        }

        MatchSettings settings = manager.getSettings();
        if (settings.getSport() == BallType.BASKETBALL) {
            tickBasketball(serverLevel, manager, settings, center, ownGoal, attackGoal);
        } else {
            tickFootball(serverLevel, manager, settings, center, ownGoal, attackGoal);
        }
    }

    private void decrementCooldowns() {
        if (kickCooldown > 0) {
            kickCooldown--;
        }
        if (tackleCooldown > 0) {
            tackleCooldown--;
        }
        if (dribbleCooldown > 0) {
            dribbleCooldown--;
        }
        if (crossoverCooldown > 0) {
            crossoverCooldown--;
        }
    }

    // ------------------------------------------------------------------
    // Football
    // ------------------------------------------------------------------

    private void tickFootball(ServerLevel level, MatchManager manager, MatchSettings settings,
                              BlockPos center, BlockPos ownGoal, BlockPos attackGoal) {
        Vec3 target = decideTarget(manager, settings, center, ownGoal);
        double speed = npc.isKeeper() ? 1.12D : (cachedChaser ? 1.28D : 1.05D);
        if (npc.distanceToSqr(target.x, target.y, target.z) > 1.2D) {
            npc.getNavigation().moveTo(target.x, target.y, target.z, speed);
        } else {
            npc.getNavigation().stop();
        }
        npc.getLookControl().setLookAt(target.x, target.y + 1.0D, target.z);

        if (ball != null && kickCooldown == 0 && npc.distanceToSqr(ball) < KICK_DISTANCE_SQR) {
            Vec3 shotDirection = Vec3.atBottomCenterOf(attackGoal).subtract(ball.position());
            if (npc.isKeeper()) {
                // Goalkeeper clears long and high.
                ball.kickFromMob(npc, shotDirection, 1.3D, 0.3D);
            } else {
                LivingEntity pressure = nearestOpponentWithin(level, manager, ball.position(), PRESSURE_RADIUS);
                SoccerNpcEntity passTarget = pickForwardPassTarget(manager, attackGoal);
                if (pressure != null && passTarget != null) {
                    // Under pressure: play a sideways/forward pass instead of
                    // dribbling into the challenge.
                    ball.kickFromMob(npc, passTarget.position().subtract(ball.position()), 0.72D, 0.18D);
                } else {
                    ball.kickFromMob(npc, shotDirection);
                }
            }
            kickCooldown = KICK_LOCK_TICKS;
        }

        // Slide tackle: the pressing bot launches when an opponent is also
        // contesting the ball and the tackle is off cooldown.
        if (cachedChaser && tackleCooldown == 0 && ball != null
                && npc.distanceToSqr(ball) < TACKLE_TRIGGER_DIST_SQR
                && nearestOpponentWithin(level, manager, ball.position(), 3.5D) != null) {
            Vec3 lunge = ball.position().subtract(npc.position());
            lunge = new Vec3(lunge.x, 0.0D, lunge.z);
            if (lunge.lengthSqr() > 1.0E-6D) {
                lunge = lunge.normalize().scale(1.35D);
                npc.startTackle();
                npc.setDeltaMovement(lunge.x, -0.1D, lunge.z);
                npc.hasImpulse = true;
                npc.getNavigation().stop();
                tackleCooldown = TACKLE_COOLDOWN_TICKS;
                level.sendParticles(ParticleTypes.CLOUD, npc.getX(), npc.getY() + 0.1D, npc.getZ(),
                        8, 0.3D, 0.1D, 0.3D, 0.02D);
            }
        }
    }

    private void tickSlide(ServerLevel level, MatchManager manager, BlockPos attackGoal) {
        // Referee (via the manager) decides whether the contact was a foul.
        manager.checkTackleFoul(level, npc, ball);
        if (ball != null) {
            npc.getLookControl().setLookAt(ball.getX(), ball.getEyeY(), ball.getZ());
            if (kickCooldown == 0 && npc.distanceToSqr(ball) < TACKLE_BALL_REACH_SQR) {
                Vec3 direction = Vec3.atBottomCenterOf(attackGoal).subtract(ball.position());
                ball.kickFromMob(npc, direction, 1.15D, 0.22D);
                kickCooldown = KICK_LOCK_TICKS;
            }
        }
    }

    /**
     * Forward pass selection: the best teammate is ahead of the bot relative
     * to the attacked goal and not too far away. Returns {@code null} when no
     * forward option exists, in which case the bot shoots instead.
     */
    private SoccerNpcEntity pickForwardPassTarget(MatchManager manager, BlockPos attackGoal) {
        if (!(npc.level() instanceof ServerLevel level)) {
            return null;
        }
        AABB searchArea = npc.getBoundingBox().inflate(24.0D);
        SoccerNpcEntity best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        double ownGoalDistance = horizontalDistanceSqr(npc.position(), attackGoal);
        for (SoccerNpcEntity candidate : level.getEntitiesOfClass(SoccerNpcEntity.class, searchArea)) {
            if (candidate == npc || candidate.isReferee()
                    || candidate.getSportTeam() != npc.getSportTeam() || !candidate.isAlive()) {
                continue;
            }
            double candidateGoalDistance = horizontalDistanceSqr(candidate.position(), attackGoal);
            double progress = ownGoalDistance - candidateGoalDistance;
            if (progress <= 1.0D) {
                continue;
            }
            double score = progress - 0.25D * npc.distanceToSqr(candidate);
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------
    // Basketball
    // ------------------------------------------------------------------

    private void tickBasketball(ServerLevel level, MatchManager manager, MatchSettings settings,
                                BlockPos center, BlockPos ownGoal, BlockPos attackGoal) {
        Vec3 hoop = Vec3.atBottomCenterOf(attackGoal);

        if (npc.isKeeper()) {
            // Defensive guard stays under the hoop and rushes only close balls.
            int inward = Integer.signum(center.getZ() - ownGoal.getZ());
            Vec3 guard = new Vec3(ownGoal.getX() + 0.5D, center.getY(), ownGoal.getZ() + inward * 2.0D);
            Vec3 target = (ball != null && npc.distanceToSqr(ball) < KEEPER_RUSH_DISTANCE_SQR)
                    ? ball.position() : guard;
            moveToward(target, 1.12D);
            if (ball != null && kickCooldown == 0 && npc.distanceToSqr(ball) < KICK_DISTANCE_SQR) {
                // Outlet pass upcourt with a soft arc.
                ball.shootAt(npc, hoop, 1.0D, 0.5D);
                kickCooldown = KICK_LOCK_TICKS;
            }
            return;
        }

        if (ball == null || !cachedChaser) {
            Vec3 home = decideTarget(manager, settings, center, ownGoal);
            moveToward(home, 1.05D);
            return;
        }

        if (npc.distanceToSqr(ball) > BASKETBALL_CONTROL_DIST_SQR) {
            // Chase the loose ball.
            moveToward(ball.position(), 1.28D);
            return;
        }

        // In possession: dribble-carry toward the hoop.
        npc.markDribbling();
        Vec3 flat = new Vec3(hoop.x - ball.getX(), 0.0D, hoop.z - ball.getZ());
        if (flat.lengthSqr() < 1.0E-6D) {
            flat = new Vec3(0.0D, 0.0D, 1.0D);
        }
        Vec3 attackDirection = flat.normalize();

        if (kickCooldown == 0 && horizontalDistanceSqr(ball.position(), attackGoal) < BASKETBALL_SHOT_DIST_SQR) {
            // Arced jump shot onto the rim.
            ball.shootAt(npc, hoop, 1.15D, 0.72D);
            kickCooldown = KICK_LOCK_TICKS;
            dribbleCooldown = DRIBBLE_BOUNCE_INTERVAL;
            return;
        }

        // Run just ahead of the ball while bouncing it forward.
        Vec3 carryTarget = ball.position().add(attackDirection.scale(2.0D));
        moveToward(carryTarget, 1.28D);
        if (dribbleCooldown == 0) {
            ball.dribbleBounce(attackDirection);
            dribbleCooldown = DRIBBLE_BOUNCE_INTERVAL;
        }

        // Crossover: lateral sidestep to beat a defending opponent.
        if (crossoverCooldown == 0
                && nearestOpponentWithin(level, manager, ball.position(), 5.0D) != null
                && level.random.nextFloat() < 0.5F) {
            double sign = level.random.nextBoolean() ? 1.0D : -1.0D;
            Vec3 side = new Vec3(-attackDirection.z * sign, 0.0D, attackDirection.x * sign);
            npc.setDeltaMovement(side.x * 0.9D, npc.getDeltaMovement().y, side.z * 0.9D);
            npc.hasImpulse = true;
            crossoverCooldown = CROSSOVER_INTERVAL;
            level.sendParticles(ParticleTypes.CRIT, npc.getX(), npc.getY() + 1.0D, npc.getZ(),
                    6, 0.25D, 0.2D, 0.25D, 0.0D);
        }
    }

    private void moveToward(Vec3 target, double speed) {
        if (npc.distanceToSqr(target.x, target.y, target.z) > 1.2D) {
            npc.getNavigation().moveTo(target.x, target.y, target.z, speed);
        } else {
            npc.getNavigation().stop();
        }
        npc.getLookControl().setLookAt(target.x, target.y + 1.0D, target.z);
    }

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------

    private Vec3 decideTarget(MatchManager manager, MatchSettings settings,
                              BlockPos center, BlockPos ownGoal) {
        FieldSize size = settings.getFieldSize();
        Formation formation = npc.getSportTeam() == Team.RED
                ? settings.getHomeFormation() : settings.getAwayFormation();
        Vec3 home = FormationLayout.homePosition(center, size, formation, npc.getSportTeam(),
                npc.getSquadIndex(), settings.getTeamSize(), npc.isKeeper());

        if (npc.isKeeper()) {
            int inward = Integer.signum(center.getZ() - ownGoal.getZ());
            // Goal positions are lifted for basketball; navigation stays on the ground.
            Vec3 guard = new Vec3(ownGoal.getX() + 0.5D, center.getY(), ownGoal.getZ() + inward * 1.5D);
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

    /** Nearest opposing participant (bot or real player) to a point, or null. */
    private LivingEntity nearestOpponentWithin(ServerLevel level, MatchManager manager,
                                               Vec3 point, double radius) {
        AABB area = new AABB(point.x, point.y, point.z, point.x, point.y, point.z).inflate(radius);
        LivingEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, area)) {
            if (!candidate.isAlive() || candidate == npc) {
                continue;
            }
            Team candidateTeam = manager.teamOf(candidate);
            if (candidateTeam == Team.SPECTATOR || candidateTeam == npc.getSportTeam()) {
                continue;
            }
            double distance = candidate.distanceToSqr(point.x, point.y, point.z);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = candidate;
            }
        }
        return nearest;
    }

    private static double horizontalDistanceSqr(Vec3 position, BlockPos target) {
        double dx = position.x - (target.getX() + 0.5D);
        double dz = position.z - (target.getZ() + 0.5D);
        return dx * dx + dz * dz;
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
            if (candidate.getSportTeam() != npc.getSportTeam() || candidate.isKeeper()
                    || candidate.isReferee()) {
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
