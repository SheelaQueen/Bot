package me.deprilula28.gamesrob.commands;

import me.deprilula28.gamesrob.GamesROB;
import me.deprilula28.gamesrob.utility.Language;
import me.deprilula28.gamesrob.data.GuildProfile;
import me.deprilula28.gamesrob.data.Statistics;
import me.deprilula28.gamesrob.data.UserProfile;
import me.deprilula28.gamesrob.events.HalloweenEvent;
import me.deprilula28.gamesrob.utility.Cache;
import me.deprilula28.gamesrob.utility.Permissions;
import me.deprilula28.gamesrobshardcluster.utilities.Constants;
import me.deprilula28.gamesrobshardcluster.utilities.Log;
import me.deprilula28.gamesrob.utility.Utility;
import javafx.util.Pair;
import lombok.AllArgsConstructor;
import lombok.Data;
import me.deprilula28.gamesrob.baseFramework.*;
import me.deprilula28.gamesrobshardcluster.utilities.ShardClusterUtilities;
import me.deprilula28.jdacmdframework.Command;
import me.deprilula28.jdacmdframework.CommandContext;
import me.deprilula28.jdacmdframework.CommandFramework;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.events.channel.text.TextChannelDeleteEvent;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.core.events.user.UserTypingEvent;
import net.dv8tion.jda.core.exceptions.PermissionException;

