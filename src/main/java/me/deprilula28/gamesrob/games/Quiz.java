package me.deprilula28.gamesrob.games;

import com.github.kevinsawicki.http.HttpRequest;
import com.google.gson.annotations.SerializedName;
import jdk.nashorn.internal.ir.Block;
import lombok.Data;
import me.deprilula28.gamesrob.Language;
import me.deprilula28.gamesrob.baseFramework.*;
import me.deprilula28.gamesrob.utility.Constants;
import me.deprilula28.gamesrob.utility.Log;
import me.deprilula28.gamesrob.utility.Utility;
import me.deprilula28.jdacmdframework.RequestPromise;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.User;

import javax.xml.ws.Provider;
import java.net.URL;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Quiz implements MatchHandler {
    private static String[] ITEMS;
    public static final GamesInstance GAME = new GamesInstance(
            "quiz", "quiz trivia quizzes",
            1, 11, GameType.MULTIPLAYER, false,
            Quiz::new, Quiz.class
    );

    private static void ensureLoaded() {
        List<Integer> numbers = new ArrayList<>();
        for (int i = 0; i < 12; i ++) numbers.add(i);
        ITEMS = numbers.stream().map(it -> GameUtil.getEmote("question" + it).get()).toArray(String[]::new);
    }

    @Setting(min = 1, max = 20, defaultValue = 5) public int rounds;
    private static final long MAX_TIME = TimeUnit.MINUTES.toMillis(5);
    private static final int MAX_SCORE = 100;
    private static final int MIN_SCORE = 50;

    private Match match;

    private Map<Optional<User>, String> playerItems = new HashMap<>();
    private Map<String, Optional<User>> itemPlayers = new HashMap<>();
    private Map<Optional<User>, Double> scoreboard = new HashMap<>();
    private Map<Optional<User>, Integer> attempts = new HashMap<>();

    private Optional<String> lastNotification = Optional.empty();
    private OpenTDBResponse.QuizQuestion curQuestion;
    private RequestPromise<Message> lastMessage = null;
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
        lastMessage = initialMessage.invoke(null);
    }

    @Override
    public void receivedMessage(String contents, User author, Message reference) {
        messages ++;
        if (contents.length() == 1) {
            int letter = Utility.inputLetter(contents);
            if (letter < 0 || letter >= orderedOptions.size()) return;

            Optional<User> player = Optional.of(author);
            if (attempts.containsKey(player) && attempts.get(player) >= orderedOptions.size() / 2) {
                reference.addReaction("⛔").queue();
                return;
            }

            String option = orderedOptions.get(letter);
            if (curQuestion.getCorrectAnswer().equals(option)) {
                double score = Math.min(1 - ((double) (System.currentTimeMillis() - questionAsked) / (double) MAX_TIME) * MAX_SCORE, MIN_SCORE);
                scoreboard.put(player, scoreboard.containsKey(player) ? scoreboard.get(player) + score : score);

                if (roundsDone == rounds) {
                    match.onEnd(scoreboard.entrySet().stream()
                            .sorted(Comparator.comparingDouble(it -> (double) ((Map.Entry) it).getValue()).reversed())
                            .map(Map.Entry::getKey).findFirst().orElse(Optional.empty()));
                } else {
                    attempts.clear();
                    Log.wrapException("Failed to get quiz question", this::newQuizQuestion);
                    lastNotification = Optional.of(Language.transl(match.getLanguage(), "game.quiz.answer", author.getAsMention(), score) + "\n");
                    updateMessage();
                }
                reference.addReaction("<:check:314349398811475968>").queue();
            } else {
                attempts.put(player, attempts.containsKey(player) ? attempts.get(player) + 1 : 1);
                if (attempts.get(player) >= orderedOptions.size() / 2) {
                    if (match.getPlayers().stream().allMatch(it -> attempts.containsKey(it) && attempts.get(it) >= orderedOptions.size() / 2)) {
                        attempts.clear();
                        lastNotification = Optional.of(Language.transl(match.getLanguage(), "game.quiz.noOneAnswered",
                                curQuestion.getCorrectAnswer()) + "\n");
                        Log.wrapException("Failed to get quiz question", this::newQuizQuestion);
                        updateMessage();
                    } else match.getChannelIn().sendMessage(Language.transl(match.getLanguage(),
                            "game.quiz.cantPlay", player.map(User::getAsMention).orElse("**AI**"))).queue();
                } else reference.addReaction("<:xmark:314349398824058880>").queue();
            }
        }
    }

    @Override
    public void receivedDM(String contents, User from, Message reference) { }

    @Override
    public void onQuit(User user) { }

    private void updateMessage() {
        MessageBuilder builder = new MessageBuilder();
        updatedMessage(false, builder);
        lastMessage = GameUtil.editSend(match.getChannelIn(), messages, lastMessage, builder.build());
        if (messages > Constants.MESSAGES_SINCE_THRESHOLD) messages = 0;
    }

    @Override
    public void updatedMessage(boolean over, MessageBuilder builder) {
        String decodedQuestion = curQuestion.getQuestion().replaceAll("&quot;", "\"").replaceAll("&#039;", "'");
        if (over) builder.append(Language.transl(match.getLanguage(), "game.quiz.revealAnswer", decodedQuestion,
                curQuestion.getCorrectAnswer()));
        else {
            lastNotification.ifPresent(builder::append);
            builder.append(String.format("<:qmgreenupsidedown:456945984736460801> **%s/%s**\n%s *- %s*\n\n%s\n%s\n",
                    roundsDone, rounds,
                    curQuestion.getCategory(), curQuestion.getDifficulty(),
                    decodedQuestion.replaceAll("\\?", "<:qmgreen:456945984656900096>"),
                    orderedOptions.stream().map(it -> Utility.getLetterEmote(orderedOptions.indexOf(it)) + " " + it)
                            .collect(Collectors.joining("\n"))));
        }
        playerItems.forEach((player, item) -> {
            boolean contains = scoreboard.containsKey(player);
            if (!over || contains) builder.append("\n").append(item).append(" ").append(player.map(User::getName).orElse("**AI**"));
            if (contains) {
                long score = Math.round(scoreboard.get(player));
                builder.append(" (").append(score).append(" points)");
            }
        });
    }
}
