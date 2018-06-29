package me.deprilula28.gamesrob.commands;

import jdk.nashorn.api.scripting.ClassFilter;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import lombok.AllArgsConstructor;
import lombok.Data;
import me.deprilula28.gamesrob.BootupProcedure;
import me.deprilula28.gamesrob.GamesROB;
import me.deprilula28.gamesrob.Language;
import me.deprilula28.gamesrob.data.UserProfile;
import me.deprilula28.gamesrob.utility.Constants;
import me.deprilula28.gamesrob.utility.Log;
import me.deprilula28.gamesrob.utility.TransferUtility;
import me.deprilula28.gamesrob.utility.Utility;
import me.deprilula28.jdacmdframework.Command;
import me.deprilula28.jdacmdframework.CommandContext;
import me.deprilula28.jdacmdframework.CommandFramework;
import net.dv8tion.jda.core.entities.User;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;
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
                Utility.extractPeriod(context.next()),
                String.join(" ", context.remaining()),
                context.getAuthor()
        );
        return "Announcement set. It will now show in `g*help`.";
    }

    public static String blacklist(CommandContext context) {
        if (!GamesROB.owners.contains(context.getAuthor().getIdLong())) return Language.transl(context,
                "genericMessages.ownersOnly");

        User blacklisting = context.nextUser();
        String reason = String.join(" ", context.remaining());
        GamesROB.database.ifPresent(db -> {
            db.insert("blacklist", Arrays.asList("userid", "botownerid", "reason", "time"),
                    set -> Log.wrapException("Adding blacklist entry", () -> {
                set.setString(1, blacklisting.getId());
                set.setString(2, context.getAuthor().getId());
                set.setString(3, reason);
                set.setLong(4, System.currentTimeMillis());
            }));
        });
        return "k";
    }

    public static String sql(CommandContext context) {
        if (!GamesROB.owners.contains(context.getAuthor().getIdLong())) return Language.transl(context,
                "genericMessages.ownersOnly");

        GamesROB.database.ifPresent(db -> {
            context.send("<a:typing:393848431413559296> Querying...");
            try {
                ResultSet set = db.sqlQuery(String.join(" ", context.remaining()));
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
        scriptEngine.put("shards", GamesROB.shards);

        context.send("<a:typing:393848431413559296> Evaluating...");
        Object response;
        long begin = System.nanoTime();
        boolean success;
        try {
            response = scriptEngine.eval(String.join(" ", context.remaining()));
            success = true;
        } catch (Exception e) {
            e.printStackTrace();
            response = e.getClass().getName() + ": " + e.getMessage();
            success = false;
        }
        double time = (double) (System.nanoTime() - begin) / 1000000000.0;

        String text = response == null ? "null" : (response instanceof String ? (String) response : response.toString());
        text = text.replaceAll(context.getJda().getToken(), "no u");
        String message = (success
                ? "<:check:314349398811475968> Output took " + Utility.formatPeriod(time)
                : "<:xmark:314349398824058880> Failed in " + Utility.formatPeriod(time)) + ":\n" +
                "```js\n" + (text.length() > 1500 ? text.substring(0, 1500) + "..." : text) + "\n```";

        context.edit(message);

        return null;
    }

    public static Object update(CommandContext context) {
        if (!GamesROB.owners.contains(context.getAuthor().getIdLong())) return Language.transl(context,
                "genericMessages.ownersOnly");

        String updateURL = context.next();
        context.send("<a:updating:403035325242540032> Beginning download...");

        try {
            File output = new File(Constants.TEMP_FOLDER, "update_download");

            if (output.exists()) output.delete();
            if (!output.getParentFile().exists()) output.getParentFile().mkdirs();
            output.createNewFile();

            URLConnection conn = new URL(updateURL).openConnection();
            OutputStream os = new FileOutputStream(output);
            long begin = System.currentTimeMillis();
            AtomicLong lastSecond = new AtomicLong(0L);

            TransferUtility.download(conn, os, step -> {
                long downloaded = step.getDownloaded();
                long now = System.currentTimeMillis();
                lastSecond.set(downloaded);

                context.edit(String.format(
                        "<a:updating:403035325242540032> Downloading: %s/%s @%s/s (ETA %s)",
                        Utility.formatBytes(downloaded), Utility.formatBytes(step.getContentSize()),
                        Utility.formatBytes((downloaded - lastSecond.get()) / TransferUtility.UPDATE_MESSAGE_PERIOD),
                        Utility.formatPeriod(Utility.calculateETA(now - begin, downloaded, step.getContentSize()))
                ));
            }, success -> {
                context.edit("<a:updating:403035325242540032> Finished download, saving file...");

                try {
                    File gamesrobJar = new File("gamesrob.jar");
                    if (gamesrobJar.exists()) gamesrobJar.delete();
                    gamesrobJar.createNewFile();

                    TransferUtility.download(new FileInputStream(output), new FileOutputStream(gamesrobJar),
                            output.length(), step -> {}, n -> {
                                output.delete();
                                context.edit("<a:updating:403035325242540032> Restarting...").then(m -> System.exit(-1));
                            }, error -> {
                                context.send("Failed to install update: " + error.getClass().getName() + ": " + error.getMessage());
                                Log.exception("Failed to install update from " + updateURL, error);
                            });
                } catch (Exception e) {
                    context.send("Failed to update: " + e.getClass().getName() + ": " + e.getMessage());
                    Log.exception("Failed to update", e);
                }
            }, error -> {
                context.send("Failed to download: " + error.getClass().getName() + ": " + error.getMessage());
                Log.exception("Failed to download from " + updateURL, error);
            });
        } catch (Exception e) {
            context.send("Couldn't begin download: " + e.getClass().getName() + ": " + e.getMessage());
            Log.exception("Failed to begin download from " + updateURL, e);
        }

        return null;
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
                if (!GamesROB.owners.contains(context.getAuthor().getIdLong())) return Language.transl(context,
                        "genericMessages.ownersOnly");

                GamesROB.owners = BootupProcedure.getOwners();
                return "Cleared owners.";
            });
        });
    }
}