import javax.imageio.ImageIO;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class CommandManager {
    public static Map<Class<? extends MatchHandler>, List<GameSettingValue>> matchHandlerSettings = new HashMap<>();
    private static Map<String, String> languageHelpMessages = new HashMap<>();
    public static Map<String, Long> commandStart = new ConcurrentHashMap<>();
    public static Map<Pair<User, String>, Long> cooldowns = new ConcurrentHashMap<>();
    public static double avgCommandDelay = 0.0;

    private static final String[] CATEGORIES = new String[]{
            "games", "tokencommands", "profilecommands", "servercommands", "matchcommands", "infocommands", "partnercommands", "eventcommands"
    };
    private static final String[] EMOTES = new String[]{
            "\ud83c\udfb2", "\ud83d\udd38", "\ud83d\udc64", "\ud83d\udcdf", "\ud83c\udfae", "\ud83d\udccb", "\ud83e\udd1d", "\uD83C\uDF86"
    };

    private static final List<String> PREFERRED_CATEGORIES = Arrays.asList("games");    
    private static final List<String> EMOTE_LIST = Arrays.asList(EMOTES);
    private static final Map<String, List<Command>> perCategory = new HashMap<>();

    private static String finMessage = "";

    public static void registerCommands(CommandFramework f) {
        // Halloween Events
        f.command("trickortreating trickortreat trickotreating tot", HalloweenEvent::trickOrTreatingMinigame)
                .attr("category", "eventcommands").attr("permissions", "MESSAGE_ADD_REACTION");
        f.command("candy halloweentokens candycurrency candybal", HalloweenEvent::candy)
                .attr("category", "eventcommands");

        // Games
        Arrays.stream(GamesROB.ALL_GAMES).forEach(cur -> {
            Command command = f.command(cur.getAliases(), Match.createCommand(cur)).attr("category", "games")
                    .attr("gameCode", cur.getLanguageCode()).attr("cooldown", "5s");
            if (cur.getGameType() == GameType.MULTIPLAYER || cur.getGameType() == GameType.HYBRID) {
                command.setUsage(command.getName().toLowerCase() + " [Players] [Betting] [Settings]");
            }
            ArrayList settings = new ArrayList();
            Arrays.stream(cur.getMatchHandlerClass().getDeclaredFields()).filter(it -> it.isAnnotationPresent(Setting.class)).forEach(field -> settings.add(new GameSettingValue((Field)field, field.getAnnotation(Setting.class))));
            matchHandlerSettings.put(cur.getMatchHandlerClass(), settings);
        });

        // Partners
        f.command("idlerpg idle rpg idlerpgbot", CommandManager.partnerCommand("https://discordbots.org/bot/idlerpg"))
                .attr("category", "partnercommands");
        f.command("discordserversme dsm discordservers serverlist", CommandManager.partnerCommand("http://discordservers.me"))
                .attr("category", "partnercommands");

        // Tokens
        f.command("slots c slot lotto lottery gamble gmb gmbl", Slots::slotsGame)
                .attr("category", "tokencommands").attr("cooldown", "3s")
                .setUsage("slots <amount/all>");
        f.command("achievements a achieve achieved achieves ach viewachievements viewachieve viewachieved " +
                "viewachieves viewach accomplishments accomplished viewaccomplishments viewaccomplished tasks" +
                " task viewtasks viewtask missions mission viewmissions viewmission", Tokens::achievements)
                .attr("category", "tokencommands").attr("permissions", "MESSAGE_EMBED_LINKS")
                .attr("cooldown", "3s");
        f.command("baltop b balancetop topbalance rich tokensleaderboard tokenslb tklb", Tokens::baltop)
                .attr("category", "tokencommands").attr("cooldown", "3s").setUsage("baltop [global] [page]");

        f.command("upvote upvotes vote votes upvoteinfo voteinfo up ups daily", GenericCommands::upvote, cmd -> {
            cmd.sub("set setdays", OwnerCommands.tokenCommand((profile, days) -> {
                profile.setUpvotedDays(days);
                profile.setLastUpvote(System.currentTimeMillis());
            }, "command.upvote.setdays"));
            cmd.sub("history his lastvotes", GenericCommands::upvoteHistory).setUsage("upvote history <page>");
        }).attr("category", "servercommands");

        // Profile
        f.command("profile p prof getprofile getprof viewprofile viewprof user usr getuser getusr viewuser viewusr " +
                "player getplayer viewplayers rank tokens token pfptokens t tk tks tok toks viewtokens viewtk viewtks " +
                "viewtok viewtoks viewt gettokens gettk gettks gettok gettoks gett tokenamount tkamount tokamount tamount " +
                "bal balance viewbalance money cash $ dollars dollar bucks \ufdfc \u20be \u20bd \u20bc \u20ba \u20b9 \u20b8" +
                " \u20b5 \u20b4 \u20b2 \u20b1 \u20ae \u20ad \u20ac \u20ab \u20a9 \u20a8 \u20a7 \u20a6 \u20a1 \u0e3f \u058f " +
                "\u0564\u0580. \u043b\u0432 \u0434\u0438\u043d \u0434\u0435\u043d \u0192 \u00a5 \u5143 \u00a3 $ USD BRL PHP " +
                "\u0441\u045e\u043c \u0bb0\u0bc2  \u0dbb\u0dd4  robux RUB R$ K\u010d C$ B/. \u0783 imjuanrichboiiiilmaoxd ",
                CommandManager.imageCommand(ProfileCommands::profile), cmd -> {
            cmd.sub("a add p addition additions additive plus insertplussignhere + U+002B hax",
                    OwnerCommands.tokenCommand((profile, tokens) -> profile.addTokens(tokens, "transactions.cheater"),
                    "command.tokens.add"));
            cmd.sub("r take k d takeaway minus delete remove - rem eternal_sadness_dot_jpeg remisabadbot cheat",
                    OwnerCommands.tokenCommand((profile, tokens) -> profile.addTokens(- tokens.intValue(), "transactions.cheater"),
                    "command.tokens.remove"));

            cmd.sub("s set e hak", OwnerCommands.tokenCommand((profile, tokens) -> {
                profile.setTokens(tokens);
                profile.setEdited(true);
            }, "command.tokens.set"));

            cmd.sub("t transactions tr taxes do_your_taxes transactts payments bought history salary view", Tokens::transactions);
            cmd.sub("g give pay repay giveto send sendnudes", context -> {
                User target = context.nextUser();
                if (CommandManager.getBlacklist(target.getId()).isPresent()) return Language.transl(context, "command.tokens.giveInvalidUser");
                int amount = context.nextInt();
                if (amount < 10 || amount > 5000) return Language.transl(context, "command.tokens.giveInvalidAmount", 10, 5000);

                if (!UserProfile.get(context.getAuthor()).transaction(amount, "transactions.give"))
                    return Utility.getNotEnoughTokensMessage(context, amount);
                UserProfile.get(target).addTokens(amount, "transactions.got");
                return Language.transl(context, "command.tokens.give", context.getAuthor().getAsMention(), target.getAsMention(), amount);
            }).setUsage("g*token give <user> <amount>");
        }).attr("category", "profilecommands").attr("permissions", "MESSAGE_ATTACH_FILES,MESSAGE_EMBED_LINKS")
            .attr("cooldown", "3s").setUsage("profile [user]");

        f.command("userlang u lang language userlanguage mylang mylanguage", LanguageCommands::setUserLanguage).attr("category", "profilecommands");
        f.command("emote emojitile e emoji changeemoji setemojitile setemoji emojis emoticons emoticon changeemoticon emoticontile setemoticon setemoticontile changeemote emotetile setemote setemotetile emotes tile changetile settile depwhyaretheretwolinesforonecommandimscaredidkwhattodosothisisherehelphelphelphelp", ProfileCommands::emojiTile).attr("category", "profilecommands").setUsage("emojitile <Emoji>");

        // Server
        f.command("leaderboard y getleaderboard viewleaderboard checkleaderboard leaderboards getleaderboards " +
                "viewleaderboards checkleaderboards board getboard viewboard checkboard boards getboards checkboards " +
                "viewboards leader getleader checkleader viewleader leaders getleaders checkleaders viewleaders lb " +
                "getlb checklb viewlb lbs getlbs checklbs viewlbs top gettop checktop viewtop",
                CommandManager.imageCommand(LeaderboardCommand::leaderboard))
                .attr("category", "servercommands").attr("permissions", "MESSAGE_ATTACH_FILES")
                .attr("cooldown", "3s");
        f.command("guildlang g changeguildlang setguildlang guildlanguage changeguildlanguage setguildlanguage glang changeglang setglang glanguage changeglanguage setglanguage serverlang changeserverlang setserverlang serverlanguage changeserverlanguage setserverlanguage slang changeslang setslang slanguage changeslanguage setslanguage", LanguageCommands::setGuildLanguage).attr("adminlockdefault", "true").attr("category", "servercommands");
        f.command("perm % pr setperm changeperm perms setperms changeperms permission setpermission changepermission permissions setpermissions changepermissions", PermissionCommands::permission, cmd -> {
            cmd.sub("allow add permadd a +", PermissionCommands.permissionEdit(Permissions.PermCommandMode.ALLOW));
            cmd.sub("deny remove permremove r -", PermissionCommands.permissionEdit(Permissions.PermCommandMode.DENY));
            cmd.sub("neutral neutralize removeentry =", PermissionCommands.permissionEdit(Permissions.PermCommandMode.NEUTRAL));
            cmd.sub("lock l /", PermissionCommands.permissionEdit(Permissions.PermCommandMode.LOCK));
            cmd.sub("check view", PermissionCommands::permissionCheck);
        }).attr("adminlockdefault", "true").attr("category", "servercommands")
        .setUsage("perm <allow/neutral/deny/check/lock> [category/channel] [user/role] <permission> [game/command]");

        f.command("setprefix * prefix changeprefix", GenericCommands::setPrefix)
                .attr("adminlockdefault", "true").attr("category", "servercommands")
                .setUsage("setprefix <Prefix>");

        // Match
        f.command("leave l lv leavegame lg leavematch lm quit", MatchCommands::leave).attr("cateogry", "matchcommands");
        f.command("join j jn joingame jg joinmatch jm", MatchCommands::join).attr("category", "matchcommands");
        f.command("stop s stopgame stopmatch stopplaying staph stahp stap nodie", MatchCommands::stop).attr("category", "matchcommands");
        f.command("listplayers & players viewplayers getplayers checkplayers playerlist viewplayerlist getplayerlist checkplayerlist", MatchCommands::listPlayers).attr("category", "matchcommands");
        f.command("invite i invitebot invitegrob invitegamesrob add addbot addgrob addgamesrob get getbot getgrob getgamesrob getgood getgud", GenericCommands::invite).attr("category", "infocommands").attr("permissions", "MESSAGE_EMBED_LINKS");

        // Information
        f.command("info ? information botinfo botinformation helpbutwithdetails", GenericCommands::info, it -> it.sub("reload", GenericCommands::info)).reactSub("\ud83d\udd01", "reload").attr("category", "infocommands").attr("permissions", "MESSAGE_EMBED_LINKS");
        f.command("statistics stats viewstats viewstatistics getstatistics", GenericCommands::statistics).attr("category", "infocommands").attr("permissions", "MESSAGE_EMBED_LINKS");
        f.command("changelog getchangelog viewchangelog log getlog viewlog clog getclog viewclog changes getchanges viewchanges version getversion viewversion ver getver viewver additions getaddions viewadditions whatsnew getwhatsnew viewwhatsnew", GenericCommands::changelog).attr("category", "infocommands").attr("permissions", "MESSAGE_EMBED_LINKS");
        f.command("ping getping viewping seeping checkping pong getpong viewpong seepong checkpong connection getconnection viewconnection seeconnection checkconnection latency getlatency viewlatency seelatency checklatency latenci getlatenci viewlatenci seelatenci checklatenci pingpong getpingpong viewpingpong seepingpong checkpingpong pongping getpongping viewpongping seepongping checkpongping ms2 getms2 viewms2 seems2 checkms2", GenericCommands::ping).attr("category", "infocommands");
        f.command("shardinfo sharddetails getsharddetails viewsharddetails seesharddetails checksharddetails shard getshardinfo viewshardinfo seeshardinfo checkshardinfo shardinformation getshardinformation viewshardinformation seeshardinformation checkshardinformation shardstuff getshardstuff viewshardstuff seeshardstuff checkshardstuff shards getshards viewshards seeshards checkshards", GenericCommands::shardsInfo).attr("category", "infocommands");
        f.command("support bug report glitch bugs error glitches errors server", CommandManager.messageCommand()).attr("category", "infocommands").attr("permissions", "MESSAGE_EMBED_LINKS");
        f.command("website web site online internet interweb gamesrobcom gamesrobdotcom gamesrob.com .com dotcom com", CommandManager.messageCommand()).attr("category", "infocommands").attr("permissions", "MESSAGE_EMBED_LINKS");

        f.command("help h halp games what wat uwot uwotm8 uwotm9  wtf tf ... ivefallenandicantgetup whatisgoingon imscared commands cmds imgoingtoexplode please~~sendnudes~~*help*me ineedassistance", CommandManager::help, cmd -> {
            cmd.sub("back bbbb bbb bb b aaaa aaa aa a cccc ccc cc c kkkk kkk kk k bac bccc bcck " +
                    "bckk bac bak bacc bacccccccccccccc bacwith20cs", CommandManager::help);

            for (String category : CATEGORIES) {
                if (!PREFERRED_CATEGORIES.contains(category)) cmd.sub(category, context -> {
                    try { context.getMessage().addReaction("⬅").queue(); } catch (PermissionException e) {}
                    return categoryMessage(Utility.getLanguage(context), context.getGuild(), category);
                });
            }

            f.getCommands().forEach(cur -> {
                cmd.sub(String.join(" ", cur.getAliases()), cur.attr("gameCode") != null ? context -> {
                    String prefix = Utility.getPrefixHelp(context.getGuild());
                    String language = Utility.getLanguage(context);
                    String gameCode = cur.attr("gameCode");
                    GamesInstance game = Arrays.stream(GamesROB.ALL_GAMES).filter(it -> it.getLanguageCode().equalsIgnoreCase(gameCode))
                            .findFirst().orElseThrow(() -> new RuntimeException("The game code doesn't correspond to a game!"));

                    return Language.transl(context, "command.help.gameInfo2", prefix, cur.getName(),
                            Language.transl(context, "game." + gameCode + ".shortDescription"),
                            Language.transl(context, "game." + gameCode + ".longDescription"),
                            CommandManager.matchHandlerSettings.get(game.getMatchHandlerClass()).stream()
                                    .map(it -> Language.transl(context, "command.help.setting",
                                            it.field.getName(), it.annotation.min(), it.annotation.max(),
                                            it.annotation.defaultValue()))
                                    .collect(Collectors.joining("\n")),
                            game.getModes().isEmpty() ? "" : Language.transl(context, "command.help.modes",
                                    game.getModes().stream().map(it -> String.format("%s `%s` - %s",
                                            it.getName(language), it.getAliases(), it.getDescription(language)))
                                            .collect(Collectors.joining("\n"))),
                            String.join(", ", cur.getAliases().stream().map(it -> "`" + prefix + it + "`")
                                    .collect(Collectors.toList())),
                            Constants.GAMESROB_DOMAIN + "/help/games/" + gameCode.toLowerCase());
                } : context -> {
                    String prefix = Utility.getPrefixHelp(context.getGuild());
                    return Language.transl(context, "command.help.commandInfo", prefix, cur.getName(),
                            Language.transl(context, "command." + cur.getName() + ".description"),
                            String.join(", ", cur.getAliases().stream().map(it -> "`" + prefix + it + "`")
                                    .collect(Collectors.toList())),
                            Language.transl(context, "command.help.categories." + cur.attr("category")));
                });
            });

            for (int i = PREFERRED_CATEGORIES.size(); i < EMOTE_LIST.size(); i++) cmd.reactSub(EMOTE_LIST.get(i), CATEGORIES[i]);
        }).reactSub("⬅", "back").attr("category", "infocommands")
                .attr("permissions", "MESSAGE_EMBED_LINKS");

        f.getCommands().forEach(cur -> {
            String category = cur.attr("category");
            if (!perCategory.containsKey(category)) perCategory.put(category, new ArrayList<>());
            perCategory.get(category).add(cur);
        });

        Language.getLanguageList().forEach(CommandManager::genHelpMessage);
        Log.info("Generated help messages for ", "" + languageHelpMessages.size() + " languages.");

        // Owner Commadns
        f.command("^ update upd8 updep dabbinguncontrollably newupdate thiswasaddedin1.7.4", OwnerCommands::updateCommand);
        f.command("/ eval evaluate ebal ebaluate ejaculate", OwnerCommands::eval);
        f.command("> bash con console commandconsole cmd commandprompt terminal term", OwnerCommands::console);
        f.command("# sql postgres postgresql sqlexecute runsql", OwnerCommands::sql);
        f.command("! announce announcement br broadcast", OwnerCommands::announce);
        f.command("< blacklist bl l8r adios cya pce peace later bye rekt dab", OwnerCommands::blacklist);
        f.command("| cache", OwnerCommands::cache);
        f.command(". servercount srvcount svc", OwnerCommands::servercount);
        f.command("\u00a8 compilelanguage cl21", OwnerCommands::compileLanguage);
        f.command("reload rl", OwnerCommands::reload);
        f.command("restart shutdown kill die death deathisgood ripbot", OwnerCommands::restart);
        f.command("+ badges badge bdg bg", OwnerCommands::badges);
        f.command("0 1 2 3 4 5 6 7 8 9 $ @ whymustyoumentioneveryone fin finmessage finmsg fintime meme memes maymays maymay meemee dankmeme dank", context -> finMessage, command -> command.sub("set", context -> {
            if (!GamesROB.owners.contains(context.getAuthor().getIdLong()))
                return Language.transl(context, "genericMessages.ownersOnly");
            finMessage = String.join(" ", context.remaining());
            return "dank maymay set.";
        }));

        OwnerCommands.owners(f);
        f.before(it -> {
            if (commandStart.containsKey(it.getAuthor().getId())) return Language.transl(it, "genericMessages.tooFast");

            // Blacklist
            Optional<Blacklist> optBl = CommandManager.getBlacklist(it.getAuthor().getId());
            if (optBl.isPresent()) {
                Blacklist blacklist = optBl.get();
                return Language.transl(it, "genericMessages.blacklisted", 
                        blacklist.getBotOwner().map(User::getName).orElse("*No longer an owner*"),
                        ShardClusterUtilities.formatTime(blacklist.getTime()), blacklist.getReason());
            }

            // Bot Permissions
            String permissions = it.getCurrentCommand().attr("permissions");
            if (permissions != null && !it.getGuild().getMember(it.getJda().getSelfUser()).hasPermission(Permission.ADMINISTRATOR)) {
                List missing = Arrays.stream(permissions.split(",")).map(Permission::valueOf)
                        .filter(perm -> !Utility.hasPermission(it.getChannel(), it.getGuild()
                            .getMember(it.getJda().getSelfUser()), perm)).map(Objects::toString)
                    .collect(Collectors.toList());
                if (!missing.isEmpty()) return Language.transl(it, "genericMessages.requirespermissions",
                        String.join(", ", missing));
            }

            // User Permissions
            if (!Permissions.check(it)) {
                if (it.getCurrentCommand().attr("adminlockdefault") == null) {
                    return Language.transl(it, "genericMessages.cmdPermDenied");
                }
                return Language.transl(it, "genericMessages.cmdPermDeniedAdmin");
            }

            // Cooldown
            String cooldown = it.getCurrentCommand().attr("cooldown");
            if (cooldown != null) {
                Pair<User, String> key = new Pair<>(it.getAuthor(), it.getCurrentCommand().getName());
                if (cooldowns.containsKey(key) && System.currentTimeMillis() < cooldowns.get(key)) {
                    return Language.transl(it, "genericMessages.cooldown", ShardClusterUtilities
                            .formatPeriod(cooldowns.get(key) - System.currentTimeMillis()));
                } else cooldowns.put(key, System.currentTimeMillis() + ShardClusterUtilities.extractPeriod(cooldown));
            }

            if (it.getCurrentCommand().attr("gameCode") == null) {
                Statistics.get().registerCommand(it);
            }
            commandStart.put(it.getAuthor().getId(), System.nanoTime());
            return null;
        });
        f.after(it -> {
            Statistics stats = Statistics.get();
            stats.setCommandCount(stats.getCommandCount() + 1L);
            if (commandStart.containsKey(it.getAuthor().getId())) {
                long delay = System.nanoTime() - commandStart.get(it.getAuthor().getId());
                commandStart.remove(it.getAuthor().getId());
                double singleCommandWeight = 1.0 / (double)stats.getCommandCount();
                avgCommandDelay = (int)(avgCommandDelay * (1.0 - singleCommandWeight) + (double)delay * singleCommandWeight);
            }
            return null;
        });

        if (HalloweenEvent.TIMER.isEventInTime()) {
            f.handleEvent(UserTypingEvent.class, event -> {
                if (HalloweenEvent.connections.containsKey(event.getTextChannel()))
                    HalloweenEvent.typingTunneling(event);
            });
            f.handleEvent(MessageReceivedEvent.class, event -> {
                if (HalloweenEvent.connections.containsKey(event.getTextChannel()))
                    HalloweenEvent.messageTunneling(event);
                if (Match.PLAYING.containsKey(event.getAuthor())) {
                    Match game = Match.PLAYING.get(event.getAuthor());

                    if (game.getGameState() == GameState.MATCH) game.messageEvent(event);
                } else if (!event.getAuthor().isBot() && event.getGuild() != null && Match.GAMES.containsKey(event.getTextChannel())) {
                    Match game = Match.GAMES.get(event.getTextChannel());
                    if (game.getGame().getGameType() != GameType.COLLECTIVE) return;
                    game.messageEvent(event);
                }
            });
        }

        f.handleEvent(TextChannelDeleteEvent.class, event -> {
            if (Match.GAMES.containsKey(event.getChannel())) Match.GAMES.get(event.getChannel())
                    .onEnd("Channel Deleted", true);
        });

        // Reactions
        f.handleEvent(GuildMessageReactionAddEvent.class, event -> {
            if (event.getUser().equals(event.getJDA().getSelfUser())) return;
            if (Match.GAMES.containsKey(event.getChannel())) event.getReaction().getUsers().queue(users -> {
                if (!users.contains(event.getJDA().getSelfUser())) return;
                Match.GAMES.get(event.getChannel()).reactionEvent(event, users);
            });
        });

        f.reactionHandler("\uD83D\uDEAA", context -> {
            if (Match.GAMES.containsKey(context.getChannel())) Match.GAMES.get(context.getChannel()).joinReaction(context);
        });
        f.reactionHandler("\uD83E\uDD16", context -> {
            if (Match.GAMES.containsKey(context.getChannel())) Match.GAMES.get(context.getChannel()).aiReaction(context);
        });
        f.reactionHandler("\uD83D\uDD04", context -> {
            if (Match.REMATCH_GAMES.containsKey(context.getChannel())) Match.REMATCH_GAMES.get(context.getChannel()).rematchReaction(context);
        });
        f.reactionHandler("\uD83C\uDF6C", context -> {
            if (HalloweenEvent.connections.containsKey(context.getChannel())) HalloweenEvent.reaction(context, true);
        });
        f.reactionHandler("⛔", context -> {
            if (HalloweenEvent.connections.containsKey(context.getChannel())) HalloweenEvent.reaction(context, false);
        });

        f.getSettings().setMentionedMessageGetter(guild -> {
            if (guild == null) return null;
            String lang = GuildProfile.get(guild).getLanguage();
            return languageHelpMessages.get(lang == null ? Constants.DEFAULT_LANGUAGE : lang)
                    .replaceAll("%PREFIX%", Utility.getPrefixHelp(guild));
        });
    }

    private static Command.Executor partnerCommand(String url) {
        return context -> {
            context.send(it -> {
                it.append(Language.transl(context, "command." + context.getCurrentCommand().getName() + ".message"));
                it.setEmbed(new EmbedBuilder().setTitle(Language.transl(context, "genericMessages.partnerCheckItOut"), url).setColor(Utility.getEmbedColor(context.getGuild())).build());
            });
            return null;
        };
    }

    public static Optional<Blacklist> getBlacklist(String userId) {
        Blacklist set = Cache.get("bl_" + userId, n -> {
            try {
                ResultSet output = GamesROB.database.get().select("blacklist", Arrays.asList("userid", "botownerid", "reason", "time"), "userid = '" + userId + "'");
                if (output.next()) {
                    return new Blacklist(GamesROB.getUserById(output.getString("botownerid")), output.getString("reason"), output.getLong("time"));
                }
                return null;
            }
            catch (Exception e) {
                Log.exception("Getting blacklisted", e);
                return null;
            }
        });
        return Optional.ofNullable(set);
    }

    private static Command.Executor messageCommand() {
        return context -> Language.transl(context, "command." + context.getCurrentCommand().getName() + ".message");
    }

    private static String categoryMessage(String language, Guild guild, String category) {
        String prefix = Utility.getPrefixHelp(guild);
        return Language.transl(language, new StringBuilder().append("command.help.categories.").append(category).toString()) + "\n" + perCategory.get(category).stream().map(it -> String.format("%s `%s%s` - %s", it.getName(), prefix, it.getUsage(), Language.transl(language, "command." + it.getName() + ".description"))).collect(Collectors.joining("\n")).replaceAll("%PREFIX%", prefix);
    }

    private static void genHelpMessage(String language) {
        StringBuilder help = new StringBuilder(Language.transl(language, "command.help.beginning", GamesROB.VERSION));
        for (int i = 0; i < CATEGORIES.length; ++i) {
            boolean preferred;
            String category;
            help.append(EMOTES[i]).append((preferred = PREFERRED_CATEGORIES.contains(category = CATEGORIES[i])) ? " **" : "").append(Language.transl(language, "command.help.categories." + category)).append(preferred ? "**" : "");
            if (preferred) {
                help.append("\n");
                perCategory.get(category).forEach(cur -> {
                    String gameCode = cur.attr("gameCode");
                    help.append(gameCode == null ? String.format("%s `%%PREFIX%%%s` - %s", cur.getName(), cur.getUsage(), Language.transl(language, "command." + cur.getName() + ".description")) : String.format("%s `%%PREFIX%%%s` - %s", Language.transl(language, "game." + gameCode + ".name"), cur.getAliases().get(0), Language.transl(language, "game." + gameCode + ".shortDescription"))).append("\n");
                });
            } else {
                help.append(" (");
                List<Command> catCommands = perCategory.get(category);
                if (catCommands.size() <= 5) {
                    help.append(catCommands.stream().map(it -> String.format("`%s`", it.getName())).collect(Collectors.joining(", ")));
                } else {
                    List strings = catCommands.stream().limit(4L).map(it -> String.format("`%s`", it.getName())).collect(Collectors.toList());
                    strings.add(Language.transl(language, "command.help.other", catCommands.size() - 2));
                    help.append(String.join(", ", strings));
                }
                help.append(")");
            }
            help.append("\n");
        }
        help.append(Language.transl(language, "command.help.subCategory2"));
        languageHelpMessages.put(language, help.toString());
    }

    public static String help(CommandContext context) {
        EmbedBuilder embed = new EmbedBuilder().setColor(Utility.getEmbedColor(context.getGuild())).setTitle(Language.transl(context, "command.help.websiteTitle"), "https://gamesrob.com");
        if (System.currentTimeMillis() - Statistics.get().getLastUpdateLogSentTime() <= TimeUnit.DAYS.toMillis(1L)) {
            embed.appendDescription(Language.transl(Utility.getLanguage(context), "command.help.recentUpdate", GamesROB.VERSION, Utility.getPrefix(context.getGuild()))).appendDescription("\n");
        }
        OwnerCommands.getAnnouncement().ifPresent(announcement -> {
            User announcer = announcement.getAnnouncer();
            embed.appendDescription(":loudspeaker: " + announcement.getMessage());
            embed.setFooter(announcer.getName() + "#" + announcer.getDiscriminator(), null);
            Calendar time = Calendar.getInstance();
            time.setTimeInMillis(announcement.getAnnounced());
            embed.setTimestamp(time.toInstant());
        });
        Consumer<MessageBuilder> messageBuilder = builder -> builder.append(languageHelpMessages.get(Utility.getLanguage(context)).replaceAll("%PREFIX%", Utility.getPrefixHelp(context.getGuild()))).setEmbed(embed.build());
        if (context.getEvent() instanceof GuildMessageReactionAddEvent) {
            context.edit(messageBuilder).then(it -> it.getReactions().stream().filter(react -> react.getReactionEmote().getName().equals("\u2b05")).findFirst().ifPresent(react -> react.removeReaction().queue()));
        } else {
            context.send(messageBuilder).then(it -> {
                for (int i = 0; i < EMOTES.length; ++i) {
                    if (PREFERRED_CATEGORIES.contains(CATEGORIES[i])) continue;
                    it.addReaction(EMOTES[i]).queue();
                }
            });
        }
        return null;
    }

    public static Command.Executor imageCommand(ImageCommand command) {
        return context -> {
            Utility.Promise<RenderContext> promise = new Utility.Promise<>();
            Pair<Integer, Integer> dimensions = command.render(context, promise);

            BufferedImage image = new BufferedImage(dimensions.getKey(), dimensions.getValue(), 1);
            Graphics2D g2d = image.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

            Font starlight = Utility.getStarlightFont();
            MessageBuilder builder = new MessageBuilder();

            long begin = System.nanoTime();
            promise.done(new RenderContext(g2d, starlight, builder, dimensions.getKey(), dimensions.getValue()));
            long time = System.nanoTime() - begin;

            ArrayList<ByteArrayOutputStream> toClose = new ArrayList<>();
            try {
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                toClose.add(os);
                ImageOutputStream ios = ImageIO.createImageOutputStream(os);
                ImageIO.write(image, "png", ios);

                Consumer<Message> logAction = Statistics.get().registerImageProcessor(time, os.size());

                if (builder.isEmpty()) context.getChannel().sendFile(os.toByteArray(), "cooleastereggamirite.png").queue(logAction);
                else context.getChannel().sendFile(os.toByteArray(), "coolimagecommandright.png", builder.build()).queue(logAction);
                toClose.forEach(Utility::quietlyClose);
            }
            catch (Exception e) {
                toClose.forEach(Utility::quietlyClose);
                throw new RuntimeException(e);
            }
            return null;
        };
    }

    @Data
    @AllArgsConstructor
    public static class RenderContext {
        private Graphics2D graphics;
        private Font starlight;
        private MessageBuilder message;
        private int width;
        private int height;
    }

    @FunctionalInterface
    private static interface ImageCommand {
        Pair<Integer, Integer> render(CommandContext context, Utility.Promise<RenderContext> rcontext);
    }

    @Data
    @AllArgsConstructor
    public static class Blacklist {
        private Optional<User> botOwner;
        private String reason;
        private long time;
    }

    @Data
    @AllArgsConstructor
    public static class GameSettingValue {
        private Field field;
        private Setting annotation;
    }
}
