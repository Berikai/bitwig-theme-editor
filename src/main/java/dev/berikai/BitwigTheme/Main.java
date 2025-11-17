package dev.berikai.BitwigTheme;

import com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTMaterialDarkerIJTheme;
import com.formdev.flatlaf.util.SystemInfo;
import dev.berikai.BitwigTheme.UI.MainUI;
import dev.berikai.BitwigTheme.asm.JarNode;
import dev.berikai.BitwigTheme.core.BitwigClass;
import dev.berikai.BitwigTheme.core.PatchClass;
import dev.berikai.BitwigTheme.core.IntegrityClass;
import dev.berikai.BitwigTheme.core.impl.BridgePatchClass;
import dev.berikai.BitwigTheme.core.impl.ColorPatchClass;
import org.objectweb.asm.tree.FieldNode;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.net.URISyntaxException;
import java.util.zip.ZipException;

public class Main {
    public static String version; // Project version
    public static String bitwigVersion; // Bitwig Studio version, obtained from BitwigClass
    public static String configPath; // Path to config directory
    public static JarNode jar; // ASM-tree JarNode object for bitwig.jar
    public static boolean isGUI = false; // Whether the app is running with GUI or command line

    private static void printUsage() throws URISyntaxException {
        // Get bitwig-theme-editor-x.x.x.jar location
        String jarName = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath()).getName();

