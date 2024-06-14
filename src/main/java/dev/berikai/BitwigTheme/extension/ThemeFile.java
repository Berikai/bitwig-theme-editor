package dev.berikai.BitwigTheme.extension;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.berikai.BitwigTheme.core.BitwigColor;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import javax.swing.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.TreeMap;

public class ThemeFile {
    public static HashMap<String, HashMap<String, BitwigColor>> readTheme(String path) throws IOException {
        String content = Files.readString(Paths.get(path), StandardCharsets.UTF_8);

        HashMap<String, HashMap<String, String>> themePair = new HashMap<>();

        if (path.toLowerCase().endsWith(".yaml")) {
            Yaml yaml = new Yaml();
            themePair = yaml.load(content);
        } else if (path.toLowerCase().endsWith(".json")) {
            Gson gson = new Gson();

            Type type = new TypeToken<HashMap<String, HashMap<String, String>>>() {}.getType();
            themePair = gson.fromJson(content, type);
        } else {
            System.out.println("Unknown extension! Please use a YAML or JSON file.");
            JOptionPane.showMessageDialog(null, "Unknown extension! Please use a YAML or JSON file.", "ERROR!", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }

        HashMap<String, BitwigColor> windowTheme = new HashMap<>();
        HashMap<String, BitwigColor> arrangerTheme = new HashMap<>();

        HashMap<String, HashMap<String, BitwigColor>> theme = new HashMap<>();

        for (String key : themePair.get("window").keySet()) {
            windowTheme.put(key, new BitwigColor(themePair.get("window").get(key)));
        }

        for (String key : themePair.get("arranger").keySet()) {
            arrangerTheme.put(key, new BitwigColor(themePair.get("arranger").get(key)));
        }

        theme.put("window", windowTheme);
        theme.put("arranger", arrangerTheme);

        return theme;
    }

    // Accepts TreeMap rather HashMap in the inner map to get a ordered pair.
    public static void exportTheme(HashMap<String, TreeMap<String, BitwigColor>> theme, String path) throws IOException {
        TreeMap<String, String> windowThemePair = new TreeMap<>();
        TreeMap<String, String> arrangerThemePair = new TreeMap<>();

        HashMap<String, TreeMap<String, String>> themePair = new HashMap<>();

        for (String key : theme.get("window").keySet()) {
            windowThemePair.put(key, theme.get("window").get(key).getHex());
        }

        for (String key : theme.get("arranger").keySet()) {
            arrangerThemePair.put(key, theme.get("arranger").get(key).getHex());
        }

        themePair.put("window", windowThemePair);
        themePair.put("arranger", arrangerThemePair);

        if (path.toLowerCase().endsWith(".yaml")) {
            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            options.setPrettyFlow(true);

            Yaml yaml = new Yaml(options);
            String yaml_content = yaml.dump(themePair);

            PrintWriter out = new PrintWriter(path);
            out.println(yaml_content);
            out.close();
        } else if (path.toLowerCase().endsWith(".json")) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String json_content = gson.toJson(themePair);

            PrintWriter out = new PrintWriter(path);
            out.println(json_content);
            out.close();
        } else {
            System.out.println("Unknown extension! Please use a YAML or JSON file.");
            JOptionPane.showMessageDialog(null, "Unknown extension! Please use a YAML or JSON file.", "ERROR!", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }

    }
}
