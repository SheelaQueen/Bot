package me.deprilula28.gamesrobshardcluster;

import me.deprilula28.gamesrobshardcluster.utilities.CommandProcessorClassLoader;
import me.deprilula28.gamesrobshardcluster.utilities.Constants;
import me.deprilula28.gamesrobshardcluster.utilities.Log;
import me.deprilula28.gamesrobshardcluster.utilities.ShardClusterUtilities;
import me.deprilula28.jdacmdframework.CommandFramework;
import net.dv8tion.jda.core.JDA;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class GamesROBShardCluster {
    public static final String VERSION = "1.0.0";
    public static String commandProcessorFilePath;
    public static List<JDA> shards = new ArrayList<>();
    public static CommandProcessor commandProcessor;
    public static CommandFramework framework;
    public static boolean debug = false;
    private static List<String> args;

    public static void main(String[] args) {
        Log.wrapException("Starting the bot", () -> {
            Log.info("\n" +
                    " ██████╗  █████╗ ███╗   ███╗███████╗███████╗██████╗  ██████╗ ██████╗ \n" +
                    "██╔════╝ ██╔══██╗████╗ ████║██╔════╝██╔════╝██╔══██╗██╔═══██╗██╔══██╗\n" +
                    "██║  ███╗███████║██╔████╔██║█████╗  ███████╗██████╔╝██║   ██║██████╔╝\n" +
                    "██║   ██║██╔══██║██║╚██╔╝██║██╔══╝  ╚════██║██╔══██╗██║   ██║██╔══██╗\n" +
                    "╚██████╔╝██║  ██║██║ ╚═╝ ██║███████╗███████║██║  ██║╚██████╔╝██████╔╝\n" +
                    " ╚═════╝ ╚═╝  ╚═╝╚═╝     ╚═╝╚══════╝╚══════╝╚═╝  ╚═╝ ╚═════╝ ╚═════╝ \n\n" +
                    "Developed by deprilula28\n2017--");
            Log.info("-= Starting Shard Cluster " + VERSION + " =-");
            BootupProcedure.bootup(args);
        });
    }

    public static void reloadCommandProcessor() {
        loadCommandProcessor(args);
    }

    public static void loadCommandProcessor(List<String> args) {
        Log.wrapException("Loading command processor", () -> {
            if (commandProcessor != null) {
                commandProcessor.close();
                System.gc();
            } else GamesROBShardCluster.args = args;

            File originFile = new File(commandProcessorFilePath);
            if (!originFile.exists()) throw new RuntimeException("Command processor file not found.");

            File copiedFile = new File(Constants.DATA_FOLDER, "commandprocessor-clone.jar");
            if (!copiedFile.getParentFile().exists()) copiedFile.getParentFile().mkdirs();
            if (copiedFile.exists()) copiedFile.delete();
            copiedFile.createNewFile();

            Log.info("Cloning command processor file");
            ShardClusterUtilities.copyFile(originFile, copiedFile);

            Log.info("Loading command processor class");
            CommandProcessorClassLoader classLoader = new CommandProcessorClassLoader(copiedFile,
                    GamesROBShardCluster.class.getClassLoader());
            Class<?> cpClass = Class.forName("me.deprilula28.gamesrob.GamesROB", true, classLoader);
            commandProcessor = (CommandProcessor) cpClass.newInstance();
            commandProcessor.registerCommands(args.toArray(new String[]{}), framework);
        });
    }
}
