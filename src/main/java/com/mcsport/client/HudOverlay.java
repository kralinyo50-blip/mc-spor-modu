package com.mcsport.client;

import com.mcsport.client.screen.MatchScreen;
import com.mcsport.item.SportsBallItem;
import com.mcsport.match.MatchState;
import com.mcsport.match.Team;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Scoreboard panel plus the throw-charge bar. The scoreboard is a compact
 * translucent panel with team-coloured side strips, a bold score line and a
 * state-coloured clock; it stays hidden while any full screen is open so it
 * never overlaps the match or guide-book GUIs.
 */
public class HudOverlay {

    private static final int PANEL_BORDER = 0xC0101010;
    private static final int PANEL_BACKGROUND = 0xAA1A1A22;
    private static final int TEXT_LIGHT = 0xFFEDEDED;
    private static final int TEXT_DIM = 0xFFB8B8B8;

    public static final IGuiOverlay MATCH_HUD = (gui, graphics, partialTick, screenWidth, screenHeight) -> {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui
                || minecraft.screen != null) {
            return;
        }
        Font font = minecraft.font;
        ClientMatchData data = ClientMatchData.INSTANCE;

        if (data.isActive()) {
            renderScorePanel(graphics, font, data, screenWidth);
        }

        if (minecraft.player.isUsingItem()
                && minecraft.player.getUseItem().getItem() instanceof SportsBallItem) {
            renderChargeBar(graphics, font, minecraft, screenWidth, screenHeight);
        }
    };

    private static void renderScorePanel(GuiGraphics graphics, Font font,
                                         ClientMatchData data, int screenWidth) {
        Component redName = Component.translatable(Team.RED.translationKey())
                .withStyle(ChatFormatting.RED);
        Component blueName = Component.translatable(Team.BLUE.translationKey())
                .withStyle(ChatFormatting.BLUE);
        Component scoreLine = Component.empty()
                .append(redName)
                .append("  " + data.getRedScore() + " : " + data.getBlueScore() + "  ")
                .append(blueName);

        Component teamLine;
        if (data.isLocalKeeper()) {
            teamLine = Component.empty()
                    .append(Component.translatable(data.getLocalTeam().translationKey()))
                    .append(" (")
                    .append(Component.translatable("gui.mcsport.keeper_tag"))
                    .append(")");
        } else {
            teamLine = Component.translatable(data.getLocalTeam().translationKey());
        }
        Component statusLine = Component.empty()
                .append(Component.translatable(data.getState().translationKey()))
                .append("  |  " + formatTime(data.getRemainingSeconds()) + "  |  ")
                .append(teamLine);

        int contentWidth = Math.max(font.width(scoreLine), font.width(statusLine));
        int panelWidth = contentWidth + 16;
        int panelHeight = 32;
        int left = (screenWidth - panelWidth) / 2;
        int top = 4;
        int right = left + panelWidth;
        int bottom = top + panelHeight;
        int centerX = screenWidth / 2;

        graphics.fill(left - 1, top - 1, right + 1, bottom + 1, PANEL_BORDER);
        graphics.fill(left, top, right, bottom, PANEL_BACKGROUND);
        // Team-coloured accent strips.
        graphics.fill(left, top, left + 2, bottom, 0xFFC02626);
        graphics.fill(right - 2, top, right, bottom, 0xFF274FC0);

        graphics.drawCenteredString(font, scoreLine, centerX, top + 6, TEXT_LIGHT);
        graphics.drawCenteredString(font, statusLine, centerX, top + 19,
                stateTextColor(data.getState()));
    }

    private static int stateTextColor(MatchState state) {
        return switch (state) {
            case RUNNING -> TEXT_DIM;
            case PAUSED -> 0xFFFFD060;
            case HALF_TIME -> 0xFF7FE0FF;
            case FINISHED -> 0xFFFFCC33;
            default -> 0xFF9A9A9A;
        };
    }

    private static void renderChargeBar(GuiGraphics graphics, Font font, Minecraft minecraft,
                                        int screenWidth, int screenHeight) {
        float charge = SportsBallItem.getChargeRatio(minecraft.player);
        int barWidth = 102;
        int x = (screenWidth - barWidth) / 2;
        int y = screenHeight - 44;
        graphics.fill(x - 1, y - 1, x + barWidth + 1, y + 7, 0xCC000000);
        graphics.fill(x, y, x + barWidth, y + 5, 0xFF555555);
        int fillColor = charge >= 1.0F ? 0xFFFF5555 : 0xFF55FF55;
        graphics.fill(x, y, x + Math.max(1, (int) (barWidth * charge)), y + 5, fillColor);
        graphics.drawCenteredString(font, Component.translatable("hud.mcsport.charge"),
                screenWidth / 2, y - 11, 0xFFFFFFFF);
    }

    private static String formatTime(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private HudOverlay() {
    }
}
