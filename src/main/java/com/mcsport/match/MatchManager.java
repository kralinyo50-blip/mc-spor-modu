package com.mcsport.match;

import com.mcsport.advancement.ModCriteriaTriggers;
import com.mcsport.entity.SoccerBallEntity;
import com.mcsport.entity.SoccerNpcEntity;
import com.mcsport.network.ModNetwork;
import com.mcsport.network.network_packet.MatchStateS2C;
import com.mcsport.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-authoritative match state, persisted with the overworld data storage.
 *
 * <p>Holds the match lifecycle, clock, scores, player-to-team assignments,
 * goalkeeper flags, the setup-GUI {@link MatchSettings} and the auto-built
 * field status. Clients never mutate this directly; they send validated C2S
 * packets and receive {@link MatchStateS2C} snapshots.</p>
 */
public class MatchManager extends SavedData {

    private static final String DATA_NAME = "mcsport_match";
    private static final int MIN_DURATION = 60;
    private static final int MAX_DURATION = 3600;

    private MatchState state = MatchState.IDLE;
    private int redScore;
    private int blueScore;
    private int remainingSeconds;
    // Current half (1 or 2). A full match always has two halves of the same
    // configured length; half-time flips sidesSwapped so the teams attack
    // the opposite goal after the break.
    private int half = 1;
    private boolean sidesSwapped;
    private int halfTimeTicks;
    private int configuredHalfSeconds;
    private static final int HALF_TIME_TICKS = 200;
    private final MatchSettings settings = new MatchSettings();
    private final Map<UUID, Team> teams = new HashMap<>();
    private final Set<UUID> keepers = new HashSet<>();
    private boolean fieldBuilt;
    private BlockPos fieldCenter;
    private boolean extraTimeUsed;
    private int tickCounter;
    private final Set<UUID> npcIds = new HashSet<>();
    // Preferred UI language ("tr"/"en"), learned from the connected players'
    // client locales and used to localise generated bot squad names.
    private String language = "tr";

    // Referee decision inputs. Fouls are reported by tackle logic and consumed
    // by RefereeGoal; the tick stamp makes the stream strictly de-duplicable.
    private int lastFoulTick = Integer.MIN_VALUE / 2;
    private Team lastFoulTeam = Team.RED;
    private Vec3 lastFoulPos = Vec3.ZERO;
    private UUID lastFoulTackler = null;
    private byte lastFoulCard = SoccerNpcEntity.CARD_NONE;
    // Discipline ledger: total fouls and shown yellow cards per participant.
    private final Map<UUID, Integer> foulCounts = new HashMap<>();
    private final Map<UUID, Integer> yellowCardCounts = new HashMap<>();
    // Real-player goals during the current match; drives the goal/hat-trick
    // advancements. Cleared whenever a fresh match starts, never saved.
    private final Map<UUID, Integer> playerGoals = new HashMap<>();
    // Real-player slide tackles: cooldown gate plus the active contact window.
    private final Map<UUID, Integer> playerSlideCooldownUntil = new HashMap<>();
    private final Map<UUID, Integer> playerSlideActiveUntil = new HashMap<>();
    // Call-for-ball requests (R key): player UUID -> server tick at which the
    // request expires. The on-ball bot consumes one when it plays the pass.
    private final Map<UUID, Integer> passRequestUntil = new HashMap<>();
    private static final int PASS_REQUEST_DURATION_TICKS = 45;
    private static final int PASS_REQUEST_REARM_TICKS = 12;

    // Free-kick phase. While active the teams freeze into set-piece roles
    // (wall, taker, support) instead of chasing; the phase ends on the kick
    // or automatically after the safety timeout.
    private boolean freeKickActive;
    private Team freeKickTeam = Team.RED;
    private Vec3 freeKickPos = Vec3.ZERO;
    private int freeKickEndTick;

    // Full-time ceremony. When the clock expires the players wave to the
    // crowd for a fixed scene and are then removed together; the referee's
    // closing whistle is driven by RefereeGoal off the state transition.
    private boolean ceremonyStarted;
    private int ceremonyTicks;

    // Fun-mode party rotation. Every PARTY_EVENT_INTERVAL ticks one random
    // short event replaces the previous one; between events play is normal.
    public enum PartyEvent {
        NONE, LOW_GRAVITY, SUPER_SHOT, SPEED_BURST
    }

    private PartyEvent partyEvent = PartyEvent.NONE;
    private int partyTicks;
    private static final int PARTY_EVENT_INTERVAL = 600;
    private static final int PARTY_EVENT_DURATION = 300;
    private static final double FUN_GRAVITY_FACTOR = 0.45D;
    private static final double FUN_SHOT_FACTOR = 1.6D;
    private static final double FUN_SPEED_FACTOR = 1.25D;

    // Stable presser (chaser) assignment per team. Without shared state every
    // bot recomputed "am I the closest?" independently, so two nearby bots
    // swapped the role every few ticks and oscillated forward/backward. The
    // manager picks one presser and keeps it until a teammate is clearly
    // closer (hysteresis), giving the whole squad deterministic roles.
    private UUID redChaser;
    private UUID blueChaser;
    private int chaserRefreshTick = Integer.MIN_VALUE / 2;
    private static final int CHASER_REFRESH_INTERVAL = 15;
    private static final double CHASER_HYSTERESIS_SQR = 6.25D;

    /** Outcome of a bot population run, used for the operator summary message. */
    public record PopulateResult(int redBots, int blueBots, int redPlayers,
                                 int bluePlayers, boolean referee) {
    }

    // Squad names and outfield kit colours now live on the selected Club.
    private static final int KIT_KEEPER = 0x2E8B46;
    private static final int KIT_REFEREE = 0xF2D024;
    private static final int KIT_DARK = 0x1E1E24;
    private static final int FOUL_MIN_GAP_TICKS = 50;
    // Clean tackle reach: ball within 2.4 blocks of the tackler is always fair,
    // so crowded contests rarely turn into fouls.
    private static final double CLEAN_TACKLE_DIST_SQR = 5.76D;
    private static final int PLAYER_SLIDE_COOLDOWN_TICKS = 25;
    // Long enough for the referee's run-in, talk and card scene before the
    // safety timeout releases the set piece; the kick itself ends it earlier.
    private static final int FREE_KICK_DURATION_TICKS = 280;
    private static final double FREE_KICK_WALL_DISTANCE = 4.2D;
    private static final double FREE_KICK_WALL_SPACING = 0.95D;
    // Keep the parked free-kick ball exactly at its resting centre height.
    private static final double BALL_REST_LIFT = SoccerBallEntity.BALL_RADIUS;

