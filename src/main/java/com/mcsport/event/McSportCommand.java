package com.mcsport.event;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mcsport.advancement.ModCriteriaTriggers;
import com.mcsport.match.MatchManager;
import com.mcsport.match.MatchState;
import com.mcsport.match.Team;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

/**
 * {@code /mcsport ...} server commands. Match control requires permission
 * level 2; team selection is available to every player.
 */
public class McSportCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mcsport")
                .then(Commands.literal("join")
                        .then(Commands.argument("team", StringArgumentType.word())
                                .suggests((context, builder) ->
                                        SharedSuggestionProvider.suggest(new String[]{"red", "blue", "spectator"}, builder))
                                .executes(context -> joinTeam(context.getSource(),
                                        StringArgumentType.getString(context, "team")))))
                .then(Commands.literal("start")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("minutes", IntegerArgumentType.integer(1, 120))
                                .executes(context -> startMatch(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "minutes") * 60)))
                        .executes(context -> startMatch(context.getSource(), 300)))
                .then(Commands.literal("pause")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> pauseMatch(context.getSource())))
                .then(Commands.literal("resume")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> resumeMatch(context.getSource())))
                .then(Commands.literal("reset")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> resetMatch(context.getSource()))));
    }

    private static int joinTeam(CommandSourceStack source, String teamName) throws CommandSyntaxException {
        Team team;
        try {
            team = Team.valueOf(teamName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.translatable("command.mcsport.unknown_team", teamName));
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        MatchManager manager = MatchManager.get((ServerLevel) player.level());
        manager.setTeam(player.getUUID(), team);
        // Command joins never carry the keeper flag from the match GUI.
        manager.setKeeper(player.getUUID(), false);
        if (team != Team.SPECTATOR) {
            ModCriteriaTriggers.TEAM_JOIN.trigger(player, 1);
        }
        manager.broadcast(player.server);
        source.sendSuccess(() -> Component.translatable("command.mcsport.joined",
                Component.translatable(team.translationKey())), false);
        return 1;
    }

    private static int startMatch(CommandSourceStack source, int seconds) {
        ServerLevel overworld = source.getLevel().getServer().overworld();
        MatchManager manager = MatchManager.get(overworld);
        if (!manager.isFieldBuilt()) {
            source.sendFailure(Component.translatable("command.mcsport.no_field"));
            return 0;
        }
        manager.start(seconds);
        if (manager.getState() == MatchState.RUNNING) {
            MatchManager.PopulateResult result = manager.populateNpcs(overworld, source.getServer());
            Component referee = Component.translatable(result.referee()
                    ? "message.mcsport.referee_present"
                    : "message.mcsport.referee_missing");
            source.sendSuccess(() -> Component.translatable("message.mcsport.teams_ready",
                    result.redBots() + result.redPlayers(),
                    result.blueBots() + result.bluePlayers(),
                    referee), true);
        }
        manager.broadcast(source.getServer());
        source.sendSuccess(() -> Component.translatable("command.mcsport.started"), true);
        return 1;
    }

    private static int pauseMatch(CommandSourceStack source) {
        MatchManager manager = MatchManager.get(source.getLevel().getServer().overworld());
        manager.pause();
        manager.broadcast(source.getServer());
        source.sendSuccess(() -> Component.translatable("command.mcsport.paused"), true);
        return 1;
    }

    private static int resumeMatch(CommandSourceStack source) {
        MatchManager manager = MatchManager.get(source.getLevel().getServer().overworld());
        manager.resume();
        manager.broadcast(source.getServer());
        source.sendSuccess(() -> Component.translatable("command.mcsport.resumed"), true);
        return 1;
    }

    private static int resetMatch(CommandSourceStack source) {
        ServerLevel overworld = source.getLevel().getServer().overworld();
        MatchManager manager = MatchManager.get(overworld);
        manager.clearNpcs(overworld);
        manager.reset();
        manager.broadcast(source.getServer());
        source.sendSuccess(() -> Component.translatable("command.mcsport.reset"), true);
        return 1;
    }
}
