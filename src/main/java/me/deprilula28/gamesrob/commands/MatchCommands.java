package me.deprilula28.gamesrob.commands;

import me.deprilula28.gamesrob.Language;
import me.deprilula28.gamesrob.baseFramework.GameState;
import me.deprilula28.gamesrob.baseFramework.GameType;
import me.deprilula28.gamesrob.baseFramework.Match;
import me.deprilula28.gamesrob.data.UserProfile;
import me.deprilula28.gamesrob.utility.Constants;
import me.deprilula28.jdacmdframework.CommandContext;
import me.deprilula28.jdacmdframework.exceptions.CommandArgsException;
import net.dv8tion.jda.core.entities.User;

import java.util.Optional;

public class MatchCommands {
    private static Match getGame(CommandContext context) {
        if (!Match.GAMES.containsKey(context.getChannel()))
            throw new CommandArgsException(Language.transl(context, "genericMessages.noGamesInChannel",
                    Constants.getPrefix(context.getGuild())));

        return Match.GAMES.get(context.getChannel());
    }

    public static String join(CommandContext context) {
        if (Match.PLAYING.containsKey(context.getAuthor()))
            return Language.transl(context, "genericMessages.alreadyPlaying",
                    Match.PLAYING.get(context.getAuthor()).getChannelIn().getAsMention(),
                    Constants.getPrefix(context.getGuild())
            );

        Match game = getGame(context);
        if (game.getGame().getGameType() == GameType.COLLECTIVE) return Language.transl(context, "command.join.collectiveMatch");
        if (game.getGameState() != GameState.PRE_GAME) return Language.transl(context, "command.join.gameStarted");
        if (game.getPlayers().contains(Optional.of(context.getAuthor())))
            return Language.transl(context, "command.join.alreadyOnMatch");
        if (game.getPlayers().size() == game.getTargetPlayerCount() + 1)
            return Language.transl(context, "command.join.full");
        if (game.getBetting().isPresent() && !UserProfile.get(context.getAuthor()).transaction(game.getBetting().get()))
            return Constants.getNotEnoughTokensMessage(context, game.getBetting().get());

        game.joined(context.getAuthor());
        return null;
    }

    public static String listPlayers(CommandContext context) {
        Match game = getGame(context);
        String language = Constants.getLanguage(context);
        StringBuilder builder = new StringBuilder(Language.transl(context, "command.listplayers.header",
                game.getGame().getName(language), game.getGame().getShortDescription(language)
        ));

        game.getPlayers().forEach(cur -> builder.append(cur.map(User::getName).orElse("**AI**")).append("\n"));
        return builder.toString();
    }

    public static String stop(CommandContext context) {
        Match game = getGame(context);
        game.onEnd(Language.transl(context, "command.stop.matchStopped", context.getAuthor().getName()),
                false);

        return null;
    }
}
