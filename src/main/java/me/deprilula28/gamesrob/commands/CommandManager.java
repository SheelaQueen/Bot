package me.deprilula28.gamesrob.commands;

import lombok.AllArgsConstructor;
import lombok.Data;
import me.deprilula28.gamesrob.GamesROB;
import me.deprilula28.gamesrob.Language;
import me.deprilula28.gamesrob.baseFramework.GamesInstance;
import me.deprilula28.gamesrob.baseFramework.MatchHandler;
import me.deprilula28.gamesrob.baseFramework.Setting;
import me.deprilula28.gamesrob.data.GuildProfile;
import me.deprilula28.gamesrob.utility.Constants;
import me.deprilula28.gamesrob.utility.Log;
import me.deprilula28.jdacmdframework.Command;
import me.deprilula28.jdacmdframework.CommandContext;
import me.deprilula28.jdacmdframework.CommandFramework;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.events.guild.member.GuildMemberLeaveEvent;

import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CommandManager {
    public static Map<Class<? extends MatchHandler>, List<GameSettingValue>> matchHandlerSettings = new HashMap<>();
    private static Map<String, String> languageHelpMessages = new HashMap<>();

    @Data
    @AllArgsConstructor
    public static class GameSettingValue {
        private Field field;
        private Setting annotation;
    }

    public static void registerCommands(CommandFramework f) {
        f.command("join joinmatch play jm joinm jmatch", MatchCommands::join);
        f.command("leave leavematch quit lm lmatch leavem", MatchCommands::leave);
        f.command("stop stopmatch stopgame gamestop matchstop stap stahp",
                permissionLock(MatchCommands::stop, ctx -> GuildProfile.get(ctx.getGuild()).canStop(ctx)));
        f.command("listplayers players getplayers viewplayers playerlist", MatchCommands::listPlayers);
        f.command("leaderboard leaders winners lb leaderboards leaderb lboards lboard winnerlist",
                LeaderboardCommand::leaderboard);
        f.command("profile whois getprofile viewprofile", ProfileCommands::profile);
        f.command("tokens token viewtokens gettokens tokenamount", ProfileCommands::tokens);
        f.command("userlang ulang lang setlang", LanguageCommands::setUserLanguage);
        f.command("guildlang glang setglang",
                permissionLock(LanguageCommands::setGuildLanguage, ctx -> ctx.getAuthorMember()
                        .hasPermission(Permission.MANAGE_SERVER)));

        f.command("help ? games what commands cmds", CommandManager::help);
        f.command("invite addbot getbot getgrob", GenericCommands::invite);
        f.command("info information botinfo", GenericCommands::info);

        f.command("ping pingpong pong getping pinger botping botsping", GenericCommands::ping);
        f.command("shardinfo shards servers notspoofing shardinformation",
                GenericCommands::shardsInfo);

        f.command("perm setperm changeperm", permissionLock(PermissionCommands::changePerm,
                ctx -> ctx.getAuthorMember().hasPermission(Permission.MANAGE_SERVER))).setUsage("perm <command> [permission]");
        f.command("setprefix prefix changeprefix", permissionLock(GenericCommands::setPrefix,
                ctx -> ctx.getAuthorMember().hasPermission(Permission.MANAGE_SERVER))).setUsage("setprefix <Prefix>");
        f.command("emote emotetile changeemote setemote setemoji changeemoji tile etile emojit emotet " +
                "changetile settile et changeemojitile setemojitile emoji emojitile", ProfileCommands::emojiTile)
                .setUsage("emojitile <Emoji>");

        Language.getLanguageList().forEach(lang -> genHelpMessage(f.getCommands(), lang));
        Log.info("Generated help messages for ", languageHelpMessages.size() + " languages.");

        f.command("update", UpdateCommand::update);
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
                Constants.GAMESROB_DOMAIN + "/help"));
        languageHelpMessages.put(language, help.toString());
    }

    public static String help(CommandContext context) {
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
