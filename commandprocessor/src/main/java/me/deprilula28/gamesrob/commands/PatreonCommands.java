package me.deprilula28.gamesrob.commands;

import lombok.AllArgsConstructor;
import lombok.Data;
import me.deprilula28.gamesrob.GamesROB;
import me.deprilula28.gamesrob.data.GuildProfile;
import me.deprilula28.gamesrob.data.PremiumGuildMember;
import me.deprilula28.gamesrob.data.UserProfile;
import me.deprilula28.gamesrob.utility.Cache;
import me.deprilula28.gamesrob.utility.Language;
import me.deprilula28.gamesrob.utility.Utility;
import me.deprilula28.gamesrobshardcluster.utilities.Constants;
import me.deprilula28.gamesrobshardcluster.utilities.Log;
import me.deprilula28.gamesrobshardcluster.utilities.ShardClusterUtilities;
import me.deprilula28.jdacmdframework.CommandContext;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.User;

import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class PatreonCommands {
    private static final long TOURNMENT_TIME_LIMIT = TimeUnit.DAYS.toMillis(30);
    private static final int TOURNMENT_SHOW_TOP = 10;

    public static String profileBackground(CommandContext context) {
        UserProfile profile = UserProfile.get(context.getAuthor());
        if (!profile.getPatreonPerks().contains(UserProfile.PatreonPerk.VIP))
            return Language.transl(context, "command.backgroundupload.nonpatreon", Constants.PATREON_URL);

        String url = context.next();
        if (!url.startsWith("https://i.imgur.com/")) return Language.transl(context, "command.backgroundupload.invalidlink");

        profile.setBackgroundImageUrl(url);
        profile.setEdited(true);
        
        return Language.transl(context, "command.backgroundupload.setimg");
    }

    public static String tournment(CommandContext context) {
        GuildProfile profile = GuildProfile.get(context.getGuild());
        long tournmentEnds = profile.getTournmentEnds();

        if (tournmentEnds <= 0L) return Language.transl(context, "command.tournment.notstarted",
                Utility.getPrefix(context.getGuild()));
        if (tournmentEnds > System.currentTimeMillis()) return Language.transl(context, "command.tournment.askend",
                Utility.getPrefix(context.getGuild()));

        try {
            ResultSet position = GamesROB.database.orElseThrow(() -> new RuntimeException("")).sqlFileQuery(
                    "selectTournmentPosition.sql", statement -> Log.wrapException("Getting SQL position", () -> {
                statement.setString(1, context.getGuild().getId());
                statement.setString(2, context.getAuthor().getId());
            }), "guildid = '" + context.getGuild().getId() + "'");

            int pos = position.next() ? position.getInt("rank") : -1;
            StringBuilder builder = new StringBuilder(Language.transl(context, "command.tournment.leaderboard.header",
                    ShardClusterUtilities.formatTime(tournmentEnds), TOURNMENT_SHOW_TOP));

            appendLeaderboard(builder, context);

            if (pos > TOURNMENT_SHOW_TOP) builder.append("...\n").append(Language.transl(context,
                        "command.tournment.leaderboard.entry", String.format(
                    "**%s%s**: %s",
                    pos, Utility.formatNth(Utility.getLanguage(context), pos), context.getAuthor().getName()
            ), PremiumGuildMember.get(context.getAuthor(), context.getGuild()).getTournmentWins()));

            return builder.toString();
        } catch (Exception e) {
            Log.info(e);
            throw new RuntimeException(e);
        }
    }

    public static String tournmentCreate(CommandContext context) {
        long period = ShardClusterUtilities.extractPeriod(context.next());

        if (period > TOURNMENT_TIME_LIMIT) return Language.transl(context, "command.tournment.timelimit",
                ShardClusterUtilities.formatPeriod(TOURNMENT_TIME_LIMIT));

        GuildProfile profile = GuildProfile.get(context.getGuild());
        if (profile.getTournmentEnds() <= 0L) return Language.transl(context, "command.tournment.alreadystarted",
                ShardClusterUtilities.formatTime(profile.getTournmentEnds()), Utility.getPrefix(context.getGuild()));

        long time = System.currentTimeMillis() + period;

        profile.setTournmentEnds(time);
        profile.setEdited(true);
        return Language.transl(context, "command.tournment.timeset", ShardClusterUtilities.formatTimeRegularFormat(time));
    }

    public static String tournmentEnd(CommandContext context) {
        GuildProfile profile = GuildProfile.get(context.getGuild());
        long tournmentEnds = profile.getTournmentEnds();

        if (tournmentEnds <= 0L) return Language.transl(context, "command.tournment.notstarted",
                Utility.getPrefix(context.getGuild()));
        if (tournmentEnds <= System.currentTimeMillis()) return Language.transl(context, "command.tournment.notended",
                Utility.getPrefix(context.getGuild()));

        try {
            StringBuilder builder = new StringBuilder(Language.transl(context, "command.tournment.leaderboard.endheader"));
            appendLeaderboard(builder, context);

            context.getAuthor().openPrivateChannel().queue(dm -> dm.sendMessage(Language.transl(context,
                    "command.tournment.enddm", Utility.getPrefix(context.getGuild()))).queue());

            GamesROB.database.orElseThrow(() -> new RuntimeException("Why no db")).update("premiumguildmember",
                    Collections.singletonList("tournmentwins"), "guildid = '" + context.getAuthor().getId() + "'",
                    statement -> Log.wrapException("SQL error purging tournment wins", () -> statement.setInt(1, 0)));

            return builder.toString();
        } catch (Exception e) {
            Log.info(e);
            throw new RuntimeException(e);
        }
    }

    public static String tournmentPurge(CommandContext context) {
        GuildProfile profile = GuildProfile.get(context.getGuild());
        if (profile.getTournmentEnds() <= 0L) return Language.transl(context, "command.tournment.notstarted",
                Utility.getPrefix(context.getGuild()));

        GamesROB.database.orElseThrow(() -> new RuntimeException("Why no db")).update("premiumguildmember",
                Collections.singletonList("tournmentwins"), "guildid = '" + context.getAuthor().getId() + "'",
                statement -> Log.wrapException("SQL error purging tournment wins", () -> statement.setInt(1, 0)));
        Cache.clearAll();
        profile.setTournmentEnds(0L);

        return Language.transl(context, "command.tournment.purge");
    }

    public static void appendLeaderboard(StringBuilder builder, CommandContext context) throws Exception {
        ResultSet topMembers = GamesROB.database.orElseThrow(() -> new RuntimeException("What why no DB that's rude tbh"))
                .select("premiumguildmember", Arrays.asList("userid", "tournmentwins"),
                "guildid = '" + context.getGuild().getId() + "'", "tournmentwins", true, TOURNMENT_SHOW_TOP, 0);

        int i = 1;
        while (topMembers.next()) {
            builder.append(Language.transl(context, "command.tournment.leaderboard.entry", String.format(
                    "**%s%s**: %s",
                    i, Utility.formatNth(Utility.getLanguage(context), i),
                    GamesROB.getUserById(topMembers.getString("userid")).map(User::getName).orElse("*not found*")
            ), topMembers.getInt("tournmentwins")));
            i ++;
        }
    }

    public static String customEco(CommandContext context) {
        boolean hasCustomEco = GuildProfile.get(context.getGuild()).isCustomEconomy();
        String prefix = Utility.getPrefix(context.getGuild());

        if (hasCustomEco) return Language.transl(context, "command.customeco.enabled")
                + Language.transl(context, "command.customeco.enableddesc", prefix, prefix, prefix);
        else return Language.transl(context, "command.customeco.disabled")
                + Language.transl(context, "command.customeco.disableddesc", prefix);
    }

    public static String customEcoToggle(CommandContext context) {
        GuildProfile profile = GuildProfile.get(context.getGuild());
        String prefix = Utility.getPrefix(context.getGuild());
        boolean hasCustomEco = profile.isCustomEconomy();
        profile.setCustomEconomy(!hasCustomEco);
        profile.setEdited(true);

        return Language.transl(context, "command.customeco.togglemessage", String.valueOf(!hasCustomEco),
                context.getGuild().getName()) + (hasCustomEco
                ? Language.transl(context, "command.customeco.disableddesc", prefix)
                : Language.transl(context, "command.customeco.enableddesc", prefix, prefix, prefix));
    }

    private static final Map<Guild, PurgeTask> PURGE_TASKS = new HashMap<>();
    private static final long PURGE_TIME_PERIOD = TimeUnit.SECONDS.toMillis(20);

    @AllArgsConstructor
    @Data
    public static class PurgeTask extends Thread {
        private CommandContext context;

        @Override
        public void run() {
            try {
                Thread.sleep(PURGE_TIME_PERIOD);
                context.edit(Language.transl(context, "command.customeco.purgebegin"));
                GamesROB.database.ifPresent(db -> db.delete("premiumguildmember", "guildid = '" + context.getGuild().getId() + "'").then(n -> {
                    GuildProfile profile = GuildProfile.get(context.getGuild());
                    profile.setCustomEconomy(false);
                    profile.setEdited(true);
                    Cache.clearAll();
                    context.edit(Language.transl(context, "command.customeco.purgefinish", Utility.getPrefix(context.getGuild())));
                }));
            } catch (InterruptedException e) { }
            catch (Exception e) {
                Log.exception("Running purge task", e, context);
            }
        }
    }

    public static String customEcoPurge(CommandContext context) {
        if (PURGE_TASKS.containsKey(context.getGuild())) {
            PurgeTask task = PURGE_TASKS.get(context.getGuild());
            task.interrupt();
            task.getContext().edit(Language.transl(context, "command.customeco.purgecancelled"));
            return null;
        }

        PurgeTask task = new PurgeTask(context);
        PURGE_TASKS.put(context.getGuild(), task);
        task.setName("CustomEco Purge Delayed Task");
        task.setDaemon(false);
        task.start();

        return Language.transl(context, "command.customeco.purgewarning", ShardClusterUtilities.formatPeriod(PURGE_TIME_PERIOD),
                Utility.getPrefix(context.getGuild()));
    }

    public static String patreon(CommandContext context) {
        List<UserProfile.PatreonPerk> perks = UserProfile.get(context.getAuthor()).getPatreonPerks();
        return Language.transl(context, "command.patreon.text", Constants.PATREON_URL, Language.transl(context, "command.patreon." + (
                perks.contains(UserProfile.PatreonPerk.INSIDER) ? "insiders" :
                perks.contains(UserProfile.PatreonPerk.PREMIUM) ? "premium" :
                perks.contains(UserProfile.PatreonPerk.VIP) ? "vip" :
                perks.contains(UserProfile.PatreonPerk.SUPPORTER) ? "supporter" :
                "none"
        )));
    }
}
