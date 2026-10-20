package com.sportcraft.network;

import com.sportcraft.SportCraft;
import com.sportcraft.network.network_packet.JoinTeamC2S;
import com.sportcraft.network.network_packet.MatchControlC2S;
import com.sportcraft.network.network_packet.MatchStateS2C;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Registration point for the SportCraft SimpleChannel and all of its packets.
 */
public final class ModNetwork {

    private static final String PROTOCOL_VERSION = "1";

    public static SimpleChannel CHANNEL;

    private ModNetwork() {
    }

    public static void register(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            CHANNEL = NetworkRegistry.ChannelBuilder
                    .named(new ResourceLocation(SportCraft.MOD_ID, "main"))
                    .networkProtocolVersion(() -> PROTOCOL_VERSION)
                    .clientAcceptedVersions(PROTOCOL_VERSION::equals)
                    .serverAcceptedVersions(PROTOCOL_VERSION::equals)
                    .simpleChannel();

            int id = 0;
            CHANNEL.messageBuilder(JoinTeamC2S.class, id++)
                    .encoder(JoinTeamC2S::encode)
                    .decoder(JoinTeamC2S::new)
                    .consumerMainThread(JoinTeamC2S::handle)
                    .add();
            CHANNEL.messageBuilder(MatchControlC2S.class, id++)
                    .encoder(MatchControlC2S::encode)
                    .decoder(MatchControlC2S::new)
                    .consumerMainThread(MatchControlC2S::handle)
                    .add();
            CHANNEL.messageBuilder(MatchStateS2C.class, id++)
                    .encoder(MatchStateS2C::encode)
                    .decoder(MatchStateS2C::new)
                    .consumerMainThread(MatchStateS2C::handle)
                    .add();
        });
    }
}
