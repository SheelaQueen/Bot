package me.deprilula28.gamesrob.baseFramework;

import lombok.Data;
import me.deprilula28.gamesrob.GamesROB;
import me.deprilula28.gamesrob.Language;
import me.deprilula28.gamesrob.achievements.AchievementType;
import me.deprilula28.gamesrob.commands.CommandManager;
import me.deprilula28.gamesrob.data.GuildProfile;
import me.deprilula28.gamesrob.data.Statistics;
import me.deprilula28.gamesrob.data.UserProfile;
import me.deprilula28.gamesrob.utility.Cache;
import me.deprilula28.gamesrob.utility.Constants;
import me.deprilula28.gamesrob.utility.Log;
import me.deprilula28.gamesrob.utility.Utility;
import me.deprilula28.jdacmdframework.Command;
import me.deprilula28.jdacmdframework.CommandContext;
import me.deprilula28.jdacmdframework.RequestPromise;
import me.deprilula28.jdacmdframework.exceptions.CommandArgsException;
import me.deprilula28.jdacmdframework.exceptions.InvalidCommandSyntaxException;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.Event;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.events.message.guild.react.GuildMessageReactionAddEvent;

import java.awt.event.HierarchyBoundsAdapter;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/*
THIS CLASS IS A MESS HELP
 */

@Data
public class Match extends Thread {
    public static final Map<TextChannel, Match> GAMES = new HashMap<>();
    public static final Map<TextChannel, Match> REMATCH_GAMES = new HashMap<>();

    public static final Map<JDA, List<Match>> ACTIVE_GAMES = new HashMap<>();
    public static final Map<User, Match> PLAYING = new HashMap<>();
    private static final long MATCH_TIMEOUT_PERIOD = TimeUnit.MINUTES.toMillis(5);
    private static final long JOIN_TIMEOUT_PERIOD = TimeUnit.SECONDS.toMillis(20);
    private static final long REMATCH_TIMEOUT_PERIOD = TimeUnit.MINUTES.toMillis(1);

    protected TextChannel channelIn;
    private Optional<GamesInstance.GameMode> mode = Optional.empty();
    private final Map<User, Map<AchievementType, Integer>> addAchievement = new HashMap<>();
    private GamesInstance game;
    private GameState gameState;
    private List<Player> players = new ArrayList<>();
    private User creator;
    private transient MatchHandler matchHandler;
    private transient RequestPromise<Message> matchMessage;

    private String language;
    private Map<String, String> options;
    public int matchesPlayed = 0;
    private boolean canReact;
    private Optional<Integer> betting = Optional.empty();
    private boolean multiplayer;
    public int iteration;
    private boolean allowRematch;
    private Map<String, Integer> settings = new HashMap<>();
    public boolean playMore = false;
    private boolean reacted = false;
    private boolean reactedJoin = false;
    private boolean reactedAi = false;

    // Rematch multiplayer
    public Match(GamesInstance game, User creator, TextChannel channel, List<Player> players,
                 Map<String, String> options, int matchesPlayed) {
        multiplayer = players.size() > 1;
        String guildLang = GuildProfile.get(channel.getGuild()).getLanguage();
        language = guildLang == null ? Constants.DEFAULT_LANGUAGE : guildLang;
        channelIn = channel;
        Member member = channelIn.getGuild().getMember(channelIn.getJDA().getSelfUser());
        canReact = Utility.hasPermission(channelIn, member, Permission.MESSAGE_ADD_REACTION) &&
                Utility.hasPermission(channelIn, member, Permission.MESSAGE_HISTORY);

        this.creator = creator;
        this.players = players;
        this.game = game;
        this.options = options;

        matchHandler = game.getMatchHandlerSupplier().get();
        updateSettings(options);

        gameState = GameState.MATCH;

        GAMES.put(channel, this);
        ACTIVE_GAMES.get(channel.getJDA()).add(this);
        this.matchesPlayed = matchesPlayed;

        setName("Game timeout thread for " + game.getName(language));
        setDaemon(true);
        start();

        matchHandler.begin(this, n -> {
            MessageBuilder builder = new MessageBuilder().append(Language.transl(language, "gameFramework.begin",
                    game.getName(language), game.getLongDescription(language)
            ));
            matchHandler.updatedMessage(false, builder);
            return RequestPromise.forAction(channelIn.sendMessage(builder.build())).then(no -> gameState = GameState.MATCH);
        });
        players.stream().filter(it -> it.getUser().isPresent()).forEach(it -> {
            User user = it.getUser().get();
            PLAYING.put(user, this);
            achievement(user, AchievementType.PLAY_GAMES, 1);
        });
    }

