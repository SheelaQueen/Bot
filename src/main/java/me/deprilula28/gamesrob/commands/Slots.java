package me.deprilula28.gamesrob.commands;

import me.deprilula28.gamesrob.Language;
import me.deprilula28.gamesrob.achievements.AchievementType;
import me.deprilula28.gamesrob.data.UserProfile;
import me.deprilula28.gamesrob.utility.Constants;
import me.deprilula28.gamesrob.utility.Log;
import me.deprilula28.jdacmdframework.CommandContext;
import net.dv8tion.jda.core.MessageBuilder;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class Slots {
    private static final String[] ITEMS = {
        "🍒", "🍇", "🍎", "🍉", "🔔", "🎲", "👑", "🍋", "🍀"
    };
    private static final int MIN_TOKENS = 50;
    private static final int ALL_ITEMS = 2000;
    private static final int MIN_ITEMS = 3;

    public static String slotsGame(CommandContext context) {
        Random random = new Random(System.currentTimeMillis());
        String next = context.next();
        int betting = next.equalsIgnoreCase("all") ? UserProfile.get(context.getAuthor()).getTokens()
            : Integer.parseInt(next);
        if (betting < MIN_TOKENS) return Language.transl(context, "command.slots.invalidTokens",
                MIN_TOKENS, Language.transl(context, "command.slots.all"));

        UserProfile profile = UserProfile.get(context.getAuthor());
        if (!profile.transaction(betting))
            return Constants.getNotEnoughTokensMessage(context, betting);

        double percentBet = (double) (betting - MIN_TOKENS) / (double) (ALL_ITEMS - MIN_TOKENS);
        int validItemCount = (int) (percentBet * (ITEMS.length - MIN_ITEMS)) + MIN_ITEMS;
        List<String> validItems = betting == ALL_ITEMS ? Arrays.asList(ITEMS) :
                Arrays.stream(ITEMS).sorted(Comparator.comparingInt(it -> random.nextInt(ITEMS.length)))
                    .limit(validItemCount).collect(Collectors.toList());

        List<List<String>> items = new ArrayList<>();
        for (int x = 0; x < 3; x ++) items.add(generateRow(random, validItems));

        context.send(generateMessage(Language.transl(context, "command.slots.matchHeader", betting), items)).then(message -> {
            new Thread(() -> {
                int edits = 2;
                while (edits > 0) {
                    Log.wrapException("Waiting on slots thread", () -> Thread.sleep(TimeUnit.SECONDS.toMillis(1)));
                    items.remove(0);
                    items.add(2, generateRow(random, validItems));
                    if (edits == 1) {
                        List<String> finalRow = items.get(1);
                        List<Integer> finalRowI = finalRow.stream().map(it -> finalRow.stream().mapToInt(item ->
                            item.equals(it) ? 1 : 0).sum()).collect(Collectors.toList());
                        Optional<String> found = finalRow.stream().filter(it -> finalRowI.get(finalRow.indexOf(it)) >= 2)
                            .findAny();

                        double multiplier = 0;
                        if (found.isPresent()) {
                            String tile = found.get();
                            int amount = finalRowI.get(finalRow.indexOf(tile));
                            if (amount == 2) multiplier = 1.3;
                            else if (amount == 3 && tile.equals("🍀")) multiplier = 3.0;
                            else multiplier = 1.8;
                        };

                        int earntAmount = (int) (multiplier * betting);
                        if (multiplier != 0) profile.setTokens(profile.getTokens() + earntAmount);

                        context.edit(it -> {
                            String language = Constants.getLanguage(context);

                            it.append(generateMessage(Language.transl(context, "command.slots.matchEnd",
                                    earntAmount == 0 ? Language.transl(context, "command.slots.lost") :
                                            Language.transl(context, "command.slots.earnt"), Math.abs(earntAmount)),
                                    items));
                            AchievementType.GAMBLE_TOKENS.addAmount(false, betting, it, context.getAuthor(), language);
                            if (earntAmount > 0) AchievementType.WIN_TOKENS_GAMBLING.addAmount(false, earntAmount - betting, it, context.getAuthor(), language);
                            else AchievementType.LOSE_TOKENS_GAMBLING.addAmount(false, betting, it, context.getAuthor(), language);
                            AchievementType.REACH_TOKENS.addAmount(false, earntAmount, it, context.getAuthor(), language);
                        });
                    } else {
                        context.edit(generateMessage(Language.transl(context, "command.slots.matchHeader", betting), items));
                    }
                    edits --;
                }
            }).start();
        });

        return null;
    }

    private static Consumer<MessageBuilder> generateMessage(String header, List<List<String>> items) {
        return builder -> {
            builder.append(new StringBuilder(header + "\n\uD83D\uDD36\uD83D\uDCB8\uD83D\uDD36\n"));
            items.forEach(it -> {
                it.forEach(builder::append);
                builder.append("\n");
            });
        };
    }

    private static List<String> generateRow(Random random, List<String> validItems) {
        List<String> row = new ArrayList<>();
        for (int y = 0 ; y < 3; y ++) row.add(validItems.get(random.nextInt(validItems.size())));

        return row;
    }
}