        // Print usage
        System.out.println("Usage:  java -jar " + jarName + " <bitwig-jar-path>");
    }

    public static void main(String[] args) throws Exception {
        // Get version
        version = Main.class.getPackage().getImplementationVersion();
        version = (version != null) ? version : "Development";

        // Print introduction
        System.out.println("Bitwig Theme Editor " + version);
        System.out.println("DISCLAIMER: This is an unofficial free open source 3rd party tool, made with educational purposes only. Author is not responsible for any damage caused by using this tool. Use at your own risk.");
        System.out.println();

        // Initialize config path
        // (e.g. Windows: C:\Users\<User>\AppData\Roaming\.bitwig-theme-editor, Linux: /home/<User>/.bitwig-theme-editor)
        initializeConfigPath();

        // Run UI, if no argument given
        if (args.length == 0) {
            // macOS specific settings
            if(SystemInfo.isMacOS) {
                System.setProperty("apple.awt.application.name", "Bitwig Theme Editor");
                System.setProperty("apple.awt.application.appearance", "system");
                //System.setProperty("apple.laf.useScreenMenuBar", "true"); // Not sure if this is better in terms of UX
            }

            UIManager.setLookAndFeel(new FlatMTMaterialDarkerIJTheme());
            try {
                isGUI = true;
                new MainUI();
            } catch (HeadlessException headlessException) {
                System.out.println("ERROR: GUI cannot be run in headless environment.");
                System.out.println(" -> Make sure your JRE/JDK installation includes GUI support (e.g. Oracle JDK).");
                System.out.println(" -> As a last resort, you can use command line with jar path instead.");
                System.out.println();
                printUsage();
                System.out.println();
            }
            return;
        }

        // Print usage, if argument count isn't 1
        if (args.length != 1) {
            System.out.println("ERROR: Wrong usage!");
            System.out.println();
            printUsage();
            return;
        }

        // Get bitwig.jar path from the first argument
        String bitwig_path = args[0];

        // Create an ASM-tree JarNode object for bytecode manipulation
        try {
            jar = new JarNode(bitwig_path);
        } catch (ZipException e) {
            System.out.println("ERROR: JAR file " + bitwig_path + " is not valid or corrupted.");
            System.out.println();
            throw new RuntimeException(e);
        } catch (Exception e) {
            System.out.println("ERROR: JAR file " + bitwig_path + " does not exist or could not be accessed. Try to run as admin/root.");
            System.out.println();
            throw new RuntimeException(e);
        }

        // Patch jar
        // 1 = success, 0 = fail
        final int result = applyPatch(bitwig_path, jar);
        if(result == 1) {
            // Brief usage instruction
            System.out.println("Patch successful!");
            System.out.println();
            System.out.println("1. Run Bitwig Studio (run as admin/root if step 2 fails)");
            System.out.println("2. A file named 'default.bte' will be created in the directory: ");
            System.out.println(" -> " + getVersionConfigPath("default.bte"));
            System.out.println("3. Create a file named 'theme.bte' in the same directory");
            System.out.println("4. Add the lines of the color values you want to change, modify, and save");
            System.out.println("5. Click on the 'Dashboard Button' or resize the window to render changes");
            System.out.println("Note: You can also run this app with GUI to change theme!");
            System.out.println();

            System.out.println("Happy theming!");
            System.out.println();
        }

        // ❤
        System.out.println("If you enjoy the project and feel like showing a little love, thank you!");
        System.out.println("❤ Donate ETH/BNB: 0x3aCdA83c0EAD65033cD532357De3c8B71b1C94d5");
        System.out.println("❤ Buy Me A Coffee: https://buymeacoffee.com/verdant");
        System.out.println();
    }

    public static int applyPatch(String bitwig_path, JarNode jar) {
        System.out.println("Patching started...");

        BitwigClass bwClass = new BitwigClass(jar.getNodes());
        if (!bwClass.isBitwigJAR()) {
            System.out.println("ERROR: Selected JAR file is not Bitwig Studio JAR.");
            System.out.println();
            return 3;
        }

        bitwigVersion = bwClass.getVersion();
        System.out.println("Detected Bitwig Studio version: " + bitwigVersion);

        // Initialize class patchers
        PatchClass colorClass = new ColorPatchClass(jar.getNodes());
        PatchClass bridgeClass = new BridgePatchClass(jar.getNodes());

        // Check if ColorClass already has the field "colorName"
        // This is to prevent double patching
        boolean alreadyPatched = false;
        for (FieldNode field : colorClass.getClassNode().fields) {
            if (field.name.equals("colorName") && field.desc.equals("Ljava/lang/String;")) {
                alreadyPatched = true;
                break;
            }
        }

        if (alreadyPatched) {
            System.out.println("WARNING: Your bitwig.jar is already patched! Skipping...");
            System.out.println();
            return 0;
        }

        // Write all PatchClasss.mappings elements to console
        // It can be helpful for the folks who want to experiment with the bytecode themselves
        for (String key : PatchClass.mappings.keySet()) {
            System.out.println("> Mapping: " + key + " -> " + PatchClass.mappings.get(key));
        }
        System.out.println();

        // Apply patches
        colorClass.patch();
        bridgeClass.patch();

        // Disable integrity check
        IntegrityClass integrityClass = new IntegrityClass(jar.getNodes());
        integrityClass.disableIntegrity();

        // Try exporting and overwriting the jar
        // If the operation succeeds, return 1
        try {
            jar.export(bitwig_path);
            return 1;
        } catch (Exception e) {
            System.out.println("ERROR: Failed to patch jar. Couldn't write to JAR file.");
            System.out.println();
            e.printStackTrace();
            if(isGUI) {
                JOptionPane.showMessageDialog(null,
                        "Failed to patch jar.\nError: " + e.getMessage(),
                        "Error!",
                        JOptionPane.INFORMATION_MESSAGE);
            }
            return 2;
        }
    }

    private static void initializeConfigPath() {
        String appDirName = ".bitwig-theme-editor";
        String os = System.getProperty("os.name").toLowerCase();

        String basePath;
        if (os.contains("win")) basePath = System.getenv("APPDATA");
        else basePath = System.getProperty("user.home");

        // File seperator of OS (e.g. Windows: "\", Linux & macOS: "/")
        String sep = File.separator;

        // Build full directory path
        String directoryPath = basePath + sep + appDirName;

        // Create directory if it doesn't exist
        if(new File(directoryPath).mkdirs()) {
            System.out.println("Created config directory: " + directoryPath);
        }

        configPath = directoryPath;
    }

    public static String getVersionConfigPath(String filename) {
        String directoryPath = configPath + File.separator + bitwigVersion;

        // Create directory if it doesn't exist
        if(new File(directoryPath).mkdirs()) {
            System.out.println("Created version config directory: " + directoryPath);
        }

        return directoryPath + File.separator + filename;

    }
}