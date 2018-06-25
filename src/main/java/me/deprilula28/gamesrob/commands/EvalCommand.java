package me.deprilula28.gamesrob.commands;

import me.deprilula28.gamesrob.GamesROB;
import me.deprilula28.gamesrob.Language;
import me.deprilula28.gamesrob.utility.Utility;
import me.deprilula28.jdacmdframework.CommandContext;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.util.Optional;

public class EvalCommand {
    private static ScriptEngine scriptEngine = new ScriptEngineManager().getEngineByName("nashorn");

    public static String eval(CommandContext context) {
        if (!GamesROB.owners.contains(context.getAuthor().getIdLong())) return Language.transl(context,
                "genericMessages.ownersOnly");

        scriptEngine.put("guild", context.getGuild());
        scriptEngine.put("channel", context.getChannel());
        scriptEngine.put("jda", context.getJda());
        scriptEngine.put("me", context.getAuthor());
        scriptEngine.put("member", context.getAuthorMember());
        scriptEngine.put("shards", GamesROB.shards);

        try {
            long begin = System.nanoTime();
            Object response = scriptEngine.eval(String.join(" ", context.remaining()));
            double time = (double) (System.nanoTime() - begin) / 1000000000.0;
            String responseStr = response.toString();

            String message = "Response took " + Utility.formatPeriod(time) + ":\n" +
                    "```js\n" + (responseStr.length() > 1500 ? responseStr.substring(0, 1500) + "..." : responseStr) + "\n```";

            return Optional.of(message).map(Object::toString).orElse("null");
        } catch (Exception e) {
            return e.getClass().getName() + ": " + e.getMessage();
        }
    }
}
