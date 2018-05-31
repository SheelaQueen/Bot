package me.deprilula28.gamesrob.baseFramework;

import lombok.Getter;
import me.deprilula28.gamesrob.Language;
import me.deprilula28.gamesrob.utility.Constants;
import me.deprilula28.gamesrob.utility.Utility;
import me.deprilula28.jdacmdframework.RequestPromise;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.User;

import javax.xml.ws.Provider;
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

    public Optional<User> getTurn() {
        if (turn >= match.getPlayers().size()) turn = 0;
        return match.getPlayers().get(turn);
    }

    public void nextTurn() {
        match.interrupt();
        turn ++;
        if (turn >= match.getPlayers().size()) turn = 0;
        while (!getTurn().isPresent()) {
            handleAIPlay();
            turn ++;
            if (turn >= match.getPlayers().size()) turn = 0;
        }

        lastMessage.then(cur -> cur.delete().queue());
        lastMessage = RequestPromise.forAction(match.getChannelIn().sendMessage(updatedMessage(false)));
    }

    protected void appendTurns(StringBuilder builder, Map<Optional<User>, String> playerItems) {
        match.getPlayers().forEach(cur -> {
            if (playerItems != null) builder.append(playerItems.get(cur)).append(" ");
            builder.append(cur.map(User::getName).orElse("**AI**"));

            int pos = match.getPlayers().indexOf(cur) - turn;
            if (pos < 0) pos += match.getPlayers().size();

            if (pos == 0) builder.append(Language.transl(match.getLanguage(), "gameFramework.now")).append("\n");
            else if (pos == 1) builder.append(Language.transl(match.getLanguage(), "gameFramework.next")).append("\n");
            else builder.append(String.format(" - %s%s\n", pos, Utility.formatNth(match.getLanguage(), pos)));
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
}
