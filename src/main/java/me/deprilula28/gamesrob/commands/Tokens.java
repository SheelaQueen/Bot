package me.deprilula28.gamesrob.commands;

import me.deprilula28.gamesrob.GamesROB;
import me.deprilula28.gamesrob.Language;
import me.deprilula28.gamesrob.achievements.Achievement;
import me.deprilula28.gamesrob.achievements.AchievementType;
import me.deprilula28.gamesrob.achievements.Achievements;
import me.deprilula28.gamesrob.data.SQLDatabaseManager;
import me.deprilula28.gamesrob.data.UserProfile;
import me.deprilula28.gamesrob.utility.Constants;
import me.deprilula28.gamesrob.utility.Log;
import me.deprilula28.gamesrob.utility.Utility;
import me.deprilula28.jdacmdframework.CommandContext;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.entities.User;

import java.sql.ResultSet;
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
        else {
            StringBuilder builder = new StringBuilder(Language.transl(context, "command.achievements.beginning"));
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
                        name.toString(), bar.toString(), achievement.getAmount(), achievement.getTokens()
                ));
            });

            return builder.toString();
        }
    }

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
                winMethods.put(Language.transl(context, "command.tokens.winningMatches") + " (\uD83D\uDD38 " +
                        Constants.MATCH_WIN_TOKENS + "+ tokens)", true);

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

                winMethods.put(Language.transl(context, "command.tokens.achievements2", Constants.getPrefix(context.getGuild())), true);


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

    private static final int ENTRIES_PAGE = 10;

    public static String baltop(CommandContext context) {
        Optional<String> next = context.opt(context::next);
        boolean global = next.map(it -> it.equalsIgnoreCase("global")).orElse(false);
        int page = (global ? context.opt(context::nextInt) : next.map(Integer::parseInt)).orElse(1);
        if (page <= 0) return Language.transl(context, "command.baltop.invalidPage");

        if (GamesROB.database.isPresent()) {
            try {
                SQLDatabaseManager db = GamesROB.database.get();

                ResultSet set = global
                    ? db.select("userData", Arrays.asList("tokens", "userid"), "tokens", true, page * ENTRIES_PAGE, (page + 1) * ENTRIES_PAGE)
                    : db.select("userData", Arrays.asList("tokens", "userid"), context.getGuild().getMembers().stream()
                        .map(it -> "userid = '" + it.getUser().getId() + "'").collect(Collectors.joining(" OR ")),
                        "tokens", true, page * ENTRIES_PAGE, (page + 1) * ENTRIES_PAGE);

                StringBuilder builder = new StringBuilder(global
                        ? Language.transl(context, "command.baltop.global")
                        : Language.transl(context, "command.baltop.server", context.getGuild().getName()));
                int index = 1;
                while (set.next()) {
                    int pos = ((page - 1) * ENTRIES_PAGE) + index;
                    builder.append(String.format(
                            "%s: **%s** [%s \uD83D\uDD38 tokens]\n",
                            pos + Utility.formatNth(Constants.getLanguage(context), pos),
                            GamesROB.getUserById(set.getString("userid")).map(User::getName).orElse("not found"),
                            set.getInt("tokens")
                    ));
                    index ++;
                }
                builder.append(Language.transl(context, "command.baltop.footer", ENTRIES_PAGE, page,
                        Constants.getPrefix(context.getGuild()), global ? "global " : ""));
                if (!global) builder.append(Language.transl(context, "command.baltop.globalView",
                        Constants.getPrefix(context.getGuild())));

                return builder.toString();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else return null;
    }
}
