package com.sportcraft.event;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sportcraft.match.MatchManager;
import com.sportcraft.match.Team;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

/**
 * {@code /sportcraft ...} server commands. Match control requires permission
 * level 2; team selection is available to every player.
 */
public class SportCraftCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sportcraft")
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
            source.sendFailure(Component.translatable("command.sportcraft.unknown_team", teamName));
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        MatchManager manager = MatchManager.get((ServerLevel) player.level());
        manager.setTeam(player.getUUID(), team);
        manager.broadcast(player.server);
        source.sendSuccess(() -> Component.translatable("command.sportcraft.joined",
                Component.translatable(team.translationKey())), false);
        return 1;
    }

    private static int startMatch(CommandSourceStack source, int seconds) {
        MatchManager manager = MatchManager.get(source.getLevel().getServer().overworld());
        manager.start(seconds);
        manager.broadcast(source.getServer());
        source.sendSuccess(() -> Component.translatable("command.sportcraft.started"), true);
        return 1;
    }

    private static int pauseMatch(CommandSourceStack source) {
        MatchManager manager = MatchManager.get(source.getLevel().getServer().overworld());
        manager.pause();
        manager.broadcast(source.getServer());
        source.sendSuccess(() -> Component.translatable("command.sportcraft.paused"), true);
        return 1;
    }

    private static int resumeMatch(CommandSourceStack source) {
        MatchManager manager = MatchManager.get(source.getLevel().getServer().overworld());
        manager.resume();
        manager.broadcast(source.getServer());
        source.sendSuccess(() -> Component.translatable("command.sportcraft.resumed"), true);
        return 1;
    }

    private static int resetMatch(CommandSourceStack source) {
        MatchManager manager = MatchManager.get(source.getLevel().getServer().overworld());
        manager.reset();
        manager.broadcast(source.getServer());
        source.sendSuccess(() -> Component.translatable("command.sportcraft.reset"), true);
        return 1;
    }
}
