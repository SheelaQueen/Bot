package me.deprilula28.gamesrob;

import lombok.AllArgsConstructor;
import lombok.Data;
import me.deprilula28.gamesrob.baseFramework.GamesInstance;
import me.deprilula28.gamesrob.baseFramework.Match;
import me.deprilula28.gamesrob.commands.CommandManager;
import me.deprilula28.gamesrob.data.PlottingStatistics;
import me.deprilula28.gamesrob.data.RPCManager;
import me.deprilula28.gamesrob.data.SQLDatabaseManager;
import me.deprilula28.gamesrob.games.*;
import me.deprilula28.gamesrob.utility.Cache;
import me.deprilula28.gamesrob.utility.Language;
import me.deprilula28.gamesrob.utility.Utility;
import me.deprilula28.gamesrobshardcluster.CommandProcessor;
import me.deprilula28.gamesrobshardcluster.GamesROBShardCluster;
import me.deprilula28.gamesrobshardcluster.utilities.Constants;
import me.deprilula28.gamesrobshardcluster.utilities.Log;
import me.deprilula28.jdacmdframework.CommandFramework;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.OnlineStatus;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.guild.GuildJoinEvent;
import org.java_websocket.client.WebSocketClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class GamesROB extends CommandProcessor {
    public static final GamesInstance[] ALL_GAMES = {
            Connect4.GAME, TicTacToe.GAME, Minesweeper.GAME, Hangman.GAME, Detective.GAME, Roulette.GAME, Quiz.GAME, Uno.GAME,
            TownCountryRiver.GAME
    };
    public static final String VERSION = "1.8.2";

    public static final long UP_SINCE = System.currentTimeMillis();

    public static List<Long> owners;
    public static Optional<SQLDatabaseManager> database = Optional.empty();
    public static boolean debug = false;
    static Optional<String> twitchClientID = Optional.empty();
    static long twitchUserIDListen = -1L;
    private static boolean twitchPresence = false;
    public static Optional<RPCManager> rpc = Optional.empty();
    public static PlottingStatistics plots = new PlottingStatistics();

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

    public static List<ShardStatus> getShardsInfo() {
        return GamesROBShardCluster.shards.stream().map(it ->
                new ShardStatus(String.valueOf(BootupProcedure.shardFrom + GamesROBShardCluster.shards.indexOf(it)),
                        it.getGuilds().size(), it.getUsers().size(), it.getTextChannels().size(), it.getStatus().toString(),
                        it.getPing(), Match.ACTIVE_GAMES.get(it).size())).collect(Collectors.toList());
    }

    public static Utility.Promise<List<ShardStatus>> getAllShards() {
        return rpc.filter(WebSocketClient::isOpen).map(it -> it.request(RPCManager.RequestType.GET_ALL_SHARDS_INFO, null)
                .map(list -> {
            List<ShardStatus> statuses = new ArrayList<>();
            list.getAsJsonArray().forEach(el -> statuses.add(Constants.GSON.fromJson(el, ShardStatus.class)));

            return statuses;
        })).orElse(Utility.Promise.result(getShardsInfo()));
    }

    public static Optional<Category> getCategoryById(long id) {
        return GamesROBShardCluster.shards.stream().map(it -> it.getCategoryById(id)).filter(Objects::nonNull).findFirst();
    }

    public static Optional<Role> getRoleById(long id) {
        return GamesROBShardCluster.shards.stream().map(it -> it.getRoleById(id)).filter(Objects::nonNull).findFirst();
    }

    public static Optional<TextChannel> getTextChannelById(long id) {
        return GamesROBShardCluster.shards.stream().map(it -> it.getTextChannelById(id)).filter(Objects::nonNull).findFirst();
    }

    public static Optional<Guild> getGuildById(long id) {
        return GamesROBShardCluster.shards.stream().map(it -> it.getGuildById(id)).filter(Objects::nonNull).findFirst();
    }

    public static Optional<User> getUserById(long id) {
        return GamesROBShardCluster.shards.stream().map(it -> it.getUserById(id)).filter(Objects::nonNull).findFirst();
    }

    public static Optional<Guild> getGuildById(String id) {
        return GamesROBShardCluster.shards.stream().map(it -> it.getGuildById(id)).filter(Objects::nonNull).findFirst();
    }

    public static Optional<User> getUserById(String id) {
        return GamesROBShardCluster.shards.stream().map(it -> it.getUserById(id)).filter(Objects::nonNull).findFirst();
    }

    public static void setPresence() {
        defaultPresence();
        /*
        if (twitchClientID.isPresent() && twitchUserIDListen != -1) {
            StreamData data = Constants.GSON.fromJson(HttpRequest
                    .get("https://api.twitch.tv/kraken/streams/" + GamesROB.twitchUserIDListen)
                    .header("Accept", "application/vnd.twitchtv.v5+json")
                    .header("Client-ID", GamesROB.twitchClientID.get())
                    .body(), StreamData.class);
            if (data.getStream().isJsonNull() && twitchPresence) defaultPresence();
            else if (!data.getStream().isJsonNull()) {
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
        */
    }

    private static final String[] HALLOWEEN_EVENT_MESSAGES = {
            "Try trick or treating on Discord!",
            "View your candy with the candy command!"
    };

    public static void defaultPresence() {
        final String message = "halloween event active! " + HALLOWEEN_EVENT_MESSAGES[ThreadLocalRandom.current().nextInt(HALLOWEEN_EVENT_MESSAGES.length)];
        GamesROBShardCluster.shards.forEach(cur -> {
            cur.getPresence().setStatus(OnlineStatus.ONLINE);
            cur.getPresence().setGame(Game.playing(message));
        });
    }

    @Override
    public void registerCommands(String[] args, CommandFramework f) {
        Log.info("-= Starting Command Processor " + VERSION + " =-");

        BootupProcedure.bootup(args);
        CommandManager.registerCommands(f);
        f.getSettings().setPrefixGetter(Utility::getPrefix);
        f.handleEvent(GuildJoinEvent.class, event -> {
            if (event.getGuild().getMembers().size() < 50)
                event.getGuild().getTextChannels().stream().filter(TextChannel::canTalk).findFirst().ifPresent(channel ->
                        channel.sendMessage(new MessageBuilder()
                                .append(":wave: Hey there!\n" +
                                        "I'm **GamesROB**, the <:botTag:230105988211015680> that lets you play chat games right from Discord!\n\n" +
                                        "If you want to set a language for your guild, type `g*glang`!\n" +
                                        "We currently support: " + Language.getLanguageList().stream().map(it ->
                                        Language.transl(it, "languageProperties.languageName")).collect(Collectors.joining(", ")) + "!\n\n" +
                                        "If you want to start playing games, type `g*help` and pick a game!")
                                .setEmbed(new EmbedBuilder().setTitle("If you need help, you can:")
                                        .setDescription("- [View our online command list](" + Constants.GAMESROB_DOMAIN + "/help)\n" +
                                                "- [Join our support server](https://discord.gg/xajeDYR)")
                                        .setColor(Utility.getEmbedColor(event.getGuild())).build())
                                .build()).queue());
        });
    }

    @Override
    public void close() {
        Log.info("-= Closing Command Processor " + VERSION + " =-");
        GamesROBShardCluster.framework.clear();
        rpc.ifPresent(it -> it.setShouldAllowClose(true));
        rpc.ifPresent(WebSocketClient::close);
        Cache.onClose();
        GamesROB.plots.interrupt();
        database.ifPresent(SQLDatabaseManager::close);
    }
}
