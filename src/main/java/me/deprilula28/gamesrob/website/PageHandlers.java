package me.deprilula28.gamesrob.website;

import lombok.AllArgsConstructor;
import lombok.Data;
import me.deprilula28.gamesrob.BootupProcedure;
import me.deprilula28.gamesrob.GamesROB;
import me.deprilula28.gamesrob.Language;
import me.deprilula28.gamesrob.data.GuildProfile;
import me.deprilula28.gamesrob.data.Statistics;
import me.deprilula28.gamesrob.data.UserProfile;
import me.deprilula28.gamesrob.utility.Cache;
import me.deprilula28.gamesrob.utility.Constants;
import me.deprilula28.gamesrob.utility.Log;
import me.deprilula28.gamesrob.utility.Utility;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Game;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.User;
import org.eclipse.jetty.http.HttpStatus;
import org.markdown4j.Markdown4jProcessor;
import spark.ModelAndView;
import spark.Request;
import spark.Response;
import spark.Route;
import spark.template.jade.JadeTemplateEngine;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

class PageHandlers {
    private static Markdown4jProcessor markdownProcessor = new Markdown4jProcessor();

    private static String jadePage(String jadeFilePath, Consumer<Map<String, Object>> consumer) throws Exception {
        Map<String, Object> model = new HashMap<>();
        consumer.accept(model);

        return new JadeTemplateEngine().render(new ModelAndView(model, jadeFilePath));
    }

    private static String getLanguage(Request request) {
        if (request.cookie("Language") != null) return request.cookie("Language");

        String language = Constants.DEFAULT_LANGUAGE;
        if (request.headers("Accept-Language") != null)
            for (String langCur : request.headers("Accept-Language").split(",")){
                String[] sep = langCur.replaceAll("-", "_").split("_");
                if (sep.length != 2) continue;
                String filteredLangCur = sep[0].toLowerCase() + "_" + sep[1].toUpperCase();
                if (Language.getLanguageList().contains(filteredLangCur)) language = filteredLangCur;
            }
        return language;
    }

    @AllArgsConstructor
    public static class MutualServer {
        public String link;
        public String icon;
        public String name;
    }

    static String commonWrapper(Request request, Response response) throws Exception {
        String language = getLanguage(request);
        return jadePage("common.jade", model -> {
            model.put("help", Language.transl(language, "website.commonWrapper.help"));

            Optional<User> optionalUser = DiscordAuthentication.getUser(request);
            model.put("authenticated", optionalUser.isPresent());

            optionalUser.ifPresent(user -> {
                List<Guild> mutualGuilds = new ArrayList<>();
                GamesROB.shards.forEach(cur -> {
                    User su = cur.getUserById(user.getId());
                    if (su != null) mutualGuilds.addAll(su.getMutualGuilds());
                });
                model.put("mutualServers", mutualGuilds.stream().map(it -> new MutualServer(
                            "/serverLeaderboard/" + it.getId() + "/", it.getIconUrl() == null ?
                        "https://discordapp.com/assets/dd4dbc0016779df1378e7812eabaa04d.png" :
                        it.getIconUrl().replaceAll(".jpg", ".webp"),
                        it.getName())).collect(Collectors.toList()));
                model.put("userProfileURL", "/userProfile/" + user.getId() + "/");
                model.put("userImageURL", user.getAvatarUrl());
                model.put("userName", user.getName());
                model.put("userDiscrim", user.getDiscriminator());
                model.put("myServers", Language.transl(language, "website.commonWrapper.myServers"));

                Optional<Member> officialGuildMember = GamesROB.getGuildById(Constants.SERVER_ID).map(it ->
                    it.getMember(user));
                model.put("isMember", officialGuildMember.isPresent());
                officialGuildMember.ifPresent(member -> {
                    Color color = member.getColor();
                    Color colorDark = color.darker();
                    model.put("serverRoleColor", String.format("rgb(%s, %s, %s)",
                            color.getRed(), color.getGreen(), color.getBlue()));
                });
            });
            if (!optionalUser.isPresent()) {
                model.put("joinServer", Language.transl(language, "website.commonWrapper.joinServer"));
                model.put("logIn", Language.transl(language, "website.commonWrapper.logIn"));
            }

            model.put("languages", Language.getLanguageList().stream().map(it ->
                    new LanguageOption(false,
                            Language.transl(it, "languageProperties.languageName"),
                            Language.transl(it, "languageProperties.code"))).collect(Collectors.toList()));

            model.put("addBot", Language.transl(language, "website.commonWrapper.addBot"));
            model.put("gamesROBDescription", Language.transl(language, "website.home.botDescription"));
            model.put("githubSource", Language.transl(language, "website.commonWrapper.githubSource"));
            model.put("trelloPage", Language.transl(language, "website.commonWrapper.trelloPage"));
            model.put("dblListing", Language.transl(language, "website.commonWrapper.dblListing"));
            model.put("supportServer", Language.transl(language, "website.commonWrapper.supportServer"));
            model.put("signOut", Language.transl(language, "website.commonWrapper.signOut"));
            model.put("languageDropdownButtonName", Language.transl(language, "website.commonWrapper.language"));
        });
    }

