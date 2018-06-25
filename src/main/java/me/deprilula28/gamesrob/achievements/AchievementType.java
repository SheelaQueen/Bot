package me.deprilula28.gamesrob.achievements;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import me.deprilula28.gamesrob.GamesROB;
import me.deprilula28.gamesrob.Language;
import me.deprilula28.gamesrob.data.UserProfile;
import me.deprilula28.gamesrob.utility.Constants;
import me.deprilula28.gamesrob.utility.Log;
import me.deprilula28.jdacmdframework.CommandContext;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.entities.User;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

@AllArgsConstructor
public enum AchievementType {
    PLAY_GAMES("playGames", new Achievement[]{
            Achievements.realPlayer, Achievements.intensePlayer, Achievements.proPlayer, Achievements.tooManyGames
    }), WIN_GAMES("winGames", new Achievement[] {
            Achievements.crowned, Achievements.master, Achievements.tooGood, Achievements.tryhard
    }), GAMBLE_TOKENS("gambleTokens", new Achievement[] {
            Achievements.gambler, Achievements.addicted, Achievements.highRoller
    }), WIN_TOKENS_GAMBLING("winTokensGambling", new Achievement[] {
            Achievements.onARoll, Achievements.lucky, Achievements.rigged
    }), LOSE_TOKENS_GAMBLING("loseTokensGambling", new Achievement[] {}),
    REACH_PLACE_LEADERBOARD("reachPlaceLeaderboard", new Achievement[] {}),
    REACH_TOKENS("reachTokens", new Achievement[] {}),
    OTHER("other", new Achievement[] {});

    private static final String[] ACHIEVEMENT_GOT_EMOTES = {
        "\uD83D\uDC83", "\uD83D\uDC51", "\uD83D\uDCAB", "<:rules_header:456960990559338497>", "\uD83C\uDF20", "\uD83C\uDF89"
    };

    @Getter private String languageCode;
    private Achievement[] achievements;

    public void addAmount(int amount, CommandContext context, Consumer<MessageBuilder> before) {
        context.send(builder -> {
            before.accept(builder);
            addAmount(false, amount, builder, context.getAuthor(), Constants.getLanguage(context));
        });
    }

    public void addAmount(boolean tagName, int amount, MessageBuilder builder, User user, String language) {
        GamesROB.database.ifPresent(db -> {
            UserProfile profile = UserProfile.get(user);
            db.save("achievements", Arrays.asList("type", "amount", "userid"),
                    "userid = '" + profile.getUserId() + "'", set -> true, (set, statement) ->
                            Log.wrapException("Storing achievement amount", () -> {
                int prevAmount = set.map(it -> {
                    try {
                        return it.getInt("amount");
                    } catch (Exception e) {
                        Log.exception("Getting achievements", e);
                        return null;
                    }
                }).orElse(0);
                int newAmount = prevAmount + amount;

                statement.setString(1, toString());
                statement.setInt(2, newAmount);
                statement.setString(3, profile.getUserId());

                for (Achievement achievement : achievements)
                    if (prevAmount < achievement.getAmount() && newAmount >= achievement.getAmount()) {
                        // Achievement got!
                        builder.append("\n");
                        if (tagName) builder.append(user.getAsMention()).append(": ");
                        builder.append(ACHIEVEMENT_GOT_EMOTES[ThreadLocalRandom.current().nextInt(ACHIEVEMENT_GOT_EMOTES.length)])
                                .append(Language.transl(language, "game.achievement.got",
                                        achievement.getName(language), achievement.getDescription(language),
                                        achievement.getTokens()));
                        profile.addTokens(achievement.getTokens());
                    }
            }));
        });
    }
}
