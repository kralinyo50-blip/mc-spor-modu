package com.sportcraft.network.network_packet;

import com.sportcraft.client.ClientMatchData;
import com.sportcraft.match.MatchState;
import com.sportcraft.match.Team;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server-to-client snapshot of the scoreboard state for the receiving player.
 */
public class MatchStateS2C {

    private final MatchState state;
    private final int redScore;
    private final int blueScore;
    private final int remainingSeconds;
    private final Team localTeam;

    public MatchStateS2C(byte stateOrdinal, int redScore, int blueScore,
                         int remainingSeconds, byte localTeamOrdinal) {
        this.state = MatchState.byOrdinal(stateOrdinal);
        this.redScore = redScore;
        this.blueScore = blueScore;
        this.remainingSeconds = remainingSeconds;
        this.localTeam = Team.byOrdinal(localTeamOrdinal);
    }

    public MatchStateS2C(FriendlyByteBuf buffer) {
        this(buffer.readByte(), buffer.readInt(), buffer.readInt(),
                buffer.readInt(), buffer.readByte());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeByte(state.ordinal());
        buffer.writeInt(redScore);
        buffer.writeInt(blueScore);
        buffer.writeInt(remainingSeconds);
        buffer.writeByte(localTeam.ordinal());
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // Only the physical client ever receives this packet.
            if (FMLEnvironment.dist == Dist.CLIENT) {
                ClientMatchData.update(state, redScore, blueScore, remainingSeconds, localTeam);
            }
        });
        context.setPacketHandled(true);
    }
}
