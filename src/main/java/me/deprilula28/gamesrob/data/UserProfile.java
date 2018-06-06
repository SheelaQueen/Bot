package me.deprilula28.gamesrob.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import me.deprilula28.gamesrob.GamesROB;
import me.deprilula28.gamesrob.baseFramework.GamesInstance;
import me.deprilula28.gamesrob.utility.Constants;
import me.deprilula28.gamesrob.utility.Log;
import me.deprilula28.gamesrob.utility.Utility;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.User;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.Optional;

@Data
@AllArgsConstructor
public class UserProfile {
    private String userID;
    private String emote;
    private String language;
    private int tokens;
    private long lastUpvote;
    private int upvotedDays;
    private int shardId;

    public GuildProfile.UserStatistics getStatsForGuild(Guild guild) {
        return GuildProfile.get(guild).getUserStats(userID);
    }

    @Data
    @AllArgsConstructor
    public static class GameStatistics {
        public int victories;
        public int losses;
        public int gamesPlayed;

        public double getWLRatio() {
            double ratio = (double) victories / (double) losses;
            if (Double.isNaN(ratio) || Double.isInfinite(ratio)) return (double) victories;
            else return ratio;
        }
    }

    public void addTokens(int amount) {
        tokens += amount;
    }

    public boolean transaction(int amount) {
        if (tokens >= amount) {
            tokens -= amount;
            return true;
        } else return false;
    }

    public void registerGameResult(Guild guild, User user, boolean victory, boolean loss, GamesInstance game) {
        if (victory) addTokens(40);

        GuildProfile.UserStatistics stats = getStatsForGuild(guild);
        Log.info(stats);
        registerGameResult(stats.getOverall(), victory, loss);

        if (stats.getGamesStats().containsKey(game.getLanguageCode())) {
            registerGameResult(stats.getGamesStats().get(game.getLanguageCode()), victory, loss);
        } else {
            stats.getGamesStats().put(game.getLanguageCode(), new GameStatistics(victory ? 1 : 0, victory ? 0 : 1, 1));
        }
        GuildProfile.get(guild).getUserStatisticsMap().put(user.getId(), stats);
        GuildProfile.get(guild).onUserProfileSaved(this);
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
            ResultSet select = db.select("userData", Arrays.asList("emote", "language", "tokens", "lastUpvote",
                    "upvotedDays", "shardId"),
                    "userId = '" + from + "'");
            if (select.next()) {
                return Optional.of(new UserProfile(from, select.getString("emote"),
                        select.getString("language"), select.getInt("tokens"),
                        select.getLong("lastUpvote"), select.getInt("upvotedDays"),
                        select.getInt("shardId")));
            }
            select.close();

            return Optional.empty();
        }

        private UserProfile.GameStatistics readEntry(DataInputStream stream) throws Exception {
            return new UserProfile.GameStatistics(stream.readInt(), stream.readInt(), stream.readInt());
        }

        @Override
        public Utility.Promise<Void> saveToSQL(SQLDatabaseManager db, UserProfile value) {
            return db.save("userData", Arrays.asList(
                    "emote", "userId", "tokens", "lastUpvote", "upvotedDays", "shardId", "language"
            ), "userId = '" + value.getUserID() + "'", it -> Log.wrapException("Saving data on SQL", () -> write(it, value)));
            /*
            if (value.isExists()) {
                db.update("userData", Arrays.asList("emote"), "userID = ?",
                        it -> Log.wrapException("Saving to SQL", () -> write(it, value)));
            } else {
                db.insert("userData", Arrays.asList("emote", "userID"),
                        it -> Log.wrapException("Inserting to SQL", () -> write(it, value)));
            }
            */
        }

        private void write(PreparedStatement statement, UserProfile profile) throws Exception {
            statement.setString(1, profile.getEmote());
            statement.setString(2, profile.getUserID());
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
