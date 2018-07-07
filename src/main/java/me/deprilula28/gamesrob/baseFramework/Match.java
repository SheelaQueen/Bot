package me.deprilula28.gamesrob.baseFramework;

import lombok.Data;
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

import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Data
public class Match extends Thread {
    public static final Map<TextChannel, Match> GAMES = new HashMap<>();
    public static final Map<TextChannel, Match> REMATCH_GAMES = new HashMap<>();

    public static final Map<JDA, List<Match>> ACTIVE_GAMES = new HashMap<>();
    public static final Map<User, Match> PLAYING = new HashMap<>();
    private static final long MATCH_TIMEOUT_PERIOD = TimeUnit.MINUTES.toMillis(5);
    private static final long REMATCH_TIMEOUT_PERIOD = TimeUnit.MINUTES.toMillis(1);

    protected TextChannel channelIn;
    private final Map<User, Map<AchievementType, Integer>> addAchievement = new HashMap<>();
    private GamesInstance game;
    private GameState gameState;
    private List<Optional<User>> players = new ArrayList<>();
    private User creator;
    private transient MatchHandler matchHandler;

    private int targetPlayerCount;
    private transient RequestPromise<Message> matchMessage;

    private String language;
    private Map<String, String> options;
    public int matchesPlayed = 0;
    private boolean canReact;
    private Optional<Integer> betting = Optional.empty();
    private boolean multiplayer;
    public int iteration;
    private boolean allowRematch;

    public Match(GamesInstance game, User creator, TextChannel channel, int targetPlayerCount, List<Optional<User>> players,
                 Map<String, String> options, int matchesPlayed) {
        multiplayer = true;
        String guildLang = GuildProfile.get(channel.getGuild()).getLanguage();
        language = guildLang == null ? Constants.DEFAULT_LANGUAGE : guildLang;
        channelIn = channel;
        canReact = canReact();

        this.creator = creator;
        this.players = players;
        this.game = game;
        this.options = options;
        this.targetPlayerCount = targetPlayerCount;

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
        players.stream().filter(Optional::isPresent).forEach(it -> {
            PLAYING.put(it.get(), this);
            achievement(it.get(), AchievementType.PLAY_GAMES, 1);
        });
    }

