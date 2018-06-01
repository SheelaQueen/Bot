package me.deprilula28.gamesrob.utility;

import me.deprilula28.gamesrob.Language;
import me.deprilula28.gamesrob.commands.ProfileCommands;
import me.deprilula28.gamesrob.data.Statistics;
import me.deprilula28.gamesrob.data.UserProfile;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Channel;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.User;

import java.awt.*;
import java.io.Closeable;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.temporal.TemporalAdjuster;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Utility {
    private static final int[] TIME_MEASURE_UNITS = {
            24 * 60 * 60 * 1000, 60 * 60 * 1000, 60 * 1000, 1000, 1 // days, hours, minutes, seconds, millis
    };
    private static final String[] TIME_UNIT_NAMES = {
            "d", "h", "m", "s", "ms"
    };

    private static final int[] BYTE_MEASURE_UNITS = {
            1000000000, 1000000, 1000, 1
    };
    private static final String[] BYTE_UNIT_NAMES = {
            "GB", "MB", "KB", "B"
    };

    public static Color randomBotColor() {
        return Constants.BOT_COLORS[ThreadLocalRandom.current().nextInt(Constants.BOT_COLORS.length)];
    }

    public static class Promise<R> {
        private List<Consumer<R>> consumers = new ArrayList<>();
        private List<Thread> awaiting = new ArrayList<>();
        private Optional<R> result = Optional.empty();

        public static <R> Promise<R> result(R resulting) {
            Promise<R> promise = new Promise<>();
            promise.done(resulting);

            return promise;
        }

        public void done(R result) {
            this.result = Optional.of(result);
            consumers.forEach(cur -> cur.accept(result));
            consumers.clear();
            awaiting.forEach(Thread::interrupt);
            awaiting.clear();
        }

        public void then(Consumer<R> consumer) {
            if (result.isPresent()) consumer.accept(result.get());
            else consumers.add(consumer);
        }

        public <V> Promise<V> map(Function<R, V> mapper) {
            Promise<V> promiseSecond = new Promise<>();
            then(it -> promiseSecond.done(mapper.apply(it)));

            return promiseSecond;
        }
    }

    private static final SimpleDateFormat WEEK_DATE_FORMAT = new SimpleDateFormat("EEE, HH:mm");
    private static final SimpleDateFormat REGULAR_DATE_FORMAT = new SimpleDateFormat("EEE, d/M/yyyy hh:mm a");
    
    public static String addNumberDelimitors(long number) {
        return new DecimalFormat(",###").format(number);
    }

    public static String addNumberDelimitors(int number) {
        return new DecimalFormat(",###").format(number);
    }

    public static void populateItems(List<Optional<User>> players, String[] items,
                                     Map<Optional<User>, String> playerItems, Map<String, Optional<User>> itemPlayers) {
        for (int i = 0; i < players.size(); i++) {
            Optional<User> user = players.get(i);

            String emote = user.isPresent()
                    ? Optional.ofNullable(UserProfile.get(user.get()).getEmote()).filter(it -> !itemPlayers.containsKey(it)
                    && ProfileCommands.validateEmote(user.get().getJDA(), it)).orElse(items[i])
                    : items[i];
            playerItems.put(user, emote);
            itemPlayers.put(emote, user);
        }
    }

    public static long getRamUsage(Object object) {
        return 0L;
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
    public static <T> T random(List<T> in) {
        return in.get(ThreadLocalRandom.current().nextInt(in.size()));
    }

    public static <T> T random(T[] in) {
        return in[ThreadLocalRandom.current().nextInt(in.length)];
    }

    public static String formatPeriod(long timePeriod) {
        int minIndex = TIME_MEASURE_UNITS.length - 1;

        for (int index = 0; index < TIME_MEASURE_UNITS.length; index++) {
            int n = TIME_MEASURE_UNITS[index];
            if (timePeriod >= n) {
                minIndex = index;
                break;
            }
        }

        StringBuilder builder = new StringBuilder();
        for (int i = minIndex; i < minIndex + 2 && i < TIME_MEASURE_UNITS.length; i ++) {
            long number = timePeriod;

            if (i - 1 >= 0) number %= TIME_MEASURE_UNITS[i - 1];
            number /= TIME_MEASURE_UNITS[i];

            builder.append(number).append(TIME_UNIT_NAMES[i]);
        }

        return builder.toString();
    }

    public static void quietlyClose(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException e) {}
    }

    public static String readResource(String path) {
        Scanner scann = new Scanner(Language.class.getResourceAsStream(path));
        StringBuilder builder = new StringBuilder();

        while (scann.hasNextLine()) {
            if (builder.length() > 0) builder.append("\n");
            builder.append(scann.nextLine());
        }

        return builder.toString();
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

    public static long calculateETA(long ellapsed, long downloaded, long size) {
        return (ellapsed * size / downloaded) - ellapsed;
    }

    public static String getNumberEmote(int i) {
        return "" + (char) (i + 49) + '⃣';
    }

    public static String getLetterEmote(int i) {
        return "" + '\ud83c' + (char) ('\udde6' + i);
    }

    public static int inputLetter(String from) {
        int charv = (int) from.charAt(0);
        int lower = charv - (int) 'a';
        int upper = charv - (int) 'A';

        return lower < 0 ? upper : lower;
    }

    public static String formatNth(String language, int i) {
        int mod = Math.max(Math.min(i % 10, 4), 1);
        return Language.transl(language, "genericMessages.nth" + mod);
    }

    public static List<Optional<String>> matchValues(List<String> values, String... names) {
        return Arrays.stream(names).map(cur -> values.stream()
                .filter(it -> it.startsWith(cur + "="))
                .findAny().map(it -> it.substring(cur.length() + 1)))
                .collect(Collectors.toList());
    }

    public static String generateTable(List<String> titles, int rows, List<List<String>> columns) {
        List<Integer> itemLengths = titles.stream().map(it -> Math.max(it.length(),
                columns.get(titles.indexOf(it)).stream()
                        .map(String::length)
                        .max(Comparator.comparingInt(n -> n))
                        .orElse(0)))
                .collect(Collectors.toList());

        StringBuilder builder = new StringBuilder();

        // Titles
        for (int i = 0; i < titles.size(); i++) {
            String title = titles.get(i);
            int desired = itemLengths.get(i);

            if (builder.length() > 0) builder.append(" | ");
            appendTitle(title, builder, desired);
        }
        builder.append("\n");

        // Columns
        StringBuilder line = new StringBuilder();
        for (int row = 0; row < rows; row ++) {
            for (int i = 0; i < titles.size(); i++) {
                String title = columns.get(i).get(row);
                int desired = itemLengths.get(i);

                if (line.length() > 0) line.append(" | ");
                appendTitle(title, line, desired);
            }

            line.append("\n");
            builder.append(line.toString());
            line = new StringBuilder();
        }

        return builder.toString();
    }

    private static void appendTitle(String text, StringBuilder builder, int desired) {
        int addOffset = desired - text.length();
        int leftOffset = addOffset / 2;

        for (int diff = 0; diff < leftOffset; diff ++) {
            builder.append(" ");
        }
        builder.append(text);
        for (int diff = 0; diff < addOffset - leftOffset; diff ++) {
            builder.append(" ");
        }
    }

    public static String getRAM() {
        Runtime rt = Runtime.getRuntime();
        long free = rt.freeMemory();
        long allocated = rt.totalMemory();

        return String.format("%s/%s",
                formatBytes(allocated - free),
                formatBytes(allocated));
    }

    public static boolean hasPermission(Channel channel, Member member, Permission permission) {
        return (member.hasPermission(permission) &&
                channel.getRolePermissionOverrides().stream().noneMatch(it -> member.getRoles().contains(it.getRole())
                        && it.getDenied().contains(permission)) &&
                channel.getMemberPermissionOverrides().stream().noneMatch(it -> it.getMember().equals(member)
                        && it.getDenied().contains(permission)))
            || channel.getRolePermissionOverrides().stream().anyMatch(it -> member.getRoles().contains(it.getRole())
                    && it.getAllowed().contains(permission)) || channel.getMemberPermissionOverrides().stream()
                .anyMatch(it -> it.getMember().equals(member) && it.getAllowed().contains(permission));
    }

    public static long predictNextUpdate() {
        LocalDateTime time = Instant.ofEpochMilli(Statistics.get().getLastUpdateLogSentTime())
                .atZone(ZoneId.systemDefault()).toLocalDateTime();
        return time.with(TemporalAdjusters.next(DayOfWeek.FRIDAY)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
