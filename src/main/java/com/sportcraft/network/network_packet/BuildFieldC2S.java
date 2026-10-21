package com.sportcraft.network.network_packet;

import com.sportcraft.match.FieldBuilder;
import com.sportcraft.match.FieldSize;
import com.sportcraft.match.MatchManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Operator asks the server to auto-build a pitch centred on the block the
 * player is standing on. The build runs entirely server-side; the client
 * only receives the normal block updates and the resulting match snapshot.
 */
public class BuildFieldC2S {

    private final FieldSize size;

    public BuildFieldC2S(FieldSize size) {
        this.size = size;
    }

    public BuildFieldC2S(FriendlyByteBuf buffer) {
        this.size = FieldSize.byOrdinal(buffer.readByte());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeByte(size.ordinal());
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !player.hasPermissions(2)) {
                return;
            }
            ServerLevel level = (ServerLevel) player.level();
            BlockPos center = player.blockPosition();
            FieldBuilder.FieldBuildResult result = FieldBuilder.build(level, center, size);
            MatchManager manager = MatchManager.get(level);
            manager.onFieldBuilt(center, size);
            manager.broadcast(player.server);
            player.sendSystemMessage(Component.translatable(
                    "message.sportcraft.field_built",
                    result.blocksPlaced(),
                    size.getWidth() + "x" + size.getLength())
                    .withStyle(ChatFormatting.GREEN));
        });
        context.setPacketHandled(true);
    }
}
