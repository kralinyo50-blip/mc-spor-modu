package com.mcsport.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mcsport.entity.SoccerBallEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Genuine UV sphere rendered directly as a latitude/longitude quad grid
 * instead of stacked cube bands, so the ball reads as perfectly round from
 * every angle with no visible band steps. Vertices carry proper spherical
 * normals and continuous equirectangular UVs, which lets the classic
 * black-pentagon soccer texture wrap without seams or stretching.
 *
 * <p>Native model radius is half a block; the renderer scales the sphere to
 * the entity's smaller physics radius so the visual matches the hitbox.</p>
 */
public class BallModel extends EntityModel<SoccerBallEntity> {

    public static final float NATIVE_RADIUS = 0.5F;
    private static final int LATITUDE_RINGS = 20;
    private static final int LONGITUDE_SECTORS = 32;

    public BallModel() {
        super(RenderType::entityCutoutNoCull);
    }

    @Override
    public void setupAnim(SoccerBallEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        // Rolling is driven in the renderer.
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer consumer, int packedLight,
                               int packedOverlay, float red, float green, float blue, float alpha) {
        PoseStack.Pose pose = poseStack.last();
        for (int ring = 0; ring < LATITUDE_RINGS; ring++) {
            float lat0 = (float) (-Math.PI / 2.0D + Math.PI * ring / LATITUDE_RINGS);
            float lat1 = (float) (-Math.PI / 2.0D + Math.PI * (ring + 1) / LATITUDE_RINGS);
            float v0 = (float) ring / LATITUDE_RINGS;
            float v1 = (float) (ring + 1) / LATITUDE_RINGS;
            for (int sector = 0; sector < LONGITUDE_SECTORS; sector++) {
                float lon0 = (float) (2.0D * Math.PI * sector / LONGITUDE_SECTORS);
                float lon1 = (float) (2.0D * Math.PI * (sector + 1) / LONGITUDE_SECTORS);
                float u0 = (float) sector / LONGITUDE_SECTORS;
                float u1 = (float) (sector + 1) / LONGITUDE_SECTORS;
                // One quad per grid cell, emitted as four finished vertices.
                emitVertex(pose, consumer, packedLight, red, green, blue, alpha, lat0, lon0, u0, v0);
                emitVertex(pose, consumer, packedLight, red, green, blue, alpha, lat0, lon1, u1, v0);
                emitVertex(pose, consumer, packedLight, red, green, blue, alpha, lat1, lon1, u1, v1);
                emitVertex(pose, consumer, packedLight, red, green, blue, alpha, lat1, lon0, u0, v1);
            }
        }
    }

    private void emitVertex(PoseStack.Pose pose, VertexConsumer consumer, int packedLight,
                            float red, float green, float blue, float alpha,
                            float latitude, float longitude, float u, float v) {
        float cosLat = (float) Math.cos(latitude);
        float nx = cosLat * (float) Math.cos(longitude);
        float ny = (float) Math.sin(latitude);
        float nz = cosLat * (float) Math.sin(longitude);
        consumer.vertex(pose.pose(), nx * NATIVE_RADIUS, ny * NATIVE_RADIUS, nz * NATIVE_RADIUS)
                .color(red, green, blue, alpha)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(packedLight)
                .normal(pose.normal(), nx, ny, nz)
                .endVertex();
    }
}
