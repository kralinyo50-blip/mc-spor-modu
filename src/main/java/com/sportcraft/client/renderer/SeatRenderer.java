package com.sportcraft.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sportcraft.entity.SeatEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * Seat mounts are invisible: no model, no shadow. The chair block underneath
 * is the only visible part.
 */
public class SeatRenderer extends EntityRenderer<SeatEntity> {

    public SeatRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(SeatEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        // Deliberately empty.
    }

    @Override
    public ResourceLocation getTextureLocation(SeatEntity entity) {
        return new ResourceLocation("minecraft", "textures/misc/white.png");
    }
}
