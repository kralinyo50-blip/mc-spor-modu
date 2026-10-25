package com.mcsport.client.renderer;

import com.mcsport.blockentity.ScoreboardBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;
import org.joml.AxisAngle4f;

/**
 * Draws the live match data cached by {@link ScoreboardBlockEntity} onto the
 * north face of the board. Everything rendered here was already validated on
 * the server; the renderer only paints the cached strings.
 */
public class ScoreboardRenderer implements BlockEntityRenderer<ScoreboardBlockEntity> {

    private static final float TEXT_SCALE = 0.011F;
    private static final int COLOR_RED = 0xFFFF6B6B;
    private static final int COLOR_BLUE = 0xFF6BA8FF;
    private static final int COLOR_WHITE = 0xFFFFFFFF;
    private static final int COLOR_DIM = 0xFFC8C8C8;
    private static final int COLOR_GOLD = 0xFFFFCC33;

    private final Font font;

    public ScoreboardRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(ScoreboardBlockEntity board, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (!board.hasContent()) {
            return;
        }
        Component red = Component.literal(board.getRedName());
        Component score = Component.literal(board.getRedScore() + " : " + board.getBlueScore());
        Component blue = Component.literal(board.getBlueName());
        Component clock = Component.literal(board.getClock() + "  "
                + Component.translatable(board.getStateKey()).getString());
        int clockColor = board.isFinished() ? COLOR_GOLD : COLOR_DIM;

        poseStack.pushPose();
        // Centre of the board's north-facing panel, just in front of it.
        poseStack.translate(0.5D, 0.80D, 0.36D);
        poseStack.mulPose(new org.joml.Quaternionf(new AxisAngle4f((float) Math.PI,
                0.0F, 1.0F, 0.0F)));
        poseStack.scale(TEXT_SCALE, TEXT_SCALE, TEXT_SCALE);

        drawCentered(red, -19, COLOR_RED, poseStack, bufferSource, packedLight);
        drawCentered(score, -9, COLOR_WHITE, poseStack, bufferSource, packedLight);
        drawCentered(blue, 1, COLOR_BLUE, poseStack, bufferSource, packedLight);
        drawCentered(clock, 11, clockColor, poseStack, bufferSource, packedLight);

        poseStack.popPose();
    }

    private void drawCentered(Component text, int y, int color, PoseStack poseStack,
                              MultiBufferSource bufferSource, int packedLight) {
        float x = -font.width(text) / 2.0F;
        font.drawInBatch(text, x, y, color, false, poseStack.last().pose(),
                bufferSource, Font.DisplayMode.NORMAL, 0, packedLight);
    }
}
