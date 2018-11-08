package me.deprilula28.gamesrob.utility;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import me.deprilula28.gamesrob.commands.CommandManager;
import me.deprilula28.gamesrobshardcluster.utilities.Constants;
import me.deprilula28.gamesrobshardcluster.utilities.Log;
import me.deprilula28.gamesrobshardcluster.utilities.ShardClusterUtilities;

import javax.xml.ws.Provider;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class Cache {
    @Getter private static final Map<Object, CachedObjectData> cachedMap = new ConcurrentHashMap<>();
    private static long allCleaned = 0;

    @AllArgsConstructor
    @Data
    public static class CachedObjectData {
        Object result;
        long added;
        Consumer<Object> onRemove;
    }

    public static long getRAMUsage() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }

    static {
        Thread cleaner = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(Constants.CACHE_SLEEP_TIME);
                    long time = System.currentTimeMillis();
                    int removed = 0;

                    long before = getRAMUsage();
                    for (Map.Entry<Object, CachedObjectData> cur : cachedMap.entrySet()) {
                        CachedObjectData object = cur.getValue();
                        if (time > object.added + Constants.OBJECT_STORE_TIME) {
                            cachedMap.remove(cur.getKey());
                            removed ++;
                            if (object.onRemove != null) object.onRemove.accept(object.result);
                        }
                    }
                    CommandManager.commandStart.clear();
                    long after = getRAMUsage();

                    if (removed > 0) {
                        allCleaned += removed;
                        Log.info("GamesROB garbage cleaner removed " + removed + " (" + ShardClusterUtilities.formatBytes(after - before) + "), "
                                + Utility.addNumberDelimitors(allCleaned) + " total.");
                    }
                } catch (Exception ex) {
                    Log.exception("Running cache GC task", ex);
                }
            }
        });

        cleaner.setDaemon(true);
        cleaner.setName("Cache GC");
        cleaner.start();
    }

    public static void clear(Object object) {
        if (cachedMap.containsKey(object)) {
            CachedObjectData data = cachedMap.get(object);
            if (data.onRemove != null) data.onRemove.accept(data.result);
            cachedMap.remove(object);
        }
    }

    public static void onClose() {
        cachedMap.forEach((key, value) -> {
            cachedMap.remove(key);
            if (value.onRemove != null) value.onRemove.accept(value.result);
        });
    }

    public static <K extends Object, V extends Object> V get(K key, Provider<V> backup) {
        return get(key, backup, null);
    }

    public static <K extends Object, V extends Object> V get(K key, Provider<V> backup, Consumer<Object> onRemove) {
        if (cachedMap.containsKey(key)) {
            CachedObjectData data = cachedMap.get(key);
            return (V) data.result;
        } else {
            V result = backup.invoke(null);
            cachedMap.put(key, new CachedObjectData(result, System.currentTimeMillis(), onRemove));

            return result;
        }
    }
}
