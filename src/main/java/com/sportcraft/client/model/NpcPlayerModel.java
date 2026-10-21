package com.sportcraft.client.model;

import com.sportcraft.entity.SoccerNpcEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Vanilla player-shaped model with three SportCraft limb animations layered
 * on top of the normal walk cycle, driven by the entity's synched action
 * values so every client renders the same pose:
 *
 * <ul>
 *   <li><b>Slide tackle</b> — body leaned back, legs thrust forward, arms
 *       spread while {@link SoccerNpcEntity#isSliding()} is active.</li>
 *   <li><b>Basketball dribble</b> — fast alternating arm pumps and a slight
 *       body sway while {@link SoccerNpcEntity#isDribbling()} is active.</li>
 *   <li><b>Referee signal</b> — one arm raised straight up while the referee
 *       communicates a decision.</li>
 * </ul>
 */
public class NpcPlayerModel extends HumanoidModel<SoccerNpcEntity> {

    public NpcPlayerModel(ModelPart root) {
        super(root);
    }

    @Override
    public void setupAnim(SoccerNpcEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

        if (entity.isSliding()) {
            // Reclined slide: torso back, legs extended toward the ball.
            body.xRot = -1.15F;
            head.xRot = 0.4F;
            rightLeg.xRot = -0.85F;
            leftLeg.xRot = -0.7F;
            rightLeg.yRot = 0.18F;
            leftLeg.yRot = -0.18F;
            rightArm.xRot = 0.7F;
            leftArm.xRot = 0.7F;
            rightArm.zRot = 0.5F;
            leftArm.zRot = -0.5F;
        } else if (entity.isDribbling()) {
            // Alternating dribble-arm pumps with a small protective body sway.
            float phase = ageInTicks * 0.9F;
            rightArm.xRot = Mth.cos(phase) * 1.05F - 0.35F;
            leftArm.xRot = Mth.cos(phase + (float) Math.PI) * 1.05F - 0.35F;
            rightArm.zRot = 0.12F;
            leftArm.zRot = -0.12F;
            body.yRot = Mth.sin(phase * 0.5F) * 0.08F;
            body.xRot = 0.12F;
        } else if (entity.getSignalTicks() > 0) {
            // Referee decision: arm raised vertically.
            rightArm.xRot = -2.9F;
            rightArm.zRot = 0.05F;
        }
    }
}
