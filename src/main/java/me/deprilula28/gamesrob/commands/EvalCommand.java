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

        try {
            return Optional.ofNullable(scriptEngine.eval(String.format(" ", context.remaining()))).map(Object::toString)
                    .orElse("null");
        } catch (Exception e) {
            return e.getClass().getName() + ": " + e.getMessage();
        }
    }
}
