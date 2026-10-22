package com.mcsport.entity.ai;

import com.mcsport.entity.SoccerBallEntity;
import com.mcsport.entity.SoccerNpcEntity;
import com.mcsport.match.MatchManager;
import com.mcsport.match.MatchState;
import com.mcsport.match.Team;
import com.mcsport.registry.ModItems;
import com.mcsport.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Brain of the on-pitch referee. The referee follows the play from a
 * sideline-biased position and turns the server-authoritative match events
 * (kick-off, goals, reported fouls) into visible decisions: a whistle sound,
 * a raised-arm signal animation and a broadcast chat message.
 *
 * <p>Fouls play out as a short scene: the referee runs to the offending
 * player, stops face to face and "talks" to them, then holds the animated
 * yellow/red card item when the discipline ledger requires one, and finally
 * watches the free kick from beside the spot. All events are produced by
 * {@link MatchManager}; this goal only reacts, so it can never invent a goal,
 * foul or card, and every transition is timeout-guarded against a missing or
 * removed offender.</p>
 */
public class RefereeGoal extends Goal {

    private static final double BALL_SEARCH_RADIUS = 64.0D;
    private static final int REPATH_INTERVAL = 10;
    private static final double SIDELINE_OFFSET = 3.5D;
    private static final double TRAIL_OFFSET = 3.0D;

    private static final int STATE_NONE = 0;
    private static final int STATE_GO_TO_FOUL = 1;
    private static final int STATE_TALK = 2;
    private static final int STATE_CARD = 3;
    private static final int STATE_WATCH_SET_PIECE = 4;
    private static final int TALK_DURATION_TICKS = 28;
    private static final int GO_TO_FOUL_TIMEOUT = 130;
    private static final int CARD_TIMEOUT_TICKS = 60;

    private final SoccerNpcEntity npc;
    private SoccerBallEntity ball;
    private int repathCooldown;
    private boolean observed;
    private int lastRedScore;
    private int lastBlueScore;
    private MatchState lastState = MatchState.IDLE;
    private int lastHandledFoulTick = Integer.MIN_VALUE;

