package me.deprilula28.gamesrob.data;

import javafx.util.Pair;
import lombok.Getter;
import me.deprilula28.gamesrob.utility.Log;
import me.deprilula28.gamesrob.utility.Utility;
import org.postgresql.util.PSQLException;

import java.sql.*;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class SQLDatabaseManager {
    private ExecutorService asyncExecutor = Executors.newFixedThreadPool(4);
    private String url;
    private Optional<Pair<String, String>> login = Optional.empty();
    @Getter private Connection connection;

    private List<String> batchCommands;
    private List<Consumer<ResultSet>> batchSelectHandlers;
    private List<BiFunction<Integer, PreparedStatement, Integer>> batchExecuteHandlers;

    public SQLDatabaseManager(String connectionInfo) {
        String[] attribs = connectionInfo.split(",");
        if (attribs.length < 2) throw new RuntimeException("Required specification of driver class and database path.");
        Log.wrapException("Loading SQL Database", () -> {
            Log.info("SQL database specifications:\nDriver is " + attribs[0] + "\nDatabase is " + attribs[1]);
            Class.forName(attribs[0]);
            this.url = attribs[1];
            if (attribs.length == 4) {
                login = Optional.of(new Pair<>(attribs[2], attribs[3]));
                Log.info("Login is " + attribs[2]);
            }

            if (login.isPresent()) connection = DriverManager.getConnection(url, login.get().getKey(), login.get().getValue());
            else connection = DriverManager.getConnection(url);
            registerTables();
        });
    }

    public void batchRun() {
        asyncExecutor.execute(() -> Log.wrapException("Batched SQL Execute", () -> {
            String sql = String.join(";", batchCommands);
            PreparedStatement statement = connection.prepareStatement(sql);
            int curOffset = 1;
            for (BiFunction<Integer, PreparedStatement, Integer> cur : batchExecuteHandlers)
                curOffset += cur.apply(curOffset, statement);

            ResultSet set = statement.executeQuery();
            int index = 0;
            while (set.next()) {
                batchSelectHandlers.get(index).accept(set);
                index ++;
            }
            statement.close();
        }));
    }

    public void batchedExecute(String sql, BiFunction<Integer, PreparedStatement, Integer> cons) {
        batchExecuteHandlers.add(cons);
        batchCommands.add(sql);
    }

    public Utility.Promise<ResultSet> batchedSqlQuery(String sql) {
        Utility.Promise<ResultSet> promise = new Utility.Promise<>();
        batchSelectHandlers.add(promise::done);
        batchCommands.add(sql);

        return promise;
    }

    private Utility.Promise<Void> sqlExecute(String sql, Consumer<PreparedStatement> cons) {
        Utility.Promise<Void> promise = new Utility.Promise<>();
        asyncExecutor.execute(() -> Log.wrapException("SQL Execute " + sql, () -> {
            PreparedStatement statement = connection.prepareStatement(sql);
            cons.accept(statement);
            statement.close();

            promise.done(null);
        }));
        return promise;
    }

    public ResultSet sqlQuery(String sql) throws Exception {
        return connection.createStatement().executeQuery(sql);
    }

    public Utility.Promise<Void> save(String table, List<String> keys, String where, Predicate<ResultSet> checkSameValue,
                                      boolean batched, BiConsumer<Optional<ResultSet>, PreparedStatement> consumer) {
        try {
            ResultSet set = select(table, keys, where);
            if (set.next()) {
                if (checkSameValue.test(set)) return Utility.Promise.result(null);
                else return update(table, keys, where, statement -> consumer.accept(Optional.of(set), statement));
            } else return insert(table, keys, statement -> consumer.accept(Optional.empty(), statement));
        } catch (PSQLException ex) {
            Log.trace(ex.getClass().getName() + ": " + ex.getMessage());
            return insert(table, keys, statement -> consumer.accept(Optional.empty(), statement));
        } catch (Exception e) {
            Log.exception("Saving SQL Data", e);
            return null;
        }
    }

    public ResultSet select(String table, List<String> items, String where, String orderBy, boolean desc, int limit, int offset) throws Exception {
        return sqlQuery(String.format(
                "SELECT %s FROM %s WHERE %s ORDER BY %s%s LIMIT %s OFFSET %s",
                items.stream().collect(Collectors.joining(", ")),
                table, where, orderBy, desc ? " DESC" : "", limit, offset
        ));
    }

    public ResultSet select(String table, List<String> items, String orderBy, boolean desc, int limit, int offset) throws Exception {
        return sqlQuery(String.format(
                "SELECT %s FROM %s ORDER BY %s%s LIMIT %s OFFSET %s",
                items.stream().collect(Collectors.joining(", ")),
                table, orderBy, desc ? " DESC" : "", limit, offset
        ));
    }

    public ResultSet select(String table, List<String> items, String where, int limit, int offset) throws Exception {
        return sqlQuery(String.format(
                "SELECT %s FROM %s WHERE %s LIMIT %s OFFSET %s",
                items.stream().collect(Collectors.joining(", ")),
                table, where, limit, offset
        ));
    }

    public int getSize(String table) throws Exception {
        ResultSet set = sqlQuery(String.format(
                "SELECT COUNT(*) FROM %s",
                table
        ));
        if (set.next()) return set.getInt("count");
        else return 0;
    }

    public int getSize(String table, String where) throws Exception {
        ResultSet set = sqlQuery(String.format(
                "SELECT COUNT(*) FROM %s WHERE %s",
                table, where
        ));
        if (set.next()) return set.getInt("count");
        else return 0;
    }

    public ResultSet select(String table, List<String> items, String where) throws Exception {
         return sqlQuery(String.format(
                 "SELECT %s FROM %s WHERE %s",
                 items.stream().collect(Collectors.joining(", ")),
                 table, where
         ));
    }

    public Utility.Promise<Void> insert(String table, List<String> keys, Consumer<PreparedStatement> consumer) {
        StringBuilder keysBuilder = new StringBuilder();
        StringBuilder valuesBuilder = new StringBuilder();

        keys.forEach(key -> {
            if (keysBuilder.length() > 0) keysBuilder.append(", ");
            keysBuilder.append(key);

            if (valuesBuilder.length() > 0) valuesBuilder.append(", ");
            valuesBuilder.append("?");
        });

        return sqlExecute(String.format(
                "INSERT INTO %s (%s) VALUES (%s)",
                table, keysBuilder.toString(), valuesBuilder.toString()
        ), statement -> Log.wrapException("Inserting to SQL table", () -> {
            consumer.accept(statement);
            statement.executeUpdate();
        }, table, keys, consumer));
    }

    public Utility.Promise<Void> update(String table, List<String> keys, String where, Consumer<PreparedStatement> consumer) {
        StringBuilder valuesBuilder = new StringBuilder();
        keys.forEach(key -> {
            if (valuesBuilder.length() > 0) valuesBuilder.append(", ");
            valuesBuilder.append(key).append(" = ?");
        });

       return sqlExecute(String.format(
                "UPDATE %s SET %s WHERE %s",
                table, valuesBuilder.toString(), where
        ), statement -> Log.wrapException("Update SQL table", () -> {
           consumer.accept(statement);
           statement.executeUpdate();
       }));
    }

    public void sqlFileExecute(String path, Consumer<PreparedStatement> cons) {
        asyncExecutor.execute(() -> Log.wrapException("Executing SQL file", () -> {
            PreparedStatement statement = connection.prepareStatement(Utility.readResource("/sql/" + path));

            cons.accept(statement);
            statement.executeUpdate();
            statement.close();
        }));
    }

    public ResultSet sqlFileQuery(String path, Consumer<PreparedStatement> cons, Object... format) throws Exception {
        PreparedStatement statement = connection.prepareStatement(String.format(Utility.readResource("/sql/" + path), (Object[]) format));
        cons.accept(statement);

        return statement.executeQuery();
    }

    private void registerTables() {
        sqlFileExecute("makeTables.sql", st -> {});
    }
}
