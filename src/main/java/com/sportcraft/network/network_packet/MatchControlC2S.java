package com.sportcraft.network.network_packet;

import com.sportcraft.match.MatchManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client asks the server to start / pause / resume / reset the match.
 *
 * <p>Every action is re-checked for permission level 2 on the server, so a
 * modified client cannot control the match without operator rights.</p>
 */
public class MatchControlC2S {

    public static final byte ACTION_START = 0;
    public static final byte ACTION_PAUSE = 1;
    public static final byte ACTION_RESUME = 2;
    public static final byte ACTION_RESET = 3;

    private final byte action;
    private final int durationSeconds;

    public MatchControlC2S(byte action, int durationSeconds) {
        this.action = action;
        this.durationSeconds = durationSeconds;
    }

    public MatchControlC2S(FriendlyByteBuf buffer) {
        this.action = buffer.readByte();
        this.durationSeconds = buffer.readInt();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeByte(action);
        buffer.writeInt(durationSeconds);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !player.hasPermissions(2)) {
                return;
            }
            MatchManager manager = MatchManager.get(player.serverLevel());
            switch (action) {
                case ACTION_START -> manager.start(durationSeconds > 0
                        ? durationSeconds : manager.getConfiguredDuration());
                case ACTION_PAUSE -> manager.pause();
                case ACTION_RESUME -> manager.resume();
                case ACTION_RESET -> manager.reset();
                default -> {
                    return;
                }
            }
            manager.broadcast(player.server);
        });
        context.setPacketHandled(true);
    }
}
