package dev.berikai.BitwigTheme.UI;

import dev.berikai.BitwigTheme.Main;
import dev.berikai.BitwigTheme.asm.JarNode;

import javax.swing.*;
import java.io.File;
import java.net.URISyntaxException;
import java.util.Scanner;

import static dev.berikai.BitwigTheme.Main.applyPatch;

public class MainUI extends JFrame {
    public MainUI() throws URISyntaxException {
        loadConfig();
    }

    public static void updateConfig(String bitwigPath) {
        try {
            File configFile = new File(Main.configPath, "bte_config.txt");
            if (!configFile.exists()) {
                configFile.createNewFile();
            }
            try (java.io.FileWriter writer = new java.io.FileWriter(configFile)) {
                writer.write("# Bitwig Theme Editor configuration file\n" + "bitwig_path=" + bitwigPath + "\n");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void loadConfig() throws URISyntaxException {
        File configFile = new File(Main.configPath, "bte_config.txt");
        if (configFile.exists()) {
            try (Scanner scanner = new Scanner(configFile)) {
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    // Process the line as needed
                    String[] parts = line.split("=");
                    if (parts.length == 2) {
                        String key = parts[0].trim();
                        String value = parts[1].trim();
                        // Store or use the key-value pair as needed
                        if(key.equals("bitwig_path")) {
                            Main.jar = new JarNode(value);
                            final int result = applyPatch(value, Main.jar);
                            new Editor(value, result);
                            return;
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        new Welcome();
    }
}
