package me.deprilula28.gamesrobshardcluster;

import me.deprilula28.gamesrobshardcluster.utilities.Constants;
import me.deprilula28.gamesrobshardcluster.utilities.Log;
import me.deprilula28.gamesrobshardcluster.utilities.ShardClusterUtilities;
import me.deprilula28.gamesrobshardcluster.utilities.Trello;
import me.deprilula28.jdacmdframework.CommandFramework;
import me.deprilula28.jdacmdframework.Settings;
import net.dv8tion.jda.core.*;
import net.dv8tion.jda.core.entities.Game;
import org.trello4j.TrelloImpl;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class BootupProcedure {
    public static void bootup(String[] args) {
        long begin = System.currentTimeMillis();
        List<String> argList = Arrays.asList(args);
        task(argList, "Loading arguments", loadArguments);
        task(argList, "Connecting to Discord", connectDiscord);
        task(argList, "Loading framework", frameworkLoad);
        task(argList, "Load Command Processor", GamesROBShardCluster::loadCommandProcessor);
        Log.info("Bot fully loaded in " + ShardClusterUtilities.formatPeriod(System.currentTimeMillis() - begin) + "!");
    }

    @FunctionalInterface
    private static interface BootupTask {
        void execute(List<String> args) throws Exception;
    }

    private static String token;
    private static int shardTo;
    public static int shardFrom;
    private static int totalShards;

    private static final BootupTask loadArguments = args -> {
        List<Optional<String>> pargs = ShardClusterUtilities.matchValues(args, "token", "dblToken", "shards", "ownerID",
                "sqlDatabase", "debug", "twitchUserID", "clientSecret", "twitchClientID", "rpcServerIP",
                "shardId", "totalShards", "useRedis", "trelloToken", "dbotsToken", "botsfordiscordToken",
                "commandProcessorFilePath", "premium");
        token = pargs.get(0).orElseThrow(() -> new RuntimeException("You need to provide a token!"));
        shardTo = pargs.get(2).map(Integer::parseInt).orElse(1);
        shardFrom = pargs.get(10).map(Integer::parseInt).orElse(0);
        totalShards = pargs.get(11).map(Integer::parseInt).orElse(shardTo);
        GamesROBShardCluster.debug = pargs.get(5).map(Boolean::parseBoolean).orElse(false);
        Trello.optTrello = pargs.get(13).map(it -> new TrelloImpl(Constants.TRELLO_API_KEY, it));
        GamesROBShardCluster.commandProcessorFilePath = pargs.get(16).orElse("commandprocessor.jar");
        GamesROBShardCluster.premiumBot = pargs.get(17).map(Boolean::parseBoolean).orElse(false);
    };

    private static final Game[] LOADING_MESSAGES = {
            Game.watching("it all load.."), Game.watching("life pass me by..."),
            Game.listening("the cogs spinning"), Game.playing("something. Maybe that's why it's taking so long?"),
            Game.playing("01100001011011010010000001101100011011110110000101100100")
    };

    private static final BootupTask connectDiscord = args -> {
        int curShard = shardFrom;
        Game game = LOADING_MESSAGES[ThreadLocalRandom.current().nextInt(LOADING_MESSAGES.length)];
        while (curShard < shardTo) {
            JDA jda = new JDABuilder(AccountType.BOT).setToken(token)
                    .useSharding(curShard, totalShards).setStatus(OnlineStatus.DO_NOT_DISTURB)
                    .setGame(game).build();
            GamesROBShardCluster.shards.add(jda);

            curShard ++;
            if (curShard < shardTo) Thread.sleep(5000L);
        }
    };

    private static final BootupTask frameworkLoad = args -> {
        CommandFramework f = new CommandFramework(GamesROBShardCluster.shards, Settings.builder()
                .loggerFunction(Log::info).removeCommandMessages(false).protectMentionEveryone(true)
                .prefix("").async(true).threadPool(new ThreadPoolExecutor(10, 100, 5, TimeUnit.MINUTES,
                        new LinkedBlockingQueue<>())).joinQuotedArgs(true).dmOnCantTalk(null)
                .genericExceptionFunction((message, exception) -> Log.exception(message, exception))
                .caseIndependent(true)
                .build());
        f.listenEvents();

        GamesROBShardCluster.framework = f;
        // Commands
    };

    /*"<@&484522326906503172>\n<:update:264184209617321984> **GamesROB v" + GamesROB.VERSION + " is available!**" +
                        "\n\nChangelog:\n" + changelog + "\n\n*Updates are usually scheduled for every friday, " +
                        "making the next update " + Utility.formatTime(Utility.predictNextUpdate()) + ".*\n" +
                            "If you don't want to get pinged in these messages, you can remove your reaction on <#358451223612882944>."
                            */

    private static void task(List<String> args, String name, BootupTask task) {
        Log.info(name + "...");
        long begin = System.currentTimeMillis();
        Log.wrapException("Failed to bootup on task: " + name, () -> task.execute(args));
        long time = System.currentTimeMillis() - begin;
        Log.info("Finished in " + ShardClusterUtilities.formatPeriod(time));
    }
}
