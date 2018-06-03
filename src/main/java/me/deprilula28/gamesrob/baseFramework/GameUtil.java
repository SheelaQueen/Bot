package me.deprilula28.gamesrob.baseFramework;

import javafx.util.Pair;
import me.deprilula28.gamesrob.GamesROB;
import me.deprilula28.gamesrob.utility.Constants;
import me.deprilula28.gamesrob.utility.Log;
import me.deprilula28.jdacmdframework.RequestPromise;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.TextChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public class GameUtil {
    public static Optional<Integer> safeParseInt(String contents) {
        try {
            return Optional.of(Integer.valueOf(contents));
        } catch (Exception e) {
            return Optional.empty();
        }
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
        return GamesROB.getGuildById(Constants.EMOTE_GUILD_ID).map(it -> it.getEmotesByName(of, false)
                .get(0).getAsMention());
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
}
