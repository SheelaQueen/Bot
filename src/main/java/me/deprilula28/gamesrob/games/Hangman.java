package me.deprilula28.gamesrob.games;

import lombok.AllArgsConstructor;
import lombok.Data;
import me.deprilula28.gamesrob.Language;
import me.deprilula28.gamesrob.baseFramework.*;
import me.deprilula28.gamesrob.utility.Constants;
import me.deprilula28.gamesrob.utility.Log;
import me.deprilula28.jdacmdframework.RequestPromise;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.User;

import javax.xml.ws.Provider;
import java.util.*;
import java.util.stream.Collectors;

public class Hangman implements MatchHandler {
    public static final GamesInstance GAME = new GamesInstance(
            "hangman", "hangman hgm hm forca",
            1, 5, GameType.MULTIPLAYER, false,
            Hangman::new, Hangman.class, Collections.emptyList()
    );

    private static final String[] FACES = {
            "\uD83D\uDE15", "\uD83D\uDE26", "\uD83D\uDE27", "\uD83D\uDE23", "\uD83D\uDE35"
    };
    private static final List<String> BODY_PARTS = new ArrayList<>();
    private static boolean loaded = false;

    private static void ensureLoaded() {
        if (!loaded) {
            for (int i = 0; i <= 2; i ++) GameUtil.getEmote("torso" + i).ifPresent(BODY_PARTS::add);
            for (int i = 0; i <= 2; i ++) GameUtil.getEmote("bottom" + i).ifPresent(BODY_PARTS::add);
            loaded = true;
        }
    }

    @Setting(min = 1, max = 16, defaultValue = 4) public int tries;
    private Optional<String> word = Optional.empty();
    private List<Character> guessedLetters = new ArrayList<>();
    private Map<Optional<User>, PlayerInfo> playerInfoMap = new HashMap<>();
    private String lastNotification;
    private Match match;
    private int messages = 0;

    @Data
    @AllArgsConstructor
    public static class PlayerInfo {
        private int tries;

        private boolean hasLost() {
            return tries == 0;
        }
    }

    @Override
    public void begin(Match match, Provider<RequestPromise<Message>> initialMessage) {
        this.match = match;

        ensureLoaded();
        match.getPlayers().forEach(cur -> {
            playerInfoMap.put(cur, new PlayerInfo(tries));
            cur.ifPresent(player -> {
                if (player.equals(match.getCreator())) player.openPrivateChannel().queue(pm ->
                    pm.sendMessage("What's the word gonna be?").queue());
            });
        });
        match.setMatchMessage(initialMessage.invoke(null));
    }

    @Override
    public void onQuit(User user) {
    }

    @Override
    public void receivedMessage(String contents, User author, Message reference) {
        messages ++;
        word.ifPresent(theWord -> {
            if (!match.getPlayers().contains(Optional.of(author)) || match.getCreator().equals(author) ||
                    playerInfoMap.get(Optional.of(author)).hasLost() || contents.length() != 1 ||
                    contents.charAt(0) == ' ') return;
            char guess = Character.toLowerCase(contents.charAt(0));

            // Invalid guess
            if (theWord.toLowerCase().indexOf(guess) < 0) {
                PlayerInfo info = playerInfoMap.get(Optional.of(author));
                lastNotification = Language.transl(match.getLanguage(), "game.hangman.invalidGuess",
                        author.getAsMention(), guess);

                info.tries --;
                if (info.hasLost()) {
                    lastNotification = Language.transl(match.getLanguage(), "game.hangman.outOfLimbs",
                            author.getAsMention());
                }
            // Valid guess
            } else {
                guessedLetters.add(guess);
                lastNotification = Language.transl(match.getLanguage(), "game.hangman.validGuess",
                        author.getAsMention(), guess);
            }

            if (!detectVictory(author)) updateMessage();
        });
        match.interrupt();
    }

    @Override
    public void receivedDM(String contents, User from, Message reference) {
        if (!word.isPresent() && from.equals(match.getCreator())) {
            word = Optional.of(contents);
            reference.getChannel().sendMessage(Language.transl(match.getLanguage(), "game.hangman.wordSet",
                    match.getChannelIn().getAsMention())).queue();
            lastNotification = Language.transl(match.getLanguage(), "game.hangman.wordPicked");
            updateMessage();
        }
    }

    private void updateMessage() {
        MessageBuilder builder = new MessageBuilder();
        updatedMessage(false, builder);
        match.setMatchMessage(GameUtil.editSend(match.getChannelIn(), messages, match.getMatchMessage(), builder.build()));
        if (messages > Constants.MESSAGES_SINCE_THRESHOLD) messages = 0;
    }

    private boolean detectVictory(User user) {
        if (word.isPresent() && word.get().chars().mapToObj(c -> (char) c).allMatch(it ->
                guessedLetters.contains(Character.toLowerCase(it)) || !Character.isLetter(it)) ) {
            match.onEnd(Optional.of(user));
            return true;
        }
        if (match.getPlayers().stream().allMatch(it -> it.equals(Optional.of(match.getCreator()))
                || playerInfoMap.get(it).hasLost())) {
            match.onEnd(Optional.of(match.getCreator()));
            return true;
        }

        return false;
    }

    @Override
    public void updatedMessage(boolean over, MessageBuilder builder) {
        if (word.isPresent()) {
            builder.append(lastNotification).append("\n\n");
            if (!over) {
                for (Optional<User> curPlayer : match.getPlayers()) {
                    if (curPlayer.isPresent() && match.getCreator().equals(curPlayer.get())) continue;
                    PlayerInfo playerInfo = playerInfoMap.get(curPlayer);

                    // torso0 torso1 torso2 bottom0 bottom1 bottom2
                    // 0      1      2      3       4       5
                    if (match.getPlayers().size() > 2) builder.append(curPlayer.map(User::getAsMention).orElse("**AI**"));
                    builder.append(" \\_\\_\\_\\_\n")
                            .append("⏐          |\n")
                            .append("|       ").append(FACES[Math.min(FACES.length - playerInfo.tries, 0)]).append("\n")
                            .append("|       ").append(BODY_PARTS.get(Math.min(playerInfo.tries, 2))).append("\n")
                            .append("|       ").append(BODY_PARTS.get(Math.max(Math.min(playerInfo.tries, 5), 3))).append("\n")
                            .append("|       \n");
                }
            }
            builder.append("```\n");
            for (char cur : word.get().toCharArray())
                builder.append(cur == ' ' || guessedLetters.contains(Character.toLowerCase(cur)) || !Character.isLetter(cur)
                        || over ? String.valueOf(cur) : "_");

            builder.append("\n```");
        } else builder.append(over ? "" : Language.transl(match.getLanguage(), "game.hangman.sendWord", match.getCreator().getAsMention()));
    }
}