    private int foulState = STATE_NONE;
    private int stateTicks;
    private UUID pendingTackler;
    private byte pendingCard = SoccerNpcEntity.CARD_NONE;
    private Vec3 pendingFoulPos = Vec3.ZERO;

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
        if (foulState == STATE_NONE) {
            handleMovement(center);
        } else {
            handleFoulScene(serverLevel, manager);
        }
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
            broadcast(serverLevel, Component.translatable("message.mcsport.referee_kickoff")
                    .withStyle(ChatFormatting.YELLOW));
        }

        // Full time: three short closing whistles, both arms up and the final
        // score in chat, then the match manager despawns the whole squad.
        if (state == MatchState.FINISHED && lastState != MatchState.FINISHED) {
            whistle(serverLevel);
            npc.raiseSignal();
            Component winner = finalScoreLine(manager);
            broadcast(serverLevel, Component.translatable("message.mcsport.referee_full_time", winner)
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        }

        if (manager.getRedScore() != lastRedScore || manager.getBlueScore() != lastBlueScore) {
            boolean redScored = manager.getRedScore() != lastRedScore;
            lastRedScore = manager.getRedScore();
            lastBlueScore = manager.getBlueScore();
            whistle(serverLevel);
            npc.raiseSignal();
            Component team = Component.translatable((redScored ? Team.RED : Team.BLUE).translationKey());
            broadcast(serverLevel, Component.translatable("message.mcsport.referee_goal",
                            team, manager.getRedScore(), manager.getBlueScore())
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        }

        if (manager.getLastFoulTick() > lastHandledFoulTick) {
            lastHandledFoulTick = manager.getLastFoulTick();
            whistle(serverLevel);
            npc.raiseSignal();
            Component team = Component.translatable(manager.getLastFoulTeam().translationKey());
            broadcast(serverLevel, Component.translatable("message.mcsport.referee_foul", team)
                    .withStyle(ChatFormatting.RED));
            // Open the animated foul scene.
            pendingTackler = manager.getLastFoulTackler();
            pendingCard = manager.getLastFoulCard();
            pendingFoulPos = manager.getLastFoulPos();
            foulState = STATE_GO_TO_FOUL;
            stateTicks = 0;
        }

        lastState = state;
    }

    /** Drives the run-in -> talk -> card -> watch sequence around a foul. */
    private void handleFoulScene(ServerLevel level, MatchManager manager) {
        stateTicks++;
        Entity tackler = pendingTackler == null ? null : level.getEntity(pendingTackler);
        Vec3 scenePos = tackler != null ? tackler.position() : pendingFoulPos;

        switch (foulState) {
            case STATE_GO_TO_FOUL -> {
                // Run right up to the offender and face them.
                if (npc.distanceToSqr(scenePos.x, npc.getY(), scenePos.z) > 2.2D
                        && stateTicks < GO_TO_FOUL_TIMEOUT) {
                    npc.getNavigation().moveTo(scenePos.x, scenePos.y, scenePos.z, 1.3D);
                } else {
                    npc.getNavigation().stop();
                    foulState = STATE_TALK;
                    stateTicks = 0;
                }
                npc.getLookControl().setLookAt(scenePos.x, scenePos.y + 1.2D, scenePos.z);
            }
            case STATE_TALK -> {
                // Stand toe to toe and warn the player.
                npc.getNavigation().stop();
                npc.getLookControl().setLookAt(scenePos.x, scenePos.y + 1.2D, scenePos.z);
                if (stateTicks == 1) {
                    npc.raiseSignal();
                    broadcast(level, Component.translatable("message.mcsport.referee_warning")
                            .withStyle(ChatFormatting.GRAY));
                }
                if (stateTicks >= TALK_DURATION_TICKS) {
                    if (pendingCard != SoccerNpcEntity.CARD_NONE) {
                        equipCard(pendingCard);
                        npc.showCard(pendingCard);
                        whistle(level);
                        broadcast(level, cardMessage(pendingCard));
                        foulState = STATE_CARD;
                    } else {
                        foulState = STATE_WATCH_SET_PIECE;
                    }
                    stateTicks = 0;
                }
            }
            case STATE_CARD -> {
                // Hold the card up until the synched animation finishes.
                npc.getNavigation().stop();
                npc.getLookControl().setLookAt(scenePos.x, scenePos.y + 1.2D, scenePos.z);
                if (!npc.isShowingCard() || stateTicks > CARD_TIMEOUT_TICKS) {
                    npc.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                    foulState = STATE_WATCH_SET_PIECE;
                    stateTicks = 0;
                }
            }
            default -> {
                // Watch the free kick from beside the ball; resume normal
                // trailing once the set piece is over or times out.
                if (!manager.isFreeKickActive()) {
                    npc.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                    foulState = STATE_NONE;
                    return;
                }
                Vec3 spot = manager.getFreeKickPos();
                Vec3 watch = new Vec3(spot.x + SIDELINE_OFFSET, spot.y, spot.z + TRAIL_OFFSET);
                if (npc.distanceToSqr(watch) > 1.6D) {
                    npc.getNavigation().moveTo(watch.x, watch.y, watch.z, 1.2D);
                } else {
                    npc.getNavigation().stop();
                }
                npc.getLookControl().setLookAt(spot.x, spot.y + 1.0D, spot.z);
            }
        }
    }

    /** Final score phrasing for the full-time announcement. */
    private Component finalScoreLine(MatchManager manager) {
        int red = manager.getRedScore();
        int blue = manager.getBlueScore();
        if (red == blue) {
            return Component.translatable("message.mcsport.result_draw", red, blue);
        }
        Team winner = red > blue ? Team.RED : Team.BLUE;
        return Component.translatable("message.mcsport.result_winner",
                Component.translatable(winner.translationKey()),
                Math.max(red, blue), Math.min(red, blue));
    }

    private Component cardMessage(byte card) {
        String key = card == SoccerNpcEntity.CARD_RED
                ? "message.mcsport.referee_red_card"
                : "message.mcsport.referee_yellow_card";
        return Component.translatable(key)
                .withStyle(card == SoccerNpcEntity.CARD_RED ? ChatFormatting.DARK_RED : ChatFormatting.YELLOW);
    }

    private void equipCard(byte card) {
        ItemStack cardStack = new ItemStack(card == SoccerNpcEntity.CARD_RED
                ? ModItems.RED_CARD.get() : ModItems.YELLOW_CARD.get());
        npc.setItemSlot(EquipmentSlot.MAINHAND, cardStack);
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
