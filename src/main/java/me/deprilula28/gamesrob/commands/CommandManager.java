package me.deprilula28.gamesrob.commands;

import lombok.AllArgsConstructor;
import lombok.Data;
import me.deprilula28.gamesrob.GamesROB;
import me.deprilula28.gamesrob.Language;
import me.deprilula28.gamesrob.baseFramework.GamesInstance;
import me.deprilula28.gamesrob.baseFramework.MatchHandler;
import me.deprilula28.gamesrob.baseFramework.Setting;
import me.deprilula28.gamesrob.data.GuildProfile;
import me.deprilula28.gamesrob.data.Statistics;
import me.deprilula28.gamesrob.utility.Constants;
import me.deprilula28.gamesrob.utility.Log;
import me.deprilula28.jdacmdframework.Command;
import me.deprilula28.jdacmdframework.CommandContext;
import me.deprilula28.jdacmdframework.CommandFramework;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.events.guild.member.GuildMemberLeaveEvent;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class CommandManager {
    public static Map<Class<? extends MatchHandler>, List<GameSettingValue>> matchHandlerSettings = new HashMap<>();
    private static Map<String, String> languageHelpMessages = new HashMap<>();
    private static Map<String, Long> commandStart = new ConcurrentHashMap<>();
    public static long avgCommandDelay = 0L;

    @Data
    @AllArgsConstructor
    public static class GameSettingValue {
        private Field field;
        private Setting annotation;
    }

    public static void registerCommands(CommandFramework f) {
        f.command("slots gamble gmb casino c", Slots::slotsGame).setUsage("slots <amount>");

        f.command("join joinmatch play jm joinm jmatch j", MatchCommands::join);
        f.command("stop stopmatch stopgame gamestop matchstop stap stahp s",
                permissionLock(MatchCommands::stop, ctx -> GuildProfile.get(ctx.getGuild()).canStop(ctx)));
        f.command("listplayers players getplayers viewplayers playerlist y", MatchCommands::listPlayers);
        f.command("leaderboard leaders winners board boards lb lbs leaderboards leaderb lboards lboard winnerlist b",
                LeaderboardCommand::leaderboard);
        f.command("profile whois getprofile viewprofile v", ProfileCommands::profile);
        f.command("tokens tk tks tok token viewtokens gettokens tokenamount t", ProfileCommands::tokens);
        f.command("userlang ulang lang setlang language u", LanguageCommands::setUserLanguage);
        f.command("guildlang glang setglang serverlang setslang setserverlang setguildlang g",
                permissionLock(LanguageCommands::setGuildLanguage, ctx -> ctx.getAuthorMember()
                        .hasPermission(Permission.MANAGE_SERVER)));

        f.command("help games what ivefallenandicantgetup whatisgoingon commands cmds ?", CommandManager::help, cmd -> {
            cmd.sub("developers developer dev devs d", context -> "My Developers are deprilula28#3609 and Fin#1337.");
            cmd.sub("discordstaff staff discstaff disc discs s", context -> "The staff in the GamesROB Discord are deprilula28#3609, Fin#1337, dirtify#3776, Not Hamel#5995, diniboy#0998, and Jazzy Spazzy#0691");
            cmd.sub("translators translator t", context -> "My translators are deprilula28#3609 (pt_BR), diniboy#0998 (hu_HU), Niekold#9410 (de_DE), Ephysios#1912 (fr_FR), and 0211#موهاماد هيف (ar_SA).");
            cmd.sub("commands command cmd cmds c", context -> "GOING TO DO THIS LATER BECAUSE IM LAZY");
            cmd.sub("libraries lib libs library frameworks fws fw framew rwork fworks framework dependancies dependancy f", context -> "We use the following frameworks/dependancies; Lombok, DepsJDAFramework, SLF4j, emoji-java, sqlite-driver (JDBC), snakeyaml, sparkjava, markdown4j, jade4j, Materialize, and JQuery.");
            cmd.sub("api apis", context -> "We use the Discord Developer API, Twitch API, and Discord Bot List's API.");
            cmd.sub("prgm programs prgms progrm progrms software sw softw sware", context -> "The programs we use for the bot's development are; JetBrains' IntelliJ IDEA, Git, and of course, Discord (More specifically; Discord Canary)");
            cmd.sub("webapps wapps wa was webapp services srvcs srvc service", context -> "We use GSuite (Business), Google Domains, and OVH for web apps.");

            cmd.sub("i_like_easter_eggs", context -> "Fin was here Fin was here Fin was here Fin was here Fin was here Fin was here Fin was here Fin was here Fin was here Fin was here Fin was here Fin was here Fin was here Fin was here (lol)");
        });

        f.command("invite addbot getbot getgrob add getgamesrob getbot get a", GenericCommands::invite);
        f.command("info information botinfo i", GenericCommands::info);
        f.command("changelog cl chgl version latestversion versioninfo ver r", GenericCommands::changelog);

        f.command("ping pingpong pong getping pinger botping botsping latency connection p", GenericCommands::ping);
        f.command("shardinfo shards servers notspoofing shardinformation h",
                GenericCommands::shardsInfo);

        f.command("perm setperm changeperm m", permissionLock(PermissionCommands::changePerm,
                ctx -> ctx.getAuthorMember().hasPermission(Permission.MANAGE_SERVER))).setUsage("perm <command> [permission]");
        f.command("setprefix prefix changeprefix f", permissionLock(GenericCommands::setPrefix,
                ctx -> ctx.getAuthorMember().hasPermission(Permission.MANAGE_SERVER))).setUsage("setprefix <Prefix>");
        f.command("emote emotetile changeemote setemote setemoji changeemoji tile etile emojit emotet e" +
                "changetile settile et changeemojitile setemojitile emoji emojitile", ProfileCommands::emojiTile)
                .setUsage("emojitile <Emoji>");

        Language.getLanguageList().forEach(lang -> genHelpMessage(f.getCommands(), lang));
        Log.info("Generated help messages for ", languageHelpMessages.size() + " languages.");

        f.command("update", UpdateCommand::update);
        f.command("eval", EvalCommand::eval);

        f.before(it -> {
            commandStart.put(it.getAuthor().getId(), System.currentTimeMillis());
            return null;
        });
        f.after(it -> {
            Statistics stats = Statistics.get();
            stats.setCommandCount(stats.getCommandCount() + 1);

            long delay = System.currentTimeMillis() - commandStart.get(it.getAuthor().getId());
            commandStart.remove(it.getAuthor().getId());
            double singleCommandWeight = (1.0 / (double) stats.getCommandCount());
            avgCommandDelay = (int) (avgCommandDelay * (1.0 - singleCommandWeight) + delay * singleCommandWeight);

            return null;
        });

        f.getSettings().setMentionedMessageGetter(guild -> {
            String lang = GuildProfile.get(guild).getLanguage();
            return languageHelpMessages.get(lang == null ? Constants.DEFAULT_LANGUAGE : lang)
                    .replaceAll("%PREFIX%", Constants.getPrefix(guild));
        });
        f.handleEvent(GuildMemberLeaveEvent.class, event -> {
            GuildProfile guild = GuildProfile.get(event.getGuild());
            synchronized (guild) {
                if (guild.getUserStatisticsMap() != null) guild.getUserStatisticsMap().remove(event.getUser().getId());
                if (guild.getOverall() != null) guild.getOverall().forEach(entry -> {
                    if (entry.getId().equals(event.getUser().getId())) guild.getOverall().remove(entry);
                });
                if (guild.getPerGame() != null) guild.getPerGame().forEach((key, value) -> {
                    value.forEach(entry -> {
                        if (entry.getId().equals(event.getUser().getId())) guild.getOverall().remove(entry);
                    });
                });
            }
        });
    }

    private static void genHelpMessage(List<Command> commands, String language) {
        StringBuilder help = new StringBuilder(Language.transl(language, "command.help.beginning"));

        /*
        commands.forEach(command -> {
            String code = command.getAliases().get(0).toLowerCase();
            help.append(String.format(
                "`%%PREFIX%%%s` - %s\n",
                code, Language.transl(language, "command." + code + ".description")
            ));
        });

        help.append(Language.transl(language, "command.help.games"));
        */
        for (GamesInstance game : GamesROB.ALL_GAMES) {
            List<GameSettingValue> settings = new ArrayList<>();
            Arrays.stream(game.getMatchHandlerClass().getDeclaredFields()).filter(it -> it.isAnnotationPresent(Setting.class))
                    .forEach(field -> settings.add(new GameSettingValue(field, field.getAnnotation(Setting.class))));
            matchHandlerSettings.put(game.getMatchHandlerClass(), settings);

            help.append(Language.transl(language, "command.help.gameString",
                    game.getName(language), game.getAliases().substring(0, game.getAliases().indexOf(" ")),
                    game.getShortDescription(language)
            )).append("\n");
        }

        help.append("\n").append(Language.transl(language, "command.help.moreInfo",
                Constants.GAMESROB_DOMAIN + "/help/"));
        languageHelpMessages.put(language, help.toString());
    }

    public static String help(CommandContext context) {
        Log.trace("Help command handler");
        return languageHelpMessages.get(Constants.getLanguage(context))
                .replaceAll("%PREFIX%", Constants.getPrefix(context.getGuild()));
    }

    public static Command.Executor permissionLock(Command.Executor command, Function<CommandContext, Boolean> func) {
        return context -> {
            if (!func.apply(context)) return Language.transl(context, "command.permissionLock");
            return command.execute(context);
        };
    }
}
