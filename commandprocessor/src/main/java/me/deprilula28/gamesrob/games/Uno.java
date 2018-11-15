package me.deprilula28.gamesrob.games;

import lombok.AllArgsConstructor;
import lombok.Data;
import me.deprilula28.gamesrob.baseFramework.*;
import me.deprilula28.gamesrob.commands.ImageCommands;
import me.deprilula28.gamesrob.utility.Language;
import me.deprilula28.gamesrob.utility.Utility;
import me.deprilula28.gamesrobshardcluster.utilities.Constants;
import me.deprilula28.jdacmdframework.RequestPromise;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.User;

import javax.imageio.ImageIO;
import javax.xml.ws.Provider;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
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

    private Optional<String> eventMessage = Optional.empty();
    private Map<Player, String> playerItems = new HashMap<>();
    private Map<Player, List<UnoCard>> decks = new HashMap<>();
    private Map<User, RequestPromise<Message>> dmMessages = new HashMap<>();
    private Random random;
    private UnoCard card;
    private UnoCard.UnoCardColor color;
    private static final int CARDS_LIMIT = 26;
    private int roundsDrawing;

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
            BLOCK((uno) -> uno.turn ++, (uno) -> Language.transl(uno.match.getLanguage(), "game.uno.block", uno.seekTurn().toString())),
            INVERT((uno) -> {
                List<Player> oldList = new ArrayList<>(uno.match.getPlayers());
                uno.match.getPlayers().sort(Comparator.comparingInt(oldList::indexOf).reversed());
            }, (uno) -> Language.transl(uno.match.getLanguage(), "game.uno.invert")),
            PLUS2((uno) -> uno.draw(uno.seekTurn(), 2), (uno) -> Language.transl(uno.match.getLanguage(),
                    "game.uno.draw2", uno.seekTurn().toString(), 2)),
            PLUS4((uno) -> uno.draw(uno.seekTurn(), 4), (uno) -> Language.transl(uno.match.getLanguage(),
                    "game.uno.draw4", uno.color.toString(), uno.seekTurn().toString(), 4)),
            WILDCARD((uno) -> {}, (uno) -> Language.transl(uno.match.getLanguage(), "game.uno.wildcard", uno.color.toString()));

            private Consumer<Uno> handler;
            private Function<Uno, String> eventTranslHandler;
        }

        public boolean canUse(UnoCard card, UnoCardColor topColor) {
            return color.equals(UnoCardColor.SPECIAL) || topColor.equals(this.color) ||
                    (card.number == -1 ? card.action.equals(action) : card.number == number);
        }

        private String getId() {
            return color.toString().toLowerCase() + (action == null ? number : action.toString().toLowerCase());
        }

        @Override
        public String toString() {
            return GameUtil.getEmote(getId()).orElse("unknown");
        }

        public String getPath() {
            return "res/uno/" + getId() + ".png";
        }
    }

    private void draw(Player player, int amount) {
        List<UnoCard> cards = decks.get(player);
        for (int i = 0; i < amount && cards.size() < CARDS_LIMIT;  i++)
            cards.add(randomCard(true));
    }

    // Numbers according to the original deck
    private static final int CARDS_PER_DECK = 108;
    private static final int CARD_COLOR_AMOUNT = 25;
    private static final int SPECIAL_CARD_AMOUNT = 8;
    private static final int NUMBER_CHANCE = 19;
    private static final int NON_WILD_ACTIONS = 3;
    private static final int STARTING_CARDS = 7;

    private UnoCard getValidCard() {
        return new UnoCard(color, random.nextInt(9), null);
    }

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
                dmMessages.put(user, RequestPromise.forAction(pm.sendFile(getDmMessage(cards), "itsjustgettingstarted.png")));
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
        if (values.length > 2 || values[0].length() != 1) return;

        int cardi = Utility.inputLetter(values[0]);
        List<UnoCard> deck = decks.get(Player.user(author));
        if (cardi < 0 || cardi > deck.size()) return;
        UnoCard card = deck.get(cardi);
        if (!card.canUse(this.card, color)) return;

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

        if (card.action == null) eventMessage = Optional.of(Language.transl(match.getLanguage(), "game.uno.played",
                author.getAsMention(), card.toString(), ""));
        else {
            card.action.handler.accept(this);
            eventMessage = Optional.of(Language.transl(match.getLanguage(), "game.uno.played",
                    author.getAsMention(), card.toString(), card.action.eventTranslHandler.apply(this)));
        }

        roundsDrawing = 0;
        match.getPlayers().forEach(player -> player.getUser().ifPresent(this::updateMessage));
        if (!detectVictory()) nextTurn();
    }

    private void updateMessage(User user) {
        dmMessages.get(user).morphAction(it -> {
            List<UnoCard> deck = decks.get(Player.user(user));
            it.delete().queue();

            if (deck.isEmpty()) return it.getChannel().sendMessage(Language.transl(match.getLanguage(), "game.uno.won"));
            else {
                byte[] message = getDmMessage(deck);
                return it.getChannel().sendFile(message, "hellyeahunodeckisanimagenow.png");
            }
        });
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
            updateMessage(user);
            user.openPrivateChannel().queue(it -> it.sendMessage(Language.transl(match.getLanguage(), "game.uno.drawCard")).queue());
            if (roundsDrawing ++ > 3) decks.get(Player.user(user)).add(getValidCard());
            else draw(getTurn(), 1);
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

    private static final int IMAGE_WIDTH = 800;
    private static final int IMAGE_HEIGHT = 700;

    private static final double MAX_ANGLE = 135.0;
    private static final double ANGLE_INC_PER_CARD = 22.5;

    private static final int CENTER_POINT_X = IMAGE_WIDTH / 2;
    private static final int CENTER_POINT_Y = IMAGE_HEIGHT - 60;
    private static final int CENTER_POINT_Y_SEL = IMAGE_HEIGHT - 90;
    private static final int RENDER_CARD_HEIGHT = IMAGE_HEIGHT - 300;
    private static final int RENDER_CARD_WIDTH = (int) (RENDER_CARD_HEIGHT / 1.5);

    private byte[] getDmMessage(List<UnoCard> deck) {
        ByteArrayOutputStream baos = null;
        try {
            BufferedImage image = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = image.createGraphics();

            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2d.setFont(Utility.getStarlightFont().deriveFont(42.0f));

            double angle = Math.min(MAX_ANGLE, ANGLE_INC_PER_CARD * Math.ceil((deck.size() - 1) / 2.0));
            double cardAngle = angle / Math.max(1, deck.size() - 1);

            for (int i = 0; i < deck.size(); i++) {
                double rotAngle = -angle / 2.0 + i * cardAngle;
                g2d.rotate(Math.toRadians(rotAngle), CENTER_POINT_X, CENTER_POINT_Y);
                UnoCard card = deck.get(i);
                boolean canUse = card.canUse(this.card, this.color);

                g2d.drawImage(ImageCommands.getImageFromWebsite(card.getPath()), CENTER_POINT_X - RENDER_CARD_WIDTH / 2,
                        canUse ? CENTER_POINT_Y_SEL - RENDER_CARD_HEIGHT : CENTER_POINT_Y - RENDER_CARD_HEIGHT,
                        RENDER_CARD_WIDTH, RENDER_CARD_HEIGHT, null);

                if (canUse) {
                    g2d.drawString(String.valueOf((char) ((int) 'A' + i)), CENTER_POINT_X - RENDER_CARD_WIDTH / 2,
                            CENTER_POINT_Y_SEL - RENDER_CARD_HEIGHT - g2d.getFontMetrics().getHeight());
                }
                g2d.rotate(-Math.toRadians(rotAngle), CENTER_POINT_X, CENTER_POINT_Y);
            }

            baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
        } catch (IOException e) {
            Utility.quietlyClose(baos);
            throw new RuntimeException(e);
        }

        return baos.toByteArray();
    }

    @Override
    public String turnUpdatedMessage(boolean over) {
        StringBuilder builder = new StringBuilder();
        eventMessage.ifPresent(it -> builder.append(it).append("\n\n"));
        if (!over) builder.append(Language.transl(match.getLanguage(), "game.uno.chooseCard", getTurn().getUser()
                .map(User::getAsMention).orElseThrow(() -> new RuntimeException("Asked update message on AI turn."))));

        builder.append(Language.transl(match.getLanguage(), "game.uno.game", Constants.GAMESROB_DOMAIN + "/" + card.getPath()));

        appendTurns(builder, playerItems, user -> decks.get(user).stream().map(it -> over ? it.toString() :
                GameUtil.getEmote("unknown").orElse("card")).collect(Collectors.joining("")));
        eventMessage = Optional.empty();

        return builder.toString();
    }

    @Override
    public void handleAIPlay() {
        // TODO
    }
}