    // Collective
    public Match(GamesInstance game, User creator, TextChannel channel, Map<String, String> options) {
        multiplayer = true;
        String guildLang = GuildProfile.get(channel.getGuild()).getLanguage();
        language = guildLang == null ? Constants.DEFAULT_LANGUAGE : guildLang;
        channelIn = channel;
        Member member = channelIn.getGuild().getMember(channelIn.getJDA().getSelfUser());
        canReact = Utility.hasPermission(channelIn, member, Permission.MESSAGE_ADD_REACTION) &&
                Utility.hasPermission(channelIn, member, Permission.MESSAGE_HISTORY);
        players.add(Player.user(creator));

        this.betting = Optional.empty();
        this.creator = creator;
        this.game = game;
        this.options = options;
        matchHandler = game.getMatchHandlerSupplier().get();
        gameState = GameState.MATCH;
        updateSettings(options);

        GAMES.put(channel, this);
        PLAYING.put(creator, this);
        achievement(creator, AchievementType.PLAY_GAMES, 1);
        ACTIVE_GAMES.get(channel.getJDA()).add(this);

        Statistics.get().registerGame(game);

        matchHandler.begin(this, no -> {
            MessageBuilder builder = new MessageBuilder().append(Language.transl(language, "gameFramework.collectiveMatch2",
                    game.getName(language), game.getLongDescription(language)
            ));
            matchHandler.updatedMessage(false, builder);
            matchMessage = RequestPromise.forAction(channel.sendMessage(builder.build()));

            return matchMessage;
        });

        setName("Game timeout thread for " + game.getName(language));
        setDaemon(true);
        start();
    }

    // Hybrid/Multiplayer
    public Match(GamesInstance game, User creator, TextChannel channel, Map<String, String> options, Optional<Integer> bet) {
        multiplayer = true;
        String guildLang = GuildProfile.get(channel.getGuild()).getLanguage();
        language = guildLang == null ? Constants.DEFAULT_LANGUAGE : guildLang;
        channelIn = channel;
        Member member = channelIn.getGuild().getMember(channelIn.getJDA().getSelfUser());
        canReact = Utility.hasPermission(channelIn, member, Permission.MESSAGE_ADD_REACTION) &&
                Utility.hasPermission(channelIn, member, Permission.MESSAGE_HISTORY);
        players.add(Player.user(creator));

        this.mode = mode;
        this.betting = betting;
        this.creator = creator;
        this.game = game;
        this.options = options;
        matchHandler = game.getMatchHandlerSupplier().get();
        updateSettings(options);

        gameState = GameState.PRE_GAME;

        GAMES.put(channel, this);
        PLAYING.put(creator, this);
        ACTIVE_GAMES.get(channel.getJDA()).add(this);

        updatePreMessage();

        setName("Game timeout thread for " + game.getName(language));
        setDaemon(true);
        start();
    }

    // Game Timeout
    @Override
    public void run() {
        while (gameState != GameState.POST_MATCH)
            try {
                if (gameState == GameState.MATCH || players.size() < game.getMinTargetPlayers() + 1) {
                    Thread.sleep(MATCH_TIMEOUT_PERIOD);
                    onEnd(Language.transl(language, "gameFramework.timeout", Utility.formatPeriod(MATCH_TIMEOUT_PERIOD)),
                            true);
                } else {
                    Thread.sleep(JOIN_TIMEOUT_PERIOD);
                    gameStart();
                }
            } catch (InterruptedException e) {}
        try {
            Thread.sleep(REMATCH_TIMEOUT_PERIOD);
            if (this.equals(REMATCH_GAMES.get(channelIn))) REMATCH_GAMES.remove(channelIn);
            else if (REMATCH_GAMES.containsKey(channelIn)) REMATCH_GAMES.get(channelIn).matchesPlayed = matchesPlayed + 1;
            if (Utility.hasPermission(channelIn, channelIn.getGuild().getMember(channelIn.getJDA().getSelfUser()),
                    Permission.MESSAGE_MANAGE)) matchMessage.then(it -> it.clearReactions().queue());
        } catch (InterruptedException e) {}
    }

