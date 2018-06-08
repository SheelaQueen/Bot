package me.deprilula28.gamesrob.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import me.deprilula28.gamesrob.GamesROB;
import me.deprilula28.gamesrob.baseFramework.GamesInstance;
import me.deprilula28.gamesrob.utility.Constants;
import me.deprilula28.gamesrob.utility.Log;
import me.deprilula28.gamesrob.utility.Utility;
import me.deprilula28.jdacmdframework.CommandContext;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Role;

import java.io.*;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

@Data
@AllArgsConstructor
public class GuildProfile {
    private String guildId;
    private final List<LeaderboardEntry> overall;
    private final Map<String, List<LeaderboardEntry>> perGame;
    private final Map<String, UserStatistics> userStatisticsMap;

    private String guildPrefix;
    private String permStartGame;
    private String permStopGame;
    private String language;
    private int shardId;

    private boolean rolePermission(CommandContext context, String value, Supplier<Boolean> fallback) {
        boolean out;
        if (value == null) out = fallback.get();
        else {
            Role role = context.getGuild().getRoleById(value);
            out = role == null ? fallback.get() : role.getPosition() <=
                    context.getAuthorMember().getRoles().stream().mapToInt(Role::getPosition).max().orElse(0);
        }

        return out || context.getAuthorMember().isOwner() || context.getAuthorMember().hasPermission(Permission.ADMINISTRATOR)
                || GamesROB.owners.contains(context.getAuthor().getIdLong());
    }

    public boolean canStart(CommandContext context) {
        return rolePermission(context, permStartGame, () -> true);
    }

    public boolean canStop(CommandContext context) {
        return rolePermission(context, permStopGame, () -> context.getAuthorMember().hasPermission(Permission.MANAGE_SERVER));
    }

    public UserStatistics getUserStats(String id) {
        if (!userStatisticsMap.containsKey(id)) userStatisticsMap.put(id, new UserStatistics(
                new UserProfile.GameStatistics(0, 0, 0), new HashMap<>()
        ));
        return userStatisticsMap.get(id);
    }

