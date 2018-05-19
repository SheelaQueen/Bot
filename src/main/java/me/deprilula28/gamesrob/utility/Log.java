package me.deprilula28.gamesrob.utility;

import me.deprilula28.gamesrob.GamesROB;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class Log {
    private static final SimpleDateFormat FULL_DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
    private static final SimpleDateFormat FILE_DATE_FORMAT = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("HH:mm:ss");
    private static List<String> pastLogs = new ArrayList<>();

    public static void trace(Object... print) {
        String log = Arrays.stream(print).map(Object::toString).collect(Collectors.joining(" "));
        if (GamesROB.debug) {
            logMessage("TRACE", System.out, new Object[] { log });
        } else addLog(log);
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
        if (GamesROB.debug) {
            StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
            from += String.format(" %s:%s (%s)", ste.getFileName(), ste.getLineNumber(), Thread.currentThread().getName());
        }

        String log = Arrays.stream(print).map(Object::toString).collect(Collectors.joining(" "));
        stream.println(String.format(
                "%s [%s] %s",
                DATE_FORMAT.format(Calendar.getInstance().getTime()), from, log
        ));
        addLog(log);
    }

    private static void addLog(String message) {

    }

    public static void exception(String occurence, Exception exception, Object... objects) {
        Date time = Calendar.getInstance().getTime();
        StringBuilder builder = new StringBuilder();

        builder.append("-== Uncaught Exception ").append(FULL_DATE_FORMAT.format(time)).append(" ==-\n")
                .append("Occurence: ").append(occurence).append("\n")
                .append(exception.getClass().getName()).append(": ").append(exception.getMessage()).append("\n");
        appendThrowable(exception, builder);
        String basics = builder.toString();

        builder.append("\n\n-== Extra Information ==-\n").append(Arrays.stream(objects).map(Object::toString)
                .collect(Collectors.joining("\n")));

        Runtime runtime = Runtime.getRuntime();
        builder.append("\n\n-== System Information ==-\n")
                .append("Java Installation: version ").append(System.getProperty("java.version"))
                .append(", vendor ").append(System.getProperty("java.vendor")).append("\n")
                .append("OS: ").append(System.getProperty("os.name")).append(" ").append(System.getProperty("os.version"))
                .append(" (arch ").append(System.getProperty("os.arch")).append(")\n")
                .append("RAM: ").append(Utility.getRAM()).append("\n")
                .append(runtime.availableProcessors()).append(" available processors");
        String ts = builder.toString();

        System.err.println(ts);
        System.out.println("Saving error information...");

        FileWriter writer = null;
        try {
            File file = new File(Constants.LOGS_FOLDER, FILE_DATE_FORMAT.format(time) + ".txt");
            if (file.exists()) file.delete();
            if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
            file.createNewFile();

            writer = new FileWriter(file);
            writer.write(ts);
            writer.close();
            System.out.println("Saved error to " + file.getAbsolutePath());
        } catch (Exception e) {
            if (writer != null) Utility.quietlyClose(writer);
            System.err.println("Failed to save error!");
            e.printStackTrace();
        }
    }

    private static void appendThrowable(Throwable error, StringBuilder builder) {
        for (StackTraceElement ste : error.getStackTrace()) {
            builder.append(String.format("  at %s\n", ste.toString()));
        }
        if (error.getCause() != null) {
            Throwable cause = error.getCause();
            builder.append("Caused by " + cause.getClass().getName() + ": " + cause.getMessage() + "\n");
            appendThrowable(cause, builder);
        }
    }

    @FunctionalInterface
    public static interface ExceptionWrapper {
        void invoke() throws Exception;
    }

    public static void wrapException(String status, ExceptionWrapper wrapper) {
        try {
            wrapper.invoke();
        } catch (Exception e) {
            exception(status, e);
        }
    }
}
