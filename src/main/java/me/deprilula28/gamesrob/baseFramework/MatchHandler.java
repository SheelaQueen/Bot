package me.deprilula28.gamesrob.baseFramework;

import me.deprilula28.jdacmdframework.RequestPromise;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.User;

import javax.xml.ws.Provider;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

public interface MatchHandler {
    void onQuit(User user);
    void begin(Match base, Provider<RequestPromise<Message>> initialMessage);
    void updatedMessage(boolean over, MessageBuilder builder);

    void receivedDM(String contents, User from, Message reference);
    void receivedMessage(String contents, User author, Message reference);

    public static interface ImageMatchHandler extends MatchHandler {
        byte[] getImage();
    }
}
