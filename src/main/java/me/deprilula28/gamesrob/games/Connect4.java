package me.deprilula28.gamesrob.games;

import me.deprilula28.gamesrob.GamesROB;
import me.deprilula28.gamesrob.Language;
import me.deprilula28.gamesrob.baseFramework.*;
import me.deprilula28.gamesrob.utility.Log;
import me.deprilula28.gamesrob.utility.Utility;
import me.deprilula28.jdacmdframework.RequestPromise;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.User;

import javax.xml.ws.Provider;
import java.util.*;

public class Connect4 extends TurnMatchHandler {
    private static final String[] ITEMS = {
            "\uD83D\uDD35", "\uD83D\uDD34", "\uD83D\uDD18", "⚫", "\uD83D\uDD36", "\uD83D\uDD37"
    };
    public static final GamesInstance GAME = new GamesInstance(
            "connectFour", "connect4 connectfour lig4 ligquatro con4 confour c4",
            1, ITEMS.length - 1, GameType.MULTIPLAYER, false, true,
            Connect4::new, Connect4.class, Collections.singletonList(new GamesInstance.GameMode(
                    "reversec4", "reverse rev reversed inverted dontconnect4")));

    @Setting(min = 3, max = 6, defaultValue = 4) public int tiles;
    @Setting(min = 1, max = 6, defaultValue = 6) public int rows; // Y axis
    @Setting(min = 1, max = 8, defaultValue = 7) public int columns; // X axis

    private Map<Player, String> playerItems = new HashMap<>();
    private Map<String, Player> itemPlayers = new HashMap<>();
    private List<List<Optional<String>>> board = new ArrayList<>(); // List of rows (Y axis)
    private List<Player> alive = new ArrayList<>();

    @Override
    public void begin(Match match, Provider<RequestPromise<Message>> initialMessage) {
        Utility.populateItems(match.getPlayers(), ITEMS, playerItems, itemPlayers);
        alive = new ArrayList<>(match.getPlayers());
        super.begin(match, initialMessage);
    }

    @Override
    public void receivedMessage(String contents, User author, Message reference) {
        messages ++;
        Player cur = getTurn();
        if (!cur.getUser().filter(it -> it.equals(author)).isPresent()) return;
        Optional<Integer> numb = GameUtil.safeParseInt(contents);
        if (!numb.isPresent()) return;
        int n = numb.get() - 1;
        if (n < 0 || n >= columns) return;

        List<Optional<String>> row = board.get(n);
        int ln = getLnDrop(row);
        if (ln >= rows) return;
        row.set(ln, Optional.of(playerItems.get(Player.user(author))));
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
    public void receivedDM(String contents, User from, Message reference) { }

    @Override
    public boolean detectVictory() {
        if (board.stream().allMatch(it -> it.stream().allMatch(Optional::isPresent))) {
            match.onEnd("It's a draw!", true);
            return true;
        }

        String matcher = GameUtil.detectVictory(board, rows, columns - 1, 1, tiles - 2);
        if (matcher == null) return false;

        boolean reverse = match.isMode("reversec4");
        Player matcherPlayer = itemPlayers.get(matcher);
        return GameUtil.gameEnd(reverse, matcherPlayer, alive, match);
    }

    @Override
    public String turnUpdatedMessage(boolean over) {
        if (board.isEmpty()) {
            for (int y = 0; y <= rows; y ++) {
                List<Optional<String>> row = new ArrayList<>();
                for (int x = 0; x <= columns; x ++) {
                    row.add(Optional.empty());
                }
                board.add(row);
            }
        }

        StringBuilder builder = new StringBuilder();
        if (!over) builder.append(Language.transl(match.getLanguage(), "gameFramework.turn", getTurn().getUser()
                .map(User::getAsMention).orElseThrow(() -> new RuntimeException("Asked update message on AI turn."))));
        builder.append("\n");

        for (int i = 0; i < columns; i ++) builder.append(Utility.getNumberEmote(i));
        builder.append("\n");

        for (int x = rows - 1; x >= 0; x --) {
            for (int y = 0; y < columns; y ++) {
                builder.append(board.get(y).get(x).orElse("⬜"));
            }
            builder.append("\n");
        }

        if (!over) appendTurns(builder, playerItems);

        return builder.toString();
    }

    private static final int AI_MAX_LAYERS = 3;

    @Override
    public void handleAIPlay() {
        String tile = playerItems.get(getTurn());
        int playCol = MinMaxAI.use(processor(tile, board, (turn + 1) >= getPlayers().size() ? 0 : turn + 1, 0));
        List<Optional<String>> row = board.get(playCol);
        int ln = getLnDrop(row);
        if (ln >= rows) return;
        row.set(ln, Optional.of(tile));
        detectVictory();
    }

    private int getLnDrop(List<Optional<String>> row) {
        int ln = 0;
        for (int curi = row.size() - 1; curi >= 0; curi --) {
            if (row.get(curi).isPresent()) {
                ln = curi + 1;
                break;
            }
        }

        return ln;
    }

    private MinMaxAI.BranchProcessor processor(String emote, List<List<Optional<String>>> board, int nturn, int layer) {
        return branch -> {
            for (int i = 0; i < columns; i ++) {
                List<List<Optional<String>>> clonedBoard = new ArrayList<>(board);

                List<Optional<String>> row = new ArrayList<>(clonedBoard.get(i));
                int ln = getLnDrop(row);
                if (ln >= rows) {
                    branch.node(0.0);
                    continue;
                }

                row.set(ln, Optional.of(emote));
                clonedBoard.set(i, row);

                if (clonedBoard.stream().allMatch(it -> it.stream().allMatch(Optional::isPresent))) {
                    branch.node(0.5);
                    continue;
                }

                String winner = GameUtil.detectVictory(board, rows, columns - 1, 2, 1);
                if (winner == null) {
                    if (layer >= AI_MAX_LAYERS) branch.node(0.25);
                    else branch.walk(processor(playerItems.get(getPlayers().get(nturn)), clonedBoard,
                            (nturn + 1) >= getPlayers().size() ? 0 : nturn + 1, layer + 1));
                    continue;
                }
                branch.node(winner.equals(emote) ? 1.0 : 0.0);
            }
        };
    }
}
