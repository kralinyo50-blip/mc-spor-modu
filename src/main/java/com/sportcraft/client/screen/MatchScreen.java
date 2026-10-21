package com.sportcraft.client.screen;

import com.sportcraft.client.ClientMatchData;
import com.sportcraft.match.BallType;
import com.sportcraft.match.DrawRule;
import com.sportcraft.match.FieldSize;
import com.sportcraft.match.Formation;
import com.sportcraft.match.MatchDifficulty;
import com.sportcraft.match.MatchSettings;
import com.sportcraft.match.MatchState;
import com.sportcraft.match.Team;
import com.sportcraft.network.ModNetwork;
import com.sportcraft.network.network_packet.BuildFieldC2S;
import com.sportcraft.network.network_packet.JoinTeamC2S;
import com.sportcraft.network.network_packet.MatchControlC2S;
import com.sportcraft.network.network_packet.MatchSettingsC2S;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Match setup screen modelled on the reference "MC Spor" GUI: cycling option
 * buttons (click forward, Shift+click backward), a one-click field builder,
 * team / goalkeeper join buttons and the match lifecycle controls.
 *
 * <p>Every button only emits validated C2S packets; the server remains the
 * single authority for teams, settings, field state and the match clock.</p>
 */
public class MatchScreen extends Screen {

    private static final int COL_WIDTH = 150;
    private static final int GAP = 4;
    private static final int ROW_HEIGHT = 22;
    private static final int ROW_STEP = 24;

    private MatchSettings draft;

    private Button sportButton;
    private Button sizeButton;
    private Button teamSizeButton;
    private Button difficultyButton;
    private Button halfButton;
    private Button drawButton;
    private Button homeFormButton;
    private Button awayFormButton;
    private Button startButton;

    public MatchScreen() {
        super(Component.translatable("gui.sportcraft.match_title"));
    }

