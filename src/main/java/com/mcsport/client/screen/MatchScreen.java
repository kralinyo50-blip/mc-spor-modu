package com.mcsport.client.screen;

import com.mcsport.client.ClientMatchData;
import com.mcsport.match.BallType;
import com.mcsport.match.Club;
import com.mcsport.match.DrawRule;
import com.mcsport.match.FieldSize;
import com.mcsport.match.Formation;
import com.mcsport.match.MatchDifficulty;
import com.mcsport.match.MatchSettings;
import com.mcsport.match.MatchState;
import com.mcsport.match.Team;
import com.mcsport.network.ModNetwork;
import com.mcsport.network.network_packet.BuildFieldC2S;
import com.mcsport.network.network_packet.JoinTeamC2S;
import com.mcsport.network.network_packet.MatchControlC2S;
import com.mcsport.network.network_packet.MatchSettingsC2S;
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
    private Button homeClubButton;
    private Button awayClubButton;
    private Button startButton;
    private Button buildButton;
    private Button joinRedButton;
    private Button joinBlueButton;
    private Button keeperRedButton;
    private Button keeperBlueButton;
    private Button leaveButton;

    // Optimistic join feedback: the click is shown immediately with an
    // ellipsis and replaced by the authoritative check mark once the server
    // snapshot arrives, so the buttons never look frozen.
    private Team pendingTeam;
    private boolean pendingKeeper;
    private int pendingTicks;

    public MatchScreen() {
        super(Component.translatable("gui.mcsport.match_title"));
    }

    @Override
    protected void init() {
        draft = ClientMatchData.INSTANCE.getSettings().copy();
        int left = width / 2 - COL_WIDTH - GAP / 2;
        int right = width / 2 + GAP / 2;
        int top = height / 2 - 120;
        int row = 0;

        // Row 1: sport and field size.
        sportButton = addRenderableWidget(Button.builder(Component.empty(), b -> cycleSport())
                .bounds(left, top + row * ROW_STEP, COL_WIDTH, ROW_HEIGHT).build());
        sizeButton = addRenderableWidget(Button.builder(Component.empty(), b -> cycleSize())
                .bounds(right, top + row * ROW_STEP, COL_WIDTH, ROW_HEIGHT).build());
        row++;

        // Row 2: one-click field build across both columns.
        buildButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.mcsport.build_field"),
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

        // Row 6: home and away club identity (six fixed-squad clubs).
        homeClubButton = addRenderableWidget(Button.builder(Component.empty(), b -> cycleHomeClub())
                .bounds(left, top + row * ROW_STEP, COL_WIDTH, ROW_HEIGHT).build());
        awayClubButton = addRenderableWidget(Button.builder(Component.empty(), b -> cycleAwayClub())
                .bounds(right, top + row * ROW_STEP, COL_WIDTH, ROW_HEIGHT).build());
        row++;

        // Row 7: join home / away.
        joinRedButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.mcsport.join_red"),
                        b -> requestJoin(Team.RED, false))
                .bounds(left, top + row * ROW_STEP, COL_WIDTH, ROW_HEIGHT).build());
        joinBlueButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.mcsport.join_blue"),
                        b -> requestJoin(Team.BLUE, false))
                .bounds(right, top + row * ROW_STEP, COL_WIDTH, ROW_HEIGHT).build());
        row++;

        // Row 8: goalkeeper join buttons.
        keeperRedButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.mcsport.keeper_red"),
                        b -> requestJoin(Team.RED, true))
                .bounds(left, top + row * ROW_STEP, COL_WIDTH, ROW_HEIGHT).build());
        keeperBlueButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.mcsport.keeper_blue"),
                        b -> requestJoin(Team.BLUE, true))
                .bounds(right, top + row * ROW_STEP, COL_WIDTH, ROW_HEIGHT).build());
        row++;

        // Row 9: leave team and start match.
        leaveButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.mcsport.leave_team"),
                        b -> requestJoin(Team.SPECTATOR, false))
                .bounds(left, top + row * ROW_STEP, COL_WIDTH, ROW_HEIGHT).build());
        startButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.mcsport.start"),
                        b -> ModNetwork.CHANNEL.sendToServer(new MatchControlC2S(
                                MatchControlC2S.ACTION_START, draft.getHalfLengthSeconds())))
                .bounds(right, top + row * ROW_STEP, COL_WIDTH, ROW_HEIGHT).build());
        row++;

        // Row 10: lifecycle controls in thirds.
        int third = (COL_WIDTH * 2 + GAP - GAP * 2) / 3;
        int lifecycleTop = top + row * ROW_STEP;
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.mcsport.pause"),
                        b -> ModNetwork.CHANNEL.sendToServer(
                                new MatchControlC2S(MatchControlC2S.ACTION_PAUSE, 0)))
                .bounds(left, lifecycleTop, third, ROW_HEIGHT).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.mcsport.resume"),
                        b -> ModNetwork.CHANNEL.sendToServer(
                                new MatchControlC2S(MatchControlC2S.ACTION_RESUME, 0)))
                .bounds(left + third + GAP, lifecycleTop, third, ROW_HEIGHT).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.mcsport.reset"),
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
        int next = draft.getTeamSize() + direction;
        // Wrap around instead of clamping so the button never gets stuck at 7v7.
        if (next > MatchSettings.MAX_TEAM_SIZE) {
            next = MatchSettings.MIN_TEAM_SIZE;
        } else if (next < MatchSettings.MIN_TEAM_SIZE) {
            next = MatchSettings.MAX_TEAM_SIZE;
        }
        draft.setTeamSize(next);
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

    private void cycleHomeClub() {
        Club next = hasShiftDown() ? draft.getHomeClub().previous() : draft.getHomeClub().next();
        // A club cannot play itself; skip the away club.
        if (next == draft.getAwayClub()) {
            next = hasShiftDown() ? next.previous() : next.next();
        }
        draft.setHomeClub(next);
        sendSettings();
    }

    private void cycleAwayClub() {
        Club next = hasShiftDown() ? draft.getAwayClub().previous() : draft.getAwayClub().next();
        if (next == draft.getHomeClub()) {
            next = hasShiftDown() ? next.previous() : next.next();
        }
        draft.setAwayClub(next);
        sendSettings();
    }

    private void sendSettings() {
        ModNetwork.CHANNEL.sendToServer(new MatchSettingsC2S(draft));
    }

    /** Sends the join packet and arms the optimistic pending feedback. */
    private void requestJoin(Team team, boolean keeper) {
        pendingTeam = team;
        pendingKeeper = keeper && team != Team.SPECTATOR;
        pendingTicks = 15;
        ModNetwork.CHANNEL.sendToServer(new JoinTeamC2S(team, keeper));
    }

    @Override
    public void tick() {
        // Re-sync with the authoritative snapshot so rejected edits revert and
        // changes made by another operator appear immediately.
        draft = ClientMatchData.INSTANCE.getSettings().copy();
        if (pendingTicks > 0) {
            pendingTicks--;
            // The server snapshot already reflects the requested assignment:
            // drop the pending ellipsis in favour of the confirmed check mark.
            ClientMatchData data = ClientMatchData.INSTANCE;
            if (data.getLocalTeam() == pendingTeam
                    && data.isLocalKeeper() == pendingKeeper) {
                pendingTicks = 0;
            }
        }
    }

    /** Join-button label: green check when active, gold ellipsis while pending. */
    private MutableComponent joinLabel(String baseKey, Team team, boolean keeper) {
        MutableComponent label = Component.translatable(baseKey);
        boolean confirmed = ClientMatchData.INSTANCE.getLocalTeam() == team
                && ClientMatchData.INSTANCE.isLocalKeeper() == keeper;
        boolean pending = pendingTicks > 0 && pendingTeam == team && pendingKeeper == keeper;
        if (pending) {
            return label.append(" …").withStyle(ChatFormatting.GOLD);
        }
        if (confirmed) {
            return label.append(" ✓").withStyle(ChatFormatting.GREEN);
        }
        return label.withStyle(ChatFormatting.GRAY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        ClientMatchData data = ClientMatchData.INSTANCE;
        boolean fieldBuilt = data.isFieldBuilt();

        int titleColor = fieldBuilt ? 0xFFFFFFFF : 0xFFFF5555;
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 124, titleColor);

        Component scoreLine = Component.translatable("gui.mcsport.score_line",
                data.getRedScore(), data.getBlueScore(),
                Component.translatable(data.getState().translationKey()));
        graphics.drawCenteredString(font, scoreLine, width / 2, height / 2 - 112, 0xFFC0C0C0);

        sportButton.setMessage(option("gui.mcsport.opt.sport",
                Component.translatable("sport.mcsport." + draft.getSport().name().toLowerCase())));
        FieldSize size = draft.getFieldSize();
        sizeButton.setMessage(option("gui.mcsport.opt.field_size",
                Component.translatable("field_size.mcsport." + size.getTranslationSuffix())
                        .append(" " + size.getWidth() + "x" + size.getLength())));
        homeClubButton.setMessage(option("gui.mcsport.opt.home_club",
                Component.translatable(draft.getHomeClub().translationKey())));
        awayClubButton.setMessage(option("gui.mcsport.opt.away_club",
                Component.translatable(draft.getAwayClub().translationKey())));
        teamSizeButton.setMessage(option("gui.mcsport.opt.team_size",
                Component.literal(draft.getTeamSize() + "v" + draft.getTeamSize())));
        difficultyButton.setMessage(option("gui.mcsport.opt.difficulty",
                Component.translatable(draft.getDifficulty().translationKey())));
        halfButton.setMessage(option("gui.mcsport.opt.half_length",
                Component.translatable("gui.mcsport.minutes_value",
                        draft.getHalfMinutes())));
        drawButton.setMessage(option("gui.mcsport.opt.draw_rule",
                Component.translatable(draft.getDrawRule().translationKey())));
        homeFormButton.setMessage(option("gui.mcsport.opt.home_formation",
                Component.literal(draft.getHomeFormation().getLabel())));
        awayFormButton.setMessage(option("gui.mcsport.opt.away_formation",
                Component.literal(draft.getAwayFormation().getLabel())));

        startButton.active = fieldBuilt && data.getState() != MatchState.RUNNING;

        // Explicit join feedback: the active slot is highlighted on the
        // button itself instead of only in the small footer line.
        joinRedButton.setMessage(joinLabel("gui.mcsport.join_red", Team.RED, false));
        joinBlueButton.setMessage(joinLabel("gui.mcsport.join_blue", Team.BLUE, false));
        keeperRedButton.setMessage(joinLabel("gui.mcsport.keeper_red", Team.RED, true));
        keeperBlueButton.setMessage(joinLabel("gui.mcsport.keeper_blue", Team.BLUE, true));
        leaveButton.setMessage(joinLabel("gui.mcsport.leave_team", Team.SPECTATOR, false));

        // The server rejects setup edits while a match is running; show that
        // visibly instead of letting buttons silently revert on the next tick.
        boolean locked = data.getState() == MatchState.RUNNING;
        sportButton.active = !locked;
        sizeButton.active = !locked;
        teamSizeButton.active = !locked;
        difficultyButton.active = !locked;
        halfButton.active = !locked;
        drawButton.active = !locked;
        homeFormButton.active = !locked;
        awayFormButton.active = !locked;
        homeClubButton.active = !locked;
        awayClubButton.active = !locked;
        buildButton.active = !locked;

        int footerY = height / 2 + 132;
        Component fieldStatus = Component.translatable(
                        fieldBuilt ? "gui.mcsport.field_ready" : "gui.mcsport.field_missing")
                .withStyle(fieldBuilt ? ChatFormatting.GREEN : ChatFormatting.RED);
        graphics.drawCenteredString(font, fieldStatus, width / 2, footerY, 0xFFFFFFFF);

        MutableComponent teamLine = Component.translatable("gui.mcsport.your_team",
                Component.translatable(data.getLocalTeam().translationKey()));
        if (data.isLocalKeeper()) {
            teamLine.append(" (").append(Component.translatable("gui.mcsport.keeper_tag"))
                    .append(")");
        }
        graphics.drawCenteredString(font, teamLine, width / 2, footerY + 12, 0xFFD0D0D0);
        graphics.drawCenteredString(font,
                Component.translatable("gui.mcsport.shift_hint"),
                width / 2, footerY + 24, 0xFF909090);
        if (locked) {
            graphics.drawCenteredString(font,
                    Component.translatable("gui.mcsport.locked_hint"),
                    width / 2, footerY + 36, 0xFFFFB0B0);
        }
    }

    private MutableComponent option(String labelKey, Component value) {
        return Component.translatable(labelKey).append(": ").append(value);
    }
}
