package me.deprilula28.gamesrob .commands;

import com.vdurmont.emoji.EmojiManager;
import me.deprilula28.gamesrob.GamesROB;
import me.deprilula28.gamesrob.Language;
import me.deprilula28.gamesrob.baseFramework.GamesInstance;
import me.deprilula28.gamesrob.data.GuildProfile;
import me.deprilula28.gamesrob.data.UserProfile;
import me.deprilula28.gamesrob.utility.Constants;
import me.deprilula28.gamesrob.utility.Utility;
import me.deprilula28.jdacmdframework.CommandContext;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.User;

import java.math.BigDecimal;
import java.util.regex.Pattern;

public class ProfileCommands {
    public static String profile(CommandContext context) {
        GuildProfile board = GuildProfile.get(context.getGuild());
        User profileOf = context.opt(context::nextUser).orElse(context.getAuthor());
        GuildProfile.UserStatistics stats = UserProfile.get(profileOf).getStatsForGuild(context.getGuild());

        StringBuilder builder = new StringBuilder(Language.transl(context, "command.profile.playerStats",
                profileOf.getName()));
        builder.append(Language.transl(context, "command.profile.overall",
                getEmoji(stats.getOverall()),
                getStatisticsString(context, stats.getOverall(), board.getIndex(board.getOverall(), profileOf.getId()))));

        for (GamesInstance game : GamesROB.ALL_GAMES) {
            UserProfile.GameStatistics gameStats = stats.getGameStats(game);
            builder.append(String.format("%s %s: %s\n", getEmoji(gameStats), game.getName(Constants.getLanguage(context)),
                    getStatisticsString(context, gameStats, board.getIndex(board.getOption(game), profileOf.getId()))));
        }

        return builder.toString();
    }

    private static String[] MEDALS = { "\uD83E\uDD47", "\uD83E\uDD48", "\uD83E\uDD49" };

    private static String getStatisticsString(CommandContext context, UserProfile.GameStatistics stats, int leaderboardPosition) {
        return Language.transl(context, "command.profile.statsString",
                leaderboardPosition == -1 ? "" : String.format("%s**%s%s %s!** ",
                    leaderboardPosition < 3 ? MEDALS[leaderboardPosition] + " " : "",
                    leaderboardPosition + 1, Utility.formatNth(Constants.getLanguage(context), leaderboardPosition + 1),
                    Language.transl(context, "command.profile.place")),
                stats.getVictories(), stats.getLosses(),
                new BigDecimal(stats.getWLRatio()).setScale(2, BigDecimal.ROUND_HALF_UP),
                stats.getGamesPlayed());
    }

    public static String getEmoji(UserProfile.GameStatistics stats) {
        if (stats.getGamesPlayed() < Constants.LEADERBOARD_GAMES_PLAYED_REQUIREMENT) return "\uD83C\uDFF4";

        double winCount = stats.getWLRatio();
        if (winCount < 0.5) return "\uD83D\uDEA9";
        else if (winCount < 1.0) return "\uD83C\uDFF3";
        else if (winCount < 2.0) return "\uD83C\uDFC1";
        else return "\uD83D\uDC51";
    }

    private static Pattern emotePattern = Pattern.compile("(<:.*:[0-9]{18}>)");

    public static String tokens(CommandContext context) {
        User target = context.opt(context::nextUser).orElse(context.getAuthor());
        int tokens = UserProfile.get(target).getTokens();
        return target.equals(context.getAuthor()) ? Language.transl(context, "command.tokens.own", tokens)
                : Language.transl(context, "command.tokens.other", target.getName(), tokens);
    }

    public static String emojiTile(CommandContext context) {
        if (!UserProfile.get(context.getAuthor()).transaction(40)) return Language.transl(context,
                "genericMessages.notEnoughTokens", Constants.getDboURL(context.getJda()) + "/vote");
        String emote = context.next();

        if (EmojiManager.isEmoji(emote)) {
            UserProfile.get(context.getAuthor()).setEmote(emote);
            return Language.transl(context, "command.emote.set", emote);
        } else if (emotePattern.matcher(emote).matches()) {
            if (!validateEmote(context.getJda(), emote)) return Language.transl(context, "command.emote.cannotUse");
            UserProfile.get(context.getAuthor()).setEmote(emote);
            return Language.transl(context, "command.emote.set", emote);
        } else return Language.transl(context, "command.emote.invalid");
    }

    public static boolean validateEmote(JDA jda, String text) {
        return !emotePattern.matcher(text).matches() ||
                jda.getEmoteById(text.substring(text.length() - 19, text.length() - 1)) != null;
    }
}
