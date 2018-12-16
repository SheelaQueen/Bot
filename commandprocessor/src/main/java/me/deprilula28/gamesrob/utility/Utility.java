package me.deprilula28.gamesrob.utility;

import lombok.Getter;
import lombok.Setter;
import me.deprilula28.gamesrob.baseFramework.Match;
import me.deprilula28.gamesrob.baseFramework.Player;
import me.deprilula28.gamesrob.commands.ProfileCommands;
import me.deprilula28.gamesrob.data.GuildProfile;
import me.deprilula28.gamesrob.data.Statistics;
import me.deprilula28.gamesrob.data.UserProfile;
import me.deprilula28.gamesrobshardcluster.GamesROBShardCluster;
import me.deprilula28.gamesrobshardcluster.utilities.Constants;
import me.deprilula28.gamesrobshardcluster.utilities.Log;
import me.deprilula28.jdacmdframework.CommandContext;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Channel;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.requests.RestAction;

import java.awt.*;
import java.io.Closeable;
import java.io.IOException;
import java.text.DecimalFormat;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static me.deprilula28.gamesrobshardcluster.utilities.Constants.GAMESROB_DOMAIN;

public class Utility {
    @Getter @Setter private static Font starlightFont;

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

        public static <R> Promise<R> action(RestAction<R> provider) {
            Promise<R> promise = new Promise<>();
            provider.queue(promise::done);

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

        public <V> Promise<V> mapPromise(Function<R, Promise<V>> mapper) {
            Promise<V> promiseSecond = new Promise<>();
            then(it -> mapper.apply(it).then(promiseSecond::done));

            return promiseSecond;
        }
    }
    
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

    public static List<Guild> getAllMutualGuilds(String id) {
        List<Guild> mutualGuilds = new ArrayList<>();
        GamesROBShardCluster.shards.forEach(cur -> {
            User su = cur.getUserById(id);
            if (su != null) mutualGuilds.addAll(su.getMutualGuilds());
        });

        return mutualGuilds;
    }

