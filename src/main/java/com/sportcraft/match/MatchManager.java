package com.sportcraft.match;

import com.sportcraft.entity.SoccerBallEntity;
import com.sportcraft.entity.SoccerNpcEntity;
import com.sportcraft.network.ModNetwork;
import com.sportcraft.network.network_packet.MatchStateS2C;
import com.sportcraft.registry.ModEntities;
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

    private static final String DATA_NAME = "sportcraft_match";
    private static final int MIN_DURATION = 60;
    private static final int MAX_DURATION = 3600;

    private MatchState state = MatchState.IDLE;
    private int redScore;
    private int blueScore;
    private int remainingSeconds;
    private final MatchSettings settings = new MatchSettings();
    private final Map<UUID, Team> teams = new HashMap<>();
    private final Set<UUID> keepers = new HashSet<>();
    private boolean fieldBuilt;
    private BlockPos fieldCenter;
    private boolean extraTimeUsed;
    private int tickCounter;
    private final Set<UUID> npcIds = new HashSet<>();

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
    // Real-player slide tackles: cooldown gate plus the active contact window.
    private final Map<UUID, Integer> playerSlideCooldownUntil = new HashMap<>();
    private final Map<UUID, Integer> playerSlideActiveUntil = new HashMap<>();

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
        if (state != MatchState.RUNNING) {
            return;
        }
        if (++tickCounter >= 20) {
            tickCounter = 0;
            if (remainingSeconds > 0) {
                remainingSeconds--;
                if (remainingSeconds == 0) {
                    handleClockEnd();
                }
                setDirty();
                broadcast(server);
            }
        }
    }

    private void handleClockEnd() {
        if (redScore == blueScore && settings.getDrawRule() == DrawRule.EXTRA_TIME && !extraTimeUsed) {
            extraTimeUsed = true;
            remainingSeconds = MatchSettings.EXTRA_TIME_SECONDS;
        } else {
            state = MatchState.FINISHED;
            // Begin the farewell scene: wave first, then the squad despawns.
            ceremonyStarted = false;
            ceremonyTicks = SoccerNpcEntity.WAVE_DURATION_TICKS + 35;
            setDirty();
        }
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
        int configured = seconds > 0 ? seconds : settings.getHalfLengthSeconds();
        remainingSeconds = Math.max(MIN_DURATION, Math.min(MAX_DURATION, configured));
        tickCounter = 0;
        extraTimeUsed = false;
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
        ceremonyStarted = false;
        ceremonyTicks = 0;
        redChaser = null;
        blueChaser = null;
        endFreeKick();
        foulCounts.clear();
        yellowCardCounts.clear();
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
        if (!level.getEntitiesOfClass(com.sportcraft.entity.SoccerBallEntity.class, searchArea).isEmpty()) {
            return;
        }
        Vec3 kickoff = Vec3.atBottomCenterOf(fieldCenter).add(0.0D, 1.0D, 0.0D);
        com.sportcraft.entity.SoccerBallEntity.spawn(level, kickoff, settings.getSport());
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
                    formation, team, i, settings.getTeamSize(), keeper);
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
            npc.setCustomName(Component.literal(number + " " + club.playerName(i)));
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
        npc.setCustomName(Component.literal("Hakem"));
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
        if (fieldCenter == null) {
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
        tag.putInt("RedScore", redScore);
        tag.putInt("BlueScore", blueScore);
        tag.putInt("Remaining", remainingSeconds);
        tag.putBoolean("FieldBuilt", fieldBuilt);
        tag.putBoolean("ExtraTimeUsed", extraTimeUsed);
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
        return value;
    }

    private static MatchManager load(CompoundTag tag) {
        MatchManager manager = new MatchManager();
        manager.state = parseEnum(tag.getString("State"), MatchState.class, MatchState.IDLE);
        manager.redScore = tag.getInt("RedScore");
        manager.blueScore = tag.getInt("BlueScore");
        manager.remainingSeconds = tag.contains("Remaining")
                ? tag.getInt("Remaining") : manager.settings.getHalfLengthSeconds();
        manager.fieldBuilt = tag.getBoolean("FieldBuilt");
        manager.extraTimeUsed = tag.getBoolean("ExtraTimeUsed");
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
