package me.deprilula28.gamesrob.website;

import com.github.kevinsawicki.http.HttpRequest;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import me.deprilula28.gamesrob.BootupProcedure;
import me.deprilula28.gamesrob.GamesROB;
import me.deprilula28.gamesrob.utility.Constants;
import me.deprilula28.gamesrob.utility.Log;
import net.dv8tion.jda.core.entities.User;
import org.eclipse.jetty.http.HttpStatus;
import spark.Request;
import spark.Response;

import java.util.Optional;

public class DiscordAuthentication {
    private static final String DISCORD_API_URL = "https://discordapp.com/api/v6";
    private static final String TOKEN_ENDPOINT = "/oauth2/token";
    private static final String ME_ENDPOINT = "/users/@me";
    private static final String REDIRECT_URI = "http%3A%2F%2Fgamesrob.com%2Fdiscordoauth";

    @AllArgsConstructor
    private static class TokenRequest {
        @SerializedName("client_id") private String clientID;
        @SerializedName("client_secret") private String clientSecret;
        @SerializedName("redirect_uri") private String redirect;
        private String code;
        @SerializedName("grant_type") private String grantType;
    }

    @Data
    private static class AccessTokenResponse {
        @SerializedName("access_token") private String accessToken;
        @SerializedName("refresh_token") private String refreshToken;
        @SerializedName("expires_in") private int expireTime;
    }

    @AllArgsConstructor
    @Data
    private static class MeResponse {
        private String id;
        private String username;
        private String discriminator;
        private String icon;
    }

    public static Object oauthRoute(Request request, Response response) throws Exception {
        String code = request.queryParams("code");
        if (code == null) return Website.errorPage(response, HttpStatus.BAD_REQUEST_400);

        String body = HttpRequest.post(DISCORD_API_URL + TOKEN_ENDPOINT)
                .userAgent(Constants.USER_AGENT).contentType("application/x-www-form-urlencoded")
                .send(String.format("client_id=%s&client_secret=%s&redirect_uri=%s&code=%s&grant_type=%s",
                        "383995098754711555", BootupProcedure.secret, REDIRECT_URI, code, "authorization_code")).body();
        AccessTokenResponse atp = Constants.GSON.fromJson(body,
                AccessTokenResponse.class);
        response.cookie("discAccToken", atp.accessToken);
        response.cookie("discRefToken", atp.refreshToken);
        response.cookie("discAcTokenExpires", String.valueOf(System.currentTimeMillis() + atp.expireTime));

        MeResponse me = Constants.GSON.fromJson(HttpRequest.get(DISCORD_API_URL + ME_ENDPOINT)
                .header("Authorization", "Bearer " + atp.accessToken).acceptJson().userAgent(Constants.USER_AGENT).body(),
                MeResponse.class);

        Log.trace(body);
        response.cookie("discUserID", me.id);

        response.redirect("/#message=authenticated");
        return null;
    }

    public static Optional<User> getUser(Request request) {
        String id = request.cookie("discUserID");
        if (id != null) return GamesROB.getUserById(id);
        else return Optional.empty();
    }
}
