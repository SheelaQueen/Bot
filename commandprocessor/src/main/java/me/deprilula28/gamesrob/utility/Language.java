package me.deprilula28.gamesrob.utility;

import com.google.gson.reflect.TypeToken;
import lombok.Getter;
import me.deprilula28.gamesrobshardcluster.utilities.Constants;
import me.deprilula28.gamesrob.utility.Utility;
import me.deprilula28.jdacmdframework.CommandContext;
import org.yaml.snakeyaml.Yaml;

import java.util.*;

public class Language {
    @Getter private static Map<String, Map<String, String>> languages = new HashMap<>();
    @Getter private static List<String> languageList = new ArrayList<>();

    public static void loadLanguages() {
        Yaml yaml = new Yaml();
        String rsr = Utility.readResource("/lang/files.json");
        List<String> list = Constants.GSON.fromJson(rsr, new TypeToken<List<String>>(){}.getType());
        list.forEach(el -> readLanguage(el, Utility.readResource("/lang/" + el + ".yaml"), yaml));
    }

    private static void recReadTree(Map<String, Object> curMap, String prefix, Map<String, String> output) {
        curMap.forEach((key, value) -> {
            if (value instanceof String) output.put(prefix + key, (String) value);
            else if (value instanceof Map) recReadTree((Map<String, Object>) value, prefix + key + ".", output);
        });
    }

    private static void readLanguage(String language, String text, Yaml yaml) {
        Map<String, String> keys = new HashMap<>();
        recReadTree(yaml.load(text), "", keys);

        languages.put(language, keys);
        languageList.add(language);
    }

    public static String transl(CommandContext context, String key, Object... format) {
        return transl(Utility.getLanguage(context), key, (Object[]) format);
    }

    public static String transl(String language, String key, Object... format) {
        Map<String, String> keys = languages.get(language);
        if (!keys.containsKey(key)) {
            if (language.equals(Constants.DEFAULT_LANGUAGE)) return key;
            else return transl(Constants.DEFAULT_LANGUAGE, key, format);
        }

        try {
            return String.format(keys.get(key), format);
        } catch (MissingFormatArgumentException e) {
            return "This language seems to be glitched.\nPlease report this error here: https://discord.gg/rBAprga\n" +
                    "`" + language + ", Key " + key + ", formatted " + format.length + "`\n" +
                    "If this is an important function, you can always go back to english until it's fixed \uD83D\uDE09";
        }
    }
}
