package me.deprilula28.gamesrob.commands;

import me.deprilula28.gamesrob.BootupProcedure;
import me.deprilula28.gamesrob.GamesROB;
import me.deprilula28.gamesrob.Language;
import me.deprilula28.gamesrob.data.GuildProfile;
import me.deprilula28.gamesrob.data.PlottingStatistics;
import me.deprilula28.gamesrob.data.Statistics;
import me.deprilula28.gamesrob.data.UserProfile;
import me.deprilula28.gamesrobshardcluster.utilities.Constants;
import me.deprilula28.gamesrob.utility.Utility;
import me.deprilula28.gamesrobshardcluster.GamesROBShardCluster;
import me.deprilula28.gamesrobshardcluster.utilities.ShardClusterUtilities;
import me.deprilula28.jdacmdframework.CommandContext;
import me.deprilula28.jdacmdframework.CommandFramework;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDAInfo;
import net.dv8tion.jda.core.entities.MessageEmbed;
import net.dv8tion.jda.core.events.message.guild.react.GuildMessageReactionAddEvent;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static me.deprilula28.gamesrob.commands.Tokens.ENTRIES_PAGE;

public class GenericCommands {
    private static final String[] ICON_MAKERS = {
        "Freepik", "phatplus", "Good Ware", "smashicons"
    };