    public static void quietlyClose(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException e) {}
    }

    public static String readResource(String path) {
        return Cache.get("text_path_" + path, n -> {
            Scanner scann = new Scanner(Utility.class.getResourceAsStream(path));
            StringBuilder builder = new StringBuilder();

            while (scann.hasNextLine()) {
                if (builder.length() > 0) builder.append("\n");
                builder.append(scann.nextLine());
            }

            return builder.toString();
        });
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

    public static String getEmoteForStatus(String status) {
        switch (status) {
            case "CONNECTED":
                return "<:online:313956277808005120>";
            case "SHUTDOWN":
            case "FAILED_TO_LOGIN":
            case "RECONNECT_QUEUED":
            case "WAITING_TO_RECONNECT":
            case "DISCONNECTED":
                return "<:offline:313956277237710868>";
            case "ATTEMPTING_TO_RECONNECT":
            case "LOGGING_IN":
            case "CONNECTING_TO_WEBSOCKET":
            case "IDENTIFYING_SESSION":
            case "AWAITING_LOGIN_CONFIRMATION":
            case "LOADING_SUBSYSTEMS":
                return "<:invisible:313956277107556352>";
            default:
                return "<:dnd:313956276893646850>";
        }
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

    public static boolean hasPermission(Channel channel, Member member, Permission permission) {
        if (member.hasPermission(permission)) {
            return channel.getRolePermissionOverrides().stream().noneMatch(it -> member.getRoles().contains(it.getRole())
                    && it.getDenied().contains(permission)) && channel.getMemberPermissionOverrides().stream().noneMatch(it -> it.getMember().equals(member)
                    && it.getDenied().contains(permission));
        } else return channel.getRolePermissionOverrides().stream().anyMatch(it -> member.getRoles().contains(it.getRole())
                && it.getAllowed().contains(permission)) || channel.getMemberPermissionOverrides().stream()
                .anyMatch(it -> it.getMember().equals(member) && it.getAllowed().contains(permission));
        /*
        Log.info("Checking permission:", channel, member, permission,
                "\nMember", member.hasPermission(permission),
                channel.getRolePermissionOverrides().stream().noneMatch(it -> member.getRoles().contains(it.getRole())
                        && it.getDenied().contains(permission)),
                channel.getMemberPermissionOverrides().stream().noneMatch(it -> it.getMember().equals(member)
                        && it.getDenied().contains(permission)),
                "\nOther", channel.getRolePermissionOverrides().stream().anyMatch(it -> member.getRoles().contains(it.getRole())
                        && it.getAllowed().contains(permission)) || channel.getMemberPermissionOverrides().stream()
                        .anyMatch(it -> it.getMember().equals(member) && it.getAllowed().contains(permission)),
                "\nResult", (member.hasPermission(permission) &&
                        channel.getRolePermissionOverrides().stream().noneMatch(it -> member.getRoles().contains(it.getRole())
                                && it.getDenied().contains(permission)) &&
                        channel.getMemberPermissionOverrides().stream().noneMatch(it -> it.getMember().equals(member)
                                && it.getDenied().contains(permission)))
                        || channel.getRolePermissionOverrides().stream().anyMatch(it -> member.getRoles().contains(it.getRole())
                        && it.getAllowed().contains(permission)) || channel.getMemberPermissionOverrides().stream()
                        .anyMatch(it -> it.getMember().equals(member) && it.getAllowed().contains(permission)));
        return (member.hasPermission(permission) &&
                channel.getRolePermissionOverrides().stream().noneMatch(it -> member.getRoles().contains(it.getRole())
                        && it.getDenied().contains(permission)) &&
                channel.getMemberPermissionOverrides().stream().noneMatch(it -> it.getMember().equals(member)
                        && it.getDenied().contains(permission)))
            || channel.getRolePermissionOverrides().stream().anyMatch(it -> member.getRoles().contains(it.getRole())
                    && it.getAllowed().contains(permission)) || channel.getMemberPermissionOverrides().stream()
                .anyMatch(it -> it.getMember().equals(member) && it.getAllowed().contains(permission));
                */
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

    public static String getImageURL(Match match) {
        return GAMESROB_DOMAIN + "/gameimage/" + match.getChannelIn().getId() + "/" + match.getIteration();
    }

    public static String getDefaultPrefix() {
        return GamesROBShardCluster.premiumBot ? Constants.DEFAULT_PREMIUM_PREFIX : Constants.DEFAULT_PREFIX;
    }

    public static String getPrefix(Guild guild) {
        String defaultPrefix = getDefaultPrefix();
        if (guild == null) return defaultPrefix;
        String value = GuildProfile.get(guild).getGuildPrefix();
        return value == null ? defaultPrefix :
                (value.length() > Constants.PREFIX_MAX_TRESHHOLD ? defaultPrefix: value);
    }

    public static String getLanguage(CommandContext context) {
        return getLanguage(context.getAuthor(), context.getGuild());
    }

    public static String getLanguage(User user, Guild guild) {
        // User Language
        UserProfile up = UserProfile.get(user);
        if (up.getLanguage() != null) return up.getLanguage();

        // Guild Language
        GuildProfile gp = GuildProfile.get(guild);
        if (gp.getLanguage() != null) return gp.getLanguage();

        // Guild Owner Language
        UserProfile ownerLang = UserProfile.get(guild.getOwner().getUser());
        if (ownerLang.getLanguage() != null) return ownerLang.getLanguage();

        return Constants.DEFAULT_LANGUAGE;
    }

    public static String getNotEnoughTokensMessage(CommandContext context, int amount) {
        UserProfile profile = UserProfile.get(context.getAuthor());
        return Language.transl(context, "genericMessages.notEnoughTokens.beggining",
                Utility.addNumberDelimitors(amount - profile.getTokens(context.getGuild())))
                + Language.transl(context, "genericMessages.notEnoughTokens.tokensCommand", getPrefix(context.getGuild()));
    }

    public static String getPrefixHelp(Guild guild) {
        return getPrefix(guild).replaceAll("\\$", "\uFF04")
                .replaceAll("\\\\", "\\\\\\\\");
    }

    public static <T> int indexOf(T[] list, T entry) {
        for (int i = 0; i < list.length; i++) if (list[i] == entry) return i;
        return -1;
    }
}
