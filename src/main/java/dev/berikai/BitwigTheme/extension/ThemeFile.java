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
    public static HashMap<String, Color> readTheme(String path) throws IOException {
        String content = Files.readString(Paths.get(path), StandardCharsets.UTF_8);

        Gson gson = new Gson();

        Type type = new TypeToken<HashMap<String, String>>() {}.getType();
        HashMap<String, String> themePair = gson.fromJson(content, type);

        HashMap<String, Color> theme = new HashMap<>();

        for (String key : themePair.keySet()) {
            theme.put(key, new Color(themePair.get(key)));
        }

        return theme;
    }

    public static void exportTheme(HashMap<String, Color> theme, String path) throws IOException {
        HashMap<String, String> themePair = new HashMap<>();

        for (String key : theme.keySet()) {
            themePair.put(key, theme.get(key).getHex());
        }

        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        String content = gson.toJson(themePair);

        PrintWriter out = new PrintWriter(path);
        out.println(content);
        out.close();

    }
}
