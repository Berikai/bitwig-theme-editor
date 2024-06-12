package dev.berikai.BitwigTheme.extension;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.berikai.BitwigTheme.core.Color;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;

public class ThemeFile {
    public static HashMap<String, HashMap<String, Color>> readTheme(String path) throws IOException {
        String content = Files.readString(Paths.get(path), StandardCharsets.UTF_8);

        Gson gson = new Gson();

        Type type = new TypeToken<HashMap<String, HashMap<String, String>>>() {}.getType();
        HashMap<String, HashMap<String, String>> themePair = gson.fromJson(content, type);

        HashMap<String, Color> windowTheme = new HashMap<>();
        HashMap<String, Color> arrangerTheme = new HashMap<>();

        HashMap<String, HashMap<String, Color>> theme = new HashMap<>();

        for (String key : themePair.get("window").keySet()) {
            windowTheme.put(key, new Color(themePair.get("window").get(key)));
        }

        for (String key : themePair.get("arranger").keySet()) {
            arrangerTheme.put(key, new Color(themePair.get("arranger").get(key)));
        }

        theme.put("window", windowTheme);
        theme.put("arranger", arrangerTheme);

        return theme;
    }

    public static void exportTheme(HashMap<String, HashMap<String, Color>> theme, String path) throws IOException {
        HashMap<String, String> windowThemePair = new HashMap<>();
        HashMap<String, String> arrangerThemePair = new HashMap<>();

        HashMap<String, HashMap<String, String>> themePair = new HashMap<>();

        for (String key : theme.get("window").keySet()) {
            windowThemePair.put(key, theme.get("window").get(key).getHex());
        }

        for (String key : theme.get("arranger").keySet()) {
            arrangerThemePair.put(key, theme.get("arranger").get(key).getHex());
        }

        themePair.put("window", windowThemePair);
        themePair.put("arranger", arrangerThemePair);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        String content = gson.toJson(themePair);

        PrintWriter out = new PrintWriter(path);
        out.println(content);
        out.close();

    }
}
