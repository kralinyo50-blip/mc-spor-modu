package com.mcsport.client.screen;

import com.mcsport.blockentity.AdBoardBlockEntity;
import com.mcsport.network.ModNetwork;
import com.mcsport.network.network_packet.AdBoardTextC2S;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Compact editor for an advertising board. The current server-stored text is
 * pre-filled, the character budget is shown live and saving sends a validated
 * C2S packet instead of touching the block entity directly. Players without
 * operator permissions can open the screen to read the message, but the save
 * button is disabled and the server would reject the packet anyway.
 */
public class AdBoardEditScreen extends Screen {

    private static final int PANEL_WIDTH = 260;
    private static final int PANEL_BG = 0xD0141820;
    private static final int PANEL_EDGE = 0xFF3A445C;
    private static final int COLOR_TITLE = 0xFFFFE082;
    private static final int COLOR_HINT = 0xFF9AA6B8;
    private static final int COLOR_COUNTER = 0xFF8A93A5;
    private static final int COLOR_WARN = 0xFFFF7B72;

    private final BlockPos boardPos;
    private EditBox textField;
    private Button saveButton;

    public AdBoardEditScreen(BlockPos boardPos) {
        super(Component.translatable("screen.mcsport.ad_board.title"));
        this.boardPos = boardPos;
    }

    @Override
    protected void init() {
        int left = (width - PANEL_WIDTH) / 2;
        int fieldY = height / 2 - 4;
        int fieldWidth = PANEL_WIDTH - 24;

        textField = new EditBox(font, left + 12, fieldY, fieldWidth, 20,
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

        int buttonY = fieldY + 34;
        saveButton = addRenderableWidget(Button.builder(
                        Component.translatable("screen.mcsport.ad_board.save"),
                        button -> save())
                .bounds(left + 12, buttonY, 74, 20).build());
        saveButton.active = canEdit();

        addRenderableWidget(Button.builder(
                        Component.translatable("screen.mcsport.ad_board.clear"),
                        button -> textField.setValue(""))
                .bounds(left + 12 + 80, buttonY, 74, 20).build());

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.cancel"),
                        button -> onClose())
                .bounds(left + 12 + 160, buttonY, 74, 20).build());
    }

    private boolean canEdit() {
        return Minecraft.getInstance().player != null
                && Minecraft.getInstance().player.hasPermissions(2);
    }

    private String currentText() {
        return (String) textField.getValue();
    }

    private void save() {
        if (!canEdit()) {
            return;
        }
        ModNetwork.CHANNEL.sendToServer(new AdBoardTextC2S(boardPos, currentText()));
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        int left = (width - PANEL_WIDTH) / 2;
        int fieldY = height / 2 - 4;
        int top = fieldY - 56;
        int bottom = fieldY + 70;
        graphics.fill(left, top, left + PANEL_WIDTH, bottom, PANEL_BG);
        // Thin gold frame plus a brighter top edge for a lit-panel feel.
        graphics.fill(left, top, left + PANEL_WIDTH, top + 1, PANEL_EDGE);
        graphics.fill(left, bottom - 1, left + PANEL_WIDTH, bottom, PANEL_EDGE);
        graphics.fill(left, top, left + 1, bottom, PANEL_EDGE);
        graphics.fill(left + PANEL_WIDTH - 1, top, left + PANEL_WIDTH, bottom, PANEL_EDGE);
        graphics.fill(left + 1, top + 1, left + PANEL_WIDTH - 1, top + 2, 0x66FFE082);

        graphics.drawCenteredString(font, title, width / 2, top + 10, COLOR_TITLE);
        graphics.drawCenteredString(font,
                Component.translatable("screen.mcsport.ad_board.hint"),
                width / 2, top + 24, COLOR_HINT);

        super.render(graphics, mouseX, mouseY, partialTick);

        Component counter = Component.translatable("screen.mcsport.ad_board.counter",
                currentText().length(), AdBoardBlockEntity.MAX_TEXT_LENGTH);
        graphics.drawString(font, counter, left + 12, fieldY + 24, COLOR_COUNTER, false);
        if (!canEdit()) {
            graphics.drawCenteredString(font,
                    Component.translatable("message.mcsport.ad_board.no_permission"),
                    width / 2, fieldY + 60, COLOR_WARN);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
