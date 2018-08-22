package me.deprilula28.gamesrob.games;

import me.deprilula28.gamesrob.Language;
import me.deprilula28.gamesrob.baseFramework.*;
import me.deprilula28.gamesrob.utility.Constants;
import me.deprilula28.gamesrob.utility.Log;
import me.deprilula28.gamesrob.utility.Utility;
import me.deprilula28.jdacmdframework.RequestPromise;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageReaction;
import net.dv8tion.jda.core.entities.User;

import javax.xml.ws.Provider;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TownCountryRiver extends TurnMatchHandler implements Runnable {
    private static String[] ITEMS;
    public static final GamesInstance GAME = new GamesInstance(
            "towncountryriver", "towncountryriver tcr tocori adedonha jogostop jogarstop playstop",
            1, 11, GameType.MULTIPLAYER, false,
            TownCountryRiver::new, TownCountryRiver.class, Collections.emptyList()
    );

    private static final String[] THEMES = {
            "name", "object", "animal", "adjective", "cep", "tv", "celebrity", "profession", "bodyPart", "company",
            "trademark", "discordBots", "meme", "anime", "movie", "youtuber", "game", "videoGame", "boardGame", "apps",
            "gender", "fortniteSkin"
    };
    private static final List<String> YES = Arrays.asList("yes", "good", "right", "correct", "y", "sim", "s", "1");
    private static final List<String> NO = Arrays.asList("no", "não", "bad", "wrong", "incorrect", "n", "0");
    private static final char[] LETTERS = "abcdefghiklmnrst".toCharArray();

    private ScheduledExecutorService executor;
    private Map<Optional<User>, String> playerItems = new HashMap<>();
    private Map<String, Optional<User>> itemPlayers = new HashMap<>();
    private Map<Optional<User>, Double> scoreboard = new HashMap<>();
    @Setting(min = 1, max = 20, defaultValue = 5) public int rounds;
    private Random random;
    private char letter;
    private String theme;
    private Optional<String> word = Optional.empty();
    private List<Optional<User>> rated = new ArrayList<>();
    private int rating;
    private int curRound = 0;

    private static final double BEST_SCORE = 10.0;

    private static void ensureLoaded() {
        List<Integer> numbers = new ArrayList<>();
        for (int i = 0; i < 12; i ++) numbers.add(i);
        ITEMS = numbers.stream().map(it -> GameUtil.getEmote("question" + it).get()).toArray(String[]::new);
    }

    private void resetRound() {
        theme = THEMES[random.nextInt(THEMES.length)];
        letter = LETTERS[random.nextInt(LETTERS.length)];
        rated.clear();
        rating = 0;
        word = Optional.empty();
        curRound ++;

        executor = Executors.newSingleThreadScheduledExecutor();
        executor.schedule(this, Constants.TCR_RESET_ROUND_TIME, TimeUnit.MILLISECONDS);
    }

    @Override
    public void run() {
        resetRound();
        nextTurn();
    }

    @Override
    public void begin(Match match, Provider<RequestPromise<Message>> initialMessage) {
        ensureLoaded();
        this.match = match;

        Utility.populateItems(match.getPlayers(), ITEMS, playerItems, new HashMap<>());
        random = GameUtil.generateRandom();

        resetRound();
        super.begin(match, initialMessage);
    }

    @Override
    public void receivedDM(String contents, User from, Message reference) { }

    @Override
    public void receivedReaction(User user, Message message, MessageReaction.ReactionEmote reaction) {
        if (reaction.getName() == null) return;
        if (reaction.getName().equals("✔")) rating ++;
        if (!reaction.getName().equals("❌")) return;

        rated.add(Optional.of(user));
        checkRoundOver();
    }

    @Override
    public void receivedMessage(String contents, User author, Message reference) {
        messages ++;
        if (word.isPresent() && match.getPlayers().contains(Optional.of(author))) {
            if (getTurn().map(it -> it.equals(author)).orElse(false)) return;
            if (YES.contains(contents.toLowerCase())) rating ++;
            else if (!NO.contains(contents.toLowerCase())) return;
            rated.add(Optional.of(author));
            checkRoundOver();
        } else getTurn().ifPresent(cur -> {
            if (cur != author) return;
            if (!contents.startsWith(String.valueOf(letter)) && !contents.startsWith(String.valueOf(Character.toUpperCase(letter)))) return;

            if (executor != null) executor.shutdownNow();
            word = Optional.of(contents
                    .replaceAll("@everyone", author.getAsMention())
                    .replaceAll("@here", "no this wont work either"));

            if (match.isCanReact()) {
                reference.addReaction("✔").queue();
                reference.addReaction("❌").queue();
            }
            MessageBuilder builder = new MessageBuilder();
            updatedMessage(false, builder);
            match.setMatchMessage(GameUtil.editSend(match.getChannelIn(), messages, match.getMatchMessage(), builder.build()));
            messages = 0;
        });
    }

    private void checkRoundOver() {
        if (rated.size() == match.getPlayers().size() - 1) {
            double score = BEST_SCORE * ((double) rating / (double) rated.size());
            scoreboard.put(getTurn(), scoreboard.containsKey(getTurn()) ? scoreboard.get(getTurn()) + score : score);

            if (!detectVictory()) {
                resetRound();
                nextTurn();
            }
        }
    }

    @Override
    public boolean detectVictory() {
        boolean end = curRound >= rounds;
        if (end) match.onEnd(scoreboard.entrySet().stream()
                .sorted(Comparator.comparingDouble(it -> (double) ((Map.Entry) it).getValue()).reversed())
                .map(Map.Entry::getKey).findFirst().orElse(Optional.empty()));
        return end;
    }

    @Override
    public String turnUpdatedMessage(boolean over) {
        StringBuilder builder = new StringBuilder();
        if (!over) {
            builder.append(Language.transl(match.getLanguage(), "gameFramework.turn", getTurn()
                    .map(User::getAsMention).orElseThrow(() -> new RuntimeException("Asked update message on AI turn."))));

            String themeStr = Language.transl(match.getLanguage(), "game.towncountryriver.themes." + theme);
            String letterStr = String.valueOf(letter);

            if (word.isPresent()) {
                String it = word.get();
                if (match.isCanReact())
                    builder.append(Language.transl(match.getLanguage(), "game.towncountryriver.reactionRate2",
                            getTurn().map(User::getAsMention).orElseThrow(() -> new RuntimeException("uh")),
                            themeStr));
                else builder.append(Language.transl(match.getLanguage(), "game.towncountryriver.rate",
                        themeStr, letterStr, it));
            } else builder.append(Language.transl(match.getLanguage(),
                    "game.towncountryriver.themeMessage2", themeStr, letterStr,
                    Utility.formatPeriod(Constants.TCR_RESET_ROUND_TIME)));

            appendTurns(builder, playerItems, player -> scoreboard.containsKey(player)
                    ? " (" + BigDecimal.valueOf(scoreboard.get(player)).setScale(0, BigDecimal.ROUND_HALF_UP).toString()
                    + " points)" : "");
        }

        return builder.toString();
    }

    private static final int AI_MAX_LAYERS = 8;

    @Override
    public void handleAIPlay() {    }
}
