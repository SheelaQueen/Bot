package me.deprilula28.gamesrob.achievements;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import me.deprilula28.gamesrob.GamesROB;
import me.deprilula28.gamesrob.Language;
import me.deprilula28.gamesrob.data.UserProfile;
import me.deprilula28.gamesrob.utility.Constants;
import me.deprilula28.gamesrob.utility.Log;
import me.deprilula28.gamesrob.utility.Utility;
import me.deprilula28.jdacmdframework.CommandContext;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.entities.User;

import java.sql.ResultSet;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

@AllArgsConstructor
public enum AchievementType {
    PLAY_GAMES("playGames"), WIN_GAMES("winGames"), GAMBLE_TOKENS("gambleTokens"), WIN_TOKENS_GAMBLING("winTokensGambling"),
    LOSE_TOKENS_GAMBLING("loseTokensGambling"), REACH_PLACE_LEADERBOARD("reachPlaceLeaderboard"),
    REACH_TOKENS("reachTokens"), UPVOTE("upvote"), OTHER("other");

    private static final String[] ACHIEVEMENT_GOT_EMOTES = {
        "\uD83D\uDC83", "\uD83D\uDC51", "\uD83D\uDCAB", "<:rules_header:456960990559338497>", "\uD83C\uDF20", "\uD83C\uDF89"
    };

    @Getter private String languageCode;

    public void addAmount(int amount, CommandContext context, Consumer<MessageBuilder> before) {
        context.send(builder -> {
            before.accept(builder);
            addAmount(false, amount, builder, context.getAuthor(), context.getGuild(), Constants.getLanguage(context));
        });
    }

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
                    (set, statement) -> Log.wrapException("Storing achievement amount", () -> {
                int prevAmount = set.map(it -> {
                    try {
                        return it.getInt("amount");
                    } catch (Exception e) {
                        Log.exception("Getting achievements", e);
                        return 0;
                    }
                }).orElse(0);
                int newAmount = prevAmount + amount;

                statement.setString(1, type);
                statement.setInt(2, newAmount);
                statement.setString(3, profile.getUserId());

                Arrays.stream(Achievements.values()).filter(it -> it.getAchievement().getType().equals(this)).forEach(achievements -> {
                    Achievement achievement = achievements.getAchievement();
                    if (prevAmount < achievement.getAmount() && newAmount >= achievement.getAmount()) {
                        // Achievement got!
                        builder.append("\n");
                        if (tagName) builder.append(user.getAsMention()).append(": ");
                        builder.append(ACHIEVEMENT_GOT_EMOTES[ThreadLocalRandom.current().nextInt(ACHIEVEMENT_GOT_EMOTES.length)])
                                .append(Language.transl(language, "game.achievement.got",
                                        achievement.getName(language), achievement.getDescription(language),
                                        Utility.addNumberDelimitors(achievement.getTokens())))
                                .append("\n").append(Language.transl(language, "game.achievement.runAchievements",
                                    Constants.getPrefix(guild)));

                        profile.addTokens(achievement.getTokens(), "transactions.completeAchievement");
                        REACH_TOKENS.addAmount(tagName, achievement.getTokens(), builder, user, guild, language);
                    }
                });
            }));
        });
    }
}
