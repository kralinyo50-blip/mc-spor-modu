package com.mcsport.network.network_packet;

import com.mcsport.match.MatchManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client reports its selected UI language code (for example "tr_tr" or
 * "en_us"). The server stores only the normalised tr/en preference used to
 * name the generated bot squads. The vanilla connection locale is not part
 * of the public Mojang-mapping API on 1.20.1, so the mod syncs the value
 * explicitly over its own channel instead of touching listener internals.
 */
public class LanguageC2S {

    private static final int MAX_CODE_LENGTH = 16;

    private final String locale;

    public LanguageC2S(String locale) {
        this.locale = locale;
    }

    public LanguageC2S(FriendlyByteBuf buffer) {
        this.locale = buffer.readUtf(MAX_CODE_LENGTH);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(locale, MAX_CODE_LENGTH);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            // A player can only announce its own locale; nothing privileged
            // happens here, so an op-level check is not required.
            if (player == null || locale == null || locale.isBlank()) {
                return;
            }
            MatchManager.get((ServerLevel) player.level()).noteLanguage(locale);
        });
        context.setPacketHandled(true);
    }
}
