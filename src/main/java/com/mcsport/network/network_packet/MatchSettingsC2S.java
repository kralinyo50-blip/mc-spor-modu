package com.mcsport.network.network_packet;

import com.mcsport.match.BallType;
import com.mcsport.match.Club;
import com.mcsport.match.DrawRule;
import com.mcsport.match.FieldSize;
import com.mcsport.match.Formation;
import com.mcsport.match.MatchDifficulty;
import com.mcsport.match.MatchManager;
import com.mcsport.match.MatchSettings;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client sends the full setup-GUI settings object. Only operators (level 2)
 * may change settings; every numeric field is clamped again on the server.
 */
public class MatchSettingsC2S {

    private final MatchSettings settings;

    public MatchSettingsC2S(MatchSettings settings) {
        this.settings = settings;
    }

    public MatchSettingsC2S(FriendlyByteBuf buffer) {
        MatchSettings value = new MatchSettings();
        value.setSport(BallType.byOrdinal(buffer.readByte()));
        value.setFieldSize(FieldSize.byOrdinal(buffer.readByte()));
        value.setTeamSize(buffer.readByte());
        value.setDifficulty(MatchDifficulty.byOrdinal(buffer.readByte()));
        value.setHalfMinutes(buffer.readByte());
        value.setDrawRule(DrawRule.byOrdinal(buffer.readByte()));
        value.setHomeFormation(Formation.byOrdinal(buffer.readByte()));
        value.setAwayFormation(Formation.byOrdinal(buffer.readByte()));
        value.setHomeClub(Club.byOrdinal(buffer.readByte()));
        value.setAwayClub(Club.byOrdinal(buffer.readByte()));
        this.settings = value;
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeByte(settings.getSport().ordinal());
        buffer.writeByte(settings.getFieldSize().ordinal());
        buffer.writeByte(settings.getTeamSize());
        buffer.writeByte(settings.getDifficulty().ordinal());
        buffer.writeByte(settings.getHalfMinutes());
        buffer.writeByte(settings.getDrawRule().ordinal());
        buffer.writeByte(settings.getHomeFormation().ordinal());
        buffer.writeByte(settings.getAwayFormation().ordinal());
        buffer.writeByte(settings.getHomeClub().ordinal());
        buffer.writeByte(settings.getAwayClub().ordinal());
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !player.hasPermissions(2)) {
                return;
            }
            MatchManager manager = MatchManager.get((ServerLevel) player.level());
            manager.updateSettings(settings);
            manager.broadcast(player.server);
        });
        context.setPacketHandled(true);
    }
}
