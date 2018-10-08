package me.deprilula28.gamesrob.commands;

import lombok.AllArgsConstructor;
import me.deprilula28.gamesrob.GamesROB;
import me.deprilula28.gamesrob.Language;
import me.deprilula28.gamesrob.data.UserProfile;
import me.deprilula28.gamesrob.utility.Constants;
import me.deprilula28.gamesrob.utility.Log;
import me.deprilula28.gamesrob.utility.Utility;
import me.deprilula28.jdacmdframework.CommandContext;
import me.deprilula28.jdacmdframework.exceptions.CommandArgsException;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.events.user.UserTypingEvent;
import net.dv8tion.jda.webhook.WebhookClient;
import net.dv8tion.jda.webhook.WebhookMessageBuilder;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class HalloweenEvent {
    public static Map<TextChannel, TotConnection> connections = new HashMap<>();
    private static List<Guild> connectedGuilds = new ArrayList<>();
    private static Optional<TotConnection> looking = Optional.empty();

    public static final Calendar EVENT_BEGIN_TIME = Calendar.getInstance();

    private static final long RATE_TIME_MIN = TimeUnit.SECONDS.toMillis(10);
    private static final long MAX_TIME = TimeUnit.MINUTES.toMillis(1);
    private static final int CANDY_EARN = 10;
    private static final int CANDY_LOSE = -5;

    static {
        EVENT_BEGIN_TIME.set(Calendar.DAY_OF_MONTH, 12);
        EVENT_BEGIN_TIME.set(Calendar.HOUR, 16);
        EVENT_BEGIN_TIME.set(Calendar.MINUTE, 0);
    }

    // Trick or treating Looking for connection
    @AllArgsConstructor
    private static class TotConnection extends Thread {
        private TextChannel hostChannel;
        private TextChannel proxyChannel;
        private String hostLanguage;
        private String proxyLanguage;
        private User askedFor;
        private Message message;
        private List<User> interacted;
        private long time;

        @Override
        public void run() {
            try {
                Thread.sleep(RATE_TIME_MIN);
                hostChannel.sendMessage(Language.transl(proxyLanguage, "command.trickortreating.rateTime",
                        CANDY_EARN, CANDY_LOSE)).queue(msg -> {
                    msg.addReaction("\uD83C\uDF6C").queue();
                    msg.addReaction("⛔").queue();
                });

                Thread.sleep(MAX_TIME - RATE_TIME_MIN);
                proxyChannel.sendMessage(Language.transl(proxyLanguage, "command.trickortreating.timeUp")).queue();
                hostChannel.sendMessage(Language.transl(hostLanguage, "command.trickortreating.timeUp")).queue();
                end();
            } catch (InterruptedException e) {
                Log.info("Interrupted TOT thread.");
            }
        }

        public void end() {
            interrupt();
            connections.remove(hostChannel);
            connections.remove(proxyChannel);
            connectedGuilds.remove(hostChannel.getGuild());
            connectedGuilds.remove(proxyChannel.getGuild());
        }
    }

    private static void checkEvent(CommandContext context) {
        long halloweenEvent = EVENT_BEGIN_TIME.getTimeInMillis();
        if (System.currentTimeMillis() < halloweenEvent)
            throw new CommandArgsException(Language.transl(context, "genericMessages.eventhasntbegan",
                    Utility.formatTime(halloweenEvent), Utility.formatTimeRegularFormat(halloweenEvent)));
    }

    public static String candy(CommandContext context) {
        checkEvent(context);
        User user = context.opt(context::nextUser).orElseGet(context::getAuthor);
        UserProfile profile = UserProfile.get(user);

        return user.equals(context.getAuthor())
                ? Language.transl(context, "command.candy.own", profile.getCandy())
                : Language.transl(context, "command.candy.other", user.getName(), profile.getCandy());
    }

    public static String trickOrTreatingMinigame(CommandContext context) {
        checkEvent(context);
        if (connectedGuilds.contains(context.getGuild())) return Language.transl(context, "command.trickortreating.alreadyConnected");

        boolean hasLooking = looking.isPresent();
        if (hasLooking) {
            TotConnection connection = HalloweenEvent.looking.get();
            if (connection.askedFor.equals(context.getAuthor())) return Language.transl(context, "command.trickortreating.alreadyLooking");

            // Connecting two channels
            connection.proxyChannel = context.getChannel();
            connection.proxyLanguage = Constants.getLanguage(context);
            connection.time = System.currentTimeMillis();

            connections.put(context.getChannel(), connection);
            connections.put(connection.hostChannel, connection);
            connectedGuilds.add(context.getGuild());
            HalloweenEvent.looking = Optional.empty();

            // Messages
            connection.message.editMessage(String.format(GamesROB.commandFramework.getSettings().getMessageFormat(),
                    Language.transl(connection.hostLanguage, "command.trickortreating.connected")
                    + Language.transl(connection.hostLanguage, "command.trickortreating.connectedHost",
                    Utility.formatPeriod(RATE_TIME_MIN)))).queue();
            context.send(Language.transl(connection.proxyLanguage, "command.trickortreating.connected")
                    + Language.transl(connection.proxyLanguage, "command.trickortreating.connectedProxy",
                    Utility.formatPeriod(RATE_TIME_MIN)));

            connection.setName("Trick or treating thread");
            connection.setDaemon(true);
            connection.start();
        } else {
            context.send(Language.transl(context, "command.trickortreating.findingChannel"))
                .then(message -> looking = Optional.of(new TotConnection(context.getChannel(), null,
                    Constants.getLanguage(context), null, context.getAuthor(), message, new ArrayList<>(), -1)));
            connectedGuilds.add(context.getGuild());
        }

        return null;
    }

    public static void reaction(CommandContext context, boolean candy) {
        if (context.getAuthor().isBot()) return;

        TotConnection connection = connections.get(context.getChannel());
        if (context.getChannel().equals(connection.proxyChannel)) return;
        long timePast = System.currentTimeMillis() - connection.time;

        if (timePast > RATE_TIME_MIN) {
            connection.end();
            int awardCandy = candy ? CANDY_EARN : CANDY_LOSE;

            connection.interacted.forEach(it -> {
                UserProfile profile = UserProfile.get(it);
                profile.setCandy(Math.max(0, profile.getCandy() + awardCandy));
                profile.setEdited(true);
            });
            context.send(Language.transl(context, candy ? "command.trickortreating.rateTreat" : "command.trickortreating.rateTrick",
                    Math.abs(awardCandy), connection.interacted.stream().map(User::getName)
                    .collect(Collectors.joining(", "))));
            connection.proxyChannel.sendMessage(Language.transl(connection.proxyLanguage,
                    candy ? "command.trickortreating.rateTreat" : "command.trickortreating.rateTrick",
                    Math.abs(awardCandy), connection.interacted.stream().map(User::getName)
                    .collect(Collectors.joining(", "))) + Language.transl(connection.proxyLanguage,
                    "command.trickortreating.candyCommand", Constants.getPrefix(connection.proxyChannel.getGuild()))).queue();
        } else context.send(Language.transl(context, "command.trickortreating.rateWait",
                Utility.formatPeriod(RATE_TIME_MIN - timePast)));
    }

    public static void typingTunneling(UserTypingEvent event) {
        if (event.getUser().isBot()) return;

        TotConnection connection = connections.get(event.getTextChannel());
        (connection.hostChannel.equals(event.getTextChannel()) ? connection.proxyChannel : connection.hostChannel).sendTyping().queue();
    }

    public static void messageTunneling(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;

        TotConnection connection = connections.get(event.getTextChannel());
        TextChannel target = connection.hostChannel.equals(event.getTextChannel()) ? connection.proxyChannel : connection.hostChannel;
        final String everyoneReplacement = "i am a very, very naughty boy";
        String message = event.getMessage().getContentRaw().replaceAll("@everyone", everyoneReplacement)
                .replaceAll("@here", everyoneReplacement)
                .replaceAll("(http|https|discord.gg\\/|discordapp.com\\/invite\\/).*?( |$|\\n)", "[link]")
                + (event.getMessage().getAttachments().isEmpty() ? "" : "\n[snip]");

        if (connection.proxyChannel.equals(event.getTextChannel()) && !connection.interacted.contains(event.getAuthor()))
            connection.interacted.add(event.getAuthor());

        if (Utility.hasPermission(target, target.getGuild().getMember(event.getJDA().getSelfUser()), Permission.MANAGE_WEBHOOKS))
            Utility.Promise.action(target.getWebhooks()).mapPromise(it -> it.isEmpty()
                    ? Utility.Promise.action(target.createWebhook("GamesROB Trick or Treating"))
                    : Utility.Promise.result(it.get(0))).then(webhook -> {
                WebhookClient client = webhook.newClient().build();
                WebhookMessageBuilder builder = new WebhookMessageBuilder();

                builder.setAvatarUrl(event.getAuthor().getEffectiveAvatarUrl());
                builder.setUsername(event.getAuthor().getName());
                builder.setContent(message);
                client.send(builder.build());
                client.close();
        });
        else target.sendMessage("**" + event.getAuthor().getName() + "**: " + message).queue();
    }
}