    private static final String[] FEATURES = {
            "haveFunTogether", "playCompetitively"
    };

    @AllArgsConstructor
    public static class HomeGameExample {
        public String resource;
        public String resourceBlur;
        public String name;
        public String description;
        public String helpPage;
    }

    @AllArgsConstructor
    public static class HomeFeaturesView {
        public String title;
        public String description;
        public String image;
        public boolean side;
    }

    static Object homePage(Request request, Response response) throws Exception {
        String language = getLanguage(request);
        return jadePage("index.jade", model -> {
            model.put("gamesROBDescription", Language.transl(language, "website.home.botDescription"));

            model.put("homeGameExamples", Arrays.stream(GamesROB.ALL_GAMES).map(it -> {
                String gameName = it.getName(Constants.DEFAULT_LANGUAGE).toLowerCase();
                return new HomeGameExample("/res/" + gameName + ".png", "/res/" + gameName + "Blur.png",
                        it.getName(language), it.getShortDescription(language), "/help/games/" + gameName);
            }).collect(Collectors.toList()));

            model.put("inviteURL", Constants.getInviteURL(GamesROB.shards.get(0)));
            model.put("inviteButtonName", Language.transl(language, "website.home.invite"));

            model.put("upvoteURL", Constants.getDboURL(GamesROB.shards.get(0)));
            model.put("upvoteButtonName", Language.transl(language, "website.home.upvote"));

            AtomicBoolean side = new AtomicBoolean(true);
            model.put("features", Arrays.stream(FEATURES).map(it -> {
                side.set(!side.get());
                return new HomeFeaturesView(
                        Language.transl(language, "website.home." + it + ".title"),
                        Language.transl(language, "website.home." + it + ".description"),
                        "/res/" + it + ".png", side.get());
            }).collect(Collectors.toList()));

            boolean showChangelog = false;
            String lastVisitedVersion = request.cookie("lastVisitedVersion");
            if (lastVisitedVersion == null) response.cookie("lastVisitedVersion", GamesROB.VERSION);
            else if (!lastVisitedVersion.equals(GamesROB.VERSION)) {
                showChangelog = true;
                model.put("changelogTitle", Language.transl(language, "website.home.changelog.title",
                        GamesROB.VERSION));
                model.put("changelogSubtitle", Language.transl(language, "website.home.changelog.subtitle",
                        Utility.formatTime(Statistics.get().getLastUpdateLogSentTime())));
                model.put("changelog", BootupProcedure.changelog.split("\n"));

                response.cookie("lastVisitedVersion", GamesROB.VERSION);
            }

            model.put("showChangelog", showChangelog);
        });
    }

    @AllArgsConstructor
    public static class LeaderboardRanking {
        public String name;
        public List<LeaderboardUserEntry> topEntries;
    }

    @AllArgsConstructor
    public static class LeaderboardUserEntry {
        public String position;
        public String id;
        public String name;
        public String icon;
        public String discrim;
        public String positionRole;
        public String pageURL;
        public UserProfile.GameStatistics stats;
    }

    private static String[] MEDALS = { "\uD83E\uDD47", "\uD83E\uDD48", "\uD83E\uDD49" };
    private static String[] COLORS = { "gold", "silver", "bronze", "default" };