    public MatchManager() {
        this.remainingSeconds = settings.getHalfLengthSeconds();
    }

    public static MatchManager get(ServerLevel level) {
        // 1.20.1 signature: computeIfAbsent(loader Function, constructor Supplier, name).
        // The SavedData.Factory record was only introduced in 1.20.5.
        return level.getServer().overworld().getDataStorage()
                .computeIfAbsent(MatchManager::load, MatchManager::new, DATA_NAME);
    }

    /** Called once per server tick from the Forge event handler. */
    public void serverTick(MinecraftServer server) {
        tickPlayerSlides(server);
        if (freeKickActive && server.getTickCount() >= freeKickEndTick) {
            endFreeKick();
        }
        if (state == MatchState.FINISHED) {
            tickCeremony(server);
            return;
        }
        if (state == MatchState.HALF_TIME) {
            tickHalfTime(server);
            return;
        }
        if (state != MatchState.RUNNING) {
            return;
        }
        tickPartyEvents(server);
        if (++tickCounter >= 20) {
            tickCounter = 0;
            if (remainingSeconds > 0) {
                remainingSeconds--;
                if (remainingSeconds == 0) {
                    handleClockEnd(server);
                }
                setDirty();
                broadcast(server);
            }
        }
    }

    private void handleClockEnd(MinecraftServer server) {
        // End of the first half: enter the intermission instead of finishing.
        if (half == 1) {
            beginHalfTime(server);
            return;
        }
        if (redScore == blueScore && settings.getDrawRule() == DrawRule.EXTRA_TIME && !extraTimeUsed) {
            extraTimeUsed = true;
            remainingSeconds = MatchSettings.EXTRA_TIME_SECONDS;
        } else {
            state = MatchState.FINISHED;
            // Begin the farewell scene: wave first, then the squad despawns.
            ceremonyStarted = false;
            ceremonyTicks = SoccerNpcEntity.WAVE_DURATION_TICKS + 35;
            awardWinAdvancements(server);
            setDirty();
        }
    }

