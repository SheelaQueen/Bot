package me.deprilula28.gamesrob.games;

import me.deprilula28.gamesrob.utility.Language;
import lombok.AllArgsConstructor;
import lombok.Data;
import me.deprilula28.gamesrob.baseFramework.*;
import me.deprilula28.gamesrobshardcluster.utilities.Constants;
import me.deprilula28.jdacmdframework.RequestPromise;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.User;

import javax.xml.ws.Provider;
import java.util.*;

public class Hangman implements MatchHandler {
    public static final GamesInstance GAME = new GamesInstance(
            "hangman", "hangman hgm hm forca",
            1, 5, GameType.MULTIPLAYER, false, false,
            Hangman::new, Hangman.class, Collections.emptyList()
    );

    private static final int WORD_SIZE_LIMIT = 25;

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
    private Map<Player, PlayerInfo> playerInfoMap = new HashMap<>();
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
        match.getCreator().openPrivateChannel().queue(pm -> pm.sendMessage(Language.transl(match.getLanguage(), "game.hangman.sendWordDM")).queue());
        match.getPlayers().forEach(cur -> playerInfoMap.put(cur, new PlayerInfo(tries)));
        match.setMatchMessage(initialMessage.invoke(null));
    }

    @Override
    public void onQuit(User user) {
        playerInfoMap.remove(Player.user(user));
    }

    @Override
    public void receivedMessage(String contents, User author, Message reference) {
        messages ++;
        word.ifPresent(theWord -> {
            if (!match.getPlayers().contains(Player.user(author)) || match.getCreator().equals(author) ||
                    playerInfoMap.get(Player.user(author)).hasLost()) return;
            if (contents.equalsIgnoreCase(theWord)) {
                match.onEnd(Player.user(author));
                return;
            } else if (contents.length() != 1 || contents.charAt(0) == ' ') return;
            char guess = Character.toLowerCase(contents.charAt(0));

            // Invalid guess
            if (theWord.toLowerCase().indexOf(guess) < 0) {
                PlayerInfo info = playerInfoMap.get(Player.user(author));
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
            if (contents.length() > WORD_SIZE_LIMIT) {
                reference.getChannel().sendMessage(Language.transl(match.getLanguage(), "game.hangman.wordTooBig",
                        WORD_SIZE_LIMIT)).queue();
                return;
            }

            word = Optional.of(contents.replaceAll("`", ""));
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
            match.onEnd(Player.user(user));
            return true;
        }
        if (match.getPlayers().stream().allMatch(it -> it.equals(Player.user(match.getCreator()))
                || playerInfoMap.get(it).hasLost())) {
            match.onEnd(Player.user(match.getCreator()));
            return true;
        }

        return false;
    }

    @Override
    public void updatedMessage(boolean over, MessageBuilder builder) {
        if (word.isPresent()) {
            builder.append(lastNotification).append("\n\n");
            if (!over) {
                for (Player curPlayer : match.getPlayers()) {
                    if (curPlayer.getUser().isPresent() && match.getCreator().equals(curPlayer.getUser().get())) continue;
                    PlayerInfo playerInfo = playerInfoMap.get(curPlayer);

                    // torso0 torso1 torso2 bottom0 bottom1 bottom2
                    // 0      1      2      3       4       5
                    if (match.getPlayers().size() > 2) builder.append(curPlayer.toString());
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
