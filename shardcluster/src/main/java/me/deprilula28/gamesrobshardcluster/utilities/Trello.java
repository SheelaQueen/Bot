package me.deprilula28.gamesrobshardcluster.utilities;

import org.trello4j.TrelloImpl;
import org.trello4j.model.Card;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class Trello {
    public static Optional<TrelloImpl> optTrello = Optional.empty();
    private static final String ERROR_DUMP_LIST_ID = "5a2f0e80678b04a926a0656b";

    public static Optional<String> addErrorDump(String name, String stackTrace, String additionalData) {
        try {
            if (optTrello.isPresent()) {
                TrelloImpl trello = optTrello.get();
                for (Card card : trello.getCardsByList(ERROR_DUMP_LIST_ID))
                    if (card.getDesc().equals(stackTrace)) return Optional.of(card.getUrl());

                Map<String, String> map = new HashMap<>();
                map.put("desc", stackTrace);
                Card card = trello.createCard(ERROR_DUMP_LIST_ID, name, map);
                return Optional.of(card.getUrl());
            } else return Optional.empty();
        } catch (Exception e) {
            Log.fatal("Failed to create Trello Error Dump:");
            e.printStackTrace();
            return Optional.empty();
        }
    }
}
