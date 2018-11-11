package me.deprilula28.gamesrob.commands;

import me.deprilula28.gamesrob.GamesROB;
import me.deprilula28.gamesrob.utility.Language;
import me.deprilula28.gamesrob.data.GuildProfile;
import me.deprilula28.gamesrob.data.LeaderboardHandler;
import me.deprilula28.gamesrob.utility.Utility;
import javafx.util.Pair;
import me.deprilula28.gamesrob.baseFramework.GamesInstance;
import me.deprilula28.jdacmdframework.CommandContext;
import net.dv8tion.jda.core.entities.Member;

import java.awt.*;
import java.util.*;
import java.util.List;

public class LeaderboardCommand {
    private static final int LEADERBOARD_COMMAND_WIDTH = ImageCommands.USER_PROFILE_WIDTH + 200;
    private static final int LEADERBOARD_ENTRIES_BORDER = 30;
    private static final int LEADERBOARD_GAME_TITLE_HEIGHT = 30;
    private static final int LEADERBOARD_GAME_TITLE_FONT_SIZE = 30;

    private static final int OVERALL_ENTRY_LIMIT = 5;
    private static final int GAME_ENTRY_LIMIT = 1;

    public static Pair<Integer, Integer> leaderboard(CommandContext context, Utility.Promise<CommandManager.RenderContext> rcontextPromise) {
        GuildProfile guildprofile = GuildProfile.get(context.getGuild());
        List<LeaderboardHandler.LeaderboardEntry> overall = guildprofile.getLeaderboard().getEntriesForGame(Optional.empty());

        rcontextPromise.then(rcontext -> {
            Graphics2D g2d = rcontext.getGraphics();
            String background = guildprofile.getBackgroundImageUrl();

            ImageCommands.drawBackground(g2d, background, LEADERBOARD_COMMAND_WIDTH, rcontext.getHeight(), 0, false);

            int cury = LEADERBOARD_ENTRIES_BORDER;

            cury = drawEntriesForGame(g2d, rcontext.getStarlight(), context, Language.transl(context, "command.profile.overall"),
                    overall, cury, rcontext.getWidth(), OVERALL_ENTRY_LIMIT);
            for (GamesInstance game : GamesROB.ALL_GAMES) {
                List<LeaderboardHandler.LeaderboardEntry> entries = guildprofile.getLeaderboard()
                        .getEntriesForGame(Optional.of(game.getLanguageCode()));
                if (!entries.isEmpty()) cury = drawEntriesForGame(g2d, rcontext.getStarlight(), context,
                        game.getName(Utility.getLanguage(context)), entries, cury, rcontext.getWidth(), GAME_ENTRY_LIMIT);
            }
        });

        return new Pair<>(LEADERBOARD_COMMAND_WIDTH, LEADERBOARD_ENTRIES_BORDER * 2 +
            Arrays.stream(GamesROB.ALL_GAMES).mapToInt(it -> {
                List<LeaderboardHandler.LeaderboardEntry> entries = guildprofile.getLeaderboard()
                        .getEntriesForGame(Optional.of(it.getLanguageCode()));
                return entries.size() > 0 ? Math.min(entries.size(), GAME_ENTRY_LIMIT)
                        * ImageCommands.LEADERBOARD_ENTRY_HEIGHT + LEADERBOARD_GAME_TITLE_HEIGHT : 0;
            }).sum() + LEADERBOARD_GAME_TITLE_HEIGHT + ImageCommands.LEADERBOARD_ENTRY_HEIGHT * Math.min(overall.size(), OVERALL_ENTRY_LIMIT));
    }

    private static int drawEntriesForGame(Graphics2D g2d, Font starlight, CommandContext context, String title,
                                          List<LeaderboardHandler.LeaderboardEntry> entries, int cury, int width, int entryLimit) {
        g2d.setColor(Color.white);
        g2d.setFont(starlight.deriveFont((float) LEADERBOARD_GAME_TITLE_FONT_SIZE).deriveFont(Font.PLAIN));
        ImageCommands.drawCenteredString(g2d, title,
                0, cury + LEADERBOARD_GAME_TITLE_HEIGHT - g2d.getFontMetrics().getHeight() / 4, width);
        cury += LEADERBOARD_GAME_TITLE_FONT_SIZE;

        for (int i = 0; i < entryLimit && i < entries.size(); i++) {
            LeaderboardHandler.LeaderboardEntry it = entries.get(i);
            Optional<Member> member = Optional.ofNullable(context.getGuild().getMemberById(it.getId()));
            ImageCommands.drawLeaderboardEntry(member, member.map(mbm -> Utility.truncateLength(mbm.getEffectiveName(),
                150, g2d.getFontMetrics())).orElseGet(() -> Language.transl(context, "command.leaderboard.unknownUser")),
                    it.getStats(), i, g2d, LEADERBOARD_ENTRIES_BORDER, cury, LEADERBOARD_COMMAND_WIDTH -
                            LEADERBOARD_ENTRIES_BORDER * 2, starlight, Utility.getLanguage(context));
            cury += ImageCommands.LEADERBOARD_ENTRY_HEIGHT;
        }

        return cury;
    }

    /*
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
            builder.setEmbed(new EmbedBuilder().setColor(Utility.getEmbedColor(context.getGuild()))
                .setTitle(Language.transl(context, "command.leaderboard.websiteLink"),
                        Constants.GAMESROB_DOMAIN + "/serverLeaderboard/" + context.getGuild().getId() + "/").build());
        });
        return null;
    }
    */
}
