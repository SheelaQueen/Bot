package me.deprilula28.gamesrob .commands;

import com.vdurmont.emoji.EmojiManager;
import javafx.util.Pair;
import me.deprilula28.gamesrob.GamesROB;
import me.deprilula28.gamesrob.Language;
import me.deprilula28.gamesrob.achievements.Achievement;
import me.deprilula28.gamesrob.achievements.AchievementType;
import me.deprilula28.gamesrob.achievements.Achievements;
import me.deprilula28.gamesrob.baseFramework.GamesInstance;
import me.deprilula28.gamesrob.data.GuildProfile;
import me.deprilula28.gamesrob.data.LeaderboardHandler;
import me.deprilula28.gamesrob.data.UserProfile;
import me.deprilula28.gamesrob.utility.Constants;
import me.deprilula28.gamesrob.utility.Utility;
import me.deprilula28.jdacmdframework.CommandContext;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.User;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class ProfileCommands {
    private static final int BAR_LENGTH = 5;
    private static final String BAR_CHAR = "=";

    public static String profile(CommandContext context) {
        GuildProfile board = GuildProfile.get(context.getGuild());
        User profileOf = context.opt(context::nextUser).orElse(context.getAuthor());
        LeaderboardHandler.UserStatistics stats = UserProfile.get(profileOf).getStatsForGuild(context.getGuild());

        StringBuilder builder = new StringBuilder(Language.transl(context, "command.profile.playerStats",
                profileOf.getName()));
        builder.append(Language.transl(context, "command.profile.overall",
                getEmoji(stats.getStats("overall")),
                getStatisticsString(context, stats.getStats("overall"), board.getIndex(board.getLeaderboard()
                        .getEntriesForGame(Optional.empty()), profileOf.getId()))));

        for (GamesInstance game : GamesROB.ALL_GAMES) {
            UserProfile.GameStatistics gameStats = stats.getStats(game.getLanguageCode());
            builder.append(String.format("%s %s: %s\n", getEmoji(gameStats), game.getName(Constants.getLanguage(context)),
                    getStatisticsString(context, gameStats, board.getIndex(board.getLeaderboard()
                            .getEntriesForGame(Optional.of(game.getLanguageCode())), profileOf.getId()))));
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
                new BigDecimal(stats.getWonPercent()).setScale(1, BigDecimal.ROUND_HALF_UP) + "%",
                stats.getGamesPlayed());
    }

    public static String getEmoji(UserProfile.GameStatistics stats) {
        double winCount = stats.getWonPercent();
        if (winCount > 75.0) return "\uD83D\uDC51";
            else if (winCount > 50.0) return "\uD83D\uDEA9";
        else if (winCount > 25.0) return "\uD83C\uDFF3";
        else return "\uD83C\uDFC1";
    }

    private static Pattern emotePattern = Pattern.compile("(<:.*:[0-9]{18}>)");

    public static

    public static String tokens(CommandContext context) {
        User target = context.opt(context::nextUser).orElse(context.getAuthor());
        UserProfile profile = UserProfile.get(target);
        int tokens = profile.getTokens();
        String prefix = Constants.getPrefix(context.getGuild());

        if (target.equals(context.getAuthor())) {
            context.send(message -> {
                EmbedBuilder embed = new EmbedBuilder().setTitle(Language.transl(context, "command.tokens.guide"),
                        Constants.GAMESROB_DOMAIN + "/help/currency").setColor(Utility.getEmbedColor(context.getGuild()));

                message.append("→ ");
                Map<String, Boolean> winMethods = new HashMap<>();
                winMethods.put(Language.transl(context, "command.tokens.winningMatches"), true);

                boolean positiveBalance = tokens > 0;
                winMethods.put(Language.transl(context, positiveBalance ? "command.tokens.gamblingTokens" : "command.tokens.gamblingNoTokens"), positiveBalance);

                // Upvoting
                long timeSinceUpvote = System.currentTimeMillis() - profile.getLastUpvote();
                if (timeSinceUpvote > TimeUnit.DAYS.toMillis(1)) {
                    int row = timeSinceUpvote < TimeUnit.DAYS.toMillis(2) ? profile.getUpvotedDays() : 0;

                    winMethods.put(Language.transl(context, "command.tokens.upvoteCan", row, 125 + row * 50), true);
                    embed.setDescription(Language.transl(context, "command.tokens.embedUpvote",
                            Constants.getDboURL(context.getJda()) + "/vote"));
                } else winMethods.put(Language.transl(context, "command.tokens.upvoteLater", Utility.formatPeriod
                                ((profile.getLastUpvote() + TimeUnit.DAYS.toMillis(1)) - System.currentTimeMillis())),
                        false);

                // Achievements
                Map<Achievement, Integer> toComplete = new HashMap<>();

                for (AchievementType type : AchievementType.values()) {
                    if (type == AchievementType.OTHER) continue;
                    int amount = type.getAmount(context.getAuthor());
                    Arrays.stream(Achievements.values()).filter(it -> type.equals(it.getAchievement().getType()))
                            .map(Achievements::getAchievement).filter(it -> it.getAmount() > amount)
                            .min(Comparator.comparingInt(Achievement::getAmount)).ifPresent(achievement ->
                            toComplete.put(achievement, amount));
                }

                if (toComplete.isEmpty()) winMethods.put(Language.transl(context, "command.tokens.noAchievements"), false);
                else {
                    StringBuilder builder = new StringBuilder(Language.transl(context, "command.tokens.achievements"));
                    String language = Constants.getLanguage(context);
                    toComplete.forEach((achievement, amount) -> {
                        double percent = (double) amount / (double) achievement.getAmount();
                        StringBuilder bar = new StringBuilder();
                        for (int i = 0; i < BAR_LENGTH; i ++) {
                            if ((double) i / (double) BAR_LENGTH < percent) bar.append(BAR_CHAR);
                            else bar.append("   ");
                        }

                        builder.append(String.format(
                                "%s: *%s*\n`%s [%s] %s` (+\uD83D\uDD38 %s tokens)\n",
                                achievement.getName(language), achievement.getDescription(language),
                                amount, bar.toString(), achievement.getAmount(), achievement.getTokens()
                        ));
                    });

                    winMethods.put(builder.toString(), true);
                }

                message.append(Language.transl(context, "command.tokens.own", tokens)).append("\n");
                message.setEmbed(embed.build());
                winMethods.forEach((text, check) -> {
                    message.append(String.format(
                            "- %s %s\n",
                            check ? "<:check:314349398811475968>" : "<:xmark:314349398824058880>", text
                    ));
                });
            });

            return null;
        } else return Language.transl(context, "command.tokens.other", target.getName(), tokens);
    }

    public static String emojiTile(CommandContext context) {
        Optional<String> emoteOpt = context.opt(context::next);
        if (emoteOpt.isPresent()) {
            String emote = emoteOpt.get();
            if (!UserProfile.get(context.getAuthor()).transaction(150))
                return Constants.getNotEnoughTokensMessage(context, 150);

            if (EmojiManager.isEmoji(emote)) {
                UserProfile.get(context.getAuthor()).setEmote(emote);
                return Language.transl(context, "command.emote.set", emote);
            } else if (emotePattern.matcher(emote).matches()) {
                if (!validateEmote(context.getJda(), emote)) return Language.transl(context, "command.emote.cannotUse");
                UserProfile.get(context.getAuthor()).setEmote(emote);
                return Language.transl(context, "command.emote.set", emote);
            } else return Language.transl(context, "command.emote.invalid");
        } else {
            UserProfile.get(context.getAuthor()).setEmote(null);
            return Language.transl(context, "command.emote.reset");
        }
    }

    public static boolean validateEmote(JDA jda, String text) {
        return !emotePattern.matcher(text).matches() ||
                jda.getEmoteById(text.substring(text.length() - 19, text.length() - 1)) != null;
    }
}
