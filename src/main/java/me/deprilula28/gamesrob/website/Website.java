package me.deprilula28.gamesrob.website;

import com.google.gson.JsonElement;
import lombok.AllArgsConstructor;
import lombok.Data;
import me.deprilula28.gamesrob.GamesROB;
import me.deprilula28.gamesrob.utility.Cache;
import me.deprilula28.gamesrob.utility.Constants;
import me.deprilula28.gamesrob.utility.Log;
import me.deprilula28.gamesrob.utility.Utility;
import org.eclipse.jetty.http.HttpStatus;
import spark.Response;
import spark.Route;
import sun.misc.IOUtils;

import java.io.InputStream;
import java.util.List;

import static spark.Spark.*;

public class Website {
    private static final String[] FAVICONS = {
        "android-chrome-192x192.png", "apple-touch-icon.png", "browserconfig.xml", "favicon.ico", "favicon-16x16.png",
        "favicon-32x32.png", "mstile-150x150.png", "safari-pinned-tab.svg", "site.webmanifest"
    };

    public static void start(int port) {
        Log.info("Starting web server in port " + port);

        port(port);

        get("/discordoauth", DiscordAuthentication::oauthRoute);

        /*
        get("/twitchwebhook", WebhookHandlers::getTwitch);
        post("/twitchwebhook", WebhookHandlers::postTwitch);
        */
        post("/dblwebhook", WebhookHandlers::dblWebhook);

        get("/common/*", directoryRoute("common", "/common/".length()));
        get("/res/*", directoryRoute("res", "/res/".length()));

        path("/help", () -> {
            path("/games", () -> {
                redirect.get("/", "/help/");
                get("/connectfour", commonOverlayRoute(PageHandlers.wikiPage("connectfour")));
                get("/hangman", commonOverlayRoute(PageHandlers.wikiPage("hangman")));
                get("/tictactoe", commonOverlayRoute(PageHandlers.wikiPage("tictactoe")));
                get("/minesweeper", commonOverlayRoute(PageHandlers.wikiPage("minesweeper")));
            });
            get("/", commonOverlayRoute(PageHandlers.wikiPage("intro")));
            get("/commands", commonOverlayRoute(PageHandlers.wikiPage("commands")));
            get("/credits", commonOverlayRoute(PageHandlers.wikiPage("credits")));
        });

        path("/serverLeaderboard/:serverID", () -> {
            get("/", commonOverlayRoute(PageHandlers::serverLeaderboardPage));
            get("/edit", commonOverlayRoute(PageHandlers::serverEditPage));
            post("/edit", apiRoute(APIRoutes::serverEditRequest));
            post("/userProfileModal", apiRoute(PageHandlers::userProfileModal));
        });

        for (String favicon : FAVICONS) {
            get("/" + favicon, (res, req) -> getResource("favicon/" + favicon));
        }

        get("/", commonOverlayRoute(PageHandlers::homePage));

        redirect.get("/server", "https://discord.gg/8EZ7BEz");

        notFound("404 - Not Found");
        init();
    }

    public static String errorPage(Response response, int code) {
        response.status(code);
        return code + " - " + HttpStatus.getMessage(code);
    }

    public static byte[] getResource(String path) {
        return Cache.get(path, n -> {
            String fixedPath = "/website/" + path;
            InputStream fis = null;
            try {
                fis = Website.class.getClass().getResourceAsStream(fixedPath);
                byte[] data = IOUtils.readFully(fis, -1, false);
                fis.close();
                return data;
            } catch (Exception e) {
                Log.trace("Couldn't find file on resources: " + fixedPath);
                if (fis != null) Utility.quietlyClose(fis);
                return null;
            }
           });
    }

    private static Route directoryRoute(String path, int substring) {
        return (req, res) -> {
            String servletPath = req.pathInfo().substring(substring);
            byte[] resource = getResource(path +  "/" + (servletPath.length() > 0 ? servletPath : "index.html"));
            if (resource == null) return errorPage(res, HttpStatus.NOT_FOUND_404);

            if (servletPath.endsWith(".js")) res.header("Content-Type", "text/javascript");
            else if (servletPath.endsWith(".jpeg")) res.header("Content-Type", "image/jpeg");
            else if (servletPath.endsWith(".png")) res.header("Content-Type", "image/png");
            else if (servletPath.endsWith(".css")) res.header("Content-Type", "text/css");

            return resource;
        };
    }

    @AllArgsConstructor
    @Data
    private static class APIResponse {
        private long msTaken;
        private boolean success;
        private Exception error;
        private JsonElement response;
    }

    private static Route apiRoute(Route route) {
        return (req, res) -> {
            long begin = System.currentTimeMillis();
            try {
                JsonElement response = Constants.GSON.toJsonTree(route.handle(req, res));
                return Constants.GSON.toJson(new APIResponse(System.currentTimeMillis() - begin,
                        true, null, response));
            } catch (Exception e) {
                return Constants.GSON.toJson(new APIResponse(System.currentTimeMillis() - begin,
                        false, e, null));
            }
        };
    }

    private static Route commonOverlayRoute(Route route) {
        return (req, res) -> {
            Object returned = route.handle(req, res);
            if ("true".equals(req.headers("PageOnly"))) return returned;
            else return PageHandlers.commonWrapper(req, res).replace("%%MAIN_PAGE%%", returned instanceof String
                    ? (String) returned : new String((byte[]) returned, "UTF-8"));
        };
    }
}
