package me.deprilula28.gamesrob.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import me.deprilula28.gamesrobshardcluster.utilities.Constants;
import me.deprilula28.gamesrobshardcluster.utilities.Log;
import me.deprilula28.gamesrob.utility.Utility;
import net.dv8tion.jda.core.entities.Guild;

import java.io.*;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.function.Supplier;

@Data
@AllArgsConstructor
public class GuildProfile {
    private String guildId;

    private String guildPrefix;
    private String language;
    private String backgroundImageUrl;
    private boolean edited;

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
            ResultSet select = db.select("guildData", Arrays.asList("prefix", "language", "leaderboardbackgroundimgurl"),
                    "guildid = '" + from + "'");

            if (select.next()) return fromResultSet(from, select);
            select.close();

            return Optional.empty();
        }

        @Override
        public Utility.Promise<Void> saveToSQL(SQLDatabaseManager db, GuildProfile value) {
            return db.save("guildData", Arrays.asList(
                    "prefix", "language", "leaderboardbackgroundimgurl", "guildid"
            ), "guildid = '" + value.getGuildId() + "'",
                set -> !value.isEdited(), true,
                (set, it) -> Log.wrapException("Saving data on SQL", () -> writeGuildData(it, value)));
        }

        private Optional<GuildProfile> fromResultSet(String from, ResultSet select) {
            try {
                return Optional.of(new GuildProfile(from,
                        select.getString("prefix"), select.getString("language"),
                        select.getString("leaderboardbackgroundimgurl"), false));
            } catch (Exception e) {
                Log.exception("Saving GuildProfile in SQL", e);
                return Optional.empty();
            }
        }

        private void writeGuildData(PreparedStatement statement, GuildProfile profile) throws Exception {
            statement.setString(1, profile.getGuildPrefix());
            statement.setString(2, profile.getLanguage());
            statement.setString(3, profile.getBackgroundImageUrl());
            statement.setString(4, profile.getGuildId());
        }


        private void saveStats(DataOutputStream stream, UserProfile.GameStatistics stats) throws Exception {
            stream.writeInt(stats.getVictories());
            stream.writeInt(stats.getLosses());
            stream.writeInt(stats.getGamesPlayed());
        }

        private void saveEntries(DataOutputStream stream, List<LeaderboardHandler.LeaderboardEntry> entries) throws Exception {
            stream.writeInt(entries.size());
            for (LeaderboardHandler.LeaderboardEntry entry : entries) {
                stream.writeUTF(entry.getId());
                saveStats(stream, entry.getStats());
            }
        }

        @Override
        public GuildProfile createNew(String from) {
            return new GuildProfile(from, "g*", Constants.DEFAULT_LANGUAGE, null,false);
        }

        @Override
        public File getDataFile(String from) {
            File guildFolder = new File(Constants.GUILDS_FOLDER, from);
            return new File(guildFolder, "leaderboard.json");
        }
    }
}
