package me.deprilula28.gamesrobshardcluster.utilities;

import me.deprilula28.jdacmdframework.exceptions.InvalidCommandSyntaxException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ShardClusterUtilities {
    private static final double[] TIME_MEASURE_UNITS = {
            24 * 60 * 60 * 1000, 60 * 60 * 1000, 60 * 1000, 1000, 1, 1.0 / 1000.0, 1.0 / 1000000.0 // days, hours, minutes, seconds, milliseconds, microseconds
    };
    private static final String[] TIME_UNIT_NAMES = {
            "d", "h", "m", "s", "ms", "μs", "ns"
    };

    private static final int[] BYTE_MEASURE_UNITS = {
            1024 * 1024 * 1024, 1024 * 1024, 1024, 1
    };
    private static final String[] BYTE_UNIT_NAMES = {
            "GB", "MB", "KB", "B"
    };

    private static final SimpleDateFormat WEEK_DATE_FORMAT = new SimpleDateFormat("EEE, HH:mm");
    private static final SimpleDateFormat REGULAR_DATE_FORMAT = new SimpleDateFormat("EEE, d/M/yyyy hh:mm a");


    public static String formatPeriod(long timePeriod) {
        return formatPeriod((double) timePeriod);
    }

    public static long extractPeriod(String text) {
        if (text.matches("[0-9]+[dhms]")) {
            int n = Integer.valueOf(text.substring(0, text.length() - 1) + "");
            int period = Arrays.asList(TIME_UNIT_NAMES).indexOf(text.charAt(text.length() - 1) + "");
            return (long) TIME_MEASURE_UNITS[period] * n;
        } else throw new InvalidCommandSyntaxException();
    }

    public static String formatTimeRegularFormat(long time) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(time);

        return REGULAR_DATE_FORMAT.format(calendar.getTime());
    }

    public static String formatTime(long time) {
        long now = System.currentTimeMillis();
        boolean future = time > now;

        if (future) {
            return "in" + formatPeriod(time - now);
        }

        long diff = now - time;
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(time);

        if (diff < TimeUnit.DAYS.toMillis(2)) {
            return formatPeriod(diff) + " ago";
        } else if (diff < TimeUnit.DAYS.toMillis(7)) {
            return WEEK_DATE_FORMAT.format(calendar.getTime());
        } else return REGULAR_DATE_FORMAT.format(calendar.getTime());
    }

    public static List<Optional<String>> matchValues(List<String> values, String... names) {
        return Arrays.stream(names).map(cur -> values.stream()
                .filter(it -> it.startsWith(cur + "="))
                .findAny().map(it -> it.substring(cur.length() + 1)))
                .collect(Collectors.toList());
    }

    public static String formatBytes(long bytes) {
        for (int index = 0; index < BYTE_MEASURE_UNITS.length; index ++) {
            int n = BYTE_MEASURE_UNITS[index];
            if (bytes >= n) {
                String name = BYTE_UNIT_NAMES[index];
                return BigDecimal.valueOf((double) bytes / (double) n).setScale(2, BigDecimal.ROUND_HALF_UP)
                        .toString() + name;
            }
        }

        return bytes + "B";
    }

    public static String formatPeriod(double timePeriod) {
        int minIndex = TIME_MEASURE_UNITS.length - 1;

        for (int index = 0; index < TIME_MEASURE_UNITS.length; index++) {
            double n = TIME_MEASURE_UNITS[index];
            if (timePeriod >= n) {
                minIndex = index;
                break;
            }
        }

        StringBuilder builder = new StringBuilder();
        for (int i = minIndex; i < minIndex + 2 && i < TIME_MEASURE_UNITS.length; i ++) {
            double number = timePeriod;

            if (i - 1 >= 0) number %= TIME_MEASURE_UNITS[i - 1];
            number /= TIME_MEASURE_UNITS[i];
            if (number == 0.0) continue;

            builder.append(new DecimalFormat(",###").format(Math.floor(number))).append(TIME_UNIT_NAMES[i]);
        }

        return builder.length() > 0 ? builder.toString() : "0s";
    }

    public static String getRAM() {
        Runtime rt = Runtime.getRuntime();
        long free = rt.freeMemory();
        long allocated = rt.totalMemory();

        return String.format("%s/%s",
                formatBytes(allocated - free),
                formatBytes(allocated));
    }

    public static long getRawRAM() {
        Runtime rt = Runtime.getRuntime();
        long free = rt.freeMemory();
        long allocated = rt.totalMemory();

        return allocated - free;
    }

    public static void copyFile(File inputFile, File outputFile) throws IOException {
        FileInputStream input = new FileInputStream(inputFile);
        FileOutputStream output = new FileOutputStream(outputFile);

        int read;
        byte[] buffer = new byte[512];
        while ((read = input.read(buffer)) > 0) output.write(buffer, 0, read);

        input.close();
        output.close();
    }
}
