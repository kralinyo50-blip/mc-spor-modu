package com.sportcraft.client.model;

import com.sportcraft.entity.SoccerNpcEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Vanilla player-shaped model with SportCraft limb animations layered on top
 * of the normal walk cycle, driven by the entity's synched action values so
 * every client renders the same pose:
 *
 * <ul>
 *   <li><b>Slide tackle</b> — body leaned back, legs thrust forward, arms spread.</li>
 *   <li><b>Goalkeeper saves</b> — horizontal flying dive, two-handed catch and
 *       a one-arm punch, each with its own synched action byte.</li>
 *   <li><b>Referee cards</b> — the card arm raised high while the item is held.</li>
 *   <li><b>Referee signal</b> — one arm raised straight up.</li>
 *   <li><b>Free-kick wall</b> — braced crouch with arms crossed over the chest.</li>
 *   <li><b>Kick wind-up</b> — a back-to-front leg swing before the strike.</li>
 *   <li><b>Basketball dribble</b> — fast alternating arm pumps and body sway.</li>
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

        // Full-time wave takes priority over every gameplay pose: the squad
        // stands still and waves goodbye before the match manager despawns it.
        if (entity.isWaving()) {
            float wave = Mth.sin(ageInTicks * 0.6F) * 0.45F;
            rightArm.xRot = -2.7F;
            rightArm.zRot = 0.15F + wave;
            leftArm.xRot = 0.3F;
            leftArm.zRot = 0.0F;
            rightLeg.xRot = 0.0F;
            leftLeg.xRot = 0.0F;
            body.xRot = 0.0F;
            body.yRot = 0.0F;
            head.xRot = 0.0F;
            return;
        }

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
        } else if (entity.isKeeperSaving()) {
            applyKeeperSave(entity);
        } else if (entity.isShowingCard()) {
            // Hold the card arm up high; the card item rides in this hand.
            rightArm.xRot = -2.5F;
            rightArm.zRot = 0.15F;
            leftArm.xRot = 0.35F;
            body.xRot = 0.1F;
        } else if (entity.getSignalTicks() > 0) {
            // Referee decision: arm raised vertically.
            rightArm.xRot = -2.9F;
            rightArm.zRot = 0.05F;
        } else if (entity.isInWall()) {
            // Defensive wall: braced, slightly crouched, arms across the body.
            body.xRot = 0.28F;
            head.xRot = -0.15F;
            rightLeg.xRot = 0.32F;
            leftLeg.xRot = 0.32F;
            rightArm.xRot = -0.95F;
            leftArm.xRot = -0.95F;
            rightArm.zRot = 0.45F;
            leftArm.zRot = -0.45F;
        } else if (entity.isWindingUp()) {
            // Leg swing from behind (wind-up) to the front (strike moment).
            float phase = 1.0F - (entity.getKickWindupTicks()
                    / (float) SoccerNpcEntity.KICK_WINDUP_DURATION_TICKS);
            rightLeg.xRot = Mth.lerp(phase, 0.95F, -1.15F);
            leftLeg.xRot = Mth.lerp(phase, -0.25F, 0.35F);
            rightArm.xRot = Mth.lerp(phase, -0.4F, 0.7F);
            leftArm.xRot = Mth.lerp(phase, 0.5F, -0.5F);
            body.xRot = 0.18F;
        } else if (entity.isDribbling()) {
            // Alternating dribble-arm pumps with a small protective body sway.
            float phase = ageInTicks * 0.9F;
            rightArm.xRot = Mth.cos(phase) * 1.05F - 0.35F;
            leftArm.xRot = Mth.cos(phase + (float) Math.PI) * 1.05F - 0.35F;
            rightArm.zRot = 0.12F;
            leftArm.zRot = -0.12F;
            body.yRot = Mth.sin(phase * 0.5F) * 0.08F;
            body.xRot = 0.12F;
        }
    }

    /** Dive, catch and punch share a flying torso but differ in arm work. */
    private void applyKeeperSave(SoccerNpcEntity entity) {
        byte action = entity.getKeeperAction();
        // Nearly horizontal airborne torso, head tilted to track the ball.
        body.xRot = -1.35F;
        head.xRot = 0.55F;
        // Trailing legs.
        rightLeg.xRot = 0.55F;
        leftLeg.xRot = 0.4F;
        rightLeg.yRot = 0.22F;
        leftLeg.yRot = -0.22F;
        if (action == SoccerNpcEntity.KEEPER_CATCH) {
            // Both gloves stretched forward together to collect the ball.
            rightArm.xRot = -1.75F;
            leftArm.xRot = -1.75F;
            rightArm.zRot = 0.22F;
            leftArm.zRot = -0.22F;
        } else if (action == SoccerNpcEntity.KEEPER_PUNCH) {
            // One fist punched fully forward, the other tucked in.
            rightArm.xRot = -2.35F;
            rightArm.zRot = 0.05F;
            leftArm.xRot = -0.6F;
            leftArm.zRot = -0.4F;
        } else {
            // Dive: arms spread wide while flying across the goal.
            rightArm.xRot = -1.35F;
            leftArm.xRot = -1.35F;
            rightArm.zRot = 0.75F;
            leftArm.zRot = -0.75F;
        }
    }
}
