package com.mcsport.network.network_packet;

import com.mcsport.client.ClientMatchData;
import com.mcsport.match.BallType;
import com.mcsport.match.DrawRule;
import com.mcsport.match.FieldSize;
import com.mcsport.match.Formation;
import com.mcsport.match.MatchDifficulty;
import com.mcsport.match.MatchSettings;
import com.mcsport.match.MatchState;
import com.mcsport.match.Team;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server-to-client snapshot of the scoreboard state for the receiving player,
 * including the full setup-GUI settings, field status and the local player's
 * goalkeeper flag.
 */
public class MatchStateS2C {

    private final MatchState state;
    private final int redScore;
    private final int blueScore;
    private final int remainingSeconds;
    private final Team localTeam;
    private final boolean localKeeper;
    private final boolean fieldBuilt;
    private final MatchSettings settings;

    public MatchStateS2C(byte stateOrdinal, int redScore, int blueScore, int remainingSeconds,
                         byte localTeamOrdinal, boolean localKeeper, boolean fieldBuilt,
                         MatchSettings settings) {
        this.state = MatchState.byOrdinal(stateOrdinal);
        this.redScore = redScore;
        this.blueScore = blueScore;
        this.remainingSeconds = remainingSeconds;
        this.localTeam = Team.byOrdinal(localTeamOrdinal);
        this.localKeeper = localKeeper;
        this.fieldBuilt = fieldBuilt;
        this.settings = settings;
    }

    public MatchStateS2C(FriendlyByteBuf buffer) {
        this.state = MatchState.byOrdinal(buffer.readByte());
        this.redScore = buffer.readInt();
        this.blueScore = buffer.readInt();
        this.remainingSeconds = buffer.readInt();
        this.localTeam = Team.byOrdinal(buffer.readByte());
        this.localKeeper = buffer.readBoolean();
        this.fieldBuilt = buffer.readBoolean();
        MatchSettings value = new MatchSettings();
        value.setSport(BallType.byOrdinal(buffer.readByte()));
        value.setFieldSize(FieldSize.byOrdinal(buffer.readByte()));
        value.setTeamSize(buffer.readByte());
        value.setDifficulty(MatchDifficulty.byOrdinal(buffer.readByte()));
        value.setHalfMinutes(buffer.readByte());
        value.setDrawRule(DrawRule.byOrdinal(buffer.readByte()));
        value.setHomeFormation(Formation.byOrdinal(buffer.readByte()));
        value.setAwayFormation(Formation.byOrdinal(buffer.readByte()));
        this.settings = value;
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeByte(state.ordinal());
        buffer.writeInt(redScore);
        buffer.writeInt(blueScore);
        buffer.writeInt(remainingSeconds);
        buffer.writeByte(localTeam.ordinal());
        buffer.writeBoolean(localKeeper);
        buffer.writeBoolean(fieldBuilt);
        buffer.writeByte(settings.getSport().ordinal());
        buffer.writeByte(settings.getFieldSize().ordinal());
        buffer.writeByte(settings.getTeamSize());
        buffer.writeByte(settings.getDifficulty().ordinal());
        buffer.writeByte(settings.getHalfMinutes());
        buffer.writeByte(settings.getDrawRule().ordinal());
        buffer.writeByte(settings.getHomeFormation().ordinal());
        buffer.writeByte(settings.getAwayFormation().ordinal());
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // Only the physical client ever receives this packet.
            if (FMLEnvironment.dist == Dist.CLIENT) {
                ClientMatchData.update(state, redScore, blueScore, remainingSeconds,
                        localTeam, localKeeper, fieldBuilt, settings);
            }
        });
        context.setPacketHandled(true);
    }
}