    private byte[] getImage(int iteration) {
        if (this.iteration != iteration) throw new RuntimeException("Invalid iteration.");
        if (!(matchHandler instanceof MatchHandler.ImageMatchHandler)) throw new RuntimeException("Not an image game.");
        return Cache.get(matchHandler.hashCode() + "_" + iteration, n -> ((MatchHandler.ImageMatchHandler) matchHandler).getImage());
    }

    private void updateSettings(Map<String, String> options) {
        CommandManager.matchHandlerSettings.get(game.getMatchHandlerClass()).forEach(cur -> {
            String name = cur.getField().getName();
            Optional<Integer> intOpt = options.containsKey(name) ? GameUtil.safeParseInt(options.get(name)) : Optional.empty();
            intOpt.ifPresent(num -> {
                if (num < cur.getAnnotation().min() || num > cur.getAnnotation().max())
                    throw new CommandArgsException(Language.transl(language, "gameFramework.settingOutOfRange",
                            name, cur.getAnnotation().min(), cur.getAnnotation().max()
                    ));
            });

            try {
                int setting = intOpt.orElse(cur.getAnnotation().defaultValue());
                cur.getField().set(matchHandler, setting);
                settings.put(name, setting);
            } catch (Exception e) {
                throw new RuntimeException("Failed to set value for setting", e);
            }
        });
    }

    private String getPregameText() {
        StringBuilder builder = new StringBuilder(Language.transl(language, "gameFramework.multiplayerMatch",
                game.getName(language), game.getShortDescription(language) + mode.map(it ->
                        Language.transl(language, "gameFramework.mode", it.getName(language), it.getDescription(language)))
                    .orElse("")
        ));

        players.forEach(cur -> builder.append("☑ **").append(cur.toString()).append("**").append("\n"));
        for (int i = players.size(); i <= game.getMinTargetPlayers(); i ++) builder.append(Language.transl(language, "gameFramework.waiting"));

        builder.append("\n");
        if (game.isAllowAi()) builder.append(Language.transl(language, "gameFramework.addBot")).append("\n");
        betting.ifPresent(amount -> builder.append(Language.transl(language, "gameFramework.betting", amount)).append("\n"));
        builder.append(Language.transl(language, canReact ? "gameFramework.joinToPlay2" : "gameFramework.joinToPlayNoReaction2",
                Constants.getPrefix(channelIn.getGuild())
        ));

        if (game.getGameType() == GameType.HYBRID || getPlayers().size() >= game.getMinTargetPlayers() + 1) {
            builder.append("\n");
            if (game.getModes().isEmpty()) builder.append(Language.transl(language,
                    "gameFramework.playButton", game.getName(language)));
            else {
                builder.append(Language.transl(language, "gameFramework.modePlayButtonHeader", game.getName(language)))
                        .append(Language.transl(language, "gameFramework.modePlayButton", Utility.getNumberEmote(0),
                            Language.transl(language, "gameFramework.defaultMode", game.getName(language))));
                for (int i = 0; i < game.getModes().size(); i ++) {
                    GamesInstance.GameMode mode = game.getModes().get(i);
                    builder.append(Language.transl(language, "gameFramework.modePlayButton",
                            Utility.getNumberEmote(i + 1), mode.getName(language) + ": " + mode.getDescription(language)));
                }
            }
        }

        return builder.toString();
    }

    private void updatePreMessage() {
        if (matchMessage == null) matchMessage = RequestPromise.forAction(channelIn.sendMessage(getPregameText()));
        else matchMessage.then(it -> it.editMessage(getPregameText()).queue());
        if (canReact && !reacted) {
            matchMessage.then(msg -> {
                msg.addReaction("\uD83D\uDEAA").queue();
            });
            reacted = true;
        }
        if (game.getGameType() == GameType.HYBRID || getPlayers().size() >= game.getMinTargetPlayers() + 1 && !reactedJoin) {
            matchMessage.then(msg -> {
                if (game.getModes().isEmpty()) msg.addReaction("▶").queue();
                else {
                    msg.addReaction(Utility.getNumberEmote(0)).queue();
                    for (int i = 0; i < game.getModes().size(); i ++) msg.addReaction(Utility.getNumberEmote(i + 1)).queue();
                }
            });
            reactedJoin = true;
        }
        if (game.isAllowAi() && !reactedAi) {
            matchMessage.then(msg -> msg.addReaction("\uD83E\uDD16").queue());
            reactedAi = true;
        }
    }

