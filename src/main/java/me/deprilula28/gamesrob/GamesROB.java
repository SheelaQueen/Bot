package me.deprilula28.gamesrob;

import com.github.kevinsawicki.http.HttpRequest;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Data;
import me.deprilula28.gamesrob.baseFramework.GamesInstance;
import me.deprilula28.gamesrob.baseFramework.Match;
import me.deprilula28.gamesrob.data.SQLDatabaseManager;
import me.deprilula28.gamesrob.games.Connect4;
import me.deprilula28.gamesrob.games.Hangman;
import me.deprilula28.gamesrob.games.Minesweeper;
import me.deprilula28.gamesrob.games.TicTacToe;
import me.deprilula28.gamesrob.utility.Constants;
import me.deprilula28.gamesrob.utility.Log;
import me.deprilula28.gamesrob.utility.Utility;
import me.deprilula28.jdacmdframework.CommandFramework;
import me.deprilula28.jdacmdframework.discordbotsorgapi.DiscordBotsOrg;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.OnlineStatus;
import net.dv8tion.jda.core.entities.*;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class GamesROB {
    public static final GamesInstance[] ALL_GAMES = {
            Connect4.GAME, TicTacToe.GAME, Minesweeper.GAME, Hangman.GAME
    };

    public static final long UP_SINCE = System.currentTimeMillis();
    private static final int MAJOR = 1;
    private static final int MINOR = 6;
    private static final int PATCH = 0;
    public static final String VERSION = String.format("%s.%s.%s", MAJOR, MINOR, PATCH);

    public static Optional<DiscordBotsOrg> dboAPI = Optional.empty();
    public static List<JDA> shards = new ArrayList<>();
    public static CommandFramework commandFramework;
    public static List<Long> owners;
    public static Optional<SQLDatabaseManager> database = Optional.empty();
    public static boolean debug = false;
    static Optional<String> twitchClientID = Optional.empty();
    static long twitchUserIDListen = -1L;
    private static boolean twitchPresence = false;

    @Data
    @AllArgsConstructor
    public static class ShardStatus {
        private String id;
        private int guilds;
        private int users;
        private int textChannels;
        private String status;
        private long ping;
        private int activeGames;
    }

    public static List<ShardStatus> getAllShards() {
        return shards.stream().map(it ->
                new ShardStatus(String.valueOf(shards.indexOf(it)),
                        it.getGuilds().size(), it.getUsers().size(), it.getTextChannels().size(), it.getStatus().toString(),
                        it.getPing(), Match.ACTIVE_GAMES.get(it).size())).collect(Collectors.toList());
    }

    private static final List<Supplier<String>> STATISTICS_SUPPLIER = Arrays.asList(
            () -> Utility.addNumberDelimitors(getAllShards().stream().mapToInt(GamesROB.ShardStatus::getGuilds).sum()) + " servers",
            () -> Utility.addNumberDelimitors(getAllShards().stream().mapToInt(GamesROB.ShardStatus::getUsers).sum()) + " users",
            () -> Utility.addNumberDelimitors(getAllShards().stream().mapToInt(GamesROB.ShardStatus::getTextChannels).sum()) + " channels"
    );
    private static final Supplier<String> MENTION_BOT_SUPPLIER = () -> {
        SelfUser self = shards.get(0).getSelfUser();
        return "@" + self.getName() + "#" + self.getDiscriminator();
    };

    public static Optional<TextChannel> getTextChannelById(long id) {
        return shards.stream().map(it -> it.getTextChannelById(id)).filter(Objects::nonNull).findFirst();
}

    public static Optional<Guild> getGuildById(long id) {
        return shards.stream().map(it -> it.getGuildById(id)).filter(Objects::nonNull).findFirst();
    }

    public static Optional<User> getUserById(long id) {
        return shards.stream().map(it -> it.getUserById(id)).filter(Objects::nonNull).findFirst();
    }

    public static Optional<Guild> getGuildById(String id) {
        return shards.stream().map(it -> it.getGuildById(id)).filter(Objects::nonNull).findFirst();
    }

    public static Optional<User> getUserById(String id) {
        return shards.stream().map(it -> it.getUserById(id)).filter(Objects::nonNull).findFirst();
    }

    public static void main(String[] args) {
        Log.wrapException("Loading the bot.", () -> {
            Log.info("-= GamesRob Initializing! =-");
            Log.info(VERSION);

            BootupProcedure.bootup(args);
            GamesROB.commandFramework.listenEvents();
        });
    }

    @Data
    private static class StreamData {
        private JsonElement stream;
    }

    public static void setPresence() {
        if (twitchClientID.isPresent() && twitchUserIDListen != -1) {
            StreamData data = Constants.GSON.fromJson(HttpRequest
                .get("https://api.twitch.tv/kraken/streams/" + GamesROB.twitchUserIDListen)
                    .header("Accept", "application/vnd.twitchtv.v5+json")
                    .header("Client-ID",  GamesROB.twitchClientID.get())
                    .body(), StreamData.class);
            if (data.getStream().isJsonNull() && twitchPresence) defaultPresence();
            else if (!data.getStream().isJsonNull()) {
                Log.trace("Updated streaming status");
                JsonObject obj = data.getStream().getAsJsonObject();
                String title = obj.get("title").getAsString() + " to " + obj.get("viewers").getAsInt() + " viewers";
                String url = obj.get("channel").getAsJsonObject().get("url").getAsString();

                shards.forEach(cur -> {
                    cur.getPresence().setStatus(OnlineStatus.ONLINE);
                    cur.getPresence().setGame(Game.streaming(title, url));
                });
                twitchPresence = true;
            }
        }
    }

    public static void defaultPresence() {
        shards.forEach(cur -> {
            cur.getPresence().setStatus(OnlineStatus.ONLINE);
            cur.getPresence().setGame(Game.watching("gamesrob.com - @" + cur.getSelfUser().getName() + "#" +
                cur.getSelfUser().getDiscriminator()));
        });
    }

    public static boolean hasUpvoted(String id) {
        return !dboAPI.isPresent() || dboAPI.get().getVoters().stream().filter(it -> it.getId().equals(id)).count() > 0;
    }
}
