package com.mcsport.network;

import com.mcsport.McSport;
import com.mcsport.network.network_packet.BuildFieldC2S;
import com.mcsport.network.network_packet.JoinTeamC2S;
import com.mcsport.network.network_packet.LanguageC2S;
import com.mcsport.network.network_packet.MatchControlC2S;
import com.mcsport.network.network_packet.MatchSettingsC2S;
import com.mcsport.network.network_packet.MatchStateS2C;
import com.mcsport.network.network_packet.RequestPassC2S;
import com.mcsport.network.network_packet.SlideTackleC2S;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Registration point for the McSport SimpleChannel and all of its packets.
 */
public final class ModNetwork {

    // "6" adds the LanguageC2S client-locale sync; client and server accept
    // only this exact version.
    private static final String PROTOCOL_VERSION = "6";

    public static SimpleChannel CHANNEL;

    private ModNetwork() {
    }

    public static void register(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            CHANNEL = NetworkRegistry.ChannelBuilder
                    .named(new ResourceLocation(McSport.MOD_ID, "main"))
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
            CHANNEL.messageBuilder(MatchSettingsC2S.class, id++)
                    .encoder(MatchSettingsC2S::encode)
                    .decoder(MatchSettingsC2S::new)
                    .consumerMainThread(MatchSettingsC2S::handle)
                    .add();
            CHANNEL.messageBuilder(BuildFieldC2S.class, id++)
                    .encoder(BuildFieldC2S::encode)
                    .decoder(BuildFieldC2S::new)
                    .consumerMainThread(BuildFieldC2S::handle)
                    .add();
            CHANNEL.messageBuilder(SlideTackleC2S.class, id++)
                    .encoder(SlideTackleC2S::encode)
                    .decoder(SlideTackleC2S::new)
                    .consumerMainThread(SlideTackleC2S::handle)
                    .add();
            CHANNEL.messageBuilder(RequestPassC2S.class, id++)
                    .encoder(RequestPassC2S::encode)
                    .decoder(RequestPassC2S::new)
                    .consumerMainThread(RequestPassC2S::handle)
                    .add();
            CHANNEL.messageBuilder(LanguageC2S.class, id++)
                    .encoder(LanguageC2S::encode)
                    .decoder(LanguageC2S::new)
                    .consumerMainThread(LanguageC2S::handle)
                    .add();
        });
    }
}
