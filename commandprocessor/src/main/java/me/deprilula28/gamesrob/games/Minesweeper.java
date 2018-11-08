package me.deprilula28.gamesrob.games;

import me.deprilula28.gamesrob.Language;
import lombok.AllArgsConstructor;
import lombok.Data;
import me.deprilula28.gamesrob.baseFramework.*;
import me.deprilula28.gamesrobshardcluster.utilities.Constants;
import me.deprilula28.gamesrob.utility.Utility;
import me.deprilula28.jdacmdframework.RequestPromise;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.User;

import javax.xml.ws.Provider;
import java.util.*;

public class Minesweeper implements MatchHandler {
    public static final GamesInstance GAME = new GamesInstance(
            "minesweeper", "minesweeper minsw miner ms",
            1, 3, GameType.HYBRID, false, false,
            Minesweeper::new, Minesweeper.class, Arrays.asList(
                new GamesInstance.GameMode("knightpaths", "knightpaths knightspaths knightpath kp knpath"),
                new GamesInstance.GameMode("house", "house hs onlytopandcorners topcorners"))
    );
    private static final Map<String, String> EMOTE_ID_MAP = new HashMap<>();
    private static boolean loaded = false;

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;

        for (int i = 0; i < 9; i ++) {
            String name = "bombs" + i;
            GameUtil.getEmote(name).ifPresent(it -> EMOTE_ID_MAP.put(name, it));
        }
    }

    @Setting(min = 4, max = 6, defaultValue = 6) public int rows; // Y axis
    @Setting(min = 4, max = 6, defaultValue = 6) public int columns; // X axis
    @Setting(min = 2, max = 30, defaultValue = 12) public int bombs;
    private List<User> hiddenBombs = new ArrayList<>();

    @Data
    @AllArgsConstructor
    private class MinesweeperTile {
        private MinesweeperTileType type;
        private boolean flagged;
    }

    private enum MinesweeperTileType {
        UNCOVERED, DUG, BOMB
    }

    private Map<Player, List<List<MinesweeperTile>>> board = new HashMap<>(); // List of columns (X axis)
    private List<Player> playing = new ArrayList<>();
    private Match match;
    private int messages = 0;

    @Override
    public void begin(Match match, Provider<RequestPromise<Message>> initialMessage) {
        match.getPlayers().forEach(player -> {
            List<List<MinesweeperTile>> curBoard = new ArrayList<>();
            for (int y = 0; y <= rows; y ++) {
                List<MinesweeperTile> row = new ArrayList<>();
                for (int x = 0; x <= columns; x ++) {
                    row.add(new MinesweeperTile(MinesweeperTileType.UNCOVERED, false));
                }
                curBoard.add(row);
            }
            board.put(player, curBoard);
        });
        playing.addAll(match.getPlayers());

        ensureLoaded();
        this.match = match;
        match.setMatchMessage(initialMessage.invoke(null));
    }

    @Override
    public void receivedMessage(String contents, User author, Message reference) {
        messages ++;
        if (!playing.contains(Player.user(author))) return;
        int length = contents.length();
        if (length < 2 || length > 3 || (length == 3 && !contents.endsWith("F"))) return;

        List<List<MinesweeperTile>> curBoard = board.get(Player.user(author));
        int x = Utility.inputLetter(contents);
        Optional<Integer> optY = GameUtil.safeParseInt(contents.charAt(1) + "");
        if (x < 0 || x >= columns || !optY.isPresent()) return;
        int y = optY.get() - 1;
        if (y < 0 || y >= rows) return;

        MinesweeperTile tile = curBoard.get(x).get(y);
        MinesweeperTileType type = tile.getType();
        if (type == MinesweeperTileType.DUG) return;
        if (type == MinesweeperTileType.BOMB && length != 3) {
            if (match.isMultiplayer()) {
                playing.remove(Player.user(author));
                if (playing.size() == 1) match.onEnd(playing.get(0));
            } else match.onEnd(Language.transl(match.getLanguage(), "game.minesweeper.bomb"), true);
            return;
        }

        if (length == 3) curBoard.get(x).set(y, new MinesweeperTile(tile.type, true));
        else if (!hiddenBombs.contains(author) || match.getMode().isPresent())
            curBoard.get(x).set(y, new MinesweeperTile(MinesweeperTileType.DUG, false));
        else clearArea(curBoard, x, y);

        /*
        if (!match.getMode().isPresent()) clearArea(curBoard, x, y);
        else curBoard.get(x).set(y, new MinesweeperTile(MinesweeperTileType.DUG, false))
         */

        if (!hiddenBombs.contains(author)) {
            Random rng = GameUtil.generateRandom();
            int bombsLeft = bombs;
            while (bombsLeft -- > 0) {
                int plantX = rng.nextInt(columns);
                int plantY = rng.nextInt(rows);
                while (curBoard.get(plantX).get(plantY).getType() != MinesweeperTileType.UNCOVERED) {
                    plantX = rng.nextInt(columns);
                    plantY = rng.nextInt(rows);
                }

                curBoard.get(plantX).set(plantY, new MinesweeperTile(MinesweeperTileType.BOMB, false));
            }

            hiddenBombs.add(author);
            if (length != 3 && !match.getMode().isPresent()) clearArea(curBoard, x, y);
        }

        // If all the tiles are either dug or bombs
        if (curBoard.stream().noneMatch(tiles -> tiles.stream().anyMatch(ctile -> ctile.getType() == MinesweeperTileType.UNCOVERED))) {
            match.onEnd(Player.user(author));
            return;
        }

        MessageBuilder builder = new MessageBuilder();
        updatedMessage(false, builder);
        match.setMatchMessage(GameUtil.editSend(match.getChannelIn(), messages, match.getMatchMessage(), builder.build()));
        if (messages > Constants.MESSAGES_SINCE_THRESHOLD) messages = 0;
        match.interrupt();
    }

    private void clearArea(List<List<MinesweeperTile>> curBoard, int x, int y) {
        curBoard.get(x).set(y, new MinesweeperTile(MinesweeperTileType.DUG, false));
        if (getNearbyBombs(curBoard, x, y) == 0)
            for (int xOff = -1; xOff <= 1; xOff ++)
                for (int yOff = -1; yOff <= 1; yOff ++) {
                    int nx = x + xOff;
                    int ny = y + yOff;
                    if (nx < 0 || nx > columns || ny < 0 || ny > rows || (xOff == 0 && yOff == 0)) continue;
                    if (curBoard.get(nx).get(ny).getType().equals(MinesweeperTileType.DUG)) continue;
                    clearArea(curBoard, x + xOff, y + yOff);
                }
    }

    @Override
    public void receivedDM(String contents, User from, Message reference) { }

    @Override
    public void onQuit(User user) {
        board.remove(Player.user(user));
    }

    @Override
    public void updatedMessage(boolean over, MessageBuilder msgBuilder) {
        EmbedBuilder embed = new EmbedBuilder().setTitle(Language.transl(match.getLanguage(), "game.minesweeper.name"))
                .setColor(Utility.getEmbedColor(match.getChannelIn().getGuild()));

        board.forEach((user, curBoard) -> {
            StringBuilder builder = new StringBuilder();
            builder.append("⬛");
            for (int i = 0; i < columns; i ++) builder.append(Utility.getLetterEmote(i)).append(" ");
            builder.append("\n");

            for (int y = 0; y < rows; y ++) {
                builder.append(Utility.getNumberEmote(y));

                for (int x = 0; x < columns; x ++) {
                    MinesweeperTile tile = curBoard.get(x).get(y);
                    switch(tile.getType()) {
                        case DUG:
                            int bombs = getNearbyBombs(curBoard, x, y);
                            builder.append(bombs > 0 ? EMOTE_ID_MAP.get("bombs" + bombs) : "⬛").append(" ");
                            break;
                        case BOMB:
                            if (over || (!playing.contains(user) && match.isMultiplayer())) {
                                builder.append("\uD83D\uDCA3 ");
                                break;
                            }
                        case UNCOVERED:
                            builder.append(tile.isFlagged() ? "🚩" : "⬜").append(" ");
                            break;
                    }
                }
                builder.append("\n");
            }

            if (match.isMultiplayer()) {
                builder.append("\n");
                embed.addField(user.toString(), builder.toString(), true);
            } else msgBuilder.append(builder.toString());
        });

        if (match.isMultiplayer()) msgBuilder.setEmbed(embed.build());
    }

    private int getNearbyBombs(List<List<MinesweeperTile>> curBoard, int x, int y) {
        int count = 0;

        int[] scroll;
        if (!match.getMode().isPresent()) for (int searchX = x - 1; searchX <= x + 1; searchX ++) {
            for (int searchY = y - 1; searchY <= y + 1; searchY ++) {
                if (searchX < 0 || searchX > columns || searchY < 0 || searchY > rows) continue;
                if (curBoard.get(searchX).get(searchY).getType() == MinesweeperTileType.BOMB) count ++;
            }
        } else switch (match.getMode().get().getLanguageCode()) {
            case "knightpaths":
                final int[] X_ARRAY = { -2, -2, -1, -1, 1, 1, 2, 2 };
                final int[] Y_ARRAY = { -1, 1, -2, 2, -2, 2, -1, 1 };

                for (int i = 0; i < X_ARRAY.length; i ++) {
                    int searchX = x + X_ARRAY[i];
                    int searchY = y + Y_ARRAY[i];
                    if (searchX < 0 || searchX > columns || searchY < 0 || searchY > rows) continue;
                    if (curBoard.get(searchX).get(searchY).getType() == MinesweeperTileType.BOMB) count ++;
                }
                break;
            case "house":
                for (int searchX = x - 1; searchX <= x + 1; searchX ++) {
                    for (int searchY = y - 1; searchY <= y + 1; searchY ++) {
                        if (searchX < 0 || searchX > columns || searchY < 0 || searchY > rows
                                || ((searchX == x - 1 || searchX == x + 1) && searchY == y) // left & right
                                || (searchY == y + 1 && searchX == x) /* bottom */) continue;
                        if (curBoard.get(searchX).get(searchY).getType() == MinesweeperTileType.BOMB) count ++;
                    }
                }
                break;
        }

        return count;
    }
}
