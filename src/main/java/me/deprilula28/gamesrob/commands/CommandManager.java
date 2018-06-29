package me.deprilula28.gamesrob.commands;

import lombok.AllArgsConstructor;
import lombok.Data;
import me.deprilula28.gamesrob.GamesROB;
import me.deprilula28.gamesrob.Language;
import me.deprilula28.gamesrob.baseFramework.*;
import me.deprilula28.gamesrob.data.GuildProfile;
import me.deprilula28.gamesrob.data.Statistics;
import me.deprilula28.gamesrob.utility.Cache;
import me.deprilula28.gamesrob.utility.Constants;
import me.deprilula28.gamesrob.utility.Log;
import me.deprilula28.gamesrob.utility.Utility;
import me.deprilula28.jdacmdframework.Command;
import me.deprilula28.jdacmdframework.CommandContext;
import me.deprilula28.jdacmdframework.CommandFramework;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.events.message.guild.react.GuildMessageReactionAddEvent;

import java.lang.reflect.Field;
import java.security.acl.Owner;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CommandManager {
    public static Map<Class<? extends MatchHandler>, List<GameSettingValue>> matchHandlerSettings = new HashMap<>();
    private static Map<String, String> languageHelpMessages = new HashMap<>();
    private static Map<String, Long> commandStart = new ConcurrentHashMap<>();
    public static double avgCommandDelay = 0L;

    @Data
    @AllArgsConstructor
    public static class GameSettingValue {
        private Field field;
        private Setting annotation;
    }

    private static final String[] CATEGORIES = {
            "games", "profilecommands", "servercommands", "matchcommands", "infocommands"
    };
    private static final String[] EMOTES = {
            "\uD83C\uDFB2", "\uD83D\uDC65", "\uD83D\uDCDF", "\uD83C\uDFAE", "\uD83D\uDCCB"
    };
    private static final List<String> EMOTE_LIST = Arrays.asList(EMOTES);
    private static final Map<String, List<Command>> perCategory = new HashMap<>();

    public static void registerCommands(CommandFramework f) {
        // Games
        f.command("slots gamble gmb casino c", Slots::slotsGame).attr("category", "games").setUsage("slots <amount>");
        Arrays.stream(GamesROB.ALL_GAMES).forEach(cur -> {
            Command command = f.command(cur.getAliases(), Match.createCommand(cur)).attr("category", "games").attr("gameCode", cur.getLanguageCode());
            if (cur.getGameType() == GameType.MULTIPLAYER) command.setUsage(command.getName().toLowerCase() + " [Players] [Betting] [Settings]");

            List<GameSettingValue> settings = new ArrayList<>();
            Arrays.stream(cur.getMatchHandlerClass().getDeclaredFields()).filter(it -> it.isAnnotationPresent(Setting.class))
                .forEach(field -> settings.add(new GameSettingValue(field, field.getAnnotation(Setting.class))));
            matchHandlerSettings.put(cur.getMatchHandlerClass(), settings);
        });

        // Profile
        f.command("profile prof getprofile getprof viewprofile viewprof user usr getuser getusr viewuser viewusr player getplayer viewplayers", ProfileCommands::profile).attr("category", "profilecommands");
        f.command("tokens tk tks tok toks t viewtokens viewtk viewtks viewtok viewtoks viewt gettokens gettk gettks gettok gettoks gett tokenamount tkamount tokamount tamount achievements achieve achieved achieves ach viewachievements viewachieve viewachieved viewachieves viewach accomplishments accomplished viewaccomplishments viewaccomplished tasks task viewtasks viewtask missions mission viewmissions viewmission", ProfileCommands::tokens).attr("category", "profilecommands");
        f.command("lang language userlang userlanguage mylang mylanguage ", LanguageCommands::setUserLanguage).attr("category", "profilecommands");
        f.command("emoji changeemoji emojitile setemojitile setemoji emojis emoticons emoticon changeemoticon emoticontile setemoticon setemoticontile emote changeemote emotetile setemote setemotetile emotes tile changetile settile" +
                "depwhyaretheretwolinesforonecommandimscaredidkwhattodosothisisherehelphelphelphelp", ProfileCommands::emojiTile)
                .attr("category", "profilecommands").setUsage("emojitile <Emoji>");

        // Server
        f.command("leaderboard getleaderboard viewleaderboard checkleaderboard leaderboards getleaderboards viewleaderboards checkleaderboards board getboard viewboard checkboard boards getboards checkboards viewboards leader getleader checkleader viewleader leaders getleaders checkleaders viewleaders lb getlb checklb viewlb lbs getlbs checklbs viewlbs top gettop checktop viewtop",
                LeaderboardCommand::leaderboard).attr("category", "servercommands");
        f.command("guildlang changeguildlang setguildlang guildlanguage changeguildlanguage setguildlanguage glang changeglang setglang glanguage changeglanguage setglanguage serverlang changeserverlang setserverlang serverlanguage changeserverlanguage setserverlanguage slang changeslang setslang slanguage changeslanguage setslanguage",
                permissionLock(LanguageCommands::setGuildLanguage, ctx -> ctx.getAuthorMember()
                        .hasPermission(Permission.MANAGE_SERVER))).attr("category", "servercommands");
        f.command("perm setperm changeperm perms setperms changeperms permission setpermission changepermission permissions setpermissions changepermissions", permissionLock(PermissionCommands::changePerm,
                ctx -> ctx.getAuthorMember().hasPermission(Permission.MANAGE_SERVER))).attr("category", "servercommands")
                .setUsage("perm <command> [permission]");
        f.command("prefix setprefix changeprefix", permissionLock(GenericCommands::setPrefix,
                ctx -> ctx.getAuthorMember().hasPermission(Permission.MANAGE_SERVER)))
                .attr("category", "servercommands").setUsage("setprefix <Prefix>");

        // Match
        f.command("join jn joingame jg joinmatch jm", MatchCommands::join).attr("category", "matchcommands");
        f.command("stop stopgame stopmatch stopplaying staph stahp stap nodie",
                permissionLock(MatchCommands::stop, ctx -> GuildProfile.get(ctx.getGuild()).canStop(ctx))).attr("category", "matchcommands");
        f.command("listplayers players viewplayers getplayers checkplayers playerlist viewplayerlist getplayerlist checkplayerlist", MatchCommands::listPlayers).attr("category", "matchcommands");

        // Information
        f.command("invite invitebot invitegrob invitegamesrob add addbot addgrob addgamesrob get getbot getgrob getgamesrob getgood getgud", GenericCommands::invite).attr("category", "infocommands");
        f.command("info information botinfo botinformation helpbutwithdetails", GenericCommands::info).attr("category", "infocommands");
        f.command("changelog getchangelog viewchangelog log getlog viewlog clog getclog viewclog changes getchanges viewchanges version getversion viewversion ver getver viewver additions getaddions viewadditions whatsnew getwhatsnew viewwhatsnew", GenericCommands::changelog).attr("category", "infocommands");
        f.command("ping getping viewping seeping checkping pong getpong viewpong seepong checkpong connection getconnection viewconnection seeconnection checkconnection latency getlatency viewlatency seelatency checklatency latenci getlatenci viewlatenci seelatenci checklatenci pingpong getpingpong viewpingpong seepingpong checkpingpong pongping getpongping viewpongping seepongping checkpongping ms2 getms2 viewms2 seems2 checkms2", GenericCommands::ping)
                .attr("category", "infocommands");
        f.command("shard sharddetails getsharddetails viewsharddetails seesharddetails checksharddetails shardinfo getshardinfo viewshardinfo seeshardinfo checkshardinfo shardinformation getshardinformation viewshardinformation seeshardinformation checkshardinformation shardstuff getshardstuff viewshardstuff seeshardstuff checkshardstuff shards getshards viewshards seeshards checkshards", GenericCommands::shardsInfo)
                .attr("category", "infocommands");

        f.command("help halp games what wat uwot uwotm8 uwotm9  wtf tf ... ivefallenandicantgetup whatisgoingon imscared commands cmds ?", CommandManager::help, cmd -> {
            /*
            cmd.sub("developers developer dev devs d", context -> "My Developers are deprilula28#3609 and Fin#1337.");
            cmd.sub("discordstaff staff discstaff disc discs s", context -> "The staff in the GamesROB Discord are deprilula28#3609, Fin#1337, dirtify#3776, Not Hamel#5995, diniboy#0998, and Jazzy Spazzy#0691");
            cmd.sub("translators translator t", context -> "My translators are deprilula28#3609 (pt_BR), diniboy#0998 (hu_HU), Niekold#9410 (de_DE), Ephysios#1912 (fr_FR), and 0211#موهاماد هيف (ar_SA).");
            cmd.sub("libraries lib libs library frameworks fws fw framew rwork fworks framework dependancies dependancy f", context -> "We use the following frameworks/dependancies; Lombok, DepsJDAFramework, SLF4j, emoji-java, sqlite-driver (JDBC), snakeyaml, sparkjava, markdown4j, jade4j, Materialize, and JQuery.");
            cmd.sub("api apis", context -> "We use the Discord Developer API, Twitch API, and Discord Bot List's API.");
            cmd.sub("prgm programs prgms progrm progrms software sw softw sware", context -> "The programs we use for the bot's development are; JetBrains' IntelliJ IDEA, Git, and of course, Discord (More specifically; Discord Canary)");
            cmd.sub("webapps wapps wa was webapp services srvcs srvc service", context -> "We use OVH.");
            */

            for (String category : CATEGORIES) {
                if (!category.equals("games")) cmd.sub(category, context -> {
                    return categoryMessage(Constants.getLanguage(context), context.getGuild(), category);
                });
            }

            f.getCommands().forEach(cur -> {
                cmd.sub(String.join(" ", cur.getAliases()), cur.attr("gameCode") != null ? context -> {
                    String prefix = Constants.getPrefixHelp(context.getGuild());
                    String gameCode = cur.attr("gameCode");
                    return Language.transl(context, "command.help.gameInfo", prefix, cur.getName(),
                            Language.transl(context, "game." + gameCode + ".shortDescription"),
                            Language.transl(context, "game." + gameCode + ".longDescription"),
                            String.join(", ", cur.getAliases().stream().map(it -> "`" + prefix + it + "`")
                                    .collect(Collectors.toList())),
                            Constants.GAMESROB_DOMAIN + "/help/games/" + gameCode.toLowerCase());
                } : context -> {
                    String prefix = Constants.getPrefixHelp(context.getGuild());
                    return Language.transl(context, "command.help.commandInfo", prefix, cur.getName(),
                            Language.transl(context, "command." + cur.getName() + ".description"),
                            String.join(", ", cur.getAliases().stream().map(it -> "`" + prefix + it + "`")
                                    .collect(Collectors.toList())),
                            Language.transl(context, "command.help.categories." + cur.attr("category")));
                });
            });

            for (int i = 1; i < EMOTE_LIST.size(); i++) cmd.reactSub(EMOTE_LIST.get(i), CATEGORIES[i]);
            cmd.sub(" why dep_lied why_dep -_- really_dep really rlly dep_why", context -> "So dep said that he didn't want to hardcode owners, but I just now searched the bot code for his User ID, and found he hardcoded himself and not me. If you found this message, please DM dep and ask him why he did this and that he needs to stop abusing me, thank you.");
        }).attr("category", "infocommands");

        f.getCommands().forEach(cur -> {
            String category = cur.attr("category");
            if (!perCategory.containsKey(category)) perCategory.put(category, new ArrayList<>());
            perCategory.get(category).add(cur);
        });
        Language.getLanguageList().forEach(CommandManager::genHelpMessage);
        Log.info("Generated help messages for ", languageHelpMessages.size() + " languages.");

        f.command("update upd8 updep", OwnerCommands::update);
        f.command("eval evaluate ebal ebaluate", OwnerCommands::eval);
        f.command("bash con console commandconsole cmd commandprompt terminal term", OwnerCommands::console);
        f.command("sql postgres postgresql sqlexecute runsql", OwnerCommands::sql);
        f.command("announce announcement br broadcast ", OwnerCommands::announce);
        f.command("blacklist bl l8r adios cya pce peace later bye rekt dab", OwnerCommands::blacklist);
        f.command("gengif gifgen xdxdxdlmao", Gengif::gen);

        f.before(it -> {
            ResultSet set = Cache.get("bl_" + it.getAuthor().getId(), n -> {
                try {
                    ResultSet output = GamesROB.database.get().select("blacklist", Arrays.asList("userid", "botownerid", "reason", "time"),
                            "userid = '" + it.getAuthor().getId() + "'");
                    if (output.next()) return output;
                    else return null;
                } catch (Exception e) {
                    Log.exception("Getting blacklisted", e);
                    return null;
                }
            });
            if (set != null) {
                try {
                    return Language.transl(it, "genericMessages.blacklisted",
                            GamesROB.getUserById(set.getString("botownerid")).map(User::getName).orElse("*unknown*"),
                            Utility.formatTime(set.getLong("time")), set.getString("reason"));
                } catch (SQLException e) {
                    Log.exception("Getting blacklist info", e);
                    return Language.transl(it, "genericMessages.blacklistedNoInfo");
                }
            }

            commandStart.put(it.getAuthor().getId(), System.nanoTime());
            return null;
        });
        f.after(it -> {
            Statistics stats = Statistics.get();
            stats.setCommandCount(stats.getCommandCount() + 1);

            long delay = System.nanoTime() - commandStart.get(it.getAuthor().getId());
            commandStart.remove(it.getAuthor().getId());
            double singleCommandWeight = (1.0 / (double) stats.getCommandCount());
            avgCommandDelay = (int) (avgCommandDelay * (1.0 - singleCommandWeight) + delay * singleCommandWeight);

            return null;
        });

        // Door
        f.reactionHandler("\uD83D\uDEAA", context -> {
            if (Match.GAMES.containsKey(context.getChannel())) Match.GAMES.get(context.getChannel()).joinReaction(context);
        });
        f.reactionHandler("\uD83D\uDD04", context -> {
            if (Match.REMATCH_GAMES.containsKey(context.getChannel())) Match.REMATCH_GAMES.get(context.getChannel()).rematchReaction(context);
        });

        f.getSettings().setMentionedMessageGetter(guild -> {
            String lang = GuildProfile.get(guild).getLanguage();
            return languageHelpMessages.get(lang == null ? Constants.DEFAULT_LANGUAGE : lang)
                    .replaceAll("%PREFIX%", Constants.getPrefixHelp(guild));
        });
    }

    private static String categoryMessage(String language, Guild guild, String category) {
        String prefix = Constants.getPrefixHelp(guild);
        return Language.transl(language, "command.help.categories." + category) + "\n"
                + perCategory.get(category).stream().map(it -> Language.transl(language, "command.help.gameString",
                "", it.getName(),
                Language.transl(language, "command." + it.getName() + ".description")
        )).collect(Collectors.joining("\n")).replaceAll("%PREFIX%", prefix);
    }

    private static void genHelpMessage(String language) {
        StringBuilder help = new StringBuilder(Language.transl(language, "command.help.beginning", GamesROB.VERSION));

        for (int i = 0; i < CATEGORIES.length; i++) {
            String category = CATEGORIES[i];
            help.append(EMOTES[i]).append(" ").append(Language.transl(language, "command.help.categories." + category));
            if (category.equals("games")) {
                help.append("\n");
                perCategory.get(category).forEach(cur -> {
                    String gameCode = cur.attr("gameCode");
                    help.append(gameCode == null
                        ? Language.transl(language, "command.help.gameString",
                            "", cur.getName(),
                            Language.transl(language, "command." + cur.getName() + ".description"))
                        : Language.transl(language, "command.help.gameString",
                                Language.transl(language, "game." + gameCode + ".name"),
                                cur.getAliases().get(0),
                            Language.transl(language, "game." + gameCode + ".shortDescription")
                        ))
                    .append("\n");
                });
            } else {
                help.append(" `").append(category).append("` (");
                List<Command> catCommands = perCategory.get(category);
                if (catCommands.size() <= 3) help.append(catCommands.stream().map(Command::getName).collect(Collectors.joining(", ")));
                else {
                    List<String> strings = catCommands.stream().limit(2).map(Command::getName).collect(Collectors.toList());
                    strings.add(Language.transl(language, "command.help.other", catCommands.size() - 2));
                    help.append(String.join(", ", strings));
                }
                help.append(")");
            }
            help.append("\n");
        }

        help.append("\n").append(Language.transl(language, "command.help.subCategory"));
        languageHelpMessages.put(language, help.toString());
    }

    public static String help(CommandContext context) {
        EmbedBuilder embed = new EmbedBuilder().setColor(Utility.getEmbedColor(context.getGuild()))
                .setTitle(Language.transl(context, "command.help.websiteTitle"), Constants.GAMESROB_DOMAIN);
        OwnerCommands.getAnnouncement().ifPresent(announcement -> {
            User announcer = announcement.getAnnouncer();

            embed.appendDescription(":loudspeaker: " + announcement.getMessage());
            embed.setFooter(announcer.getName() + "#" + announcer.getDiscriminator(), null);

            Calendar time = Calendar.getInstance();
            time.setTimeInMillis(announcement.getAnnounced());
            embed.setTimestamp(time.toInstant());
        });

        context.send(builder -> builder.append(languageHelpMessages.get(Constants.getLanguage(context))
                .replaceAll("%PREFIX%", Constants.getPrefixHelp(context.getGuild()))).setEmbed(
                embed.build()))
            .then(message -> {
                for (int i = 0; i < EMOTES.length; i++) {
                    if (!CATEGORIES[i].equals("games")) message.addReaction(EMOTES[i]).queue();
                }}
            );
        return null;
    }

    public static Command.Executor permissionLock(Command.Executor command, Function<CommandContext, Boolean> func) {
        return context -> {
            if (!func.apply(context)) return Language.transl(context, "command.permissionLock");
            return command.execute(context);
        };
    }
}
