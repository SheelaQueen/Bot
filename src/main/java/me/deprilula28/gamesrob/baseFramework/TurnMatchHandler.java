package me.deprilula28.gamesrob.baseFramework;

import lombok.Getter;
import me.deprilula28.gamesrob.Language;
import me.deprilula28.gamesrob.utility.Constants;
import me.deprilula28.gamesrob.utility.Utility;
import me.deprilula28.jdacmdframework.RequestPromise;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.User;

import javax.xml.ws.Provider;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public abstract class TurnMatchHandler implements MatchHandler {
    @Getter protected RequestPromise<Message> lastMessage = null;
    protected int turn = 0;
    protected Match match;

    @Override
    public void begin(Match match, Provider<RequestPromise<Message>> initialMessage) {
        this.match = match;
        lastMessage = initialMessage.invoke(null);
    }

    public abstract void handleAIPlay();
    public abstract boolean detectVictory();
    protected int messages = 0;

    public List<Optional<User>> getPlayers() {
        return match.getPlayers();
    }

    public Optional<User> getTurn() {
        if (turn >= getPlayers().size()) turn = 0;
        return getPlayers().get(turn);
    }

    public void nextTurn() {
        match.interrupt();
        turn ++;
        if (turn >= getPlayers().size()) turn = 0;
        while (!getTurn().isPresent()) {
            handleAIPlay();
            turn ++;
            if (turn >= getPlayers().size()) turn = 0;
        }

        MessageBuilder builder = new MessageBuilder();
        updatedMessage(false, builder);
        lastMessage = GameUtil.editSend(match.getChannelIn(), messages, lastMessage, builder.build());
        if (messages > Constants.MESSAGES_SINCE_THRESHOLD) messages = 0;
    }

    protected void appendTurns(StringBuilder builder, Map<Optional<User>, String> playerItems) {
        match.getPlayers().forEach(cur -> {
            if (playerItems != null) builder.append(playerItems.get(cur)).append(" ");
            builder.append(cur.map(User::getName).orElse("**AI**"));

            if (getPlayers().contains(cur)) {
                int pos = getPlayers().indexOf(cur) - turn;
                if (pos < 0) pos += getPlayers().size();

                if (pos == 0) builder.append(Language.transl(match.getLanguage(), "gameFramework.now")).append("\n");
                else if (pos == 1)
                    builder.append(Language.transl(match.getLanguage(), "gameFramework.next")).append("\n");
                else builder.append(String.format(" - %s%s\n", pos, Utility.formatNth(match.getLanguage(), pos)));
            }
        });
    }

    @Override
    public void onQuit(User user) {
        getTurn().ifPresent(it -> {
            if (it == user) {
                nextTurn();
            }
        });
    }

    @Override
    public void updatedMessage(boolean over, MessageBuilder builder) {
        builder.append(turnUpdatedMessage(over));
    }

    public abstract String turnUpdatedMessage(boolean over);
}
