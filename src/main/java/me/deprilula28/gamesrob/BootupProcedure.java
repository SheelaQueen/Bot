package me.deprilula28.gamesrob;

import lombok.Getter;
import me.deprilula28.gamesrob.baseFramework.GameState;
import me.deprilula28.gamesrob.baseFramework.GameType;
import me.deprilula28.gamesrob.baseFramework.Match;
import me.deprilula28.gamesrob.commands.CommandManager;
import me.deprilula28.gamesrob.data.*;
import me.deprilula28.gamesrob.utility.*;
import me.deprilula28.jdacmdframework.CommandFramework;
import me.deprilula28.jdacmdframework.Settings;
import me.deprilula28.jdacmdframework.discordbotsorgapi.DiscordBotsOrg;
import net.dv8tion.jda.core.*;
import net.dv8tion.jda.core.entities.Game;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.events.guild.GuildJoinEvent;
import net.dv8tion.jda.core.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.events.message.guild.react.GuildMessageReactionAddEvent;
import org.trello4j.TrelloImpl;
import redis.clients.jedis.Jedis;

import java.awt.*;
import java.io.File;
import java.io.FileReader;
import java.util.*;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
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
        task(argList, "Loading presence task", presenceTask);
        task(argList, "Sending changelog message", sendChangelog);
        Log.info("Bot fully loaded in " + Utility.formatPeriod(System.currentTimeMillis() - begin) + "!");
        GamesROB.plots.start();
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
    @Getter private static List<Long> owners;

    private static final BootupTask loadArguments = args -> {
        List<Optional<String>> pargs = Utility.matchValues(args, "token", "dblToken", "shards", "ownerID",
                "sqlDatabase", "debug", "twitchUserID", "clientSecret", "twitchClientID", "rpcServerIP",
                "shardId", "totalShards", "useRedis", "trelloToken");
        token = pargs.get(0).orElseThrow(() -> new RuntimeException("You need to provide a token!"));
        optDblToken = pargs.get(1);
        shardTo = pargs.get(2).map(Integer::parseInt).orElse(1);
        GamesROB.owners = Collections.unmodifiableList(pargs.get(3).map(it -> Arrays.stream(it.split(",")).map(Long::parseLong).collect(Collectors.toList()))
                .orElse(Arrays.asList(197448151064379393L, 386945522608373785L)));
        owners = GamesROB.owners;
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
    };

    private static final BootupTask connectDiscord = args -> {
        int curShard = shardFrom;
        while (curShard < shardTo) {
            JDA jda = new JDABuilder(AccountType.BOT).setToken(token)
                    .useSharding(curShard, totalShards).setStatus(OnlineStatus.DO_NOT_DISTURB)
                    .setGame(Game.watching("it all load...")).buildBlocking();
            GamesROB.shards.add(jda);
            Match.ACTIVE_GAMES.put(jda, new ArrayList<>());

            Log.info("Shard loaded: " + curShard + "/" + (shardTo - 1));
            curShard ++;
            if (curShard < shardTo) Thread.sleep(5000L);
        }
    };

    private static final String[] ERROR_MESSAGES = {
            "So um... This is awkard...", "Oof...", "Sorry :c", "This ~~happens~~ *doesn't happen* very often you know?",
            "Roses are red,\nViolets are blue.\nDep is bad at coding,\nSo I bring this message for you!"
    };
    private static final String[] GIFS = {
            "https://media2.giphy.com/media/dRgcwKJaGgWgo/giphy.gif",
            "https://media1.giphy.com/media/ucqzuPUJSrTvW/giphy.gif",
            "https://media2.giphy.com/media/3ztfAWk2M2msM/giphy.gif",
            "https://media.giphy.com/media/sXICOpe3B2cc8/giphy.gif",
            "https://media.giphy.com/media/11H1vD2IrZxg9G/giphy.gif",
            "https://this.is-la.me/c82a77.png"
    };

    private static final BootupTask frameworkLoad = args -> {
        CommandFramework f = new CommandFramework(GamesROB.shards, Settings.builder()
                .loggerFunction(Log::info).removeCommandMessages(false).protectMentionEveryone(true)
                .prefix("").async(true).threadPool(new ThreadPoolExecutor(10, 100, 5, TimeUnit.MINUTES,
                        new LinkedBlockingQueue<>())).prefixGetter(Constants::getPrefix).joinQuotedArgs(true)
                .commandExceptionFunction((context, exception) -> {
                    Optional<String> trelloUrl = Log.exception("Command: " + context.getMessage().getContentRaw(), exception, context);
                    context.send(new EmbedBuilder()
                        .setTitle("Failed to run that command!", trelloUrl.orElse("https://discord.gg/gJKQPkN"))
                        .setDescription(ERROR_MESSAGES[ThreadLocalRandom.current().nextInt(ERROR_MESSAGES.length)]
                                + "\n\nFeel free to [click here](https://discord.gg/gJKQPkN) to join our support server and report this:\n"
                                + trelloUrl.orElse("*No trello info*")
                        ).setColor(Color.decode("#D50000"))
                        .setThumbnail(GIFS[ThreadLocalRandom.current().nextInt(GIFS.length)]));
                }).genericExceptionFunction((message, exception) -> Log.exception(message, exception))
                .caseIndependent(true)
                .build());
        GamesROB.commandFramework = f;

        // Commands
        CommandManager.registerCommands(f);

        f.handleEvent(MessageReceivedEvent.class, event -> {
            if (Match.PLAYING.containsKey(event.getAuthor())) {
                Match game = Match.PLAYING.get(event.getAuthor());

                if (game.getGameState() == GameState.MATCH) game.messageEvent(event);
            } else if (!event.getAuthor().isBot() && event.getGuild() != null && Match.GAMES.containsKey(event.getTextChannel())) {
                Match game = Match.GAMES.get(event.getTextChannel());
                if (game.getGame().getGameType() != GameType.COLLECTIVE || !game.playMore) return;
                game.messageEvent(event);
            }
        });

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
                            .setEmbed(new EmbedBuilder().setTitle("If you need help:")
                                    .setDescription("- [View our online command list](" + Constants.GAMESROB_DOMAIN + "/help)\n" +
                                            "- [Join our support server](https://discord.gg/8EZ7BEz), we'll always be there for you!")
                                    .setColor(Utility.getEmbedColor(event.getGuild())).build())
                    .build()).queue());
        });
    };

    private static final BootupTask loadData = args -> {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Log.info("Shutting down...");
            Cache.onClose();
            GamesROB.plots.interrupt();
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
            GamesROB.owners = Collections.unmodifiableList(GamesROB.dboAPI.get().getBot().getOwners().stream().map(Long::parseLong).collect(Collectors.toList()));
            owners = GamesROB.owners;
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

    private static final BootupTask sendChangelog = args -> {
        changelog = Utility.readResource("/changelog.txt");
        Statistics statistics = Statistics.get();
        if (!GamesROB.VERSION.equals(statistics.getLastUpdateLogSent()) && Constants.changelogChannel.isPresent()) {
            statistics.setLastUpdateLogSent(GamesROB.VERSION);
            statistics.setLastUpdateLogSentTime(System.currentTimeMillis());
            GamesROB.getTextChannelById(Constants.changelogChannel.get()).ifPresent(channel ->
                    channel.sendMessage("<@&389918430733664256>\n<:update:264184209617321984> **GamesROB v" + GamesROB.VERSION + " is available!**" +
                        "\n\nChangelog:\n" + changelog + "\n\n*Updates are usually scheduled for every friday, " +
                        "making the next update " + Utility.formatTime(Utility.predictNextUpdate()) + ".*\n" +
                            "If you don't want to receive these messages, run `!unsubscribe` in <#389921363093356554>.")
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
