package com.mcsport.client.renderer;

import com.mcsport.blockentity.AdBoardBlockEntity;
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
 * Paints the operator-edited advertising text onto the panel face matching
 * the block's horizontal facing. The text is always server-validated; an
 * empty board renders nothing so the bare model stays clean.
 */
public class AdBoardRenderer implements BlockEntityRenderer<AdBoardBlockEntity> {

    private static final float TEXT_SCALE = 0.012F;
    private static final int COLOR_WHITE = 0xFFFFFFFF;
    /** Font.lineHeight is a fixed 9px in the vanilla bitmap font. */
    private static final int FONT_LINE_HEIGHT = 9;
    /** Half of the panel's 4-pixel thickness plus a tiny anti-z-fight gap. */
    private static final float FACE_OFFSET = 0.13F;

    private final Font font;

    public AdBoardRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
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
        Component text = Component.literal(raw);

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.5D, 0.5D);
        // JOML 1.10.5 (MC 1.20.1): setAngleAxis takes radians, same pattern as SoccerBallRenderer.
        poseStack.mulPose(new Quaternionf().setAngleAxis(
                (float) Math.toRadians(yaw), 0.0F, 1.0F, 0.0F));
        poseStack.translate(0.0D, 0.0D, FACE_OFFSET);
        poseStack.scale(TEXT_SCALE, TEXT_SCALE, TEXT_SCALE);

        float x = -font.width(text) / 2.0F;
        float y = -FONT_LINE_HEIGHT / 2.0F;
        font.drawInBatch(text, x, y, COLOR_WHITE, false, poseStack.last().pose(),
                bufferSource, Font.DisplayMode.NORMAL, 0, packedLight);

        poseStack.popPose();
    }
}
