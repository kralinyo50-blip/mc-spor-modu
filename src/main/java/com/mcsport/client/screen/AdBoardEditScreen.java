package com.mcsport.client.screen;

import com.mcsport.blockentity.AdBoardBlockEntity;
import com.mcsport.network.ModNetwork;
import com.mcsport.network.network_packet.AdBoardTextC2S;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Small text entry dialog opened by right-clicking an ad board. The current
 * server-stored text is pre-filled when available; saving sends a validated
 * C2S packet rather than touching the block entity directly.
 */
public class AdBoardEditScreen extends Screen {

    private final BlockPos boardPos;
    private EditBox textField;

    public AdBoardEditScreen(BlockPos boardPos) {
        super(Component.translatable("screen.mcsport.ad_board.title"));
        this.boardPos = boardPos;
    }

    @Override
    protected void init() {
        int fieldWidth = 240;
        int left = (width - fieldWidth) / 2;
        int fieldY = height / 2 - 10;

        textField = new EditBox(font, left, fieldY, fieldWidth, 20,
                Component.translatable("screen.mcsport.ad_board.text"));
        textField.setMaxLength(AdBoardBlockEntity.MAX_TEXT_LENGTH);
        if (Minecraft.getInstance().level != null) {
            BlockEntity existing = Minecraft.getInstance().level.getBlockEntity(boardPos);
            if (existing instanceof AdBoardBlockEntity board) {
                textField.setValue(board.getText());
            }
        }
        addRenderableWidget(textField);
        setInitialFocus(textField);

        addRenderableWidget(Button.builder(
                        Component.translatable("screen.mcsport.ad_board.save"),
                        button -> save())
                .bounds(left, fieldY + 30, 115, 20).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.cancel"),
                        button -> onClose())
                .bounds(left + fieldWidth - 115, fieldY + 30, 115, 20).build());
    }

    private void save() {
        ModNetwork.CHANNEL.sendToServer(new AdBoardTextC2S(boardPos, (String) textField.getValue()));
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 40, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
