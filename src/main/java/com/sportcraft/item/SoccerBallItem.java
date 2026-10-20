package com.sportcraft.item;

import com.sportcraft.entity.SoccerBallEntity;
import com.sportcraft.registry.ModEntities;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Hold right click to charge, release to throw a {@link SoccerBallEntity}.
 * A quick tap produces a gentle throw; a full charge produces a powerful one.
 * The charge ratio is rendered by the client HUD.
 */
public class SoccerBallItem extends Item {

    /** Ticks of use required to reach full power. */
    public static final int CHARGE_TICKS = 25;
    private static final int USE_DURATION = 72000;

    public SoccerBallItem(Properties properties) {
        super(properties);
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
    public void releaseUsing(ItemStack stack, Level level, net.minecraft.world.entity.LivingEntity entity,
                             int timeLeft) {
        if (level.isClientSide || !(entity instanceof Player player)) {
            return;
        }
        int usedTicks = getUseDuration(stack) - timeLeft;
        float power = Mth.clamp(usedTicks / (float) CHARGE_TICKS, 0.15F, 1.0F);

        SoccerBallEntity ball = ModEntities.SOCCER_BALL.get().create(level);
        if (ball == null) {
            return;
        }
        ball.moveTo(player.getX(), player.getEyeY() - 0.25D, player.getZ(),
                player.getYRot(), player.getXRot());
        ball.launchFrom(player, power);
        level.addFreshEntity(ball);

        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
    }

    /** 0..1 charge ratio for the HUD power bar. */
    public static float getChargeRatio(Player player) {
        ItemStack stack = player.getUseItem();
        if (!(stack.getItem() instanceof SoccerBallItem)) {
            return 0.0F;
        }
        int usedTicks = stack.getUseDuration() - player.getUseItemRemainingTicks();
        return Mth.clamp(usedTicks / (float) CHARGE_TICKS, 0.0F, 1.0F);
    }
}
