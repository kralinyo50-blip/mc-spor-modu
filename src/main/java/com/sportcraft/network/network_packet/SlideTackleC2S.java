package com.sportcraft.network.network_packet;

import com.sportcraft.match.MatchManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client asks the server to perform a slide tackle in the look direction.
 * The packet carries no payload: the server derives everything from the
 * sender's position and view, applies the lunge itself and runs the same
 * foul checks as bot tackles, so a modified client cannot force contact.
 */
public class SlideTackleC2S {

    public SlideTackleC2S() {
    }

    public SlideTackleC2S(FriendlyByteBuf buffer) {
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
            MatchManager.get(level).tryPlayerSlide(level, player);
        });
        context.setPacketHandled(true);
    }
}
