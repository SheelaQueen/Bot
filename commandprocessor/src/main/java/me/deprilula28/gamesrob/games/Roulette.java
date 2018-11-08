package me.deprilula28.gamesrob.games;

import me.deprilula28.gamesrob.Language;
import me.deprilula28.gamesrob.baseFramework.*;
import me.deprilula28.gamesrob.utility.Utility;
import me.deprilula28.jdacmdframework.RequestPromise;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.User;

import javax.xml.ws.Provider;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class Roulette extends TurnMatchHandler {
    public static final GamesInstance GAME = new GamesInstance(
            "roulette", "roulette russianroulette rl rr roul",
            1, 7, GameType.MULTIPLAYER, true, false,
            Roulette::new, Roulette.class, Collections.emptyList()
    );
    private static final int BARREL_HOLES = 8;
    private static final int MIN_STRENGTH = 2;
    private static final int MAX_STRENGTH = 6;
    private static final String[] ITEMS = {
            "\uD83D\uDE10", "\uD83D\uDE1F", "\uD83D\uDE20", "\uD83D\uDE44", "\uD83D\uDE11", "\uD83D\uDE35", "\uD83D\uDE26", "\uD83E\uDD58"
    };

    private Map<Player, String> playerItems = new HashMap<>();
    private List<Player> alive = new ArrayList<>();
    private String lastNotification = "";

    private int bulletIn;

    @Override
    public List<Player> getPlayers() {
        return alive;
    }

    @Override
    public void onQuit(User user) {
        alive.remove(Player.user(user));
        super.onQuit(user);
    }

    @Override
    public void begin(Match match, Provider<RequestPromise<Message>> initialMessage) {
        Utility.populateItems(match.getPlayers(), ITEMS, playerItems, new HashMap<>());
        bulletIn = ThreadLocalRandom.current().nextInt(BARREL_HOLES);
        alive.addAll(match.getPlayers());
        super.begin(match, initialMessage);
    }

    @Override
    public void receivedMessage(String contents, User author, Message reference) {
        messages ++;
        Player cur = getTurn();
        if (!cur.getUser().filter(it -> it.equals(author)).isPresent()) return;
        Optional<Integer> numb = GameUtil.safeParseInt(contents);
        if (!numb.isPresent()) return;
        int pickedInt = numb.get();
        if (pickedInt < MIN_STRENGTH || pickedInt > MAX_STRENGTH) return;

        bulletIn = (bulletIn + pickedInt + (int) (ThreadLocalRandom.current().nextDouble() * (pickedInt / 2))) % BARREL_HOLES;
        if (bulletIn == 0) {
            alive.remove(Player.user(author));
            playerItems.put(Player.user(author), "❌");
            lastNotification = Language.transl(match.getLanguage(), "game.roulette.shot", author.getAsMention());
        } else lastNotification = Language.transl(match.getLanguage(), "game.roulette.survive", author.getAsMention());
        if (!detectVictory()) nextTurn();
    }

    @Override
    public boolean detectVictory() {
        if (alive.size() == 1) {
            match.onEnd(alive.get(0));
            return true;
        }
        return false;
    }

    @Override
    public String turnUpdatedMessage(boolean over) {
        StringBuilder builder = new StringBuilder();
        if (!lastNotification.isEmpty()) builder.append(lastNotification).append("\n").append("\n");

        if (!over) builder.append(Language.transl(match.getLanguage(), "gameFramework.turn", getTurn().getUser()
                .map(User::getAsMention).orElseThrow(() -> new RuntimeException("Asked update message on AI turn."))));
        builder.append("\n");

        if (!over) {
            builder.append(Language.transl(match.getLanguage(), "game.roulette.chooseStrength", MIN_STRENGTH, MAX_STRENGTH)).append("\n");
            appendTurns(builder, playerItems);
        }

        return builder.toString();
    }

    @Override
    public void handleAIPlay() {

    }
}
