package me.deprilula28.gamesrob.data;

import me.deprilula28.gamesrob.BootupProcedure;
import me.deprilula28.gamesrob.GamesROB;
import me.deprilula28.gamesrob.baseFramework.Match;
import me.deprilula28.gamesrob.commands.CommandManager;
import me.deprilula28.gamesrob.utility.Log;
import net.dv8tion.jda.core.JDA;

import java.io.DataOutputStream;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class PlottingStatistics extends Thread {
    private Map<String, Supplier<Long>> providers = getProviders();
    public PlottingStatistics() {
        setName("Statistics plotting thread");
        setDaemon(false);
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
                Thread.sleep(TimeUnit.MINUTES.toMillis(10));
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
        map.put("activegames", () -> GamesROB.shards.stream().mapToLong(it -> Match.ACTIVE_GAMES.get(it).size()).sum());
        map.put("websocketping", () -> (long) GamesROB.shards.stream().mapToLong(JDA::getPing).average().getAsDouble());

        map.put("guilds", () -> GamesROB.shards.stream().mapToLong(it -> it.getGuilds().size()).sum());
        map.put("users", () -> GamesROB.shards.stream().mapToLong(it -> it.getUsers().size()).sum());
        map.put("textchannels", () -> GamesROB.shards.stream().mapToLong(it -> it.getTextChannels().size()).sum());

        map.put("ramusage", () -> {
            Runtime rt = Runtime.getRuntime();
            long free = rt.freeMemory();
            long allocated = rt.totalMemory();

            return allocated - free;
        });
        map.put("upvotes", () -> Statistics.get().getUpvotes());

        return map;
    }
}
