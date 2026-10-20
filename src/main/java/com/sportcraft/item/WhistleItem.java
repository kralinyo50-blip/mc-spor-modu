package com.sportcraft.item;

import com.sportcraft.client.ClientScreenOpener;
import com.sportcraft.registry.ModSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * Referee whistle: opens the match management screen on the client and plays a
 * whistle sound on the server. The screen itself only sends validated packets.
 */
public class WhistleItem extends Item {

    public WhistleItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientScreenOpener::openMatchScreen);
        } else {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    ModSounds.WHISTLE.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }
}
