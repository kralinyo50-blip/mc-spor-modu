package com.sportcraft.network.network_packet;

import com.sportcraft.match.MatchManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client asks the bot teammates for a pass. The server records a short-lived
 * request window per player; the on-ball bot checks it on its next touch and
 * plays a lead pass into the requester's running direction when a lane
 * exists. A modified client can only register its own request, never force a
 * pass or touch another participant's state.
 */
public class RequestPassC2S {

    public RequestPassC2S() {
    }

    public RequestPassC2S(FriendlyByteBuf buffer) {
        // No fields; the decoder still has to exist for the channel contract.
    }

    public void encode(FriendlyByteBuf buffer) {
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            ServerLevel level = (ServerLevel) player.level();
            MatchManager.get(level).requestPass(player);
        });
        context.setPacketHandled(true);
    }
}
