package me.deprilula28.gamesrob.commands;

import me.deprilula28.gamesrob.GamesROB;
import me.deprilula28.gamesrob.Language;
import me.deprilula28.gamesrob.baseFramework.GamesInstance;
import me.deprilula28.gamesrob.data.GuildProfile;
import me.deprilula28.gamesrob.data.LeaderboardHandler;
import me.deprilula28.gamesrob.data.UserProfile;
import me.deprilula28.gamesrob.utility.Constants;
import me.deprilula28.gamesrob.utility.Log;
import me.deprilula28.gamesrob.utility.Utility;
import me.deprilula28.jdacmdframework.CommandContext;
import net.dv8tion.jda.core.EmbedBuilder;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class LeaderboardCommand {
    private static final String[] MEDALS = {
            "\uD83E\uDD47", "\uD83E\uDD48", "\uD83E\uDD49"
    };

    private static String getScore(CommandContext context, LeaderboardHandler.LeaderboardEntry entry, Function<UserProfile, UserProfile.GameStatistics> profileStatsGetter) {
        return GamesROB.shards.stream().map(it -> it.getUserById(entry.getId())).filter(Objects::nonNull).findFirst()
                .map(it -> it.getName() + "#" + it.getDiscriminator())
                .orElse(Language.transl(context, "command.leaderboard.unknownUser")) +
                    Language.transl(context, "command.leaderboard.playerRatings", entry.getStats().getVictories());
    }

    public static Object leaderboard(CommandContext context) {
        LeaderboardHandler leaderboard = GuildProfile.get(context.getGuild()).getLeaderboard();
        List<LeaderboardHandler.LeaderboardEntry> sortedOverall = leaderboard.getEntriesForGame(Optional.empty());

        StringBuilder overallBuilder = new StringBuilder();
        for (int i = 0; i < 5 && i < sortedOverall.size(); i++) {
            overallBuilder.append(String.format(
                    "%s**%s%s:** %s\n",
                    i < 3 ? MEDALS[i] + " " : "",
                    i + 1, Utility.formatNth(Constants.getLanguage(context), i + 1),
                    getScore(context, sortedOverall.get(i), it -> it.getStatsForGuild(context.getGuild()).getStats("overall"))));
        }
        StringBuilder gameWinners = new StringBuilder();
        for (GamesInstance game : GamesROB.ALL_GAMES) {
            List<LeaderboardHandler.LeaderboardEntry> winners = leaderboard.getEntriesForGame(Optional.of(game.getLanguageCode()));

            gameWinners.append(String.format("**%s**: %s\n", Language.transl(context, game.getName(Constants.getLanguage(context))),
                    winners.size() > 0
                        ? getScore(context, winners.get(0), it -> it.getStatsForGuild(context.getGuild()).getStats(game.getLanguageCode()))
                        : Language.transl(context, "command.leaderboard.noWinner")));
        }


        context.send(builder -> {
            builder.append(Language.transl(context, "command.leaderboard.response", overallBuilder, gameWinners));
            Log.info(overallBuilder.toString(), gameWinners.toString());
            builder.setEmbed(new EmbedBuilder().setColor(Utility.getEmbedColor(context.getGuild()))
                .setTitle(Language.transl(context, "command.leaderboard.websiteLink"),
                        Constants.GAMESROB_DOMAIN + "/serverLeaderboard/" + context.getGuild().getId() + "/").build());
        });
        return null;
    }
}
