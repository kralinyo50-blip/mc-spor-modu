package com.mcsport.network.network_packet;

import com.mcsport.blockentity.AdBoardBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client submits the advertising text for a board. The server is authoritative:
 * only operators (level 2) may edit, the player must still be standing near the
 * block, the position must actually hold an ad board and the text is re-clamped
 * here; {@link AdBoardBlockEntity#setText(String)} then syncs the accepted text
 * back to every tracking client.
 */
public class AdBoardTextC2S {

    /** Reject edits from further away than this (8 blocks, squared). */
    private static final double MAX_EDIT_DISTANCE_SQR = 64.0D;

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
            if (player == null) {
                return;
            }
            if (!player.hasPermissions(2)) {
                player.displayClientMessage(
                        Component.translatable("message.mcsport.ad_board.no_permission")
                                .withStyle(ChatFormatting.GOLD), true);
                return;
            }
            double dx = player.getX() - (pos.getX() + 0.5D);
            double dy = player.getY() - (pos.getY() + 0.5D);
            double dz = player.getZ() - (pos.getZ() + 0.5D);
            if (dx * dx + dy * dy + dz * dz > MAX_EDIT_DISTANCE_SQR) {
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
