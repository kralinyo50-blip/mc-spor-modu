package com.sportcraft.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Referee card. Shows a whistle + chat/actionbar message on use. Colour is
 * driven by the registry name so the model and message stay consistent.
 */
public class CardItem extends Item {

    private final boolean red;

    public CardItem(Properties properties, boolean red) {
        super(properties);
        this.red = red;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.get(),
                    SoundSource.PLAYERS, 0.8F, red ? 0.6F : 1.2F);
            Component message = Component.translatable(red
                    ? "message.sportcraft.red_card" : "message.sportcraft.yellow_card",
                    player.getDisplayName())
                    .withStyle(red ? ChatFormatting.RED : ChatFormatting.YELLOW);
            player.displayClientMessage(message, true);
        }
        return InteractionResultHolder.success(stack);
    }
}
