package me.deprilula28.gamesrob.baseFramework;

import lombok.Data;
import me.deprilula28.gamesrob.Language;
import me.deprilula28.gamesrob.commands.CommandManager;
import me.deprilula28.gamesrob.data.GuildProfile;
import me.deprilula28.gamesrob.data.Statistics;
import me.deprilula28.gamesrob.data.UserProfile;
import me.deprilula28.gamesrob.utility.Constants;
import me.deprilula28.gamesrob.utility.Log;
import me.deprilula28.gamesrob.utility.Utility;
import me.deprilula28.jdacmdframework.Command;
import me.deprilula28.jdacmdframework.RequestPromise;
import me.deprilula28.jdacmdframework.exceptions.CommandArgsException;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.message.guild.react.GuildMessageReactionAddEvent;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Data
public class Match extends Thread {
    public static final Map<TextChannel, Match> GAMES = new HashMap<>();
    public static final Map<TextChannel, Match> REMATCH_GAMES = new HashMap<>();

    public static final Map<JDA, List<Match>> ACTIVE_GAMES = new HashMap<>();
    public static final Map<User, Match> PLAYING = new HashMap<>();
    private static final long MATCH_TIMEOUT_PERIOD = TimeUnit.MINUTES.toMillis(5);
    private static final long REMATCH_TIMEOUT_PERIOD = TimeUnit.MINUTES.toMillis(1);

    protected TextChannel channelIn;
    private GamesInstance game;
    private GameState gameState;
    private List<Optional<User>> players = new ArrayList<>();
    private User creator;
    private transient MatchHandler matchHandler;

    private int targetPlayerCount;
    private transient RequestPromise<Message> preMatchMessage;

    private String language;
    private List<String> options;
    public int matchesPlayed = 0;
    private boolean canReact;
    private Optional<Integer> betting = Optional.empty();

    public Match(GamesInstance game, User creator, TextChannel channel, int targetPlayerCount, List<Optional<User>> players,
                 List<String> options, int matchesPlayed) {
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

        matchHandler.begin(this, n -> RequestPromise.forAction(
                channelIn.sendMessage(Language.transl(language, "gameFramework.begin",
                        game.getName(language), game.getLongDescription(language), matchHandler.updatedMessage(false)
                ))).then(no -> gameState = GameState.MATCH));
        players.stream().filter(Optional::isPresent).forEach(it -> PLAYING.put(it.get(), this));
    }

