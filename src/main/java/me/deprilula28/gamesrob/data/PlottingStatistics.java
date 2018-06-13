package me.deprilula28.gamesrob.data;

import me.deprilula28.gamesrob.BootupProcedure;
import me.deprilula28.gamesrob.GamesROB;
import me.deprilula28.gamesrob.baseFramework.Match;
import me.deprilula28.gamesrob.commands.CommandManager;
import net.dv8tion.jda.core.JDA;

import java.io.DataOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class PlottingStatistics {
    private DataOutputStream stream;

    public PlottingStatistics() {

    }

    private Map<String, Supplier<Long>> getProviders() {
        Map<String, Supplier<Long>> map = new HashMap<>();

        map.put("avgCommandDelay", () -> {
            double delay = CommandManager.avgCommandDelay;
            CommandManager.avgCommandDelay = 0;
            return (long) (delay * 1000000);
        });
        map.put("totalCommandsExecuted", () -> Statistics.get().getCommandCount());
        map.put("totalGamesPlayed", () -> Statistics.get().getGameCount());
        map.put("activeGames", () -> GamesROB.shards.stream().mapToLong(it -> Match.ACTIVE_GAMES.get(it).size()).sum());
        map.put("websocketPing", () -> (long) GamesROB.shards.stream().mapToLong(JDA::getPing).average().getAsDouble());

        map.put("guilds", () -> GamesROB.shards.stream().mapToLong(it -> it.getGuilds().size()).sum());
        map.put("users", () -> GamesROB.shards.stream().mapToLong(it -> it.getUsers().size()).sum());
        map.put("textChannels", () -> GamesROB.shards.stream().mapToLong(it -> it.getTextChannels().size()).sum());

        map.put("ramUsage", () -> {
            Runtime rt = Runtime.getRuntime();
            long free = rt.freeMemory();
            long allocated = rt.totalMemory();

            return allocated - free;
        });
        map.put("upvotes", () -> Statistics.get().getUpvotes());

        return map;
    }
}
