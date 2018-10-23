package me.deprilula28.gamesrob.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import me.deprilula28.gamesrob.GamesROB;
import me.deprilula28.gamesrob.Language;
import me.deprilula28.gamesrob.achievements.AchievementType;
import me.deprilula28.gamesrob.commands.OwnerCommands;
import me.deprilula28.gamesrob.utility.Constants;
import me.deprilula28.gamesrob.utility.Log;
import me.deprilula28.gamesrob.utility.Utility;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.User;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
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
        WEBHOOK_NOTIFICATION, GET_USER_BY_ID, GET_GUILD_BY_ID, GET_MUTUAL_SERVERS, GET_SHARDS_INFO, OWNER_LIST_UPDATED,
        BOT_UPDATED, BOT_RESTARTED, IS_OWNER,

        // Client -> Server
        GET_ALL_SHARDS_INFO, BOT_UPDATE, OWNER_LIST_UPDATE
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
        ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
        disconnectExec(service);
    }

    private void disconnectExec(ScheduledExecutorService service) {
        Log.wrapException("Failed to connect to RPC/JSON", () -> {
            Log.info("Attempting reconnect to RPC/JSON...");
            if (reconnectBlocking()) Log.info("Connected sucessfully.");
            else service.schedule(() -> disconnectExec(service), 3, TimeUnit.SECONDS);
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
        handlerMap.put(RequestType.IS_OWNER, id -> GamesROB.owners.contains(Long.parseLong(id.getAsString())));

        handlerMap.put(RequestType.OWNER_LIST_UPDATED, owners -> {
            List<Long> newOwners = new ArrayList<>();
            owners.getAsJsonArray().forEach(it -> newOwners.add(Long.parseLong(it.getAsString())));
            GamesROB.owners = newOwners;
            Log.info("Owner list has been updated.");

            return null;
        });

        handlerMap.put(RequestType.BOT_UPDATED, url -> {
            Log.wrapException("Updating bot from RPC request", () -> OwnerCommands.update(url.getAsString(), Log::info));
            return null;
        });
        handlerMap.put(RequestType.BOT_RESTARTED, url -> {
            Log.info("Bot restarting as RPC server request.");
            System.exit(-1);
            return null;
        });
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
        return Utility.getAllMutualGuilds(id.getAsString()).stream().map(it -> new MutualServer(
                "/serverLeaderboard/" + it.getId() + "/",
                it.getIconUrl() == null
                        ? "https://discordapp.com/assets/dd4dbc0016779df1378e7812eabaa04d.png"
                        : it.getIconUrl().replace(".jpg", ".png"),
                it.getName())).collect(Collectors.toList());
    }

    private Object webhookNotification(JsonElement upvoteInfo) {
        boolean weekend = Utility.isWeekendMultiplier();
        Statistics.get().setUpvotes(Statistics.get().getUpvotes() + (weekend ? 2 : 1));
        Statistics.get().setMonthUpvotes(Statistics.get().getMonthUpvotes() + (weekend ? 2 : 1));
        GamesROB.getUserById(upvoteInfo.getAsJsonObject().get("user").getAsLong()).ifPresent(user -> user.openPrivateChannel().queue(pm -> {
            UserProfile profile = UserProfile.get(user.getId());
            if (System.currentTimeMillis() - profile.getLastUpvote() < TimeUnit.DAYS.toMillis(2))
                profile.setUpvotedDays(profile.getUpvotedDays() + 1);
            else profile.setUpvotedDays(0);
            int days = profile.getUpvotedDays();
            int amount = 125 + days * 50;
            if (weekend) amount *= 2;

            MessageBuilder builder = new MessageBuilder();
            String lang = Optional.ofNullable(profile.getLanguage()).orElse("en_US");

            builder.append(Language.transl(lang, "genericMessages.upvote.header", amount));
            if (weekend) builder.append(Language.transl(lang, "genericMessages.upvote.weekend"));
            if (days > 0) builder.append(Language.transl(lang, "genericMessages.upvote.streak", days));
            builder.append(Language.transl(lang, "genericMessages.upvote.footer"));

            AchievementType.REACH_TOKENS.addAmount(false, amount, builder, user, null, lang);
            AchievementType.UPVOTE.addAmount(false, 1, builder, user, null, lang);
            pm.sendMessage(builder.build()).queue();

            profile.addTokens(amount, "transactions.upvote");
            profile.setLastUpvote(System.currentTimeMillis());
            profile.setEdited(true);
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
