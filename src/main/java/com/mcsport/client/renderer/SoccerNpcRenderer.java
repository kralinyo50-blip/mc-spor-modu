package com.mcsport.client.renderer;

import com.mcsport.client.model.NpcPlayerModel;
import com.mcsport.entity.SoccerNpcEntity;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders AI players (and the referee) with the vanilla player mesh wrapped
 * in {@link NpcPlayerModel} so the slide-tackle, dribble and referee-signal
 * limb animations play. The armor layer keeps the dyed leather team kits
 * and the referee's yellow shirt visible.
 */
public class SoccerNpcRenderer extends HumanoidMobRenderer<SoccerNpcEntity, NpcPlayerModel> {

    public SoccerNpcRenderer(EntityRendererProvider.Context context) {
        super(context,
                new NpcPlayerModel(context.bakeLayer(ModelLayers.PLAYER)),
                0.5F);
        NpcPlayerModel innerArmor =
                new NpcPlayerModel(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR));
        NpcPlayerModel outerArmor =
                new NpcPlayerModel(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR));
        this.addLayer(new HumanoidArmorLayer<SoccerNpcEntity, NpcPlayerModel, NpcPlayerModel>(
                this, innerArmor, outerArmor, context.getModelManager()));
    }

    @Override
    public ResourceLocation getTextureLocation(SoccerNpcEntity entity) {
        return DefaultPlayerSkin.getDefaultSkin();
    }
}