    /** Freezes the clock for a short break and announces the half-time score. */
    private void beginHalfTime(MinecraftServer server) {
        state = MatchState.HALF_TIME;
        halfTimeTicks = HALF_TIME_TICKS;
        // The client HUD renders remainingSeconds, so mirror the break
        // countdown there until the second half restarts the match clock.
        remainingSeconds = HALF_TIME_TICKS / 20;
        broadcast(server);
        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("message.mcsport.half_time",
                        redScore, blueScore).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                false);
        setDirty();
    }

    private void tickHalfTime(MinecraftServer server) {
        if (halfTimeTicks <= 0) {
            return;
        }
        halfTimeTicks--;
        // Keep the on-screen break countdown in sync once per second.
        int breakSeconds = halfTimeTicks / 20;
        if (breakSeconds != remainingSeconds) {
            remainingSeconds = breakSeconds;
            broadcast(server);
        }
        if (halfTimeTicks == 0) {
            beginSecondHalf(server);
        }
        setDirty();
    }

    /** Swaps sides, rebuilds the squads on their new halves and restarts play. */
    private void beginSecondHalf(MinecraftServer server) {
        ServerLevel level = server.overworld();
        sidesSwapped = !sidesSwapped;
        half = 2;
        remainingSeconds = configuredHalfSeconds > 0
                ? configuredHalfSeconds : settings.getHalfLengthSeconds();
        tickCounter = 0;
        state = MatchState.RUNNING;
        // Fresh kick-off: squads respawn facing their new goals and any ball
        // on the pitch is returned to the centre spot.
        populateNpcs(level, server);
        returnBallToCentre(level);
        broadcast(server);
        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("message.mcsport.second_half")
                        .withStyle(ChatFormatting.YELLOW),
                false);
        setDirty();
    }

    /** Teleports the pitch ball back to the centre spot, spawning one if absent. */
    private void returnBallToCentre(ServerLevel level) {
        if (fieldCenter == null) {
            return;
        }
        AABB searchArea = new AABB(fieldCenter).inflate(96.0D);
        java.util.List<SoccerBallEntity> balls =
                level.getEntitiesOfClass(SoccerBallEntity.class, searchArea);
        if (balls.isEmpty()) {
            ensureKickoffBall(level);
            return;
        }
        for (SoccerBallEntity ball : balls) {
            ball.resetTo(fieldCenter);
        }
    }

    /** Fires the champion advancement for every real player on the winning side. */
    private void awardWinAdvancements(MinecraftServer server) {
        if (redScore == blueScore) {
            return;
        }
        Team winner = redScore > blueScore ? Team.RED : Team.BLUE;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (getTeam(player.getUUID()) == winner) {
                ModCriteriaTriggers.MATCH_WIN.trigger(player, 1);
            }
        }
    }

    /**
     * Credits a real player with a goal they actually struck and fires the
     * goal/hat-trick triggers with their running total for this match.
     */
    public void notePlayerGoal(ServerPlayer scorer) {
        if (scorer == null) {
            return;
        }
        int total = playerGoals.merge(scorer.getUUID(), 1, Integer::sum);
        ModCriteriaTriggers.PLAYER_GOAL.trigger(scorer, total);
        setDirty();
    }

    /**
     * Full-time ceremony. On the first finished tick every outfield bot and
     * keeper starts its farewell wave while the referee blows the closing
     * whistle (handled in RefereeGoal); once the scene timer runs out the
     * whole squad, including the referee, is removed together.
     */
    private void tickCeremony(MinecraftServer server) {
        if (!ceremonyStarted) {
            ceremonyStarted = true;
            startFarewellWave(server.overworld());
        }
        if (ceremonyTicks > 0) {
            ceremonyTicks--;
            if (ceremonyTicks == 0) {
                clearNpcs(server.overworld());
            }
            setDirty();
        }
    }

    private void startFarewellWave(ServerLevel level) {
        if (fieldCenter == null) {
            return;
        }
        AABB searchArea = new AABB(fieldCenter).inflate(96.0D);
        for (SoccerNpcEntity npc : level.getEntitiesOfClass(SoccerNpcEntity.class, searchArea)) {
            if (!npc.isReferee()) {
                npc.getNavigation().stop();
                npc.startWave();
            }
        }
    }

    /** True while the post-match wave/despawn scene is still playing. */
    public boolean isCeremonyActive() {
        return state == MatchState.FINISHED && ceremonyTicks > 0;
    }

    public void updateSettings(MatchSettings incoming) {
        // Never restructure a match that is already in progress.
        if (state == MatchState.RUNNING) {
            return;
        }
        settings.setSport(incoming.getSport());
        settings.setFieldSize(incoming.getFieldSize());
        settings.setTeamSize(incoming.getTeamSize());
        settings.setDifficulty(incoming.getDifficulty());
        settings.setHalfMinutes(incoming.getHalfMinutes());
        settings.setDrawRule(incoming.getDrawRule());
        settings.setHomeFormation(incoming.getHomeFormation());
        settings.setAwayFormation(incoming.getAwayFormation());
        settings.setHomeClub(incoming.getHomeClub());
        settings.setAwayClub(incoming.getAwayClub());
        settings.setHomeCustomName(incoming.getHomeCustomName());
        settings.setAwayCustomName(incoming.getAwayCustomName());
        settings.setFunMode(incoming.getFunMode());
        settings.setFatigue(incoming.isFatigue());
        if (state == MatchState.IDLE) {
            remainingSeconds = settings.getHalfLengthSeconds();
        }
        setDirty();
    }

    public void onFieldBuilt(BlockPos center, FieldSize size) {
        this.fieldCenter = center.immutable();
        this.fieldBuilt = true;
        settings.setFieldSize(size);
        // A freshly built stadium means fresh teams: stale assignments from a
        // previous match would otherwise silently eat bot slots (the root
        // cause of the "one bot missing in 1v1" report).
        teams.clear();
        keepers.clear();
        passRequestUntil.clear();
        redChaser = null;
        blueChaser = null;
        setDirty();
    }

    public void setTeam(UUID playerId, Team team) {
        teams.put(playerId, team);
        if (team == Team.SPECTATOR) {
            keepers.remove(playerId);
        }
        setDirty();
    }

    public Team getTeam(UUID playerId) {
        return teams.getOrDefault(playerId, Team.SPECTATOR);
    }

    public void setKeeper(UUID playerId, boolean keeper) {
        if (keeper) {
            keepers.add(playerId);
        } else {
            keepers.remove(playerId);
        }
        setDirty();
    }

    public boolean isKeeper(UUID playerId) {
        return keepers.contains(playerId);
    }

    /**
     * Records a goal. Goals only count while a match is running; free play outside
     * a match still resets the ball but does not change the scoreboard.
     */
    public boolean onGoal(Team scoring) {
        if (state != MatchState.RUNNING || scoring == Team.SPECTATOR) {
            return false;
        }
        if (scoring == Team.RED) {
            redScore++;
        } else {
            blueScore++;
        }
        endFreeKick();
        setDirty();
        return true;
    }

    public void start(int seconds) {
        if (!fieldBuilt) {
            return;
        }
        // Starting from IDLE/FINISHED begins a fresh match, so the previous
        // game's score, cards and fouls must not leak into the new one. A
        // start pressed while paused keeps the running match untouched.
        boolean freshMatch = state != MatchState.RUNNING && state != MatchState.PAUSED;
        if (freshMatch) {
            redScore = 0;
            blueScore = 0;
            foulCounts.clear();
            yellowCardCounts.clear();
            playerGoals.clear();
            passRequestUntil.clear();
            redChaser = null;
            blueChaser = null;
            endFreeKick();
            ceremonyStarted = false;
            ceremonyTicks = 0;
        }
        int configured = seconds > 0 ? seconds : settings.getHalfLengthSeconds();
        remainingSeconds = Math.max(MIN_DURATION, Math.min(MAX_DURATION, configured));
        // Each half lasts this long; the same length restarts after the break.
        configuredHalfSeconds = remainingSeconds;
        tickCounter = 0;
        extraTimeUsed = false;
        half = 1;
        sidesSwapped = false;
        halfTimeTicks = 0;
        state = MatchState.RUNNING;
        setDirty();
    }

    public void pause() {
        if (state == MatchState.RUNNING) {
            state = MatchState.PAUSED;
            setDirty();
        }
    }

    public void resume() {
        if (state == MatchState.PAUSED) {
            state = MatchState.RUNNING;
            setDirty();
        }
    }

    public void reset() {
        state = MatchState.IDLE;
        redScore = 0;
        blueScore = 0;
        remainingSeconds = settings.getHalfLengthSeconds();
        tickCounter = 0;
        extraTimeUsed = false;
        half = 1;
        sidesSwapped = false;
        halfTimeTicks = 0;
        configuredHalfSeconds = 0;
        ceremonyStarted = false;
        ceremonyTicks = 0;
        partyEvent = PartyEvent.NONE;
        partyTicks = 0;
        redChaser = null;
        blueChaser = null;
        passRequestUntil.clear();
        endFreeKick();
        foulCounts.clear();
        yellowCardCounts.clear();
        playerGoals.clear();
        setDirty();
    }

    /**
     * Goal-line centre for the given team's own goal, derived from the stored
     * field geometry so NPCs and other logic never hard-code coordinates.
     */
    public BlockPos getGoalPosition(Team team) {
        if (fieldCenter == null) {
            return null;
        }
        int halfL = settings.getFieldSize().getLength() / 2;
        int offsetZ = team == Team.RED ? -halfL : halfL;
        // After the half-time swap each side defends the physical goal it
        // attacked in the first half.
        if (sidesSwapped) {
            offsetZ = -offsetZ;
        }
        // Basketball hoops are elevated structures; football goals sit on the ground.
        int lift = settings.getSport() == BallType.BASKETBALL ? 3 : 0;
        return new BlockPos(fieldCenter.getX(), fieldCenter.getY() + lift, fieldCenter.getZ() + offsetZ);
    }

    /**
     * Fills both teams up to the configured team size with AI bots. Real,
     * online players already on a team reduce the number of bots spawned.
     * One referee bot is always added. The returned summary lets the caller
     * prove every requested slot was actually spawned.
     */
    public PopulateResult populateNpcs(ServerLevel level, MinecraftServer server) {
        clearNpcs(level);
        if (fieldCenter == null) {
            return new PopulateResult(0, 0, 0, 0, false);
        }
        ensureKickoffBall(level);
        int target = settings.getTeamSize();
        int redReal = countRealPlayers(server, Team.RED);
        int blueReal = countRealPlayers(server, Team.BLUE);
        int redBots = spawnTeamNpcs(level, Team.RED, Math.max(0, target - redReal));
        int blueBots = spawnTeamNpcs(level, Team.BLUE, Math.max(0, target - blueReal));
        boolean referee = spawnReferee(level);
        setDirty();
        return new PopulateResult(redBots, blueBots, redReal, blueReal, referee);
    }

    /** Spawns the configured ball type at the centre spot when the pitch has none. */
    private void ensureKickoffBall(ServerLevel level) {
        AABB searchArea = new AABB(fieldCenter).inflate(64.0D);
        if (!level.getEntitiesOfClass(com.mcsport.entity.SoccerBallEntity.class, searchArea).isEmpty()) {
            return;
        }
        Vec3 kickoff = Vec3.atBottomCenterOf(fieldCenter).add(0.0D, 1.0D, 0.0D);
        com.mcsport.entity.SoccerBallEntity.spawn(level, kickoff, settings.getSport());
    }

    /** Removes every bot tracked by this manager from the world. */
    public void clearNpcs(ServerLevel level) {
        for (UUID id : npcIds) {
            if (level.getEntity(id) instanceof SoccerNpcEntity npc) {
                npc.discard();
            }
        }
        npcIds.clear();
        redChaser = null;
        blueChaser = null;
    }

    private int countRealPlayers(MinecraftServer server, Team team) {
        int count = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (getTeam(player.getUUID()) == team) {
                count++;
            }
        }
        return count;
    }

    private int spawnTeamNpcs(ServerLevel level, Team team, int count) {
        if (count <= 0) {
            return 0;
        }
        BlockPos goal = getGoalPosition(team);
        if (goal == null) {
            return 0;
        }
        Formation formation = team == Team.RED
                ? settings.getHomeFormation() : settings.getAwayFormation();
        Club club = team == Team.RED ? settings.getHomeClub() : settings.getAwayClub();
        // A one-slot side has no dedicated keeper: the lone bot must play the
        // whole pitch, otherwise nobody presses the ball at kick-off. When a
        // real player already took the keeper slot, bots fill outfield only.
        boolean humanKeeper = level.getServer().getPlayerList().getPlayers().stream()
                .anyMatch(p -> getTeam(p.getUUID()) == team && keepers.contains(p.getUUID()));
        boolean useBotKeeper = settings.getTeamSize() >= 2 && !humanKeeper;
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            boolean keeper = useBotKeeper && i == 0;
            int number = i + 1;
            Vec3 home = FormationLayout.homePosition(fieldCenter, settings.getFieldSize(),
                    formation, team, i, settings.getTeamSize(), keeper, sidesSwapped);
            SoccerNpcEntity npc = ModEntities.SOCCER_NPC.get().create(level);
            if (npc == null) {
                continue;
            }
            Vec3 safe = safeSpawnPosition(level, home);
            npc.moveTo(safe.x, safe.y, safe.z, 0.0F, 0.0F);
            npc.setSportTeam(team);
            npc.setKeeper(keeper);
            npc.setSquadIndex(i);
            npc.setPersistenceRequired();
            npc.setCustomName(Component.literal(number + " "
                    + club.playerName(i, isEnglishLocale())));
            npc.setCustomNameVisible(true);
            equipKit(npc, club, keeper);
            // addFreshEntity can reject a spawn (overlap, world border); never
            // track a ghost UUID and keep filling the remaining slots.
            if (level.addFreshEntity(npc)) {
                npcIds.add(npc.getUUID());
                spawned++;
            }
        }
        return spawned;
    }

    /** Spawns the single referee beside the centre spot. */
    private boolean spawnReferee(ServerLevel level) {
        if (fieldCenter == null) {
            return false;
        }
        SoccerNpcEntity npc = ModEntities.SOCCER_NPC.get().create(level);
        if (npc == null) {
            return false;
        }
        Vec3 preferred = Vec3.atBottomCenterOf(fieldCenter).add(3.5D, 0.0D, 0.0D);
        Vec3 safe = safeSpawnPosition(level, preferred);
        npc.moveTo(safe.x, safe.y, safe.z, 0.0F, 0.0F);
        npc.setReferee(true);
        npc.setPersistenceRequired();
        // Translatable so every client renders the referee name in its own
        // language regardless of the server-side squad locale.
        npc.setCustomName(Component.translatable("entity.mcsport.referee"));
        npc.setCustomNameVisible(true);
        equipRefereeKit(npc);
        if (!level.addFreshEntity(npc)) {
            return false;
        }
        npcIds.add(npc.getUUID());
        return true;
    }

    /**
     * Returns the preferred position when it is clear, otherwise the first
     * collision-free point on expanding rings around it, so a bot never gets
     * spawned inside a goal post, hoop pole or another entity.
     */
    private Vec3 safeSpawnPosition(ServerLevel level, Vec3 preferred) {
        if (level.noCollision(npcBox(preferred))) {
            return preferred;
        }
        for (int radius = 1; radius <= 4; radius++) {
            for (int angle = 0; angle < 8; angle++) {
                double radians = Math.PI * 2.0D * angle / 8.0D;
                Vec3 candidate = preferred.add(Math.cos(radians) * radius, 0.0D, Math.sin(radians) * radius);
                if (level.noCollision(npcBox(candidate))) {
                    return candidate;
                }
            }
        }
        return preferred;
    }

    private AABB npcBox(Vec3 position) {
        return new AABB(position.x - 0.3D, position.y, position.z - 0.3D,
                position.x + 0.3D, position.y + 1.8D, position.z + 0.3D);
    }

    private void equipKit(SoccerNpcEntity npc, Club club, boolean keeper) {
        int color = keeper ? KIT_KEEPER : club.getKitColor();
        ItemStack chest = new ItemStack(Items.LEATHER_CHESTPLATE);
        ((DyeableLeatherItem) chest.getItem()).setColor(chest, color);
        ItemStack legs = new ItemStack(Items.LEATHER_LEGGINGS);
        ((DyeableLeatherItem) legs.getItem()).setColor(legs, 0x1E1E24);
        ItemStack boots = new ItemStack(Items.LEATHER_BOOTS);
        ((DyeableLeatherItem) boots.getItem()).setColor(boots, 0x202020);
        npc.setItemSlot(EquipmentSlot.CHEST, chest);
        npc.setItemSlot(EquipmentSlot.LEGS, legs);
        npc.setItemSlot(EquipmentSlot.FEET, boots);
        npc.setDropChance(EquipmentSlot.CHEST, 0.0F);
        npc.setDropChance(EquipmentSlot.LEGS, 0.0F);
        npc.setDropChance(EquipmentSlot.FEET, 0.0F);
    }

    private void equipRefereeKit(SoccerNpcEntity npc) {
        ItemStack chest = new ItemStack(Items.LEATHER_CHESTPLATE);
        ((DyeableLeatherItem) chest.getItem()).setColor(chest, KIT_REFEREE);
        ItemStack legs = new ItemStack(Items.LEATHER_LEGGINGS);
        ((DyeableLeatherItem) legs.getItem()).setColor(legs, KIT_DARK);
        ItemStack boots = new ItemStack(Items.LEATHER_BOOTS);
        ((DyeableLeatherItem) boots.getItem()).setColor(boots, KIT_DARK);
        npc.setItemSlot(EquipmentSlot.CHEST, chest);
        npc.setItemSlot(EquipmentSlot.LEGS, legs);
        npc.setItemSlot(EquipmentSlot.FEET, boots);
        npc.setDropChance(EquipmentSlot.CHEST, 0.0F);
        npc.setDropChance(EquipmentSlot.LEGS, 0.0F);
        npc.setDropChance(EquipmentSlot.FEET, 0.0F);
    }

    // ------------------------------------------------------------------
    // Fouls and player slide tackles
    // ------------------------------------------------------------------

    /** Resolves which match team a living participant belongs to. */
    public Team teamOf(LivingEntity entity) {
        if (entity instanceof SoccerNpcEntity npc) {
            return npc.isReferee() ? Team.SPECTATOR : npc.getSportTeam();
        }
        if (entity instanceof Player player) {
            return getTeam(player.getUUID());
        }
        return Team.SPECTATOR;
    }

    /**
     * Records a foul in favour of {@code favoured} (the tackled side), opens
     * the free-kick phase and decides the disciplinary card. Gap throttling
     * prevents several contacts in the same slide from turning into a spam of
     * whistle decisions. The ball is parked at the foul spot immediately so
     * the set piece is well defined even before the referee reaches it.
     */
    public void reportFoul(ServerLevel level, Team favoured, LivingEntity tackler, Vec3 pos) {
        if (favoured == Team.SPECTATOR || tackler == null) {
            return;
        }
        int now = level.getServer().getTickCount();
        if (now - lastFoulTick < FOUL_MIN_GAP_TICKS) {
            return;
        }
        lastFoulTick = now;
        lastFoulTeam = favoured;
        lastFoulPos = pos;
        lastFoulTackler = tackler.getUUID();
        lastFoulCard = registerDiscipline(tackler.getUUID());
        if (tackler instanceof ServerPlayer player && lastFoulCard != SoccerNpcEntity.CARD_NONE) {
            // Yellow tier satisfies value 1, a straight red satisfies both.
            ModCriteriaTriggers.CARD_SHOWN.trigger(player,
                    lastFoulCard == SoccerNpcEntity.CARD_RED ? 2 : 1);
        }
        beginFreeKick(level, favoured, pos);
        setDirty();
    }

    /**
     * Updates the foul/yellow ledgers and returns the card the referee must
     * show for this offence: a yellow on the 2nd and 3rd foul, a straight red
     * on the 4th, nothing for the first offence.
     */
    private byte registerDiscipline(UUID tackler) {
        int count = foulCounts.getOrDefault(tackler, 0) + 1;
        foulCounts.put(tackler, count);
        if (count >= 4) {
            return SoccerNpcEntity.CARD_RED;
        }
        if (count >= 2) {
            yellowCardCounts.merge(tackler, 1, Integer::sum);
            return SoccerNpcEntity.CARD_YELLOW;
        }
        return SoccerNpcEntity.CARD_NONE;
    }

    /** Opens the free-kick phase and parks the ball on the foul spot. */
    private void beginFreeKick(ServerLevel level, Team takerTeam, Vec3 pos) {
        Vec3 spot = new Vec3(pos.x, pos.y, pos.z);
        freeKickActive = true;
        freeKickTeam = takerTeam;
        freeKickPos = spot;
        freeKickEndTick = level.getServer().getTickCount() + FREE_KICK_DURATION_TICKS;
        SoccerBallEntity ball = findNearestBall(level);
        if (ball != null && !ball.isCarried()) {
            ball.placeAt(spot.add(0.0D, BALL_REST_LIFT, 0.0D));
        }
    }

    /** Closes the set piece; normal open play resumes. */
    public void endFreeKick() {
        freeKickActive = false;
    }

    public boolean isFreeKickActive() {
        return freeKickActive;
    }

    public Team getFreeKickTeam() {
        return freeKickTeam;
    }

    public Vec3 getFreeKickPos() {
        return freeKickPos;
    }

    /**
     * Position of wall slot {@code index} for a wall of {@code wallCount}
     * defenders: 4.2 blocks between the ball and the defended goal, spread
     * along the line perpendicular to the shot direction.
     */
    public Vec3 getWallPosition(int index, int wallCount) {
        Vec3 shotDir = freeKickShotDirection();
        Vec3 perpendicular = new Vec3(-shotDir.z, 0.0D, shotDir.x);
        double lateral = (index - (wallCount - 1) / 2.0D) * FREE_KICK_WALL_SPACING;
        Vec3 point = freeKickPos.add(shotDir.scale(FREE_KICK_WALL_DISTANCE))
                .add(perpendicular.scale(lateral));
        return new Vec3(point.x, freeKickPos.y, point.z);
    }

    /** Where the taker stands: one block behind the ball, away from the goal. */
    public Vec3 getFreeKickTakerPos() {
        Vec3 shotDir = freeKickShotDirection();
        Vec3 point = freeKickPos.subtract(shotDir.scale(1.15D));
        return new Vec3(point.x, freeKickPos.y, point.z);
    }

    private Vec3 freeKickShotDirection() {
        BlockPos defendedGoal = getGoalPosition(freeKickTeam.opponent());
        if (defendedGoal == null) {
            return new Vec3(0.0D, 0.0D, freeKickTeam == Team.RED ? 1.0D : -1.0D);
        }
        Vec3 direction = new Vec3(defendedGoal.getX() + 0.5D - freeKickPos.x, 0.0D,
                defendedGoal.getZ() + 0.5D - freeKickPos.z);
        if (direction.lengthSqr() < 1.0E-6D) {
            return new Vec3(0.0D, 0.0D, freeKickTeam == Team.RED ? 1.0D : -1.0D);
        }
        return direction.normalize();
    }

    /**
     * Checks an active slide for illegal contact: an opposing participant
     * inside the lunge box while the ball is outside clean tackling reach.
     * The ball is slowed for the resulting free kick.
     */
    public void checkTackleFoul(ServerLevel level, LivingEntity tackler, SoccerBallEntity ball) {
        Team tacklerTeam = teamOf(tackler);
        if (tacklerTeam == Team.SPECTATOR) {
            return;
        }
        // Tight contact box: only an actual body collision while the ball is
        // out of reach counts as a foul, not nearby presence.
        AABB area = tackler.getBoundingBox().inflate(0.8D);
        for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, area)) {
            if (other == tackler || !other.isAlive()) {
                continue;
            }
            Team otherTeam = teamOf(other);
            if (otherTeam == Team.SPECTATOR || otherTeam == tacklerTeam) {
                continue;
            }
            if (ball != null && tackler.distanceToSqr(ball) <= CLEAN_TACKLE_DIST_SQR) {
                continue;
            }
            reportFoul(level, otherTeam, tackler, tackler.position());
            return;
        }
    }

    /**
     * Applies a real player's slide tackle: a forward lunge with the same
     * cooldown/contact rules as the bot version. Returns false while the
     * cooldown is active so the client packet gets no effect.
     */
    public boolean tryPlayerSlide(ServerLevel level, ServerPlayer player) {
        // Slides are a match action only: outside a running match the C key
        // must not shove the player around or trigger foul logic.
        if (fieldCenter == null || state != MatchState.RUNNING) {
            return false;
        }
        int now = player.server.getTickCount();
        if (playerSlideCooldownUntil.getOrDefault(player.getUUID(), Integer.MIN_VALUE / 2) > now) {
            return false;
        }
        Vec3 flat = new Vec3(player.getLookAngle().x, 0.0D, player.getLookAngle().z);
        if (flat.lengthSqr() < 1.0E-6D) {
            return false;
        }
        flat = flat.normalize();
        player.setDeltaMovement(flat.x * 1.35D, -0.1D, flat.z * 1.35D);
        player.hasImpulse = true;
        playerSlideCooldownUntil.put(player.getUUID(), now + PLAYER_SLIDE_COOLDOWN_TICKS);
        playerSlideActiveUntil.put(player.getUUID(),
                now + SoccerNpcEntity.TACKLE_DURATION_TICKS);
        level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.1D, player.getZ(),
                8, 0.3D, 0.1D, 0.3D, 0.02D);
        return true;
    }

    private void tickPlayerSlides(MinecraftServer server) {
        if (playerSlideActiveUntil.isEmpty()) {
            return;
        }
        ServerLevel level = server.overworld();
        int now = server.getTickCount();
        SoccerBallEntity ball = findNearestBall(level);
        playerSlideActiveUntil.entrySet().removeIf(entry -> {
            if (entry.getValue() <= now) {
                return true;
            }
            // PlayerList#getPlayer(UUID) already returns ServerPlayer (or null);
            // an instanceof pattern here is rejected by javac as a redundant
            // subtype test, so a plain null check is the correct form.
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                checkTackleFoul(level, player, ball);
            }
            return false;
        });
    }

    private SoccerBallEntity findNearestBall(ServerLevel level) {
        if (fieldCenter == null) {
            return null;
        }
        AABB searchArea = new AABB(fieldCenter).inflate(64.0D);
        SoccerBallEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (SoccerBallEntity candidate : level.getEntitiesOfClass(SoccerBallEntity.class, searchArea)) {
            double distance = candidate.distanceToSqr(Vec3.atCenterOf(fieldCenter));
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = candidate;
            }
        }
        return nearest;
    }

    // ------------------------------------------------------------------
    // Stable chaser assignment
    // ------------------------------------------------------------------

    /**
     * The single outfield bot of {@code team} that currently presses the ball.
     * The assignment is refreshed on a throttle with distance hysteresis, so
     * nearby bots never flip the role tick by tick.
     */
    public SoccerNpcEntity getChaser(ServerLevel level, Team team) {
        if (team != Team.RED && team != Team.BLUE) {
            return null;
        }
        refreshChasers(level);
        UUID id = team == Team.RED ? redChaser : blueChaser;
        if (id != null && level.getEntity(id) instanceof SoccerNpcEntity npc
                && npc.isAlive() && !npc.isRemoved() && !npc.isReferee()
                && !npc.isKeeper() && npc.getSportTeam() == team) {
            return npc;
        }
        return null;
    }

    private void refreshChasers(ServerLevel level) {
        if (fieldCenter == null) {
            redChaser = null;
            blueChaser = null;
            return;
        }
        int now = level.getServer().getTickCount();
        boolean fresh = now - chaserRefreshTick >= CHASER_REFRESH_INTERVAL;
        if (!fresh && chaserValid(level, redChaser, Team.RED)
                && chaserValid(level, blueChaser, Team.BLUE)) {
            return;
        }
        chaserRefreshTick = now;
        SoccerBallEntity ball = findNearestBall(level);
        redChaser = pickChaser(level, Team.RED, ball, redChaser);
        blueChaser = pickChaser(level, Team.BLUE, ball, blueChaser);
    }

    private boolean chaserValid(ServerLevel level, UUID id, Team team) {
        if (id == null || !(level.getEntity(id) instanceof SoccerNpcEntity npc)) {
            return false;
        }
        return npc.isAlive() && !npc.isRemoved() && !npc.isReferee()
                && !npc.isKeeper() && npc.getSportTeam() == team;
    }

    private UUID pickChaser(ServerLevel level, Team team, SoccerBallEntity ball, UUID current) {
        if (ball == null) {
            return null;
        }
        // While our own keeper holds the ball the outfield squad stays in
        // shape instead of bunching around the holder.
        if (ball.isCarried() && ball.getCarrier() instanceof LivingEntity carrier
                && teamOf(carrier) == team) {
            return null;
        }
        AABB area = new AABB(fieldCenter).inflate(64.0D);
        SoccerNpcEntity nearest = null;
        double nearestSqr = Double.MAX_VALUE;
        for (SoccerNpcEntity npc : level.getEntitiesOfClass(SoccerNpcEntity.class, area)) {
            if (npc.isReferee() || npc.isKeeper() || npc.getSportTeam() != team
                    || !npc.isAlive()) {
                continue;
            }
            double distanceSqr = npc.distanceToSqr(ball);
            if (distanceSqr < nearestSqr) {
                nearestSqr = distanceSqr;
                nearest = npc;
            }
        }
        if (nearest == null) {
            return null;
        }
        if (current != null && nearest.getUUID().equals(current)) {
            return current;
        }
        // Immediate takeover: a teammate already has the ball at their feet
        // while the assigned presser is far away. Without this override the
        // squad waits for the refresh window and leaves a reachable ball
        // untouched, which looked like bots ignoring the play.
        if (nearestSqr < 2.25D
                && (!(level.getEntity(current) instanceof SoccerNpcEntity holder)
                        || holder.distanceToSqr(ball) > 16.0D)) {
            return nearest.getUUID();
        }
        // Hysteresis: keep the current presser until a teammate gets clearly
        // closer, which removes the role-swap oscillation around contested balls.
        if (current != null && level.getEntity(current) instanceof SoccerNpcEntity holder
                && holder.isAlive() && !holder.isKeeper() && holder.getSportTeam() == team) {
            if (holder.distanceToSqr(ball) <= nearestSqr + CHASER_HYSTERESIS_SQR) {
                return current;
            }
        }
        return nearest.getUUID();
    }

    // ------------------------------------------------------------------
    // Locale handling for generated squad names
    // ------------------------------------------------------------------

    public String getLanguage() {
        return language;
    }

    public boolean isEnglishLocale() {
        return "en".equals(language);
    }

    /** True after the half-time swap; flips every derived goal/home position. */
    public boolean isSidesSwapped() {
        return sidesSwapped;
    }

    public int getHalf() {
        return half;
    }

    /** Sets (or clears, when blank) the custom display name of one side. */
    public void renameClub(boolean home, String name) {
        if (home) {
            settings.setHomeCustomName(name);
        } else {
            settings.setAwayCustomName(name);
        }
        setDirty();
    }

    // ------------------------------------------------------------------
    // Fun-mode modifiers (low gravity, big ball, super shots, party events)
    // ------------------------------------------------------------------

    /** Gravity multiplier applied to the ball while a match runs. */
    public double getGravityFactor() {
        if (settings.getFunMode() == FunMode.LOW_GRAVITY
                || (settings.getFunMode() == FunMode.PARTY
                && partyEvent == PartyEvent.LOW_GRAVITY)) {
            return FUN_GRAVITY_FACTOR;
        }
        return 1.0D;
    }

    /** Speed multiplier for every ball kick while a match runs. */
    public double getShotFactor() {
        if (settings.getFunMode() == FunMode.SUPER_SHOT
                || (settings.getFunMode() == FunMode.PARTY
                && partyEvent == PartyEvent.SUPER_SHOT)) {
            return FUN_SHOT_FACTOR;
        }
        return 1.0D;
    }

    /** Movement multiplier bots inherit during a party speed burst. */
    public double getBotSpeedFactor() {
        if (settings.getFunMode() == FunMode.PARTY && partyEvent == PartyEvent.SPEED_BURST) {
            return FUN_SPEED_FACTOR;
        }
        return 1.0D;
    }

    /** True when the oversized ball modifier is selected. */
    public boolean isBigBall() {
        return settings.getFunMode() == FunMode.BIG_BALL;
    }

    private void tickPartyEvents(MinecraftServer server) {
        if (settings.getFunMode() != FunMode.PARTY) {
            if (partyEvent != PartyEvent.NONE) {
                partyEvent = PartyEvent.NONE;
            }
            return;
        }
        partyTicks++;
        if (partyTicks < PARTY_EVENT_INTERVAL) {
            return;
        }
        if (partyEvent == PartyEvent.NONE) {
            PartyEvent[] pool = {PartyEvent.LOW_GRAVITY, PartyEvent.SUPER_SHOT, PartyEvent.SPEED_BURST};
            partyEvent = pool[server.getTickCount() % pool.length];
            partyTicks = PARTY_EVENT_INTERVAL;
            announcePartyEvent(server);
        } else if (partyTicks >= PARTY_EVENT_INTERVAL + PARTY_EVENT_DURATION) {
            partyEvent = PartyEvent.NONE;
            partyTicks = 0;
            setDirty();
        }
    }

    private void announcePartyEvent(MinecraftServer server) {
        String key = switch (partyEvent) {
            case LOW_GRAVITY -> "message.mcsport.party_gravity";
            case SUPER_SHOT -> "message.mcsport.party_super_shot";
            case SPEED_BURST -> "message.mcsport.party_speed";
            default -> null;
        };
        if (key != null) {
            server.getPlayerList().broadcastSystemMessage(
                    Component.translatable(key).withStyle(ChatFormatting.LIGHT_PURPLE), false);
        }
        setDirty();
    }

    /**
     * Records the preferred UI language from a client-synced locale code
     * ("tr_tr", "en_us", ...). Anything starting with "tr" picks Turkish,
     * every other non-blank code picks English. The code arrives over the
     * mod channel because the vanilla listener locale field is private in
     * the 1.20.1 Mojang mappings.
     */
    public void noteLanguage(String locale) {
        if (locale == null || locale.isBlank()) {
            return;
        }
        language = locale.toLowerCase(Locale.ROOT).startsWith("tr") ? "tr" : "en";
        setDirty();
    }

    // ------------------------------------------------------------------
    // Call-for-ball (pass request)
    // ------------------------------------------------------------------

    /** Records a real player's call-for-ball for a short window. */
    public void requestPass(ServerPlayer player) {
        if (getTeam(player.getUUID()) == Team.SPECTATOR) {
            return;
        }
        passRequestUntil.put(player.getUUID(),
                player.server.getTickCount() + PASS_REQUEST_DURATION_TICKS);
        player.displayClientMessage(
                Component.translatable("message.mcsport.pass_requested"), true);
    }

    /**
     * Returns and consumes the oldest active requester on {@code team}, or
     * null. Expired entries are pruned during the same sweep.
     */
    public ServerPlayer takePassRequest(ServerLevel level, Team team) {
        int now = level.getServer().getTickCount();
        var iterator = passRequestUntil.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            if (entry.getValue() < now) {
                iterator.remove();
                continue;
            }
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player != null && player.isAlive() && getTeam(player.getUUID()) == team) {
                iterator.remove();
                return player;
            }
        }
        return null;
    }

    /** Keeps a request alive for a few more ticks when the first lane was blocked. */
    public void rearmPassRequest(ServerPlayer player) {
        passRequestUntil.put(player.getUUID(),
                player.server.getTickCount() + PASS_REQUEST_REARM_TICKS);
    }

    public int getLastFoulTick() {
        return lastFoulTick;
    }

    public Team getLastFoulTeam() {
        return lastFoulTeam;
    }

    public Vec3 getLastFoulPos() {
        return lastFoulPos;
    }

    public UUID getLastFoulTackler() {
        return lastFoulTackler;
    }

    /** Card level ({@link SoccerNpcEntity#CARD_NONE}/YELLOW/RED) for the latest foul. */
    public byte getLastFoulCard() {
        return lastFoulCard;
    }

    public void broadcast(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncToPlayer(player);
        }
    }

    public void syncToPlayer(ServerPlayer player) {
        Team team = getTeam(player.getUUID());
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new MatchStateS2C((byte) state.ordinal(), redScore, blueScore,
                        remainingSeconds, (byte) team.ordinal(),
                        keepers.contains(player.getUUID()), fieldBuilt, settings.copy()));
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putString("State", state.name());
        tag.putString("Language", language);
        tag.putInt("RedScore", redScore);
        tag.putInt("BlueScore", blueScore);
        tag.putInt("Remaining", remainingSeconds);
        tag.putBoolean("FieldBuilt", fieldBuilt);
        tag.putBoolean("ExtraTimeUsed", extraTimeUsed);
        tag.putInt("Half", half);
        tag.putBoolean("SidesSwapped", sidesSwapped);
        tag.putInt("HalfTimeTicks", halfTimeTicks);
        tag.putInt("ConfiguredHalfSeconds", configuredHalfSeconds);
        tag.putString("PartyEvent", partyEvent.name());
        tag.putInt("PartyTicks", partyTicks);
        if (fieldCenter != null) {
            tag.putLong("FieldCenter", fieldCenter.asLong());
        }
        tag.put("Settings", saveSettings());
        ListTag list = new ListTag();
        teams.forEach((uuid, team) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", uuid);
            entry.putString("Team", team.name());
            entry.putBoolean("Keeper", keepers.contains(uuid));
            list.add(entry);
        });
        tag.put("Teams", list);
        return tag;
    }

    private CompoundTag saveSettings() {
        CompoundTag value = new CompoundTag();
        value.putByte("Sport", (byte) settings.getSport().ordinal());
        value.putByte("FieldSize", (byte) settings.getFieldSize().ordinal());
        value.putByte("TeamSize", (byte) settings.getTeamSize());
        value.putByte("Difficulty", (byte) settings.getDifficulty().ordinal());
        value.putByte("HalfMinutes", (byte) settings.getHalfMinutes());
        value.putByte("DrawRule", (byte) settings.getDrawRule().ordinal());
        value.putByte("HomeFormation", (byte) settings.getHomeFormation().ordinal());
        value.putByte("AwayFormation", (byte) settings.getAwayFormation().ordinal());
        value.putByte("HomeClub", (byte) settings.getHomeClub().ordinal());
        value.putByte("AwayClub", (byte) settings.getAwayClub().ordinal());
        value.putString("HomeCustomName", settings.getHomeCustomName());
        value.putString("AwayCustomName", settings.getAwayCustomName());
        value.putByte("FunMode", (byte) settings.getFunMode().ordinal());
        value.putBoolean("Fatigue", settings.isFatigue());
        return value;
    }

    private static MatchManager load(CompoundTag tag) {
        MatchManager manager = new MatchManager();
        manager.state = parseEnum(tag.getString("State"), MatchState.class, MatchState.IDLE);
        if (tag.contains("Language")) {
            String saved = tag.getString("Language");
            manager.language = "en".equals(saved) ? "en" : "tr";
        }
        manager.redScore = tag.getInt("RedScore");
        manager.blueScore = tag.getInt("BlueScore");
        manager.remainingSeconds = tag.contains("Remaining")
                ? tag.getInt("Remaining") : manager.settings.getHalfLengthSeconds();
        manager.fieldBuilt = tag.getBoolean("FieldBuilt");
        manager.extraTimeUsed = tag.getBoolean("ExtraTimeUsed");
        // Older saves predate the two-half system; defaults are first half,
        // unswapped sides and no running intermission.
        manager.half = tag.contains("Half") ? Math.max(1, Math.min(2, tag.getInt("Half"))) : 1;
        manager.sidesSwapped = tag.getBoolean("SidesSwapped");
        manager.halfTimeTicks = Math.max(0, tag.getInt("HalfTimeTicks"));
        manager.configuredHalfSeconds = Math.max(0, tag.getInt("ConfiguredHalfSeconds"));
        if (tag.contains("PartyEvent")) {
            manager.partyEvent = parseEnum(tag.getString("PartyEvent"),
                    PartyEvent.class, PartyEvent.NONE);
            manager.partyTicks = Math.max(0, tag.getInt("PartyTicks"));
        }
        if (tag.contains("FieldCenter")) {
            manager.fieldCenter = BlockPos.of(tag.getLong("FieldCenter"));
        }
        if (tag.contains("Settings")) {
            readSettings(manager.settings, tag.getCompound("Settings"));
        }
        ListTag list = tag.getList("Teams", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            UUID id = entry.getUUID("Id");
            manager.teams.put(id, parseEnum(entry.getString("Team"), Team.class, Team.SPECTATOR));
            if (entry.getBoolean("Keeper")) {
                manager.keepers.add(id);
            }
        }
        return manager;
    }

    private static void readSettings(MatchSettings target, CompoundTag value) {
        target.setSport(BallType.byOrdinal(value.getByte("Sport")));
        target.setFieldSize(FieldSize.byOrdinal(value.getByte("FieldSize")));
        target.setTeamSize(value.getByte("TeamSize"));
        target.setDifficulty(MatchDifficulty.byOrdinal(value.getByte("Difficulty")));
        target.setHalfMinutes(value.getByte("HalfMinutes"));
        target.setDrawRule(DrawRule.byOrdinal(value.getByte("DrawRule")));
        target.setHomeFormation(Formation.byOrdinal(value.getByte("HomeFormation")));
        target.setAwayFormation(Formation.byOrdinal(value.getByte("AwayFormation")));
        // Older saves predate club selection; fall back to the classic red/blue.
        if (value.contains("HomeClub")) {
            target.setHomeClub(Club.byOrdinal(value.getByte("HomeClub")));
        }
        if (value.contains("AwayClub")) {
            target.setAwayClub(Club.byOrdinal(value.getByte("AwayClub")));
        }
        if (value.contains("HomeCustomName")) {
            target.setHomeCustomName(value.getString("HomeCustomName"));
        }
        if (value.contains("AwayCustomName")) {
            target.setAwayCustomName(value.getString("AwayCustomName"));
        }
        // Older saves predate the fun update; absence keeps OFF / no fatigue.
        if (value.contains("FunMode")) {
            target.setFunMode(FunMode.byOrdinal(value.getByte("FunMode")));
        }
        target.setFatigue(value.getBoolean("Fatigue"));
    }

    private static <E extends Enum<E>> E parseEnum(String name, Class<E> type, E fallback) {
        if (name == null || name.isEmpty()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    public MatchState getState() {
        return state;
    }

    public int getRedScore() {
        return redScore;
    }

    public int getBlueScore() {
        return blueScore;
    }

    public int getRemainingSeconds() {
        return remainingSeconds;
    }

    public int getConfiguredDuration() {
        return settings.getHalfLengthSeconds();
    }

    public MatchSettings getSettings() {
        return settings;
    }

    public boolean isFieldBuilt() {
        return fieldBuilt;
    }

    public BlockPos getFieldCenter() {
        return fieldCenter;
    }
}
