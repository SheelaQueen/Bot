package me.deprilula28.gamesrob.achievements;

import lombok.AllArgsConstructor;
import lombok.Data;
import me.deprilula28.gamesrob.GamesROB;
import me.deprilula28.gamesrob.Language;
import me.deprilula28.gamesrob.data.UserProfile;

import java.util.Arrays;

@AllArgsConstructor
@Data
public class Achievement {
    private String langCode;
    private int tokens;
    private AchievementType type;
    private int amount;

    public static enum AchievementType {
        PLAY_GAMES, WIN_GAMES, GAMBLE_TOKENS, WIN_TOKENS_GAMBLING, LOSE_TOKENS_GAMBLING, REACH_PLACE_LEADERBOARD,
        REACH_TOKENS, OTHER;
    }

    public String getName(String language) {
        return Language.transl(langCode + ".name", language);
    }

    public String getDescription(String language) {
        return Language.transl(langCode + ".description", language);
    }

    public void addAmount(UserProfile profile, int amount) {
        GamesROB.database.ifPresent(db -> {
            db.save("achievements", Arrays.asList("type", "amount", "userid"),
                    "userid = '" + profile.getUserId() + "'", set -> true, statement -> {
                // TODO
            });
        });
    }
}
