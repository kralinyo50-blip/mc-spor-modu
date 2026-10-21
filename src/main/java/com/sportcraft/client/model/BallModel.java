package com.sportcraft.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sportcraft.entity.SoccerBallEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;

/**
 * Near-perfect 16-pixel sphere built from sixteen one-pixel-tall bands.
 * Instead of hand-picked band widths, each band's half-size is derived from
 * the circle equation {@code half = round(sqrt(r^2 - dy^2))} with an 8-pixel
 * radius, so the silhouette reads as a genuinely round ball from every
 * angle (the previous seven-band version looked visibly faceted).
 *
 * <p>The model spans a full block (16 px) centred on the origin; the renderer
 * aligns that centre with the smaller physics collision box. Texture size is
 * 64x64 to give the denser bands enough UV resolution.</p>
 */
public class BallModel extends EntityModel<SoccerBallEntity> {

    public static final int BAND_COUNT = 16;
    public static final int RADIUS_PIXELS = 8;

    private final ModelPart ball;

    public BallModel() {
        super(RenderType::entityCutoutNoCull);
        this.ball = createBodyLayer().bakeRoot().getChild("ball");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition rootPart = mesh.getRoot();
        PartDefinition ballPart = rootPart.addOrReplaceChild("ball",
                CubeListBuilder.create(), PartPose.ZERO);

        for (int band = 0; band < BAND_COUNT; band++) {
            // Distance of the band's centre from the sphere's equator.
            double centerOffset = band - (BAND_COUNT / 2.0D) + 0.5D;
            double inside = (double) RADIUS_PIXELS * RADIUS_PIXELS - centerOffset * centerOffset;
            int half = Math.max(1, (int) Math.round(Math.sqrt(Math.max(0.0D, inside))));
            int y = -RADIUS_PIXELS + band;
            CubeListBuilder cubes = CubeListBuilder.create().texOffs(0, 0)
                    .addBox(-half, y, -half, half * 2, 1, half * 2);
            ballPart.addOrReplaceChild("band_" + band, cubes, PartPose.ZERO);
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(SoccerBallEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        // Animation is driven directly in the renderer from movement data.
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer consumer, int packedLight,
                               int packedOverlay, float red, float green, float blue, float alpha) {
        ball.render(poseStack, consumer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
