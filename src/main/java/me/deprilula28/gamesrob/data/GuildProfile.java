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

    public LeaderboardHandler getLeaderboard() {
        return new LeaderboardHandler(guildId);
    }

    public boolean canStart(CommandContext context) {
        return rolePermission(context, permStartGame, () -> true);
    }

    public boolean canStop(CommandContext context) {
        return rolePermission(context, permStopGame, () -> context.getAuthorMember().hasPermission(Permission.MANAGE_SERVER));
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
            ResultSet select = db.select("guildData", Arrays.asList("permstartgame",
                    "permstopgame", "prefix", "language", "shardid"), "guildid = '" + from + "'");

            if (select.next()) return fromResultSet(from, select);
            select.close();
            Log.info("Next not found!");

            return Optional.empty();
        }

        @Override
        public Utility.Promise<Void> saveToSQL(SQLDatabaseManager db, GuildProfile value) {
            return db.save("guildData", Arrays.asList(
                    "prefix", "permstartgame", "permstopgame", "shardid", "language", "guildid"
            ), "guildid = '" + value.getGuildId() + "'",
                set -> fromResultSet(value.getGuildId(), set).equals(Optional.of(value)),
                (set, it) -> Log.wrapException("Saving data on SQL", () -> writeGuildData(it, value)));
        }

        private Optional<GuildProfile> fromResultSet(String from, ResultSet select) {
            try {
                return Optional.of(new GuildProfile(from,
                        select.getString("prefix"), select.getString("permStartgame"),
                        select.getString("permstopgame"), select.getString("language"),
                        select.getInt("shardid")));
            } catch (Exception e) {
                Log.exception("Saving GuildProfile in SQL", e);
                return Optional.empty();
            }
        }

        private void writeGuildData(PreparedStatement statement, GuildProfile profile) throws Exception {
            statement.setString(1, profile.getGuildPrefix());
            statement.setString(2, profile.getPermStartGame());
            statement.setString(3, profile.getPermStopGame());
            statement.setInt(4, profile.getShardId());
            statement.setString(5, profile.getLanguage());
            statement.setString(6, profile.getGuildId());
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
            return new GuildProfile(from, "g*", null, null, Constants.DEFAULT_LANGUAGE,
                    GamesROB.getGuildById(from).map(it -> it.getJDA().getShardInfo().getShardId()).orElse(0));
        }

        @Override
        public File getDataFile(String from) {
            File guildFolder = new File(Constants.GUILDS_FOLDER, from);
            return new File(guildFolder, "leaderboard.json");
        }
    }
}
