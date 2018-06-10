package me.deprilula28.gamesrob.utility;

import com.github.kevinsawicki.http.HttpRequest;
import com.google.gson.reflect.TypeToken;
import org.trello4j.TrelloImpl;
import org.trello4j.model.Card;

import java.io.Console;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public class Trello {
    public static Optional<TrelloImpl> optTrello = Optional.empty();
    private static final String ERROR_DUMP_LIST_ID = "5b14965180d04f4d71fc2966";

    public static void addErrorDump(String name, String stackTrace, String additionalData) {
        Log.info(optTrello.isPresent());
        optTrello.ifPresent(trello -> {
            boolean sent = false;
            for (Card card : trello.getCardsByList(ERROR_DUMP_LIST_ID)) {
                if (card.getDesc().equals(stackTrace)) {
                    Log.info(card.getId());
                    sent = true;
                }
            }

            if (!sent) {
                Map<String, String> map = new HashMap<>();
                map.put("desc", stackTrace);
                Card card = trello.createCard(ERROR_DUMP_LIST_ID, name, map);
                Log.info(card.getId());
            }
        });
    }
}
