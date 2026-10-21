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
 * Low-poly sphere built from seven stacked pixel bands, replacing the old
 * half-block cube so the ball reads as round like the reference mod.
 *
 * <p>Band sizes (diameter in pixels) from bottom to top: 6, 12, 14, 16, 14,
 * 12, 6 — centred on the origin so the renderer only translates the centre.</p>
 */
public class BallModel extends EntityModel<SoccerBallEntity> {

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

        // Each band: half-size, y start, height.
        addBand(ballPart, 3, -8, 2);
        addBand(ballPart, 6, -6, 2);
        addBand(ballPart, 7, -4, 2);
        addBand(ballPart, 8, -2, 4);
        addBand(ballPart, 7, 2, 2);
        addBand(ballPart, 6, 4, 2);
        addBand(ballPart, 3, 6, 2);
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static void addBand(PartDefinition parent, int half, int y, int height) {
        CubeListBuilder cubes = CubeListBuilder.create().texOffs(0, 0)
                .addBox(-half, y, -half, half * 2, height, half * 2);
        parent.addOrReplaceChild("band_" + y + "_" + half, cubes, PartPose.ZERO);
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
