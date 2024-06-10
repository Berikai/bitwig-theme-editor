package dev.berikai.BitwigTheme;

import dev.berikai.BitwigTheme.asm.JarNode;
import dev.berikai.BitwigTheme.core.ThemeClass;
import dev.berikai.BitwigTheme.core.impl.ArrangerThemeClass;
import dev.berikai.BitwigTheme.core.impl.WindowThemeClass;
import dev.berikai.BitwigTheme.extension.ThemeFile;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

public class Main {
    public static JarNode jar;

    private static void printUsage() throws URISyntaxException {
        String jarName = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath()).getName();

        System.out.println("Usage:  java -jar " + jarName + " <bitwig-jar-path> [command] <theme-path>");
        System.out.println("    export-window      Exports the current window theme to the specified file path (overwrites existing file).");
        System.out.println("    export-arranger    Exports the current arranger theme to the specified file path (overwrites existing file).");
        System.out.println("    update-window      Updates the window theme by modifying the bitwig.jar file based on the specified theme file.");
        System.out.println("    update-arranger    Updates the arranger theme by modifying the bitwig.jar file based on the specified theme file.");
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Bitwig meets themes... Let's go!");

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
            case "export-window":
                exportCurrentWindowTheme(theme_path);
                break;
            case "export-arranger":
                exportCurrentArrangerTheme(theme_path);
                break;
            case "update-window":
                applyWindowTheme(bitwig_path, theme_path);
                break;
            case "update-arranger":
                applyArrangerTheme(bitwig_path, theme_path);
                break;
            default:
                System.out.println("Unknown command: " + command);
                printUsage();
        }
    }

    private static void exportCurrentWindowTheme(String path) throws IOException {
        System.out.println("Exporting current window theme to: " + path);
        ThemeClass themeClass = new WindowThemeClass(jar.getNodes());
        ThemeFile.exportTheme(themeClass.getTheme(), path);
        System.out.println("Window theme successfully exported to: " + path);
    }

    private static void exportCurrentArrangerTheme(String path) throws IOException {
        System.out.println("Exporting current arranger theme to: " + path);
        ThemeClass themeClass = new ArrangerThemeClass(jar.getNodes());
        ThemeFile.exportTheme(themeClass.getTheme(), path);
        System.out.println("Arranger theme successfully exported to: " + path);
    }

    private static void applyWindowTheme(String bitwig_path, String path) throws IOException {
        System.out.println("Applying window theme from: " + path);
        File file = new File(path);
        if (file.exists() && file.canRead()) {
            ThemeClass themeClass = new WindowThemeClass(jar.getNodes());
            themeClass.setTheme(ThemeFile.readTheme(path));
            jar.export(bitwig_path);
            System.out.println("Window theme successfully applied from: " + path);
        } else {
            System.err.println("Failed to apply window theme. File not found or permission issue.");
        }
    }

    private static void applyArrangerTheme(String bitwig_path, String path) throws IOException {
        System.out.println("Applying arranger theme from: " + path);
        File file = new File(path);
        if (file.exists() && file.canRead()) {
            ThemeClass themeClass = new ArrangerThemeClass(jar.getNodes());
            themeClass.setTheme(ThemeFile.readTheme(path));
            jar.export(bitwig_path);
            System.out.println("Arranger theme successfully applied from: " + path);
        } else {
            System.err.println("Failed to apply arranger theme. File not found or permission issue.");
        }
    }
}