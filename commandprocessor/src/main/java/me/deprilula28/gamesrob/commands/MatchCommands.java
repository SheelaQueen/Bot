package me.deprilula28.gamesrob.commands;

import me.deprilula28.gamesrob.Language;
import me.deprilula28.gamesrob.data.UserProfile;
import me.deprilula28.gamesrob.utility.Utility;
import me.deprilula28.gamesrob.baseFramework.GameState;
import me.deprilula28.gamesrob.baseFramework.GameType;
import me.deprilula28.gamesrob.baseFramework.Match;
import me.deprilula28.gamesrob.baseFramework.Player;
import me.deprilula28.jdacmdframework.CommandContext;
import me.deprilula28.jdacmdframework.exceptions.CommandArgsException;

public class MatchCommands {
    private static Match getGame(CommandContext context) {
        if (!Match.GAMES.containsKey(context.getChannel()))
            throw new CommandArgsException(Language.transl(context, "genericMessages.noGamesInChannel",
                    Utility.getPrefix(context.getGuild())));

        return Match.GAMES.get(context.getChannel());
    }

    public static String leave(CommandContext context) {
        Match game = getGame(context);
        if (game.getCreator().equals(context.getAuthor()))
            throw new CommandArgsException(Language.transl(context, "command.leave.creator",
                    Utility.getPrefix(context.getGuild())));
        if (!game.getPlayers().contains(Player.user(context.getAuthor())))
            throw new CommandArgsException(Language.transl(context, "command.leave.notInMatch"));
        if (game.getGame().getGameType().equals(GameType.COLLECTIVE))
            throw new CommandArgsException(Language.transl(context, "command.leave.cantLeave"));

        context.send(builder -> {
            builder.append(Language.transl(context, "command.leave.message", context.getAuthor().getAsMention()));
            game.left(Player.user(context.getAuthor()), builder);
        });
        return null;
    }

    public static String join(CommandContext context) {
        if (Match.PLAYING.containsKey(context.getAuthor()))
            return Language.transl(context, "genericMessages.alreadyPlaying",
                    Match.PLAYING.get(context.getAuthor()).getChannelIn().getAsMention(),
                    Utility.getPrefix(context.getGuild())
            );

        Match game = getGame(context);
        if (game.getGame().getGameType() == GameType.COLLECTIVE) return Language.transl(context, "command.join.collectiveMatch");
        if (game.getGameState() != GameState.PRE_GAME) return Language.transl(context, "command.join.gameStarted");
        if (game.getPlayers().contains(Player.user(context.getAuthor())))
            return Language.transl(context, "command.join.alreadyOnMatch");
        if (game.getBetting().isPresent() && !UserProfile.get(context.getAuthor()).transaction(game.getBetting().get(), "transactions.betting"))
            return Utility.getNotEnoughTokensMessage(context, game.getBetting().get());

        game.joined(Player.user(context.getAuthor()));
        return null;
    }

    public static String listPlayers(CommandContext context) {
        Match game = getGame(context);
        String language = Utility.getLanguage(context);
        StringBuilder builder = new StringBuilder(Language.transl(context, "command.listplayers.header",
                game.getGame().getName(language), game.getGame().getShortDescription(language)
        ));

        game.getPlayers().forEach(cur -> builder.append(cur.toString()).append("\n"));
        return builder.toString();
    }

    public static String stop(CommandContext context) {
        Match game = getGame(context);
        game.onEnd(Language.transl(context, "command.stop.matchStopped", context.getAuthor().getName()),
                false);

        return Language.transl(context, "command.stop.response");
    }
}
