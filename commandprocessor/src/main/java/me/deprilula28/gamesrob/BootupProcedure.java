package me.deprilula28.gamesrob;

import com.github.kevinsawicki.http.HttpRequest;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import me.deprilula28.gamesrob.baseFramework.Match;
import me.deprilula28.gamesrob.data.DataManager;
import me.deprilula28.gamesrob.data.RPCManager;
import me.deprilula28.gamesrob.data.SQLDatabaseManager;
import me.deprilula28.gamesrob.data.Statistics;
import me.deprilula28.gamesrob.utility.*;
import lombok.Getter;
import me.deprilula28.gamesrobshardcluster.*;
import me.deprilula28.gamesrobshardcluster.utilities.Constants;
import me.deprilula28.gamesrobshardcluster.utilities.Log;
import me.deprilula28.gamesrobshardcluster.utilities.ShardClusterUtilities;
import me.deprilula28.gamesrobshardcluster.utilities.Trello;
import net.dv8tion.jda.core.*;
import net.dv8tion.jda.core.events.guild.GuildJoinEvent;
import net.dv8tion.jda.core.events.guild.GuildLeaveEvent;
import org.trello4j.TrelloImpl;
import redis.clients.jedis.Jedis;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class BootupProcedure {
    public static void bootup(String[] args) {
        long begin = System.currentTimeMillis();
        List<String> argList = Arrays.asList(args);
        task(argList, "Loading arguments", loadArguments);
        task(argList, "Loading languages", n -> Language.loadLanguages());
        task(argList, "Loading data", loadData);
        task(argList, "Loading DiscordBotsOrg", dblLoad);
        task(argList, "Loading presence task", presenceTask);
        task(argList, "Sending changelog message", sendChangelog);
        Log.info("Bot fully loaded in " + ShardClusterUtilities.formatPeriod(System.currentTimeMillis() - begin) + "!");
    }

    @FunctionalInterface
    private static interface BootupTask {
        void execute(List<String> args) throws Exception;
    }

    public static Optional<String> optDblToken;
    public static Optional<String> optDBotsToken;
    public static Optional<String> optBfdToken;
    private static int shardTo;
    public static int shardFrom;
    private static int totalShards;
    public static String secret;
    public static String changelog;
    public static boolean useRedis;
    @Getter private static List<Long> owners;

    private static final BootupTask loadArguments = args -> {
        List<Optional<String>> pargs = ShardClusterUtilities.matchValues(args, "token", "dblToken", "shards", "ownerID",
                "sqlDatabase", "debug", "twitchUserID", "clientSecret", "twitchClientID", "rpcServerIP",
                "shardId", "totalShards", "useRedis", "trelloToken", "dbotsToken", "botsfordiscordToken");
        optDblToken = pargs.get(1);
        shardTo = pargs.get(2).map(Integer::parseInt).orElse(1);
        GamesROB.owners = Collections.unmodifiableList(pargs.get(3).map(it -> Arrays.stream(it.split(",")).map(Long::parseLong).collect(Collectors.toList()))
                .orElse(Arrays.asList(197448151064379393L, 386945522608373785L)));
        owners = GamesROB.owners;
        GamesROB.database = pargs.get(4).map(SQLDatabaseManager::new);
        GamesROB.twitchUserIDListen = pargs.get(6).map(Long::parseLong).orElse(-1L);
        secret = pargs.get(7).orElse("");
        GamesROB.twitchClientID = pargs.get(8);

        GamesROB.rpc = pargs.get(9).map(it -> {
            try {
                return new RPCManager(it, shardFrom, shardTo, totalShards);
            } catch (Exception e) {
                Log.exception("Connecting to RPC/JSON", e);
                return null;
            }
        });

        shardFrom = pargs.get(10).map(Integer::parseInt).orElse(0);
        totalShards = pargs.get(11).map(Integer::parseInt).orElse(shardTo);

        DataManager.jedisOpt = pargs.get(12).flatMap(it -> (Boolean.valueOf(it) ? Optional.of(new Jedis("localhost")) : Optional.empty()));
        Trello.optTrello = pargs.get(13).map(it -> new TrelloImpl(Constants.TRELLO_API_KEY, it));
        optDBotsToken = pargs.get(14);
        optBfdToken = pargs.get(15);
    };

    private static final BootupTask loadData = args -> {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Log.info("Shutting down...");
            Cache.onClose();
            GamesROB.plots.interrupt();
        }));
        GamesROBShardCluster.shards.forEach(jda -> Match.ACTIVE_GAMES.put(jda, new ArrayList<>()));

        Utility.setStarlightFont(Font.createFont(Font.TRUETYPE_FONT, Utility.class
                .getResourceAsStream("/imggen/starlightfont.ttf")));
    };

    private static final BootupTask dblLoad = args ->
        optDblToken.ifPresent(dblToken -> {
            if (shardFrom <= 0) GamesROB.getAllShards().then(BootupProcedure::postAllShards);

            GamesROBShardCluster.framework.handleEvent(GuildJoinEvent.class, event -> postUpdatedShard(event.getJDA()));
            GamesROBShardCluster.framework.handleEvent(GuildLeaveEvent.class, event -> postUpdatedShard(event.getJDA()));

            owners = GamesROB.owners;
        });

    private static final String DBL_URL_ROOT = "https://discordbots.org/api/";
    private static final String DBOTS_URL_ROOT = "https://bots.discord.pw/api/";
    private static final String BFD_URL_ROOT = "https://botsfordiscord.com/api/v1/";

    @Getter private static String lastDblRequest;
    @Getter private static String lastDbotsRequest;
    @Getter private static String lastBfdRequest;

    private static void postAllShards(List<GamesROB.ShardStatus> shards) {
        String id = GamesROBShardCluster.shards.get(0).getSelfUser().getId();

        JsonObject dblJson = new JsonObject();
        JsonArray dblShardsArray = new JsonArray();
        shards.forEach(it -> {
            dblShardsArray.add(new JsonPrimitive(it.getGuilds()));

            JsonObject dbotsJson = new JsonObject();
            dbotsJson.add("shard_id", new JsonPrimitive(Integer.parseInt(it.getId())));
            dbotsJson.add("shard_count", new JsonPrimitive(shards.size()));
            dbotsJson.add("server_count", new JsonPrimitive(it.getGuilds()));

            optDBotsToken.ifPresent(dbotsToken -> {
                lastDbotsRequest = HttpRequest.post(DBOTS_URL_ROOT + "bots/" + id + "/stats")
                        .userAgent(Constants.USER_AGENT).authorization(dbotsToken).acceptJson().contentType("application/json")
                        .send(Constants.GSON.toJson(dbotsJson)).body();
            });
        });

        dblJson.add("shards", dblShardsArray);
        optDblToken.ifPresent(dblToken -> {
            lastDblRequest = HttpRequest.post(DBL_URL_ROOT + "bots/" + id + "/stats")
                    .userAgent(Constants.USER_AGENT).authorization(dblToken).acceptJson().contentType("application/json")
                    .send(Constants.GSON.toJson(dblJson)).body();
        });

        JsonObject bfdJson = new JsonObject();
        bfdJson.add("count", new JsonPrimitive(shards.stream().mapToInt(GamesROB.ShardStatus::getGuilds).sum()));
        optBfdToken.ifPresent(bfdToken -> {
            lastBfdRequest = HttpRequest.post(BFD_URL_ROOT + "bots/" + id)
                    .userAgent(Constants.USER_AGENT).authorization(bfdToken).acceptJson().contentType("application/json")
                    .send(Constants.GSON.toJson(bfdJson)).body();
        });
    }

    private static void postUpdatedShard(JDA jda) {
        String id = jda.getSelfUser().getId();

        JsonObject json = new JsonObject();
        json.add("shard_id", new JsonPrimitive(jda.getShardInfo().getShardId()));
        json.add("shard_count", new JsonPrimitive(jda.getShardInfo().getShardTotal()));
        json.add("server_count", new JsonPrimitive(jda.getGuilds().size()));

        optDBotsToken.ifPresent(dbotsToken -> {
            lastDbotsRequest = HttpRequest.post(DBOTS_URL_ROOT + "bots/" + id + "/stats")
                    .userAgent(Constants.USER_AGENT).authorization(dbotsToken).acceptJson().contentType("application/json")
                    .send(Constants.GSON.toJson(json)).body();
        });
        optDblToken.ifPresent(dblToken -> {
            lastDblRequest = HttpRequest.post(DBL_URL_ROOT + "bots/" + id + "/stats")
                    .userAgent(Constants.USER_AGENT).authorization(dblToken).acceptJson().contentType("application/json")
                    .send(Constants.GSON.toJson(json)).body();
        });

        GamesROB.getAllShards().then(shards -> {
            JsonObject bfdJson = new JsonObject();
            bfdJson.add("count", new JsonPrimitive(shards.stream().mapToInt(GamesROB.ShardStatus::getGuilds).sum()));
            optBfdToken.ifPresent(bfdToken -> {
                lastBfdRequest = HttpRequest.post(BFD_URL_ROOT + "bots/" + id)
                        .userAgent(Constants.USER_AGENT).authorization(bfdToken).acceptJson().contentType("application/json")
                        .send(Constants.GSON.toJson(bfdJson)).body();
            });
        });
    }

    private static final BootupTask presenceTask = args -> {
        Thread presenceThread = new Thread(() -> {
            while (true) {
                Log.wrapException("Updating presence", () -> {
                    Thread.sleep(Constants.PRESENCE_UPDATE_PERIOD);
                    GamesROB.setPresence();
                });
            }
        });
        presenceThread.setDaemon(true);
        presenceThread.setName("Presence updater thread");
        presenceThread.start();
        GamesROB.defaultPresence();

        // Twitch integration
        /*
        if (GamesROB.twitchUserIDListen != -1 && GamesROB.twitchClientID.isPresent()) {
            HttpRequest request = HttpRequest.post(String.format("https://api.twitch.tv/helix/webhooks/hub" +
                "?hub.callback=%s&hub.mode=%s&hub.topic=%s&hub.lease_seconds=%s",
                    "http%3A%2F%2F%2Ftwitchwebhook", "subscribe",
                    "https%3A%2F%2Fapi.twitch.tv%2Fhelix%2Fstreams%3Fuser_id%3D" + GamesROB.twitchUserIDListen,
                    864000))
                    .header("Accept", "application/vnd.twitchtv.v5+json")
                    .header("Client-ID", GamesROB.twitchClientID.get());
            Log.trace("Subscription to twitch response: " + request.body() + " (" + request.code() + ")");
        }
        */
    };

    private static final String sendChangelogFormat = "<@&%s>\n%s **GamesROB v%s**\nHere's what's new:\n```diff\n%s\n```\n" +
            "*We schedule updates to be every friday. This means the next update should be %s.\n" +
            "If you don't want to be notified whenever we update, you can remove the reaction on <#%s>" +
            " (click it twice if you havent reacted to it).*";

    /*"<@&484522326906503172>\n<:update:264184209617321984> **GamesROB v" + GamesROB.VERSION + " is available!**" +
                        "\n\nChangelog:\n" + changelog + "\n\n*Updates are usually scheduled for every friday, " +
                        "making the next update " + Utility.formatTime(Utility.predictNextUpdate()) + ".*\n" +
                            "If you don't want to get pinged in these messages, you can remove your reaction on <#358451223612882944>."
                            */

    private static final BootupTask sendChangelog = args -> {
        changelog = Utility.readResource("/changelog.txt");
        Statistics statistics = Statistics.get();
        if (!GamesROB.VERSION.equals(statistics.getLastUpdateLogSent()) && Constants.changelogChannel.isPresent()) {
            statistics.setLastUpdateLogSent(GamesROB.VERSION);
            statistics.setLastUpdateLogSentTime(System.currentTimeMillis());
            GamesROB.getTextChannelById(Constants.changelogChannel.get()).ifPresent(channel -> channel.sendMessage(String.format(sendChangelogFormat,
                "484522326906503172", "<:update:264184209617321984>", GamesROB.VERSION, changelog,
                ShardClusterUtilities.formatTimeRegularFormat(Utility.predictNextUpdate()), "358451223612882944"
            )).queue());
        }
    };

    private static void task(List<String> args, String name, BootupTask task) {
        Log.info(name + "...");
        long begin = System.currentTimeMillis();
        Log.wrapException("Failed to bootup on task: " + name, () -> task.execute(args));
        long time = System.currentTimeMillis() - begin;
        Log.info("Finished in " + ShardClusterUtilities.formatPeriod(time));
    }
}
