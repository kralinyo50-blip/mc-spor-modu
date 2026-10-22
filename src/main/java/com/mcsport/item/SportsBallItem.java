package com.mcsport.item;

import com.mcsport.entity.SoccerBallEntity;
import com.mcsport.match.BallType;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * One item class for every ball sport. The stored {@link BallType} decides
 * which texture the item uses and which physics profile the spawned entity gets.
 *
 * <p>Hold right click to charge, release to throw — a quick tap is a gentle
 * throw, a full charge a powerful one. The HUD renders the charge ratio.</p>
 */
public class SportsBallItem extends Item {

    /** Ticks of use required to reach full power. */
    public static final int CHARGE_TICKS = 25;
    private static final int USE_DURATION = 72000;
    private static final double MAX_THROW_SPEED = 1.6D;

    private final BallType ballType;

    public SportsBallItem(Properties properties, BallType ballType) {
        super(properties);
        this.ballType = ballType;
    }

    public BallType getBallType() {
        return ballType;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return USE_DURATION;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (level.isClientSide || !(entity instanceof Player player)) {
            return;
        }
        int usedTicks = getUseDuration(stack) - timeLeft;
        float power = Mth.clamp(usedTicks / (float) CHARGE_TICKS, 0.15F, 1.0F);
        Vec3 look = player.getLookAngle();
        Vec3 spawnPos = new Vec3(player.getX(), player.getEyeY() - 0.25D, player.getZ());
        SoccerBallEntity ball = SoccerBallEntity.spawn(level, spawnPos, ballType);
        ball.setDeltaMovement(look.scale(MAX_THROW_SPEED * power));
        // Strafe input while throwing bends the airborne ball (falso).
        ball.applyPlayerSpin(player);

        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
    }

    /** 0..1 charge ratio for the HUD power bar. */
    public static float getChargeRatio(Player player) {
        ItemStack stack = player.getUseItem();
        if (!(stack.getItem() instanceof SportsBallItem)) {
            return 0.0F;
        }
        int usedTicks = stack.getUseDuration() - player.getUseItemRemainingTicks();
        return Mth.clamp(usedTicks / (float) CHARGE_TICKS, 0.0F, 1.0F);
    }
}