    public int getIndex(List<LeaderboardEntry> list, String id) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId().equals(id)) return i;
        }
        return -1;
    }

    @Data
    @AllArgsConstructor
    public static class UserStatistics {
        public UserProfile.GameStatistics overall;
        public Map<String, UserProfile.GameStatistics> gamesStats;

        public UserProfile.GameStatistics getGameStats(GamesInstance game) {
            return getGameStats(game.getLanguageCode());
        }

        public UserProfile.GameStatistics getGameStats(String gameName) {
            if (!gamesStats.containsKey(gameName)) gamesStats.put(gameName,
                    new UserProfile.GameStatistics(0, 0, 0));
            return gamesStats.get(gameName);
        }
    }

    @Data
    @AllArgsConstructor
    public static class LeaderboardEntry {
        private String id;
        private UserProfile.GameStatistics stats;
    }

    public List<LeaderboardEntry> getOption(GamesInstance game) {
        return getOption(game.getLanguageCode());
    }

    public List<LeaderboardEntry> getOption(String gameLangCode) {
        if (!perGame.containsKey(gameLangCode)) perGame.put(gameLangCode, new CopyOnWriteArrayList<>());
        return perGame.get(gameLangCode);
    }

    public void onUserProfileSaved(UserProfile profile) {
        Log.wrapException("Failed to save leaderboard", () -> {
            synchronized (userStatisticsMap) {
                if (!userStatisticsMap.containsKey(profile.getUserID()))
                    userStatisticsMap.put(profile.getUserID(),
                        new UserStatistics(new UserProfile.GameStatistics(0, 0, 0),
                                new HashMap<>()));
                UserStatistics userStatistics = userStatisticsMap.get(profile.getUserID());
                handleOrganizeGame(new LeaderboardEntry(profile.getUserID(), userStatistics.getOverall()), overall);
                userStatistics.getGamesStats().forEach((game, stats) -> {
                    if (!perGame.containsKey(game)) perGame.put(game, new CopyOnWriteArrayList<>());
                    handleOrganizeGame(new LeaderboardEntry(profile.getUserID(), stats), perGame.get(game));
                });
            }
        });
    }

    private void handleOrganizeGame(LeaderboardEntry entry, List<LeaderboardEntry> list) {
        if (entry.getStats().getGamesPlayed() < Constants.LEADERBOARD_GAMES_PLAYED_REQUIREMENT) return;
        list.removeIf(it -> it.getId().equals(entry.getId()));
        list.add(entry);
    }

    public static GuildSaveManager manager = new GuildSaveManager();

    public static GuildProfile get(Guild guild) {
        return manager.get(guild.getId(), GuildProfile.class);
    }

    public static GuildProfile get(String guild) {
        return manager.get(guild, GuildProfile.class);
    }

    public static class GuildSaveManager extends DataManager<String, GuildProfile> {
        private static List<LeaderboardEntry> getEntriesForGame(SQLDatabaseManager db, String from, String game,
                                                    Map<String, UserStatistics> userStats) throws Exception {
            ResultSet overallSelect = db.select("leaderboardEntries", Arrays.asList("userId", "victories", "losses", "gamesPlayed"),
                    "guildId = '" + from + "' AND gameId='" + game + "'");
            List<LeaderboardEntry> entries = new ArrayList<>();

            while (overallSelect.next()) {
                UserProfile.GameStatistics stats = new UserProfile.GameStatistics(overallSelect.getInt("victories"),
                        overallSelect.getInt("losses"),
                        overallSelect.getInt("gamesPlayed"));
                String userId = overallSelect.getString("userId");

                if (game.equals("overall")) userStats.put(userId, new UserStatistics(stats, new HashMap<>()));
                else userStats.get(userId).getGamesStats().put(game, stats);

                entries.add(new LeaderboardEntry(userId, stats));
            }

            return entries;
        }

        @Override
        public Optional<GuildProfile> getFromSQL(SQLDatabaseManager db, String from) throws Exception {
            ResultSet select = db.select("guildData", Arrays.asList("permStartGame",
                    "permStopGame", "prefix", "language", "shardId"), "guildId = '" + from + "'");

            if (select.next()) {
                // Overall leaderboard entries

                /*
                DataInputStream overallStream = new DataInputStream(new ByteArrayInputStream(select.getBytes("leaderboardEntries")));
                List<LeaderboardEntry> overall = readEntries(overallStream);
                Utility.quietlyClose(overallStream);
*/
                Map<String, UserStatistics> userStats = new HashMap<>();
                List<LeaderboardEntry> overall = getEntriesForGame(db, from, "overall", userStats);

                // Per game leaderboard entries
                /*
                DataInputStream gameStreams = new DataInputStream(new ByteArrayInputStream(select.getBytes("gameEntries")));

                int left = gameStreams.readInt();
                Map<String, List<LeaderboardEntry>> perGame = new HashMap<>();
                while (left -- > 0) {
                    perGame.put(gameStreams.readUTF(), readEntries(gameStreams));
                }
                Utility.quietlyClose(gameStreams);
                */
                Map<String, List<LeaderboardEntry>> perGame = new HashMap<>();
                for (GamesInstance game : GamesROB.ALL_GAMES) {
                    perGame.put(game.getLanguageCode(), getEntriesForGame(db, from, game.getLanguageCode(), userStats));
                }

                // User statistics data
                /*
                DataInputStream userStatisticsStreams = new DataInputStream(new ByteArrayInputStream(select.getBytes("userStatisticsMap")));

                left = userStatisticsStreams.readInt();
                Map<String, UserStatistics> userStats = new HashMap<>();
                while (left -- > 0) {
                    userStats.put(userStatisticsStreams.readUTF(), new UserStatistics(readStats(userStatisticsStreams),
                           readGameStats(userStatisticsStreams)));
                }
                Utility.quietlyClose(userStatisticsStreams);
                */

                return Optional.of(new GuildProfile(from, overall, perGame, userStats,
                        select.getString("prefix"), select.getString("permStartGame"),
                        select.getString("permStopGame"), select.getString("language"),
                        select.getInt("shardId")));
            }
            select.close();
            Log.info("Next not found!");

            return Optional.empty();
        }

        private Map<String, UserProfile.GameStatistics> readGameStats(DataInputStream stream) throws Exception {
            int left = stream.readInt();
            Map<String, UserProfile.GameStatistics> map = new HashMap<>();

            while (left -- > 0) {
                map.put(stream.readUTF(), readStats(stream));
            }

            return map;
        }

        private UserProfile.GameStatistics readStats(DataInputStream stream) throws Exception {
            return new UserProfile.GameStatistics(stream.readInt(), stream.readInt(), stream.readInt());
        }

        private List<LeaderboardEntry> readEntries(DataInputStream stream) throws Exception {
            int left = stream.readInt();
            List<LeaderboardEntry> list = new ArrayList<>();
            while (left -- > 0) {
                list.add(new LeaderboardEntry(stream.readUTF(), readStats(stream)));
            }
            return list;
        }

        @Override
        public Utility.Promise<Void> saveToSQL(SQLDatabaseManager db, GuildProfile value) {
            Log.wrapException("Saving data of SQL", () -> writeLeaderboardEntries(db, value));
            return db.save("guildData", Arrays.asList("prefix", "permStartGame", "permStopGame", "shardId", "guildId"),
                    "guildId = '" + value.getGuildId() + "'", it -> Log.wrapException("Saving data on SQL",
                    () -> writeGuildData(it, value)));
            /*if (value.isExists()) {
                db.update("guildData", Arrays.asList("leaderboardEntries", "gameEntries", "userStatisticsMap",
                        "prefix", "permStartGame", "permStopGame"), "guildId = ?",
                        it -> Log.wrapException("Saving to SQL", () -> write(it, value)));
            } else {
                db.insert("guildData", Arrays.asList("leaderboardEntries", "gameEntries", "userStatisticsMap",
                        "prefix", "permStartGame", "permStopGame", "guildId"),
                        it -> Log.wrapException("Inserting to SQL", () -> write(it, value)));
            }
            */
        }

        private void writeLeaderboardEntries(SQLDatabaseManager db, GuildProfile profile) throws Exception {
            profile.getUserStatisticsMap().forEach((userId, stats) -> {
                writeGameEntries(db, userId, profile.getGuildId(), "overall", stats.getOverall());
                stats.getGamesStats().forEach((gameId, gameStats) ->
                        writeGameEntries(db, userId, profile.getGuildId(),  gameId, gameStats));
            });
        }

        private void writeGameEntries(SQLDatabaseManager db, String userId, String guildId, String gameId, UserProfile.GameStatistics stats) {
            db.save("leaderboardEntries", Arrays.asList("userId", "victories", "losses", "gamesPlayed", "guildId", "gameId"),
                    "guildId = '" + guildId + "' AND userId='" + userId + "' AND gameId = '" + gameId + "'",
                    it -> Log.wrapException("Saving data on SQL", () -> {
                        it.setString(1, userId);
                        it.setInt(2, stats.getVictories());
                        it.setInt(3, stats.getLosses());
                        it.setInt(4, stats.getGamesPlayed());
                        it.setString(5, guildId);
                        it.setString(6, gameId);
            }));
        }

        private void writeGuildData(PreparedStatement statement, GuildProfile profile) throws Exception {
            /*
            // Overall leaderboard entries
            ByteArrayOutputStream overallBaos = new ByteArrayOutputStream();
            DataOutputStream overallStream = new DataOutputStream(overallBaos);

            saveEntries(overallStream, profile.getOverall());
            statement.setBytes(1, overallBaos.toByteArray());
            Utility.quietlyClose(overallStream);

            // Per game leaderboard entries
            ByteArrayOutputStream gameBaos = new ByteArrayOutputStream();
            DataOutputStream gameStream = new DataOutputStream(gameBaos);

            gameStream.writeInt(profile.getPerGame().size());
            for (Map.Entry<String, List<LeaderboardEntry>> cur : profile.getPerGame().entrySet()) {
                gameStream.writeUTF(cur.getKey());
                saveEntries(gameStream, cur.getValue());
            }

            statement.setBytes(2, gameBaos.toByteArray());
            Utility.quietlyClose(gameStream);

            // User Statistics
            ByteArrayOutputStream userStatsBaos = new ByteArrayOutputStream();
            DataOutputStream userStatsStream = new DataOutputStream(userStatsBaos);

            userStatsStream.writeInt(profile.getUserStatisticsMap().size());
            for (Map.Entry<String, UserStatistics> cur : profile.getUserStatisticsMap().entrySet()) {
                userStatsStream.writeUTF(cur.getKey());
                saveStats(userStatsStream, cur.getValue().getOverall());

                userStatsStream.writeInt(cur.getValue().getGamesStats().size());
                for (Map.Entry<String, UserProfile.GameStatistics> gameStat : cur.getValue().getGamesStats().entrySet()) {
                    userStatsStream.writeUTF(gameStat.getKey());
                    saveStats(userStatsStream, gameStat.getValue());
                }
            }

            statement.setBytes(3, userStatsBaos.toByteArray());
            Utility.quietlyClose(userStatsStream);
*/
            statement.setString(1, profile.getGuildPrefix());
            statement.setString(2, profile.getPermStartGame());
            statement.setString(3, profile.getPermStopGame());
            statement.setInt(4, profile.getShardId());
            statement.setString(5, profile.getGuildId());
        }


        private void saveStats(DataOutputStream stream, UserProfile.GameStatistics stats) throws Exception {
            stream.writeInt(stats.getVictories());
            stream.writeInt(stats.getLosses());
            stream.writeInt(stats.getGamesPlayed());
        }

        private void saveEntries(DataOutputStream stream, List<LeaderboardEntry> entries) throws Exception {
            stream.writeInt(entries.size());
            for (LeaderboardEntry entry : entries) {
                stream.writeUTF(entry.getId());
                saveStats(stream, entry.getStats());
            }
        }

        @Override
        public GuildProfile createNew(String from) {
            return new GuildProfile(from, new ArrayList<>(), new HashMap<>(), new HashMap<>(),
                    "g*", null, null, Constants.DEFAULT_LANGUAGE,
                    GamesROB.getGuildById(from).map(it -> it.getJDA().getShardInfo().getShardId()).orElse(0));
        }

        @Override
        public File getDataFile(String from) {
            File guildFolder = new File(Constants.GUILDS_FOLDER, from);
            return new File(guildFolder, "leaderboard.json");
        }
    }
}
