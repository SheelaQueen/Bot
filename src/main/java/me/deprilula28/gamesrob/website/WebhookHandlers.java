package me.deprilula28.gamesrob.website;

import com.google.gson.JsonObject;
import me.deprilula28.gamesrob.BootupProcedure;
import me.deprilula28.gamesrob.GamesROB;
import me.deprilula28.gamesrob.Language;
import me.deprilula28.gamesrob.data.Statistics;
import me.deprilula28.gamesrob.data.UserProfile;
import me.deprilula28.gamesrob.utility.Constants;
import me.deprilula28.gamesrob.utility.Log;
import net.dv8tion.jda.core.entities.Game;
import org.eclipse.jetty.http.HttpStatus;
import spark.Request;
import spark.Response;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class WebhookHandlers {
    public static boolean hasConfirmed = false;

    private static class Notification {
        private List<JsonObject> data;
    }

    /*
    public static Object postTwitch(Request request, Response response) throws Exception {
        Log.trace("Received Twitch POST notification.");
        Notification notification = Constants.GSON.fromJson(request.body(), Notification.class);
        if (notification.data.size() == 0)  {
            Log.trace("Stream has ended.");
            GamesROB.twitchPresence = false;
            GamesROB.setPresence();
        } else {
            Log.trace("Stream has returned.");
            GamesROB.twitchPresence = true;
            JsonObject stream = notification.data.get(0);

            GamesROB.shards.forEach(jda -> jda.getPresence().setGame(Game.streaming(stream.get("title").getAsString(),
                    streamURL)));
        }
        return "";
    }

    public static Object getTwitch(Request request, Response response) throws Exception {
        if (hasConfirmed) {
            if ("denied".equals(request.queryParams("hub.mode"))) {
                Log.warn("Twitch webhook subscription request was denied for: ", request.queryParams("hub.reason"));
                hasConfirmed = false;
            }
            return null;
        } else {
            hasConfirmed = true;
            Log.info("Subscribed to Twitch.");
            return request.queryParams("hub.challenge");
        }
    }
    */

    private static class DBLUpvote {
        private long bot;
        private long user;
        private String test;
    }

    public static Object dblWebhook(Request request, Response response) throws Exception {
        if (!BootupProcedure.optDblToken.get().equals(request.headers("Authorization"))) {
            response.status(HttpStatus.UNAUTHORIZED_401);
            return null;
        }
        Statistics.get().setUpvotes(Statistics.get().getUpvotes() + 1);

        DBLUpvote upvote = Constants.GSON.fromJson(request.body(), DBLUpvote.class);
        GamesROB.getUserById(upvote.user).ifPresent(user -> {
            user.openPrivateChannel().queue(pm -> {
                UserProfile profile = UserProfile.get(user.getId());
                if (System.currentTimeMillis() - profile.getLastUpvote() < TimeUnit.DAYS.toMillis(2))
                    profile.setUpvotedDays(profile.getUpvotedDays() + 1);
                else profile.setUpvotedDays(0);
                int days = profile.getUpvotedDays();
                int amount = 125 + days * 50;

                pm.sendMessage(Language.transl(Optional.ofNullable(profile.getLanguage()).orElse("en_US"),
                        "genericMessages.upvoteMessage", "+" + amount + " \uD83D\uDD38 tokens", days)).queue();
                profile.addTokens(amount);
                profile.setLastUpvote(System.currentTimeMillis());
            });
        });
        return "";
    }
}
