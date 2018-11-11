package me.deprilula28.gamesrob.data;

import me.deprilula28.gamesrob.GamesROB;
import lombok.AllArgsConstructor;
import lombok.Data;
import me.deprilula28.gamesrob.baseFramework.GamesInstance;
import me.deprilula28.gamesrob.utility.Cache;
import me.deprilula28.gamesrobshardcluster.utilities.Constants;
import me.deprilula28.gamesrobshardcluster.utilities.Log;
import me.deprilula28.gamesrob.utility.Utility;
import me.deprilula28.jdacmdframework.CommandContext;
import net.dv8tion.jda.core.entities.Message;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.function.Consumer;

@Data
@AllArgsConstructor
public class Statistics {
    private long gameCount;
    private long totalTime;
    private long firstBoot;
    private String lastUpdateLogSent;
    private long lastUpdateLogSentTime;
    private Map<String, Integer> perGameCount;
    private long commandCount;
    private long upvotes;
    private long monthUpvotes;
    private long gameplayTime;
    private long totConnections;
    private Map<String, Long> perGameGameplayTime;
    private Map<String, Integer> perCommandCount;
    private double totalImageCommandGenTime;
    private double totalImageCommandPostTime;
    private long totalImageCommandBytes;
    private long imageCommands;

    public Consumer<Message> registerImageProcessor(long generateTimeNano, int byteAmount) {
        imageCommands ++;
        totalImageCommandBytes += byteAmount;
        totalImageCommandGenTime += generateTimeNano / 1000000.0;
        long postBegin = System.nanoTime();
        return message -> totalImageCommandPostTime += (double) (System.nanoTime() - postBegin) / 1000000.0;
    }

    public void registerCommand(CommandContext context) {
        String name = context.getCurrentCommand().getName();
        perCommandCount.put(name, perCommandCount.containsKey(name) ? perCommandCount.get(name) + 1 : 1);
    }

    public void registerGameTime(long time, GamesInstance instance) {
        gameplayTime += time;
        perGameGameplayTime.put(instance.getLanguageCode(), perGameGameplayTime.containsKey(instance.getLanguageCode())
                ? perGameGameplayTime.get(instance.getLanguageCode()) + time : time);
    }

    public void registerGame(GamesInstance instance) {
        gameCount ++;
        perGameCount.put(instance.getLanguageCode(), perGameCount.containsKey(instance.getLanguageCode())
                ? perGameCount.get(instance.getLanguageCode()) + 1 : 1);
    }

    public void save() {
        File file = Constants.STATS_FILE;
        totalTime += System.currentTimeMillis() - GamesROB.UP_SINCE;

        if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
        if (file.exists()) file.delete();

        Closeable toClose = null;
        try {
            file.createNewFile();

            FileWriter writer = new FileWriter(file);
            toClose = writer;
            writer.write(Constants.GSON.toJson(this));
            Log.info("Saved statistics.");
        } catch (Exception e) {
            Log.exception("Saving statistics", e, this);
        } finally {
            if (toClose != null) Utility.quietlyClose(toClose);
        }
    }

    public static Statistics get() {
        return Cache.get("statistics", n -> {
            File file = Constants.STATS_FILE;
            if (file.exists()) {
                Scanner scann = null;
                try {
                    file.createNewFile();

                    scann = new Scanner(new FileInputStream(file));
                    StringBuilder str = new StringBuilder();

                    while (scann.hasNextLine()) str.append(scann.nextLine());
                    scann.close();

                    Statistics stats = Constants.GSON.fromJson(str.toString(), Statistics.class);
                    if (stats.getPerGameCount() == null) stats.setPerGameCount(new HashMap<>());
                    if (stats.getPerCommandCount() == null) stats.setPerCommandCount(new HashMap<>());
                    if (stats.getPerGameGameplayTime() == null) stats.setPerGameGameplayTime(new HashMap<>());

                    return stats;
                } catch (Exception e) {
                    Log.exception("Loading statistics", e);
                } finally {
                    if (scann != null) Utility.quietlyClose(scann);
                }
                return null;
            } else return new Statistics(
                    0L, 0L, System.currentTimeMillis(), null, 0,
                    new HashMap<>(), 0, 0L, 0L, 0L, 0L, new HashMap<>(),
                    new HashMap<>(), 0.0, 0.0, 0, 0
            );
        }, object -> ((Statistics) object).save());
    }
}
