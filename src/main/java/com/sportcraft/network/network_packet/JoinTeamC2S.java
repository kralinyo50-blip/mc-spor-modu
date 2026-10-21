package com.sportcraft.network.network_packet;

import com.sportcraft.match.MatchManager;
import com.sportcraft.match.Team;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client asks the server to put the sender into a team (or spectator slot).
 */
public class JoinTeamC2S {

    private final Team team;

    public JoinTeamC2S(Team team) {
        this.team = team;
    }

    public JoinTeamC2S(FriendlyByteBuf buffer) {
        this.team = Team.byOrdinal(buffer.readByte());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeByte(team.ordinal());
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            MatchManager manager = MatchManager.get((ServerLevel) player.level());
            manager.setTeam(player.getUUID(), team);
            manager.broadcast(player.server);
        });
        context.setPacketHandled(true);
    }
}
