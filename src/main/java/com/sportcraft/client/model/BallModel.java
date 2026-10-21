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
 * Single 8x8x8 pixel cube (half a block) textured with the soccer ball texture.
 * Rolling rotation is applied by the renderer.
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
        rootPart.addOrReplaceChild("ball",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -4.0F, -4.0F, 8.0F, 8.0F, 8.0F),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 16, 16);
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
