package me.deprilula28.gamesrob.commands;

import javafx.util.Pair;
import lombok.AllArgsConstructor;
import lombok.Data;
import me.deprilula28.gamesrob.GamesROB;
import me.deprilula28.gamesrob.Language;
import me.deprilula28.gamesrob.baseFramework.*;
import me.deprilula28.gamesrob.data.GuildProfile;
import me.deprilula28.gamesrob.data.Statistics;
import me.deprilula28.gamesrob.data.UserProfile;
import me.deprilula28.gamesrob.utility.Cache;
import me.deprilula28.gamesrob.utility.Constants;
import me.deprilula28.gamesrob.utility.Log;
import me.deprilula28.gamesrob.utility.Utility;
import me.deprilula28.jdacmdframework.Command;
import me.deprilula28.jdacmdframework.CommandContext;
import me.deprilula28.jdacmdframework.CommandFramework;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.core.events.user.UserTypingEvent;
import net.dv8tion.jda.core.exceptions.PermissionException;

import javax.imageio.ImageIO;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CommandManager {
    public static Map<Class<? extends MatchHandler>, List<GameSettingValue>> matchHandlerSettings = new HashMap<>();
    private static Map<String, String> languageHelpMessages = new HashMap<>();
    public static Map<String, Long> commandStart = new ConcurrentHashMap<>();
    public static double avgCommandDelay = 0L;

    @Data
    @AllArgsConstructor
    public static class GameSettingValue {
        private Field field;
        private Setting annotation;
    }

    private static final String[] CATEGORIES = {
            "eventcommands", "games", "tokencommands", "profilecommands", "servercommands", "matchcommands", "infocommands", "partnercommands"
    };
    private static final String[] EMOTES = {
            "\uD83C\uDF83", "\uD83C\uDFB2", "\uD83D\uDD38", "\uD83D\uDC64", "\uD83D\uDCDF", "\uD83C\uDFAE", "\uD83D\uDCCB", "\uD83E\uDD1D"
    };
    private static final List<String> PREFERRED_CATEGORIES = Arrays.asList(
            "eventcommands", "games"
    );
    private static final List<String> EMOTE_LIST = Arrays.asList(EMOTES);
    private static final Map<String, List<Command>> perCategory = new HashMap<>();

    private static String finMessage = "";
    public static void registerCommands(CommandFramework f) {
        // Halloween Event
        f.command("trickortreating trickortreat trickotreating tot", HalloweenEvent::trickOrTreatingMinigame)
                .attr("category", "eventcommands").attr("permissions", "MESSAGE_ADD_REACTION");

        f.command("candy halloweentokens candycurrency candybal", HalloweenEvent::candy).attr("category", "eventcommands");

        // Games
        Arrays.stream(GamesROB.ALL_GAMES).forEach(cur -> {
            Command command = f.command(cur.getAliases(), Match.createCommand(cur)).attr("category", "games")
                    .attr("gameCode", cur.getLanguageCode());
            if (cur.getGameType() == GameType.MULTIPLAYER || cur.getGameType() == GameType.HYBRID)
                command.setUsage(command.getName().toLowerCase() + " [Players] [Betting] [Settings]");

            List<GameSettingValue> settings = new ArrayList<>();
            Arrays.stream(cur.getMatchHandlerClass().getDeclaredFields()).filter(it -> it.isAnnotationPresent(Setting.class))
                .forEach(field -> settings.add(new GameSettingValue(field, field.getAnnotation(Setting.class))));
            matchHandlerSettings.put(cur.getMatchHandlerClass(), settings);
        });

        // Partners
        f.command("idlerpg idle rpg idlerpgbot",
                partnerCommand("https://discordbots.org/bot/idlerpg")).attr("category", "partnercommands");
        f.command("discordserversme dsm discordservers serverlist",
                partnerCommand("http://discordservers.me")).attr("category", "partnercommands");

        // Tokens
        f.command("slots c slot lotto lottery gamble gmb gmbl", Slots::slotsGame)
                .attr("category", "tokencommands").setUsage("slots <amount/all>");

        f.command("achievements a achieve achieved achieves ach " +
                "viewachievements viewachieve viewachieved viewachieves viewach accomplishments accomplished viewaccomplishments " +
                "viewaccomplished tasks task viewtasks viewtask missions mission viewmissions viewmission",
                Tokens::achievements).attr("category", "tokencommands")
                .attr("permissions", "MESSAGE_EMBED_LINKS");

        f.command("baltop b balancetop topbalance rich tokensleaderboard tokenslb tklb", Tokens::baltop)
                .attr("category", "tokencommands").setUsage("baltop [global] [page]");

        // Profile Commands
        f.command("profile p prof getprofile getprof viewprofile viewprof user usr getuser getusr viewuser viewusr \" +\n" +
                        "player getplayer viewplayers rank tokens token pfptokens t tk tks tok toks viewtokens viewtk viewtks " +
                        "viewtok viewtoks viewt gettokens gettk gettks gettok gettoks gett tokenamount tkamount tokamount " +
                        "tamount bal balance viewbalance money cash $ dollars dollar bucks ﷼ ₾ ₽ ₼ ₺ ₹ ₸ ₵ ₴ ₲ ₱ ₮ ₭ € ₫ ₩ " +
                        "₨ ₧ ₦ ₡ ฿ ֏ դր. лв дин ден ƒ ¥ 元 £ $ USD BRL PHP сўм ரூ  රු  robux RUB R$ Kč C$ B/. ރ imjuanrichboiiiilmaoxd ",
                imageCommand(ProfileCommands::profile), cmd -> {
                    cmd.sub("a add p addition additions additive plus insertplussignhere + U+002B hax",
                            OwnerCommands.tokenCommand((profile, tokens) -> profile.addTokens(tokens, "transactions.cheater"),
                                    "command.tokens.add"));
                    cmd.sub("r take k d takeaway minus delete remove - rem eternal_sadness_dot_jpeg remisabadbot cheat",
                            OwnerCommands.tokenCommand((profile, tokens) -> profile.addTokens(-tokens, "transactions.cheater"),
                                    "command.tokens.remove"));
                    cmd.sub("s set e hak", OwnerCommands.tokenCommand((profile, tokens) -> {
                        profile.setTokens(tokens);
                        profile.setEdited(true);
                    }, "command.tokens.set"));

                    cmd.sub("t transactions tr taxes do_your_taxes transactts payments bought history salary view", Tokens::transactions);
                    // Giving tokens
                    cmd.sub("g give pay repay giveto send sendnudes", context -> {
                        User target = context.nextUser();
                        if (getBlacklist(target.getId()).isPresent()) return Language.transl(context, "command.tokens.giveInvalidUser");
                        int amount = context.nextInt();
                        if (amount < 10 || amount > 5000) return Language.transl(context,"command.tokens.giveInvalidAmount", 10, 5000);

                        if (!UserProfile.get(context.getAuthor()).transaction(amount, "transactions.give"))
                            return Constants.getNotEnoughTokensMessage(context, amount);
                        UserProfile.get(target).addTokens(amount, "transactions.got");

                        return Language.transl(context, "command.tokens.give", context.getAuthor().getAsMention(), target.getAsMention(), amount);
                    }).setUsage("g*token give <user> <amount>");
                }).attr("category", "profilecommands").attr("permissions", "MESSAGE_ATTACH_FILES,MESSAGE_EMBED_LINKS")
                .setUsage("profile [user]");

        f.command("userlang u lang language userlanguage mylang mylanguage", LanguageCommands::setUserLanguage).attr("category", "profilecommands");

        f.command("emote emojitile e emoji changeemoji setemojitile setemoji emojis emoticons emoticon changeemoticon emoticontile " +
                "setemoticon setemoticontile changeemote emotetile setemote setemotetile emotes tile changetile settile " +
                "depwhyaretheretwolinesforonecommandimscaredidkwhattodosothisisherehelphelphelphelp", ProfileCommands::emojiTile)
                .attr("category", "profilecommands").setUsage("emojitile <Emoji>");

        // Server
        f.command("leaderboard y getleaderboard viewleaderboard checkleaderboard leaderboards getleaderboards " +
                        "viewleaderboards checkleaderboards board getboard viewboard checkboard boards getboards checkboards " +
                        "viewboards leader getleader checkleader viewleader leaders getleaders checkleaders viewleaders lb " +
                        "getlb checklb viewlb lbs getlbs checklbs viewlbs top gettop checktop viewtop",
                imageCommand(LeaderboardCommand::leaderboard)).attr("category", "servercommands")
                .attr("permissions", "MESSAGE_ATTACH_FILES");

        f.command("guildlang g changeguildlang setguildlang guildlanguage changeguildlanguage setguildlanguage " +
                        "glang changeglang setglang glanguage changeglanguage setglanguage serverlang changeserverlang " +
                        "setserverlang serverlanguage changeserverlanguage setserverlanguage slang changeslang setslang slanguage changeslanguage setslanguage",
                permissionLock(LanguageCommands::setGuildLanguage, ctx -> ctx.getAuthorMember()
                        .hasPermission(Permission.MANAGE_SERVER))).attr("category", "servercommands");

        f.command("perm % pr setperm changeperm perms setperms changeperms permission setpermission changepermission permissions " +
                "setpermissions changepermissions", permissionLock(PermissionCommands::changePerm,
                ctx -> ctx.getAuthorMember().hasPermission(Permission.MANAGE_SERVER))).attr("category", "servercommands")
                .setUsage("perm <command> [permission]");

        f.command("setprefix * prefix changeprefix", permissionLock(GenericCommands::setPrefix,
                ctx -> ctx.getAuthorMember().hasPermission(Permission.MANAGE_SERVER)))
                .attr("category", "servercommands").setUsage("setprefix <Prefix>");

        f.command("upvote upvotes vote votes upvoteinfo voteinfo up ups daily", GenericCommands::upvote, cmd -> {
            cmd.sub("set setdays", OwnerCommands.tokenCommand((profile, days) -> {
                profile.setUpvotedDays(days);
                profile.setLastUpvote(System.currentTimeMillis());
            }, "command.upvote.setdays"));

            cmd.sub("history his lastvotes", GenericCommands::upvoteHistory).setUsage("upvote history <page>");
        }).attr("category", "servercommands");

        // Match
        f.command("leave l lv leavegame lg leavematch lm quit", MatchCommands::leave).attr("cateogry", "matchcommands");

        f.command("join j jn joingame jg joinmatch jm", MatchCommands::join).attr("category", "matchcommands");

        f.command("stop s stopgame stopmatch stopplaying staph stahp stap nodie",
                permissionLock(MatchCommands::stop, ctx -> GuildProfile.get(ctx.getGuild()).canStop(ctx) ||
                        (Match.GAMES.containsKey(ctx.getChannel()) && Match.GAMES.get(ctx.getChannel())
                        .getCreator().equals(ctx.getAuthor()))))
                .attr("category", "matchcommands");

        f.command("listplayers & players viewplayers getplayers checkplayers playerlist viewplayerlist getplayerlist " +
                "checkplayerlist", MatchCommands::listPlayers).attr("category", "matchcommands");

        // Information
        f.command("invite i invitebot invitegrob invitegamesrob add addbot addgrob addgamesrob get getbot getgrob " +
                "getgamesrob getgood getgud", GenericCommands::invite).attr("category", "infocommands")
                .attr("permissions", "MESSAGE_EMBED_LINKS");

        f.command("info ? information botinfo botinformation helpbutwithdetails", GenericCommands::info,
                it -> it.sub("reload", GenericCommands::info)).reactSub("\uD83D\uDD01", "reload")
                .attr("category", "infocommands").attr("permissions", "MESSAGE_EMBED_LINKS");

        f.command("changelog getchangelog viewchangelog log getlog viewlog clog getclog viewclog changes getchanges " +
                "viewchanges version getversion viewversion ver getver viewver additions getaddions viewadditions whatsnew g" +
                "etwhatsnew viewwhatsnew", GenericCommands::changelog).attr("category", "infocommands")
                .attr("permissions", "MESSAGE_EMBED_LINKS");

        f.command("ping getping viewping seeping checkping pong getpong viewpong seepong checkpong connection getconnection " +
                "viewconnection seeconnection checkconnection latency getlatency viewlatency seelatency checklatency latenci " +
                "getlatenci viewlatenci seelatenci checklatenci pingpong getpingpong viewpingpong seepingpong checkpingpong " +
                "pongping getpongping viewpongping seepongping checkpongping ms2 getms2 viewms2 seems2 checkms2", GenericCommands::ping)
                .attr("category", "infocommands");

        f.command("shardinfo sharddetails getsharddetails viewsharddetails seesharddetails checksharddetails shard " +
                "getshardinfo viewshardinfo seeshardinfo checkshardinfo shardinformation getshardinformation viewshardinformation " +
                "seeshardinformation checkshardinformation shardstuff getshardstuff viewshardstuff seeshardstuff checkshardstuff " +
                "shards getshards viewshards seeshards checkshards", GenericCommands::shardsInfo)
                .attr("category", "infocommands");

        f.command("support bug report glitch bugs error glitches errors server", messageCommand())
                .attr("category", "infocommands").attr("permissions", "MESSAGE_EMBED_LINKS");

        f.command("website web site online internet interweb gamesrobcom gamesrobdotcom gamesrob.com .com dotcom com", messageCommand())
                .attr("category", "infocommands").attr("permissions", "MESSAGE_EMBED_LINKS");

        f.command("help h halp games what wat uwot uwotm8 uwotm9  wtf tf ... ivefallenandicantgetup whatisgoingon " +
                "imscared commands cmds imgoingtoexplode please~~sendnudes~~*help*me ineedassistance", CommandManager::help, cmd -> {
            cmd.sub("back bbbb bbb bb b aaaa aaa aa a cccc ccc cc c kkkk kkk kk k bac bccc bcck " +
			"bckk bac bak bacc bacccccccccccccc bacwith20cs", CommandManager::help);

            for (String category : CATEGORIES) {
                if (!PREFERRED_CATEGORIES.contains(category)) cmd.sub(category, context -> {
                    try { context.getMessage().addReaction("⬅").queue(); } catch (PermissionException e) {}
                    return categoryMessage(Constants.getLanguage(context), context.getGuild(), category);
                });
            }

            f.getCommands().forEach(cur -> {
                cmd.sub(String.join(" ", cur.getAliases()), cur.attr("gameCode") != null ? context -> {
                    String prefix = Constants.getPrefixHelp(context.getGuild());
                    String language = Constants.getLanguage(context);
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
                    String prefix = Constants.getPrefixHelp(context.getGuild());
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
        Log.info("Generated help messages for ", languageHelpMessages.size() + " languages.");

        f.command("^ update upd8 updep dabbinguncontrollably newupdate thiswasaddedin1.7.4", OwnerCommands::updateCommand);
        f.command("/ eval evaluate ebal ebaluate ejaculate", OwnerCommands::eval);
        f.command("> bash con console commandconsole cmd commandprompt terminal term", OwnerCommands::console);
        f.command("# sql postgres postgresql sqlexecute runsql", OwnerCommands::sql);
        f.command("! announce announcement br broadcast", OwnerCommands::announce);
        f.command("< blacklist bl l8r adios cya pce peace later bye rekt dab", OwnerCommands::blacklist);
        f.command("| cache", OwnerCommands::cache);
        f.command(". servercount srvcount svc", OwnerCommands::servercount);
        f.command("¨ compilelanguage cl21", OwnerCommands::compileLanguage);
        f.command("+ badges badge bdg bg", OwnerCommands::badges);

        f.command("0 1 2 3 4 5 6 7 8 9 $ @ whymustyoumentioneveryone fin finmessage finmsg fintime meme memes " +
                "maymays maymay meemee dankmeme dank", context -> finMessage, command -> {
            command.sub("set", context -> {
                if (!GamesROB.owners.contains(context.getAuthor().getIdLong())) return Language.transl(context,
                        "genericMessages.ownersOnly");
                finMessage = String.join(" ", context.remaining());
                return "dank maymay set.";
            });
        });
        OwnerCommands.owners(f);

        f.before(it -> {
            if (commandStart.containsKey(it.getAuthor().getId())) return Language.transl(it, "genericMessages.tooFast");

            Optional<Blacklist> optBl = getBlacklist(it.getAuthor().getId());
            if (optBl.isPresent()) {
                Blacklist blacklist = optBl.get();
                return Language.transl(it, "genericMessages.blacklisted",
                        blacklist.getBotOwner().map(User::getName).orElse("*No longer an owner*"),
                        Utility.formatTime(blacklist.getTime()), blacklist.getReason());
            }

            String permissions = it.getCurrentCommand().attr("permissions");
            if (permissions != null && !it.getGuild().getMember(it.getJda().getSelfUser())
                    .hasPermission(Permission.ADMINISTRATOR)) {
                List<String> missing = Arrays.stream(permissions.split(",")).map(Permission::valueOf)
                        .filter(perm -> !Utility.hasPermission(it.getChannel(), it.getGuild().getMember(it.getJda().getSelfUser()), perm))
                        .map(Objects::toString).collect(Collectors.toList());

                if (!missing.isEmpty()) return Language.transl(it, "genericMessages.requirespermissions",
                        String.join(", ", missing));
            }

            commandStart.put(it.getAuthor().getId(), System.nanoTime());
            return null;
        });
        f.after(it -> {
            Statistics stats = Statistics.get();
            stats.setCommandCount(stats.getCommandCount() + 1);

            if (commandStart.containsKey(it.getAuthor().getId())) {
                long delay = System.nanoTime() - commandStart.get(it.getAuthor().getId());
                commandStart.remove(it.getAuthor().getId());
                double singleCommandWeight = (1.0 / (double) stats.getCommandCount());
                avgCommandDelay = (int) (avgCommandDelay * (1.0 - singleCommandWeight) + delay * singleCommandWeight);
            }

            return null;
        });

        f.handleEvent(UserTypingEvent.class, event -> {
            if (HalloweenEvent.connections.containsKey(event.getTextChannel())) HalloweenEvent.typingTunneling(event);
        });
        f.handleEvent(MessageReceivedEvent.class, event -> {
            if (HalloweenEvent.connections.containsKey(event.getTextChannel())) HalloweenEvent.messageTunneling(event);
            if (Match.PLAYING.containsKey(event.getAuthor())) {
                Match game = Match.PLAYING.get(event.getAuthor());

                if (game.getGameState() == GameState.MATCH) game.messageEvent(event);
            } else if (!event.getAuthor().isBot() && event.getGuild() != null && Match.GAMES.containsKey(event.getTextChannel())) {
                Match game = Match.GAMES.get(event.getTextChannel());
                if (game.getGame().getGameType() != GameType.COLLECTIVE || !game.playMore) return;
                game.messageEvent(event);
            }
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
            if (guild == null) return "Please use the bot in a guild.";
            String lang = GuildProfile.get(guild).getLanguage();
            return languageHelpMessages.get(lang == null ? Constants.DEFAULT_LANGUAGE : lang)
                    .replaceAll("%PREFIX%", Constants.getPrefixHelp(guild));
        });
    }

    private static Command.Executor partnerCommand(String url) {
        return context -> {
            context.send(it -> {
                it.append(Language.transl(context, "command." + context.getCurrentCommand().getName() + ".message"));
                it.setEmbed(new EmbedBuilder().setTitle(Language.transl(context, "genericMessages.partnerCheckItOut"),
                        url).setColor(Utility.getEmbedColor(context.getGuild())).build());
            });
            return null;
        };
    }

    @Data
    @AllArgsConstructor
    public static class Blacklist {
        private Optional<User> botOwner;
        private String reason;
        private long time;
    }

    public static Optional<Blacklist> getBlacklist(String userId) {
        Blacklist set = Cache.get("bl_" + userId, n -> {
            try {
                ResultSet output = GamesROB.database.get().select("blacklist", Arrays.asList("userid", "botownerid", "reason", "time"),
                        "userid = '" + userId + "'");
                if (output.next()) return new Blacklist(
                        GamesROB.getUserById(output.getString("botownerid")),
                        output.getString("reason"), output.getLong("time"));
                else return null;
            } catch (Exception e) {
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
        String prefix = Constants.getPrefixHelp(guild);
        return Language.transl(language, "command.help.categories." + category) + "\n"
                + perCategory.get(category).stream().map(it -> String.format("%s `%s%s` - %s",
                it.getName(), prefix, it.getUsage(),
                Language.transl(language, "command." + it.getName() + ".description")
        )).collect(Collectors.joining("\n")).replaceAll("%PREFIX%", prefix);
    }

    private static void genHelpMessage(String language) {
        StringBuilder help = new StringBuilder(Language.transl(language, "command.help.beginning", GamesROB.VERSION));

        for (int i = 0; i < CATEGORIES.length; i++) {
            String category = CATEGORIES[i];
            boolean preferred = PREFERRED_CATEGORIES.contains(category);
            help.append(EMOTES[i]).append(preferred ? " **" : "").append(Language.transl(language, "command.help.categories." + category))
                .append(preferred ? "**" : "");
            if (preferred) {
                help.append("\n");
                perCategory.get(category).forEach(cur -> {
                    String gameCode = cur.attr("gameCode");
                    help.append(gameCode == null
                        ? String.format("%s `%%PREFIX%%%s` - %s",
                            cur.getName(), cur.getUsage(),
                            Language.transl(language, "command." + cur.getName() + ".description"))
                        : String.format("%s `%%PREFIX%%%s` - %s",
                            Language.transl(language, "game." + gameCode + ".name"),
                            cur.getAliases().get(0),
                            Language.transl(language, "game." + gameCode + ".shortDescription")
                        ))
                    .append("\n");
                });
            } else {
                help.append(" (");
                List<Command> catCommands = perCategory.get(category);
                if (catCommands.size() <= 5) help.append(catCommands.stream().map(it -> String.format("`%s`",
                        it.getName())).collect(Collectors.joining(", ")));
                else {
                    List<String> strings = catCommands.stream().limit(4).map(it -> String.format("`%s`", it.getName()))
                        .collect(Collectors.toList());
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
        EmbedBuilder embed = new EmbedBuilder().setColor(Utility.getEmbedColor(context.getGuild()))
                .setTitle(Language.transl(context, "command.help.websiteTitle"), Constants.GAMESROB_DOMAIN);

        if (System.currentTimeMillis() - Statistics.get().getLastUpdateLogSentTime() <= TimeUnit.DAYS.toMillis(1))
            embed.appendDescription(Language.transl(Constants.getLanguage(context), "command.help.recentUpdate",
                    GamesROB.VERSION, Constants.getPrefix(context.getGuild()))).appendDescription("\n");
        OwnerCommands.getAnnouncement().ifPresent(announcement -> {
            User announcer = announcement.getAnnouncer();

            embed.appendDescription(":loudspeaker: " + announcement.getMessage());
            embed.setFooter(announcer.getName() + "#" + announcer.getDiscriminator(), null);

            Calendar time = Calendar.getInstance();
            time.setTimeInMillis(announcement.getAnnounced());
            embed.setTimestamp(time.toInstant());
        });

        Consumer<MessageBuilder> messageBuilder = builder -> builder.append(languageHelpMessages.get(Constants.getLanguage(context))
                .replaceAll("%PREFIX%", Constants.getPrefixHelp(context.getGuild()))).setEmbed(
                embed.build());

        if (context.getEvent() instanceof GuildMessageReactionAddEvent) context.edit(messageBuilder).then(it ->
                it.getReactions().stream().filter(react -> react.getReactionEmote().getName().equals("⬅")).findFirst()
                .ifPresent(react -> react.removeReaction().queue()));
        else context.send(messageBuilder).then(it -> {
            for (int i = 0; i < EMOTES.length; i++) if (!PREFERRED_CATEGORIES.contains(CATEGORIES[i])) it.addReaction(EMOTES[i]).queue();
        });
        return null;
    }

    public static Command.Executor permissionLock(Command.Executor command, Function<CommandContext, Boolean> func) {
        return context -> {
            if (!func.apply(context) && !GamesROB.owners.contains(context.getAuthor().getIdLong()))
                return Language.transl(context, "command.permissionLock");
            return command.execute(context);
        };
    }

    private static final int TIME_TAKEN_HEIGHT = 20;

    @FunctionalInterface
    private static interface ImageCommand {
        Pair<Integer, Integer> render(CommandContext context, Utility.Promise<RenderContext> promise);
    }

    @AllArgsConstructor
    @Data
    public static class RenderContext {
        private Graphics2D graphics;
        private Font starlight;
        private MessageBuilder message;
        private int width;
        private int height;
    }

    public static Command.Executor imageCommand(ImageCommand command) {
        return context -> {
            Utility.Promise<RenderContext> promise = new Utility.Promise<>();
            Pair<Integer, Integer> dimensions = command.render(context, promise);

            BufferedImage image = new BufferedImage(dimensions.getKey(), dimensions.getValue(),
                    BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = image.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); // AA
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC); // Bicubic Image Interpolation

            Font starlight = Constants.getStarlightFont();
            MessageBuilder builder = new MessageBuilder();
            long begin = System.currentTimeMillis();
            promise.done(new RenderContext(g2d, starlight, builder, dimensions.getKey(), dimensions.getValue()));

            List<Closeable> toClose = new ArrayList<>();
            try {
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                toClose.add(os);
                ImageOutputStream ios = ImageIO.createImageOutputStream(os);
                ImageIO.write(image, "png", ios);

                if (builder.isEmpty()) context.getChannel().sendFile(os.toByteArray(), "cooleastereggamirite.png").queue();
                else context.getChannel().sendFile(os.toByteArray(), "coolimagecommandright.png", builder.build()).queue();
                toClose.forEach(Utility::quietlyClose);
            } catch (Exception e) {
                toClose.forEach(Utility::quietlyClose);
                throw new RuntimeException(e);
            }
            return null;
        };
    }
}
