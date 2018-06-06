package me.deprilula28.gamesrob.utility;

import me.deprilula28.gamesrob.GamesROB;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class Log {
    private static final SimpleDateFormat FULL_DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
    private static final SimpleDateFormat FILE_DATE_FORMAT = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("HH:mm:ss");
    private static FileWriter writer = null;

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
        StackTraceElement[] st = null;
            if (GamesROB.debug) {
                if (writer == null) try {
                    File file = new File(Constants.LOGS_FOLDER, FILE_DATE_FORMAT.format(Calendar.getInstance().getTime()) + ".txt");
                    if (file.exists()) writer = new FileWriter(file);
                    else {
                        if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
                        file.createNewFile();
                        writer = new FileWriter(file);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                st = Thread.currentThread().getStackTrace();
                StackTraceElement ste = st[3];
                from += String.format(" %s:%s (%s)", ste.getFileName(), ste.getLineNumber(), Thread.currentThread().getName());
            }

        String log = Arrays.stream(print).map(Object::toString).collect(Collectors.joining(" "));
        stream.println(String.format(
                "%s [%s] %s",
                DATE_FORMAT.format(Calendar.getInstance().getTime()), from, log
        ));
        if (GamesROB.debug) {
            StackTraceElement[] stackTrace = st;
            try {
                writer.write(log + "\n");
                for (StackTraceElement ste : stackTrace) {
                    writer.write("  from " + ste.toString() + "\n");
                }
                writer.write("\n");
            }catch (Exception e) {
                e.printStackTrace();
            }
        }
        addLog(log);
    }

    public static void closeStream() {
        if (GamesROB.debug && writer != null) wrapException("Closing log output stream", () -> writer.close());
    }

    private static void addLog(String message) {

    }

    public static void exception(String occurence, Exception exception, Object... objects) {
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
        builder.append("\n\n-== Extra Information ==-\n").append(Arrays.stream(objects).map(Object::toString)
                .collect(Collectors.joining("\n")));

        Runtime runtime = Runtime.getRuntime();
        StringBuilder extraInfo = new StringBuilder().append("\n\n-== System Information ==-\n")
                .append("Java Installation: version ").append(System.getProperty("java.version"))
                .append(", vendor ").append(System.getProperty("java.vendor")).append("\n")
                .append("OS: ").append(System.getProperty("os.name")).append(" ").append(System.getProperty("os.version"))
                .append(" (arch ").append(System.getProperty("os.arch")).append(")\n")
                .append("RAM: ").append(Utility.getRAM()).append("\n")
                .append(runtime.availableProcessors()).append(" available processors");
        String additiveInfo = extraInfo.toString();
        builder.append(extraInfo.toString());
        String ts = builder.toString();

        System.err.println(ts);
        System.out.println("Saving error information...");

        if (!GamesROB.debug) Trello.addErrorDump(exceptionName, finalBasics, additiveInfo);
        /*
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
        */
    }

    private static void appendThrowable(Throwable error, StringBuilder builder, boolean githubLinks) {
        for (StackTraceElement ste : error.getStackTrace()) {
            if (githubLinks) {
                String githubLink = ste.getClassName() + "." + ste.getMethodName() + (ste.isNativeMethod()
                        ? "(Native Method)" : (ste.getFileName() != null && ste.getLineNumber() >= 0
                        ? "(" + (ste.getClassName().startsWith("me.deprilula28.gamesrob.")
                        ? "[" + ste.getFileName() + ":" + ste.getLineNumber() + "](" +
                        "https://github.com/deprilula28/GamesROB/blob/master/src/main/java/"
                        + ste.getClassName().replaceAll("\\.", "/") + ".java#L" + ste.getLineNumber() + "))"
                        : ste.getFileName() + ":" + ste.getLineNumber() + ")")
                        : (ste.getFileName() != null ?  "("+ ste.getFileName() +")" : "(Unknown Source)")));
                builder.append(String.format("  at %s\n", githubLink));
            }
            builder.append(String.format("  at %s\n", ste.toString()));
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

    public static void wrapException(String status, ExceptionWrapper wrapper) {
        try {
            wrapper.invoke();
        } catch (Exception e) {
            exception(status, e);
        }
    }
}