    public void achievement(User user, AchievementType type, int amount) {
        if (!addAchievement.containsKey(user)) addAchievement.put(user, new HashMap<>());
        if (!addAchievement.get(user).containsKey(type)) addAchievement.get(user).put(type, 0);
        addAchievement.get(user).put(type, amount + addAchievement.get(user).get(type));
    }

    public void reactionEvent(GuildMessageReactionAddEvent event, List<User> users) {
        try {
            if (gameState != GameState.PRE_GAME) event.getChannel().getMessageById(event.getMessageIdLong()).queue(message ->
                getMatchHandler().receivedReaction(event.getUser(), message, event.getReactionEmote()));
            else {
                String name = event.getReactionEmote().getName();
                if (name == null) return;
                if (name.equals("▶") && game.getModes().isEmpty()) startReaction(event.getUser());
                else if (name.charAt(1) == '⃣') {
                    int number = name.charAt(0) - 49;
                    if (game.getModes().size() < number || number < 0) return;

                    if (number == 0) mode = Optional.empty();
                    else mode = Optional.of(game.getModes().get(number - 1));
                    startReaction(event.getUser());
                }
            }
        } catch (Exception e) {
            Optional<String> trelloUrl = Log.exception("Game of " + getGame().getName(Constants.DEFAULT_LANGUAGE) + " had an error", e);
            onEnd("⛔ Oops! Something spoopy happened and I had to stop this game.\n" +
                    "You can send this: " + trelloUrl.orElse("*No trello info found*") + " to our support server at https://discord.gg/gJKQPkN !", false);
        }
    }

    public boolean isMode(String code) {
        return mode.map(it -> code.equals(it.getLanguageCode())).orElse(code == null);
    }

    public void messageEvent(MessageReceivedEvent event) {
        GamesROB.commandFramework.getSettings().getThreadPool().execute(() -> {
            try {
                if (event.getGuild() == null) getMatchHandler().receivedDM(event.getMessage().getContentRaw(),
                        event.getAuthor(), event.getMessage());
                else getMatchHandler().receivedMessage(event.getMessage().getContentRaw(),
                        event.getAuthor(), event.getMessage());
            } catch (Exception e) {
                Optional<String> trelloUrl = Log.exception("Game of " + getGame().getName(Constants.DEFAULT_LANGUAGE) + " had an error", e);
                onEnd("⛔ Oops! Something spoopy happened and I had to stop this game.\n" +
                        "You can send this: " + trelloUrl.orElse("*No trello info found*") + " to our support server at https://discord.gg/gJKQPkN !", false);
            }
        });
    }

    public void left(Player player, MessageBuilder builder) {
        interrupt();
        if (players.size() == 2) {
            players.stream().filter(it -> !it.equals(player)).findFirst().ifPresent(this::onEnd);
            return;
        }

        players.remove(player);
        player.getUser().ifPresent(user -> {
            PLAYING.remove(user);
            if (addAchievement.containsKey(user)) addAchievement.get(user).forEach((type, amount) ->
                    type.addAmount(true, amount, builder, user, channelIn.getGuild(), language));
            matchHandler.onQuit(user);
        });
    }

    public void joined(Player player) {
        interrupt(); // Keep the match alive
        players.add(player);
        player.getUser().ifPresent(user -> {
            PLAYING.put(user, this);
            achievement(user, AchievementType.PLAY_GAMES, 1);
        });
        if (gameState == GameState.PRE_GAME) {
            if (players.size() == game.getMaxTargetPlayers() + 1) gameStart();
            else updatePreMessage();
        } else if (game.getGameType() != GameType.COLLECTIVE) channelIn.sendMessage(Language.transl(language, "gameFramework.join",
                player.toString(), players.size(), game.getMaxTargetPlayers()
        )).queue();
    }

