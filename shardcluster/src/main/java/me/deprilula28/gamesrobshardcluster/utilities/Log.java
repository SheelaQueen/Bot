package me.deprilula28.gamesrobshardcluster.utilities;

import me.deprilula28.gamesrobshardcluster.GamesROBShardCluster;

import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Optional;
import java.util.stream.Collectors;

public class Log {
    private static final SimpleDateFormat FULL_DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("HH:mm:ss");

    public static void trace(Object... print) {
        String log = Arrays.stream(print).map(Object::toString).collect(Collectors.joining(" "));
        if (GamesROBShardCluster.debug) logMessage("TRACE", System.out, new Object[] { log });
    }

    public static void warn(Object... print) {
        logMessage("WARN", System.err, print);
    }

    public static void fatal(Object... print) {
        logMessage("FATAL", System.err, print);
    }

    public static void info(Object... print) {
        logMessage("INFO", System.out, print);
    }

    private static void logMessage(String from, PrintStream stream, Object[] print) {
        String log = Arrays.stream(print).map(Object::toString).collect(Collectors.joining(" "));
        stream.println(String.format(
                "%s [%s] %s",
                DATE_FORMAT.format(Calendar.getInstance().getTime()), from, log
        ));
    }

    public static Optional<String> exception(String occurence, Exception exception, Object... objects) {
        Date time = Calendar.getInstance().getTime();
        StringBuilder builder = new StringBuilder();

        builder.append("-== Uncaught Exception ").append(FULL_DATE_FORMAT.format(time)).append(" ==-\n");

        StringBuilder basics = new StringBuilder();
        basics.append("Occurence: ").append(occurence).append("\n")
                .append(exception.getClass().getName()).append(": ").append(exception.getMessage()).append("\n");
        String exceptionName = basics.toString();
        appendThrowable(exception, basics, true);
        String finalBasics = basics.toString();

        builder.append(exceptionName);
        appendThrowable(exception, builder, false);

        /*
        builder.append("\n\n-== Extra Information ==-\n").append(Arrays.stream(objects).map(Object::toString)
                .collect(Collectors.joining("\n")));
                */

        Runtime runtime = Runtime.getRuntime();
        StringBuilder extraInfo = new StringBuilder().append("\n\n-== System Information ==-\n")
                .append("Java Installation: version ").append(System.getProperty("java.version"))
                .append(", vendor ").append(System.getProperty("java.vendor")).append("\n")
                .append("OS: ").append(System.getProperty("os.name")).append(" ").append(System.getProperty("os.version"))
                .append(" (arch ").append(System.getProperty("os.arch")).append(")\n")
                .append("RAM: ").append(ShardClusterUtilities.getRAM()).append("\n")
                .append(runtime.availableProcessors()).append(" available processors");
        String additiveInfo = extraInfo.toString();
        builder.append(extraInfo.toString());
        String ts = builder.toString();

        System.err.println(ts);
        System.out.println("Saving error information...");

        return Trello.addErrorDump(exceptionName, finalBasics, additiveInfo);
    }

    private static void appendThrowable(Throwable error, StringBuilder builder, boolean githubLinks) {
        for (StackTraceElement ste : error.getStackTrace()) {
            if (githubLinks) {
                builder.append("  at ").append(ste.getClassName()).append(".").append(ste.getMethodName());

                if (ste.isNativeMethod()) {
                    builder.append("(Native Method)");
                } else if (ste.getFileName() != null && ste.getLineNumber() >= 0) {
                    if (ste.getClassName().startsWith("me.deprilula28.")) {
                        builder.append("([").append(ste.getFileName()).append(":").append(ste.getLineNumber())
                                .append("](https://github.com/GamesROB/Bot/blob/master/")
                                .append(ste.getClassName().startsWith("me.deprilula28.gamesrobshardcluster")
                                        ? "shardcluster" : "commandprocessor").append("/src/main/java/")
                                .append(ste.getClassName().replaceAll("\\.", "/")).append(".java#L")
                                .append(ste.getLineNumber()).append("))");
                    } else builder.append(ste.getFileName()).append(":").append(ste.getLineNumber()).append(")");
                }
            } else builder.append(String.format("  at %s\n", ste.toString()));
        }
        if (error.getCause() != null) {
            Throwable cause = error.getCause();
            builder.append("Caused by " + cause.getClass().getName() + ": " + cause.getMessage() + "\n");
            appendThrowable(cause, builder, githubLinks);
        }
    }

    @FunctionalInterface
    public static interface ExceptionWrapper {
        void invoke() throws Exception;
    }

    public static void wrapException(String status, ExceptionWrapper wrapper, Object... objects) {
        try {
            wrapper.invoke();
        } catch (Exception e) {
            exception(status, e, (Object[]) objects);
        }
    }
}
