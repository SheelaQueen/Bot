package me.deprilula28.gamesrob.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import me.deprilula28.gamesrob.GamesROB;
import me.deprilula28.gamesrob.baseFramework.GamesInstance;
import me.deprilula28.gamesrob.utility.Cache;
import me.deprilula28.gamesrob.utility.Constants;
import me.deprilula28.gamesrob.utility.Log;
import me.deprilula28.gamesrob.utility.Utility;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

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

    public long getTotalUptime() {
        return totalTime + (System.currentTimeMillis() - GamesROB.UP_SINCE);
    }

    public void registerGame(GamesInstance instance) {
        gameCount ++;
        perGameCount.put(instance.getLanguageCode(), perGameCount.containsKey(instance.getLanguageCode()) ?
                perGameCount.get(instance.getLanguageCode()) + 1 : 1);
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

                    return Constants.GSON.fromJson(str.toString(), Statistics.class);
                } catch (Exception e) {
                    Log.exception("Loading statistics", e);
                } finally {
                    if (scann != null) Utility.quietlyClose(scann);
                }
                return null;
            } else return new Statistics(
                    0L, 0L, System.currentTimeMillis(), null, 0,
                    new HashMap<>(), 0, 0L, 0L
            );
        }, object -> ((Statistics) object).save());
    }
}
