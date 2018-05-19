package me.deprilula28.gamesrob.baseFramework;

import lombok.AllArgsConstructor;
import lombok.Data;
import me.deprilula28.gamesrob.Language;

import java.util.function.Supplier;

@Data
@AllArgsConstructor
public class GamesInstance {
    private String languageCode;
    private String aliases;

    private int minTargetPlayers;
    private int maxTargetPlayers;
    private GameType gameType;

    private transient Supplier<MatchHandler> matchHandlerSupplier;
    private transient Class<? extends MatchHandler> matchHandlerClass;

    public String getName(String language) {
        return Language.transl(language, "game." + getLanguageCode() + ".name");
    }

    public String getShortDescription(String language) {
        return Language.transl(language, "game." + getLanguageCode() + ".shortDescription");
    }

    public String getLongDescription(String language) {
        return Language.transl(language, "game." + getLanguageCode() + ".longDescription");
    }
}
