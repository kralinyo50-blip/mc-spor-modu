package com.sportcraft.client.screen;

import com.sportcraft.client.ClientMatchData;
import com.sportcraft.match.Team;
import com.sportcraft.network.ModNetwork;
import com.sportcraft.network.network_packet.JoinTeamC2S;
import com.sportcraft.network.network_packet.MatchControlC2S;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Match management screen. Buttons only emit validated C2S packets; the server
 * remains the single authority for teams, scores and match lifecycle.
 */
public class MatchScreen extends Screen {

    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;
    private static final int ROW_GAP = 24;

    private EditBox minutesBox;

    public MatchScreen() {
        super(Component.translatable("gui.sportcraft.match_title"));
    }

    @Override
    protected void init() {
        int x = width / 2 - BUTTON_WIDTH / 2;
        int y = height / 2 - 90;

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.sportcraft.join_red"),
                        button -> ModNetwork.CHANNEL.sendToServer(new JoinTeamC2S(Team.RED)))
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.sportcraft.join_blue"),
                        button -> ModNetwork.CHANNEL.sendToServer(new JoinTeamC2S(Team.BLUE)))
                .bounds(x, y + ROW_GAP, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.sportcraft.spectator"),
                        button -> ModNetwork.CHANNEL.sendToServer(new JoinTeamC2S(Team.SPECTATOR)))
                .bounds(x, y + ROW_GAP * 2, BUTTON_WIDTH, BUTTON_HEIGHT).build());

        // Start button leaves room for the minutes edit box on its right.
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.sportcraft.start"),
                        button -> ModNetwork.CHANNEL.sendToServer(
                                new MatchControlC2S(MatchControlC2S.ACTION_START, readMinutes() * 60)))
                .bounds(x, y + ROW_GAP * 3, 120, BUTTON_HEIGHT).build());
        minutesBox = new EditBox(font, x + 130, y + ROW_GAP * 3, 70, BUTTON_HEIGHT,
                Component.translatable("gui.sportcraft.minutes"));
        minutesBox.setFilter(text -> text.isEmpty() || text.matches("\\d{1,3}"));
        minutesBox.setMaxLength(3);
        minutesBox.setValue("5");
        addRenderableWidget(minutesBox);

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.sportcraft.pause"),
                        button -> ModNetwork.CHANNEL.sendToServer(
                                new MatchControlC2S(MatchControlC2S.ACTION_PAUSE, 0)))
                .bounds(x, y + ROW_GAP * 4, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.sportcraft.resume"),
                        button -> ModNetwork.CHANNEL.sendToServer(
                                new MatchControlC2S(MatchControlC2S.ACTION_RESUME, 0)))
                .bounds(x, y + ROW_GAP * 5, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.sportcraft.reset"),
                        button -> ModNetwork.CHANNEL.sendToServer(
                                new MatchControlC2S(MatchControlC2S.ACTION_RESET, 0)))
                .bounds(x, y + ROW_GAP * 6, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.done"),
                        button -> onClose())
                .bounds(x, y + ROW_GAP * 7, BUTTON_WIDTH, BUTTON_HEIGHT).build());
    }

    private int readMinutes() {
        if (minutesBox == null || minutesBox.getValue().isEmpty()) {
            return 5;
        }
        try {
            return Math.max(1, Math.min(120, Integer.parseInt(minutesBox.getValue())));
        } catch (NumberFormatException exception) {
            return 5;
        }
    }

    @Override
    public void tick() {
        if (minutesBox != null) {
            minutesBox.tick();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(font, title, width / 2, height / 2 - 115, 0xFFFFFFFF);

        ClientMatchData data = ClientMatchData.INSTANCE;
        Component scoreLine = Component.translatable("gui.sportcraft.score_line",
                data.getRedScore(), data.getBlueScore(),
                Component.translatable(data.getState().translationKey()));
        graphics.drawCenteredString(font, scoreLine, width / 2, height / 2 - 100, 0xFFC0C0C0);

        if (minutesBox != null) {
            graphics.drawString(font, Component.translatable("gui.sportcraft.minutes"),
                    minutesBox.getX() - 2, minutesBox.getY() - 11, 0xFFAAAAAA);
        }
    }
}
