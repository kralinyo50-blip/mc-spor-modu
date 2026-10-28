package com.mcsport.blockentity;

import com.mcsport.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Stores the custom advertising text painted onto an ad board. The text is
 * validated and clamped on the server, synced through the block entity
 * update packet and rendered by the client block entity renderer.
 */
public class AdBoardBlockEntity extends BlockEntity {

    public static final int MAX_TEXT_LENGTH = 48;

    private String text = "";

    public AdBoardBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AD_BOARD.get(), pos, state);
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        String sanitized = text == null ? "" : text.trim();
        if (sanitized.length() > MAX_TEXT_LENGTH) {
            sanitized = sanitized.substring(0, MAX_TEXT_LENGTH);
        }
        if (!sanitized.equals(this.text)) {
            this.text = sanitized;
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("Text", text);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        text = tag.getString("Text");
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
