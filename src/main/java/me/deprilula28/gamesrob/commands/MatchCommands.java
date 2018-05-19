package me.deprilula28.gamesrob.commands;

import me.deprilula28.gamesrob.Language;
import me.deprilula28.gamesrob.baseFramework.Match;
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
        if (game.getPlayers().contains(Optional.of(context.getAuthor()))) return "You're already on the match!";
        if (game.getPlayers().size() == game.getTargetPlayerCount() + 1) return "The match is full!";

        game.joined(context.getAuthor());
        return null;
    }

    public static String leave(CommandContext context) {
        if (!Match.PLAYING.containsKey(context.getAuthor()))
            throw new CommandArgsException(Language.transl(context, "command.leave.notPlaying",
                (Match.GAMES.containsKey(context.getChannel())
                    ? Language.transl(context, "command.leave.joinChannelGame", Constants.getPrefix(context.getGuild()))
                    : Language.transl(context, "command.leave.startGame", Constants.getPrefix(context.getGuild()))
            )));
        Match.PLAYING.get(context.getAuthor()).left(context.getAuthor());
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
