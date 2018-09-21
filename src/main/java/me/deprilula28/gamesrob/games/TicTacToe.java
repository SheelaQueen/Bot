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
            1, ITEMS.length - 1, GameType.MULTIPLAYER, false, true,
            TicTacToe::new, TicTacToe.class, Collections.singletonList(new GamesInstance.GameMode("reversettt", "reverse rev reversed inverted donttictactoe")));
    private int tiles = 9;
    @Setting(min = 2, max = 5, defaultValue = 3) public int boardSize;
    @Setting(min = 2, max = 10, defaultValue = 3) public int connectTiles;

    private List<Optional<String>> board = new ArrayList<>();
    private Map<Player, String> playerItems = new HashMap<>();
    private Map<String, Player> itemPlayers = new HashMap<>();
    private List<Player> alive = new ArrayList<>();

    @Override
    public void begin(Match match, Provider<RequestPromise<Message>> initialMessage) {
        tiles = boardSize * boardSize;
        alive = new ArrayList<>(match.getPlayers());

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
        Player cur = getTurn();
        if (!cur.getUser().filter(it -> it.equals(author)).isPresent()) return;
        if (contents.length() != 1) return;

        int numb = Utility.inputLetter(contents);
        if (numb >= tiles || numb < 0) return;

        if (board.get(numb).isPresent()) return;

        board.set(numb, Optional.of(playerItems.get(cur)));

        if (!detectVictory()) nextTurn();
    }

    @Override
    protected boolean isInvalidTurn() {
        return !getTurn().getUser().isPresent() || !alive.contains(getTurn());
    }

    @Override
    protected void handleInvalidTurn() {
        if (!getTurn().getUser().isPresent()) handleAIPlay();
    }

    @Override
    public boolean detectVictory() {
        String matcher = detectVictory(board);
        if (matcher == null) return false;

        if (board.stream().allMatch(Optional::isPresent)) {
            match.onEnd(Language.transl(match.getLanguage(), "game.tictactoe.tie"), true);
            return true;
        }

        boolean reverse = match.isMode("reversettt");
        Player matcherPlayer = itemPlayers.get(matcher);
        return GameUtil.gameEnd(reverse, matcherPlayer, alive, match);
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

        return GameUtil.detectVictory(reorganized, boardSize - 1, boardSize - 1,
                1, connectTiles - 2);
    }

    @Override
    public String turnUpdatedMessage(boolean over) {
        StringBuilder builder = new StringBuilder();
        if (!over) builder.append(Language.transl(match.getLanguage(), "gameFramework.turn", getTurn().getUser()
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

    private static final int AI_MAX_LAYERS = 4;

    @Override
    public void handleAIPlay() {
        String tile = playerItems.get(getTurn());
        int playTile = MinMaxAI.use(processor(tile, board, (turn + 1) >= getPlayers().size() ? 0 : turn + 1, 0));

        if (playTile < 0 || playTile >= board.size() || board.get(playTile).isPresent()) board.stream()
                .filter(Optional::isPresent).findFirst().ifPresent(it -> board.set(board.indexOf(it), Optional.of(tile)));
        else board.set(playTile, Optional.of(tile));
        detectVictory();
    }

    private MinMaxAI.BranchProcessor processor(String emote, List<Optional<String>> board, int nturn, int layer) {
        return branch -> {
            for (int i = 0; i < tiles; i ++) {
                if (board.get(i).isPresent()) {
                    branch.node(0.0);
                    continue;
                }

                List<Optional<String>> clonedBoard = new ArrayList<>(board);
                clonedBoard.set(i, Optional.of(emote));

                if (clonedBoard.stream().allMatch(Optional::isPresent)) {
                    branch.node(0.0);
                    continue;
                }

                String winner = detectVictory(clonedBoard);
                if (winner == null) {
                    if (layer >= AI_MAX_LAYERS) branch.node(0.0);
                    else branch.walk(processor(playerItems.get(getPlayers().get(nturn)), clonedBoard,
                            (nturn + 1) >= getPlayers().size() ? 0 : nturn + 1, layer + 1));
                } else branch.node(1.0);
            }
        };
    }
}
