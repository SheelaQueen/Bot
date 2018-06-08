package me.deprilula28.gamesrob.commands;

import me.deprilula28.gamesrob.GamesROB;
import me.deprilula28.gamesrob.Language;
import me.deprilula28.gamesrob.data.GuildProfile;
import me.deprilula28.gamesrob.data.UserProfile;
import me.deprilula28.gamesrob.utility.Constants;
import me.deprilula28.gamesrob.utility.Utility;
import me.deprilula28.jdacmdframework.CommandContext;
import net.dv8tion.jda.core.EmbedBuilder;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class LeaderboardCommand {
    private static final String[] MEDALS = {
            "\uD83E\uDD47", "\uD83E\uDD48", "\uD83E\uDD49"
    };

    private static String getScore(CommandContext context, GuildProfile.LeaderboardEntry entry, Function<UserProfile, UserProfile.GameStatistics> profileStatsGetter) {
        return GamesROB.shards.stream().map(it -> it.getUserById(entry.getId())).filter(it -> it != null).findFirst()
                .map(it -> it.getName() + "#" + it.getDiscriminator())
                .orElse(Language.transl(context, "command.leaderboard.unknownUser")) +
                    Language.transl(context, "command.leaderboard.playerRatings", entry.getStats().getVictories());
    }

    public static Object leaderboard(CommandContext context) {
        GuildProfile leaderboard = GuildProfile.get(context.getGuild());
        List<GuildProfile.LeaderboardEntry> sortedOverall = leaderboard.getOverall().stream()
                .sorted(Comparator.comparingDouble(it -> it.getStats().getWonPercent()))
                .collect(Collectors.toList());

        StringBuilder overallBuilder = new StringBuilder();
        for (int i = 0; i < 5 && i < sortedOverall.size(); i++) {
            overallBuilder.append(String.format(
                    "%s**%s%s:** %s\n",
                    i < 3 ? MEDALS[i] + " " : "",
                    i + 1, Utility.formatNth(Constants.getLanguage(context), i + 1),
                    getScore(context, sortedOverall.get(i), it -> it.getStatsForGuild(context.getGuild()).getOverall())));
        }
        StringBuilder gameWinners = new StringBuilder();
        leaderboard.getPerGame().forEach((game, unsorted) -> {
            List<GuildProfile.LeaderboardEntry> winners = unsorted.stream()
                    .sorted(Comparator.comparingDouble(it -> it.getStats().getWonPercent()))
                    .collect(Collectors.toList());

            gameWinners.append(String.format("**%s**: %s\n", Language.transl(context, "game." + game + ".name"),
                    winners.size() > 0
                ? getScore(context, winners.get(0), it -> it.getStatsForGuild(context.getGuild()).getGameStats(game))
                : Language.transl(context, "command.leaderboard.noWinner")));
        });

        context.send(builder -> {
            builder.append(Language.transl(context, "command.leaderboard.response", overallBuilder, gameWinners));
            builder.setEmbed(new EmbedBuilder().setColor(Utility.randomBotColor())
                .setTitle(Language.transl(context, "command.leaderboard.websiteLink"),
                        Constants.GAMESROB_DOMAIN + "/serverLeaderboard/" + context.getGuild().getId() + "/").build());
        });
        return null;
    }
}
