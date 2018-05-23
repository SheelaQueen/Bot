package me.deprilula28.gamesrob.commands;

import me.deprilula28.gamesrob.GamesROB;
import me.deprilula28.gamesrob.Language;
import me.deprilula28.gamesrob.utility.Constants;
import me.deprilula28.gamesrob.utility.Log;
import me.deprilula28.gamesrob.utility.TransferUtility;
import me.deprilula28.gamesrob.utility.Utility;
import me.deprilula28.jdacmdframework.CommandContext;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.atomic.AtomicLong;

public class UpdateCommand {
    public static Object update(CommandContext context) {
        if (!GamesROB.owners.contains(context.getAuthor().getIdLong())) return Language.transl(context,
                "genericMessages.ownersOnly");
        String updateURL = context.next();
        context.send("Beginning download...");

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
                        "Downloading: %s/%s @%s/s (ETA %s)",
                        Utility.formatBytes(downloaded), Utility.formatBytes(step.getContentSize()),
                        Utility.formatBytes((downloaded - lastSecond.get()) / TransferUtility.UPDATE_MESSAGE_PERIOD),
                        Utility.formatPeriod(Utility.calculateETA(now - begin, downloaded, step.getContentSize()))
                ));
            }, success -> {
                context.edit("Finished download, updating...");

                try {
                    File gamesrobJar = new File("gamesrob.jar");
                    if (gamesrobJar.exists()) gamesrobJar.delete();
                    gamesrobJar.createNewFile();

                    TransferUtility.download(new FileInputStream(output), new FileOutputStream(gamesrobJar),
                            output.length(), step -> {}, n -> {
                                output.delete();
                                context.edit("Finished update!").then(m -> System.exit(-1));
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
}
