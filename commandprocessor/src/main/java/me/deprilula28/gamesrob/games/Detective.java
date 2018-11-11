package me.deprilula28.gamesrob.games;

import me.deprilula28.gamesrob.utility.Language;
import me.deprilula28.gamesrob.baseFramework.*;
import me.deprilula28.gamesrobshardcluster.utilities.Constants;
import me.deprilula28.gamesrob.utility.Utility;
import me.deprilula28.jdacmdframework.RequestPromise;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.User;

import javax.xml.ws.Provider;
import java.util.*;
import java.util.stream.Collectors;

public class Detective implements MatchHandler {
    public static final GamesInstance GAME = new GamesInstance(
            "detective", "detective assassin clue murder murderer detetive det dt",
            3, 11, GameType.MULTIPLAYER, false, false,
            Detective::new, Detective.class, Collections.emptyList()
    );

    private Player killer = null;
    private List<Player> alive = new ArrayList<>();
    private String lastNotification;
    private Match match;
    private boolean awake = false;
    private int days = 0;
    private int messages = 0;

    @Override
    public void begin(Match match, Provider<RequestPromise<Message>> initialMessage) {
        this.match = match;

        killer = match.getPlayers().get(GameUtil.generateRandom().nextInt(match.getPlayers().size()));
        alive.addAll(match.getPlayers());
        match.getPlayers().forEach(cur -> cur.getUser().ifPresent(user -> user.openPrivateChannel().queue(pm -> {
            if (killer.equals(cur)) pm.sendMessage(Language.transl(match.getLanguage(), "game.detective.dmAssassin2",
                    getOptionsKillerMessage())).queue();
            else pm.sendMessage(Language.transl(match.getLanguage(), "game.detective.dmCitizen")).queue();
        })));
        lastNotification = Language.transl(match.getLanguage(), "game.detective.sleep");
        match.setMatchMessage(initialMessage.invoke(null));
    }

    @Override
    public void onQuit(User user) {
        if (Player.user(user).equals(killer))
            match.onEnd(Language.transl(match.getLanguage(), "game.detective.assassinKicked"), true);
        else {
            alive.remove(Player.user(user));
            updateMessage();
        }
    }

    @Override
    public void receivedMessage(String contents, User author, Message reference) {
        messages ++;
        if (awake && alive.contains(Player.user(author)) && contents.length() == 1 && !voted.contains(Player.user(author))) {
            int index = Utility.inputLetter(contents);
            if (index >= alive.size()) return;
            if (index < 0) return;

            voted.add(Player.user(author));
            Player target = alive.get(index);
            votes.put(target, votes.containsKey(target) ? votes.get(target) + 1 : 1);

            if (voted.size() == alive.size()) {
                voted.clear();
                List<Map.Entry<Player, Integer>> orderedEntries = votes.entrySet().stream()
                        .sorted(Comparator.comparingInt(it -> ((Map.Entry<Player, Integer>) it).getValue())
                                .reversed()).collect(Collectors.toList());

                orderedEntries.stream().findFirst().ifPresent(cur -> {
                    int first = cur.getValue();
                    List<Player> firstPlaces = orderedEntries.stream().filter(it -> it.getValue() == first)
                            .map(Map.Entry::getKey).collect(Collectors.toList());

                    if (firstPlaces.size() > 1) lastNotification = Language.transl(match.getLanguage(), "game.detective.sleepIndecisive");
                    else {
                        Player kicked = cur.getKey();
                        if (killer.equals(kicked)) {
                            match.onEnd(Language.transl(match.getLanguage(), "game.detective.assassinKicked"), true);
                            return;
                        }
                        lastNotification = Language.transl(match.getLanguage(), "game.detective.sleepKicked", kicked.toString());
                        alive.remove(kicked);
                    }

                    awake = false;
                    killer.getUser().ifPresent(user -> user.openPrivateChannel().queue(pm ->
                            pm.sendMessage(Language.transl(match.getLanguage(), "game.detective.dmAssassinRecursive2",
                                    getOptionsKillerMessage())).queue()));
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

    private String getOptionsKillerMessage() {
        return alive.stream().map(it -> Utility.getLetterEmote(alive.indexOf(it)) + it.toString())
                .collect(Collectors.joining("\n"));
    }

    private List<Player> voted = new ArrayList<>();
    private Map<Player, Integer> votes = new HashMap<>();

    @Override
    public void receivedDM(String contents, User from, Message reference) {
        if (!awake && killer.getUser().filter(it -> it.equals(from)).isPresent()) {
            if (contents.length() != 1) return;
            int numb = Utility.inputLetter(contents);
            if (numb >= alive.size() || numb < 0) return;

            Player player = alive.get(numb);
            if (player.equals(Player.user(from))) return;

            alive.remove(player);
            if (alive.size() <= 2) {
                match.onEnd(killer);
                return;
            }

            awake = true;
            days ++;

            lastNotification = Language.transl(match.getLanguage(),
                    days == 1 ? "game.detective.wakeFirst" :
                    days == 2 ? "game.detective.wakeSecond" :
                    days == 3 ? "game.detective.wakeThird" :
                    "game.detective.wakeFourthPlus", player.toString());
            updateMessage();
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
        if (over) builder.append(Language.transl(match.getLanguage(), "game.detective.murderReveal", killer.toString()));
        else if (awake) builder.append(lastNotification + Language.transl(match.getLanguage(), "game.detective.chooseKick")
                + alive.stream().map(it -> Utility.getLetterEmote(alive.indexOf(it)) + it.toString()
                    + (votes.containsKey(it) ? " (" + votes.get(it) + " votes)" : ""))
                .collect(Collectors.joining("\n")));
        else builder.append(lastNotification);
    }
}
