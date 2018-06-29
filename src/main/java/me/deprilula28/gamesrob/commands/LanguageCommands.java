package me.deprilula28.gamesrob.commands;

import me.deprilula28.gamesrob.Language;
import me.deprilula28.gamesrob.data.GuildProfile;
import me.deprilula28.gamesrob.data.UserProfile;
import me.deprilula28.gamesrob.utility.Constants;
import me.deprilula28.jdacmdframework.CommandContext;

import java.util.Optional;
import java.util.stream.Collectors;

public class LanguageCommands {
    private static String allLangs = null;

    private static String getAllLangsMessage(String prefix, String command) {
        if (allLangs == null) allLangs = Language.getLanguageList().stream().map(it -> String.format(
                "%s %s `%s` ~%s",
                Language.transl(it, "languageProperties.flag"),
                Language.transl(it, "languageProperties.languageName"),
                Language.transl(it, "languageProperties.code"),
                Language.transl(it, "languageProperties.translators")
        )).collect(Collectors.joining("\n"));

        return "`" + prefix + command + " <co_DE>`\n" + allLangs;
    }

    public static String setGuildLanguage(CommandContext context) {
        Optional<String> language = context.opt(context::next);
        if (language.isPresent()) {
            String it = language.get();

            if (!Language.getLanguageList().contains(it)) return Language.transl(context, "genericMessages.invalidLanguage");
            GuildProfile.get(context.getGuild()).setLanguage(it);
            return Language.transl(context, "command.guildlang.set", it);
        } else return getAllLangsMessage(Constants.getPrefix(context.getGuild()), context.getCurrentCommand().getName());
    }

    public static String setUserLanguage(CommandContext context) {
        Optional<String> language = context.opt(context::next);

        if (language.isPresent()) {
            String it = language.get();

            if (!Language.getLanguageList().contains(it)) return Language.transl(context, "genericMessages.invalidLanguage");
            UserProfile.get(context.getAuthor()).setLanguage(it);
            return Language.transl(it, "command.userlang.set", Language.transl(it, "languageProperties.languageName"));
        } else return getAllLangsMessage(Constants.getPrefix(context.getGuild()), context.getCurrentCommand().getName());
    }
}
