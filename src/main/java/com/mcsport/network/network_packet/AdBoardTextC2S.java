package com.mcsport.network.network_packet;

import com.mcsport.blockentity.AdBoardBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client submits the advertising text for a board. Only operators (level 2)
 * may edit, the position must actually hold an ad board and the text is
 * re-clamped on the server; {@link AdBoardBlockEntity#setText(String)} then
 * syncs the change back to every tracking client.
 */
public class AdBoardTextC2S {

    private final BlockPos pos;
    private final String text;

    public AdBoardTextC2S(BlockPos pos, String text) {
        this.pos = pos;
        this.text = text;
    }

    public AdBoardTextC2S(FriendlyByteBuf buffer) {
        this.pos = buffer.readBlockPos();
        this.text = buffer.readUtf(AdBoardBlockEntity.MAX_TEXT_LENGTH);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeUtf(text, AdBoardBlockEntity.MAX_TEXT_LENGTH);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !player.hasPermissions(2)) {
                return;
            }
            if (!player.level().isLoaded(pos)) {
                return;
            }
            BlockEntity blockEntity = player.level().getBlockEntity(pos);
            if (blockEntity instanceof AdBoardBlockEntity board) {
                board.setText(text);
            }
        });
        context.setPacketHandled(true);
    }
}
