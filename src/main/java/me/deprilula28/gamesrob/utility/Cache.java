package me.deprilula28.gamesrob.utility;

import lombok.AllArgsConstructor;
import me.deprilula28.gamesrob.GamesROB;

import javax.xml.ws.Provider;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class Cache {
    private static Map<Object, CachedObjectData> cachedMap = new ConcurrentHashMap<>();
    private static long allCleaned = 0;

    @AllArgsConstructor
    private static class CachedObjectData {
        Object result;
        long added;
        Consumer<Object> onRemove;
    }

    public static long getRAMUsage() {
        return Utility.getRamUsage(cachedMap);
    }

    static {
        Thread cleaner = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(GamesROB.debug ? 5 * 1000 : 30 * 1000);
                    long time = System.currentTimeMillis();
                    int removed = 0;

                    long before = getRAMUsage();
                    for (Map.Entry<Object, CachedObjectData> cur : cachedMap.entrySet()) {
                        CachedObjectData object = cur.getValue();
                        if (time > object.added + Constants.RAM_CACHE_TIME) {
                            cachedMap.remove(cur.getKey());
                            removed ++;
                            if (object.onRemove != null) object.onRemove.accept(object.result);
                        }
                    }
                    long after = getRAMUsage();

                    if (removed > 0) {
                        allCleaned += removed;
                        Log.info("(GC) Cleaned " + removed + ", " + Utility.addNumberDelimitors(allCleaned) + "total.");
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