    static Object serverLeaderboardPage(Request request, Response response) throws Exception {
        String language = getLanguage(request);
        Optional<User> user = DiscordAuthentication.getUser(request);
        return jadePage("server.jade", model -> {
            Optional<Guild> optGuild = GamesROB.getGuildById(request.params(":serverID"));
            if (!optGuild.isPresent()) {
                Website.errorPage(response, HttpStatus.NOT_FOUND_404);
                return;
            }

            Guild guild = optGuild.get();
            GuildProfile profile = GuildProfile.get(guild);

            model.put("leaderboardTableName", Language.transl(language, "website.server.user"));
            model.put("leaderboardTableVictories", Language.transl(language, "website.server.wins"));
            model.put("leaderboardTableLosses", Language.transl(language, "website.server.lost"));
            model.put("leaderboardTableMatches", Language.transl(language, "website.server.gamesPlayed"));

            if (guild.getIconUrl() != null) model.put("guildIcon", guild.getIconUrl().replaceAll(".jpg", ".png"));
            model.put("guildName", guild.getName());

            List<LeaderboardRanking> rankings = new ArrayList<>();
            rankings.add(new LeaderboardRanking(Language.transl(language, "website.server.overall"),
                    generateTopEntries(profile, language, profile.getOverall(), 10)));
            profile.getPerGame().forEach((gameKey, values) -> {
                if (!values.isEmpty()) rankings.add(new LeaderboardRanking(Language.transl(language,
                        "game." + gameKey + ".name"), generateTopEntries(profile, language, values, 5)));
            });
            model.put("rankings", rankings);

            boolean showSettings = user.map(it -> guild.getMember(it).hasPermission(Permission.MANAGE_SERVER))
                    .orElse(false);
            if (showSettings) model.put("settingsText", Language.transl(language, "website.server.settings"));
            model.put("showSettings", showSettings);

            model.put("memberCount", guild.getMembers().size());
        });
    }

    private static List<LeaderboardUserEntry> generateTopEntries(GuildProfile profile, String language,
                             List<GuildProfile.LeaderboardEntry> entries, int limit) {
        return entries.stream().limit(limit).map(it -> {
            Optional<User> user = GamesROB.getUserById(it.getId());
            int pos = profile.getIndex(profile.getOverall(), it.getId());
            return new LeaderboardUserEntry(
                    ((pos < 3 && pos >= 0) ? MEDALS[pos] : "") + (pos + 1) + Utility.formatNth(language, pos + 1),
                    user.map(User::getId).orElse(null), user.map(User::getName).orElse(null),
                    user.map(User::getAvatarUrl).orElse(null), user.map(User::getDiscriminator).orElse(null),
                    pos < 3 ? COLORS[pos] : COLORS[3], "/userProfile/"  + it.getId() + "/", it.getStats()
            );
        }).collect(Collectors.toList());
    }

    @AllArgsConstructor
    public static class LanguageOption {
        public boolean selected;
        public String name;
        public String code;
    }

    @AllArgsConstructor
    public static class RoleOption {
        public String name;
        public String id;
    }

    @AllArgsConstructor
    public static class PermissionOption {
        public String curValue;
        public String name;
    }

    static Object serverEditPage(Request request, Response response) throws Exception {
        String language = getLanguage(request);
        Optional<User> optUser = DiscordAuthentication.getUser(request);
        return jadePage("editServer.jade", model -> {
            Optional<Guild> optGuild = GamesROB.getGuildById(request.params(":serverID"));
            if (!optGuild.isPresent() || !optUser.isPresent()) {
                Website.errorPage(response, HttpStatus.NOT_FOUND_404);
                return;
            }
            Guild guild = optGuild.get();
            User user = optUser.get();
            if (!guild.getMember(user).hasPermission(Permission.MANAGE_SERVER)) {
                Website.errorPage(response, HttpStatus.UNAUTHORIZED_401);
                return;
            }

            GuildProfile profile = GuildProfile.get(guild);

            Log.info(String.join(", ", profile.getOverall().stream().map(Object::toString).collect(Collectors.toList())));
            Log.info(String.join(", ", profile.getUserStatisticsMap().entrySet().stream().map(entry ->
                    entry.getKey() + ": " + entry.getValue().toString()).collect(Collectors.toList())));

            model.put("defaultTranslation", Language.transl(language, "website.serverEdit.default"));
            model.put("languageTranslation", Language.transl(language, "website.serverEdit.language"));
            model.put("submitButtonName", Language.transl(language, "website.serverEdit.submit"));

            model.put("guildName", guild.getName());
            model.put("currentPrefix", Constants.DEFAULT_PREFIX.equals(profile.getGuildPrefix()) ? null :
                    profile.getGuildPrefix());
            model.put("languages", Language.getLanguageList().stream().map(it ->
                    new LanguageOption(it.equals(profile.getLanguage()),
                            Language.transl(it, "languageProperties.languageName"),
                            Language.transl(it, "languageProperties.code"))).collect(Collectors.toList()));
            model.put("roles", guild.getRoles().stream().map(it -> new RoleOption(it.getName(), it.getId()))
                    .collect(Collectors.toList()));

            List<PermissionOption> perms = new ArrayList<>();
            perms.add(new PermissionOption(profile.getPermStartGame(), Language.transl(language, "website.serverEdit.permStartGame")));
            perms.add(new PermissionOption(profile.getPermStopGame(), Language.transl(language, "website.serverEdit.permStopGame")));
            model.put("permissions", perms);
        });
    }

