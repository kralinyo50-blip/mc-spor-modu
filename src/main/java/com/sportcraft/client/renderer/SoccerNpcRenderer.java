package com.sportcraft.client.renderer;

import com.sportcraft.entity.SoccerNpcEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders AI players with the vanilla player model and default skin, plus the
 * armor layer so their dyed leather team kits are visible.
 */
public class SoccerNpcRenderer extends HumanoidMobRenderer<SoccerNpcEntity, HumanoidModel<SoccerNpcEntity>> {

    public SoccerNpcRenderer(EntityRendererProvider.Context context) {
        super(context,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)),
                0.5F);
        HumanoidModel<SoccerNpcEntity> innerArmor =
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR));
        HumanoidModel<SoccerNpcEntity> outerArmor =
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR));
        this.addLayer(new HumanoidArmorLayer<SoccerNpcEntity, HumanoidModel<SoccerNpcEntity>,
                HumanoidModel<SoccerNpcEntity>>(this, innerArmor, outerArmor, context.getModelManager()));
    }

    @Override
    public ResourceLocation getTextureLocation(SoccerNpcEntity entity) {
        return DefaultPlayerSkin.getDefaultSkin();
    }
}
