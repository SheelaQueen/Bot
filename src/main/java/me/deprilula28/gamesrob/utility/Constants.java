package me.deprilula28.gamesrob.utility;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import me.deprilula28.gamesrob.Language;
import me.deprilula28.gamesrob.data.GuildProfile;
import me.deprilula28.gamesrob.data.UserProfile;
import me.deprilula28.jdacmdframework.CommandContext;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.User;

import java.awt.*;
import java.io.File;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class Constants {
    public static final String GAMESROB_DOMAIN = "https://gamesrob.com";
    public static final String TRELLO_API_KEY = "52734e54b9cdcdd16656a70e30d5b596";

    public static final int LEADERBOARD_GAMES_PLAYED_REQUIREMENT = 1;
    public static final File OLD_DATA_FOLDER = new File("gamesrobData");

    public static final String DEFAULT_PREFIX = "g*";
    public static final String DEFAULT_LANGUAGE = "en_US";

    public static final File WEBSITE_FOLDER = new File("website");
    public static final File DATA_FOLDER = new File("data");
    public static final File TEMP_FOLDER = new File(DATA_FOLDER, "temp");
    public static final File LOGS_FOLDER = new File(DATA_FOLDER, "logs");

    public static final File USER_PROFILES_FOLDER = new File(DATA_FOLDER, "userProfiles");
    public static final File STATS_FILE = new File(DATA_FOLDER, "statistics.json");
    public static final File GUILDS_FOLDER = new File(DATA_FOLDER, "guildData");

    public static final long PRESENCE_UPDATE_PERIOD = TimeUnit.SECONDS.toMillis(60);
    public static final long RAM_CACHE_TIME = TimeUnit.SECONDS.toMillis(15);

    public static final Optional<String> VANITY_DBL_URL = Optional.of("https://discordbots.org/bot/GamesROB");
    public static final Optional<Long> changelogChannel = Optional.of(397851392016121877L);

    public static final int MATCH_WIN_TOKENS = 40;
    public static final int MESSAGES_SINCE_THRESHOLD = 3;

    public static final Color[] BOT_COLORS = {
            new Color(0xf8bb37), new Color(0x02aff4), new Color(0xf7413b), new Color(0x05b996)
    };

    public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, " +
            "like Gecko) Chrome/66.0.3359.139 Safari/537.36";

    public static final Gson GSON = new GsonBuilder()
            .enableComplexMapKeySerialization().disableHtmlEscaping()
            .create();

    public static String getPrefix(Guild guild) {
        String value = GuildProfile.get(guild).getGuildPrefix();
        return value == null ? DEFAULT_PREFIX : value;
    }

    public static String getPrefixHelp(Guild guild) {
        String result = getPrefix(guild).replaceAll("\\$", "\uFF04").replaceAll("\\\\", "\\\\\\\\");
        Log.trace(result);
        return result;
    }

    public static String getInviteURL(JDA jda) {
        return String.format(
                "https://discordapp.com/oauth2/authorize?client_id=%s&scope=bot&permissions=347136",
                jda.getSelfUser().getId()
        );
    }

    public static String getLanguage(CommandContext context) {
        return getLanguage(context.getAuthor(), context.getGuild());
    }

    public static String getLanguage(User user, Guild guild) {
        UserProfile up = UserProfile.get(user);
        if (up.getLanguage() != null) return up.getLanguage();

        GuildProfile gp = GuildProfile.get(guild);
        if (gp.getLanguage() != null) return gp.getLanguage();

        return DEFAULT_LANGUAGE;
    }

    public static String getDboURL(JDA jda) {
        return VANITY_DBL_URL.orElse("https://discordbots.org/bot/" + jda.getSelfUser().getId());
    }

    public static String getNotEnoughTokensMessage(CommandContext context, int amount) {
        UserProfile profile = UserProfile.get(context.getAuthor());
        return Language.transl(context, "genericMessages.notEnoughTokens.beggining", amount - profile.getTokens())
                + (System.currentTimeMillis() - profile.getLastUpvote() > TimeUnit.DAYS.toMillis(1) ?
                Language.transl(context, "genericMessages.notEnoughTokens.upvote",
                        Constants.getDboURL(context.getJda()) + "/vote") : "");
    }

    public static final long EMOTE_GUILD_ID = 361592912095739904L;
    public static final long SERVER_ID = 345259986303057930L;
}
