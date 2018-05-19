package me.deprilula28.gamesrob.games;

import lombok.AllArgsConstructor;
import lombok.Data;
import me.deprilula28.gamesrob.baseFramework.*;
import me.deprilula28.gamesrob.utility.Log;
import me.deprilula28.gamesrob.utility.Utility;
import me.deprilula28.jdacmdframework.RequestPromise;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.User;

import javax.xml.ws.Provider;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class Minesweeper implements MatchHandler {
    public static final GamesInstance GAME = new GamesInstance(
            "minesweeper", "minesweeper minsw miner ms",
            1, 1, GameType.SINGLEPLAYER,
            Minesweeper::new, Minesweeper.class
    );
    private static final Map<String, String> EMOTE_ID_MAP = new HashMap<>();
    private static boolean loaded = false;

    public static void ensureLoaded() {
        if (loaded) return;
        loaded = true;

        for (int i = 0; i < 9; i ++) {
            String name = "bombs" + i;
            GameUtil.getEmote(name).ifPresent(it -> EMOTE_ID_MAP.put(name, it));
        }
        GameUtil.getEmote("unnocupied").ifPresent(it -> EMOTE_ID_MAP.put("unnocupied", it));
        GameUtil.getEmote("flag").ifPresent(it -> EMOTE_ID_MAP.put("flag", it));
        Log.info(EMOTE_ID_MAP);
    }

    @Setting(min = 4, max = 6, defaultValue = 6) public int rows; // Y axis
    @Setting(min = 4, max = 6, defaultValue = 6) public int columns; // X axis
    @Setting(min = 2, max = 30, defaultValue = 13) public int bombs;
    private boolean hiddenBombs = false;

    @Data
    @AllArgsConstructor
    public class MinesweeperTile {
        private MinesweeperTileType type;
        private boolean flagged;
    }

    private enum MinesweeperTileType {
        UNCOVERED, DUG, BOMB
    }

    private List<List<MinesweeperTile>> board = new ArrayList<>(); // List of columns (X axis)
    private RequestPromise<Message> message;
    private Match match;

    @Override
    public void begin(Match match, Provider<RequestPromise<Message>> initialMessage) {
        for (int y = 0; y <= rows; y ++) {
            List<MinesweeperTile> row = new ArrayList<>();
            for (int x = 0; x <= columns; x ++) {
                row.add(new MinesweeperTile(MinesweeperTileType.UNCOVERED, false));
            }
            board.add(row);
        }

        ensureLoaded();
        this.match = match;
        message = initialMessage.invoke(null);
    }

    @Override
    public void receivedMessage(String contents, User author, Message reference) {
        if (!match.getPlayers().contains(Optional.of(author))) return;
        int length = contents.length();
        if (length < 2 || length > 3 || (length == 3 && !contents.endsWith("F"))) return;

        int x = Utility.inputLetter(contents);
        Optional<Integer> optY = GameUtil.safeParseInt(contents.charAt(1) + "");
        if (x < 0 || x >= columns || !optY.isPresent()) return;
        int y = optY.get() - 1;
        if (y < 0 || y >= rows) return;

        MinesweeperTile tile = board.get(x).get(y);
        MinesweeperTileType type = tile.getType();
        if (type == MinesweeperTileType.DUG) return;
        if (type == MinesweeperTileType.BOMB && length != 3) {
            match.onEnd("You dug a bomb!", true);
            return;
        }
        board.get(x).set(y, length == 3
                ? new MinesweeperTile(MinesweeperTileType.UNCOVERED, true)
                : new MinesweeperTile(MinesweeperTileType.DUG, false));

        if (!hiddenBombs) {
            Random rng = ThreadLocalRandom.current();
            while (bombs -- > 0) {
                int plantX = rng.nextInt(columns);
                int plantY = rng.nextInt(rows);
                while (board.get(plantX).get(plantY).getType() != MinesweeperTileType.UNCOVERED) {
                    plantX = rng.nextInt(columns);
                    plantY = rng.nextInt(rows);
                }

                board.get(plantX).set(plantY, new MinesweeperTile(MinesweeperTileType.BOMB, false));
            }

            hiddenBombs = true;
        }

        // If all the tiles are either dug or bombs
        if (board.stream().allMatch(it -> it.stream().allMatch(t -> t.getType() != MinesweeperTileType.UNCOVERED))) {
            match.onEnd(Optional.of(author));
            return;
        }

        message.then(it -> it.delete().queue());
        message = RequestPromise.forAction(reference.getChannel().sendMessage(updatedMessage(false)));
        match.interrupt();
    }


    @Override
    public void receivedDM(String contents, User from, Message reference) { }

    @Override
    public void onQuit(User user) {
        match.onEnd("The player left", true);
    }

    @Override
    public String updatedMessage(boolean over) {
        StringBuilder builder = new StringBuilder();

        builder.append("⬛");
        for (int i = 0; i < columns; i ++) builder.append(Utility.getLetterEmote(i) + " ");
        builder.append("\n");

        for (int y = 0; y < rows; y ++) {
            builder.append(Utility.getNumberEmote(y));

            for (int x = 0; x < columns; x ++) {
                MinesweeperTile tile = board.get(x).get(y);
                switch(tile.getType()) {
                    case DUG:
                        builder.append(EMOTE_ID_MAP.get("bombs" + getNearbyBombs(x, y)) + " ");
                        break;
                    case BOMB:
                        if (over) {
                            builder.append("\uD83D\uDCA3 ");
                            break;
                        }
                    case UNCOVERED:
                        builder.append((tile.isFlagged() ? EMOTE_ID_MAP.get("flag") : EMOTE_ID_MAP.get("unnocupied"))
                                + " ");
                        break;
                }
            }
            builder.append("\n");
        }

        return builder.toString();
    }

    private int getNearbyBombs(int x, int y) {
        int count = 0;
        for (int searchX = x - 1; searchX <= x + 1; searchX ++) {
            for (int searchY = y - 1; searchY <= y + 1; searchY ++) {
                if (searchX < 0 || searchX > columns || searchY < 0 || searchY > rows) continue;

                if (board.get(searchX).get(searchY).getType() == MinesweeperTileType.BOMB) count ++;
            }
        }

        return count;
    }
}
