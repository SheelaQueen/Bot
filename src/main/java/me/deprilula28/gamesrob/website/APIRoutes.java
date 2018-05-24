package me.deprilula28.gamesrob.website;

import lombok.AllArgsConstructor;
import lombok.Data;
import me.deprilula28.gamesrob.GamesROB;
import me.deprilula28.gamesrob.data.GuildProfile;
import me.deprilula28.gamesrob.utility.Constants;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.User;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.util.IO;
import spark.Request;
import spark.Response;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Optional;

public class APIRoutes {
    @Data
    private static class ServerSettings {
        private String prefix;
        private String language;
        private String permStartGame;
        private String permStopGame;
    }

    static Object serverEditRequest(Request request, Response response) throws Exception {
        Optional<User> optUser = DiscordAuthentication.getUser(request);
        Optional<Guild> optGuild = GamesROB.getGuildById(request.params(":serverID"));
        if (!optGuild.isPresent() || !optUser.isPresent()) throw new FileNotFoundException();
        Guild guild = optGuild.get();
        User user = optUser.get();
        if (!guild.getMember(user).hasPermission(Permission.MANAGE_SERVER)) throw new IOException();

        GuildProfile profile = GuildProfile.get(guild);
        ServerSettings settings = Constants.GSON.fromJson(request.body(), ServerSettings.class);
        profile.setGuildPrefix(settings.prefix);
        profile.setLanguage(settings.language);
        profile.setPermStartGame(settings.permStartGame);
        profile.setPermStopGame(settings.permStopGame);

        return null;
    }

    static Object changeLangRequest(Request request, Response response) throws Exception {
        String code = Constants.GSON.fromJson(request.body(), String.class);
        response.cookie("Language", code);

        return null;
    }
}
