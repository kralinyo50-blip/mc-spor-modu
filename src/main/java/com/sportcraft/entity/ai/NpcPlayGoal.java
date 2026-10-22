package com.sportcraft.entity.ai;

import com.sportcraft.entity.SoccerBallEntity;
import com.sportcraft.entity.SoccerNpcEntity;
import com.sportcraft.match.BallType;
import com.sportcraft.match.FieldSize;
import com.sportcraft.match.Formation;
import com.sportcraft.match.FormationLayout;
import com.sportcraft.match.MatchManager;
import com.sportcraft.match.MatchSettings;
import com.sportcraft.match.MatchState;
import com.sportcraft.match.Team;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
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
 *       presses, shoots in range, plays through balls into runners' space,
 *       lobs over blocked lanes, passes through open lanes under pressure
 *       and slide-tackles contests. Any bot that reaches a loose ball first
 *       gets the same first-touch decision instead of waiting for the role.</li>
 *   <li><b>Close support (rank 1)</b> tracks behind the ball, collects
 *       intercepted passes and presses loose balls together with rank 0.</li>
 *   <li><b>Support runners</b> keep formation shape but make off-the-ball
 *       attacking runs into open space (through-ball targets), mark the
 *       nearest opponent while defending, shift to the ball side and run
 *       onto predicted interception/landing points, so every bot is a
 *       viable passing option and nobody stands idle.</li>
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
    // Tighter control range: the presser only touches the ball from ~1.5
    // blocks so dribble taps stay small instead of ping-ponging away.
    private static final double KICK_DISTANCE_SQR = 2.25D;
    private static final int REPATH_INTERVAL = 12;
    private static final int KICK_LOCK_TICKS = 10;

    private static final double TACKLE_TRIGGER_DIST_SQR = 7.0D;
    private static final double TACKLE_BALL_REACH_SQR = 2.25D;
    private static final int TACKLE_COOLDOWN_TICKS = 90;
    private static final int DRIBBLE_BOUNCE_INTERVAL = 10;
    private static final int CROSSOVER_INTERVAL = 55;
    private static final double BASKETBALL_SHOT_DIST_SQR = 64.0D;
    private static final double BASKETBALL_CONTROL_DIST_SQR = 4.0D;

    // Football presser tuning. After this many dribble taps without a shot or
    // pass the bot looks for a release pass so it never dribbles in circles.
    private static final int DRIBBLE_STUCK_TAPS = 22;
    private static final int FEINT_COOLDOWN_TICKS = 45;
    private static final double SHOT_LANE_WIDTH = 1.1D;
    private static final double PASS_LANE_WIDTH = 1.4D;
    private static final double GOAL_MOUTH_OFFSET = 1.8D;

    // Positional play: any bot this close to a loose ball steps in as a
    // secondary presser instead of watching from its formation slot.
    private static final double SECOND_PRESS_SQR = 16.0D;
    // Through balls (ara pas): lead distance ahead of the runner plus the
    // open-space checks around the landing point.
    private static final double THROUGH_LEAD = 4.0D;
    private static final double THROUGH_MIN_PROGRESS = 3.5D;
    private static final double THROUGH_SPACE_RADIUS = 2.6D;
    // Lofted passes (yüksekten pas): distance band and arc tiers.
    private static final double LOB_MIN_DIST_SQR = 36.0D;
    private static final double LOB_MAX_DIST_SQR = 676.0D;
    // Off-the-ball attacking runs.
    private static final int RUN_DURATION_TICKS = 42;
    private static final int RUN_REST_TICKS = 55;
    private static final double RUN_OPEN_SPACE_RADIUS = 3.0D;

    // Goalkeeping
    private static final double KEEPER_SWEEP_RANGE_SQR = 100.0D;
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
    // Consecutive soft dribble taps; forces a release pass when it runs long.
    private int dribbleHoldTicks;
    private int feintCooldown;
    // Off-the-ball attacking run window so support players sprint into space
    // and become through-ball targets instead of standing in their slot.
    private int runCooldown;
    private int runTicks;
    private Vec3 runPoint;
    // Last navigation target, used to skip wasteful path recalculations that
    // made the bots stutter/stop-and-go while following a moving target.
    private Vec3 lastPathTarget;

    public NpcPlayGoal(SoccerNpcEntity npc) {
        this.npc = npc;
        // Stagger the first off-ball run so the whole squad does not sprint
        // forward on the same tick.
        this.runCooldown = 25 + Math.floorMod(npc.getId() * 17, 50);
        setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // The referee entity runs RefereeGoal instead of the team brain.
        if (npc.level().isClientSide || npc.isReferee()) {
            return false;
        }
        // After full time the squad only plays the farewell-wave scene;
        // chasing the ball during it looked like the match never ended.
        if (npc.level() instanceof ServerLevel level) {
            MatchManager manager = MatchManager.get(level);
            if (manager.getState() == MatchState.FINISHED) {
                npc.getNavigation().stop();
                return false;
            }
        }
        return true;
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

        DifficultyProfile profile = profile(manager);
        if (--roleCooldown <= 0 || ball == null) {
            roleCooldown = profile.roleRefreshTicks();
            // Shared, hysteresis-stable assignment from the manager: exactly
            // one outfield bot per team presses the ball.
            cachedChaser = manager.getChaser(serverLevel, npc.getSportTeam()) == npc;
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
        if (feintCooldown > 0) {
            feintCooldown--;
        }
        if (runCooldown > 0) {
            runCooldown--;
        }
        if (runTicks > 0) {
            runTicks--;
            if (runTicks == 0) {
                runPoint = null;
                runCooldown = RUN_REST_TICKS + npc.getRandom().nextInt(35);
            }
        }
    }

    /**
     * Difficulty-reactive behaviour tuning. EASY bots press slowly and shoot
     * only from close range; HARD bots press faster, from further out and
     * feint past defenders more often.
     */
    private DifficultyProfile profile(MatchManager manager) {
        return switch (manager.getSettings().getDifficulty()) {
            case EASY -> new DifficultyProfile(14, 1.26D, 81.0D, 4.5D, 0.30F);
            case HARD -> new DifficultyProfile(8, 1.46D, 169.0D, 6.0D, 0.75F);
            default -> new DifficultyProfile(10, 1.38D, 144.0D, 5.0D, 0.55F);
        };
    }

    private record DifficultyProfile(int roleRefreshTicks, double chaseSpeed,
                                     double shotRangeSqr, double pressureRadius,
                                     float feintChance) {
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

        DifficultyProfile profile = profile(manager);

        // First-touch rule: whoever has the loose ball at their feet plays it,
        // not only the designated presser. The ball's own kick cooldown makes
        // the first bot to touch it win, so contests never double-kick.
        if (ball != null && !ball.isCarried() && kickCooldown == 0
                && npc.distanceToSqr(ball) < KICK_DISTANCE_SQR) {
            decideWithBall(level, manager, attackGoal, profile);
            return;
        }

        // The designated presser hunts the ball from further out; everyone
        // else keeps formation shape, makes runs and offers passing options.
        if (cachedChaser && ball != null && !ball.isCarried()) {
            tickChaser(level, manager, attackGoal, profile);
            return;
        }

        boolean possession = ownTeamInPossession(level, manager);
        Vec3 target = decideTarget(level, manager, settings, center, ownGoal, attackGoal, possession);
        double speed = runTicks > 0 ? 1.32D : (cachedRank == 1 ? 1.22D : 1.14D);
        moveToward(target, speed);
        maybeTackle(level, manager);
    }

    /** Presser behaviour: intercept the rolling ball, then shoot, pass or dribble. */
    private void tickChaser(ServerLevel level, MatchManager manager, BlockPos attackGoal,
                            DifficultyProfile profile) {
        if (ball == null) {
            return;
        }
        // Out of control range (or cooling down from a touch): run onto the
        // ball, leading its roll so bots intercept instead of chasing tails.
        if (kickCooldown > 0 || npc.distanceToSqr(ball) >= KICK_DISTANCE_SQR) {
            moveToward(chasePoint(ball), profile.chaseSpeed());
            maybeTackle(level, manager);
            if (npc.distanceToSqr(ball) > 16.0D) {
                dribbleHoldTicks = Math.max(0, dribbleHoldTicks - 1);
            }
            return;
        }
        decideWithBall(level, manager, attackGoal, profile);
    }

    /** Ball is at the presser's feet: one of shot, pass, clearance or dribble. */
    private void decideWithBall(ServerLevel level, MatchManager manager, BlockPos attackGoal,
                                DifficultyProfile profile) {
        LivingEntity pressure = nearestOpponentWithin(level, manager,
                ball.position(), profile.pressureRadius());
        double goalDistanceSqr = horizontalDistanceSqr(ball.position(), attackGoal);
        boolean goalLane = laneIsClear(level, manager, ball.position(), attackGoal, SHOT_LANE_WIDTH);

        // 1) Finish: in range, narrow open lane to the goal mouth. Aim for the
        // corner away from the keeper instead of blasting straight at them.
        if (goalDistanceSqr < profile.shotRangeSqr() && goalLane) {
            Vec3 aim = cornerAim(level, manager, attackGoal);
            ball.kickFromMob(npc, aim.subtract(ball.position()), 1.42D, 0.26D);
            kickCooldown = KICK_LOCK_TICKS;
            dribbleHoldTicks = 0;
            npc.startKickWindup();
            maybeTackle(level, manager);
            return;
        }

        // 2) Human pass request: a real teammate pressed the call-for-ball
        // key, so play a lead pass just in front of him whenever a lane exists.
        ServerPlayer requester = manager.takePassRequest(level, npc.getSportTeam());
        if (requester != null && requester.isAlive()
                && npc.distanceToSqr(requester) < 576.0D) {
            Vec3 lead = passLeadPoint(level, requester);
            if (laneIsClear(level, manager, ball.position(), lead, PASS_LANE_WIDTH + 0.4D)) {
                double distance = Math.sqrt(npc.distanceToSqr(lead.x, lead.y, lead.z));
                double reqSpeed = Math.min(1.3D, 0.9D + distance * 0.03D);
                ball.kickFromMob(npc, lead.subtract(ball.position()), reqSpeed, 0.16D);
                kickCooldown = KICK_LOCK_TICKS;
                dribbleHoldTicks = 0;
                npc.startKickWindup();
                maybeTackle(level, manager);
                return;
            }
            // Lane blocked: keep the request alive briefly for the next touch.
            manager.rearmPassRequest(requester);
        }

        // 3) Through ball (ara pas): a runner is attacking open space ahead
        // of the defence, so the pass goes into that space, not at his feet.
        // Gated so the squad still dribbles and combines instead of launching
        // one long ball every single touch.
        if (goalDistanceSqr >= profile.shotRangeSqr() || !goalLane) {
            Vec3 through = pickThroughBall(level, manager, attackGoal);
            if (through != null && (pressure != null
                    || dribbleHoldTicks >= 4 || level.random.nextFloat() < 0.45F)) {
                ball.kickFromMob(npc, through.subtract(ball.position()), 1.22D, 0.15D);
                kickCooldown = KICK_LOCK_TICKS;
                dribbleHoldTicks = 0;
                npc.startKickWindup();
                maybeTackle(level, manager);
                return;
            }
        }

        // 4) Tiki-taka short combo: an unmarked teammate 4-11 blocks away with
        // a clean lane receives a crisp ground pass, producing the quick
        // keep-ball triangles from the reference play style. Not automatic
        // every touch: a short carry first keeps the dribble alive.
        SoccerNpcEntity comboTarget = pickShortComboTarget(level, manager, attackGoal);
        if (comboTarget != null && (dribbleHoldTicks >= 3
                || (pressure != null && level.random.nextFloat() < 0.7F))) {
            Vec3 passDirection = comboTarget.position().add(0.0D, 1.0D, 0.0D)
                    .subtract(ball.position());
            double distance = Math.sqrt(npc.distanceToSqr(comboTarget));
            double comboSpeed = Math.min(1.2D, 0.92D + distance * 0.02D);
            ball.kickFromMob(npc, passDirection, comboSpeed, 0.15D);
            kickCooldown = KICK_LOCK_TICKS;
            dribbleHoldTicks = 0;
            maybeTackle(level, manager);
            return;
        }

        // 5) Lofted pass (yüksekten pas): every ground lane is blocked but a
        // teammate is open beyond the defenders, so lift the ball over them.
        SoccerNpcEntity lobTarget = pickLobTarget(level, manager, attackGoal);
        if (lobTarget != null && (pressure != null || dribbleHoldTicks >= DRIBBLE_STUCK_TAPS)) {
            Vec3 lobDirection = lobTarget.position().subtract(ball.position());
            double distance = Math.sqrt(npc.distanceToSqr(lobTarget));
            double lobSpeed = Math.min(1.15D, 0.78D + distance * 0.018D);
            double lobUp = distance < 11.0D ? 0.34D : (distance < 18.0D ? 0.44D : 0.54D);
            ball.kickFromMob(npc, lobDirection, lobSpeed, lobUp);
            kickCooldown = KICK_LOCK_TICKS;
            dribbleHoldTicks = 0;
            npc.startKickWindup();
            maybeTackle(level, manager);
            return;
        }

        // 6) Ground passing network: release under pressure, after carrying
        // too long, or when a teammate is clearly better placed.
        SoccerNpcEntity passTarget = pickPassTarget(level, manager, attackGoal, pressure != null);
        if (passTarget != null && (pressure != null || dribbleHoldTicks >= DRIBBLE_STUCK_TAPS)) {
            Vec3 passDirection = passTarget.position().add(0.0D, 1.0D, 0.0D)
                    .subtract(ball.position());
            double distance = Math.sqrt(npc.distanceToSqr(passTarget));
            double passSpeed = Math.min(1.25D, 0.85D + distance * 0.025D);
            ball.kickFromMob(npc, passDirection, passSpeed, 0.16D);
            kickCooldown = KICK_LOCK_TICKS;
            dribbleHoldTicks = 0;
            maybeTackle(level, manager);
            return;
        }

        // 7) Closed down with no outlet: emergency clearance upfield.
        if (pressure != null) {
            ball.kickFromMob(npc, Vec3.atBottomCenterOf(attackGoal).subtract(ball.position()),
                    1.3D, 0.32D);
            kickCooldown = KICK_LOCK_TICKS;
            dribbleHoldTicks = 0;
            maybeTackle(level, manager);
            return;
        }

        // 8) Space ahead: carry with a soft, angled touch that bends around
        // the nearest defender (the dribble/çalım), plus an occasional feint.
        Vec3 dribbleDir = dribbleDirection(level, manager, attackGoal, pressure);
        ball.kickFromMob(npc, dribbleDir, 0.82D, 0.18D);
        kickCooldown = KICK_LOCK_TICKS;
        dribbleHoldTicks++;
        feint(level, dribbleDir, profile);
        // Run straight onto where the touch went so possession is kept.
        moveToward(ball.position().add(dribbleDir.scale(1.6D)), profile.chaseSpeed());
        maybeTackle(level, manager);
    }

    /** Predicted meeting point: ball position plus a short lead along its roll. */
    private Vec3 chasePoint(SoccerBallEntity target) {
        Vec3 velocity = target.getDeltaMovement();
        Vec3 flat = new Vec3(velocity.x, 0.0D, velocity.z);
        double speed = flat.length();
        if (speed < 0.08D) {
            return target.position();
        }
        double lead = Math.min(3.0D, speed * 4.0D);
        return target.position().add(flat.normalize().scale(lead));
    }

    /** Goal-mouth point on the side farther from the defending keeper. */
    private Vec3 cornerAim(ServerLevel level, MatchManager manager, BlockPos attackGoal) {
        Vec3 mouth = Vec3.atBottomCenterOf(attackGoal);
        SoccerNpcEntity keeper = findKeeper(level, npc.getSportTeam().opponent());
        double side;
        if (keeper != null) {
            side = keeper.getX() >= mouth.x ? -GOAL_MOUTH_OFFSET : GOAL_MOUTH_OFFSET;
        } else {
            side = npc.getX() >= mouth.x ? GOAL_MOUTH_OFFSET : -GOAL_MOUTH_OFFSET;
        }
        return new Vec3(mouth.x + side, mouth.y, mouth.z);
    }

    private SoccerNpcEntity findKeeper(ServerLevel level, Team team) {
        if (managerCenter(level) == null) {
            return null;
        }
        AABB area = new AABB(managerCenter(level)).inflate(BALL_SEARCH_RADIUS);
        for (SoccerNpcEntity candidate : level.getEntitiesOfClass(SoccerNpcEntity.class, area)) {
            if (candidate.isAlive() && !candidate.isReferee()
                    && candidate.getSportTeam() == team && candidate.isKeeper()) {
                return candidate;
            }
        }
        return null;
    }

    private BlockPos managerCenter(ServerLevel level) {
        return MatchManager.get(level).getFieldCenter();
    }

    /**
     * Forward dribble direction bent ~40 degrees away from a defender that
     * blocks the straight lane, so the bot visibly dribbles around opponents
     * instead of tapping the ball into their legs.
     */
    private Vec3 dribbleDirection(ServerLevel level, MatchManager manager, BlockPos attackGoal,
                                  LivingEntity pressure) {
        Vec3 goal = Vec3.atBottomCenterOf(attackGoal);
        Vec3 dir = new Vec3(goal.x - ball.getX(), 0.0D, goal.z - ball.getZ());
        if (dir.lengthSqr() < 1.0E-6D) {
            dir = new Vec3(0.0D, 0.0D, 1.0D);
        }
        dir = dir.normalize();
        if (pressure == null) {
            return dir;
        }
        Vec3 toRival = new Vec3(pressure.getX() - ball.getX(), 0.0D,
                pressure.getZ() - ball.getZ());
        if (toRival.lengthSqr() < 1.0E-6D) {
            return dir;
        }
        Vec3 rivalDir = toRival.normalize();
        // Only bend when the defender actually stands in the forward lane.
        if (dir.dot(rivalDir) <= 0.25D) {
            return dir;
        }
        Vec3 away = rivalDir.scale(-1.0D);
        double angle = 0.7D;
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double sign = (dir.x * away.z - dir.z * away.x) >= 0.0D ? 1.0D : -1.0D;
        Vec3 bent = new Vec3(cos * dir.x - sin * sign * dir.z, 0.0D,
                sin * sign * dir.x + cos * dir.z);
        return bent.add(away.scale(0.25D)).normalize();
    }

    /** Short lateral sidestep with a dust puff while dribbling past a rival. */
    private void feint(ServerLevel level, Vec3 dribbleDir, DifficultyProfile profile) {
        if (feintCooldown > 0 || level.random.nextFloat() >= profile.feintChance()) {
            return;
        }
        LivingEntity rival = nearestOpponentWithin(level, MatchManager.get(level),
                ball.position(), 3.2D);
        if (rival == null) {
            return;
        }
        double sign = level.random.nextBoolean() ? 1.0D : -1.0D;
        Vec3 side = new Vec3(-dribbleDir.z * sign, 0.0D, dribbleDir.x * sign);
        npc.setDeltaMovement(side.x * 0.75D, npc.getDeltaMovement().y, side.z * 0.75D);
        npc.hasImpulse = true;
        feintCooldown = FEINT_COOLDOWN_TICKS;
        level.sendParticles(ParticleTypes.CLOUD, npc.getX(), npc.getY() + 0.1D, npc.getZ(),
                6, 0.25D, 0.1D, 0.25D, 0.02D);
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

        // Sweeper keeper: come off the line for loose balls the defence cannot
        // reach first, including through balls rolling into the box. During an
        // opposing free kick the parked ball stays untouched until the taker
        // strikes it.
        int inward = Integer.signum(center.getZ() - ownGoal.getZ());
        Vec3 guard = new Vec3(ownGoal.getX() + 0.5D, center.getY(), ownGoal.getZ() + inward * 1.5D);
        boolean setPieceFreeze = manager.isFreeKickActive();
        Vec3 sweepTarget = keeperSweepPoint(level, manager, ownGoal, setPieceFreeze);
        if (sweepTarget != null) {
            moveToward(sweepTarget, 1.28D);
        } else {
            // Shade across the goal mouth toward the ball side.
            Vec3 shaded = new Vec3(Mth.clamp(ball != null ? ball.getX() : guard.x,
                    guard.x - 2.0D, guard.x + 2.0D), guard.y, guard.z);
            moveToward(shaded, 1.12D);
        }

        if (ball != null && kickCooldown == 0 && !ball.isCarried()
                && npc.distanceToSqr(ball) < KICK_DISTANCE_SQR) {
            Vec3 clearDirection = Vec3.atBottomCenterOf(attackGoal).subtract(ball.position());
            ball.kickFromMob(npc, clearDirection, 1.3D, 0.3D);
            kickCooldown = KICK_LOCK_TICKS;
        }
    }

    /**
     * Sweeper-keeper gather point, or {@code null} when he should hold the
     * line. He comes out only for loose/slow balls or through balls heading
     * toward his own goal, and stays home when an outfield teammate is
     * clearly closer to the ball.
     */
    private Vec3 keeperSweepPoint(ServerLevel level, MatchManager manager,
                                  BlockPos ownGoal, boolean setPieceFreeze) {
        if (ball == null || ball.isCarried() || setPieceFreeze) {
            return null;
        }
        double distanceSqr = npc.distanceToSqr(ball);
        if (distanceSqr > KEEPER_SWEEP_RANGE_SQR) {
            return null;
        }
        double nearestMateSqr = Double.MAX_VALUE;
        for (SoccerNpcEntity mate : outfieldBots(level, npc.getSportTeam())) {
            nearestMateSqr = Math.min(nearestMateSqr, mate.distanceToSqr(ball));
        }
        boolean loose = ball.getDeltaMovement().horizontalDistanceSqr() < 0.06D;
        Vec3 velocity = ball.getDeltaMovement();
        Vec3 toGoal = Vec3.atBottomCenterOf(ownGoal).subtract(ball.position());
        Vec3 flat = new Vec3(velocity.x, 0.0D, velocity.z);
        boolean headingIn = flat.lengthSqr() > 1.0E-6D && toGoal.horizontalDistanceSqr() > 1.0E-6D
                && flat.normalize().dot(new Vec3(toGoal.x, 0.0D, toGoal.z).normalize()) > 0.15D;
        // A defender clearly closer to a ball that is not coming at the goal:
        // the keeper trusts the back line instead of rushing out for nothing.
        if (!loose && !headingIn && nearestMateSqr < distanceSqr) {
            return null;
        }
        if (!loose && nearestMateSqr + 4.0D < distanceSqr) {
            return null;
        }
        return chasePoint(ball);
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
        SoccerNpcEntity outlet = pickPassTarget(level, manager, attackGoal, false);
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
            Vec3 home = decideTarget(level, manager, settings, center, ownGoal, attackGoal,
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
            // Re-issue a path only when the target moved meaningfully or the
            // previous path finished; recalculating every tick against a
            // rolling ball produced visible stop-and-go stuttering.
            if (lastPathTarget == null || npc.getNavigation().isDone()
                    || lastPathTarget.distanceToSqr(target) > 2.25D) {
                npc.getNavigation().moveTo(target.x, target.y, target.z, speed);
                lastPathTarget = target;
            }
        } else {
            npc.getNavigation().stop();
            lastPathTarget = null;
        }
        npc.getLookControl().setLookAt(target.x, target.y + 1.0D, target.z);
    }

    // ------------------------------------------------------------------
    // Shared tactical helpers
    // ------------------------------------------------------------------

    private Vec3 decideTarget(ServerLevel level, MatchManager manager, MatchSettings settings,
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

        // A pass travelling toward this bot: step onto the predicted meeting
        // point so through balls and lobs are actively received.
        Vec3 interception = interceptionPoint();
        if (interception != null) {
            return interception;
        }

        // Loose ball within the secondary press window: any nearby bot steps
        // in, not just the rank-1 support man, so a reachable ball is never
        // left standing between two idle players.
        if (!ball.isCarried() && npc.distanceToSqr(ball) < SECOND_PRESS_SQR) {
            return ballPoint;
        }

        // Close support: offer a passing option just behind and beside the ball.
        if (cachedRank == 1) {
            Vec3 toOwnGoal = new Vec3(ownGoal.getX() + 0.5D - ballPoint.x, 0.0D,
                    ownGoal.getZ() + 0.5D - ballPoint.z);
            if (toOwnGoal.lengthSqr() > 1.0E-6D) {
                toOwnGoal = toOwnGoal.normalize();
            }
            double side = (npc.getSquadIndex() % 2 == 0) ? 2.0D : -2.0D;
            Vec3 lateral = new Vec3(-toOwnGoal.z * side, 0.0D, toOwnGoal.x * side);
            Vec3 supportSpot = ballPoint.add(toOwnGoal.scale(4.0D)).add(lateral);
            return home.lerp(supportSpot, possession ? 0.6D : 0.45D);
        }

        // Block movement: every formation player applies the SAME ball-axis
        // shift, so the whole team slides upfield/back and across as one
        // compact unit instead of eleven independent slots.
        double ballSideShift = Mth.clamp(ballPoint.x - (center.getX() + 0.5D), -3.5D, 3.5D) * 0.8D;
        double ballDepthShift = Mth.clamp(ballPoint.z - (center.getZ() + 0.5D),
                -size.getLength() / 2.0D, size.getLength() / 2.0D) * 0.45D;
        Vec3 shaped = new Vec3(home.x + ballSideShift, home.y, home.z + ballDepthShift);

        if (possession) {
            // In possession: the block pushes high, plus individual off-ball
            // runs into space so the passer always has moving options.
            Vec3 run = updateAttackingRun(level, manager, center, size, shaped, attackGoal);
            if (run != null) {
                return run;
            }
            Vec3 push = new Vec3(attackGoal.getX() + 0.5D - shaped.x, 0.0D,
                    attackGoal.getZ() + 0.5D - shaped.z);
            if (push.lengthSqr() > 1.0E-6D) {
                shaped = shaped.add(push.normalize().scale(4.0D));
            }
            return clampToField(center, size, shaped);
        }

        // Out of possession: mark the nearest opponent from a point between
        // him and our own goal; fall back to a compact defensive shift.
        LivingEntity mark = nearestOpponentWithin(level, manager, npc.position(), 14.0D);
        if (mark != null) {
            Vec3 toOwnGoal = new Vec3(ownGoal.getX() + 0.5D - mark.getX(), 0.0D,
                    ownGoal.getZ() + 0.5D - mark.getZ());
            if (toOwnGoal.lengthSqr() > 1.0E-6D) {
                toOwnGoal = toOwnGoal.normalize();
            }
            Vec3 markPoint = mark.position().add(toOwnGoal.scale(2.0D));
            return clampToField(center, size, shaped.lerp(markPoint, 0.55D));
        }
        Vec3 drop = new Vec3(ownGoal.getX() + 0.5D - shaped.x, 0.0D,
                ownGoal.getZ() + 0.5D - shaped.z);
        if (drop.lengthSqr() > 1.0E-6D) {
            shaped = shaped.add(drop.normalize().scale(3.0D));
        }
        return clampToField(center, size, shaped.lerp(ballPoint, 0.2D));
    }

    /**
     * State machine for off-ball attacking runs. While a run is active the
     * bot sprints to a point ahead of its formation slot; after reaching it
     * (or timing out) it rests before the next run. The start point must be
     * open space inside the pitch, which is exactly what through balls target.
     */
    private Vec3 updateAttackingRun(ServerLevel level, MatchManager manager, BlockPos center,
                                    FieldSize size, Vec3 home, BlockPos attackGoal) {
        if (runTicks > 0 && runPoint != null) {
            if (npc.distanceToSqr(runPoint.x, npc.getY(), runPoint.z) < 2.5D) {
                runTicks = 0;
                runPoint = null;
                runCooldown = RUN_REST_TICKS + npc.getRandom().nextInt(35);
                return null;
            }
            return runPoint;
        }
        if (runCooldown > 0) {
            return null;
        }
        // Re-arm a short wait when the roll fails so we do not randomise every tick.
        if (level.random.nextFloat() > 0.30F) {
            runCooldown = 8;
            return null;
        }
        Vec3 goal = Vec3.atBottomCenterOf(attackGoal);
        Vec3 forward = new Vec3(goal.x - home.x, 0.0D, goal.z - home.z);
        if (forward.lengthSqr() < 1.0E-6D) {
            runCooldown = 8;
            return null;
        }
        forward = forward.normalize();
        double wide = (npc.getSquadIndex() % 2 == 0 ? 1.0D : -1.0D)
                * (1.5D + npc.getRandom().nextDouble() * 1.5D);
        Vec3 lateral = new Vec3(-forward.z, 0.0D, forward.x).scale(wide);
        Vec3 candidate = clampToField(center, size, home.add(forward.scale(4.5D)).add(lateral));
        if (nearestOpponentWithin(level, manager, candidate, RUN_OPEN_SPACE_RADIUS) != null) {
            runCooldown = 12;
            return null;
        }
        runPoint = new Vec3(candidate.x, home.y, candidate.z);
        runTicks = RUN_DURATION_TICKS;
        return runPoint;
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
        Vec3 flatVelocity = new Vec3(velocity.x, 0.0D, velocity.z);
        double speed = flatVelocity.length();
        if (speed < 0.05D) {
            return null;
        }
        Vec3 toBot = new Vec3(npc.getX() - ball.getX(), 0.0D, npc.getZ() - ball.getZ());
        if (toBot.lengthSqr() < 1.0E-6D || toBot.horizontalDistanceSqr() > 400.0D) {
            return null;
        }
        double alignment = flatVelocity.normalize().dot(toBot.normalize());
        if (alignment < 0.35D) {
            return null;
        }
        // Simulate the ball's horizontal path: a short airborne stretch with
        // no friction (lobs and lifted passes), then ground rolling with the
        // soccer friction. The bot runs to the closest approach of that path,
        // so through balls and lobs are met instead of watched roll past.
        Vec3 point = ball.position();
        Vec3 step = flatVelocity;
        Vec3 best = null;
        double bestDistanceSqr = Double.MAX_VALUE;
        for (int tick = 1; tick <= 42; tick++) {
            if (tick > 6) {
                step = step.scale(0.82D);
            }
            point = point.add(step.x, 0.0D, step.z);
            double distanceSqr = point.distanceToSqr(npc.getX(), point.y, npc.getZ());
            if (distanceSqr < bestDistanceSqr) {
                bestDistanceSqr = distanceSqr;
                best = point;
            }
            if (step.lengthSqr() < 0.002D) {
                break;
            }
        }
        return bestDistanceSqr <= 7.5D ? best : null;
    }

    /**
     * Passing network: scores every teammate by forward progress toward the
     * attacked goal, distance and free space, rejecting lanes blocked by an
     * opponent. Returns {@code null} when no safe option exists.
     */
    private SoccerNpcEntity pickPassTarget(ServerLevel level, MatchManager manager,
                                           BlockPos attackGoal, boolean allowBackward) {
        if (ball == null) {
            return null;
        }
        AABB searchArea = npc.getBoundingBox().inflate(28.0D);
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
            // Under pressure a safety pass backward is allowed; otherwise keep
            // the passing network oriented toward the attacked goal.
            if (progress <= (allowBackward ? -10.0D : -2.0D)) {
                continue;
            }
            if (!laneIsClear(level, manager, ball.position(), candidate.position(), PASS_LANE_WIDTH)) {
                continue;
            }
            boolean marked = nearestOpponentWithin(level, manager,
                    candidate.position(), 3.0D) != null;
            double openness = marked ? 0.0D : 6.0D;
            double pressurePenalty = marked ? 8.0D : 0.0D;
            double score = progress * 1.2D + openness
                    - 0.12D * npc.distanceToSqr(candidate) - pressurePenalty;
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    /**
     * Tiki-taka combo target: an unmarked teammate in the 4-11 block band,
     * sideways or forward, with a clean narrow ground lane. Prefers the most
     * advanced open option so short combinations keep progressing upfield.
     */
    private SoccerNpcEntity pickShortComboTarget(ServerLevel level, MatchManager manager,
                                                 BlockPos attackGoal) {
        if (ball == null) {
            return null;
        }
        AABB searchArea = npc.getBoundingBox().inflate(14.0D);
        SoccerNpcEntity best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        double ownGoalDistance = horizontalDistanceSqr(npc.position(), attackGoal);
        for (SoccerNpcEntity candidate : level.getEntitiesOfClass(SoccerNpcEntity.class, searchArea)) {
            if (candidate == npc || candidate.isReferee() || !candidate.isAlive()
                    || candidate.getSportTeam() != npc.getSportTeam() || candidate.isKeeper()) {
                continue;
            }
            double distanceSqr = npc.distanceToSqr(candidate);
            if (distanceSqr < 16.0D || distanceSqr > 121.0D) {
                continue;
            }
            if (nearestOpponentWithin(level, manager, candidate.position(), 2.6D) != null) {
                continue;
            }
            if (!laneIsClear(level, manager, ball.position(), candidate.position(), PASS_LANE_WIDTH)) {
                continue;
            }
            double progress = ownGoalDistance
                    - horizontalDistanceSqr(candidate.position(), attackGoal);
            if (progress < -4.0D) {
                continue;
            }
            double score = progress * 1.1D - 0.1D * distanceSqr;
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    /** Lead point 1.6 blocks ahead of a requesting player's facing/movement. */
    private Vec3 passLeadPoint(ServerLevel level, ServerPlayer player) {
        Vec3 facing = player.getLookAngle();
        Vec3 flat = new Vec3(facing.x, 0.0D, facing.z);
        if (flat.lengthSqr() < 1.0E-6D) {
            flat = player.position().subtract(ball.position());
            flat = new Vec3(flat.x, 0.0D, flat.z);
        }
        if (flat.lengthSqr() < 1.0E-6D) {
            flat = new Vec3(0.0D, 0.0D, 1.0D);
        }
        MatchManager manager = MatchManager.get(level);
        BlockPos center = manager.getFieldCenter();
        Vec3 lead = player.position().add(flat.normalize().scale(1.6D));
        if (center != null) {
            lead = clampToField(center, manager.getSettings().getFieldSize(), lead);
        }
        return new Vec3(lead.x, ball.getY(), lead.z);
    }

    /**
     * Through-ball (ara pas) target: open space {@code THROUGH_LEAD} blocks
     * ahead of a teammate who is already advancing toward the attacked goal.
     * Returns the lead point the ball should be kicked into, or {@code null}
     * when no runner has the required space and a clear ground lane.
     */
    private Vec3 pickThroughBall(ServerLevel level, MatchManager manager, BlockPos attackGoal) {
        if (ball == null) {
            return null;
        }
        BlockPos center = manager.getFieldCenter();
        if (center == null) {
            return null;
        }
        Vec3 goal = Vec3.atBottomCenterOf(attackGoal);
        Vec3 forward = new Vec3(goal.x - ball.getX(), 0.0D, goal.z - ball.getZ());
        if (forward.lengthSqr() < 1.0E-6D) {
            return null;
        }
        forward = forward.normalize();
        AABB searchArea = npc.getBoundingBox().inflate(30.0D);
        Vec3 best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (SoccerNpcEntity runner : level.getEntitiesOfClass(SoccerNpcEntity.class, searchArea)) {
            if (runner == npc || runner.isReferee() || !runner.isAlive()
                    || runner.getSportTeam() != npc.getSportTeam() || runner.isKeeper()) {
                continue;
            }
            Vec3 toRunner = new Vec3(runner.getX() - ball.getX(), 0.0D,
                    runner.getZ() - ball.getZ());
            double progress = toRunner.dot(forward);
            if (progress < THROUGH_MIN_PROGRESS) {
                continue;
            }
            Vec3 lead = clampToField(center, manager.getSettings().getFieldSize(),
                    runner.position().add(forward.scale(THROUGH_LEAD)));
            double leadProgress = new Vec3(lead.x - ball.getX(), 0.0D,
                    lead.z - ball.getZ()).dot(forward);
            // The runner needs actual grass to sprint into.
            if (leadProgress < progress + 1.5D) {
                continue;
            }
            if (nearestOpponentWithin(level, manager, lead, THROUGH_SPACE_RADIUS) != null) {
                continue;
            }
            if (!laneIsClear(level, manager, ball.position(), lead, PASS_LANE_WIDTH - 0.1D)) {
                continue;
            }
            double score = leadProgress
                    - 0.15D * npc.distanceToSqr(lead.x, npc.getY(), lead.z);
            if (score > bestScore) {
                bestScore = score;
                best = new Vec3(lead.x, ball.getY(), lead.z);
            }
        }
        return best;
    }

    /**
     * Lofted-pass (yüksekten pas) target: an unmarked teammate in the forward
     * arc whose ground lane is blocked or simply far enough that an aerial
     * ball beats the defensive line. Returns {@code null} when no safe
     * overhead option exists.
     */
    private SoccerNpcEntity pickLobTarget(ServerLevel level, MatchManager manager,
                                          BlockPos attackGoal) {
        if (ball == null) {
            return null;
        }
        Vec3 goal = Vec3.atBottomCenterOf(attackGoal);
        Vec3 forward = new Vec3(goal.x - ball.getX(), 0.0D, goal.z - ball.getZ());
        if (forward.lengthSqr() < 1.0E-6D) {
            return null;
        }
        forward = forward.normalize();
        AABB searchArea = npc.getBoundingBox().inflate(30.0D);
        SoccerNpcEntity best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (SoccerNpcEntity candidate : level.getEntitiesOfClass(SoccerNpcEntity.class, searchArea)) {
            if (candidate == npc || candidate.isReferee() || !candidate.isAlive()
                    || candidate.getSportTeam() != npc.getSportTeam() || candidate.isKeeper()) {
                continue;
            }
            double distanceSqr = npc.distanceToSqr(candidate);
            if (distanceSqr < LOB_MIN_DIST_SQR || distanceSqr > LOB_MAX_DIST_SQR) {
                continue;
            }
            Vec3 toMate = new Vec3(candidate.getX() - ball.getX(), 0.0D,
                    candidate.getZ() - ball.getZ());
            if (toMate.dot(forward) < -3.0D) {
                continue;
            }
            // The landing zone has to be free: a tightly marked receiver
            // would lose the header/second ball immediately.
            if (nearestOpponentWithin(level, manager, candidate.position(), 2.8D) != null) {
                continue;
            }
            boolean groundBlocked = !laneIsClear(level, manager,
                    ball.position(), candidate.position(), PASS_LANE_WIDTH);
            double distance = Math.sqrt(distanceSqr);
            // Prefer the lob when the ground route is blocked; long switches
            // of play are also valid aerial balls.
            if (!groundBlocked && distance < 13.0D) {
                continue;
            }
            double score = toMate.dot(forward) * 1.3D + (groundBlocked ? 5.0D : 0.0D)
                    - 0.08D * distanceSqr;
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    /** Clamps a movement/pass point to the playable rectangle of the pitch. */
    private Vec3 clampToField(BlockPos center, FieldSize size, Vec3 point) {
        double maxX = Math.max(2.0D, size.getWidth() / 2.0D - 1.5D);
        double maxZ = Math.max(3.0D, size.getLength() / 2.0D - 2.0D);
        return new Vec3(
                Mth.clamp(point.x, center.getX() + 0.5D - maxX, center.getX() + 0.5D + maxX),
                point.y,
                Mth.clamp(point.z, center.getZ() + 0.5D - maxZ, center.getZ() + 0.5D + maxZ));
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
