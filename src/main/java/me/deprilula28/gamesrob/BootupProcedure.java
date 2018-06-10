package me.deprilula28.gamesrob;

import me.deprilula28.gamesrob.baseFramework.GameState;
import me.deprilula28.gamesrob.baseFramework.Match;
import me.deprilula28.gamesrob.commands.CommandManager;
import me.deprilula28.gamesrob.data.*;
import me.deprilula28.gamesrob.utility.*;
import me.deprilula28.jdacmdframework.CommandFramework;
import me.deprilula28.jdacmdframework.Settings;
import me.deprilula28.jdacmdframework.discordbotsorgapi.DiscordBotsOrg;
import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.OnlineStatus;
import net.dv8tion.jda.core.entities.Game;
import net.dv8tion.jda.core.events.guild.GuildJoinEvent;
import net.dv8tion.jda.core.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.events.message.guild.react.GuildMessageReactionAddEvent;
import org.trello4j.TrelloImpl;
import redis.clients.jedis.Jedis;

import java.io.File;
import java.io.FileReader;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class BootupProcedure {
    public static void bootup(String[] args) {
        long begin = System.currentTimeMillis();
        List<String> argList = Arrays.asList(args);
        task(argList, "Loading arguments", loadArguments);
        task(argList, "Loading languages", n -> Language.loadLanguages());
        task(argList, "Connecting to Discord", connectDiscord);
        task(argList, "Loading framework", frameworkLoad);
        task(argList, "Loading data", loadData);
        task(argList, "Loading DiscordBotsOrg", dblLoad);
        task(argList, "Loading website", loadWebsite);
        task(argList, "Loading presence task", presenceTask);
        task(argList, "Transferring data to DB", transferToDb);
        task(argList, "Sending changelog message", sendChangelog);
        Log.info("Bot fully loaded in " + Utility.formatPeriod(System.currentTimeMillis() - begin) + "!");
    }

    @FunctionalInterface
    private static interface BootupTask {
        void execute(List<String> args) throws Exception;
    }

    private static String token;
    public static Optional<String> optDblToken;
    private static int shardTo;
    public static int shardFrom;
    private static int totalShards;
    public static String secret;
    public static String changelog;
    public static boolean useRedis;

    private static final BootupTask loadArguments = args -> {
        List<Optional<String>> pargs = Utility.matchValues(args, "token", "dblToken", "shards", "ownerID",
                "sqlDatabase", "debug", "twitchUserID", "clientSecret", "twitchClientID", "rpcServerIP",
                "shardId", "totalShards", "useRedis", "trelloToken");
        token = pargs.get(0).orElseThrow(() -> new RuntimeException("You need to provide a token!"));
        optDblToken = pargs.get(1);
        shardTo = pargs.get(2).map(Integer::parseInt).orElse(1);
        GamesROB.owners = pargs.get(3).map(it -> Arrays.stream(it.split(",")).map(Long::parseLong).collect(Collectors.toList()))
                .orElse(Collections.singletonList(197448151064379393L));
        GamesROB.database = pargs.get(4).map(SQLDatabaseManager::new);
        GamesROB.debug = pargs.get(5).map(Boolean::parseBoolean).orElse(false);
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
        Log.info(pargs);
    };

    private static final BootupTask transferToDb = args -> {
        GamesROB.database.ifPresent(db -> {
            int transfered = 0;
            File[] GUILD_FILES = Constants.GUILDS_FOLDER.listFiles();
            if (GUILD_FILES != null) for (File file : GUILD_FILES) {
                    FileReader reader = null;
                    try {
                        reader = new FileReader(new File(file, "leaderboard.json"));
                        GuildProfile guildProfile = Constants.GSON.fromJson(reader, GuildProfile.class);

                        Log.info("Guild:", guildProfile, "Saving to SQL...");
                        GuildProfile.manager.saveToSQL(db, guildProfile).then(n -> {
                            Log.wrapException("Getting saved guild",  () -> {
                                Log.trace("Finished saving guild:", guildProfile.getGuildId() + "; Data read: ",
                                        GuildProfile.manager.getFromSQL(db, guildProfile.getGuildId()));
                            });
                        });

                        transfered ++;
                        Log.info("Transferred " + file.getName() + ". (" + transfered + ")");
                        reader.close();
                    } catch (Exception e) {
                        if (reader != null) Utility.quietlyClose(reader);
                        Log.info("Failed to save " + file.getAbsolutePath(), e);
                    }
                }
            Log.info(transfered + " guilds transferred");
            transfered = 0;

            File[] USER_PROFILES = Constants.USER_PROFILES_FOLDER.listFiles();
            if (USER_PROFILES != null) for (File file : USER_PROFILES) {
                    FileReader reader = null;
                    try {
                        reader = new FileReader(file);
                        UserProfile userProfile = Constants.GSON.fromJson(reader, UserProfile.class);

                        Log.info("User:", userProfile, "Saving to SQL...");
                        UserProfile.manager.saveToSQL(db, userProfile).then(n -> {
                            Log.wrapException("Getting saved user",  () -> {
                                Log.trace("Finished saving user:", userProfile.getUserID() + "; Data read: ",
                                        UserProfile.manager.getFromSQL(db, userProfile.getUserID()));
                            });
                        });

                        transfered ++;
                        Log.info("Transferred " + file.getName() + ". (" + transfered + ")");
                        reader.close();
                    } catch (Exception e) {
                        if (reader != null) Utility.quietlyClose(reader);
                        Log.info("Failed to save " + file.getAbsolutePath(), e);
                    }
                }
            Log.info(transfered + " users transferred");
        });
    };

    private static final BootupTask connectDiscord = args -> {
        int curShard = shardFrom;
        while (curShard < shardTo) {
            String shard = curShard + "/" + (shardTo - 1);

            JDA jda = new JDABuilder(AccountType.BOT).setToken(token)
                    .useSharding(curShard, totalShards).setStatus(OnlineStatus.DO_NOT_DISTURB)
                    .setGame(Game.watching("it all load...")).buildBlocking();
            GamesROB.shards.add(jda);
            Match.ACTIVE_GAMES.put(jda, new ArrayList<>());

            Log.info("Shard loaded: " + shard);
            curShard ++;
            if (curShard < shardTo) Thread.sleep(5000L);
        }
    };

    private static final BootupTask frameworkLoad = args -> {
        CommandFramework f = new CommandFramework(GamesROB.shards, Settings.builder()
                .loggerFunction(Log::info).removeCommandMessages(false).protectMentionEveryone(true)
                .async(true).threadPool(new ThreadPoolExecutor(10, 100, 5, TimeUnit.MINUTES,
                        new LinkedBlockingQueue<>())).prefixGetter(Constants::getPrefix).joinQuotedArgs(true)
                .commandExceptionFunction((context, exception) -> {
                    context.send("⛔ An error has occured! It has been reported to devs. My bad...");
                    Log.exception("Command: " + context.getMessage().getRawContent(), exception, context);
                }).genericExceptionFunction((message, exception) -> Log.exception(message, exception))
                .caseIndependent(true)
                .build());
        GamesROB.commandFramework = f;

        // Commands
        CommandManager.registerCommands(f);

        f.handleEvent(GuildMessageReactionAddEvent.class, event -> {
            try {
                if (Match.GAMES.containsKey(event.getChannel())) Match.GAMES.get(event.getChannel()).reaction(event);
                else if (Match.REMATCH_GAMES.containsKey(event.getChannel()))
                    Match.REMATCH_GAMES.get(event.getChannel())
                            .reaction(event);
            } catch (Exception e) {
                Log.exception("Guild message reaction add had an error", e);
            }
        });

        f.handleEvent(MessageReceivedEvent.class, event -> {
            if (Match.PLAYING.containsKey(event.getAuthor())) {
                Match game = Match.PLAYING.get(event.getAuthor());

                if (game.getGameState() == GameState.MATCH)
                    try {
                        if (event.getGuild() == null) game.getMatchHandler().receivedDM(event.getMessage().getRawContent(),
                                event.getAuthor(), event.getMessage());
                        else game.getMatchHandler().receivedMessage(event.getMessage().getRawContent(),
                                    event.getAuthor(), event.getMessage());
                    } catch (Exception e) {
                        Log.exception("Game of " + game.getGame().getName(Constants.DEFAULT_LANGUAGE) + " had an error", e);
                        game.onEnd("⛔ An error occurred causing the game to end.\nMy bad :c", false);
                    }
            }
        });
    };

    private static final BootupTask loadData = args -> {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Log.info("Shutting down...");
            Cache.onClose();
            Log.closeStream();

            /*
            if (GamesROB.twitchUserIDListen != -1 && WebhookHandlers.hasConfirmed && GamesROB.twitchClientID.isPresent())
                Log.trace("Response to twitch de-subscription: " +
                    HttpRequest.post(String.format("https://api.twitch.tv/helix/webhooks/hub?hub.callback=%s&hub.mode=%s&hub.topic=%s",
                            "http%3A%2F%2F1%2Ftwitchwebhook", "unsubscribe",
                            "https%3A%2F%2Fapi.twitch.tv%2Fhelix%2Fstreams%3Fuser_id%3D" + GamesROB.twitchUserIDListen))
                    .header("Accept", "application/vnd.twitchtv.v5+json")
                    .header("Client-ID",  GamesROB.twitchClientID.get())
                    .body());

            Cache.onClose();
                    */
        }));
    };

    private static final BootupTask dblLoad = args ->
        optDblToken.ifPresent(dblToken -> {
            DiscordBotsOrg dbo = DiscordBotsOrg.builder()
                    .botID(GamesROB.shards.get(0).getSelfUser().getId()).shardCount(totalShards).token(dblToken)
                    .build();

            GamesROB.getAllShards().then(shards -> dbo.setStats(shards.stream().map(GamesROB.ShardStatus::getGuilds)
                    .collect(Collectors.toList())));

            GamesROB.commandFramework.handleEvent(GuildJoinEvent.class, event -> dbo.setStats(event.getJDA().getShardInfo().getShardId(),
                    event.getJDA().getGuilds().size()));
            GamesROB.commandFramework.handleEvent(GuildLeaveEvent.class, event -> dbo.setStats(event.getJDA().getShardInfo().getShardId(),
                    event.getJDA().getGuilds().size()));

            GamesROB.dboAPI = Optional.of(dbo);
            GamesROB.owners = GamesROB.dboAPI.get().getBot().getOwners().stream().map(Long::parseLong).collect(Collectors.toList());
    });

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

    private static final BootupTask loadWebsite = args -> {
        //Website.start(port);
    };

    private static final BootupTask sendChangelog = args -> {
        changelog = Utility.readResource("/changelog.txt");
        Statistics statistics = Statistics.get();
        if (!GamesROB.VERSION.equals(statistics.getLastUpdateLogSent()) && Constants.changelogChannel.isPresent()) {
            statistics.setLastUpdateLogSent(GamesROB.VERSION);
            statistics.setLastUpdateLogSentTime(System.currentTimeMillis());
            GamesROB.getTextChannelById(Constants.changelogChannel.get()).ifPresent(channel ->
                    channel.sendMessage("<@&389918430733664256>\n**GamesROB v" + GamesROB.VERSION + " is out!**" +
                        "\n\nChangelog:\n" + changelog + "\n\n*Updates are usually scheduled for every friday, " +
                        "making the next update " + Utility.formatTime(Utility.predictNextUpdate()) + ".*")
                        .queue());
        }
    };

    private static void task(List<String> args, String name, BootupTask task) {
        Log.info(name + "...");
        long begin = System.currentTimeMillis();
        Log.wrapException("Failed to bootup on task: " + name, () -> task.execute(args));
        long time = System.currentTimeMillis() - begin;
        Log.info("Finished in " + Utility.formatPeriod(time));
    }
}
