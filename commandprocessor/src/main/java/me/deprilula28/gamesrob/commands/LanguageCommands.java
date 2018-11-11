package me.deprilula28.gamesrob.commands;

import me.deprilula28.gamesrob.utility.Language;
import me.deprilula28.gamesrob.data.GuildProfile;
import me.deprilula28.gamesrob.data.UserProfile;
import me.deprilula28.gamesrob.utility.Utility;
import me.deprilula28.gamesrobshardcluster.utilities.Constants;
import me.deprilula28.jdacmdframework.CommandContext;

import java.util.Optional;
import java.util.stream.Collectors;

public class LanguageCommands {
    private static String allLangs = null;

    private static String getAllLangsMessage(CommandContext context) {
        String curLang = Utility.getLanguage(context);
        return Language.transl(context, "genericMessages.languagecommand.header",
                Language.transl(context, "languageProperties.flag"), Language.transl(context, "languageProperties.languageName"),
                Utility.getPrefix(context.getGuild()) + context.getCurrentCommand().getName()) +
                Language.getLanguageList().stream().filter(it -> !it.equals(curLang)).map(it -> Language.transl(context,
            "genericMessages.languagecommand.entry", String.format("%s %s `%s`",
                Language.transl(it, "languageProperties.flag"), Language.transl(it, "languageProperties.languageName"),
                Language.transl(it, "languageProperties.code")), Language.transl(it, "languageProperties.translators")))
            .collect(Collectors.joining("\n")) + Language.transl(context, "genericMessages.languagecommand.bottom",
                Constants.GAMESROB_DOMAIN + "/translators");
    }

    public static String setGuildLanguage(CommandContext context) {
        Optional<String> language = context.opt(context::next);
        if (language.isPresent()) {
            String it = language.get();

            if (!Language.getLanguageList().contains(it)) return Language.transl(context, "genericMessages.invalidLanguage");

            GuildProfile profile = GuildProfile.get(context.getGuild());
            profile.setEdited(true);
            profile.setLanguage(it);
            return Language.transl(context, "command.guildlang.set", it);
        } else return getAllLangsMessage(context);
    }

    public static String setUserLanguage(CommandContext context) {
        Optional<String> language = context.opt(context::next);

        if (language.isPresent()) {
            String it = language.get();

            if (!Language.getLanguageList().contains(it)) return Language.transl(context, "genericMessages.invalidLanguage");

            UserProfile profile = UserProfile.get(context.getAuthor());
            profile.setEdited(true);
            profile.setLanguage(it);
            return Language.transl(it, "command.userlang.set", Language.transl(it, "languageProperties.languageName"));
        } else return getAllLangsMessage(context);
    }
}
