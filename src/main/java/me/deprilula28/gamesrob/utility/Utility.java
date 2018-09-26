package me.deprilula28.gamesrob.utility;

import me.deprilula28.gamesrob.Language;
import me.deprilula28.gamesrob.baseFramework.Player;
import me.deprilula28.gamesrob.commands.ProfileCommands;
import me.deprilula28.gamesrob.data.Statistics;
import me.deprilula28.gamesrob.data.UserProfile;
import me.deprilula28.jdacmdframework.Command;
import me.deprilula28.jdacmdframework.CommandContext;
import me.deprilula28.jdacmdframework.exceptions.CommandArgsException;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.*;

import javax.xml.ws.Provider;
import java.awt.*;
import java.awt.image.BufferedImage;
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
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Utility {
    private static final double[] TIME_MEASURE_UNITS = {
            24 * 60 * 60 * 1000, 60 * 60 * 1000, 60 * 1000, 1000, 1, 1.0 / 1000000.0 // days, hours, minutes, seconds, milliseconds, microseconds
    };
    private static final String[] TIME_UNIT_NAMES = {
            "d", "h", "m", "s", "ms", "μs"
    };

    private static final int[] BYTE_MEASURE_UNITS = {
            1000000000, 1000000, 1000, 1
    };
    private static final String[] BYTE_UNIT_NAMES = {
            "GB", "MB", "KB", "B"
    };

    public static Color getEmbedColor(Guild guild) {
        Color color = guild.getMember(guild.getJDA().getSelfUser()).getColor();
        return color == null || color.equals(Color.white) ?
                Constants.BOT_COLORS[ThreadLocalRandom.current().nextInt(Constants.BOT_COLORS.length)] : color;
    }

    public static class Promise<R> {
        private List<Consumer<R>> consumers = new ArrayList<>();
        private List<Thread> awaiting = new ArrayList<>();
        private Optional<R> result = Optional.empty();

        @FunctionalInterface
        public static interface PromiseProvider<R> {
            R invoke();
        }

        public R await() {
            awaiting.add(Thread.currentThread());
            try {
                Thread.sleep(Long.MAX_VALUE);
            } catch (InterruptedException ex){
                return result.get();
            }
            Log.trace("Wait it really took that long?");
            return null;
        }

        public static <R> Promise<R> provider(PromiseProvider<R> provider) {
            Promise<R> promise = new Promise<>();
            Thread thread = new Thread(() -> promise.done(provider.invoke()));
            thread.setName("Promise thread");
            thread.setDaemon(false);
            thread.start();

            return promise;
        }

        public static <R> Promise<R> result(R resulting) {
            Promise<R> promise = new Promise<>();
            promise.done(resulting);

            return promise;
        }

        public Promise<Void> both(Promise<R> promise) {
            Promise<Void> pr = new Promise<>();
            then(a -> promise.then(b -> pr.done(null)));

            return pr;
        }

        public void done(R result) {
            this.result = Optional.ofNullable(result);
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
        return addNumberDelimitors((long) number);
    }

    public static void populateItems(List<Player> players, String[] items,
                                     Map<Player, String> playerItems, Map<String, Player> itemPlayers) {
        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);

            String emote = player.getUser().isPresent()
                    ? Optional.ofNullable(UserProfile.get(player.getUser().get()).getEmote()).filter(it -> !itemPlayers.containsKey(it)
                    && ProfileCommands.validateEmote(player.getUser().get().getJDA(), it)).orElse(items[i])
                    : items[i];
            playerItems.put(player, emote);
            itemPlayers.put(emote, player);
        }
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

        return builder.toString();
    }

    public static String formatPeriod(long timePeriod) {
        return formatPeriod((double) timePeriod);
    }

    public static long extractPeriod(String text) {
        if (text.matches("[0-9]+[dhms]")) {
            int n = Integer.valueOf(text.substring(0, text.length() - 1) + "");
            int period = Arrays.asList(TIME_UNIT_NAMES).indexOf(text.charAt(text.length() - 1) + "");
            if (period < -1) throw new CommandArgsException("Time period isn't existant!");
            return (long) TIME_MEASURE_UNITS[period] * n;
        } else throw new CommandArgsException("Couldn't extract period!");
    }

    public static void quietlyClose(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException e) {}
    }

    public static String readResource(String path) {
        return Cache.get("path_" + path, n -> {
            Scanner scann = new Scanner(Language.class.getResourceAsStream(path));
            StringBuilder builder = new StringBuilder();

            while (scann.hasNextLine()) {
                if (builder.length() > 0) builder.append("\n");
                builder.append(scann.nextLine());
            }

            return builder.toString();
        });
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
        int mod = Math.max(Math.min(i % 10, 4), 0);
        if (mod == 0) mod = 4;
        return Language.transl(language, "genericMessages.nth" + mod);
    }

    public static List<Optional<String>> matchValues(List<String> values, String... names) {
        return Arrays.stream(names).map(cur -> values.stream()
                .filter(it -> it.startsWith(cur + "="))
                .findAny().map(it -> it.substring(cur.length() + 1)))
                .collect(Collectors.toList());
    }

    public static String generateTable(List<String> titles, int columns, List<List<String>> rows) {
        List<Integer> itemLengths = titles.stream().map(it -> Math.max(it.length(),
                rows.get(titles.indexOf(it)).stream()
                        .map(String::length)
                        .max(Comparator.comparingInt(n -> n))
                        .orElse(0)))
                .collect(Collectors.toList());

        StringBuilder builder = new StringBuilder();

        // Titles
        for (int i = 0; i < titles.size(); i ++) {
            String title = titles.get(i);
            int desired = itemLengths.get(i);

            if (builder.length() > 0) builder.append(" | ");
            appendTitle(title, builder, desired);
        }
        builder.append("\n");

        // Division
        for (int i = 0; i < titles.size(); i ++) {
            if (i > 0) builder.append("-+-");
            int length = itemLengths.get(i);
            while (length > 0) {
                builder.append("-");
                length --;
            }
        }
        builder.append("\n");

        // Columns
        StringBuilder line = new StringBuilder();
        for (int column = 0; column < columns; column ++) {
            for (int row = 0; row < titles.size(); row ++) {
                List<String> rowItems = rows.get(row);
                String title = rowItems.size() > column ? rowItems.get(column) : "null";
                int desired = itemLengths.get(row);

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

    private static long nextUpdatePredictment = -1;

    public static boolean isWeekendMultiplier() {
        int day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        return day == Calendar.SUNDAY || day >= Calendar.FRIDAY;
    }

    public static long predictNextUpdate() {
        if (nextUpdatePredictment != -1) return nextUpdatePredictment;

        LocalDateTime time = Instant.ofEpochMilli(Statistics.get().getLastUpdateLogSentTime())
                .atZone(ZoneId.systemDefault()).toLocalDateTime();
        long prediction = time.with(TemporalAdjusters.next(DayOfWeek.FRIDAY)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        nextUpdatePredictment = prediction;

        return prediction;
    }

    public static <E> List<E> decodeBinary(int code, Class<E> enumClass) {
        List<E> badges = new ArrayList<>();
        int curi = 0;
        for (E badge : enumClass.getEnumConstants()) {
            if (((code >> curi) & 1) == 1) badges.add(badge);
            curi ++;
        }

        return badges;
    }

    public static <E> int encodeBinary(List<E> badges, Class<E> enumClass) {
        int code = 0;
        List<E> allBadges = Arrays.asList(enumClass.getEnumConstants());
        for (E badge : badges) code += 1 << allBadges.indexOf(badge);

        return code;
    }

    public static String truncate(String str, int lengthMax) {
        return str.length() > lengthMax ? str.substring(0, lengthMax - 3) + "..." : str;
    }

    public static String truncateLength(String str, int maxWidth, FontMetrics metrics) {
        char[] chars = str.toCharArray();
        int curWidth = 0;
        int curi = 0;
        for (char cur : chars) {
            curWidth += metrics.charsWidth(chars, curi, 1);
            if (curWidth > maxWidth) return str.substring(0, curi) + "...";
            else curi ++;
        }
        return str;
    }
}
