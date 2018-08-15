package me.deprilula28.gamesrob.commands;

import me.deprilula28.gamesrob.BootupProcedure;
import me.deprilula28.gamesrob.GamesROB;
import me.deprilula28.gamesrob.Language;
import me.deprilula28.gamesrob.data.GuildProfile;
import me.deprilula28.gamesrob.data.Statistics;
import me.deprilula28.gamesrob.utility.Constants;
import me.deprilula28.gamesrob.utility.Utility;
import me.deprilula28.jdacmdframework.CommandContext;
import me.deprilula28.jdacmdframework.CommandFramework;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDAInfo;
import net.dv8tion.jda.core.entities.MessageEmbed;
import net.dv8tion.jda.core.events.message.guild.react.GuildMessageReactionAddEvent;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GenericCommands {
    public static String ping(CommandContext context) {
        long youToBotPing = System.currentTimeMillis() - context.getMessage().getCreationTime().toInstant().toEpochMilli();

        context.getAuthor().openPrivateChannel().queue(it -> {
            long now = System.currentTimeMillis();
            it.sendTyping().queue(n -> {
                long time = System.currentTimeMillis() - now;
                context.send(Language.transl(context, "command.ping.message",
                        Utility.formatPeriod(time),
                        Utility.formatPeriod(youToBotPing),
                        Utility.formatPeriod(context.getJda().getPing()) + " (Shard " + context.getJda().getShardInfo().getShardId() + ")",
                        Utility.formatPeriod(CommandManager.avgCommandDelay / 1000000000.0)
                ));
            });
        });

        return null;
    }

    public static MessageEmbed invite(CommandContext context) {
        return new EmbedBuilder()
                .setTitle(Language.transl(context, "command.invite.embed.title"), Constants.getInviteURL(context.getJda()))
                .setFooter(Language.transl(context, "command.invite.embed.description"), "https://i.imgur.com/em1fKRC.png")
                .setColor(Utility.getEmbedColor(context.getGuild()))
                .build();
    }

    public static MessageEmbed info(CommandContext context) {
        GamesROB.getAllShards().then(shards -> {
            Statistics stats = Statistics.get();
            MessageEmbed embed = new EmbedBuilder()
                    .setAuthor("deprilula28#3609", null, "https://i.imgur.com/PPa4OzQ.png")
                    .setTitle("\uD83C\uDFAE GamesROB", Constants.GAMESROB_DOMAIN)
                    .setColor(Utility.getEmbedColor(context.getGuild()))
                    .setDescription(Language.transl(context, "command.info.embed2.description"))
                    .addField(Language.transl(context, "command.info.embed2.statistics.title"),
                            Language.transl(context, "command.info.embed2.statistics.description",
                                    Utility.addNumberDelimitors(shards.stream().mapToInt(GamesROB.ShardStatus::getGuilds).sum()),
                                    Utility.addNumberDelimitors(shards.stream().mapToInt(GamesROB.ShardStatus::getUsers).sum()),
                                    Utility.addNumberDelimitors(shards.stream().mapToInt(GamesROB.ShardStatus::getTextChannels).sum()),
                                    Utility.addNumberDelimitors(stats.getGameCount()),
                                    Utility.addNumberDelimitors(shards.stream().mapToInt(GamesROB.ShardStatus::getActiveGames).sum()),
                                    Utility.addNumberDelimitors(stats.getCommandCount()),
                                    Utility.addNumberDelimitors(stats.getUpvotes())
                            ) + Language.transl(context, "command.info.embed2.statistics.month",
                                    Utility.addNumberDelimitors(Statistics.get().getMonthUpvotes())), true)
                    .addField(Language.transl(context, "command.info.embed2.links.title"),
                            Language.transl(context, "command.info.embed2.links.description",
                                    Constants.GAMESROB_DOMAIN, Constants.getInviteURL(context.getJda()),
                                    "https://github.com/GamesROB/Bot", "https://discord.gg/xajeDYR",
                                    Constants.GAMESROB_DOMAIN + "/help/credits", Constants.getDblVoteUrl(context.getJda(), "info")
                            ), true)
                    .addField(Language.transl(context, "command.info.embed2.versions.title"),
                            Language.transl(context, "command.info.embed2.versions.description",
                                    GamesROB.VERSION, System.getProperty("java.version"),
                                    "https://github.com/DV8FromTheWorld/JDA", JDAInfo.VERSION,
                                    "https://github.com/deprilula28/DepsJDAFramework", CommandFramework.FRAMEWORK_VERSION,
                                    Utility.formatPeriod(System.currentTimeMillis() - GamesROB.UP_SINCE)
                            ), true)
                    .addField(Language.transl(context, "command.info.embed2.system.title"),
                            Language.transl(context, "command.info.embed2.system.description",
                                    Utility.getRAM(), System.getProperty("os.name"), context.getJda().getShardInfo().getShardId() + 1,
                                    context.getJda().getShardInfo().getShardTotal(), Utility.formatPeriod(context.getJda().getPing())
                            ), true)
                    .build();

            if (context.getEvent() instanceof GuildMessageReactionAddEvent) context.edit(embed);
            else context.send(embed).then(it -> it.addReaction("\uD83D\uDD01").queue());
        });
        return null;
    }

    public static String changelog(CommandContext context) {
        return Language.transl(context, "command.changelog.text", GamesROB.VERSION,
                Utility.formatTime(Statistics.get().getLastUpdateLogSentTime()), BootupProcedure.changelog,
                Utility.formatTime(Utility.predictNextUpdate()), "discord.gg/xajeDYR");
    }

    private static String getEmoteForStatus(String status) {
        switch (status) {
            case "CONNECTED":
                return "<:online:313956277808005120>";
            case "SHUTDOWN":
            case "FAILED_TO_LOGIN":
            case "RECONNECT_QUEUED":
            case "WAITING_TO_RECONNECT":
            case "DISCONNECTED":
                return "<:offline:313956277237710868>";
            case "ATTEMPTING_TO_RECONNECT":
            case "LOGGING_IN":
            case "CONNECTING_TO_WEBSOCKET":
            case "IDENTIFYING_SESSION":
            case "AWAITING_LOGIN_CONFIRMATION":
            case "LOADING_SUBSYSTEMS":
                return "<:invisible:313956277107556352>";
            default:
                return "<:dnd:313956276893646850>";
        }
    }

    public static String shardsInfo(CommandContext context) {
        GamesROB.getAllShards().then(shards -> {
            /*
            shards.add(new GamesROB.ShardStatus("Total", shards.stream().mapToInt(GamesROB.ShardStatus::getGuilds).sum(),
                    shards.stream().mapToInt(GamesROB.ShardStatus::getUsers).sum(),
                    shards.stream().mapToInt(GamesROB.ShardStatus::getTextChannels).sum(),
                    // Status
                    Arrays.stream(JDA.Status.values()).filter(a -> shards.stream().anyMatch(it -> it.getStatus().equals(a.toString())))
                            .map(a -> Utility.addNumberDelimitors(shards.stream().filter(it -> it.getStatus().equals(a.toString())).count())
                                    + " " + a.toString()).collect(Collectors.joining(", ")),
                    // Latency avarage
                    new BigDecimal(shards.stream().mapToLong(GamesROB.ShardStatus::getPing).average().orElse(0.0))
                            .setScale(0, BigDecimal.ROUND_HALF_UP).longValue(),
                    // Games total
                    shards.stream().mapToInt(GamesROB.ShardStatus::getActiveGames).sum()));

            context.send(Language.transl(context, "command.shardinfo.newTitle") + "\n" +
                shards.stream().map(it -> Language.transl(context, "command.shardinfo.singleShard",
                    it.getId().equals("Total") ? "" : getEmoteForStatus(it.getStatus()),
                        it.getId() + (it.getStatus().equals(context.getJda().getShardInfo().getShardId() + "") ? " <<" : ""),
                        it.getGuilds(), new BigDecimal(((double) it.getGuilds() / 2500.0D) * 100D)
                                .setScale(1, BigDecimal.ROUND_HALF_UP),
                        it.getTextChannels(), it.getUsers(), Utility.formatPeriod(it.getPing()), it.getActiveGames()
                )).collect(Collectors.joining("\n")));

            */
            shards.add(new GamesROB.ShardStatus("TOTAL", shards.stream().mapToInt(GamesROB.ShardStatus::getGuilds).sum(),
                    shards.stream().mapToInt(GamesROB.ShardStatus::getUsers).sum(),
                    shards.stream().mapToInt(GamesROB.ShardStatus::getTextChannels).sum(),
                    // Status
                    Arrays.stream(JDA.Status.values()).filter(a -> shards.stream().anyMatch(it -> it.getStatus().equals(a.toString())))
                            .map(a -> Utility.addNumberDelimitors(shards.stream().filter(it -> it.getStatus().equals(a.toString())).count())
                                    + " " + a.toString()).collect(Collectors.joining(", ")),
                    // Latency avarage
                    new BigDecimal(shards.stream().mapToLong(GamesROB.ShardStatus::getPing).average().orElse(0.0))
                            .setScale(0, BigDecimal.ROUND_HALF_UP).longValue(),
                    // Games total
                    shards.stream().mapToInt(GamesROB.ShardStatus::getActiveGames).sum()));

            List<List<String>> texts = new ArrayList<>();
            texts.add(shards.stream().map(GamesROB.ShardStatus::getId).collect(Collectors.toList()));
            texts.add(shards.stream().map(it -> Utility.addNumberDelimitors(it.getGuilds()))
                    .collect(Collectors.toList()));
            texts.add(shards.stream().map(it -> Utility.addNumberDelimitors(it.getUsers()))
                    .collect(Collectors.toList()));
            texts.add(shards.stream().map(it -> Utility.addNumberDelimitors(it.getTextChannels()))
                    .collect(Collectors.toList()));
            texts.add(shards.stream().map(GamesROB.ShardStatus::getStatus).collect(Collectors.toList()));
            texts.add(shards.stream().map(it -> Utility.formatPeriod(it.getPing())).collect(Collectors.toList()));
            texts.add(shards.stream().map(it -> Utility.addNumberDelimitors(it.getActiveGames()))
                    .collect(Collectors.toList()));

            context.send(Language.transl(context, "command.shardinfo.title",
                    Utility.generateTable(
                            Stream.of("shard", "guilds", "users", "channels", "status", "ping", "games")
                                    .map(item -> Language.transl(context, "command.shardinfo." + item))
                                    .collect(Collectors.toList()),
                            shards.size(), texts)));
        });
        return null;
    }

    public static String setPrefix(CommandContext context) {
        GuildProfile.get(context.getGuild()).setGuildPrefix(context.next());
        return Language.transl(context, "command.setprefix.message");
    }
}
