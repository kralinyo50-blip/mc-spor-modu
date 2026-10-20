package com.sportcraft.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Vector3f;
import com.sportcraft.SportCraft;
import com.sportcraft.client.model.BallModel;
import com.sportcraft.entity.SoccerBallEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Renders the ball with a rolling rotation whose axis is perpendicular to the
 * horizontal travel direction, so the ball visually tumbles instead of sliding.
 */
public class SoccerBallRenderer extends EntityRenderer<SoccerBallEntity> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(SportCraft.MOD_ID, "textures/entity/soccer_ball.png");

    private final BallModel model;

    public SoccerBallRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new BallModel();
        this.shadowRadius = 0.25F;
    }

    @Override
    public void render(SoccerBallEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();
        // Entity origin is at the feet; the 0.5-block cube must be centred vertically.
        poseStack.translate(0.0D, 0.25D, 0.0D);

        float yaw = entity.getVisualYaw();
        float roll = entity.getVisualRoll(partialTick);
        float axisX = Mth.cos((float) Math.toRadians(yaw));
        float axisZ = -Mth.sin((float) Math.toRadians(yaw));
        poseStack.mulPose(new Vector3f(axisX, 0.0F, axisZ).rotationDegrees(roll));

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        model.renderToStack(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY,
                1.0F, 1.0F, 1.0F, 1.0F);
        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(SoccerBallEntity entity) {
        return TEXTURE;
    }
}
