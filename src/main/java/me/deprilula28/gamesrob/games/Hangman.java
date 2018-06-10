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
            Hangman::new, Hangman.class
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
    private RequestPromise<Message> lastMessage = null;
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
        lastMessage = initialMessage.invoke(null);
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
        lastMessage = GameUtil.editSend(match.getChannelIn(), messages, lastMessage, builder.build());
        if (messages > Constants.MESSAGES_SINCE_THRESHOLD) messages = 0;
    }

    public boolean detectVictory(User user) {
        if (word.isPresent() && word.get().chars().mapToObj(c -> (char) c).allMatch(it ->
                guessedLetters.contains(Character.toLowerCase(it)) || it == ' ')) {
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

                    List<StringBuilder> lines = new ArrayList<>();
                    String[] curLines = {
                            "`" + curPlayer.map(User::getName).orElse("AI") + "`",
                            " \\_\\_\\_\\_",
                            "⏐          ",
                            "⏐       ", //+ FACES[5 - (playerInfo.bottom + playerInfo.torso + 1)],
                            "⏐       ", //+ BODY_PARTS.getTwitch(playerInfo.torso),
                            "⏐       ", //+ BODY_PARTS.getTwitch(playerInfo.bottom + 3),
                            "⏐"
                    };
                    for (int excessLeft = 0; excessLeft < playerInfo.tries / 5; excessLeft ++) {
                        curLines[0] += "\\_";
                        curLines[1] += "|";
                        curLines[2] += FACES[0];
                        curLines[3] += BODY_PARTS.get(2);
                        curLines[4] += BODY_PARTS.get(5);
                    }
                    int triesMod = playerInfo.tries % 5;
                    Log.trace(playerInfo.tries, triesMod, Math.min(triesMod, 2), Math.max(triesMod + 1, 3));
                    curLines[1] += "\\_";
                    curLines[2] += "|";
                    curLines[3] += FACES[FACES.length - triesMod];
                    curLines[4] += BODY_PARTS.get(Math.min(triesMod, 2));
                    curLines[5] += BODY_PARTS.get(Math.max(triesMod + 1, 3));

                    int max = Arrays.stream(curLines).mapToInt(String::length).max().orElse(0);

                    for (int i = 0; i < curLines.length; i++) {
                        if (i == lines.size()) lines.add(new StringBuilder());
                        StringBuilder lineBuilder = lines.get(i);
                        String line = curLines[i];

                        lineBuilder.append(line);
                        for (int padding = -2; padding < max - line.length(); padding ++) lineBuilder.append(" ");
                    }

                    builder.append(lines.stream().map(Object::toString).collect(Collectors.joining("\n"))).append("\n");
                }
            }
            builder.append("```\n");
            for (char cur : word.get().toCharArray()){
                builder.append(cur == ' ' || guessedLetters.contains(Character.toLowerCase(cur)) || over ? String.valueOf(cur) : "_");
            }

            builder.append("\n```");
        } builder.append(over ? "" : Language.transl(match.getLanguage(), "game.hangman.sendWord", match.getCreator().getAsMention()));
    }
}
