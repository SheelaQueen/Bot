package me.deprilula28.gamesrob.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import me.deprilula28.gamesrob.GamesROB;
import me.deprilula28.gamesrob.Language;
import me.deprilula28.gamesrob.utility.Constants;
import me.deprilula28.gamesrob.utility.Log;
import me.deprilula28.gamesrob.utility.Utility;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.User;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

public class RPCManager extends WebSocketClient {
    private Map<RequestType, Function<JsonElement, Object>> handlerMap = new HashMap<>();
    private Map<UUID, Utility.Promise<JsonElement>> requests = new HashMap<>();
    private boolean connected;

    @Data
    @AllArgsConstructor
    public static class Request {
        private RequestType requestType;
        private JsonElement requestBody;
        private String requestId;
    }

    @AllArgsConstructor
    @Data
    private static class JsonRPCMessage {
        @SerializedName("jsonrpc") private String version;
        private String method;
        @SerializedName("params") private JsonElement message;
        private int id;
        private JsonObject error;

        @AllArgsConstructor
        @Data
        private static class ServerRequestMessage {
            private JsonElement message;
            private String requestId;
        }

        @AllArgsConstructor
        @Data
        private static class ClientRequestMessage {
            private RequestType requestType;
            private String requestId;
            private Object requestBody;
        }
    }

    private SentInfo info;

    @AllArgsConstructor
    private static class SentInfo {
        private int shardIdFrom;
        private int shardIdTo;
        private int totalShards;
    }

    public static enum RequestType {
        // Server -> Client
        WEBHOOK_NOTIFICATION, GET_USER_BY_ID, GET_GUILD_BY_ID, GET_MUTUAL_SERVERS, GET_SHARDS_INFO,

        // Client -> Server
        GET_ALL_SHARDS_INFO
    }

    public RPCManager(String ip, int shardIdFrom, int shardIdTo, int totalShards) throws Exception {
        super(new URI(ip), new Draft_6455());
        info = new SentInfo(shardIdFrom, shardIdTo, totalShards);
        connect();
    }

    @Override
    public void onOpen(ServerHandshake serverHandshake) {
        connected = true;
        Log.wrapException("Sending action", () -> sendAction(new JsonRPCMessage("2.0", "connect",
                Constants.GSON.toJsonTree(info), -1, null)));
        registerHandlers();
    }

    @Override
    public void onMessage(String message) {
        try {
            Log.trace("Received message:", message);
            JsonRPCMessage jsonRPCMessage = Constants.GSON.fromJson(message, JsonRPCMessage.class);
            if (jsonRPCMessage.getError() != null) {
                JsonObject error = jsonRPCMessage.getError();
                Log.warn("JSON/RPC Error: " + error.get("message").getAsString() + " (" + error.get("code").getAsInt() + ")");
                return;
            }
            if (jsonRPCMessage.getMethod() != null) switch (jsonRPCMessage.getMethod()) {
                case "request":
                    Request request = Constants.GSON.fromJson(jsonRPCMessage.getMessage(), Request.class);
                    Object response = handlerMap.get(request.getRequestType()).apply(request.getRequestBody());

                    sendAction(new JsonRPCMessage("2.0", "respond",
                            Constants.GSON.toJsonTree(new JsonRPCMessage.ServerRequestMessage(
                                    Constants.GSON.toJsonTree(response), request.getRequestId())),
                            jsonRPCMessage.getId(), null));
                    break;
                case "respond":
                    JsonRPCMessage.ServerRequestMessage requestMessage = Constants.GSON.fromJson(jsonRPCMessage.getMessage(), JsonRPCMessage.ServerRequestMessage.class);
                    UUID uuid = UUID.fromString(requestMessage.getRequestId());
                    if (!requests.containsKey(uuid)) {
                        Log.warn("JSON/RPC received response for invalid id [" + uuid.toString() + "].");
                        return;
                    }
                    requests.get(uuid).done(requestMessage.getMessage());
                    break;
            }
        } catch (Exception e) {
            Log.exception("RPC socket connection received an invalid message", e, message);
        }
    }

