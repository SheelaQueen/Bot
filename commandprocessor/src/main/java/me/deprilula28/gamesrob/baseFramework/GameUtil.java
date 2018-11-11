package me.deprilula28.gamesrob.baseFramework;

import me.deprilula28.gamesrob.GamesROB;
import me.deprilula28.gamesrob.utility.Cache;
import me.deprilula28.gamesrobshardcluster.utilities.Constants;
import javafx.util.Pair;
import me.deprilula28.jdacmdframework.RequestPromise;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.entities.*;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.Supplier;

public class GameUtil {
    public static Optional<Integer> safeParseInt(String contents) {
        try {
            return Optional.of(Integer.valueOf(contents));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public static boolean gameEnd(boolean reverse, Player matcherPlayer, List<Player> alive, Match match) {
        if (reverse) {
            alive.remove(matcherPlayer);
            if (alive.size() == 1) {
                match.onEnd(alive.get(0));
                return true;
            }
            return false;
        } else match.onEnd(matcherPlayer);
        return true;
    }

    public static RequestPromise<Message> editSend(TextChannel channel, int messagesSince, RequestPromise<Message> oldMessage, Message message) {
        if (messagesSince > Constants.MESSAGES_SINCE_THRESHOLD) {
            oldMessage.then(it -> it.delete().queue());
            return RequestPromise.forAction(channel.sendMessage(message));
        } else {
            oldMessage.then(it -> it.editMessage(message).queue());
            return oldMessage;
        }
    }

    public static Optional<String> getEmote(String of) {
        return Cache.get("emote_" + of, n -> Optional.of(findEmote(Constants.EMOTE_GUILD_ID, of,
                findEmote(Constants.SERVER_ID, of, null)).get()));
    }

    private static Supplier<String> findEmote(long guild, String of, Supplier<String> backup) {
        return () -> GamesROB.getGuildById(guild).map(it -> it.getEmotesByName(of, false).stream().findFirst().map(Emote::getAsMention)
                .orElseGet(backup)).orElse("unknown");
    }

    public static String detectVictory(List<List<Optional<String>>> board, int rows, int columns, int requiredLeft,
                                       int requiredRight) {
        for (int x = 0; x < board.size(); x++) {
            List<Optional<String>> xRow = board.get(x);
            for (int y = 0; y < xRow.size(); y++) {
                Optional<String> item = xRow.get(y);
                if (!item.isPresent()) continue;
                String itm = item.get();

                if (detectOnRange(board, rows, columns, x, y, -requiredLeft, requiredRight, itm, n -> new Pair<>(n, 0))
                    || detectOnRange(board, rows, columns, x, y, -requiredLeft, requiredRight, itm, n -> new Pair<>(0, n))
                    || detectOnRange(board, rows, columns, x, y, -requiredLeft, requiredRight, itm, n -> new Pair<>(n, -n))
                    || detectOnRange(board, rows, columns, x, y, -requiredLeft, requiredRight, itm, n -> new Pair<>(n, n)))
                        return itm;
            }
        }

        return null;
    }

    private static boolean detectOnRange(List<List<Optional<String>>> board, int rows, int columns,
                                         int x, int y, int from, int to, String item,
                                         Function<Integer, Pair<Integer, Integer>> transformer) {
        List<Integer> ncol = new ArrayList<>();
        for (int i = from; i <= to; i ++) ncol.add(i);

        return ncol.stream().allMatch(cur -> {
            Pair<Integer, Integer> transformed = transformer.apply(cur);
            int transfX = x + transformed.getKey();
            int transfY = y + transformed.getValue();

            if (transfX < 0 || transfX > columns || transfY < 0 || transfY > rows) return false;

            Optional<String> curTile = board.get(transfX).get(transfY);
            return curTile.isPresent() && curTile.get().equals(item);
        });
    }

    public static void appendPlayersScore(Map<Player, String> playerItems, Map<Player, Double> scoreboard,
                                          boolean over, MessageBuilder builder) {
        playerItems.forEach((player, item) -> {
            boolean contains = scoreboard.containsKey(player);
            if (!over || contains)
                builder.append("\n").append(item).append(" ").append(player.toString());
            if (contains) {
                long score = Math.round(scoreboard.get(player));
                builder.append(" (").append(score).append(" points)");
            }
        });
    }

    public static Random generateRandom() {
        return new Random(ThreadLocalRandom.current().nextLong() + System.currentTimeMillis());
    }
}
