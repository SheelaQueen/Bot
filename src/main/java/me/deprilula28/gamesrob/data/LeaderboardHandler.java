package me.deprilula28.gamesrob.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import me.deprilula28.gamesrob.GamesROB;
import me.deprilula28.gamesrob.baseFramework.GamesInstance;
import me.deprilula28.gamesrob.utility.Cache;
import me.deprilula28.gamesrob.utility.Log;
import net.dv8tion.jda.core.entities.User;

import java.sql.ResultSet;
import java.util.*;
import java.util.stream.Collectors;

@AllArgsConstructor
public class LeaderboardHandler {
    private String guildId;

    @Data
    @AllArgsConstructor
    public static class LeaderboardEntry {
        private String id;
        private UserProfile.GameStatistics stats;
    }

    @Data
    @AllArgsConstructor
    public static class UserStatistics {
        private Map<String, UserProfile.GameStatistics> rawMap;

        public UserProfile.GameStatistics getStats(String game) {
            if (rawMap.containsKey(game)) return rawMap.get(game);
            UserProfile.GameStatistics stats = new UserProfile.GameStatistics(0, 0, 0);
            rawMap.put(game, stats);

            return stats;
        }
    }

    public List<LeaderboardEntry> sort(List<LeaderboardEntry> entries) {
        return entries.stream().sorted(Comparator.comparingDouble(it -> ((LeaderboardHandler.LeaderboardEntry) it)
                .getStats().getWonPercent()).reversed())
            .collect(Collectors.toList());
    }

    public UserStatistics getStatsForUser(String userId) {
        if (!GamesROB.database.isPresent()) return null;
        SQLDatabaseManager db = GamesROB.database.get();

        return Cache.get("lb_" + guildId + "_" + userId, n -> {
            Map<String, UserProfile.GameStatistics> userStats = new HashMap<>();

            Log.wrapException("SQL Exception", () -> {
                ResultSet overallSelect = db.select("leaderboardEntries", Arrays.asList("userid", "victories", "losses", "gamesplayed", "gameid"),
                        "guildid = '" + guildId + "' AND userid='" + userId + "'");
                while (overallSelect.next()) {
                    UserProfile.GameStatistics stats = new UserProfile.GameStatistics(overallSelect.getInt("victories"),
                            overallSelect.getInt("losses"),
                            overallSelect.getInt("gamesplayed"));
                    userStats.put(overallSelect.getString("gameid"), stats);
                }
            });

            return new UserStatistics(userStats);
        }, stats -> ((UserStatistics) stats).getRawMap().forEach((key, value) -> saveEntry(db, key,
                new LeaderboardEntry(userId, value))));
    }

    public List<LeaderboardEntry> getEntriesForGame(Optional<String> game) {
        if (!GamesROB.database.isPresent()) return null;
        SQLDatabaseManager db = GamesROB.database.get();
        String gameName = game.orElse("overall");

        return Cache.get("lb_" + guildId + "_" + gameName, n -> {

            List<LeaderboardEntry> entries = new ArrayList<>();
            Log.wrapException("SQL Exception", () -> {
                ResultSet overallSelect = db.select("leaderboardEntries", Arrays.asList("userid", "victories", "losses", "gamesplayed"),
                        "guildid = '" + guildId + "' AND gameid='" + gameName + "'");

                while (overallSelect.next()) {
                    UserProfile.GameStatistics stats = new UserProfile.GameStatistics(overallSelect.getInt("victories"),
                            overallSelect.getInt("losses"),
                            overallSelect.getInt("gamesplayed"));
                    String userId = overallSelect.getString("userid");

                    entries.add(new LeaderboardEntry(userId, stats));
                }
            });

            return sort(entries);
        }, list -> ((List<LeaderboardEntry>) list).forEach(cur -> saveEntry(db, gameName, cur)));
    }

    private void saveEntry(SQLDatabaseManager db, String gameId, LeaderboardEntry entry) {
        db.save("leaderboardEntries", Arrays.asList("victories", "losses", "gamesplayed", "guildid", "userid", "gameid"),
                "guildid = '" + guildId + "' AND userid = '" + entry.getId() + "' AND gameid = '" + gameId + "'",
            set -> {
                try {
                    return set.getString("gameid").equals(gameId) &&
                        set.getString("guildid").equals(guildId) &&
                            new LeaderboardEntry(set.getString("userid"), new UserProfile.GameStatistics(
                                    set.getInt("victories"), set.getInt("losses"),
                                    set.getInt("gamesplayed")
                            )).equals(entry);
                } catch (Exception e) {
                    Log.exception("Saving Leaderboard Entry SQL", e);
                    return false;
                }
            }, (set, statement) -> Log.wrapException("Saving leaderboard entry", () -> {
                statement.setInt(1, entry.getStats().getVictories());
                statement.setInt(2, entry.getStats().getLosses());
                statement.setInt(3, entry.getStats().getGamesPlayed());
                statement.setString(4, guildId);
                statement.setString(5, entry.getId());
                statement.setString(6, gameId);
            }));
    }
}
