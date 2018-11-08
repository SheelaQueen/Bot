package me.deprilula28.gamesrob.baseFramework;

import me.deprilula28.gamesrob.Language;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.function.Supplier;

@Data
@AllArgsConstructor
public class GamesInstance {
    private String languageCode;
    private String aliases;

    private int minTargetPlayers;
    private int maxTargetPlayers;
    private GameType gameType;
    private boolean requireBetting;
    private boolean allowAi;

    private transient Supplier<MatchHandler> matchHandlerSupplier;
    private transient Class<? extends MatchHandler> matchHandlerClass;

    private List<GameMode> modes;

    public String getName(String language) {
        return Language.transl(language, "game." + getLanguageCode() + ".name");
    }

    public String getShortDescription(String language) {
        return Language.transl(language, "game." + getLanguageCode() + ".shortDescription");
    }

    public String getLongDescription(String language) {
        return Language.transl(language, "game." + getLanguageCode() + ".longDescription");
    }

    @Data
    @AllArgsConstructor
    public static class GameMode {
        private String languageCode;
        private String aliases;

        public String getName(String language) {
            return Language.transl(language, "game.mode." + languageCode + ".name");
        }

        public String getDescription(String language) {
            return Language.transl(language, "game.mode." + getLanguageCode() + ".description");
        }
    }
}
