package com.mcsport.client.renderer;

import com.mcsport.blockentity.ScoreboardBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Quaternionf;

/**
 * Draws the live match data cached by {@link ScoreboardBlockEntity} onto the
 * panel face the block is aimed at. Everything rendered here was already
 * validated on the server; the renderer only paints the cached strings.
 *
 * <p>Layout, top to bottom: home club name in its kit colour, the big score,
 * the away club name, then the match clock and state. Lines are auto-shrunk so
 * long club names still fit inside the frame, and the panel is drawn
 * full-bright so it stays readable under the stadium roof and at night.</p>
 */
public class ScoreboardRenderer implements BlockEntityRenderer<ScoreboardBlockEntity> {

    /** Base glyph scale in blocks per font pixel. */
    private static final float BASE_SCALE = 0.0085F;
    /** Just in front of the panel's screen slab. */
    private static final float FACE_OFFSET = 0.145F;
    /** Lifts the text from the block centre up to the centre of the LED screen. */
    private static final float SCREEN_CENTER_Y = 0.131F;
    /** Usable width in base-scaled font pixels (panel inner width). */
    private static final float WINDOW_PX = 96.0F;
    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final int COLOR_WHITE = 0xFFFFFFFF;
    private static final int COLOR_DIM = 0xFFC8C8C8;
    private static final int COLOR_GOLD = 0xFFFFCC33;
    private static final float SCORE_MAX_SCALE = 2.6F;

    private final Font font;

    public ScoreboardRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    @Override
    public void render(ScoreboardBlockEntity board, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Direction facing = board.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
        // Default font glyphs face +Z; -toYRot() aims them at the block's face.
        float yaw = -(float) facing.toYRot();

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.5D, 0.5D);
        poseStack.mulPose(new Quaternionf().setAngleAxis(
                (float) Math.toRadians(yaw), 0.0F, 1.0F, 0.0F));
        poseStack.translate(0.0D, SCREEN_CENTER_Y, FACE_OFFSET);
        poseStack.scale(BASE_SCALE, BASE_SCALE, BASE_SCALE);

        if (board.isIdle()) {
            drawIdle(poseStack, bufferSource);
        } else {
            drawScore(poseStack, bufferSource, board);
        }
        poseStack.popPose();
    }

    /** Standby card shown before the first match snapshot arrives. */
    private void drawIdle(PoseStack poseStack, MultiBufferSource bufferSource) {
        drawLine(Component.literal("MC SPORT"), -22.0F, 1.8F, COLOR_GOLD, poseStack, bufferSource);
        drawLine(Component.translatable("message.mcsport.scoreboard_idle"), 8.0F, 0.9F,
                COLOR_DIM, poseStack, bufferSource);
    }

    private void drawScore(PoseStack poseStack, MultiBufferSource bufferSource,
                           ScoreboardBlockEntity board) {
        Component home = Component.literal(board.getRedName());
        Component away = Component.literal(board.getBlueName());
        Component score = Component.literal(board.getRedScore() + " : " + board.getBlueScore());
        String stateText = Component.translatable(board.getStateKey()).getString();
        Component clock = Component.literal(board.getClock() + "  " + stateText);

        drawLine(home, -32.0F, fit(home, 1.0F), board.getRedColor(), poseStack, bufferSource);
        drawLine(score, -16.0F, fit(score, SCORE_MAX_SCALE), COLOR_WHITE, poseStack, bufferSource);
        drawLine(away, 12.0F, fit(away, 1.0F), board.getBlueColor(), poseStack, bufferSource);
        drawLine(clock, 27.0F, fit(clock, 0.8F),
                board.isFinished() ? COLOR_GOLD : COLOR_DIM, poseStack, bufferSource);
    }

    /** Shrinks a line until it fits the panel width, never enlarging it. */
    private float fit(Component text, float maxScale) {
        int width = font.width(text);
        if (width <= 0) {
            return maxScale;
        }
        return Math.min(maxScale, WINDOW_PX / width);
    }

    /**
     * Draws one centred line. {@code y} and {@code scale} are expressed in the
     * renderer's base font-pixel space, so callers can stack lines as if they
     * were plain GUI text.
     */
    private void drawLine(Component text, float y, float scale, int color, PoseStack poseStack,
                         MultiBufferSource bufferSource) {
        poseStack.pushPose();
        poseStack.scale(scale, scale, 1.0F);
        float x = -font.width(text) / 2.0F;
        font.drawInBatch(text, x, y / scale, color, false, poseStack.last().pose(),
                bufferSource, Font.DisplayMode.NORMAL, 0, FULL_BRIGHT);
        poseStack.popPose();
    }
}