    public Match(GamesInstance game, User creator, TextChannel channel, int targetPlayerCount, List<String> options,
                 Optional<Integer> betting) {
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

    public Match(GamesInstance game, User creator, TextChannel channel, List<String> options) {
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
        ACTIVE_GAMES.get(channel.getJDA()).add(this);

        Statistics.get().registerGame(game);
        matchHandler.begin(this, no -> preMatchMessage = RequestPromise.forAction(
            channel.sendMessage(Language.transl(language, "gameFramework.singleplayerMatch",
                    game.getName(language), game.getLongDescription(language), matchHandler.updatedMessage(false)
            ))
        ));

        setName("Game timeout thread for " + game.getName(language));
        setDaemon(true);
        start();
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
                    Permission.MESSAGE_MANAGE)) preMatchMessage.then(it -> it.clearReactions().queue());
        } catch (InterruptedException e) {}
    }

    private void updateSettings(List<String> options) {
        CommandManager.matchHandlerSettings.get(game.getMatchHandlerClass()).forEach(cur -> {
            String name = cur.getField().getName();
            Optional<Integer> intOpt = options.stream().filter(it -> it.startsWith(name + "=")).findFirst()
                    .flatMap(option -> GameUtil.safeParseInt(option.substring(name.length() + 1)));
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

    private String getPregameText() {
        StringBuilder builder = new StringBuilder(Language.transl(language, "gameFramework.multiplayerMatch",
                game.getName(language), game.getShortDescription(language)
        ));
        players.forEach(cur -> builder.append(cur.map(it -> "☑ **" + it.getName() + "**").orElse("AI")).append("\n"));
        for (int i = players.size(); i <= targetPlayerCount; i ++) builder.append("⏰ Waiting\n\n");

        betting.ifPresent(amount -> builder.append(Language.transl(language, "gameFramework.betting", amount)));
        builder.append(Language.transl(language, canReact ? "gameFramework.joinToPlay" : "gameFramework.joinToPlayNoReaction",
                Constants.getPrefix(channelIn.getGuild()), players.size(), targetPlayerCount + 1
        ));

        return builder.toString();
    }

    private void updatePreMessage() {
        if (preMatchMessage != null) preMatchMessage.then(it -> it.delete().queue());
        preMatchMessage = RequestPromise.forAction(channelIn.sendMessage(getPregameText()));
        if (canReact) preMatchMessage.then(msg -> msg.addReaction("\uD83D\uDEAA").queue());
    }

    public void joined(User user) {
        interrupt(); // Keep the match alive
        PLAYING.put(user, this);
        players.add(Optional.of(user));
        if (gameState == GameState.PRE_GAME) {
            if (players.size() == targetPlayerCount + 1) {
                preMatchMessage.then(it -> it.delete().queue());
                Statistics.get().registerGame(game);

                matchHandler.begin(this, n -> RequestPromise.forAction(
                        channelIn.sendMessage(Language.transl(language, "gameFramework.begin",
                                game.getName(language), game.getLongDescription(language), matchHandler.updatedMessage(false)
                        ))).then(no -> gameState = GameState.MATCH));
            } else updatePreMessage();
        } else channelIn.sendMessage(Language.transl(language, "gameFramework.join",
            user.getAsMention(),
            players.size(), targetPlayerCount + 1
        )).queue();
    }

    public void reaction(GuildMessageReactionAddEvent event) {
        if (event.getUser().isBot()) return;
        String name = event.getReactionEmote().getName();

        if (name.equals("\uD83D\uDEAA")) {
            // Trying to join
            if (Match.PLAYING.containsKey(event.getUser()) || getPlayers().contains(Optional.of(event.getUser()))
                    || getPlayers().size() == getTargetPlayerCount() + 1 || (betting.isPresent() &&
                    !UserProfile.get(event.getUser()).transaction(betting.get()))) {
                if (Utility.hasPermission(channelIn, channelIn.getGuild().getMember(channelIn.getJDA().getSelfUser()),
                    Permission.MESSAGE_MANAGE)) event.getReaction().removeReaction(event.getUser()).queue();
                return;
            }

            joined(event.getUser());
        } else if (name.equals("\uD83D\uDD04")) {
            // Rematch
            List<User> allVoted = event.getReaction().getUsers().getCached();
            if (GAMES.containsKey(event.getChannel()) || PLAYING.containsKey(event.getUser()) ||
                    !players.stream().filter(Optional::isPresent).map(Optional::get).allMatch(allVoted::contains)) return;
            preMatchMessage.then(it -> it.delete().queue());

            if (game.getGameType().equals(GameType.SINGLEPLAYER)) new Match(game, creator, channelIn, options)
                    .matchesPlayed = matchesPlayed + 1;
            else new Match(game, creator, channelIn, targetPlayerCount, players, options, matchesPlayed + 1);
            interrupt();
        }
    }

    public void onEnd(Optional<User> winner) {
        players.forEach(cur ->
            cur.ifPresent(user -> {
                UserProfile userProfile = UserProfile.get(user);
                boolean victory = winner.equals(Optional.of(user));
                userProfile.registerGameResult(channelIn.getGuild(), user, victory, !victory, game);

                if (winner.equals(cur)) userProfile.addTokens(betting.map(it -> it * players.size())
                        .orElse(Constants.MATCH_WIN_TOKENS));
            })
        );

        onEnd(Language.transl(language, "gameFramework.winner",
                winner.map(User::getAsMention).orElse("**AI**") + " + \uD83D\uDD38 " +
                betting.map(it -> it * players.size()).orElse(Constants.MATCH_WIN_TOKENS) + " tokens"), false);
    }

    public void onEnd(String reason, boolean registerPoints) {
        players.forEach(cur ->
            cur.ifPresent(user -> {
                if (registerPoints) {
                    UserProfile.get(user).registerGameResult(channelIn.getGuild(), user, false, false, game);
                    betting.ifPresent(amount -> UserProfile.get(user).addTokens(amount));
                }
                PLAYING.remove(user);
            })
        );

        String gameOver = Language.transl(language, "gameFramework.gameOver",
                reason,
                gameState == GameState.PRE_GAME ? "" : matchHandler.updatedMessage(true)
        );
        if (Utility.hasPermission(channelIn, channelIn.getGuild().getMember(channelIn.getJDA().getSelfUser()),
                Permission.MESSAGE_ADD_REACTION)) gameOver += "\n" + Language.transl(language, "gameFramework.rematch");

        ACTIVE_GAMES.get(channelIn.getJDA()).remove(this);
        GAMES.remove(channelIn);

        preMatchMessage = RequestPromise.forAction(channelIn.sendMessage(gameOver));
        preMatchMessage.then(msg -> msg.addReaction("\uD83D\uDD04").queue());

        gameState = GameState.POST_MATCH;
        interrupt(); // Stop the timeout
        REMATCH_GAMES.put(channelIn, this);
    }

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

            if (game.getGameType() == GameType.MULTIPLAYER) {
                int targetPlayers = context.opt(context::nextInt).map(it -> it - 1).orElse(game.getMinTargetPlayers());
                if (targetPlayers > game.getMaxTargetPlayers() || targetPlayers < game.getMinTargetPlayers()) {
                    return Language.transl(context, "gameFramework.playersOutOfRange",
                            game.getMinTargetPlayers() + 1, game.getMaxTargetPlayers() + 1
                    );
                }
                new Match(game, context.getAuthor(), context.getChannel(), targetPlayers, context.remaining(),
                        context.opt(context::nextInt));
            } else new Match(game, context.getAuthor(), context.getChannel(), context.remaining());

            return null;
        }, ctx -> GuildProfile.get(ctx.getGuild()).canStart(ctx));
    }
}
