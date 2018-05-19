package me.deprilula28.gamesrob.baseFramework;

import lombok.Data;
import me.deprilula28.gamesrob.Language;
import me.deprilula28.gamesrob.commands.CommandManager;
import me.deprilula28.gamesrob.data.GuildProfile;
import me.deprilula28.gamesrob.data.Statistics;
import me.deprilula28.gamesrob.data.UserProfile;
import me.deprilula28.gamesrob.utility.Constants;
import me.deprilula28.gamesrob.utility.Utility;
import me.deprilula28.jdacmdframework.Command;
import me.deprilula28.jdacmdframework.RequestPromise;
import me.deprilula28.jdacmdframework.exceptions.CommandArgsException;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.entities.User;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Data
public class Match extends Thread {
    public static final Map<TextChannel, Match> GAMES = new HashMap<>();
    public static final Map<JDA, List<Match>> ACTIVE_GAMES = new HashMap<>();
    public static final Map<User, Match> PLAYING = new HashMap<>();
    private static final long TIMEOUT_PERIOD = TimeUnit.MINUTES.toMillis(5);

    protected TextChannel channelIn;
    private GamesInstance game;
    private GameState gameState;
    private List<Optional<User>> players = new ArrayList<>();
    private User creator;
    private transient MatchHandler matchHandler;

    private int targetPlayerCount;
    private transient RequestPromise<Message> preMatchMessage;

    private String language;

    public Match(GamesInstance game, User creator, TextChannel channel, int targetPlayerCount, List<String> options) {
        players.add(Optional.of(creator));

        this.creator = creator;
        this.game = game;
        this.targetPlayerCount = targetPlayerCount;
        matchHandler = game.getMatchHandlerSupplier().get();
        updateSettings(options);

        channelIn = channel;
        gameState = GameState.PRE_GAME;

        GAMES.put(channel, this);
        PLAYING.put(creator, this);
        ACTIVE_GAMES.get(channel.getJDA()).add(this);

        String guildLang = GuildProfile.get(channel.getGuild()).getLanguage();
        language = guildLang == null ? Constants.DEFAULT_LANGUAGE : guildLang;

        preMatchMessage = RequestPromise.forAction(channel.sendMessage(getPregameText()));

        setName("Game timeout thread for " + game.getName(language));
        setDaemon(true);
        start();
    }

    public Match(GamesInstance game, User creator, TextChannel channel, List<String> options) {
        players.add(Optional.of(creator));

        this.creator = creator;
        this.game = game;
        matchHandler = game.getMatchHandlerSupplier().get();
        channelIn = channel;
        gameState = GameState.MATCH;

        GAMES.put(channel, this);
        PLAYING.put(creator, this);
        ACTIVE_GAMES.get(channel.getJDA()).add(this);

        updateSettings(options);

        String guildLang = GuildProfile.get(channel.getGuild()).getLanguage();
        String userLang = UserProfile.get(creator).getLanguage();
        language = userLang == null ?  guildLang == null ? Constants.DEFAULT_LANGUAGE : guildLang : userLang;

        matchHandler.begin(this, no -> preMatchMessage = RequestPromise.forAction(
            channel.sendMessage(Language.transl(language, "gameFramework.singleplayerMatch",
                    game.getName(language), game.getLongDescription(language), matchHandler.updatedMessage(false)
            ))
        ));
        Statistics.get().registerGame(game);

        setName("Game timeout thread for " + game.getName(language));
        setDaemon(true);
        start();
    }

    @Override
    public void run() {
        while (gameState != GameState.POST_MATCH)
            try {
                Thread.sleep(TIMEOUT_PERIOD);
                if (gameState != GameState.POST_MATCH)
                    onEnd(Language.transl(language, "gameFramework.timeout", Utility.formatPeriod(TIMEOUT_PERIOD)),
                            true);
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
        for (int i = players.size(); i <= targetPlayerCount; i ++) builder.append("⏰ Waiting\n");

        builder.append(Language.transl(language, "gameFramework.joinToPlay",
                Constants.getPrefix(channelIn.getGuild()), players.size(), targetPlayerCount + 1
        ));

        return builder.toString();
    }

    private void updatePreMessage() {
        preMatchMessage.then(it -> it.delete().queue());
        preMatchMessage = RequestPromise.forAction(channelIn.sendMessage(getPregameText()));
    }

    public void left(User user) {
        interrupt(); // Keep the match alive
        if (gameState == GameState.PRE_GAME) {
            if (user == creator) {
                preMatchMessage.then(it -> it.delete().queue());
                onEnd(Language.transl(language, "gameFramework.timeout", game.getName(language)), false);
            } else updatePreMessage();
        } else {
            if (players.stream().filter(Optional::isPresent).count() - 1 < game.getMinTargetPlayers()) {
                onEnd(Language.transl(language, "gameFramework.everyoneQuit"), true);
            } else {
                players.add(players.indexOf(Optional.of(user)), Optional.empty()); // Replace user with bot
                matchHandler.onQuit(user);
                channelIn.sendMessage(Language.transl(language, "gameFramework.left", user.getName())).queue();
            }
        }
        PLAYING.remove(user);
        players.remove(Optional.of(user));
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

    public void onEnd(Optional<User> winner) {
        players.forEach(cur ->
            cur.ifPresent(user -> {
                boolean victory = winner.equals(Optional.of(user));
                UserProfile.get(user).registerGameResult(channelIn.getGuild(), user, victory, !victory, game);
            })
        );

        onEnd(Language.transl(language, "gameFramework.winner", winner.map(User::getAsMention)
                .orElse("**AI**")), false);
    }

    public void onEnd(String reason, boolean registerPoints) {
        players.forEach(cur ->
            cur.ifPresent(user -> {
                if (registerPoints) UserProfile.get(user).registerGameResult(channelIn.getGuild(), user, false, false, game);
                PLAYING.remove(user);
            })
        );

        ACTIVE_GAMES.get(channelIn.getJDA()).remove(this);
        GAMES.remove(channelIn);
        channelIn.sendMessage(Language.transl(language, "gameFramework.gameOver",
                reason,
                gameState == GameState.PRE_GAME ? "" : matchHandler.updatedMessage(true)
        )).queue();
        gameState = GameState.POST_MATCH;
        interrupt(); // Stop the timeout
    }

    public static Command.Executor createCommand(GamesInstance game) {
        return CommandManager.permissionLock(context -> {
            String prefix = Constants.getPrefix(context.getGuild());
            if (GAMES.containsKey(context.getChannel())) {
                Match match = GAMES.get(context.getChannel());
                return Language.transl(context, "gameFramework.activeGame") + (match.getPlayers()
                            .contains(Optional.of(context.getAuthor()))
                        ? Language.transl(context, "gameFramework.viewPlayers", prefix, prefix)
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
                new Match(game, context.getAuthor(), context.getChannel(), targetPlayers, context.remaining());
            } else new Match(game, context.getAuthor(), context.getChannel(), context.remaining());

            return null;
        }, ctx -> GuildProfile.get(ctx.getGuild()).canStart(ctx));
    }
}