    public static String ping(CommandContext context) {
        long youToBotPing = System.currentTimeMillis() - context.getMessage().getCreationTime().toInstant().toEpochMilli();

        context.getAuthor().openPrivateChannel().queue(it -> {
            long now = System.currentTimeMillis();
            it.sendTyping().queue(n -> {
                long time = System.currentTimeMillis() - now;
                context.send(Language.transl(context, "command.ping.message",
                        ShardClusterUtilities.formatPeriod(time),
                        ShardClusterUtilities.formatPeriod(youToBotPing),
                        ShardClusterUtilities.formatPeriod(context.getJda().getPing()) + " (Shard " + context.getJda().getShardInfo().getShardId() + ")",
                        ShardClusterUtilities.formatPeriod(CommandManager.avgCommandDelay / 1000000000.0)
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
                    .addField(Language.transl(context, "command.info.embed2.statistics2.title"),
                            globalStatistics(context, stats, shards) +
                                    Language.transl(context, "command.info.embed2.statistics2.description",
                                            Utility.getPrefix(context.getGuild())), true)
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
                                    ShardClusterUtilities.formatPeriod(System.currentTimeMillis() - GamesROB.UP_SINCE)
                            ), true)
                    .addField(Language.transl(context, "command.info.embed2.system.title"),
                            Language.transl(context, "command.info.embed2.system.description2",
                                    ShardClusterUtilities.getRAM(), System.getProperty("os.name"), context.getJda().getShardInfo().getShardId() + 1,
                                    context.getJda().getShardInfo().getShardTotal(), ShardClusterUtilities.formatPeriod(context.getJda().getPing()),
                                    GamesROB.rpc.map(it -> it.isOpen() ? "<:online:313956277808005120>" :
                                            it.isConnecting() ? "<:invisible:313956277107556352>" :
                                            it.isClosing() ? "<:dnd:313956276893646850>" : "<:offline:313956277237710868>")
                                            .orElse("<:offline:313956277237710868>")
                            ), true)
                    .build();

            if (context.getEvent() instanceof GuildMessageReactionAddEvent) context.edit(embed);
            else context.send(embed).then(it -> it.addReaction("\uD83D\uDD01").queue());
        });
        return null;
    }

    public static String statistics(CommandContext context) {
        final long compareTime = System.currentTimeMillis() - context.opt(() -> ShardClusterUtilities.extractPeriod(context.next()))
            .orElse(TimeUnit.DAYS.toMillis(7));

        GamesROB.getAllShards().then(shards -> {
            try {
                Statistics stats = Statistics.get();
                UserProfile profile = UserProfile.get(context.getAuthor());

                ResultSet set = PlottingStatistics.getSetClosestTime(compareTime);
                boolean shouldAddOvertime = set.next();
                List<Map.Entry<String, Integer>> sortedCommands = Statistics.get().getPerCommandCount().entrySet()
                        .stream().sorted(Comparator.comparingInt(it -> ((Map.Entry<String, Integer>) it).getValue()).reversed())
                        .limit(5).collect(Collectors.toList());
                List<Map.Entry<String, Integer>> sortedGames = Statistics.get().getPerGameCount().entrySet()
                        .stream().sorted(Comparator.comparingInt(it -> ((Map.Entry<String, Integer>) it).getValue()).reversed())
                        .limit(5).collect(Collectors.toList());
                EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(Language.transl(context, "command.statistics.title"))
                    .setColor(Utility.getEmbedColor(context.getGuild()))
                    .setDescription(Language.transl(context, "command.statistics.userStatistics",
                            Utility.getAllMutualGuilds(context.getAuthor().getId()).size(),
                            Utility.addNumberDelimitors(profile.getTransactionAmount(Optional.of("upvote"))),
                            ShardClusterUtilities.formatPeriod(System.currentTimeMillis() - profile.getLastUpvote()),
                            Utility.addNumberDelimitors(profile.getGamesPlayed()),
                            ShardClusterUtilities.formatPeriod(profile.getGameplayTime())))
                    .setFooter(Language.transl(context, "command.statistics.footer"), null)
                    .setTimestamp(LocalDateTime.ofInstant(Instant.ofEpochMilli(set.getLong("time")),
                            ZoneId.systemDefault()))
                    .addField(Language.transl(context, "command.statistics.commandStatistics.title"),
                    Language.transl(context, "command.statistics.commandStatistics.mostUsed") +
                            sortedCommands.stream().map(it -> {
                                int index = sortedCommands.indexOf(it) + 1;
                                return String.format("**%s%s:** %s (%s)", index, Utility.formatNth(Utility.getLanguage(context), index),
                                        it.getKey(), Utility.addNumberDelimitors(it.getValue()));
                            }).collect(Collectors.joining("\n")), true)
                    .addField(Language.transl(context, "command.statistics.gameStatistics.title"),
                    Language.transl(context, "command.statistics.gameStatistics.mostPlayed") +
                        sortedGames.stream().map(it -> {
                            int index = sortedGames.indexOf(it) + 1;
                            return String.format("**%s%s:** %s (%s | %s)", index, Utility.formatNth(Utility.getLanguage(context), index),
                                    Language.transl(context, "game." + it.getKey() + ".name"), Utility.addNumberDelimitors(it.getValue()),
                                    ShardClusterUtilities.formatPeriod(Statistics.get().getPerGameGameplayTime().getOrDefault(it, 0L)));
                        }).collect(Collectors.joining("\n")), true);

                if (shouldAddOvertime) embed.addField(Language.transl(context, "command.statistics.globalStatistics"),
                    Language.transl(context, "command.statistics.overtimeStatistics",
                        compareStats(set, shards.stream().mapToInt(GamesROB.ShardStatus::getGuilds).sum(), "guilds", Utility::addNumberDelimitors),
                        compareStats(set, shards.stream().mapToInt(GamesROB.ShardStatus::getUsers).sum(), "users", Utility::addNumberDelimitors),
                        compareStats(set, shards.stream().mapToInt(GamesROB.ShardStatus::getTextChannels).sum(), "textChannels", Utility::addNumberDelimitors),
                        compareStats(set, Statistics.get().getGameCount(), "totalGamesPlayed", Utility::addNumberDelimitors),
                        compareStats(set, shards.stream().mapToInt(GamesROB.ShardStatus::getActiveGames).sum(), "activeGames", Utility::addNumberDelimitors),
                        compareStats(set, Statistics.get().getCommandCount(), "totalCommandsExecuted", Utility::addNumberDelimitors),
                        compareStats(set, Statistics.get().getUpvotes(), "upvotes", Utility::addNumberDelimitors),
                        ShardClusterUtilities.formatPeriod(Statistics.get().getGameplayTime()),
                        compareStats(set, (long) GamesROBShardCluster.shards.stream().mapToLong(JDA::getPing).average().getAsDouble(),
                                "websocketPing", ShardClusterUtilities::formatPeriod),
                        compareStats(set, (long) (CommandManager.avgCommandDelay * 1000000), "avgCommandDelay",
                                it -> ShardClusterUtilities.formatPeriod(it / 1000000000000000.0)),
                        compareStats(set, ShardClusterUtilities.getRawRAM(), "ramUsage", ShardClusterUtilities::formatBytes)
                    ), false);
                else embed.addField(Language.transl(context, "command.statistics.globalStatistics"),
                globalStatistics(context, stats, shards) + Language.transl(context, "command.statistics.aditionalGlobalStats",
                        ShardClusterUtilities.formatPeriod(context.getJda().getPing()),
                        ShardClusterUtilities.formatPeriod(CommandManager.avgCommandDelay / 1000000000.0)), false);

                set.close();
                context.send(embed.build());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        return null;
    }

    private static String compareStats(ResultSet set, long curValue, String rowName, Function<Long, String> formatter) throws SQLException  {
        long oldValue = set.getInt(rowName);
        long diff = curValue - oldValue;

        return String.format("**%s** *%s%s*",
                formatter.apply(curValue), diff >= 0 ? "+" : "-",
                formatter.apply(Math.abs(diff)));
    }

    private static String globalStatistics(CommandContext context, Statistics stats, List<GamesROB.ShardStatus> shards) {
        return Language.transl(context, "genericMessages.globalStatistics",
                Utility.addNumberDelimitors(shards.stream().mapToInt(GamesROB.ShardStatus::getGuilds).sum()),
                Utility.addNumberDelimitors(shards.stream().mapToInt(GamesROB.ShardStatus::getUsers).sum()),
                Utility.addNumberDelimitors(shards.stream().mapToInt(GamesROB.ShardStatus::getTextChannels).sum()),
                Utility.addNumberDelimitors(stats.getGameCount()),
                Utility.addNumberDelimitors(shards.stream().mapToInt(GamesROB.ShardStatus::getActiveGames).sum()),
                Utility.addNumberDelimitors(stats.getCommandCount()),
                Utility.addNumberDelimitors(stats.getUpvotes()),
                Utility.addNumberDelimitors(stats.getMonthUpvotes()),
                ShardClusterUtilities.formatPeriod(stats.getGameplayTime())
        );
    }

    public static String changelog(CommandContext context) {
        return Language.transl(context, "command.changelog.text", GamesROB.VERSION,
                ShardClusterUtilities.formatTime(Statistics.get().getLastUpdateLogSentTime()), BootupProcedure.changelog,
                ShardClusterUtilities.formatTime(Utility.predictNextUpdate()), "discord.gg/xajeDYR");
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
            texts.add(shards.stream().map(it -> ShardClusterUtilities.formatPeriod(it.getPing())).collect(Collectors.toList()));
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
        GuildProfile profile = GuildProfile.get(context.getGuild());
        profile.setGuildPrefix(context.next());
        profile.setEdited(true);

        return Language.transl(context, "command.setprefix.message");
    }

    public static String upvote(CommandContext context) {
        UserProfile profile = UserProfile.get(context.getAuthor());

        context.send(it -> {
            if (profile.getLastUpvote() != 0) {
                int upvotes = profile.getTransactionAmount(Optional.of("upvote"));
                it.append(Language.transl(context, "command.upvote.info",
                        ShardClusterUtilities.formatPeriod(System.currentTimeMillis() - profile.getLastUpvote()),
                        profile.getUpvotedDays(), ShardClusterUtilities.formatTime(profile.getLastUpvote() + TimeUnit.DAYS.toMillis(2)),
                        upvotes, ShardClusterUtilities.formatPeriod(System.currentTimeMillis() - 1533254400000L),
                        Utility.getPrefix(context.getGuild())));
            }
            if (System.currentTimeMillis() - profile.getLastUpvote() > TimeUnit.HOURS.toMillis(12)) {
                it.append(Language.transl(context, "command.upvote.messageCanVote",
                        125 + profile.getUpvotedDays() * 50 * (Utility.isWeekendMultiplier() ? 2 : 1)));
                it.setEmbed(new EmbedBuilder().setTitle(Language.transl(context, "command.upvote.clickToVote"),
                        Constants.getDblVoteUrl(context.getJda(), "upvoteCommand"))
                        .setColor(Utility.getEmbedColor(context.getGuild())).build());
            }
        });
        return null;
    }

    public static String upvoteHistory(CommandContext context) {
        UserProfile profile = UserProfile.get(context.getAuthor());
        int page = context.opt(context::nextInt).orElse(1);
        int elements = profile.getTransactionAmount();
        int pages = (elements / ENTRIES_PAGE) + 1;
        if (page <= 0 || page > pages) return Language.transl(context, "command.baltop.invalidPage", 1, pages);

        return Language.transl(context, "command.upvote.history.title") +
                profile.getTransactions(ENTRIES_PAGE, (page - 1) * ENTRIES_PAGE, Optional.of("upvote"))
                        .stream().map(it -> String.format("%s +\uD83D\uDD36 %s tokens",
                        ShardClusterUtilities.formatPeriod(System.currentTimeMillis() - it.getTime()), it.getAmount()
                )).collect(Collectors.joining("\n")) + Language.transl(context, "command.upvote.history.footer",
                        ENTRIES_PAGE, page, pages, Utility.getPrefix(context.getGuild()));
    }
}
