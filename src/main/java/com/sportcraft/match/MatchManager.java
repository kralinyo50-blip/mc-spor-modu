package com.sportcraft.match;

import com.sportcraft.network.ModNetwork;
import com.sportcraft.network.network_packet.MatchStateS2C;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
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
        }
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
        if (state == MatchState.IDLE) {
            remainingSeconds = settings.getHalfLengthSeconds();
        }
        setDirty();
    }

    public void onFieldBuilt(BlockPos center, FieldSize size) {
        this.fieldCenter = center.immutable();
        this.fieldBuilt = true;
        settings.setFieldSize(size);
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
        setDirty();
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
