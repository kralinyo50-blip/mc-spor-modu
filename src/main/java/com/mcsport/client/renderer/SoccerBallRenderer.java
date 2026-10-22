package com.mcsport.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Quaternionf;
import com.mcsport.McSport;
import com.mcsport.client.model.BallModel;
import com.mcsport.entity.SoccerBallEntity;
import com.mcsport.match.BallType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.EnumMap;
import java.util.Map;

/**
 * Renders the ball with a rolling rotation whose axis is perpendicular to the
 * horizontal travel direction, so the ball visually tumbles instead of sliding.
 * The texture is chosen from the entity's synched {@link BallType}.
 */
public class SoccerBallRenderer extends EntityRenderer<SoccerBallEntity> {

    private static final Map<BallType, ResourceLocation> TEXTURES = new EnumMap<>(BallType.class);

    static {
        for (BallType type : BallType.values()) {
            TEXTURES.put(type, new ResourceLocation(McSport.MOD_ID,
                    "textures/entity/" + type.getTextureName() + "_ball.png"));
        }
    }

    private final BallModel model;

    public SoccerBallRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new BallModel();
        this.shadowRadius = 0.2F;
    }

    @Override
    public void render(SoccerBallEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        ResourceLocation texture = getTextureLocation(entity);
        poseStack.pushPose();
        // Sphere model is centred on the origin; lift its centre to the
        // collision-radius height, then shrink the native 0.5 sphere to the
        // smaller physics radius so visual size matches the hitbox.
        poseStack.translate(0.0D, SoccerBallEntity.BALL_RADIUS, 0.0D);

        float yaw = entity.getVisualYaw();
        float roll = entity.getVisualRoll(partialTick);
        float axisX = Mth.cos((float) Math.toRadians(yaw));
        float axisZ = -Mth.sin((float) Math.toRadians(yaw));
        // JOML 1.10.5 (MC 1.20.1): setAngleAxis takes radians; same pattern as BoatRenderer.
        poseStack.mulPose(new Quaternionf().setAngleAxis(
                (float) Math.toRadians(roll), axisX, 0.0F, axisZ));
        float scale = (float) (SoccerBallEntity.BALL_RADIUS / BallModel.NATIVE_RADIUS);
        poseStack.scale(scale, scale, scale);

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(texture));
        model.renderToBuffer(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY,
                1.0F, 1.0F, 1.0F, 1.0F);
        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(SoccerBallEntity entity) {
        return TEXTURES.get(entity.getBallType());
    }
}
