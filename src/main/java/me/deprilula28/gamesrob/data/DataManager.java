package me.deprilula28.gamesrob.data;

import me.deprilula28.gamesrob.GamesROB;
import me.deprilula28.gamesrob.utility.Cache;
import me.deprilula28.gamesrob.utility.Constants;
import me.deprilula28.gamesrob.utility.Log;
import me.deprilula28.gamesrob.utility.Utility;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.util.Optional;
import java.util.Scanner;

public abstract class DataManager<A, R> {
    public static Optional<Jedis> jedisOpt = Optional.empty();

    public abstract File getDataFile(A from);
    public abstract Optional<R> getFromSQL(SQLDatabaseManager db, A from) throws Exception;
    public abstract Utility.Promise<Void> saveToSQL(SQLDatabaseManager db, R value);
    public abstract R createNew(A from);

    public R get(A from, Class<R> rClass) {
        Optional<SQLDatabaseManager> db = GamesROB.database;
        File file = getDataFile(from);
        if (!GamesROB.debug) if (jedisOpt.isPresent()) {
            String jedisStored = jedisOpt.get().get(file.getAbsolutePath());
            if (jedisStored != null) {
                R obj = Constants.GSON.fromJson(jedisStored, rClass);
                Cache.get(from, n -> obj, it -> {
                    if (db.isPresent()) saveToSQL(db.get(), (R) it);
                    else save(file, it);
                });
                return obj;
            }
        }

        if (db.isPresent()) {
            return Cache.get(from, n -> {
                try {
                    return getFromSQL(db.get(), from).orElseGet(() -> createNew(from));
                } catch (Exception e) {
                    Log.exception("Requesting to SQL Handler", e);
                    return createNew(from);
                }
            }, it -> {
                if (!((R) it).equals(createNew(from))) saveToSQL(db.get(), (R) it).then(n -> jedisOpt.ifPresent(jedis -> {
                    jedis.set(file.getAbsolutePath(), Constants.GSON.toJson(it));
                    jedis.expire(file.getAbsolutePath(), 120);
                }));
            });
        }
        else return Cache.get(from, n -> {
            if (!file.exists()) return createNew(from);

            Scanner scann = null;
            try {
                scann = new Scanner(new FileInputStream(file));
                StringBuilder str = new StringBuilder();

                while (scann.hasNextLine()) str.append(scann.nextLine());
                scann.close();

                R result = Constants.GSON.fromJson(str.toString(), rClass);
                return result == null ? createNew(from) : result;
            } catch (Exception e) {
                Log.exception("Loading data", e);
                if (scann != null) Utility.quietlyClose(scann);
                return createNew(from);
            }
        }, it -> save(file, it));
    }

    private static void save(File file, Object it) {
        if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
        if (file.exists()) file.delete();

        Closeable toClose = null;
        try {
            file.createNewFile();

            FileWriter writer = new FileWriter(file);
            toClose = writer;
            String json = Constants.GSON.toJson(it);
            writer.write(json);
            jedisOpt.ifPresent(jedis -> {
                jedis.set(file.getAbsolutePath(), json);
                jedis.expire(file.getAbsolutePath(), 600);
            });
        } catch (Exception e) {
            Log.exception("Saving data", e);
        } finally {
            if (toClose != null) Utility.quietlyClose(toClose);
        }
    }
}
