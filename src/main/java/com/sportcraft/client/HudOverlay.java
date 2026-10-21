package com.sportcraft.client;

import com.sportcraft.client.screen.MatchScreen;
import com.sportcraft.item.SportsBallItem;
import com.sportcraft.match.Team;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Compact scoreboard overlay plus the throw-charge bar. Never covers the
 * centre of the screen and stays hidden while the match screen is open.
 */
public class HudOverlay {

    public static final IGuiOverlay MATCH_HUD = (gui, graphics, partialTick, screenWidth, screenHeight) -> {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui
                || minecraft.screen instanceof MatchScreen) {
            return;
        }
        Font font = minecraft.font;
        ClientMatchData data = ClientMatchData.INSTANCE;

        if (data.isActive()) {
            Component redName = Component.translatable(Team.RED.translationKey()).withStyle(ChatFormatting.RED);
            Component blueName = Component.translatable(Team.BLUE.translationKey()).withStyle(ChatFormatting.BLUE);
            Component score = Component.empty()
                    .append(redName)
                    .append("  " + data.getRedScore() + " : " + data.getBlueScore() + "  ")
                    .append(blueName);
            graphics.drawCenteredString(font, score, screenWidth / 2, 6, 0xFFFFFFFF);

            Component statusLine = Component.empty()
                    .append(Component.translatable(data.getState().translationKey()))
                    .append("  |  " + formatTime(data.getRemainingSeconds()) + "  |  ")
                    .append(Component.translatable(data.getLocalTeam().translationKey()));
            graphics.drawCenteredString(font, statusLine, screenWidth / 2, 18, 0xFFD0D0D0);
        }

        if (minecraft.player.isUsingItem()
                && minecraft.player.getUseItem().getItem() instanceof SportsBallItem) {
            float charge = SportsBallItem.getChargeRatio(minecraft.player);
            int barWidth = 102;
            int x = (screenWidth - barWidth) / 2;
            int y = screenHeight - 44;
            graphics.fill(x - 1, y - 1, x + barWidth + 1, y + 7, 0xCC000000);
            graphics.fill(x, y, x + barWidth, y + 5, 0xFF555555);
            int fillColor = charge >= 1.0F ? 0xFFFF5555 : 0xFF55FF55;
            graphics.fill(x, y, x + Math.max(1, (int) (barWidth * charge)), y + 5, fillColor);
            graphics.drawCenteredString(font, Component.translatable("hud.sportcraft.charge"),
                    screenWidth / 2, y - 11, 0xFFFFFFFF);
        }
    };

    private static String formatTime(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private HudOverlay() {
    }
}
