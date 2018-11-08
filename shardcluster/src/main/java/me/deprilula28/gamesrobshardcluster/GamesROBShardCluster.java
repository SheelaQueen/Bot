package me.deprilula28.gamesrobshardcluster;

import me.deprilula28.gamesrobshardcluster.utilities.CommandProcessorClassLoader;
import me.deprilula28.gamesrobshardcluster.utilities.Log;
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

    public static void loadCommandProcessor(List<String> args) throws Exception {
        if (commandProcessor != null) {
            commandProcessor.close();
            System.gc();
        }

        File originFile = new File(commandProcessorFilePath);
        if (!originFile.exists()) throw new RuntimeException("Command processor file not found.");

        CommandProcessorClassLoader classLoader = new CommandProcessorClassLoader(originFile,
                GamesROBShardCluster.class.getClassLoader());
        Class<?> cpClass = Class.forName("me.deprilula28.gamesrob.GamesROB", true, classLoader);
        commandProcessor = (CommandProcessor) cpClass.newInstance();
        commandProcessor.registerCommands(args.toArray(new String[]{}), framework);
    }
}
