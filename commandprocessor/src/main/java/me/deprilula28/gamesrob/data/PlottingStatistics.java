package me.deprilula28.gamesrob.data;

import me.deprilula28.gamesrob.GamesROB;
import me.deprilula28.gamesrob.baseFramework.Match;
import me.deprilula28.gamesrob.commands.CommandManager;
import me.deprilula28.gamesrobshardcluster.GamesROBShardCluster;
import me.deprilula28.gamesrobshardcluster.utilities.Log;
import me.deprilula28.gamesrobshardcluster.utilities.ShardClusterUtilities;
import net.dv8tion.jda.core.JDA;

import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class PlottingStatistics extends Thread {
    private Map<String, Supplier<Long>> providers = getProviders();
    public PlottingStatistics() {
        setName("Statistics plotting thread");
        setDaemon(false);
    }

    public static ResultSet getSetClosestTime(long time) throws Exception {
        return GamesROB.database.orElseThrow(() -> new RuntimeException("Requires DB"))
                .sqlFileQuery("selectStatistics.sql", statement -> Log.wrapException("Getting closest time", () -> {
            statement.setLong(1, time);
            statement.setLong(2, time);
            statement.setLong(3, time);
        }));
    }

    @Override
    public void run() {
        while (true) {
            List<String> save = new ArrayList<>(providers.keySet());
            save.add("time");
            GamesROB.database.ifPresent(db -> db.insert("statsPlots", save, statement -> {
                Log.wrapException("Adding SQL statistics plot", () -> {
                    int i = 1;
                    for (Map.Entry<String, Supplier<Long>> entry : providers.entrySet()) {
                        statement.setLong(i, entry.getValue().get());
                        i ++;
                    }
                    statement.setLong(i, System.currentTimeMillis());
                });
            }));
            Log.info("Saved stats.");

            try {
                Thread.sleep(TimeUnit.MINUTES.toMillis(30));
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private Map<String, Supplier<Long>> getProviders() {
        Map<String, Supplier<Long>> map = new HashMap<>();

        map.put("avgcommanddelay", () -> {
            double delay = CommandManager.avgCommandDelay;
            CommandManager.avgCommandDelay = 0;
            return (long) (delay * 1000000);
        });
        map.put("totalcommandsexecuted", () -> Statistics.get().getCommandCount());
        map.put("totalgamesplayed", () -> Statistics.get().getGameCount());
        map.put("activegames", () -> GamesROBShardCluster.shards.stream().mapToLong(it -> Match.ACTIVE_GAMES.get(it).size()).sum());
        map.put("websocketping", () -> (long) GamesROBShardCluster.shards.stream().mapToLong(JDA::getPing).average().getAsDouble());

        map.put("guilds", () -> GamesROBShardCluster.shards.stream().mapToLong(it -> it.getGuilds().size()).sum());
        map.put("users", () -> GamesROBShardCluster.shards.stream().mapToLong(it -> it.getUsers().size()).sum());
        map.put("textchannels", () -> GamesROBShardCluster.shards.stream().mapToLong(it -> it.getTextChannels().size()).sum());

        map.put("ramusage", ShardClusterUtilities::getRawRAM);
        map.put("upvotes", () -> Statistics.get().getUpvotes());

        return map;
    }
}
