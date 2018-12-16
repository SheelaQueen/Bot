package me.deprilula28.gamesrob.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import me.deprilula28.gamesrob.GamesROB;
import me.deprilula28.gamesrob.utility.Utility;
import me.deprilula28.gamesrobshardcluster.GamesROBShardCluster;
import me.deprilula28.gamesrobshardcluster.utilities.Constants;
import me.deprilula28.gamesrobshardcluster.utilities.Log;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Message;

import java.io.File;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Data
@AllArgsConstructor
public class GuildProfile {
    private String guildId;

    private String guildPrefix;
    private String language;
    private String backgroundImageUrl;
    private long tournmentEnds;
    private boolean customEconomy;
    private long leaderboardChannelId;
    private long leaderboardMessageId;
    private boolean edited;

    public Utility.Promise<Message> getLeaderboardMessage() {
        Utility.Promise<Message> promise = new Utility.Promise<>();
        GamesROB.getGuildById(guildId).ifPresent(guild -> guild.getTextChannelById(leaderboardChannelId)
            .getMessageById(leaderboardMessageId).queue(promise::done));

        return promise;
    }

    public LeaderboardHandler getLeaderboard() {
        return new LeaderboardHandler(guildId);
    }

    public int getIndex(List<LeaderboardHandler.LeaderboardEntry> list, String id) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId().equals(id)) return i;
        }
        return -1;
    }

    public static GuildSaveManager manager = new GuildSaveManager();

    public static GuildProfile get(Guild guild) {
        return manager.get(guild.getId(), GuildProfile.class);
    }

    public static GuildProfile get(String guild) {
        return manager.get(guild, GuildProfile.class);
    }

    public static class GuildSaveManager extends DataManager<String, GuildProfile> {
        @Override
        public Optional<GuildProfile> getFromSQL(SQLDatabaseManager db, String from) throws Exception {
            ResultSet select = db.select("guilddata", Arrays.asList("prefix", "language", "leaderboardbackgroundimgurl",
                    "tournmentend", "customeco", "lbchid", "lbmsgid"),
                    "guildid = '" + from + "'");

            if (select.next()) return fromResultSet(from, select);
            select.close();

            return Optional.empty();
        }

        @Override
        public Utility.Promise<Void> saveToSQL(SQLDatabaseManager db, GuildProfile value) {
            return db.save("guilddata", Arrays.asList(
                    "prefix", "language", "leaderboardbackgroundimgurl", "tournmentend", "customeco", "lbchid", "lbmsgid", "guildid"
            ), "guildid = '" + value.getGuildId() + "'",
                set -> !value.isEdited(), true,
                (set, it) -> Log.wrapException("Saving data on SQL", () -> writeGuildData(it, value)));
        }

        private Optional<GuildProfile> fromResultSet(String from, ResultSet select) {
            try {
                return Optional.of(new GuildProfile(from,
                        select.getString("prefix"), select.getString("language"),
                        select.getString("leaderboardbackgroundimgurl"),
                        select.getLong("tournmentend"), select.getBoolean("customeco"),
                        select.getLong("lbchid"), select.getLong("lbmsgid"), false));
            } catch (Exception e) {
                Log.exception("Saving GuildProfile in SQL", e);
                return Optional.empty();
            }
        }

        private void writeGuildData(PreparedStatement statement, GuildProfile profile) throws Exception {
            statement.setString(1, profile.getGuildPrefix());
            statement.setString(2, profile.getLanguage());
            statement.setString(3, profile.getBackgroundImageUrl());
            statement.setLong(4, profile.getTournmentEnds());
            statement.setBoolean(5, profile.isCustomEconomy());
            statement.setLong(6, profile.getLeaderboardChannelId());
            statement.setLong(7, profile.getLeaderboardMessageId());
            statement.setString(8, profile.getGuildId());
        }

        @Override
        public GuildProfile createNew(String from) {
            return new GuildProfile(from, GamesROBShardCluster.premiumBot ? Constants.DEFAULT_PREMIUM_PREFIX : Constants.DEFAULT_PREFIX,
                    Constants.DEFAULT_LANGUAGE, null, -1L, false, -1L, -1L,false);
        }

        @Override
        public File getDataFile(String from) {
            File guildFolder = new File(Constants.GUILDS_FOLDER, from);
            return new File(guildFolder, "leaderboard.json");
        }
    }
}
