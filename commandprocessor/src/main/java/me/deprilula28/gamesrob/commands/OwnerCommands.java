package me.deprilula28.gamesrob.commands;

import com.google.gson.JsonPrimitive;
import me.deprilula28.gamesrob.BootupProcedure;
import me.deprilula28.gamesrob.GamesROB;
import me.deprilula28.gamesrob.utility.Language;
import me.deprilula28.gamesrob.data.RPCManager;
import me.deprilula28.gamesrob.data.UserProfile;
import javafx.util.Pair;
import lombok.AllArgsConstructor;
import lombok.Data;
import me.deprilula28.gamesrob.utility.*;
import me.deprilula28.gamesrobshardcluster.GamesROBShardCluster;
import me.deprilula28.gamesrobshardcluster.utilities.Constants;
import me.deprilula28.gamesrobshardcluster.utilities.Log;
import me.deprilula28.gamesrobshardcluster.utilities.ShardClusterUtilities;
import me.deprilula28.jdacmdframework.Command;
import me.deprilula28.jdacmdframework.CommandContext;
import me.deprilula28.jdacmdframework.CommandFramework;
import me.deprilula28.jdacmdframework.exceptions.InvalidCommandSyntaxException;
import net.dv8tion.jda.core.entities.User;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class OwnerCommands {
    private static ScriptEngine scriptEngine = new ScriptEngineManager().getEngineByName("nashorn");
    private static Announcement announcement = null;

    @AllArgsConstructor
    @Data
    public static class Announcement {
        private long announced;
        private long time;
        private String message;
        private User announcer;
    }

    public static Command.Executor tokenCommand(BiConsumer<UserProfile, Integer> profileUser, String langCode) {
        return context -> {
            if (!GamesROB.owners.contains(context.getAuthor().getIdLong())) return Language.transl(context,
                    "genericMessages.ownersOnly");
            User user = context.nextUser();
            int amount = context.nextInt();
            UserProfile profile = UserProfile.get(user);
            profileUser.accept(profile, amount);

            return Language.transl(context, langCode, amount, user.getName(), profile.getTokens());
        };
    }

    public static Optional<Announcement> getAnnouncement() {
        return Optional.ofNullable(announcement).filter(it -> System.currentTimeMillis() <= announcement.announced + announcement.time);
    }

    public static String announce(CommandContext context) {
        if (!GamesROB.owners.contains(context.getAuthor().getIdLong())) return Language.transl(context,
                "genericMessages.ownersOnly");

        announcement = new Announcement(
                System.currentTimeMillis(),
                ShardClusterUtilities.extractPeriod(context.next()),
                String.join(" ", context.remaining()),
                context.getAuthor()
        );
        return "Announcement set. It will now show in `g*help`.";
    }

    public static String servercount(CommandContext context) {
        if (!GamesROB.owners.contains(context.getAuthor().getIdLong())) return Language.transl(context,
                "genericMessages.ownersOnly");

        return "Last server count posts:\n" +
                "`DBL`: " + BootupProcedure.getLastDblRequest() + "\n" +
                "`BotsForDiscord`: " + BootupProcedure.getLastBfdRequest() + "\n" +
                "`Discord Bots`: " + BootupProcedure.getLastDbotsRequest();
    }

    public static String badges(CommandContext context) {
        if (!GamesROB.owners.contains(context.getAuthor().getIdLong())) return Language.transl(context,
                "genericMessages.ownersOnly");

        String language = Utility.getLanguage(context);
        String thing = context.next();
        User user = context.nextUser();
        UserProfile profile = UserProfile.get(user);

        if (thing.equalsIgnoreCase("add")) {
            UserProfile.Badge badge = UserProfile.Badge.valueOf(context.next().toUpperCase());
            profile.addBadge(badge);
            return "Added badge " + badge.getName(language) + " to " + user.getAsMention() + ".";
        } else if (thing.equalsIgnoreCase("remove")) {
            UserProfile.Badge badge = UserProfile.Badge.valueOf(context.next().toUpperCase());
            profile.getBadges().remove(badge);
            return "Removed badge " + badge.getName(language) + " to " + user.getAsMention() + ".";
        } else if (thing.equalsIgnoreCase("view")) {
            int encoded = Utility.encodeBinary(profile.getBadges(), UserProfile.Badge.class);
            return user.getName() + "'s badges:\n`0x" + Integer.toBinaryString(encoded) + "` " +  encoded + "\n"
                    + profile.getBadges().stream().map(it -> it.getName(language))
                    .collect(Collectors.joining("\n"));
        }
        else throw new InvalidCommandSyntaxException();
    }

    public static String cache(CommandContext context) {
        if (!GamesROB.owners.contains(context.getAuthor().getIdLong())) return Language.transl(context,
                "genericMessages.ownersOnly");

        String cache = Cache.getCachedMap().entrySet().stream().filter(it -> it.getKey() != null && it.getValue() != null
                && it.getValue().getResult() != null).map(it -> {
            String result = it.getValue().getResult().toString();

            return  "\"" + it.getKey().toString() + "\" = " + (result.length() > 100 ? result.substring(0, 100) + "..." : result)
                    + " (added " + ShardClusterUtilities.formatTime(it.getValue().getAdded()) + (it.getValue().getOnRemove() == null ? ")"
                    : " | has on remove)");
        }).collect(Collectors.joining("\n"));

        return "**Cache**\n```java\n" + (cache.length() > 1800 ? cache.substring(0, 1800) + "..." : cache)
                + "\n```";
    }

    public static String blacklist(CommandContext context) {
        if (!GamesROB.owners.contains(context.getAuthor().getIdLong())) return Language.transl(context,
                "genericMessages.ownersOnly");

        User blacklisting = context.nextUser();
        if (GamesROB.owners.contains(blacklisting.getIdLong()) || BootupProcedure.getOwners().contains(blacklisting.getIdLong()))
            return "Nope.";

        String reason = String.join(" ", context.remaining());
        if (reason.isEmpty()) return "But why tho";

        GamesROB.database.ifPresent(db -> {
            db.insert("blacklist", Arrays.asList("userid", "botownerid", "reason", "time"),
                    set -> Log.wrapException("Adding blacklist entry", () -> {
                set.setString(1, blacklisting.getId());
                set.setString(2, context.getAuthor().getId());
                set.setString(3, reason);
                set.setLong(4, System.currentTimeMillis());
            }));
        });
        Cache.clear("bl_" + blacklisting.getId());
        return "Oof";
    }

    public static String sql(CommandContext context) {
        if (!GamesROB.owners.contains(context.getAuthor().getIdLong())) return Language.transl(context,
                "genericMessages.ownersOnly");

        String query = String.join(" ", context.remaining());
        GamesROB.database.ifPresent(db -> {
            context.send("<a:typing:393848431413559296> Querying...");
            try {
                if (query.startsWith("query ")) {
                    ResultSet set = db.sqlQuery(query.substring("query ".length()));
                    ResultSetMetaData metadata = set.getMetaData();

                    List<String> columns = new ArrayList<>();
                    List<List<String>> texts = new ArrayList<>();
                    int count = metadata.getColumnCount();
                    for (int i = 1; i <= count; i ++) {
                        columns.add(metadata.getColumnName(i));
                        texts.add(new ArrayList<>());
                    }

                    while (set.next())
                        for (int i = 1; i <= count; i ++) {
                            Object obj = set.getObject(i);
                            String name = obj == null ? "null" : obj.toString();
                            texts.get(i - 1).add(name);
                        }

                    context.edit("<:check:314349398811475968> Results:\n```prolog\n" +
                            Utility.generateTable(columns, count, texts) +
                            "\n```");
                    return;
                }

                ResultSet tables = db.sqlQuery("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'");
                List<String> tableNames = new ArrayList<>();
                while (tables.next()) tableNames.add(tables.getString("table_name"));

                if (tableNames.contains(query.split(" ")[0])) {
                    String table = query.split(" ")[0];
                    context.edit("<:check:314349398811475968> Table Information:\n```" +
                            "Name: " + table + "\n" +
                            "Size: " + db.getSize(table) + " entries\n" +
                            "\n```");
                    return;
                }

                StringBuilder message = new StringBuilder("<:check:314349398811475968> Tables:\n");
                for (String table : tableNames) {
                    message.append(table).append(" - ").append(db.getSize(table)).append(" entries\n");
                }

                Log.info(message.toString());
                context.edit(message.toString());
            } catch (Exception e) {
                e.printStackTrace();
                context.edit("<:xmark:314349398824058880> An error occured:\n```java\n" + e.getClass().getName() + ": " + e.getMessage() + "\n```");
            }
        });
        return null;
    }

    public static String console(CommandContext context) {
        if (!GamesROB.owners.contains(context.getAuthor().getIdLong())) return Language.transl(context,
                "genericMessages.ownersOnly");

        StringBuilder curMessage = new StringBuilder();
        AtomicBoolean done = new AtomicBoolean(false);
        context.send("<a:typing:393848431413559296> Executing...");

        Thread thread = new Thread(() -> {
            String lastCurMessage = null;
            while (!done.get()) {
                try {
                    if (!curMessage.toString().equals(lastCurMessage)) context.edit("<a:loading:393852367751086090> " +
                            "Executing...\n```\n" + (curMessage.length() > 1000
                                    ? "...\n" + curMessage.toString().substring(curMessage.length() - 1000, curMessage.length())
                                    : curMessage.toString()) + "\n```<a:cursor:404001393360502805>");
                    lastCurMessage = curMessage.toString();
                    Thread.sleep(1000L);
                } catch (InterruptedException ex) {
                    break;
                }
            }
        });
        thread.setName("Text updater");
        thread.start();

        BufferedReader input = null;
        try {
            Process process = Runtime.getRuntime().exec(String.join(" ", context.remaining()));
            input = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;
            while ((line = input.readLine()) != null) {
                if (curMessage.length() > 0) curMessage.append("\n");
                curMessage.append(line);
            }

            thread.interrupt();
            done.set(true);
            context.edit("<:check:314349398811475968> Output:\n```\n" + (curMessage.length() > 1000
                    ? "...\n" + curMessage.toString().substring(curMessage.length() - 1000, curMessage.length())
                    : curMessage.toString()) + "\n```");
        } catch (Exception e) {
            thread.interrupt();
            done.set(true);
            e.printStackTrace();
            context.edit("<:xmark:314349398824058880> An error occured:\n```java\n" + e.getClass().getName() + ": " + e.getMessage() + "\n```");
            if (input != null) Utility.quietlyClose(input);
        }

        return null;
    }

    public static String eval(CommandContext context) {
        if (!GamesROB.owners.contains(context.getAuthor().getIdLong())) return Language.transl(context,
                "genericMessages.ownersOnly");

        scriptEngine.put("guild", context.getGuild());
        scriptEngine.put("channel", context.getChannel());
        scriptEngine.put("jda", context.getJda());
        scriptEngine.put("me", context.getAuthor());
        scriptEngine.put("member", context.getAuthorMember());
        scriptEngine.put("shards", GamesROBShardCluster.shards);
        scriptEngine.put("event", context.getEvent());

        context.send("<a:typing:393848431413559296> Evaluating...");
        Object response;
        long begin = System.nanoTime();
        boolean success;
        try {
            response = scriptEngine.eval(String.join(" ", context.remaining()));
            success = true;
        } catch (Exception e) {
            response = e.getClass().getName() + ": " + e.getMessage();
            success = false;
        }
        double time = (double) (System.nanoTime() - begin) / 1000000000.0;

        String text = response == null ? "null" : (response instanceof String ? (String) response : response.toString());
        text = text.replaceAll(context.getJda().getToken(), "how about no");
        String message = (success
                ? "<:check:314349398811475968> Output took " + ShardClusterUtilities.formatPeriod(time)
                : "<:xmark:314349398824058880> Failed in " + ShardClusterUtilities.formatPeriod(time)) + ":\n" +
                "```js\n" + (text.length() > 1500 ? text.substring(0, 1500) + "..." : text) + "\n```";

        context.edit(message);

        return null;
    }

    public static Object updateCommand(CommandContext context) {
        if (!GamesROB.owners.contains(context.getAuthor().getIdLong())) return Language.transl(context,
                "genericMessages.ownersOnly");

        String updateURL = context.opt(context::next).orElseGet(() -> context.getMessage().getAttachments().get(0).getUrl());
        context.send("<a:updating:403035325242540032> Beginning download...");

        try {
            update(updateURL, it -> context.edit("<a:updating:403035325242540032> " + it),
                    n -> context.edit("<:check:314349398811475968> Update complete!"));
        } catch (Exception e) {
            context.send("Couldn't begin download: " + e.getClass().getName() + ": " + e.getMessage());
            Log.exception("Failed to begin download from " + updateURL, e);
        }

        return null;
    }

    public static void update(String url, Consumer<String> messageUpdater, Consumer<Void> reloadedMessage) throws Exception {
        File output = new File(Constants.TEMP_FOLDER, "update_download");

        if (output.exists()) output.delete();
        if (!output.getParentFile().exists()) output.getParentFile().mkdirs();
        output.createNewFile();

        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", Constants.USER_AGENT);
        conn.setUseCaches(false);

        OutputStream os = new FileOutputStream(output);
        long begin = System.currentTimeMillis();
        AtomicLong lastSecond = new AtomicLong(0L);

        GamesROB.rpc.ifPresent(rpc -> {
            rpc.request(RPCManager.RequestType.BOT_UPDATED, new JsonPrimitive(url));
            Log.info("Sent bot update to other clusters.");
        });

        TransferUtility.download(conn, os, step -> {
            long downloaded = step.getDownloaded();
            long now = System.currentTimeMillis();
            lastSecond.set(downloaded);

            messageUpdater.accept(String.format(
                    "Downloading: %s/%s @%s/s (ETA %s)",
                    ShardClusterUtilities.formatBytes(downloaded), ShardClusterUtilities.formatBytes(step.getContentSize()),
                    ShardClusterUtilities.formatBytes((downloaded - lastSecond.get()) / TransferUtility.UPDATE_MESSAGE_PERIOD),
                    ShardClusterUtilities.formatPeriod(Utility.calculateETA(now - begin, downloaded, step.getContentSize()))
            ));
        }, success -> {
            messageUpdater.accept("Finished download, saving file...");

            try {
                File gamesrobJar = new File(GamesROBShardCluster.commandProcessorFilePath);
                if (gamesrobJar.exists()) gamesrobJar.delete();
                gamesrobJar.createNewFile();

                TransferUtility.download(new FileInputStream(output), new FileOutputStream(gamesrobJar),
                        output.length(), step -> {}, n -> {
                            output.delete();
                            messageUpdater.accept("Reloading...");
                            GamesROBShardCluster.reloadCommandProcessor();
                            reloadedMessage.accept(null);
                        }, error -> {
                            messageUpdater.accept("Failed to install update: " + error.getClass().getName() + ": " + error.getMessage());
                            Log.exception("Failed to install update from " + url, error);
                        });
            } catch (Exception e) {
                messageUpdater.accept("Failed to update: " + e.getClass().getName() + ": " + e.getMessage());
                Log.exception("Failed to update from " + url, e);
            }
        }, error -> {
            messageUpdater.accept("Failed to download: " + error.getClass().getName() + ": " + error.getMessage());
            Log.exception("Failed to download from " + url, error);
        });
    }

    public static void owners(CommandFramework f) {
        f.command("owners getowners viewowners", context -> "Owners:\n" + GamesROB.owners.stream().map(it ->
                GamesROB.getUserById(it).map(user -> user.getName() + "#" + user.getDiscriminator()).orElse("unknown"))
                    .collect(Collectors.joining("\n")), cmd -> {
            cmd.sub("add +", context -> {
                if (!GamesROB.owners.contains(context.getAuthor().getIdLong())) return Language.transl(context,
                        "genericMessages.ownersOnly");

                List<Long> owners = new ArrayList<>(GamesROB.owners);
                owners.add(context.nextUser().getIdLong());
                GamesROB.owners = Collections.unmodifiableList(owners);

                return "Added them to the owner list.";
            });
            cmd.sub("remove -", context -> {
                if (!GamesROB.owners.contains(context.getAuthor().getIdLong())) return Language.transl(context,
                        "genericMessages.ownersOnly");

                List<Long> owners = new ArrayList<>(GamesROB.owners);
                owners.remove(context.nextUser().getIdLong());
                GamesROB.owners = Collections.unmodifiableList(owners);

                return "Removed them from the owner list.";
            });
            cmd.sub("clear", context -> {
                if (!BootupProcedure.getOwners().contains(context.getAuthor().getIdLong())) return "Only hardcoded owners can use this.";

                GamesROB.owners = BootupProcedure.getOwners();
                return "Cleared owners.";
            });
        });
    }

    public static String compileLanguage(CommandContext context) {
        if (!GamesROB.owners.contains(context.getAuthor().getIdLong())) return Language.transl(context,
                "genericMessages.ownersOnly");

        String lang = context.next();
        Map<String, String> allKeys = new HashMap<>();
        List<String> translators = new ArrayList<>();
        Optional.ofNullable(Language.getLanguages().get(lang)).ifPresent(allKeys::putAll);

        GamesROB.database.ifPresent(db -> Log.wrapException("Getting translation suggestions", () -> {
            ResultSet set = db.select("translationsuggestions", "language = '" + lang + "'");
            Map<String, List<Pair<String, Integer>>> votesForEntries = new HashMap<>();

            while (set.next()) {
                String key = set.getString("key");

                if (!votesForEntries.containsKey(key)) votesForEntries.put(key, new ArrayList<>());
                votesForEntries.get(key).add(new Pair<>(set.getString("translation"), set.getInt("rating")));
                translators.add(set.getString("userid"));
            }

            votesForEntries.forEach((key, value) -> value.stream().min(Comparator.comparingInt(it ->
                    ((Pair<String, Integer>) it).getValue()).reversed()).ifPresent(it -> allKeys.put(key, it.getKey())));
        }));
        allKeys.put("languageProperties.translators", translators.stream().map(it -> GamesROB.getUserById(it)
            .map(user -> user.getName() + "#" + user.getDiscriminator()).orElse("not found")).collect(Collectors.joining(", ")));

        context.getChannel().sendFile(Constants.GSON.toJson(allKeys).getBytes(), lang + ".json").queue();
        return null;
    }

    public static String reload(CommandContext context) {
        if (!GamesROB.owners.contains(context.getAuthor().getIdLong())) return Language.transl(context,
                "genericMessages.ownersOnly");

        context.send("<a:typing:393848431413559296> Reloading Command Processor").then(message -> {
            GamesROBShardCluster.reloadCommandProcessor();
            message.editMessage("→ <:check:314349398811475968> Reloaded Command Processor").queue();
        });

        return null;
    }

    public static String restart(CommandContext context) {
        if (!GamesROB.owners.contains(context.getAuthor().getIdLong())) return Language.transl(context,
                "genericMessages.ownersOnly");

        context.send("Restarting the bot!").then(n -> System.exit(-1));

        return null;
    }
}