    static Route wikiPage(String wikiPage) {
        return (req, res) -> {
            String language = getLanguage(req);
            return jadePage("wiki.jade", model -> {
                model.put("pageName", Language.transl(language, "website.help." + wikiPage));
                model.put("wikiPage", Language.transl(language, wikiPage));

                model.put("help", Language.transl(language, "website.commonWrapper.help"));
                model.put("introduction", Language.transl(language, "website.help.intro"));
                model.put("commands", Language.transl(language, "website.help.commands"));
                model.put("credits", Language.transl(language, "website.help.credits"));
                model.put("tictactoe", Language.transl(language, "website.help.tictactoe"));
                model.put("connectFour", Language.transl(language, "website.help.connectfour"));
                model.put("minesweeper", Language.transl(language, "website.help.minesweeper"));
                model.put("hangman", Language.transl(language, "website.help.hangman"));
                model.put("games", Language.transl(language, "website.help.games"));
            }).replace("%%MARKDOWN_TEXT%%", Cache.get(wikiPage + language, n -> {
                try {
                    return markdownProcessor.process(readPath(language, wikiPage));
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }
            }));
        };
    }

    private static String readPath(String language, String page) {
        byte[] data = Website.getResource("help/" + language.replaceAll("_", "-").toLowerCase()
                + "/" + page + ".md");
        if (data == null)
            if (language.equals(Constants.DEFAULT_LANGUAGE)) return null;
            else return readPath(Constants.DEFAULT_LANGUAGE, page);
        return new String(data);
    }

    @Data
    private static class MemberInfoRequest {
        private String id;
    }

    @AllArgsConstructor
    public static class RankingUserProfile {
        public String position;
        public String name;
        public UserProfile.GameStatistics stats;
    }

    static Object userProfileModal(Request request, Response response) throws Exception {
        String language = getLanguage(request);
        Object answer = jadePage("userProfile.jade", model -> {
            Optional<Guild> optGuild = GamesROB.getGuildById(request.params(":serverID"));
            Optional<User> optUser = GamesROB.getUserById(Constants.GSON.fromJson(request.body(),
                    MemberInfoRequest.class).id);
            if (!optGuild.isPresent() || !optUser.isPresent()) {
                Website.errorPage(response, HttpStatus.NOT_FOUND_404);
                return;
            }

            Guild guild = optGuild.get();
            GuildProfile gprofile = GuildProfile.get(guild);
            User user = optUser.get();
            UserProfile profile = UserProfile.get(user);
            GuildProfile.UserStatistics stats = gprofile.getUserStats(user.getId());

            model.put("userAvatar", user.getAvatarUrl());
            model.put("userName", user.getName());
            model.put("userDiscrim", "#" + user.getDiscriminator());

            model.put("victories", Language.transl(language, "website.server.wins"));
            model.put("losses", Language.transl(language, "website.server.lost"));
            model.put("gamesPlayed", Language.transl(language, "website.server.gamesPlayed"));

            List<RankingUserProfile> rankings = new ArrayList<>();
            rankings.add(generateRanking(stats.getOverall(), gprofile.getIndex(gprofile.getOverall(), user.getId()),
                    "website.server.overall", language));
            stats.getGamesStats().forEach((gameKey, value) -> {
                rankings.add(generateRanking(value, gprofile.getIndex(gprofile.getOption(gameKey), user.getId()),
                        "game." + gameKey + ".name", language));
            });
            model.put("rankings", rankings);
        });
        Log.info(answer);
        return answer;
    }

    private static RankingUserProfile generateRanking(UserProfile.GameStatistics statistics, int pos, String name, String language)  {
        return new RankingUserProfile(pos == -1 ? Language.transl(language, "command.profile.notRanked") :
                (pos < 3 ? MEDALS[pos] : "") + (pos + 1) + Utility.formatNth(language, pos + 1),
                Language.transl(language, name), statistics);
    }
}