    /*
    Reactions
     */
    public void joinReaction(CommandContext context) {
        if (Match.PLAYING.containsKey(context.getAuthor()) || getPlayers().contains(Player.user(context.getAuthor()))
                || (betting.isPresent() &&
                !UserProfile.get(context.getAuthor()).transaction(betting.get(), "transactions.betting"))
                || gameState != GameState.PRE_GAME) {
            throw new InvalidCommandSyntaxException();
        }

        joined(Player.user(context.getAuthor()));
    }

    public void aiReaction(CommandContext context) {
        if (!gameState.equals(GameState.PRE_GAME) || !context.getAuthor().equals(creator))
            throw new InvalidCommandSyntaxException();
        joined(Player.ai());
    }

    private void startReaction(User user) {
        if (!gameState.equals(GameState.PRE_GAME) || !user.equals(creator)) return;
        if (game.getGameType().equals(GameType.HYBRID) && players.size() == 1) {
            multiplayer = false;
            matchMessage.then(it -> it.delete().queue());
            Statistics.get().registerGame(game);

            matchHandler.begin(this, no -> {
                MessageBuilder builder = new MessageBuilder().append(Language.transl(language, "gameFramework.singleplayerMatch",
                        game.getName(language), game.getLongDescription(language) + this.mode.map(it ->
                                Language.transl(language, "gameFramework.mode", it.getName(language), it.getDescription(language)))
                            .orElse("")
                ));
                matchHandler.updatedMessage(false, builder);
                return matchMessage = RequestPromise.forAction(channelIn.sendMessage(builder.build())).then(no2 -> gameState = GameState.MATCH);
            });
            players.stream().filter(it -> it.getUser().isPresent()).forEach(it -> achievement(it.getUser().get(),
                    AchievementType.PLAY_GAMES, 1));
        } else {
            if (getPlayers().size() < game.getMinTargetPlayers() + 1) throw new InvalidCommandSyntaxException();

            gameStart();
        }
    }

    private void gameStart() {
        interrupt();
        matchMessage.then(it -> it.delete().queue());
        Statistics.get().registerGame(game);

        matchHandler.begin(this, no -> {
            MessageBuilder builder = new MessageBuilder().append(Language.transl(language, "gameFramework.begin",
                    game.getName(language), game.getLongDescription(language) + mode.map(it ->
                            Language.transl(language, "gameFramework.mode", it.getName(language), it.getDescription(language)))
                            .orElse("")
            ));
            matchHandler.updatedMessage(false, builder);
            return matchMessage = RequestPromise.forAction(channelIn.sendMessage(builder.build())).then(no2 -> gameState = GameState.MATCH);
        });
    }

    public void rematchReaction(CommandContext context) {
        if (!allowRematch) throw new InvalidCommandSyntaxException();
        if (GAMES.containsKey(context.getChannel()) || PLAYING.containsKey(context.getAuthor()) ||
                !players.stream().filter(it -> it.getUser().isPresent()).map(it -> it.getUser().get())
                        .allMatch(context.getReactionUsers()::contains)) return;
        matchMessage.then(it -> it.delete().queue());

        new Match(game, creator, channelIn, players, options, matchesPlayed + 1);
        interrupt();
    }

    public void onEnd(int tokens) {
        onEnd(Player.user(creator), tokens);
    }

    public void onEnd(Player winner) {
        onEnd(winner, players.stream().anyMatch(it -> it.getUser().isPresent()) ? 0 : Constants.MATCH_WIN_TOKENS);
    }

    private void onEnd(Player winner, int tokens) {
        players.forEach(cur ->
            cur.getUser().ifPresent(user -> {
                UserProfile userProfile = UserProfile.get(user);
                boolean victory = winner.equals(Player.user(user));
                if (tokens != 0) userProfile.registerGameResult(channelIn.getGuild(), user, victory, !victory, game);

                if (winner.equals(cur)) {
                    int won = betting.map(it -> it * players.size()).orElse(tokens);
                    userProfile.addTokens(won, "transactions.winGamePrize");
                    achievement(user, AchievementType.REACH_TOKENS, won);
                }
            })
        );
        winner.getUser().ifPresent(user -> achievement(user, AchievementType.WIN_GAMES, 1));

        onEnd(Language.transl(language, "gameFramework.winner", winner.toString())
                + Language.transl(language, "gameFramework.winnerTokens", betting.map(it -> it * players.size())
                        .orElse(tokens), Constants.getPrefix(channelIn.getGuild())), false);
    }

