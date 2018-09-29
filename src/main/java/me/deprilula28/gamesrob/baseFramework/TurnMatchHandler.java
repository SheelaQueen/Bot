package me.deprilula28.gamesrob.baseFramework;

import lombok.Getter;
import me.deprilula28.gamesrob.Language;
import me.deprilula28.gamesrob.utility.Constants;
import me.deprilula28.gamesrob.utility.Log;
import me.deprilula28.gamesrob.utility.Utility;
import me.deprilula28.jdacmdframework.RequestPromise;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.User;

import javax.xml.ws.Provider;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public abstract class TurnMatchHandler implements MatchHandler {
    protected int turn = 0;
    protected Match match;

    @Override
    public void begin(Match match, Provider<RequestPromise<Message>> initialMessage) {
        this.match = match;
        match.setMatchMessage(initialMessage.invoke(null));
    }

    public void handleAIPlay() {};
    public abstract boolean detectVictory();
    protected int messages = 0;

    public List<Player> getPlayers() {
        return match.getPlayers();
    }

    public Player getTurn() {
        if (turn >= getPlayers().size()) turn = 0;
        return getPlayers().get(turn);
    }

    public Player seekTurn() {
        return getPlayers().get(turn + 1 >= getPlayers().size() ? 0 : turn + 1);
    }

    public void nextTurn() {
        match.interrupt();
        turn ++;
        if (turn >= getPlayers().size()) turn = 0;
        while (isInvalidTurn()) {
            handleInvalidTurn();
            if (match.getGameState().equals(GameState.POST_MATCH)) return;
            turn ++;
            if (turn >= getPlayers().size()) turn = 0;
        }

        MessageBuilder builder = new MessageBuilder();
        updatedMessage(false, builder);
        match.setMatchMessage(GameUtil.editSend(match.getChannelIn(), messages, match.getMatchMessage(), builder.build()));
        if (messages > Constants.MESSAGES_SINCE_THRESHOLD) messages = 0;

    }

    protected boolean isInvalidTurn() {
        return !getTurn().getUser().isPresent();
    }

    protected void handleInvalidTurn() {
        handleAIPlay();
    }

    protected void appendTurns(StringBuilder builder, Map<Player, String> playerItems) {
        appendTurns(builder, playerItems, n -> "");
    }

    protected void appendTurns(StringBuilder builder, Map<Player, String> playerItems, Function<Player, String> messageGetter) {
        Guild guild = match.getChannelIn().getGuild();
        match.getPlayers().forEach(cur -> {
            if (playerItems != null) builder.append(playerItems.get(cur)).append(" ");
            String name = cur.toString();
            builder.append(name).append(messageGetter.apply(cur));

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
        if (getTurn().getUser().filter(it -> it.equals(user)).isPresent()) nextTurn();
        MessageBuilder builder = new MessageBuilder();
        updatedMessage(false, builder);
        match.setMatchMessage(GameUtil.editSend(match.getChannelIn(), messages, match.getMatchMessage(), builder.build()));
        if (messages > Constants.MESSAGES_SINCE_THRESHOLD) messages = 0;
    }

    @Override
    public void updatedMessage(boolean over, MessageBuilder builder) {
        builder.append(turnUpdatedMessage(over));
    }

    public abstract String turnUpdatedMessage(boolean over);
}
