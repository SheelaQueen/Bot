package me.deprilula28.gamesrob.games;

import me.deprilula28.gamesrob.Language;
import me.deprilula28.gamesrob.baseFramework.*;
import me.deprilula28.gamesrob.utility.Constants;
import me.deprilula28.gamesrob.utility.Utility;
import me.deprilula28.jdacmdframework.RequestPromise;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.User;

import javax.xml.ws.Provider;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class Detective implements MatchHandler {
    public static final GamesInstance GAME = new GamesInstance(
            "detective", "detective assassin clue murder murderer detetive det dt",
            3, 11, GameType.MULTIPLAYER, false,
            Detective::new, Detective.class
    );

    private Optional<User> killer = Optional.empty();
    private List<Optional<User>> alive = new ArrayList<>();
    private String lastNotification;
    private Match match;
    private boolean awake = false;
    private int days = 0;
    private int messages = 0;

    @Override
    public void begin(Match match, Provider<RequestPromise<Message>> initialMessage) {
        this.match = match;

        killer = match.getPlayers().get(ThreadLocalRandom.current().nextInt(match.getPlayers().size()));
        alive.addAll(match.getPlayers());
        match.getPlayers().stream().forEach(cur -> cur.ifPresent(user -> user.openPrivateChannel().queue(pm -> {
            if (killer.equals(cur)) pm.sendMessage(Language.transl(match.getLanguage(), "game.detective.dmAssassin")).queue();
            else pm.sendMessage(Language.transl(match.getLanguage(), "game.detective.dmCitizen")).queue();
        })));
        lastNotification = Language.transl(match.getLanguage(), "game.detective.sleep");
        match.setMatchMessage(initialMessage.invoke(null));
    }

    @Override
    public void onQuit(User user) {
    }

    @Override
    public void receivedMessage(String contents, User author, Message reference) {
        messages ++;
        if (awake && alive.contains(Optional.of(author)) && contents.length() == 1 && !voted.contains(Optional.of(author))) {
            int index = Utility.inputLetter(contents);
            if (index >= alive.size()) return;
            if (index < 0) return;

            voted.add(Optional.of(author));
            Optional<User> target = alive.get(index);
            votes.put(target, votes.containsKey(target) ? votes.get(target) + 1 : 1);

            if (voted.size() == alive.size()) {
                voted.clear();
                List<Map.Entry<Optional<User>, Integer>> orderedEntries = votes.entrySet().stream()
                        .sorted(Comparator.comparingInt(it -> ((Map.Entry<Optional<User>, Integer>) it).getValue())
                                .reversed()).collect(Collectors.toList());

                orderedEntries.stream().findFirst().ifPresent(cur -> {
                    int first = cur.getValue();
                    List<Optional<User>> firstPlaces = orderedEntries.stream().filter(it -> it.getValue() == first)
                            .map(Map.Entry::getKey).collect(Collectors.toList());

                    if (firstPlaces.size() > 1) lastNotification = Language.transl(match.getLanguage(), "game.detective.sleepIndecisive");
                    else {
                        Optional<User> kicked = cur.getKey();
                        if (killer.equals(kicked)) {
                            match.onEnd(Language.transl(match.getLanguage(), "game.detective.assassinKicked"), true);
                            return;
                        }
                        lastNotification = Language.transl(match.getLanguage(), "game.detective.sleepKicked", kicked.map(User::getAsMention).orElse("AI"));
                        alive.remove(kicked);
                    }

                    awake = false;
                    killer.ifPresent(user -> user.openPrivateChannel().queue(pm ->
                            pm.sendMessage(Language.transl(match.getLanguage(), "game.detective.dmAssassinRecursive")).queue()));
                    updateMessage();
                });
                votes.clear();
            } else match.getMatchMessage().then(it -> {
                MessageBuilder builder = new MessageBuilder();
                updatedMessage(false, builder);
                it.editMessage(builder.build()).queue();
            });
            match.interrupt();
        }
    }

    private List<Optional<User>> voted = new ArrayList<>();
    private Map<Optional<User>, Integer> votes = new HashMap<>();

    @Override
    public void receivedDM(String contents, User from, Message reference) {
        if (!awake && killer.isPresent() && killer.get().equals(from)) {
            Optional<User> userOpt = alive.stream().filter(it -> it.isPresent() && contents.equalsIgnoreCase(it.get().getName()))
                    .map(Optional::get).findFirst();

            if (userOpt.isPresent()) {
                User user = userOpt.get();
                if (user.equals(from)) return;

                alive.remove(Optional.of(user));
                if (alive.size() == 1) {
                    match.onEnd(killer);
                    return;
                }

                awake = true;
                days ++;

                lastNotification = Language.transl(match.getLanguage(),
                        days == 1 ? "game.detective.wakeFirst" :
                        days == 2 ? "game.detective.wakeSecond" :
                        days == 3 ? "game.detective.wakeThird" :
                        "game.detective.wakeFourthPlus", user.getAsMention());
                updateMessage();
            } else from.openPrivateChannel().queue(pm -> pm.sendMessage(Language.transl(match.getLanguage(),
                    "game.detective.invalidUser")).queue());
            match.interrupt();
        }
    }

    private void updateMessage() {
        MessageBuilder builder = new MessageBuilder();
        updatedMessage(false, builder);
        match.setMatchMessage(GameUtil.editSend(match.getChannelIn(), messages, match.getMatchMessage(), builder.build()));
        if (messages > Constants.MESSAGES_SINCE_THRESHOLD) messages = 0;
    }

    @Override
    public void updatedMessage(boolean over, MessageBuilder builder) {
        if (over) builder.append(Language.transl(match.getLanguage(), "game.detective.murderReveal", killer
                .map(User::getAsMention).orElse("AI")));
        else if (awake) builder.append(lastNotification + Language.transl(match.getLanguage(), "game.detective.chooseKick")
                + alive.stream().map(it -> Utility.getLetterEmote(alive.indexOf(it)) + it.map(User::getAsMention).orElse("AI")
                    + (votes.containsKey(it) ? " (" + votes.get(it) + " votes)" : ""))
                .collect(Collectors.joining("\n")));
        else builder.append(lastNotification);
    }
}
