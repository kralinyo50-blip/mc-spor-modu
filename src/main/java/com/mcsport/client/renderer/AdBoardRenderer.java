package com.mcsport.client.renderer;

import com.mcsport.blockentity.AdBoardBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Quaternionf;

/**
 * Paints the operator-edited advertising text onto both faces of the LED
 * panel, following the block's horizontal facing. The text is always
 * server-validated.
 *
 * <p>Short slogans are centred on the panel. Anything wider than the visible
 * window becomes a marquee: it pauses, then scrolls right to left, with only
 * the glyphs currently inside the window being drawn, so the message never
 * spills past the frame. Text is drawn full-bright so the panel still reads at
 * night, like a real stadium LED board.</p>
 */
public class AdBoardRenderer implements BlockEntityRenderer<AdBoardBlockEntity> {

    /** Glyph scale in blocks per font pixel. */
    private static final float TEXT_SCALE = 0.0135F;
    /** Just in front of the panel's screen slab (both sides). */
    private static final float FACE_OFFSET = 0.145F;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    /** Packed light value of a block at full sky+block light (glowing panel). */
    private static final int FULL_BRIGHT = 0x00F000F0;
    /** Font.lineHeight is a fixed 9px in the vanilla bitmap font. */
    private static final int FONT_LINE_HEIGHT = 9;
    /** Horizontal window available for glyphs, in block units (screen inner width). */
    private static final float WINDOW_WIDTH = 0.78F;
    /** Lifts the text from the block centre up to the centre of the LED screen. */
    private static final float SCREEN_CENTER_Y = 0.125F;
    private static final float SCROLL_PX_PER_TICK = 1.1F;
    private static final int SCROLL_PAUSE_TICKS = 50;
    private static final int SCROLL_GAP_PX = 20;

    private final Font font;

    public AdBoardRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public void render(AdBoardBlockEntity board, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        String raw = board.getText();
        if (raw == null || raw.isEmpty()) {
            return;
        }
        Direction facing = board.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
        // Default font glyphs face +Z; this maps each facing onto that face:
        // SOUTH 0, EAST 90, NORTH 180, WEST 270, i.e. -toYRot().
        float yaw = -(float) facing.toYRot();
        Level level = board.getLevel();
        float ticks = (level == null ? 0L : level.getGameTime()) + partialTick;

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.5D, 0.5D);
        // Front face, then the same message mirrored onto the back face so the
        // board can be read from either side of the pitch.
        for (int side = 0; side < 2; side++) {
            poseStack.pushPose();
            // JOML 1.10.5 (MC 1.20.1): setAngleAxis takes radians.
            poseStack.mulPose(new Quaternionf().setAngleAxis(
                    (float) Math.toRadians(yaw + side * 180.0F), 0.0F, 1.0F, 0.0F));
            poseStack.translate(0.0D, SCREEN_CENTER_Y, FACE_OFFSET);
            poseStack.scale(TEXT_SCALE, TEXT_SCALE, TEXT_SCALE);
            drawMessage(raw, ticks, poseStack, bufferSource);
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    private void drawMessage(String raw, float ticks, PoseStack poseStack,
                             MultiBufferSource bufferSource) {
        float y = -FONT_LINE_HEIGHT / 2.0F;
        int windowPx = Math.round(WINDOW_WIDTH / TEXT_SCALE);
        int textPx = font.width(raw);
        if (textPx <= windowPx) {
            drawText(raw, -textPx / 2.0F, y, poseStack, bufferSource);
            return;
        }

        // Marquee: pause on the first word, then slide one full cycle.
        float travel = textPx + SCROLL_GAP_PX;
        float cycle = SCROLL_PAUSE_TICKS + travel / SCROLL_PX_PER_TICK;
        float phase = ticks % cycle;
        float scroll = phase <= SCROLL_PAUSE_TICKS ? 0.0F : (phase - SCROLL_PAUSE_TICKS) * SCROLL_PX_PER_TICK;

        float startX = windowPx / 2.0F - scroll;
        float windowLeft = -windowPx / 2.0F;
        float windowRight = windowPx / 2.0F;

        int firstVisible = -1;
        int charX = 0;
        StringBuilder visible = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            String glyph = raw.substring(i, i + 1);
            int width = font.width(glyph);
            float glyphX = startX + charX;
            charX += width;
            if (glyphX + width <= windowLeft || glyphX >= windowRight) {
                if (firstVisible >= 0) {
                    // The visible run is contiguous; nothing further can show.
                    break;
                }
                continue;
            }
            if (firstVisible < 0) {
                firstVisible = i;
            }
            visible.append(glyph);
        }
        if (firstVisible >= 0 && visible.length() > 0) {
            float x = startX + font.width(raw.substring(0, firstVisible));
            drawText(visible.toString(), x, y, poseStack, bufferSource);
        }
    }

    private void drawText(String text, float x, float y, PoseStack poseStack,
                          MultiBufferSource bufferSource) {
        font.drawInBatch(Component.literal(text), x, y, COLOR_TEXT, false,
                poseStack.last().pose(), bufferSource, Font.DisplayMode.NORMAL, 0, FULL_BRIGHT);
    }
}
