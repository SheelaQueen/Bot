package me.deprilula28.gamesrob.games;

import lombok.AllArgsConstructor;
import lombok.Data;
import me.deprilula28.gamesrob.Language;
import me.deprilula28.gamesrob.baseFramework.*;
import me.deprilula28.gamesrob.utility.Utility;
import me.deprilula28.jdacmdframework.RequestPromise;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.User;

import javax.xml.ws.Provider;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class Uno extends TurnMatchHandler {
    private static final String[] ITEMS = {
        "<:blue1:465782607318351894>", "<:red2:465782607854960640>", "<:green3:465782607909486602>",
        "<:yellow4:465782608291168256>", "<:blue5:465782607716679680>", "<:red6:465782607922200586>",
        "<:green7:465782607590981633>", "<:yellow8:465782608207282177>", "<:blue9:465782607691644928>",
        "<:redinvert:465782607947497482>", "<:greenblock:465782607670673409>", "<:yellowplus2:465782608194830337>"
    };
    public static final GamesInstance GAME = new GamesInstance(
            "uno", "uno unogame 1",
            1, ITEMS.length - 1, GameType.MULTIPLAYER, false, false,
            Uno::new, Uno.class, Collections.emptyList()
    );

    private Map<Player, String> playerItems = new HashMap<>();
    private Map<Player, List<UnoCard>> decks = new HashMap<>();
    private Map<User, RequestPromise<Message>> dmMessages = new HashMap<>();
    private Random random;
    private UnoCard card;
    private UnoCard.UnoCardColor color;

    @Data
    @AllArgsConstructor
    private static class UnoCard {
        private UnoCardColor color;
        private int number;
        private UnoCardAction action;

        private static enum UnoCardColor {
            BLUE, GREEN, YELLOW, RED, SPECIAL
        }

        @AllArgsConstructor
        private static enum UnoCardAction {
            BLOCK((uno) -> uno.turn ++),
            INVERT((uno) -> {
                List<Player> oldList = new ArrayList<>(uno.match.getPlayers());
                uno.match.getPlayers().sort(Comparator.comparingInt(oldList::indexOf).reversed());
            }),
            PLUS2((uno) -> uno.draw(uno.seekTurn(), 2)),
            PLUS4((uno) -> uno.draw(uno.seekTurn(), 4)),
            WILDCARD((uno) -> {});

            private Consumer<Uno> handler;
        }

        public boolean canUse(UnoCard card, UnoCardColor topColor) {
            return color.equals(UnoCardColor.SPECIAL) || topColor.equals(this.color) ||
                    (card.number == -1 ? card.action.equals(action) : card.number == number);
        }

        @Override
        public String toString() {
            return GameUtil.getEmote(color.toString().toLowerCase() + (action == null ? number : action.toString().toLowerCase())).orElse("unknown");
        }
    }

    private void draw(Player player, int amount) {
        List<UnoCard> cards = decks.get(player);
        for (int i = 0; i < amount; i++)
            cards.add(randomCard(true));
        player.getUser().ifPresent(user -> dmMessages.get(user).then(it -> it.editMessage(getDmMessage(user)).queue()));
    }

    // Numbers according to the original deck
    private static final int CARDS_PER_DECK = 108;
    private static final int CARD_COLOR_AMOUNT = 25;
    private static final int SPECIAL_CARD_AMOUNT = 8;
    private static final int NUMBER_CHANCE = 19;
    private static final int NON_WILD_ACTIONS = 3;
    private static final int STARTING_CARDS = 7;

    private UnoCard randomCard(boolean allowWild) {
        int possibleCards = CARDS_PER_DECK;
        if (!allowWild) possibleCards -= SPECIAL_CARD_AMOUNT;

        UnoCard.UnoCardColor color = UnoCard.UnoCardColor.values()[random.nextInt(possibleCards) / CARD_COLOR_AMOUNT];
        if (color.equals(UnoCard.UnoCardColor.SPECIAL)) return new UnoCard(color, -1,
                UnoCard.UnoCardAction.values()[random.nextInt(UnoCard.UnoCardAction.values().length -
                        NON_WILD_ACTIONS) + NON_WILD_ACTIONS]);
        else {
            if (random.nextInt(CARD_COLOR_AMOUNT) <= NUMBER_CHANCE) return new UnoCard(color, random.nextInt(9), null);
            return new UnoCard(color, -1, UnoCard.UnoCardAction.values()[random.nextInt(NON_WILD_ACTIONS)]);
        }
    }

    @Override
    public void begin(Match match, Provider<RequestPromise<Message>> initialMessage) {
        random = GameUtil.generateRandom();
        card = randomCard(false);
        color = card.color;
        match.getPlayers().forEach(it -> {
            List<UnoCard> cards = new ArrayList<>();
            for (int i = 0; i < STARTING_CARDS; i ++) cards.add(randomCard(true));
            decks.put(it, cards);

            it.getUser().ifPresent(user -> user.openPrivateChannel().queue(pm -> {
                pm.sendMessage(Language.transl(match.getLanguage(), "game.uno.deckDm", match.getChannelIn().getAsMention())).queue();
                dmMessages.put(user, RequestPromise.forAction(pm.sendMessage(getDmMessage(user))));
            }));
        });

        Utility.populateItems(match.getPlayers(), ITEMS, playerItems, new HashMap<>());
        super.begin(match, initialMessage);
    }

    @Override
    public void receivedDM(String contents, User from, Message reference) { }

    @Override
    public void receivedMessage(String contents, User author, Message reference) {
        messages ++;
        Player cur = getTurn();
        if (!cur.getUser().filter(it -> it.equals(author)).isPresent()) return;
        String[] values = contents.split(" ");
        if (values.length > 2) return;
        if (values[0].length() != 1) return;

        int cardi = GameUtil.safeParseInt(values[0]).orElse(-1);
        List<UnoCard> deck = decks.get(Player.user(author));
        if (cardi < 1 || cardi > deck.size()) return;
        UnoCard card = deck.get(cardi - 1);
        if (!card.canUse(this.card, color)) return;

        UnoCard.UnoCardColor oldColor = color;
        if (card.color.equals(UnoCard.UnoCardColor.SPECIAL)) {
            boolean doreturn = true;
            if (values.length == 1) match.getChannelIn().sendMessage(Language.transl(match.getLanguage(), "game.uno.noColor")).queue();
            else try {
                color = UnoCard.UnoCardColor.valueOf(values[1].toUpperCase());
                doreturn = false;
            } catch (Exception e) {
                match.getChannelIn().sendMessage(Language.transl(match.getLanguage(), "game.uno.invalidColor")).queue();
            }
            if (doreturn) return;
        } else color = card.color;
        deck.remove(card);
        this.card = card;

        if (card.action != null) card.action.handler.accept(this);
        if (!color.equals(oldColor) || card.action != null) match.getPlayers().forEach(player ->
                player.getUser().ifPresent(this::updateMessage));
        else cur.getUser().ifPresent(this::updateMessage);
        if (!detectVictory()) nextTurn();
    }

    private void updateMessage(User user) {
        dmMessages.get(user).then(it -> it.editMessage(getDmMessage(user)).queue());
    }

    @Override
    protected boolean isInvalidTurn() {
        return !getTurn().getUser().isPresent() || decks.get(getTurn()).stream().noneMatch(it -> it.canUse(card, color));
    }

    @Override
    protected void handleInvalidTurn() {
        if (!getTurn().getUser().isPresent()) handleAIPlay();
        else {
            User user = getTurn().getUser().get();
            dmMessages.get(user).then(it -> it.editMessage(getDmMessage(user)).queue());
            user.openPrivateChannel().queue(it -> it.sendMessage(Language.transl(match.getLanguage(), "game.uno.drawCard")).queue());
            draw(getTurn(), 1);
            updateMessage(user);
        }
    }

    @Override
    public boolean detectVictory() {
        Optional<Map.Entry<Player, List<UnoCard>>> winner = decks.entrySet().stream()
                .filter(it -> it.getValue().size() <= 0).findAny();

        winner.ifPresent(it -> match.onEnd(it.getKey()));
        return winner.isPresent();
    }

    private String getDmMessage(User user) {
        List<UnoCard> deck = decks.get(Player.user(user));
        if (deck.isEmpty()) return Language.transl(match.getLanguage(), "game.uno.won");

        StringBuilder options = new StringBuilder();
        for (int i = 0; i < deck.size(); i ++) {
            if (deck.get(i).canUse(card, color)) options.append(Utility.getNumberEmote(i));
            else options.append("⬜");
        }

        return options.toString() + "\n" + deck.stream().map(Object::toString).collect(Collectors.joining(""));
    }

    @Override
    public String turnUpdatedMessage(boolean over) {
        StringBuilder builder = new StringBuilder();
        if (!over) builder.append(Language.transl(match.getLanguage(), "game.uno.chooseCard", getTurn().getUser()
                .map(User::getAsMention).orElseThrow(() -> new RuntimeException("Asked update message on AI turn."))));

        builder.append(Language.transl(match.getLanguage(), "game.uno.game", card.toString() +
                (card.color.equals(UnoCard.UnoCardColor.SPECIAL) ? color.toString() : "")));

        appendTurns(builder, playerItems, user -> decks.get(user).stream().map(it -> over ? it.toString() :
                GameUtil.getEmote("unknown").orElse("card")).collect(Collectors.joining("")));

        return builder.toString();
    }

    @Override
    public void handleAIPlay() {
        // TODO
    }
}
