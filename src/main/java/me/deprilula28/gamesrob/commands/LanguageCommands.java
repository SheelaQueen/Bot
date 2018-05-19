package me.deprilula28.gamesrob.commands;

import me.deprilula28.gamesrob.Language;
import me.deprilula28.gamesrob.data.GuildProfile;
import me.deprilula28.gamesrob.data.UserProfile;
import me.deprilula28.jdacmdframework.CommandContext;
import me.deprilula28.jdacmdframework.exceptions.CommandArgsException;

import java.util.Optional;
import java.util.stream.Collectors;

public class LanguageCommands {
    public static String setGuildLanguage(CommandContext context) {
        String language = context.next();
        if (!Language.getLanguageList().contains(language)) return Language.transl(context, "genericMessages.invalidLanguage");

        GuildProfile.get(context.getGuild()).setLanguage(language);
        return Language.transl(context, "command.guildlang.set", language);
    }

    public static String setUserLanguage(CommandContext context) {
        Optional<String> language = context.opt(context::next);

        if (language.isPresent()) {
            String it = language.get();

            if (!Language.getLanguageList().contains(it)) return Language.transl(context, "genericMessages.invalidLanguage");
            UserProfile.get(context.getAuthor()).setLanguage(it);
            return Language.transl(it, "command.userlang.set", Language.transl(it, "languageProperties.languageName"));
        } else {
            return "`g*lang <Language>`\n" + Language.getLanguageList().stream().map(it -> String.format(
                    "%s (%s) by %s",
                    Language.transl(it, "languageProperties.languageName"),
                    Language.transl(it, "languageProperties.code"),
                    Language.transl(it, "languageProperties.translators")
            )).collect(Collectors.joining("\n"));
        }
    }
}
