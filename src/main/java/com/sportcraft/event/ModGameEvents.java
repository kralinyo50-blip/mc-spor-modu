package com.sportcraft.event;

import com.sportcraft.entity.SoccerBallEntity;
import com.sportcraft.match.MatchManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Forge game-bus handlers: ball interaction, server clock driving and
 * command registration. Everything that changes match state runs server-side.
 */
public class ModGameEvents {

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getTarget() instanceof SoccerBallEntity ball)) {
            return;
        }
        event.setCanceled(true);
        Player player = event.getEntity();
        // Keep the attack indicator in sync on both sides.
        player.resetAttackStrengthTicker();
        if (player.level().isClientSide || ball.getKickCooldown() > 0) {
            return;
        }
        // Attack cooldown decides kick strength; sprinting turns it into a power shot.
        float power = player.getAttackStrengthScale(0.0F);
        if (player.isSprinting()) {
            power *= 1.4F;
        }
        ball.kick(player, power);
        player.swing(InteractionHand.MAIN_HAND);
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof SoccerBallEntity ball)
                || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        event.setCanceled(true);
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            return;
        }
        if (player.isSecondaryUseActive()) {
            ball.pickUp(player);
        } else {
            ball.softTouch(player);
            player.swing(InteractionHand.MAIN_HAND);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer() == null) {
            return;
        }
        ServerLevel overworld = event.getServer().getLevel(Level.OVERWORLD);
        if (overworld != null) {
            MatchManager.get(overworld).serverTick(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MatchManager.get(player.serverLevel()).syncToPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        SportCraftCommand.register(event.getDispatcher());
    }
}
