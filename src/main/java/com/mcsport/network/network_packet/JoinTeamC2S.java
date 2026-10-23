package com.mcsport.network.network_packet;

import com.mcsport.match.MatchManager;
import com.mcsport.match.Team;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client asks the server to put the sender into a team (or spectator slot)
 * and optionally mark them as that team's goalkeeper.
 */
public class JoinTeamC2S {

    private final Team team;
    private final boolean keeper;

    public JoinTeamC2S(Team team, boolean keeper) {
        this.team = team;
        this.keeper = keeper;
    }

    public JoinTeamC2S(FriendlyByteBuf buffer) {
        this.team = Team.byOrdinal(buffer.readByte());
        this.keeper = buffer.readBoolean();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeByte(team.ordinal());
        buffer.writeBoolean(keeper);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            MatchManager manager = MatchManager.get((ServerLevel) player.level());
            manager.noteLanguage(player);
            manager.setTeam(player.getUUID(), team);
            manager.setKeeper(player.getUUID(), keeper && team != Team.SPECTATOR);
            manager.broadcast(player.server);
        });
        context.setPacketHandled(true);
    }
}
