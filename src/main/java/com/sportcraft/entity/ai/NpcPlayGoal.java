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

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * Server-side sports brain with explicit, rotating roles so the whole team
 * takes part in play instead of one bot chasing while everyone else waits:
 *
 * <ul>
 *   <li><b>Goalkeeper</b> holds the line, dives at on-target shots, catches
 *       holdable balls and punches the rest away, then throws the caught ball
 *       back into play. Slow loose balls are rushed and cleared long.</li>
 *   <li><b>Presser (rank 0)</b> — the outfield bot closest to the ball —
 *       presses, shoots in range, passes through open lanes under pressure
 *       and slide-tackles contests.</li>
 *   <li><b>Close support (rank 1)</b> tracks behind the ball, collects
 *       intercepted passes and presses loose balls together with rank 0.</li>
 *   <li><b>Support runners</b> keep formation shape but shift upfield in
 *       possession, drop while defending and run onto interception points,
 *       so every bot is a viable passing option.</li>
 *   <li><b>Free kick</b> freezes everyone into set-piece roles: a taker with
 *       a wind-up kick animation, a three-man defensive wall and support.</li>
 * </ul>
 *
 * <p>Basketball swaps the kick logic for a dribble-carry: the presser bounces
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

    private static final double TACKLE_TRIGGER_DIST_SQR = 7.0D;
    private static final double TACKLE_BALL_REACH_SQR = 2.25D;
    private static final double PRESSURE_RADIUS = 4.0D;
    private static final int TACKLE_COOLDOWN_TICKS = 90;
    private static final int DRIBBLE_BOUNCE_INTERVAL = 10;
    private static final int CROSSOVER_INTERVAL = 55;
    private static final double BASKETBALL_SHOT_DIST_SQR = 64.0D;
    private static final double BASKETBALL_CONTROL_DIST_SQR = 4.0D;

    // Goalkeeping
    private static final double KEEPER_SAVE_RANGE_SQR = 49.0D;
    private static final double KEEPER_DIVE_REACH_SQR = 6.25D;
    private static final double KEEPER_CONTACT_SQR = 4.0D;
    private static final double KEEPER_CATCH_HEIGHT = 1.9D;
    private static final int KEEPER_SAVE_COOLDOWN_TICKS = 22;
    private static final int KEEPER_HOLD_THROW_TICK = 32;

    private final SoccerNpcEntity npc;
    private SoccerBallEntity ball;
    private int repathCooldown;
    private int kickCooldown;
    private int roleCooldown;
    private int tackleCooldown;
    private int dribbleCooldown;
    private int crossoverCooldown;
    private int saveCooldown;
    private int carryTicks;
    private boolean cachedChaser;
    private int cachedRank = -1;
    private boolean wasFreeKick;
    private boolean freeKickFired;

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

        boolean freeKick = manager.isFreeKickActive()
                && manager.getSettings().getSport() == BallType.SOCCER;
        if (wasFreeKick && !freeKick) {
            // Returning to open play: release wall poses and the one-shot flag.
            npc.setInWall(false);
            freeKickFired = false;
        }
        wasFreeKick = freeKick;
        if (freeKick) {
            tickFreeKick(serverLevel, manager, center, ownGoal, attackGoal);
            return;
        }

        if (--roleCooldown <= 0 || ball == null) {
            roleCooldown = 10;
            cachedChaser = isChaser(serverLevel, manager);
            cachedRank = computeRank(serverLevel);
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
        if (saveCooldown > 0) {
            saveCooldown--;
        }
    }

    // ------------------------------------------------------------------
    // Football
    // ------------------------------------------------------------------

    private void tickFootball(ServerLevel level, MatchManager manager, MatchSettings settings,
                              BlockPos center, BlockPos ownGoal, BlockPos attackGoal) {
        if (npc.isKeeper()) {
            tickKeeper(level, manager, center, ownGoal, attackGoal);
            return;
        }

        boolean possession = ownTeamInPossession(level, manager);
        Vec3 target = decideTarget(manager, settings, center, ownGoal, attackGoal, possession);
        double speed = cachedChaser ? 1.28D : (cachedRank == 1 ? 1.16D : 1.05D);
        moveToward(target, speed);

        if (ball == null || ball.isCarried() || kickCooldown > 0
                || npc.distanceToSqr(ball) >= KICK_DISTANCE_SQR) {
            maybeTackle(level, manager);
            return;
        }

        double goalDistanceSqr = horizontalDistanceSqr(ball.position(), attackGoal);
        LivingEntity pressure = nearestOpponentWithin(level, manager, ball.position(), PRESSURE_RADIUS);
        SoccerNpcEntity passTarget = pickPassTarget(level, manager, attackGoal);

        if (goalDistanceSqr < 100.0D && laneIsClear(level, manager, ball.position(), attackGoal, 8.0D)) {
            // In range with a clear lane: finish on goal.
            ball.kickFromMob(npc, Vec3.atBottomCenterOf(attackGoal).subtract(ball.position()),
                    1.28D, 0.26D);
        } else if (passTarget != null && (pressure != null || goalDistanceSqr > 225.0D)) {
            // Under pressure, or too far out to shoot: use the passing network.
            Vec3 passDirection = passTarget.position().subtract(ball.position());
            ball.kickFromMob(npc, passDirection, 0.78D, 0.18D);
        } else if (pressure != null) {
            // Closed down with no pass: emergency clearance upfield.
            ball.kickFromMob(npc, Vec3.atBottomCenterOf(attackGoal).subtract(ball.position()),
                    1.32D, 0.32D);
        } else {
            // Space to carry: dribble forward with a soft touch.
            ball.kickFromMob(npc, Vec3.atBottomCenterOf(attackGoal).subtract(ball.position()));
        }
        kickCooldown = KICK_LOCK_TICKS;
        maybeTackle(level, manager);
    }

    private void maybeTackle(ServerLevel level, MatchManager manager) {
        if (!cachedChaser || tackleCooldown > 0 || ball == null) {
            return;
        }
        if (npc.distanceToSqr(ball) >= TACKLE_TRIGGER_DIST_SQR) {
            return;
        }
        LivingEntity rival = nearestOpponentWithin(level, manager, ball.position(), 2.75D);
        if (rival == null) {
            return;
        }
        // No reckless lunges: only commit when the bot can realistically reach
        // the ball first, which removes most whistle-heavy mistimed slides.
        if (npc.distanceToSqr(ball) > rival.distanceToSqr(ball) + 1.5D) {
            return;
        }
        Vec3 lunge = ball.position().subtract(npc.position());
        lunge = new Vec3(lunge.x, 0.0D, lunge.z);
        if (lunge.lengthSqr() <= 1.0E-6D) {
            return;
        }
        lunge = lunge.normalize().scale(1.35D);
        npc.startTackle();
        npc.setDeltaMovement(lunge.x, -0.1D, lunge.z);
        npc.hasImpulse = true;
        npc.getNavigation().stop();
        tackleCooldown = TACKLE_COOLDOWN_TICKS;
        level.sendParticles(ParticleTypes.CLOUD, npc.getX(), npc.getY() + 0.1D, npc.getZ(),
                8, 0.3D, 0.1D, 0.3D, 0.02D);
    }

    private void tickSlide(ServerLevel level, MatchManager manager, BlockPos attackGoal) {
        // Referee (via the manager) decides whether the contact was a foul.
        manager.checkTackleFoul(level, npc, ball);
        if (ball != null) {
            npc.getLookControl().setLookAt(ball.getX(), ball.getEyeY(), ball.getZ());
            if (kickCooldown == 0 && !ball.isCarried()
                    && npc.distanceToSqr(ball) < TACKLE_BALL_REACH_SQR) {
                Vec3 direction = Vec3.atBottomCenterOf(attackGoal).subtract(ball.position());
                ball.kickFromMob(npc, direction, 1.15D, 0.22D);
                kickCooldown = KICK_LOCK_TICKS;
            }
        }
    }

    // ------------------------------------------------------------------
    // Goalkeeper: line holding, diving saves, catches and punches
    // ------------------------------------------------------------------

    private void tickKeeper(ServerLevel level, MatchManager manager, BlockPos center,
                            BlockPos ownGoal, BlockPos attackGoal) {
        // Ball in hands: hold briefly, then distribute back into play.
        if (ball != null && ball.isCarried() && ball.getCarrier() == npc) {
            holdAndThrow(level, manager, attackGoal);
            return;
        }

        // Mid-save: keep the dive momentum and try to make contact.
        if (npc.isKeeperSaving()) {
            continueDive(level, manager, attackGoal);
            return;
        }

        if (ball != null && !ball.isCarried() && saveCooldown == 0
                && !manager.isFreeKickActive() && isShotOnGoal(ball, ownGoal)) {
            Vec3 intercept = predictIntercept(ball, ownGoal);
            double reach = npc.distanceToSqr(intercept.x, npc.getY(), intercept.z);
            boolean catchable = reach <= KEEPER_DIVE_REACH_SQR
                    && intercept.y - npc.getY() < KEEPER_CATCH_HEIGHT;
            if (catchable) {
                npc.startKeeperAction(SoccerNpcEntity.KEEPER_CATCH);
                ball.startCarry(npc);
                carryTicks = 0;
                saveCooldown = KEEPER_SAVE_COOLDOWN_TICKS;
                npc.getNavigation().stop();
                return;
            }
            // Fly toward the predicted ball path.
            npc.startKeeperAction(SoccerNpcEntity.KEEPER_DIVE);
            Vec3 lunge = intercept.subtract(npc.position());
            lunge = new Vec3(lunge.x, 0.0D, lunge.z);
            if (lunge.lengthSqr() > 1.0E-6D) {
                lunge = lunge.normalize().scale(1.45D);
                npc.setDeltaMovement(lunge.x, 0.28D, lunge.z);
                npc.hasImpulse = true;
            }
            npc.getNavigation().stop();
            saveCooldown = KEEPER_SAVE_COOLDOWN_TICKS;
            return;
        }

        // No immediate save: hold the line, rush slow loose balls, clear long.
        // During an opposing free kick the parked ball must not be touched
        // before the taker strikes it.
        int inward = Integer.signum(center.getZ() - ownGoal.getZ());
        Vec3 guard = new Vec3(ownGoal.getX() + 0.5D, center.getY(), ownGoal.getZ() + inward * 1.5D);
        boolean setPieceFreeze = manager.isFreeKickActive();
        boolean rushBall = ball != null && !ball.isCarried() && !setPieceFreeze
                && npc.distanceToSqr(ball) < KEEPER_RUSH_DISTANCE_SQR;
        moveToward(rushBall ? ball.position() : guard, 1.12D);

        if (ball != null && kickCooldown == 0 && !ball.isCarried()
                && npc.distanceToSqr(ball) < KICK_DISTANCE_SQR) {
            Vec3 clearDirection = Vec3.atBottomCenterOf(attackGoal).subtract(ball.position());
            ball.kickFromMob(npc, clearDirection, 1.3D, 0.3D);
            kickCooldown = KICK_LOCK_TICKS;
        }
    }

    /** Keeps applying lunge momentum and converts contact into a catch or punch. */
    private void continueDive(ServerLevel level, MatchManager manager, BlockPos attackGoal) {
        if (ball == null || !ball.isAlive() || ball.isCarried()) {
            return;
        }
        npc.getLookControl().setLookAt(ball.getX(), ball.getEyeY(), ball.getZ());
        if (npc.distanceToSqr(ball) >= KEEPER_CONTACT_SQR) {
            return;
        }
        if (ball.getY() - npc.getY() < KEEPER_CATCH_HEIGHT) {
            npc.startKeeperAction(SoccerNpcEntity.KEEPER_CATCH);
            ball.startCarry(npc);
            carryTicks = 0;
            return;
        }
        npc.startKeeperAction(SoccerNpcEntity.KEEPER_PUNCH);
        Vec3 clear = Vec3.atBottomCenterOf(attackGoal).subtract(ball.position());
        double side = level.random.nextBoolean() ? 1.0D : -1.0D;
        clear = clear.add(new Vec3(side * 3.0D, 0.0D, 0.0D));
        ball.kickFromMob(npc, clear, 1.35D, 0.42D);
        kickCooldown = KICK_LOCK_TICKS;
    }

    /** Holds the caught ball, looks upfield and throws it to a teammate or long. */
    private void holdAndThrow(ServerLevel level, MatchManager manager, BlockPos attackGoal) {
        carryTicks++;
        npc.getNavigation().stop();
        Vec3 aim = Vec3.atBottomCenterOf(attackGoal);
        SoccerNpcEntity outlet = pickPassTarget(level, manager, attackGoal);
        if (outlet != null) {
            aim = outlet.position().add(0.0D, 1.2D, 0.0D);
        }
        npc.getLookControl().setLookAt(aim.x, aim.y, aim.z);
        if (carryTicks < KEEPER_HOLD_THROW_TICK) {
            return;
        }
        Vec3 direction = aim.subtract(npc.position().add(0.0D, 1.15D, 0.0D));
        if (direction.lengthSqr() < 1.0E-6D) {
            direction = npc.getLookAngle();
        }
        direction = direction.normalize();
        ball.releaseCarry(direction.scale(1.05D).add(0.0D, 0.38D, 0.0D));
        kickCooldown = KICK_LOCK_TICKS;
    }

    /** A ball counts as a shot when it is close and moving toward the goal. */
    private boolean isShotOnGoal(SoccerBallEntity target, BlockPos ownGoal) {
        double goalDistanceSqr = horizontalDistanceSqr(target.position(), ownGoal);
        if (goalDistanceSqr > KEEPER_SAVE_RANGE_SQR) {
            return false;
        }
        Vec3 velocity = target.getDeltaMovement();
        if (velocity.horizontalDistanceSqr() < 0.012D) {
            // Stationary/rolling ball right on top of the keeper still needs handling.
            return npc.distanceToSqr(target) < 6.25D;
        }
        Vec3 toGoal = Vec3.atBottomCenterOf(ownGoal).subtract(target.position());
        toGoal = new Vec3(toGoal.x, 0.0D, toGoal.z);
        if (toGoal.lengthSqr() < 1.0E-6D) {
            return true;
        }
        Vec3 flatVelocity = new Vec3(velocity.x, 0.0D, velocity.z);
        return flatVelocity.normalize().dot(toGoal.normalize()) > 0.25D;
    }

    /** Predicts where the ball will be when it reaches the keeper's goal line. */
    private Vec3 predictIntercept(SoccerBallEntity target, BlockPos ownGoal) {
        Vec3 velocity = target.getDeltaMovement();
        double lineDistance = Math.sqrt(horizontalDistanceSqr(target.position(), ownGoal));
        double speed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        double time = speed > 0.05D ? Math.min(lineDistance / speed, 12.0D) : 0.0D;
        Vec3 predicted = target.position().add(velocity.scale(time));
        // Keep the dive point roughly in front of the goal mouth.
        double goalX = ownGoal.getX() + 0.5D;
        double deltaX = predicted.x - goalX;
        if (Math.abs(deltaX) > 2.2D) {
            predicted = new Vec3(goalX + Math.signum(deltaX) * 2.2D, predicted.y, predicted.z);
        }
        return predicted;
    }

    // ------------------------------------------------------------------
    // Free kick: taker, wall and support roles
    // ------------------------------------------------------------------

    private void tickFreeKick(ServerLevel level, MatchManager manager, BlockPos center,
                              BlockPos ownGoal, BlockPos attackGoal) {
        Team takerTeam = manager.getFreeKickTeam();
        if (npc.getSportTeam() == takerTeam) {
            tickFreeKickAttack(level, manager, center, attackGoal);
        } else {
            tickFreeKickDefence(level, manager, center, ownGoal);
        }
    }

    private void tickFreeKickAttack(ServerLevel level, MatchManager manager,
                                    BlockPos center, BlockPos attackGoal) {
        // The attacking team's keeper still minds the goal instead of joining
        // the set-piece group.
        if (npc.isKeeper()) {
            tickKeeper(level, manager, center,
                    manager.getGoalPosition(npc.getSportTeam()), attackGoal);
            return;
        }
        boolean taker = isNearestOutfield(level, manager.getFreeKickPos(), npc.getSportTeam());
        if (!taker) {
            // Support players hold shape just behind the ball.
            Vec3 home = FormationLayout.homePosition(center, manager.getSettings().getFieldSize(),
                    npc.getSportTeam() == Team.RED ? manager.getSettings().getHomeFormation()
                            : manager.getSettings().getAwayFormation(),
                    npc.getSportTeam(), npc.getSquadIndex(),
                    manager.getSettings().getTeamSize(), false);
            moveToward(home, 1.0D);
            return;
        }

        Vec3 stand = manager.getFreeKickTakerPos();
        moveToward(stand, 1.2D);
        npc.getLookControl().setLookAt(manager.getFreeKickPos().x,
                manager.getFreeKickPos().y + 1.0D, manager.getFreeKickPos().z);
        if (ball == null || freeKickFired) {
            return;
        }
        boolean settled = level.getServer().getTickCount() - manager.getLastFoulTick() > 42;
        boolean inPosition = npc.distanceToSqr(stand.x, npc.getY(), stand.z) < 2.2D;
        if (settled && inPosition && !npc.isWindingUp()) {
            npc.startKickWindup();
        }
        if (npc.isWindingUp() && npc.getKickWindupTicks() <= 2) {
            Vec3 direction = Vec3.atBottomCenterOf(attackGoal).subtract(ball.position());
            ball.kickFromMob(npc, direction, 1.5D, 0.3D);
            kickCooldown = KICK_LOCK_TICKS;
            freeKickFired = true;
            manager.endFreeKick();
        }
    }

    private void tickFreeKickDefence(ServerLevel level, MatchManager manager,
                                     BlockPos center, BlockPos ownGoal) {
        if (npc.isKeeper()) {
            // Keeper stays live on the line and can save the incoming shot.
            tickKeeper(level, manager, center, ownGoal,
                    manager.getGoalPosition(npc.getSportTeam().opponent()));
            return;
        }
        List<SoccerNpcEntity> defenders = outfieldBots(level, npc.getSportTeam());
        defenders.sort(Comparator
                .<SoccerNpcEntity>comparingDouble(bot -> bot.distanceToSqr(manager.getFreeKickPos()))
                .thenComparingInt(bot -> bot.getId()));
        int wallCount = Math.min(3, defenders.size());
        int wallIndex = defenders.indexOf(npc);
        if (wallIndex >= 0 && wallIndex < wallCount) {
            npc.setInWall(true);
            Vec3 slot = manager.getWallPosition(wallIndex, wallCount);
            if (npc.distanceToSqr(slot.x, npc.getY(), slot.z) > 0.6D) {
                npc.getNavigation().moveTo(slot.x, slot.y, slot.z, 1.15D);
            } else {
                npc.getNavigation().stop();
            }
            npc.getLookControl().setLookAt(manager.getFreeKickPos().x,
                    manager.getFreeKickPos().y + 1.0D, manager.getFreeKickPos().z);
            return;
        }
        npc.setInWall(false);
        Vec3 home = FormationLayout.homePosition(center, manager.getSettings().getFieldSize(),
                npc.getSportTeam() == Team.RED ? manager.getSettings().getHomeFormation()
                        : manager.getSettings().getAwayFormation(),
                npc.getSportTeam(), npc.getSquadIndex(),
                manager.getSettings().getTeamSize(), false);
        moveToward(home, 1.0D);
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

        if (manager.isFreeKickActive()) {
            // Set piece in basketball: nearest attacker restarts play at the ball.
            boolean taker = isNearestOutfield(level, manager.getFreeKickPos(), npc.getSportTeam());
            if (taker && ball != null && npc.distanceToSqr(ball) < KICK_DISTANCE_SQR) {
                manager.endFreeKick();
            } else {
                moveToward(taker && ball != null ? ball.position()
                        : FormationLayout.homePosition(center, settings.getFieldSize(),
                        npc.getSportTeam() == Team.RED ? settings.getHomeFormation()
                                : settings.getAwayFormation(),
                        npc.getSportTeam(), npc.getSquadIndex(), settings.getTeamSize(), false), 1.05D);
            }
            return;
        }

        if (ball == null || !cachedChaser) {
            Vec3 home = decideTarget(manager, settings, center, ownGoal, attackGoal,
                    ownTeamInPossession(level, manager));
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
    // Shared tactical helpers
    // ------------------------------------------------------------------

    private Vec3 decideTarget(MatchManager manager, MatchSettings settings,
                              BlockPos center, BlockPos ownGoal, BlockPos attackGoal,
                              boolean possession) {
        FieldSize size = settings.getFieldSize();
        Formation formation = npc.getSportTeam() == Team.RED
                ? settings.getHomeFormation() : settings.getAwayFormation();
        Vec3 home = FormationLayout.homePosition(center, size, formation, npc.getSportTeam(),
                npc.getSquadIndex(), settings.getTeamSize(), npc.isKeeper());

        if (npc.isKeeper()) {
            int inward = Integer.signum(center.getZ() - ownGoal.getZ());
            Vec3 guard = new Vec3(ownGoal.getX() + 0.5D, center.getY(), ownGoal.getZ() + inward * 1.5D);
            if (ball != null && !ball.isCarried() && npc.distanceToSqr(ball) < KEEPER_RUSH_DISTANCE_SQR) {
                return ball.position();
            }
            return guard;
        }

        if (ball == null) {
            return home;
        }

        Vec3 ballPoint = ball.isCarried() && ball.getCarrier() != null
                ? ball.getCarrier().position() : ball.position();

        // Closest outfield bot does the pressing (at the carrier when held).
        if (cachedChaser) {
            return ballPoint;
        }

        // Loose ball close by: the second man presses too instead of watching.
        boolean loose = !ball.isCarried();
        if (loose && cachedRank == 1 && npc.distanceToSqr(ball) < 36.0D) {
            return ballPoint;
        }

        // A pass travelling toward this bot: step onto the interception point.
        Vec3 interception = interceptionPoint();
        if (interception != null) {
            return interception;
        }

        // Close support: offer a passing option just behind the ball.
        if (cachedRank == 1) {
            Vec3 toOwnGoal = new Vec3(ownGoal.getX() + 0.5D - ballPoint.x, 0.0D,
                    ownGoal.getZ() + 0.5D - ballPoint.z);
            if (toOwnGoal.lengthSqr() > 1.0E-6D) {
                toOwnGoal = toOwnGoal.normalize();
            }
            double side = (npc.getSquadIndex() % 2 == 0) ? 2.0D : -2.0D;
            Vec3 lateral = new Vec3(-toOwnGoal.z * side, 0.0D, toOwnGoal.x * side);
            Vec3 supportSpot = ballPoint.add(toOwnGoal.scale(4.5D)).add(lateral);
            return home.lerp(supportSpot, possession ? 0.8D : 0.6D);
        }

        // Shape players: shift upfield while attacking, drop while defending,
        // and always drift toward the ball so no bot is left fully idle.
        Vec3 shifted = home;
        if (possession) {
            Vec3 push = new Vec3(attackGoal.getX() + 0.5D - home.x, 0.0D,
                    attackGoal.getZ() + 0.5D - home.z);
            if (push.lengthSqr() > 1.0E-6D) {
                shifted = home.add(push.normalize().scale(3.0D));
            }
        }
        if (npc.distanceToSqr(ballPoint) <= HOME_DRIFT_DISTANCE_SQR) {
            return shifted;
        }
        return shifted.lerp(ballPoint, possession ? 0.42D : 0.3D);
    }

    /**
     * Returns the predicted meeting point when the ball is travelling toward
     * this bot, otherwise {@code null}. Lets support bots actively receive
     * passes instead of waiting in their slot until the ball arrives.
     */
    private Vec3 interceptionPoint() {
        if (ball == null || ball.isCarried()) {
            return null;
        }
        Vec3 velocity = ball.getDeltaMovement();
        if (velocity.horizontalDistanceSqr() < 0.02D) {
            return null;
        }
        Vec3 toBot = npc.position().subtract(ball.position());
        Vec3 flatVelocity = new Vec3(velocity.x, 0.0D, velocity.z);
        double speed = flatVelocity.length();
        double alignment = flatVelocity.normalize()
                .dot(new Vec3(toBot.x, 0.0D, toBot.z).normalize());
        if (alignment < 0.75D || toBot.horizontalDistanceSqr() > 144.0D) {
            return null;
        }
        double travelTime = Math.min(toBot.horizontalDistance() / speed, 8.0D);
        return ball.position().add(velocity.scale(travelTime));
    }

    /**
     * Passing network: scores every teammate by forward progress toward the
     * attacked goal, distance and free space, rejecting lanes blocked by an
     * opponent. Returns {@code null} when no safe option exists.
     */
    private SoccerNpcEntity pickPassTarget(ServerLevel level, MatchManager manager,
                                           BlockPos attackGoal) {
        if (ball == null) {
            return null;
        }
        AABB searchArea = npc.getBoundingBox().inflate(24.0D);
        SoccerNpcEntity best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        double ownGoalDistance = horizontalDistanceSqr(npc.position(), attackGoal);
        for (SoccerNpcEntity candidate : level.getEntitiesOfClass(SoccerNpcEntity.class, searchArea)) {
            if (candidate == npc || candidate.isReferee() || !candidate.isAlive()
                    || candidate.getSportTeam() != npc.getSportTeam()) {
                continue;
            }
            if (npc.distanceToSqr(candidate) < 9.0D) {
                continue;
            }
            double candidateGoalDistance = horizontalDistanceSqr(candidate.position(), attackGoal);
            double progress = ownGoalDistance - candidateGoalDistance;
            if (progress <= -2.0D) {
                continue;
            }
            if (!laneIsClear(level, manager, ball.position(), candidate.position(), 1.6D)) {
                continue;
            }
            double pressurePenalty = nearestOpponentWithin(level, manager,
                    candidate.position(), 3.0D) != null ? 8.0D : 0.0D;
            double score = progress * 1.2D - 0.15D * npc.distanceToSqr(candidate) - pressurePenalty;
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    /** True when no opponent stands inside {@code width} of the point-to-point lane. */
    private boolean laneIsClear(ServerLevel level, MatchManager manager,
                                Vec3 from, Vec3 to, double width) {
        Vec3 lane = to.subtract(from);
        double length = Math.sqrt(lane.x * lane.x + lane.z * lane.z);
        if (length < 1.0E-4D) {
            return true;
        }
        Vec3 direction = lane.multiply(1.0D / length, 0.0D, 1.0D / length);
        for (double t = Math.min(2.0D, length * 0.5D); t < length; t += 2.5D) {
            Vec3 sample = from.add(direction.scale(t));
            if (nearestOpponentWithin(level, manager, sample, width) != null) {
                return false;
            }
        }
        return true;
    }

    private boolean laneIsClear(ServerLevel level, MatchManager manager,
                                Vec3 from, BlockPos goal, double width) {
        return laneIsClear(level, manager, from, Vec3.atBottomCenterOf(goal), width);
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

    /**
     * Possession estimate: the ball is carried by a teammate, or the closest
     * participant to it is on this bot's team and within controlling range.
     */
    private boolean ownTeamInPossession(ServerLevel level, MatchManager manager) {
        if (ball == null) {
            return false;
        }
        if (ball.isCarried() && ball.getCarrier() instanceof LivingEntity carrier) {
            return manager.teamOf(carrier) == npc.getSportTeam();
        }
        double ownNearest = nearestTeamDistanceSqr(level, manager, npc.getSportTeam());
        double opponentNearest = nearestTeamDistanceSqr(level, manager, npc.getSportTeam().opponent());
        return ownNearest < 9.0D && ownNearest <= opponentNearest;
    }

    private double nearestTeamDistanceSqr(ServerLevel level, MatchManager manager, Team team) {
        AABB area = new AABB(ball.blockPosition()).inflate(8.0D);
        double nearest = Double.MAX_VALUE;
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, area)) {
            if (!candidate.isAlive() || manager.teamOf(candidate) != team) {
                continue;
            }
            nearest = Math.min(nearest, candidate.distanceToSqr(ball));
        }
        return nearest;
    }

    private static double horizontalDistanceSqr(Vec3 position, BlockPos target) {
        double dx = position.x - (target.getX() + 0.5D);
        double dz = position.z - (target.getZ() + 0.5D);
        return dx * dx + dz * dz;
    }

    /** The outfield bot of this team nearest to the ball does the pressing. */
    private boolean isChaser(ServerLevel level, MatchManager manager) {
        if (npc.isKeeper() || ball == null) {
            return false;
        }
        // A ball held by our own keeper is not chased by our outfield players.
        if (ball.isCarried() && ball.getCarrier() instanceof LivingEntity carrier
                && manager.teamOf(carrier) == npc.getSportTeam()) {
            return false;
        }
        return computeRank(level) == 0;
    }

    /** Distance rank of this bot among its team's outfield bots (0 = closest). */
    private int computeRank(ServerLevel level) {
        if (npc.isKeeper() || ball == null) {
            return -1;
        }
        List<SoccerNpcEntity> teammates = outfieldBots(level, npc.getSportTeam());
        teammates.sort(Comparator.comparingDouble(bot -> bot.distanceToSqr(ball)));
        return teammates.indexOf(npc);
    }

    private List<SoccerNpcEntity> outfieldBots(ServerLevel level, Team team) {
        AABB searchArea = new AABB(MatchManager.get(level).getFieldCenter()).inflate(BALL_SEARCH_RADIUS);
        // Mutable ArrayList: callers sort this list in place (rank/wall order).
        // Stream#toList() returns an immutable list and throws on sort().
        return level.getEntitiesOfClass(SoccerNpcEntity.class, searchArea).stream()
                .filter(bot -> !bot.isReferee() && !bot.isKeeper() && bot.getSportTeam() == team)
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
    }

    private boolean isNearestOutfield(ServerLevel level, Vec3 point, Team team) {
        List<SoccerNpcEntity> bots = outfieldBots(level, team);
        SoccerNpcEntity nearest = bots.stream()
                .min(Comparator.<SoccerNpcEntity>comparingDouble(
                                bot -> bot.distanceToSqr(point.x, point.y, point.z))
                        .thenComparingInt(bot -> bot.getId()))
                .orElse(null);
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
