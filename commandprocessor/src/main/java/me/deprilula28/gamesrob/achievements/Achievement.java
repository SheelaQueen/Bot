package me.deprilula28.gamesrob.achievements;

import me.deprilula28.gamesrob.Language;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Achievement {
    private String langCode;
    private int tokens;
    private AchievementType type;
    private int amount;

    public String getName(String language) {
        return Language.transl(language, "game.achievement." + langCode + (type == AchievementType.OTHER ? ".name": ""));
    }

    public String getDescription(String language) {
        return type == AchievementType.OTHER
                ? Language.transl(language, "game.achievement." + langCode + ".description")
                : Language.transl(language, "game.achievement." + type.getLanguageCode(), amount);
    }
}
