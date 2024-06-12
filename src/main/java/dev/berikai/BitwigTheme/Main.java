package dev.berikai.BitwigTheme;

import com.formdev.flatlaf.FlatIntelliJLaf;
import dev.berikai.BitwigTheme.UI.Frame;
import dev.berikai.BitwigTheme.asm.JarNode;
import dev.berikai.BitwigTheme.core.Color;
import dev.berikai.BitwigTheme.core.ThemeClass;
import dev.berikai.BitwigTheme.core.impl.ArrangerThemeClass;
import dev.berikai.BitwigTheme.core.impl.WindowThemeClass;
import dev.berikai.BitwigTheme.extension.ThemeFile;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;

public class Main {
    public static JarNode jar;

    private static void printUsage() throws URISyntaxException {
        String jarName = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath()).getName();

        System.out.println("Usage:  java -jar " + jarName + " <bitwig-jar-path> [command] <theme-path>");
        System.out.println("    export      Exports the current theme to the specified file path (overwrites existing file).");
        System.out.println("    apply       Applies the theme by modifying the bitwig.jar file based on the specified theme file.");
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Bitwig meets themes... Let's go!");

        if (args.length == 0) {
            UIManager.setLookAndFeel(new FlatIntelliJLaf());
            new Frame();
            return;
        }

        if (args.length < 3) {
            System.err.println("Not enough arguments. Usage:");
            printUsage();
            return;
        }

        String bitwig_path = args[0];
        String command = args[1];
        String theme_path = args [2];

        jar = new JarNode(bitwig_path);

        switch (command) {
            case "export":
                exportCurrentTheme(theme_path, jar);
                break;
            case "apply":
                applyTheme(bitwig_path, theme_path, jar);
                break;
            default:
                System.out.println("Unknown command: " + command);
                printUsage();
        }
    }

    public static void exportCurrentTheme(String path, JarNode jar) throws IOException {
        System.out.println("Exporting current theme to: " + path);
        ThemeClass windowThemeClass = new WindowThemeClass(jar.getNodes());
        ThemeClass arrangerThemeClass = new ArrangerThemeClass(jar.getNodes());

        HashMap<String, HashMap<String, Color>> theme = new HashMap<>();
        theme.put("window", windowThemeClass.getTheme());
        theme.put("arranger", arrangerThemeClass.getTheme());

        ThemeFile.exportTheme(theme, path);
        System.out.println("Theme successfully exported to: " + path);
    }

    public static void applyTheme(String bitwig_path, String path, JarNode jar) throws IOException {
        System.out.println("Applying theme from: " + path);
        File file = new File(path);
        if (file.exists() && file.canRead()) {
            ThemeClass windowThemeClass = new WindowThemeClass(jar.getNodes());
            ThemeClass arrangerThemeClass = new ArrangerThemeClass(jar.getNodes());

            windowThemeClass.setTheme(ThemeFile.readTheme(path).get("window"));
            arrangerThemeClass.setTheme(ThemeFile.readTheme(path).get("arranger"));

            jar.export(bitwig_path);
            System.out.println("Theme successfully applied from: " + path);
        } else {
            System.err.println("Failed to apply theme. File not found or permission issue.");
        }
    }
}