    @Override
    protected void init() {
        draft = ClientMatchData.INSTANCE.getSettings().copy();
        int left = width / 2 - COL_WIDTH - GAP / 2;
        int right = width / 2 + GAP / 2;
        int top = height / 2 - 108;
        int row = 0;

        // Row 1: sport and field size.
        sportButton = addRenderableWidget(Button.builder(Component.empty(), b -> cycleSport())
                .bounds(left, top + row * ROW_STEP, COL_WIDTH, ROW_HEIGHT).build());
        sizeButton = addRenderableWidget(Button.builder(Component.empty(), b -> cycleSize())
                .bounds(right, top + row * ROW_STEP, COL_WIDTH, ROW_HEIGHT).build());
        row++;

        // Row 2: one-click field build across both columns.
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.sportcraft.build_field"),
                        b -> ModNetwork.CHANNEL.sendToServer(new BuildFieldC2S(draft.getFieldSize())))
                .bounds(left, top + row * ROW_STEP, COL_WIDTH * 2 + GAP, ROW_HEIGHT).build());
        row++;

        // Row 3: team size and difficulty.
        teamSizeButton = addRenderableWidget(Button.builder(Component.empty(), b -> cycleTeamSize())
                .bounds(left, top + row * ROW_STEP, COL_WIDTH, ROW_HEIGHT).build());
        difficultyButton = addRenderableWidget(Button.builder(Component.empty(), b -> cycleDifficulty())
                .bounds(right, top + row * ROW_STEP, COL_WIDTH, ROW_HEIGHT).build());
        row++;

        // Row 4: half length and draw rule.
        halfButton = addRenderableWidget(Button.builder(Component.empty(), b -> cycleHalfMinutes())
                .bounds(left, top + row * ROW_STEP, COL_WIDTH, ROW_HEIGHT).build());
        drawButton = addRenderableWidget(Button.builder(Component.empty(), b -> cycleDrawRule())
                .bounds(right, top + row * ROW_STEP, COL_WIDTH, ROW_HEIGHT).build());
        row++;

        // Row 5: home and away formations.
        homeFormButton = addRenderableWidget(Button.builder(Component.empty(), b -> cycleHomeFormation())
                .bounds(left, top + row * ROW_STEP, COL_WIDTH, ROW_HEIGHT).build());
        awayFormButton = addRenderableWidget(Button.builder(Component.empty(), b -> cycleAwayFormation())
                .bounds(right, top + row * ROW_STEP, COL_WIDTH, ROW_HEIGHT).build());
        row++;

        // Row 6: join home / away.
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.sportcraft.join_red"),
                        b -> ModNetwork.CHANNEL.sendToServer(new JoinTeamC2S(Team.RED, false)))
                .bounds(left, top + row * ROW_STEP, COL_WIDTH, ROW_HEIGHT).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.sportcraft.join_blue"),
                        b -> ModNetwork.CHANNEL.sendToServer(new JoinTeamC2S(Team.BLUE, false)))
                .bounds(right, top + row * ROW_STEP, COL_WIDTH, ROW_HEIGHT).build());
        row++;

        // Row 7: goalkeeper join buttons.
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.sportcraft.keeper_red"),
                        b -> ModNetwork.CHANNEL.sendToServer(new JoinTeamC2S(Team.RED, true)))
                .bounds(left, top + row * ROW_STEP, COL_WIDTH, ROW_HEIGHT).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.sportcraft.keeper_blue"),
                        b -> ModNetwork.CHANNEL.sendToServer(new JoinTeamC2S(Team.BLUE, true)))
                .bounds(right, top + row * ROW_STEP, COL_WIDTH, ROW_HEIGHT).build());
        row++;

        // Row 8: leave team and start match.
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.sportcraft.leave_team"),
                        b -> ModNetwork.CHANNEL.sendToServer(new JoinTeamC2S(Team.SPECTATOR, false)))
                .bounds(left, top + row * ROW_STEP, COL_WIDTH, ROW_HEIGHT).build());
        startButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.sportcraft.start"),
                        b -> ModNetwork.CHANNEL.sendToServer(new MatchControlC2S(
                                MatchControlC2S.ACTION_START, draft.getHalfLengthSeconds())))
                .bounds(right, top + row * ROW_STEP, COL_WIDTH, ROW_HEIGHT).build());
        row++;

        // Row 9: lifecycle controls in thirds.
        int third = (COL_WIDTH * 2 + GAP - GAP * 2) / 3;
        int lifecycleTop = top + row * ROW_STEP;
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.sportcraft.pause"),
                        b -> ModNetwork.CHANNEL.sendToServer(
                                new MatchControlC2S(MatchControlC2S.ACTION_PAUSE, 0)))
                .bounds(left, lifecycleTop, third, ROW_HEIGHT).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.sportcraft.resume"),
                        b -> ModNetwork.CHANNEL.sendToServer(
                                new MatchControlC2S(MatchControlC2S.ACTION_RESUME, 0)))
                .bounds(left + third + GAP, lifecycleTop, third, ROW_HEIGHT).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.sportcraft.reset"),
                        b -> ModNetwork.CHANNEL.sendToServer(
                                new MatchControlC2S(MatchControlC2S.ACTION_RESET, 0)))
                .bounds(left + (third + GAP) * 2, lifecycleTop, third, ROW_HEIGHT).build());
    }

    private void cycleSport() {
        BallType next = hasShiftDown() ? draft.getSport().previous() : draft.getSport().next();
        draft.setSport(next);
        sendSettings();
    }

    private void cycleSize() {
        FieldSize next = hasShiftDown() ? draft.getFieldSize().previous() : draft.getFieldSize().next();
        draft.setFieldSize(next);
        sendSettings();
    }

    private void cycleTeamSize() {
        int direction = hasShiftDown() ? -1 : 1;
        draft.setTeamSize(draft.getTeamSize() + direction);
        sendSettings();
    }

    private void cycleDifficulty() {
        MatchDifficulty next = hasShiftDown()
                ? draft.getDifficulty().previous() : draft.getDifficulty().next();
        draft.setDifficulty(next);
        sendSettings();
    }

    private void cycleHalfMinutes() {
        int direction = hasShiftDown() ? -1 : 1;
        draft.setHalfMinutes(draft.getHalfMinutes() + direction);
        sendSettings();
    }

    private void cycleDrawRule() {
        DrawRule next = hasShiftDown() ? draft.getDrawRule().previous() : draft.getDrawRule().next();
        draft.setDrawRule(next);
        sendSettings();
    }

    private void cycleHomeFormation() {
        Formation next = hasShiftDown()
                ? draft.getHomeFormation().previous() : draft.getHomeFormation().next();
        draft.setHomeFormation(next);
        sendSettings();
    }

    private void cycleAwayFormation() {
        Formation next = hasShiftDown()
                ? draft.getAwayFormation().previous() : draft.getAwayFormation().next();
        draft.setAwayFormation(next);
        sendSettings();
    }

    private void sendSettings() {
        ModNetwork.CHANNEL.sendToServer(new MatchSettingsC2S(draft));
    }

    @Override
    public void tick() {
        // Re-sync with the authoritative snapshot so rejected edits revert and
        // changes made by another operator appear immediately.
        draft = ClientMatchData.INSTANCE.getSettings().copy();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        ClientMatchData data = ClientMatchData.INSTANCE;
        boolean fieldBuilt = data.isFieldBuilt();

        int titleColor = fieldBuilt ? 0xFFFFFFFF : 0xFFFF5555;
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 124, titleColor);

        Component scoreLine = Component.translatable("gui.sportcraft.score_line",
                data.getRedScore(), data.getBlueScore(),
                Component.translatable(data.getState().translationKey()));
        graphics.drawCenteredString(font, scoreLine, width / 2, height / 2 - 112, 0xFFC0C0C0);

        sportButton.setMessage(option("gui.sportcraft.opt.sport",
                Component.translatable("sport.sportcraft." + draft.getSport().name().toLowerCase())));
        FieldSize size = draft.getFieldSize();
        sizeButton.setMessage(option("gui.sportcraft.opt.field_size",
                Component.translatable("field_size.sportcraft." + size.getTranslationSuffix())
                        .append(" " + size.getWidth() + "x" + size.getLength())));
        teamSizeButton.setMessage(option("gui.sportcraft.opt.team_size",
                Component.literal(draft.getTeamSize() + "v" + draft.getTeamSize())));
        difficultyButton.setMessage(option("gui.sportcraft.opt.difficulty",
                Component.translatable(draft.getDifficulty().translationKey())));
        halfButton.setMessage(option("gui.sportcraft.opt.half_length",
                Component.literal(draft.getHalfMinutes() + " dk")));
        drawButton.setMessage(option("gui.sportcraft.opt.draw_rule",
                Component.translatable(draft.getDrawRule().translationKey())));
        homeFormButton.setMessage(option("gui.sportcraft.opt.home_formation",
                Component.literal(draft.getHomeFormation().getLabel())));
        awayFormButton.setMessage(option("gui.sportcraft.opt.away_formation",
                Component.literal(draft.getAwayFormation().getLabel())));

        startButton.active = fieldBuilt && data.getState() != MatchState.RUNNING;

        int footerY = height / 2 + 132;
        Component fieldStatus = Component.translatable(
                        fieldBuilt ? "gui.sportcraft.field_ready" : "gui.sportcraft.field_missing")
                .withStyle(fieldBuilt ? ChatFormatting.GREEN : ChatFormatting.RED);
        graphics.drawCenteredString(font, fieldStatus, width / 2, footerY, 0xFFFFFFFF);

        MutableComponent teamLine = Component.translatable("gui.sportcraft.your_team",
                Component.translatable(data.getLocalTeam().translationKey()));
        if (data.isLocalKeeper()) {
            teamLine.append(" (").append(Component.translatable("gui.sportcraft.keeper_tag"))
                    .append(")");
        }
        graphics.drawCenteredString(font, teamLine, width / 2, footerY + 12, 0xFFD0D0D0);
        graphics.drawCenteredString(font,
                Component.translatable("gui.sportcraft.shift_hint"),
                width / 2, footerY + 24, 0xFF909090);
    }

    private MutableComponent option(String labelKey, Component value) {
        return Component.translatable(labelKey).append(": ").append(value);
    }
}
