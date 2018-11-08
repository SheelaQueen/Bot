package me.deprilula28.gamesrob.games;

import com.github.kevinsawicki.http.HttpRequest;
import com.google.gson.annotations.SerializedName;
import me.deprilula28.gamesrob.Language;
import lombok.Data;
import me.deprilula28.gamesrob.baseFramework.*;
import me.deprilula28.gamesrobshardcluster.utilities.Constants;
import me.deprilula28.gamesrobshardcluster.utilities.Log;
import me.deprilula28.gamesrob.utility.Utility;
import me.deprilula28.jdacmdframework.RequestPromise;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.exceptions.PermissionException;
import org.unbescape.html.HtmlEscape;

import javax.xml.ws.Provider;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Quiz implements MatchHandler {
    private static String[] ITEMS;
    public static final GamesInstance GAME = new GamesInstance(
            "quiz", "quiz trivia quizzes qz",
            0, 11, GameType.COLLECTIVE, false, false,
            Quiz::new, Quiz.class, Collections.emptyList()
    );

    private static void ensureLoaded() {
        List<Integer> numbers = new ArrayList<>();
        for (int i = 0; i < 12; i ++) numbers.add(i);
        ITEMS = numbers.stream().map(it -> GameUtil.getEmote("question" + it).get()).toArray(String[]::new);
    }

    @Setting(min = 1, max = 20, defaultValue = 5) public int rounds;
    private static final double MAX_TIME = (double) TimeUnit.MINUTES.toMillis(5);
    private static final double MAX_SCORE = 30.0;
    private static final double MIN_SCORE = 10.0;
    private static final double MEDIUM_MULTIPLIER = 1.25;
    private static final double HARD_MULTIPLIER = 1.5;

    private Match match;

    private Map<Player, String> playerItems = new HashMap<>();
    private Map<String, Player> itemPlayers = new HashMap<>();
    private Map<Player, Double> scoreboard = new HashMap<>();
    private Map<Player, Integer> attempts = new HashMap<>();

    private Optional<String> lastNotification = Optional.empty();
    private OpenTDBResponse.QuizQuestion curQuestion;
    private List<String> orderedOptions;
    private int messages;
    private int roundsDone = 0;
    private long questionAsked;

    @Data
    public static class OpenTDBResponse {
        @SerializedName("response_code") private int responseCode;
        private List<QuizQuestion> results;

        @Data
        public static class QuizQuestion {
            private String category;
            private String type;
            private String difficulty;
            private String question;
            @SerializedName("correct_answer") private String correctAnswer;
            @SerializedName("incorrect_answers") private List<String> incorrectAnswers;
        }
    }
    private static Map<String, BlockingQueue<OpenTDBResponse.QuizQuestion>> urlMap = new HashMap<>();

    private static OpenTDBResponse request(String url, int amount) {
        return Constants.GSON.fromJson(HttpRequest.get(url + "&amount=" + amount).body(), OpenTDBResponse.class);
    }

    public static OpenTDBResponse.QuizQuestion getQuestion(String url) {
        if (urlMap.containsKey(url)) {
            BlockingQueue<OpenTDBResponse.QuizQuestion> queue = urlMap.get(url);
            if (queue.size() < 10) Utility.Promise.provider(() -> request(url, 50)).then(it ->
                queue.addAll(it.getResults()));

            OpenTDBResponse.QuizQuestion question = queue.poll();
            if (question == null) return request(url, 1).getResults().get(0);

            return question;
        } else {
            BlockingQueue<OpenTDBResponse.QuizQuestion> queue = new LinkedBlockingQueue<>();
            queue.addAll(request(url, 50).getResults());
            urlMap.put(url, queue);

            return queue.poll();
        }
    }

    private void newQuizQuestion() {
        double percentDone = (double) roundsDone / (double) rounds;
        curQuestion = getQuestion("https://opentdb.com/api.php?difficulty=" + (
            percentDone >= 2.0 / 3.0 ? "hard" :
            percentDone >= 1.0 / 3.0 ? "medium" :
            "easy"
        ));
        questionAsked = System.currentTimeMillis();

        List<String> options = curQuestion.getIncorrectAnswers();
        options.add(curQuestion.getCorrectAnswer());
        orderedOptions = options.stream().sorted(Comparator.comparingInt(a -> new Random(a.hashCode()).nextInt()))
                .collect(Collectors.toList());
        roundsDone ++;
    }

    @Override
    public void begin(Match match, Provider<RequestPromise<Message>> initialMessage) {
        ensureLoaded();
        this.match = match;
        Utility.populateItems(match.getPlayers(), ITEMS, playerItems, new HashMap<>());

        Log.wrapException("Failed to get quiz question", this::newQuizQuestion);
        match.setMatchMessage(initialMessage.invoke(null));
    }

    @Override
    public void receivedMessage(String contents, User author, Message reference) {
        messages ++;
        if (contents.length() == 1) {
            int letter = Utility.inputLetter(contents);
            if (letter < 0 || letter >= orderedOptions.size()) return;
            if (!match.getPlayers().contains(Player.user(author))) match.joined(Player.user(author));

            Player player = Player.user(author);
            if (attempts.containsKey(player) && attempts.get(player) >= orderedOptions.size() / 2) {
                try {
                    reference.addReaction("⛔").queue();
                } catch (PermissionException e) {
                    Log.warn("Not enough perm to add reaction!");
                }
                return;
            }

            String option = orderedOptions.get(letter);
            if (curQuestion.getCorrectAnswer().equals(option)) {
                double score = MIN_SCORE + ((1.0 - (double) (System.currentTimeMillis() - questionAsked)) / MAX_TIME) * (MAX_SCORE - MIN_SCORE);
                if (curQuestion.difficulty.equals("hard")) score *= HARD_MULTIPLIER;
                else if (curQuestion.difficulty.equals("medium")) score *= MEDIUM_MULTIPLIER;

                scoreboard.put(player, scoreboard.containsKey(player) ? scoreboard.get(player) + score : score);

                if (roundsDone >= rounds) onEnd();
                else {
                    attempts.clear();
                    Log.wrapException("Failed to get quiz question", this::newQuizQuestion);
                    lastNotification = Optional.of(Language.transl(match.getLanguage(), "game.quiz.answer",
                            author.getAsMention(), Math.round(score)) + "\n");
                    updateMessage();
                }
            } else {
                attempts.put(player, attempts.containsKey(player) ? attempts.get(player) + 1 : 1);
                if (attempts.get(player) >= orderedOptions.size() / 2) {
                    if (match.getPlayers().stream().allMatch(it -> attempts.containsKey(it) && attempts.get(it) >= orderedOptions.size() / 2)) {
                        if (roundsDone >= rounds) onEnd();
                        else {
                            attempts.clear();
                            lastNotification = Optional.of(Language.transl(match.getLanguage(), "game.quiz.noOneAnswered",
                                    curQuestion.getCorrectAnswer()) + "\n");
                            Log.wrapException("Failed to get quiz question", this::newQuizQuestion);
                            updateMessage();
                        }
                    } else match.getChannelIn().sendMessage(Language.transl(match.getLanguage(),
                            "game.quiz.cantPlay", player.toString())).queue();
                } else {
                    try {
                        reference.addReaction("❌").queue();
                    } catch (PermissionException e) {
                        Log.warn("Not enough perm to add reaction!");
                    }
                }
            }
        }
    }

    private void onEnd() {
        if (match.getPlayers().size() == 1) match.onEnd(scoreboard.containsKey(match.getPlayers().get(0))
                ? (int) Math.round(scoreboard.get(match.getPlayers().get(0))) : 0);
        else match.onEnd(scoreboard.entrySet().stream()
                .sorted(Comparator.comparingDouble(it -> (double) ((Map.Entry) it).getValue()).reversed())
                .map(Map.Entry::getKey).findFirst().orElse(null));
    }

    private void updateMessage() {
        MessageBuilder builder = new MessageBuilder();
        updatedMessage(false, builder);
        match.setMatchMessage(GameUtil.editSend(match.getChannelIn(), messages, match.getMatchMessage(), builder.build()));
        if (messages > Constants.MESSAGES_SINCE_THRESHOLD) messages = 0;
    }

    @Override
    public void updatedMessage(boolean over, MessageBuilder builder) {
        String decodedQuestion = HtmlEscape.unescapeHtml(curQuestion.getQuestion());
        if (over) builder.append(Language.transl(match.getLanguage(), "game.quiz.revealAnswer", decodedQuestion,
                curQuestion.getCorrectAnswer()));
        else {
            lastNotification.ifPresent(builder::append);
            builder.append(String.format("<:qmgreenupsidedown:456945984736460801> **%s/%s**\n%s *- %s*\n\n%s\n%s\n",
                    roundsDone, rounds,
                    curQuestion.getCategory(), curQuestion.getDifficulty(),
                    decodedQuestion.replaceAll("\\?", "<:qmgreen:456945984656900096>"),
                    orderedOptions.stream().map(it -> Utility.getLetterEmote(orderedOptions.indexOf(it)) + " " +
                            HtmlEscape.unescapeHtml(it)).collect(Collectors.joining("\n"))));
        }
        GameUtil.appendPlayersScore(playerItems, scoreboard, over, builder);
    }
}