    public void disconnectThread() {
        Log.wrapException("Reconnecting to RPC/JSON", () -> {
            long delay = 3000L;
            final long delayInc = 1000L;
            final long delayMax = TimeUnit.MINUTES.toMillis(2);

            Log.info("Attempting reconnect to RPC/JSON...");
            while (!reconnectBlocking()) {
                Log.info("RPC/JSON Reconnect failed. Re-attempting in " + Utility.formatPeriod(delay));
                Thread.sleep(delay * 100000);
                Log.info("Attempting reconnect to RPC/JSON...");
                delay += delayInc;
                if (delay > delayMax) delay = delayMax;
            }
        });
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        Log.info("RPC socket disconnected. Reason: " + reason + " (" + code + ")");
        connected = false;

        Thread reconnect = new Thread(this::disconnectThread);
        reconnect.setDaemon(true);
        reconnect.setName("RPC/JSON Reconnect Task");
        reconnect.start();
    }

    @Override
    public void onError(Exception e) {
        Log.exception("RPC/JSON WebSocket", e);
    }

    @AllArgsConstructor
    public static class MutualServer {
        public String link;
        public String icon;
        public String name;
    }

    @AllArgsConstructor
    private static class WebsiteUser {
        private String id;
        private String name;
        private String discrim;
        private String avatarUrl;
    }

    @AllArgsConstructor
    private static class WebsiteRole {
        private String name;
        private String id;
    }

    @AllArgsConstructor
    private static class WebsiteGuild {
        private String id;
        private String name;
        private String iconUrl;
        private int members;
        private boolean manageServer;
        private List<WebsiteRole> roles;
    }

    @Data
    private static class GetGuildRequest {
        private String id;
        private String userId;
    }

    private void registerHandlers() {
        handlerMap.put(RequestType.GET_USER_BY_ID, id -> GamesROB.getUserById(id.getAsString())
                .map(it -> new WebsiteUser(it.getId(), it.getName(), it.getDiscriminator(),
                        it.getAvatarUrl())).orElse(null));
        handlerMap.put(RequestType.GET_GUILD_BY_ID, this::getGuildById);
        handlerMap.put(RequestType.WEBHOOK_NOTIFICATION, this::webhookNotification);
        handlerMap.put(RequestType.GET_MUTUAL_SERVERS, this::getMutualServers);
        handlerMap.put(RequestType.GET_SHARDS_INFO, n -> GamesROB.getShardsInfo());
    }

    private WebsiteGuild getGuildById(JsonElement el) {
        GetGuildRequest req = Constants.GSON.fromJson(el, GetGuildRequest.class);
        String id = req.getId();
        Optional<String> userId = Optional.ofNullable(req.getUserId());

        return GamesROB.getGuildById(id).map(it -> new WebsiteGuild(it.getId(), it.getName(), it.getIconUrl(),
                it.getMembers().size(),
                userId.flatMap(GamesROB::getUserById).map(user -> it.getMember(user).hasPermission(Permission.MANAGE_SERVER))
                        .orElse(false),
                it.getRoles().stream().map(role -> new WebsiteRole(role.getName(), role.getId())).collect(Collectors.toList())))
            .orElse(null);
    }



    private List<MutualServer> getMutualServers(JsonElement id) {
        List<Guild> mutualGuilds = new ArrayList<>();
        GamesROB.shards.forEach(cur -> {
            User su = cur.getUserById(id.getAsString());
            if (su != null) mutualGuilds.addAll(su.getMutualGuilds());
        });
        return mutualGuilds.stream().map(it -> new MutualServer(
                "/serverLeaderboard/" + it.getId() + "/",
                it.getIconUrl() == null
                        ? "https://discordapp.com/assets/dd4dbc0016779df1378e7812eabaa04d.png"
                        : it.getIconUrl().replaceAll("\\.jpg", ".png"),
                it.getName())).collect(Collectors.toList());
    }

    private Object webhookNotification(JsonElement upvoteInfo) {
        Log.info("Received upvote: ", upvoteInfo);
        Statistics.get().setUpvotes(Statistics.get().getUpvotes() + 1);
        GamesROB.getUserById(upvoteInfo.getAsJsonObject().get("user").getAsLong()).ifPresent(user -> user.openPrivateChannel().queue(pm -> {
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
        }));

        return null;
    }

    public Utility.Promise<JsonElement> request(RequestType requestType, JsonElement body) {
        UUID id = UUID.randomUUID();
        Log.wrapException("Sending RPC/JSON request", () -> sendAction(new JsonRPCMessage("2.0",
                "request", Constants.GSON.toJsonTree(new JsonRPCMessage.ClientRequestMessage(requestType,
                id.toString(), body)), -1, null)));

        Utility.Promise<JsonElement> promise = new Utility.Promise<>();
        requests.put(id, promise);

        return promise;
    }

    public void sendAction(JsonRPCMessage message) throws Exception {
        Log.trace("Sent message:", message);
        send(Constants.GSON.toJson(message));
    }
}
