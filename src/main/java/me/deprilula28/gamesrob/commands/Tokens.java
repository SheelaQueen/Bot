package me.deprilula28.gamesrob.commands;

import me.deprilula28.gamesrob.GamesROB;
import me.deprilula28.gamesrob.Language;
import me.deprilula28.gamesrob.achievements.Achievement;
import me.deprilula28.gamesrob.achievements.AchievementType;
import me.deprilula28.gamesrob.achievements.Achievements;
import me.deprilula28.gamesrob.data.SQLDatabaseManager;
import me.deprilula28.gamesrob.data.UserProfile;
import me.deprilula28.gamesrob.utility.Cache;
import me.deprilula28.gamesrob.utility.Constants;
import me.deprilula28.gamesrob.utility.Log;
import me.deprilula28.gamesrob.utility.Utility;
import me.deprilula28.jdacmdframework.CommandContext;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.entities.User;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Tokens {
    private static final int BAR_LENGTH = 20;
    private static final String BAR_CHAR = "=";

    public static String achievements(CommandContext context) {
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

        if (toComplete.isEmpty()) return Language.transl(context, "command.achievements.noAchievements");
        else context.send(builder -> {
            builder.append(Language.transl(context, "command.achievements.beginning"));
            String language = Constants.getLanguage(context);
            int maxAmount = toComplete.values().stream().mapToInt(it -> String.valueOf(it).length()).max().orElse(1);

            toComplete.forEach((achievement, amount) -> {
                double percent = (double) amount / (double) achievement.getAmount();
                StringBuilder bar = new StringBuilder();
                for (int i = 0; i < BAR_LENGTH; i ++) {
                    if ((double) i / (double) BAR_LENGTH < percent) bar.append(BAR_CHAR);
                    else bar.append(" ");
                }
                StringBuilder name = new StringBuilder(String.valueOf(amount));
                for (int i = String.valueOf(amount).length(); i < maxAmount; i ++) name.append(" ");

                builder.append(String.format(
                        "%s: *%s*\n`%s [%s] %s` (\uD83D\uDD38 %s tokens)\n",
                        achievement.getName(language), achievement.getDescription(language),
                        name.toString(), bar.toString(), achievement.getAmount(), Utility.addNumberDelimitors(achievement.getTokens())
                ));
            });

            builder.setEmbed(new EmbedBuilder().setTitle(Language.transl(context, "command.achievements.upvote"),
                    Constants.getDboURL(context.getJda()) + "/vote")
                .setColor(Utility.getEmbedColor(context.getGuild()))
                .build());
        });

        return null;
    }

    public static String tokens(CommandContext context) {
        User target = context.opt(context::nextUser).orElse(context.getAuthor());
        UserProfile profile = UserProfile.get(target);
        int tokens = profile.getTokens();
        String prefix = Constants.getPrefix(context.getGuild());
        String baltopMessage;

        if (GamesROB.database.isPresent()) {
            int guildPos;
            int globalPos;
            try {
                SQLDatabaseManager db = GamesROB.database.get();
                ResultSet globalPosSet = db.sqlFileQuery("selectRanked.sql", statement -> Log.wrapException("Getting SQL position",
                        () -> statement.setString(1, target.getId())));
                ResultSet guildPosSet = db.sqlFileQuery("selectRankedGuild.sql", statement -> Log.wrapException("Getting Guild SQL Position",
                        () -> statement.setString(1, target.getId())), context.getGuild()
                        .getMembers().stream().map(it -> "userid = '" + it.getUser().getId() + "'")
                        .collect(Collectors.joining(" OR ")));
                guildPos = guildPosSet.next() ? guildPosSet.getInt("rank") : -1;
                globalPos = globalPosSet.next() ? globalPosSet.getInt("rank") : -1;
                guildPosSet.close();
                globalPosSet.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            baltopMessage = Language.transl(context, "command.tokens.baltop",
                    guildPos + Utility.formatNth(Constants.getLanguage(context), guildPos),
                    globalPos + Utility.formatNth(Constants.getLanguage(context), globalPos),
                    Constants.getPrefix(context.getGuild()));
        } else baltopMessage = null;

        if (target.equals(context.getAuthor())) {
            context.send(message -> {
                EmbedBuilder embed = new EmbedBuilder().setTitle(Language.transl(context, "command.tokens.guide"),
                        Constants.GAMESROB_DOMAIN + "/help/currency").setColor(Utility.getEmbedColor(context.getGuild()));

                message.append("→ ");
                Map<String, Boolean> winMethods = new HashMap<>();
                winMethods.put(Language.transl(context, "command.tokens.winningMatches") + " (\uD83D\uDD38 " +
                        Constants.MATCH_WIN_TOKENS + "+ tokens)", true);

                boolean positiveBalance = tokens > 0;
                winMethods.put(Language.transl(context, positiveBalance ? "command.tokens.gamblingTokens" : "command.tokens.gamblingNoTokens"), positiveBalance);

                // Upvoting
                long timeSinceUpvote = System.currentTimeMillis() - profile.getLastUpvote();
                if (timeSinceUpvote > TimeUnit.HOURS.toMillis(12)) {
                    int row = timeSinceUpvote < TimeUnit.DAYS.toMillis(2) ? profile.getUpvotedDays() : 0;
                    boolean weekend = Utility.isWeekendMultiplier();

                    winMethods.put(Language.transl(context, weekend ? "command.tokens.upvoteWeekend" : "command.tokens.upvoteCan",
                            row, Utility.addNumberDelimitors((125 + row * 50) * (weekend ? 2 : 1))), true);
                    embed.setDescription(Language.transl(context, "command.tokens.embedUpvote",
                            Constants.getDboURL(context.getJda()) + "/vote"));
                } else winMethods.put(Language.transl(context, "command.tokens.upvoteLater", Utility.formatPeriod
                                ((profile.getLastUpvote() + TimeUnit.HOURS.toMillis(12)) - System.currentTimeMillis())),
                        false);

                winMethods.put(Language.transl(context, "command.tokens.achievements2", Constants.getPrefix(context.getGuild())), true);

                message.append(Language.transl(context, "command.tokens.own", Utility.addNumberDelimitors(tokens))).append("\n");
                message.setEmbed(embed.build());
                winMethods.forEach((text, check) -> {
                    message.append(String.format(
                            "- %s %s\n",
                            check ? "<:check:314349398811475968>" : "<:xmark:314349398824058880>", text
                    ));
                });

                message.append(baltopMessage);
            });

            return null;
        } else return Language.transl(context, "command.tokens.other", target.getName(), Utility.addNumberDelimitors(tokens)) + baltopMessage;
    }

    private static final int ENTRIES_PAGE = 10;

    public static String baltop(CommandContext context) {
        Optional<String> next = context.opt(context::next);
        boolean global = next.map(it -> it.equalsIgnoreCase("global")).orElse(false);
        int page = (global ? context.opt(context::nextInt) : next.map(Integer::parseInt)).orElse(1);

        if (GamesROB.database.isPresent()) {
            String key = "baltop_" + page + "_" + Constants.getLanguage(context);
            return Cache.get(global ? key + "_global" : key + "_" + context.getGuild().getId(), n -> {
                try {
                    SQLDatabaseManager db = GamesROB.database.get();

                    int elements = db.getAmount("guilddata");
                    int pages = (elements / ENTRIES_PAGE) + 1;
                    if (page <= 0 || page > pages) return Language.transl(context, "command.baltop.invalidPage", 1, pages);

                    String whereGuild = context.getGuild().getMembers().stream()
                            .map(it -> "userid = '" + it.getUser().getId() + "'").collect(Collectors.joining(" OR "));
                    ResultSet set = global
                            ? db.select("userData", Arrays.asList("tokens", "userid"), "tokens", true,
                                ENTRIES_PAGE, (page - 1) * ENTRIES_PAGE)
                            : db.select("userData", Arrays.asList("tokens", "userid"), whereGuild,
                            "tokens", true, ENTRIES_PAGE, (page - 1) * ENTRIES_PAGE);

                    StringBuilder builder = new StringBuilder(global
                            ? Language.transl(context, "command.baltop.global")
                            : Language.transl(context, "command.baltop.server", context.getGuild().getName()));
                    int index = 1;
                    boolean hasUser = false;
                    while (set.next()) {
                        int pos = ((page - 1) * ENTRIES_PAGE) + index;
                        String userId = set.getString("userid");
                        append(builder, pos, Constants.getLanguage(context), GamesROB.getUserById(userId), set.getInt("tokens"));
                        if (userId.equals(context.getAuthor().getId())) hasUser = true;
                        index ++;
                    }
                    set.close();

                    if (!hasUser) {
                        ResultSet positionSet = global
                                ? db.sqlFileQuery("selectRanked.sql", statement -> Log.wrapException("Getting SQL position",
                                () -> statement.setString(1, context.getAuthor().getId())))
                                : db.sqlFileQuery("selectRankedGuild.sql", statement -> Log.wrapException("Getting Guild SQL Position",
                                () -> statement.setString(1, context.getAuthor().getId())), whereGuild);

                        if (positionSet.next()) {
                            int position = positionSet.getInt("rank");
                            if (position > page * ENTRIES_PAGE) {
                                builder.append("...\n");
                                append(builder, position, Constants.getLanguage(context), Optional.of(context.getAuthor()),
                                        UserProfile.get(context.getAuthor()).getTokens());
                            }
                        }
                        positionSet.close();
                    }

                    builder.append(Language.transl(context, "command.baltop.footer", ENTRIES_PAGE, page, pages,
                            Constants.getPrefix(context.getGuild()), global ? "global " : ""));
                    if (!global) builder.append(Language.transl(context, "command.baltop.globalView",
                            Constants.getPrefix(context.getGuild())));

                    return builder.toString();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } else return null;
    }

    private static void append(StringBuilder builder, int rank, String language, Optional<User> user, int tokens) {
        builder.append(String.format(
                "%s: **%s** [%s \uD83D\uDD38 tokens]\n",
                rank + Utility.formatNth(language, rank), user.map(User::getName).orElse("not found"), Utility.addNumberDelimitors(tokens)
        ));
    }
}
