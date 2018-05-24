package me.deprilula28.gamesrob.commands;

import me.deprilula28.gamesrob.GamesROB;
import me.deprilula28.gamesrob.Language;
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
            return Optional.ofNullable(scriptEngine.eval(String.join(" ", context.remaining()))).map(Object::toString)
                    .orElse("null");
        } catch (Exception e) {
            return e.getClass().getName() + ": " + e.getMessage();
        }
    }
}
