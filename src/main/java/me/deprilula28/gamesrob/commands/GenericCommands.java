package me.deprilula28.gamesrob.commands;

import me.deprilula28.gamesrob.BootupProcedure;
import me.deprilula28.gamesrob.GamesROB;
import me.deprilula28.gamesrob.Language;
import me.deprilula28.gamesrob.baseFramework.Match;
import me.deprilula28.gamesrob.data.GuildProfile;
import me.deprilula28.gamesrob.data.Statistics;
import me.deprilula28.gamesrob.utility.Cache;
import me.deprilula28.gamesrob.utility.Constants;
import me.deprilula28.gamesrob.utility.Log;
import me.deprilula28.gamesrob.utility.Utility;
import me.deprilula28.gamesrob.website.Website;
import me.deprilula28.jdacmdframework.CommandContext;
import me.deprilula28.jdacmdframework.CommandFramework;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDAInfo;
import net.dv8tion.jda.core.entities.MessageEmbed;

import java.awt.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GenericCommands {
    public static String ping(CommandContext context) {
        long now = System.currentTimeMillis();
        long youToBotPing = now - context.getMessage().getCreationTime().toInstant().toEpochMilli();

        context.getAuthor().openPrivateChannel().queue(it -> it.sendTyping().queue(n -> {
            long time = System.currentTimeMillis() - now;
            context.send(Language.transl(context, "command.ping.message",
                    Utility.formatPeriod(time),
                    Utility.formatPeriod(youToBotPing),
                    Utility.formatPeriod(context.getJda().getPing())
            ));
        }));

        return null;
    }
    public static MessageEmbed invite(CommandContext context) {
        return new EmbedBuilder()
                .setTitle(Language.transl(context, "command.invite.embed.title"), Constants.getInviteURL(context.getJda()))
                .setDescription(Language.transl(context, "command.invite.embed.description"))
                .setColor(Utility.randomBotColor())
                .build();
    }

    public static MessageEmbed info(CommandContext context) {
        GamesROB.getAllShards().then(shards -> {
            Statistics stats = Statistics.get();

            context.send(new EmbedBuilder()
                    .setAuthor("deprilula28#3609", null, "https://i.imgur.com/PPa4OzQ.png")
                    .setTitle("\uD83C\uDFAE GamesROB", Constants.GAMESROB_DOMAIN)
                    .setColor(Utility.randomBotColor())
                    .setDescription(Language.transl(context, "command.info.embed.description",
                            Constants.GAMESROB_DOMAIN, Constants.getInviteURL(context.getJda()),
                            Constants.getDboURL(context.getJda()),
                            "https://github.com/GamesROB/Bot", Constants.GAMESROB_DOMAIN + "/server?from=info"
                    ))
                    .addField(Language.transl(context, "command.info.embed.statistics.title"),
                            Language.transl(context, "command.info.embed.statistics.description",
                                    Utility.addNumberDelimitors(shards.stream().mapToInt(GamesROB.ShardStatus::getGuilds).sum()),
                                    Utility.addNumberDelimitors(shards.stream().mapToInt(GamesROB.ShardStatus::getUsers).sum()),
                                    Utility.addNumberDelimitors(shards.stream().mapToInt(GamesROB.ShardStatus::getTextChannels).sum()),
                                    Utility.addNumberDelimitors(stats.getGameCount()),
                                    Utility.addNumberDelimitors(shards.stream().mapToInt(GamesROB.ShardStatus::getActiveGames).sum()),
                                    Utility.addNumberDelimitors(stats.getUpvotes())
                            ), false)
                    .addField(Language.transl(context, "command.info.embed.versions.title"),
                            Language.transl(context, "command.info.embed.versions.description",
                                    GamesROB.VERSION, System.getProperty("java.version"),
                                    "https://github.com/DV8FromTheWorld/JDA", JDAInfo.VERSION,
                                    "https://github.com/deprilula28/DepsJDAFramework", CommandFramework.FRAMEWORK_VERSION,
                                    Utility.formatPeriod(System.currentTimeMillis() - GamesROB.UP_SINCE)
                            ), true)
                    .addField(Language.transl(context, "command.info.embed.system.title"),
                            Language.transl(context, "command.info.embed.system.description",
                                    Utility.getRAM(), System.getProperty("os.name")
                            ), true)
                    .addField(Language.transl(context, "command.info.embed.credits.title"),
                            Constants.GAMESROB_DOMAIN + "/help/credits", false)
                    .build());
        });
        return null;
    }

    public static String changelog(CommandContext context) {
        return Language.transl(context, "command.changelog.text", GamesROB.VERSION,
                Utility.formatTime(Statistics.get().getLastUpdateLogSentTime()), BootupProcedure.changelog,
                Utility.formatTime(Utility.predictNextUpdate()), Constants.GAMESROB_DOMAIN + "/server?from=changelog");
    }

    public static String shardsInfo(CommandContext context) {
        GamesROB.getAllShards().then(shards -> {
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
                                    .map(item -> Language.transl(context, "command.shardinfo." + item)).collect(Collectors.toList()),
                            shards.size(), texts)));
        });
        return null;
    }

    public static String setPrefix(CommandContext context) {
        GuildProfile.get(context.getGuild()).setGuildPrefix(context.next());
        return Language.transl(context, "command.setprefix.message");
    }
}
