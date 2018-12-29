package me.deprilula28.gamesrob;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Data;
import me.deprilula28.gamesrob.baseFramework.GamesInstance;
import me.deprilula28.gamesrob.baseFramework.Match;
import me.deprilula28.gamesrob.commands.CommandsManager;
import me.deprilula28.gamesrob.data.PlottingStatistics;
import me.deprilula28.gamesrob.data.RPCManager;
import me.deprilula28.gamesrob.data.SQLDatabaseManager;
import me.deprilula28.gamesrob.data.Statistics;
import me.deprilula28.gamesrob.games.*;
import me.deprilula28.gamesrob.utility.Cache;
import me.deprilula28.gamesrob.utility.Utility;
import me.deprilula28.gamesrobshardcluster.CommandProcessor;
import me.deprilula28.gamesrobshardcluster.GamesROBShardCluster;
import me.deprilula28.gamesrobshardcluster.utilities.Constants;
import me.deprilula28.gamesrobshardcluster.utilities.Log;
import me.deprilula28.gamesrobshardcluster.utilities.ShardClusterUtilities;
import me.deprilula28.jdacmdframework.CommandFramework;
import net.dv8tion.jda.core.OnlineStatus;
import net.dv8tion.jda.core.entities.*;
import org.java_websocket.client.WebSocketClient;

import javax.imageio.ImageIO;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class GamesROB extends CommandProcessor {
    public static final GamesInstance[] ALL_GAMES = {
            Connect4.GAME, TicTacToe.GAME, Minesweeper.GAME, Hangman.GAME, Detective.GAME, Roulette.GAME, Quiz.GAME, Uno.GAME,
            TownCountryRiver.GAME
    };
    public static final String VERSION = "1.8.4";

    public static final long UP_SINCE = System.currentTimeMillis();

    public static List<Long> owners;
    public static Optional<SQLDatabaseManager> database = Optional.empty();
    public static Optional<RPCManager> rpc = Optional.empty();
    public static PlottingStatistics plots = new PlottingStatistics();
    private static Optional<Game> twitchPresence = Optional.empty();

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
    }

    public static void defaultPresence() {
        User selfUser = GamesROBShardCluster.shards.get(0).getSelfUser();
        if (selfUser != null) {
            final String message = "@" + selfUser.getName() + "#" + selfUser.getDiscriminator() + " | gamesrob.com";
            GamesROBShardCluster.shards.forEach(cur -> {
                cur.getPresence().setStatus(OnlineStatus.ONLINE);
                cur.getPresence().setGame(Game.playing(message));
            });
        }
    }

    public static void updateBotStatusMessage() {
        GamesROB.getTextChannelById(Constants.BOT_STATUS_CHANNEL_ID).ifPresent(channel ->
                channel.getMessageById(Statistics.get().getBotStatusMessage()).queue(msg -> {
            if (msg != null) getAllShards().then(shards -> msg.editMessage(getBotStatusMessage(shards)).queue());
        }));
    }

    public static String getBotStatusMessage(List<ShardStatus> shards) {
        return String.format(
            "**Realtime Bot Status Message**\n" +
            "RPC Connection: %s\n\nShards:\n%s",
            GamesROB.rpc.map(it -> it.isOpen() ? "<:online:313956277808005120> Open" :
                    it.isConnecting() ? "<:invisible:313956277107556352> Connecting" :
                            it.isClosing() ? "<:dnd:313956276893646850> Closing" : "<:offline:313956277237710868> Disconnected")
                    .orElse("<:offline:313956277237710868> Disabled"),
            shards.stream().map(it -> String.format(
                    "%s Shard %s (%s)",
                    Utility.getEmoteForStatus(it.getStatus()), it.getId(), ShardClusterUtilities.formatPeriod(it.getPing())
            )).collect(Collectors.joining("\n"))
        );
    }

    @Override
    public void registerCommands(String[] args, CommandFramework f) {
        Log.info("-= Starting Command Processor " + VERSION + " =-");
        BootupProcedure.bootup(args);
        CommandsManager.registerCommands(f);
        f.getSettings().setPrefixGetter(Utility::getPrefix);
    }

    @Override
    public void close() {
        Log.info("-= Closing Command Processor " + VERSION + " =-");
        GamesROBShardCluster.framework.clear();
        rpc.ifPresent(it -> it.setShouldAllowClose(true));
        rpc.ifPresent(WebSocketClient::close);
        Cache.clearAll();
        GamesROB.plots.interrupt();
        BootupProcedure.presenceThread.interrupt();
        database.ifPresent(SQLDatabaseManager::close);
    }

    public static Object twitchStreamUpdate(JsonElement element) {
        if (element.isJsonNull()) {
            twitchPresence = Optional.empty();
            twitchPresence.ifPresent(n -> {
                if (GamesROBShardCluster.premiumBot) setAvatar("premium.png");
                else if (GamesROBShardCluster.debug) setAvatar("dev.png");
                else setAvatar("default.png");
            });
        } else {
            if (!twitchPresence.isPresent()) setAvatar("stream.png");

            JsonObject object = element.getAsJsonObject();
            twitchPresence = Optional.of(Game.streaming(object.get("title").getAsString() + " to " +
                    object.get("viewer_count").getAsInt() + " viewers",
                    "https://twitch.tv/" + object.get("user_name").getAsString()));
        }

        return null;
    }

    private static void setAvatar(String path) {
        Log.wrapException("Setting avatar", () -> GamesROBShardCluster.shards.get(0).getSelfUser().getManager()
                .setAvatar(Icon.from(GamesROB.class.getResourceAsStream("/avatar/" + path))).queue());
    }
}
