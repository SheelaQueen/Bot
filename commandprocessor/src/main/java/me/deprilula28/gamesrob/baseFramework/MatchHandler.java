package me.deprilula28.gamesrob.baseFramework;

import me.deprilula28.jdacmdframework.RequestPromise;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageReaction;
import net.dv8tion.jda.core.entities.User;

import javax.xml.ws.Provider;

public interface MatchHandler {
    default void onQuit(User user) {};
    void begin(Match base, Provider<RequestPromise<Message>> initialMessage);
    void updatedMessage(boolean over, MessageBuilder builder);

    default void receivedDM(String contents, User from, Message reference) {};
    void receivedMessage(String contents, User author, Message reference);
    default void receivedReaction(User user, Message message, MessageReaction.ReactionEmote reaction) {};

    public static interface ImageMatchHandler extends MatchHandler {
        byte[] getImage();
    }
}