    public void onEnd(String reason, boolean registerPoints) {
        players.forEach(cur ->
            cur.getUser().ifPresent(user -> {
                if (registerPoints) {
                    UserProfile.get(user).registerGameResult(channelIn.getGuild(), user, false, false, game);
                    betting.ifPresent(amount -> {
                        UserProfile.get(user).addTokens(amount, "transactions.winGamePrize");
                        achievement(user, AchievementType.REACH_TOKENS, amount);
                    });
                }
                PLAYING.remove(user);
            })
        );

        allowRematch = gameState == GameState.MATCH;

        MessageBuilder gameOver = new MessageBuilder().append(Language.transl(language, "gameFramework.gameOver",
                reason));
        Log.wrapException("Getting message for match end", () -> {
            if (gameState != GameState.PRE_GAME) matchHandler.updatedMessage(true, gameOver);
        });

        if (Utility.hasPermission(channelIn, channelIn.getGuild().getMember(channelIn.getJDA().getSelfUser()),
                Permission.MESSAGE_ADD_REACTION) && allowRematch)
            gameOver.append("\n").append(Language.transl(language, "gameFramework.rematch"));

        players.forEach(cur -> cur.getUser().ifPresent(user -> {
            if (addAchievement.containsKey(user)) addAchievement.get(user).forEach((type, amount) ->
                type.addAmount(true, amount, gameOver, user, channelIn.getGuild(), language));
        }));

        ACTIVE_GAMES.get(channelIn.getJDA()).remove(this);
        GAMES.remove(channelIn);

        if (matchMessage != null) matchMessage.then(it -> it.editMessage(gameOver.build()).queue());
        else matchMessage = RequestPromise.forAction(channelIn.sendMessage(gameOver.build()));
        
        if (allowRematch) matchMessage.then(msg -> msg.addReaction("\uD83D\uDD04").queue());

        gameState = GameState.POST_MATCH;
        interrupt(); // Stop the timeout
        REMATCH_GAMES.put(channelIn, this);
    }

    private static final int MIN_BET = 10;
    private static final int MAX_BET = 1500;

    public static Command.Executor createCommand(GamesInstance game) {
        return CommandManager.permissionLock(context -> {
            String prefix = Constants.getPrefix(context.getGuild());
            if (GAMES.containsKey(context.getChannel())) {
                Match match = GAMES.get(context.getChannel());
                return Language.transl(context, "gameFramework.activeGame") + (match.getPlayers()
                            .contains(Optional.of(context.getAuthor()))
                        ? GuildProfile.get(context.getGuild()).canStop(context)
                            ? Language.transl(context, "gameFramework.viewPlayersStop", prefix, prefix)
                            : Language.transl(context, "gameFramework.viewPlayers", prefix)
                        : Language.transl(context, "gameFramework.typeToJoin", prefix));
            }
            if (PLAYING.containsKey(context.getAuthor())) {
                return Language.transl(context, "genericMessages.alreadyPlaying",
                        PLAYING.get(context.getAuthor()).getChannelIn().getAsMention(), prefix
                );
            }

            Map<String, String> settings = new HashMap<>();
            context.remaining().forEach(it -> {
                String[] split = it.split("=");
                if (split.length == 2) settings.put(split[0], split[1]);
            });
            if (game.getGameType() == GameType.COLLECTIVE) {
                new Match(game, context.getAuthor(), context.getChannel(), settings);
                return null;
            }

            Optional<Integer> bet = settings.containsKey("bet") ? GameUtil.safeParseInt(settings.get("bet")) : Optional.empty();

            if (bet.isPresent()) {
                int amount = bet.get();
                if (amount < MIN_BET || amount > MAX_BET) return Language.transl(context,
                        "command.slots.invalidTokens", MIN_BET, MAX_BET);
                if (!UserProfile.get(context.getAuthor()).transaction(amount, "transactions.betting"))
                    return Constants.getNotEnoughTokensMessage(context, amount);
            } else if (game.isRequireBetting()) return Language.transl(context, "gameFramework.requireBetting");

            new Match(game, context.getAuthor(), context.getChannel(), settings, bet);
            return null;
        }, ctx -> GuildProfile.get(ctx.getGuild()).canStart(ctx));
    }
}
