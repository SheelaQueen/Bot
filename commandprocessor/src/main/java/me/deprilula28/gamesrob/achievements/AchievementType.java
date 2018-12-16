package me.deprilula28.gamesrob.achievements;

import me.deprilula28.gamesrob.GamesROB;
import me.deprilula28.gamesrob.utility.Language;
import me.deprilula28.gamesrob.data.UserProfile;
import me.deprilula28.gamesrobshardcluster.utilities.Log;
import me.deprilula28.gamesrob.utility.Utility;
import lombok.AllArgsConstructor;
import lombok.Getter;
import me.deprilula28.jdacmdframework.CommandContext;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.User;

import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@AllArgsConstructor
public enum AchievementType {
    PLAY_GAMES("playGames"), WIN_GAMES("winGames"), GAMBLE_TOKENS("gambleTokens"), WIN_TOKENS_GAMBLING("winTokensGambling"),
    LOSE_TOKENS_GAMBLING("loseTokensGambling"), REACH_TOKENS("reachTokens"), UPVOTE("upvote"), OTHER("other");

    private static final String[] ACHIEVEMENT_GOT_EMOTES = {
        "\uD83D\uDC83", "\uD83D\uDC51", "\uD83D\uDCAB", "<:rules_header:456960990559338497>", "\uD83C\uDF20", "\uD83C\uDF89"
    };

    @Getter private String languageCode;

    public int getAmount(User user) {
        try {
            if (GamesROB.database.isPresent()) {
                ResultSet set = GamesROB.database.get().select("achievements", Collections.singletonList("amount"),
                        "userid = '" + user.getId() + "' AND type = '" + toString() + "'");
                if (set.next()) return set.getInt("amount");
                return 0;
            } else return 0;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public void addAmount(boolean tagName, int amount, MessageBuilder builder, User user, Guild guild, String language) {
        String type = toString();
        GamesROB.database.ifPresent(db -> {
            UserProfile profile = UserProfile.get(user);
            db.save("achievements", Arrays.asList("type", "amount", "userid"),
                    "userid = '" + profile.getUserId() + "' AND type = '" + type + "'", set -> false,
                    false, (set, statement) -> Log.wrapException("Storing achievement amount", () -> {
                int prevAmount = set.map(it -> {
                    try {
                        ResultSet select = db.select("achievements", Collections.singletonList("amount"),
                                "userid = '" + profile.getUserId() + "' AND type = '" + type + "'");
                        if (select.next()) return select.getInt("amount");
                        else return 0;
                    } catch (Exception e) {
                        Log.exception("Getting achievements", e);
                        return 0;
                    }
                }).orElse(0);
                int newAmount = prevAmount + amount;

                statement.setString(1, type);
                statement.setInt(2, newAmount);
                statement.setString(3, profile.getUserId());

                List<Achievements> achievementsType = Arrays.stream(Achievements.values())
                        .filter(it -> it.getAchievement().getType().equals(this)).collect(Collectors.toList());
                achievementsType.forEach(achievements -> {
                    Achievement achievement = achievements.getAchievement();
                    if (prevAmount < achievement.getAmount() && newAmount >= achievement.getAmount()) {
                        // Achievement got!
                        builder.append("\n");
                        if (tagName) builder.append(user.getAsMention()).append(": ");

                        int tokens = profile.addTokens(guild, achievement.getTokens(), "transactions.completeAchievement");
                        builder.append(ACHIEVEMENT_GOT_EMOTES[ThreadLocalRandom.current().nextInt(ACHIEVEMENT_GOT_EMOTES.length)])
                                .append(Language.transl(language, "game.achievement.got",
                                        achievement.getName(language), achievement.getDescription(language),
                                        Utility.addNumberDelimitors(tokens)))
                                .append("\n").append(Language.transl(language, "game.achievement.runAchievements",
                                Utility.getPrefix(guild)));

                        REACH_TOKENS.addAmount(tagName, tokens, builder, user, guild, language);
                    }
                });

                // If last achievement for this type has been completed
                if (achievementsType.stream().max(Comparator.comparingInt(ac -> ac.getAchievement().getAmount()))
                        .filter(ac -> prevAmount < ac.getAchievement().getAmount() && newAmount >= ac.getAchievement().getAmount())
                        .isPresent()) {
                    Map<AchievementType, Integer> amountTypes = new HashMap<>();
                    for (AchievementType it : AchievementType.values()) {
                        if (it.equals(OTHER)) continue;
                        amountTypes.put(it, it.equals(this) ? newAmount : it.getAmount(user));
                    }

                    // If all achievements have been completed
                    if (Arrays.stream(Achievements.values()).allMatch(it -> amountTypes.get(it.getAchievement().getType())
                            >= it.getAchievement().getAmount())) {
                        profile.addBadge(UserProfile.Badge.ACHIEVER);
                        builder.append(Language.transl(language, "badges.earned", UserProfile.Badge.
                                ACHIEVER.getName(language), Utility.getPrefix(guild)));
                    }
                }
            }));
        });
    }
}
