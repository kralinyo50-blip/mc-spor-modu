package com.mcsport.blockentity;

import com.mcsport.match.MatchManager;
import com.mcsport.match.MatchState;
import com.mcsport.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Server-driven scoreboard panel. Once per second it snapshots the match
 * manager and, only when the displayed content actually changes, pushes the
 * new strings to every client through the block entity update packet. The
 * client-side renderer draws the cached strings; no match logic runs here.
 */
public class ScoreboardBlockEntity extends BlockEntity {

    private static final int SYNC_INTERVAL_TICKS = 20;

    private String redName = "";
    private String blueName = "";
    private int redScore;
    private int blueScore;
    private String clock = "00:00";
    private String stateKey = "";
    private int tickCounter;

    public ScoreboardBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SCOREBOARD.get(), pos, state);
    }

    public void serverTick(Level rawLevel) {
        // The block only registers this ticker on the logical server.
        if (!(rawLevel instanceof ServerLevel level)) {
            return;
        }
        if (++tickCounter < SYNC_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;
        MatchManager manager = MatchManager.get(level);
        String newRed = manager.getSettings().homeDisplayName().getString();
        String newBlue = manager.getSettings().awayDisplayName().getString();
        String newClock = formatClock(manager.getRemainingSeconds());
        String newState = manager.getState().translationKey();
        if (manager.getRedScore() == redScore && manager.getBlueScore() == blueScore
                && newRed.equals(redName) && newBlue.equals(blueName)
                && newClock.equals(clock) && newState.equals(stateKey)) {
            return;
        }
        redName = newRed;
        blueName = newBlue;
        redScore = manager.getRedScore();
        blueScore = manager.getBlueScore();
        clock = newClock;
        stateKey = newState;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    private static String formatClock(int totalSeconds) {
        int safe = Math.max(0, totalSeconds);
        return String.format("%02d:%02d", safe / 60, safe % 60);
    }

    public String getRedName() {
        return redName;
    }

    public String getBlueName() {
        return blueName;
    }

    public int getRedScore() {
        return redScore;
    }

    public int getBlueScore() {
        return blueScore;
    }

    public String getClock() {
        return clock;
    }

    public String getStateKey() {
        return stateKey;
    }

    public boolean hasContent() {
        return !stateKey.isEmpty();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("RedName", redName);
        tag.putString("BlueName", blueName);
        tag.putInt("RedScore", redScore);
        tag.putInt("BlueScore", blueScore);
        tag.putString("Clock", clock);
        tag.putString("StateKey", stateKey);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        redName = tag.getString("RedName");
        blueName = tag.getString("BlueName");
        redScore = tag.getInt("RedScore");
        blueScore = tag.getInt("BlueScore");
        clock = tag.getString("Clock");
        stateKey = tag.getString("StateKey");
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** State keys the renderer colours differently. */
    public boolean isFinished() {
        return MatchState.FINISHED.translationKey().equals(stateKey);
    }
}
