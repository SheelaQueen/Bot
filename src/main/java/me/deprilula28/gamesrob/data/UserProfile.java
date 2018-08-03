package me.deprilula28.gamesrob.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import me.deprilula28.gamesrob.GamesROB;
import me.deprilula28.gamesrob.achievements.AchievementType;
import me.deprilula28.gamesrob.baseFramework.GamesInstance;
import me.deprilula28.gamesrob.utility.Cache;
import me.deprilula28.gamesrob.utility.Constants;
import me.deprilula28.gamesrob.utility.Log;
import me.deprilula28.gamesrob.utility.Utility;
import me.deprilula28.jdacmdframework.CommandContext;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.User;

import java.io.DataOutputStream;
import java.io.File;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Data
@AllArgsConstructor
public class UserProfile {
    private String userId;
    private String emote;
    private String language;
    private int tokens;
    private long lastUpvote;
    private int upvotedDays;
    private int shardId;

    public LeaderboardHandler.UserStatistics getStatsForGuild(Guild guild) {
        return GuildProfile.get(guild).getLeaderboard().getStatsForUser(userId);
    }

    @Data
    @AllArgsConstructor
    public static class GameStatistics {
        public int victories;
        public int losses;
        public int gamesPlayed;

        public double getWonPercent() {
            if (gamesPlayed == 0) return 0.0;
            return ((double) victories / (double) gamesPlayed) * 100L;
        }
    }

    @Data
    @AllArgsConstructor
    public static class Transaction {
        private UserProfile profile;
        private int amount;
        private long time;
        private String message;
    }

    private static final List<String> TRANSACTION_MESSAGES = Arrays.asList(
            "upvote", "completeAchievement", "cheater", "give", "got", "winGamePrize", "betting", "slots", "changingEmote"
    );

    public void addTokens(int amount, String message) {
        tokens += amount;
        GamesROB.database.ifPresent(it ->
            it.insert("transactions", Arrays.asList("userid", "amount", "time", "message"),
                sql -> Log.wrapException("Inserting SQL transaction log", () -> {
             sql.setString(1, userId);
             sql.setInt(2, amount);
             sql.setLong(3, System.currentTimeMillis());
             sql.setShort(4, (short) TRANSACTION_MESSAGES.indexOf(message.substring("transactions.".length())));
        })));
    }

    public List<Transaction> getTransactions(int limit, int offset) {
        if (!GamesROB.database.isPresent()) return new ArrayList<>();
        return Cache.get("transactions_" + userId + "_" + limit + "_" + offset, it -> {
            try {
                ResultSet set = GamesROB.database.get().select("transactions", Arrays.asList("amount", "time", "message"),
                        "userid = '" + userId + "'", "time", true, limit, offset);
                List<Transaction> transactions = new ArrayList<>();

                while (set.next()) transactions.add(new Transaction(
                        this, set.getInt("amount"), set.getLong("time"),
                        "transactions." + TRANSACTION_MESSAGES.get(set.getShort("message"))
                ));
                set.close();

                return transactions;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public int getTransactionAmount() {
        if (!GamesROB.database.isPresent()) return 0;
        return Cache.get("transactionscount_" + userId, it -> {
            try {
                return GamesROB.database.get().getSize("transactions", "userid = '" + userId + "'");
            } catch (Exception e) {
                return 0;
            }
        });
    }

    public boolean transaction(int amount, String message) {
        if (tokens >= amount) {
            addTokens(-amount, message);
            return true;
        } else return false;
    }

    public void registerGameResult(Guild guild, User user, boolean victory, boolean loss, GamesInstance game) {
        LeaderboardHandler.UserStatistics stats = getStatsForGuild(guild);
        registerGameResult(stats.getStats("overall"), victory, loss);
        registerGameResult(stats.getStats(game.getLanguageCode()), victory, loss);
    }

    private void registerGameResult(GameStatistics stats, boolean victory, boolean loss) {
        if (victory) stats.victories ++;
        else if (loss) stats.losses ++;
        stats.gamesPlayed ++;
    }

    public static UserSaveManager manager = new UserSaveManager();

    public static UserProfile get(User user) {
        return get(user.getId());
    }

    public static UserProfile get(String id) {
        return manager.get(id, UserProfile.class);
    }

    public static class UserSaveManager extends DataManager<String, UserProfile> {
        @Override
        public Optional<UserProfile> getFromSQL(SQLDatabaseManager db, String from) throws Exception {
            ResultSet select = db.select("userData", Arrays.asList("emote", "language", "tokens", "lastupvote",
                    "upvoteddays", "shardid"),
                    "userid = '" + from + "'");
            if (select.next()) return fromResultSet(from, select);
            select.close();

            return Optional.empty();
        }

        @Override
        public Utility.Promise<Void> saveToSQL(SQLDatabaseManager db, UserProfile value) {
            return db.save("userData", Arrays.asList(
                    "emote", "userId", "tokens", "lastupvote", "upvoteddays", "shardid", "language"
            ), "userid = '" + value.getUserId() + "'",
                set -> fromResultSet(value.getUserId(), set).equals(Optional.of(value)),
                (set, it) -> Log.wrapException("Saving data on SQL", () -> write(it, value)));
        }

        private Optional<UserProfile> fromResultSet(String from, ResultSet select) {
            try {
                return Optional.of(new UserProfile(from, select.getString("emote"),
                        select.getString("language"), select.getInt("tokens"),
                        select.getLong("lastupvote"), select.getInt("upvoteddays"),
                        select.getInt("shardid")));
            } catch (Exception e) {
                Log.exception("Saving UserProfile in SQL", e);
                return Optional.empty();
            }
        }

        private void write(PreparedStatement statement, UserProfile profile) throws Exception {
            statement.setString(1, profile.getEmote());
            statement.setString(2, profile.getUserId());
            statement.setInt(3, profile.getTokens());
            statement.setLong(4, profile.getLastUpvote());
            statement.setInt(5, profile.getUpvotedDays());
            statement.setInt(6, profile.getShardId());
            statement.setString(7, profile.getLanguage());
        }

        private void saveStatistics(DataOutputStream stream, GameStatistics stats) throws Exception {
            stream.writeInt(stats.getVictories());
            stream.writeInt(stats.getLosses());
            stream.writeInt(stats.getGamesPlayed());
        }

        @Override
        public UserProfile createNew(String from) {
            return new UserProfile(from, null, null, 0, 0, 0,
                    GamesROB.getUserById(from).map(it -> it.getJDA().getShardInfo().getShardId()).orElse(0));
        }

        @Override
        public File getDataFile(String from) {
            return new File(Constants.USER_PROFILES_FOLDER, from + ".json");
        }
    }
}
