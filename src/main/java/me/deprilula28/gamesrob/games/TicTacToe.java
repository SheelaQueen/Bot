package me.deprilula28.gamesrob.games;

import me.deprilula28.gamesrob.Language;
import me.deprilula28.gamesrob.baseFramework.*;
import me.deprilula28.gamesrob.utility.Log;
import me.deprilula28.gamesrob.utility.Utility;
import me.deprilula28.jdacmdframework.RequestPromise;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.User;

import javax.xml.ws.Provider;
import java.util.*;

public class TicTacToe extends TurnMatchHandler {
    private static final String[] ITEMS = {
            "⭕", "❌", "❗", "❓", "🍆"
    };
    public static final GamesInstance GAME = new GamesInstance(
            "tictactoe", "tictactoe ttt",
            1, ITEMS.length - 1, GameType.MULTIPLAYER, false,
            TicTacToe::new, TicTacToe.class
    );
    private int tiles = 9;
    @Setting(min = 2, max = 5, defaultValue = 3) public int boardSize;
    @Setting(min = 2, max = 10, defaultValue = 3) public int connectTiles;

    private List<Optional<String>> board = new ArrayList<>();
    private Map<Optional<User>, String> playerItems = new HashMap<>();
    private Map<String, Optional<User>> itemPlayers = new HashMap<>();

    @Override
    public void begin(Match match, Provider<RequestPromise<Message>> initialMessage) {
        tiles = boardSize * boardSize;

        for (int i = 0; i < tiles; i ++) {
            board.add(Optional.empty());
        }

        Utility.populateItems(match.getPlayers(), ITEMS, playerItems, itemPlayers);
        super.begin(match, initialMessage);
    }

    @Override
    public void receivedDM(String contents, User from, Message reference) { }

    @Override
    public void receivedMessage(String contents, User author, Message reference) {
        messages ++;
        getTurn().ifPresent(cur -> {
            if (cur != author) return;
            if (contents.length() != 1) return;

            int numb = Utility.inputLetter(contents);
            if (numb >= tiles || numb < 0) return;

            if (board.get(numb).isPresent()) return;

            board.set(numb, Optional.of(playerItems.get(Optional.of(cur))));

            if (!detectVictory()) nextTurn();
        });
    }

    @Override
    public boolean detectVictory() {
        String winner = detectVictory(board);
        if (winner == null) {
            if (board.stream().allMatch(Optional::isPresent)) {
                match.onEnd("It's a draw!", true);
                return true;
            }
            return false;
        }
        match.onEnd(itemPlayers.get(winner));
        return true;
    }

    private String detectVictory(List<Optional<String>> board) {
        List<List<Optional<String>>> reorganized = new ArrayList<>();

        for (int i = 1; i <= boardSize; i ++) {
            List<Optional<String>> row = new ArrayList<>();
            int localIndex = i;
            while (row.size() < boardSize) {
                row.add(board.get(localIndex - 1));
                localIndex += boardSize;
            }
            reorganized.add(row);
        }
        Log.info(reorganized);

        return GameUtil.detectVictory(reorganized, boardSize - 1, boardSize - 1,
                1, connectTiles - 2);
    }

    @Override
    public String turnUpdatedMessage(boolean over) {
        StringBuilder builder = new StringBuilder();
        if (!over) builder.append(Language.transl(match.getLanguage(), "gameFramework.turn", getTurn()
                .map(User::getAsMention).orElseThrow(() -> new RuntimeException("Asked update message on AI turn."))));

        for (int i = 0; i < tiles; i++) {
            if (i % boardSize == 0) builder.append("\n");
            Optional<String> tile = board.get(i);
            builder.append(tile.orElse(Utility.getLetterEmote(i))).append(" ");
        }

        builder.append("\n");
        if (!over) appendTurns(builder, playerItems);

        return builder.toString();
    }

    private static final int AI_MAX_LAYERS = 8;

    @Override
    public void handleAIPlay() {
        String tile = playerItems.get(getTurn());
        int playTile = MinMaxAI.use(processor(tile, board, 0));

        if (!board.get(playTile).isPresent()) board.set(playTile, Optional.of(tile));
    }

    private MinMaxAI.BranchProcessor processor(String emote, List<Optional<String>> board, int layer) {
        return branch -> {
            for (int i = 0; i < tiles; i ++) {
                if (board.get(i).isPresent()) {
                    branch.node(0.0);
                    continue;
                }

                List<Optional<String>> clonedBoard = new ArrayList<>();
                Collections.copy(clonedBoard, board);

                clonedBoard.set(i, Optional.of(emote));

                if (clonedBoard.stream().allMatch(Optional::isPresent)) {
                    branch.node(0.5);
                    continue;
                }

                String winner = detectVictory(clonedBoard);
                if (winner == null) {
                    if (layer >= AI_MAX_LAYERS) branch.node(0.0);
                    else branch.walk(processor(emote, clonedBoard, layer + 1));
                    continue;
                }
                branch.node(winner.equals(emote) ? 1.0 : 0.0);
            }
        };
    }
}
