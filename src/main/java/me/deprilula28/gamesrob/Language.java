package me.deprilula28.gamesrob;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import lombok.Getter;
import me.deprilula28.gamesrob.utility.Constants;
import me.deprilula28.gamesrob.utility.Log;
import me.deprilula28.gamesrob.utility.Utility;
import me.deprilula28.jdacmdframework.CommandContext;
import org.yaml.snakeyaml.Yaml;

import java.util.*;

public class Language {
    private static Map<String, Map<String, String>> languages = new HashMap<>();
    @Getter
    private static List<String> languageList = new ArrayList<>();

    public static void loadLanguages() {
        Yaml yaml = new Yaml();
        String rsr = Utility.readResource("/lang/files.json");
        Log.info(rsr);
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

        Log.info("Read language " + language + ", " + Utility.formatBytes(Utility.getRamUsage(keys)));
        languages.put(language, keys);
        languageList.add(language);
    }

    public static String transl(CommandContext context, String key, Object... format) {
        return transl(Constants.getLanguage(context), key, (Object[]) format);
    }

    public static String transl(String language, String key, Object... format) {
        Map<String, String> keys = languages.get(language);
        if (!keys.containsKey(key)) {
            if (language.equals(Constants.DEFAULT_LANGUAGE)) return key;
            else return transl(Constants.DEFAULT_LANGUAGE, key, format);
        }

        return String.format(keys.get(key), format);
    }
}
