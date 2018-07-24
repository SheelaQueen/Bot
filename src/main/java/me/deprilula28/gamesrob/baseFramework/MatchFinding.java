package me.deprilula28.gamesrob.baseFramework;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.dv8tion.jda.core.entities.Game;

import java.util.*;

public class MatchFinding {
    @AllArgsConstructor
    @Data
    private static class MatchType {
        private GamesInstance game;
        private Map<String, Integer> settings;
        private int playerCount;
    }

    private static Map<MatchType, Match> matchFinderList = new HashMap<>();

    public static void signIn(Match match) {
        MatchType type = new MatchType(match.getGame(), match.getSettings(), match.getTargetPlayerCount());
        if (matchFinderList.containsKey(type)) {
            Match main = matchFinderList.get(type);
            // TODO Some joining magic
        } else matchFinderList.put(type, match);
    }
}