    public Match(GamesInstance game, User creator, TextChannel channel, int targetPlayerCount, Map<String, String> options,
                 Optional<Integer> betting) {
        multiplayer = true;
        String guildLang = GuildProfile.get(channel.getGuild()).getLanguage();
        language = guildLang == null ? Constants.DEFAULT_LANGUAGE : guildLang;
        channelIn = channel;
        canReact = canReact();
        players.add(Optional.of(creator));

        this.betting = betting;
        this.creator = creator;
        this.game = game;
        this.options = options;
        this.targetPlayerCount = targetPlayerCount;
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

    public Match(GamesInstance game, User creator, TextChannel channel, Map<String, String> options) {
        multiplayer = false;
        String guildLang = GuildProfile.get(channel.getGuild()).getLanguage();
        String userLang = UserProfile.get(creator).getLanguage();
        language = userLang == null ?  guildLang == null ? Constants.DEFAULT_LANGUAGE : guildLang : userLang;
        channelIn = channel;
        canReact = canReact();
        players.add(Optional.of(creator));

        this.creator = creator;
        this.game = game;
        matchHandler = game.getMatchHandlerSupplier().get();
        gameState = GameState.MATCH;
        updateSettings(options);

        GAMES.put(channel, this);
        PLAYING.put(creator, this);
        achievement(creator, AchievementType.PLAY_GAMES, 1);
        ACTIVE_GAMES.get(channel.getJDA()).add(this);

        Statistics.get().registerGame(game);

        matchHandler.begin(this, no -> {
            MessageBuilder builder = new MessageBuilder().append(Language.transl(language, "gameFramework.singleplayerMatch",
                    game.getName(language), game.getLongDescription(language)
            ));
            matchHandler.updatedMessage(false, builder);
            return matchMessage = RequestPromise.forAction(channel.sendMessage(builder.build()));
        });

        setName("Game timeout thread for " + game.getName(language));
        setDaemon(true);
        start();
    }

    private byte[] getImage(int iteration) {
        if (this.iteration != iteration) throw new RuntimeException("Invalid iteration.");
        if (!(matchHandler instanceof MatchHandler.ImageMatchHandler)) throw new RuntimeException("Not an image game.");
        return Cache.get(matchHandler.hashCode() + "_" + iteration, n -> ((MatchHandler.ImageMatchHandler) matchHandler).getImage());
    }

    private boolean canReact() {
        Member member = channelIn.getGuild().getMember(channelIn.getJDA().getSelfUser());
        return Utility.hasPermission(channelIn, member, Permission.MESSAGE_ADD_REACTION);
    }

    @Override
    public void run() {
        while (gameState != GameState.POST_MATCH)
            try {
                Thread.sleep(MATCH_TIMEOUT_PERIOD);
                if (gameState != GameState.POST_MATCH)
                    onEnd(Language.transl(language, "gameFramework.timeout", Utility.formatPeriod(MATCH_TIMEOUT_PERIOD)),
                            true);
            } catch (InterruptedException e) {}
        try {
            Thread.sleep(REMATCH_TIMEOUT_PERIOD);
            if (this.equals(REMATCH_GAMES.get(channelIn))) REMATCH_GAMES.remove(channelIn);
            else if (REMATCH_GAMES.containsKey(channelIn)) REMATCH_GAMES.get(channelIn).matchesPlayed = matchesPlayed + 1;
            if (Utility.hasPermission(channelIn, channelIn.getGuild().getMember(channelIn.getJDA().getSelfUser()),
                    Permission.MESSAGE_MANAGE)) matchMessage.then(it -> it.clearReactions().queue());
        } catch (InterruptedException e) {}
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
                cur.getField().set(matchHandler, intOpt.orElse(cur.getAnnotation().defaultValue()));
            } catch (Exception e) {
                throw new RuntimeException("Failed to set value for setting", e);
            }
        });
    }

    public void achievement(User user, AchievementType type, int amount) {
        if (!addAchievement.containsKey(user)) addAchievement.put(user, new HashMap<>());
        if (!addAchievement.get(user).containsKey(type)) addAchievement.get(user).put(type, 0);
        addAchievement.get(user).put(type, amount + addAchievement.get(user).get(type));
    }

    private String getPregameText() {
        StringBuilder builder = new StringBuilder(Language.transl(language, "gameFramework.multiplayerMatch",
                game.getName(language), game.getShortDescription(language)
        ));
        players.forEach(cur -> builder.append(cur.map(it -> "☑ **" + it.getName() + "**").orElse("AI")).append("\n"));
        for (int i = players.size(); i <= targetPlayerCount; i ++) builder.append("⏰ Waiting\n\n");

        betting.ifPresent(amount -> builder.append(Language.transl(language, "gameFramework.betting", amount)).append("\n"));
        builder.append(Language.transl(language, canReact ? "gameFramework.joinToPlay" : "gameFramework.joinToPlayNoReaction",
                Constants.getPrefix(channelIn.getGuild()), players.size(), targetPlayerCount + 1
        ));

        return builder.toString();
    }

    private void updatePreMessage() {
        if (matchMessage != null) matchMessage.then(it -> it.delete().queue());
        matchMessage = RequestPromise.forAction(channelIn.sendMessage(getPregameText()));
        if (canReact) matchMessage.then(msg -> msg.addReaction("\uD83D\uDEAA").queue());
    }

    public void joined(User user) {
        interrupt(); // Keep the match alive
        PLAYING.put(user, this);
        players.add(Optional.of(user));
        if (gameState == GameState.PRE_GAME) {
            if (players.size() == targetPlayerCount + 1) {
                matchMessage.then(it -> it.delete().queue());
                Statistics.get().registerGame(game);

                matchHandler.begin(this, no -> {
                    MessageBuilder builder = new MessageBuilder().append(Language.transl(language, "gameFramework.begin",
                            game.getName(language), game.getLongDescription(language)
                    ));
                    matchHandler.updatedMessage(false, builder);
                    return matchMessage = RequestPromise.forAction(channelIn.sendMessage(builder.build())).then(no2 -> gameState = GameState.MATCH);
                });
                players.stream().filter(Optional::isPresent).forEach(it -> achievement(it.get(), AchievementType.PLAY_GAMES, 1));
            } else updatePreMessage();
        } else channelIn.sendMessage(Language.transl(language, "gameFramework.join",
            user.getAsMention(),
            players.size(), targetPlayerCount + 1
        )).queue();
    }

    public void joinReaction(CommandContext context) {
        if (Match.PLAYING.containsKey(context.getAuthor()) || getPlayers().contains(Optional.of(context.getAuthor()))
                || getPlayers().size() == getTargetPlayerCount() + 1 || (betting.isPresent() &&
                !UserProfile.get(context.getAuthor()).transaction(betting.get())) || gameState != GameState.PRE_GAME) {
            throw new InvalidCommandSyntaxException();
        }

        joined(context.getAuthor());
    }

    public void rematchReaction(CommandContext context) {
        if (!allowRematch) throw new InvalidCommandSyntaxException();
        if (GAMES.containsKey(context.getChannel()) || PLAYING.containsKey(context.getAuthor()) ||
                !players.stream().filter(Optional::isPresent).map(Optional::get).allMatch(context.getReactionUsers()::contains)) return;
        matchMessage.then(it -> it.delete().queue());

        if (game.getGameType().equals(GameType.HYBRID)) new Match(game, creator, channelIn, options)
                .matchesPlayed = matchesPlayed + 1;
        else new Match(game, creator, channelIn, targetPlayerCount, players, options, matchesPlayed + 1);
        interrupt();
    }

    public void onEnd(Optional<User> winner) {
        players.forEach(cur ->
            cur.ifPresent(user -> {
                UserProfile userProfile = UserProfile.get(user);
                boolean victory = winner.equals(Optional.of(user));
                userProfile.registerGameResult(channelIn.getGuild(), user, victory, !victory, game);

                if (winner.equals(cur)) {
                    int won = betting.map(it -> it * players.size()).orElse(Constants.MATCH_WIN_TOKENS);
                    userProfile.addTokens(won);
                    achievement(user, AchievementType.REACH_TOKENS, won);
                }
            })
        );
        winner.ifPresent(it -> achievement(it, AchievementType.WIN_GAMES, 1));

        onEnd(Language.transl(language, "gameFramework.winner", winner.map(User::getAsMention).orElse("**AI**"))
                + Language.transl(language, "gameFramework.winnerTokens", betting.map(it -> it * players.size())
                        .orElse(Constants.MATCH_WIN_TOKENS), Constants.getPrefix(channelIn.getGuild())), false);
    }

    public void onEnd(String reason, boolean registerPoints) {
        players.forEach(cur ->
            cur.ifPresent(user -> {
                if (registerPoints) {
                    UserProfile.get(user).registerGameResult(channelIn.getGuild(), user, false, false, game);
                    betting.ifPresent(amount -> {
                        UserProfile.get(user).addTokens(amount);
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

        players.forEach(cur -> cur.ifPresent(user -> {
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

            Optional<Integer> bet = settings.containsKey("bet") ? GameUtil.safeParseInt(settings.get("bet")) : Optional.empty();
            Optional<Integer> players = settings.containsKey("players") ? GameUtil.safeParseInt(settings.get("players")) : Optional.empty();

            if (bet.isPresent()) {
                int amount = bet.get();
                if (amount < MIN_BET || amount > MAX_BET) return Language.transl(context,
                        "command.slots.invalidTokens", MIN_BET, MAX_BET);
                if (!UserProfile.get(context.getAuthor()).transaction(amount)) return Constants.getNotEnoughTokensMessage(context, amount);
            } else if (game.isRequireBetting()) return Language.transl(context, "gameFramework.requireBetting");

            int targetPlayers = players.map(it -> it - 1).orElse(game.getGameType() == GameType.MULTIPLAYER ? game.getMinTargetPlayers() : 0);
            if (targetPlayers == 0 && game.getGameType() == GameType.HYBRID) new Match(game, context.getAuthor(), context.getChannel(), settings);
            else if(targetPlayers > game.getMaxTargetPlayers() || targetPlayers < game.getMinTargetPlayers())
                return Language.transl(context, "gameFramework.playersOutOfRange",
                        game.getMinTargetPlayers() + 1, game.getMaxTargetPlayers() + 1
                );
            else new Match(game, context.getAuthor(), context.getChannel(), targetPlayers, settings, bet);

            return null;
        }, ctx -> GuildProfile.get(ctx.getGuild()).canStart(ctx));
    }
}
