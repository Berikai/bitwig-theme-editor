package dev.berikai.BitwigTheme.UI;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import dev.berikai.BitwigTheme.Main;
import dev.berikai.BitwigTheme.UI.components.ColorPanel;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.FontUIResource;
import javax.swing.text.StyleContext;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.net.URI;
import java.util.Locale;
import java.util.Scanner;

import static java.lang.String.format;

public class Editor extends JFrame {
    private JPanel mainPanel;
    private JTabbedPane tabbedPane1;
    private JList<ColorPanel> list1;
    private JCheckBox disableGradientCheckBox;
    private JLabel buyMeACoffeeLabel;
    private JLabel ethereumLabel;
    private JTextField textField1;
    private JCheckBox alwaysOnTopCheckbox;
    private JCheckBox minimizeThemeOnExportCheckBox;
    private JList<ColorPanel> list2;
    private JTextField textField2;
    private JCheckBox compatibilityForOldThemesCheckBox;
    private JLabel colorsSizeLabel;
    private JLabel modifiedSizeLabel;
    private JButton undoButton1;
    private JButton redoButton1;
    private JButton undoButton2;
    private JButton redoButton2;

    private int hoveredIndex = -1;

    private boolean isBitwig6OrNewer = false;
    private final String bitwig_path;

    private final UndoRedoManager undoRedoManager = new UndoRedoManager(50);

    public Editor(String bitwig_path, int result) {
        this.bitwig_path = bitwig_path;

        handlePatchResult(result);
        if (!checkThemeFile()) return;

        initializeMenuBar();

        setContentPane(mainPanel);
        setTitle("Bitwig Theme Editor " + Main.version);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(430, 500);
        setMinimumSize(new Dimension(430, 360));
        setLocationRelativeTo(null); // Center the window
        setAlwaysOnTop(true);
        //setResizable(false);

        initializeUI();

        loadDefaultThemeColors();
        loadThemeColors(bitwig_path.replace("bitwig.jar", "theme.bte"));

        setVisible(true);
    }

    private void handlePatchResult(int result) {
        setEnabled(false); // Disable the window until user closes the dialog

        switch (result) {
            case 3:
                JOptionPane.showMessageDialog(this, "Selected JAR file is not Bitwig Studio JAR.", "Error!", JOptionPane.INFORMATION_MESSAGE);
                System.exit(1);
                break;
            case 2:
                // Error messages are already shown in applyPatch method, simply exit here
                System.exit(1);
                break;
            case 1:
                JOptionPane.showMessageDialog(this, "Patched JAR file successfully!", "Success!", JOptionPane.INFORMATION_MESSAGE);
                JOptionPane.showMessageDialog(this, "Now, please run your patched Bitwig Studio installation to initialize default theme color values for theming! Then, press OK.", "Run Bitwig Studio", JOptionPane.INFORMATION_MESSAGE);
                break;
            // case 0: Do nothing if result is 0 (already patched)
        }

        MainUI.updateConfig(bitwig_path);

        setEnabled(true);
    }

    private void initializeMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        JMenu fileMenu = new JMenu("File");
        JMenuItem importTheme = new JMenuItem("Import");
        JMenuItem exportTheme = new JMenuItem("Export");
        JMenuItem exit = new JMenuItem("Exit");
        fileMenu.add(importTheme);
        fileMenu.add(exportTheme);
        fileMenu.addSeparator();
        fileMenu.add(exit);
        menuBar.add(fileMenu);

        JMenu editorMenu = new JMenu("Editor");
        JMenuItem resetToStock = new JMenuItem("Reset to default");
        JMenuItem selectJar = new JMenuItem("Select JAR");
        editorMenu.add(resetToStock);
        editorMenu.addSeparator();
        editorMenu.add(selectJar);
        menuBar.add(editorMenu);

        JMenu helpMenu = new JMenu("Help");
        JMenuItem about = new JMenuItem("About");
        JMenuItem github = new JMenuItem("Github");
        helpMenu.add(about);
        helpMenu.addSeparator();
        helpMenu.add(github);
        menuBar.add(helpMenu);

        about.addActionListener(e -> showAboutDialog());
        github.addActionListener(e -> openWebpage("https://github.com/berikai/bitwig-theme-editor"));

        resetToStock.addActionListener(e -> {
            int response = JOptionPane.showConfirmDialog(this, "Are you sure you want to reset all changes to default?", "Confirm Reset", JOptionPane.YES_NO_OPTION);
            if (response == JOptionPane.YES_OPTION) {
                loadDefaultThemeColors();
                System.out.println("-> Reset to default");
                boolean minimize = minimizeThemeOnExportCheckBox.isSelected();
                minimizeThemeOnExportCheckBox.setSelected(false); // Temporarily disable minimize to export all colors, needed to reset to stock values
                saveThemeColors(bitwig_path.replace("bitwig.jar", "theme.bte"));
                minimizeThemeOnExportCheckBox.setSelected(minimize);
            }
        });

        selectJar.addActionListener(e -> {
            int response = JOptionPane.showConfirmDialog(this, "Are you sure you want to select another bitwig.jar? Unsaved changes will be lost.", "Confirm Select JAR", JOptionPane.YES_NO_OPTION);
            if (response == JOptionPane.YES_OPTION) {
                setVisible(false);
                Welcome.selectJar(this);
                dispose();
            }
        });

        importTheme.addActionListener(e -> {
            int response = JOptionPane.showConfirmDialog(this, "Importing a theme will overwrite your current changes.\nAll unsaved changes will be lost. Are you sure you want to continue?", "Confirm Import", JOptionPane.YES_NO_OPTION);
            if (response != JOptionPane.YES_OPTION) return;

            ThemeChooser themeChooser = new ThemeChooser("Open");
            if (themeChooser.getSelectedFile() != null) {
                loadDefaultThemeColors();
                if (themeChooser.getSelectedFile().getPath().endsWith(".json")) {
                    JOptionPane.showMessageDialog(this, "Please note that JSON themes are deprecated, but importing them is still supported. Please export your theme afterwards to upgrade it to new format.", "Warning", JOptionPane.WARNING_MESSAGE);
                    loadThemeColorsFromJSON(themeChooser.getSelectedFile().getPath());
                } else {
                    loadThemeColors(themeChooser.getSelectedFile().getPath());
                }
                System.out.println("-> Import theme from: " + themeChooser.getSelectedFile().getPath());
                saveThemeColors(bitwig_path.replace("bitwig.jar", "theme.bte"));
            }
        });
        exportTheme.addActionListener(e -> {
            ThemeChooser themeChooser = new ThemeChooser("Save");
            if (themeChooser.getSelectedFile() != null) {
                saveThemeColors(themeChooser.getSelectedFile().getPath());
                System.out.println("-> Export theme to: " + themeChooser.getSelectedFile().getPath());
            }
        });
        exit.addActionListener(e -> System.exit(0));

        setJMenuBar(menuBar);
    }

    private void initializeUI() {
        for (JList<ColorPanel> list : new JList[]{list1, list2}) {
            DefaultListModel<ColorPanel> listModel = new DefaultListModel<>();
            list.setModel(listModel);

            list.setCellRenderer((_list, value, index, isSelected, cellHasFocus) -> {
                if (index == hoveredIndex) {
                    value.setBackground(Color.decode("#404040")); // hover color
                    if (isSelected) value.setBackground(Color.decode("#506550")); // hover + selected color
                    if (ColorPanel.reverted != null && ColorPanel.reverted.equals(value)) {
                        value.setBackground(Color.decode("#605050")); // hover + reverted color
                    }
                } else if (isSelected) {
                    value.setBackground(Color.decode("#405040")); // selected color
                    if (ColorPanel.reverted != null && ColorPanel.reverted.equals(value)) {
                        value.setBackground(Color.decode("#604040")); // selected + reverted color
                    }
                } else {
                    value.setBackground(Color.decode("#303030")); // default color
                    if (ColorPanel.reverted != null && ColorPanel.reverted.equals(value)) {
                        value.setBackground(Color.decode("#604040")); // reverted color
                    }
                }
                return value;
            });

            list.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            list.addMouseMotionListener(new MouseMotionAdapter() {
                public void mouseMoved(MouseEvent e) {
                    int index = list.locationToIndex(e.getPoint());
                    if (index != hoveredIndex) {
                        hoveredIndex = index;
                        list.repaint();
                    }
                }
            });
            list.addMouseListener(new MouseAdapter() {
                public void mouseExited(MouseEvent e) {
                    if (hoveredIndex != -1) {
                        hoveredIndex = -1;
                        list.repaint();
                    }
                }

                public void mouseClicked(MouseEvent mouseEvent) {
                    int index = list.locationToIndex(mouseEvent.getPoint());
                    if (index != -1 && mouseEvent.getClickCount() == 1) {
                        ColorPanel item = listModel.getElementAt(index);
                        ColorPanel.reverted = null;

                        Color newColor = JColorChooser.showDialog(item, "Choose Color", ColorPanel.decodeRGBA(item.getValue()));
                        if (newColor != null) {
                            String newColorHex = format("#%02x%02x%02x%02x", newColor.getRed(), newColor.getGreen(), newColor.getBlue(), newColor.getAlpha());
                            undoRedoManager.addAction(new ColorAction(item.getKey(), item.getValue(), newColorHex));
                            item.getColorDisplayPanel().setBackground(newColor);
                            item.setValue(newColorHex);
                            updateModifiedList(item);
                            item.setToolTipText(item.getKey() + ": " + item.getValue());
                            list.repaint();
                            saveThemeColors(bitwig_path.replace("bitwig.jar", "theme.bte"));
                            undoRedoManager.printHistory();
                        }
                    }
                    undoButton1.setEnabled(undoRedoManager.canUndo());
                    undoButton2.setEnabled(undoRedoManager.canUndo());
                    redoButton1.setEnabled(false);
                    redoButton2.setEnabled(false);
                }
            });

            JButton undoButton = (list == list1) ? undoButton1 : undoButton2;
            undoButton.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if(!undoButton.isEnabled()) return;
                    ColorAction action = undoRedoManager.undo();
                    if (action != null) {
                        for (int i = 0; i < listModel.size(); i++) {
                            ColorPanel item = listModel.getElementAt(i);
                            if (item.getKey().equals(action.getColorName())) {
                                item.setReverted();
                                item.getColorDisplayPanel().setBackground(ColorPanel.decodeRGBA(action.getOldValue()));
                                item.setValue(action.getOldValue());
                                item.setToolTipText(item.getKey() + ": " + item.getValue());
                                updateModifiedList(item);
                                list.repaint();
                                saveThemeColors(bitwig_path.replace("bitwig.jar", "theme.bte"));
                                undoRedoManager.printHistory();
                                break;
                            }
                        }
                    }
                    undoButton1.setEnabled(undoRedoManager.canUndo());
                    undoButton2.setEnabled(undoRedoManager.canUndo());
                    redoButton1.setEnabled(undoRedoManager.canRedo());
                    redoButton2.setEnabled(undoRedoManager.canRedo());
                }
            });

            JButton redoButton = (list == list1) ? redoButton1 : redoButton2;
            redoButton.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if(!redoButton.isEnabled()) return;
                    ColorAction action = undoRedoManager.redo();
                    if (action != null) {
                        for (int i = 0; i < listModel.size(); i++) {
                            ColorPanel item = listModel.getElementAt(i);
                            if (item.getKey().equals(action.getColorName())) {
                                item.setReverted();
                                item.getColorDisplayPanel().setBackground(ColorPanel.decodeRGBA(action.getNewValue()));
                                item.setValue(action.getNewValue());
                                item.setToolTipText(item.getKey() + ": " + item.getValue());
                                updateModifiedList(item);
                                list.repaint();
                                saveThemeColors(bitwig_path.replace("bitwig.jar", "theme.bte"));
                                undoRedoManager.printHistory();
                                break;
                            }
                        }
                    }
                    undoButton1.setEnabled(undoRedoManager.canUndo());
                    undoButton2.setEnabled(undoRedoManager.canUndo());
                    redoButton1.setEnabled(undoRedoManager.canRedo());
                    redoButton2.setEnabled(undoRedoManager.canRedo());
                }
            });

            JTextField textField = (list == list1) ? textField1 : textField2;
            textField.getDocument().addDocumentListener(new DocumentListener() {
                private void search() {
                    String searchText = textField.getText().toLowerCase();
                    for (int i = 0; i < listModel.size(); i++) {
                        ColorPanel item = listModel.getElementAt(i);
                        if (item.getKey().toLowerCase().contains(searchText)) {
                            list.setSelectedIndex(i);
                            list.ensureIndexIsVisible(i);
                            return;
                        }
                    }
                    list.clearSelection();
                }

                public void insertUpdate(DocumentEvent e) {
                    search();
                }

                public void removeUpdate(DocumentEvent e) {
                    search();
                }

                public void changedUpdate(DocumentEvent e) {
                    search();
                }
            });
        }

        disableGradientCheckBox.addActionListener(e -> {
            boolean isSelected = disableGradientCheckBox.isSelected();
            System.out.println("-> Disable gradient: " + isSelected);
            saveThemeColors(bitwig_path.replace("bitwig.jar", "theme.bte"));
        });

        alwaysOnTopCheckbox.addActionListener(e -> {
            boolean isSelected = alwaysOnTopCheckbox.isSelected();
            setAlwaysOnTop(isSelected);
            System.out.println("-> Always on top: " + isSelected);
        });

        buyMeACoffeeLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        buyMeACoffeeLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                openWebpage("https://buymeacoffee.com/verdant");
            }
        });

        ethereumLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        ethereumLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showCryptoDialog();
            }
        });
    }

    private void openWebpage(String uri) {
        try {
            Desktop.getDesktop().browse(new URI(uri));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean checkThemeFile() {
        // Check if theme file named "default.bte" exists in the same directory as bitwig.jar
        // If not, warn the user that the theme file is missing and create one with default values
        // And wait till the file is created

        String themeFilePath = bitwig_path.replace("bitwig.jar", "default.bte");
        System.out.println("Default theme path: " + themeFilePath);
        File themeFile = new File(themeFilePath);
        boolean outOfLoop = true;
        while (!themeFile.exists()) {
            outOfLoop = false;
            int response = JOptionPane.showConfirmDialog(this, "Default theme color values (default.bte) is not initialized! Please run your patched Bitwig Studio installation to generate it, then press OK.\n\nIf this step fails, try running Bitwig Studio as admin/root.", "'default.bte' Missing", JOptionPane.OK_CANCEL_OPTION);
            if (response == JOptionPane.CANCEL_OPTION) {
                setVisible(false);
                Welcome.selectJar(this);
                dispose();
                return false;
            }
        }

        if (!outOfLoop) {
            JOptionPane.showMessageDialog(this, "'default.bte' file detected! You can now edit the theme colors.", "File Detected", JOptionPane.INFORMATION_MESSAGE);
        }

        return true;
    }

    private void loadDefaultThemeColors() {
        String themeFilePath = bitwig_path.replace("bitwig.jar", "default.bte");
        File themeFile = new File(themeFilePath);

        if (!themeFile.exists()) {
            JOptionPane.showMessageDialog(this, "Theme file 'default.bte' not found!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        DefaultListModel<ColorPanel> listModel = (DefaultListModel<ColorPanel>) list1.getModel();
        DefaultListModel<ColorPanel> listModel2 = (DefaultListModel<ColorPanel>) list2.getModel();
        listModel.clear();
        listModel2.clear();

        // Read the theme file line by line, separate lines with ": " to get key and value
        // For each line, create a ColorPanel item and add it to the list1
        // Example line: background: #1e1e1e
        try (Scanner scanner = new Scanner(themeFile)) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();

                // Check if Bitwig Studio x.x version is bigger than 6.0
                // The line is "// Default color values for Bitwig Studio 6.0 Beta 2"
                if (line.startsWith("// Default color values for Bitwig Studio")) {
                    String version = line.replace("// Default color values for Bitwig Studio", "").trim();
                    // Check if version is bigger than 6.0
                    if (version.compareTo("6.0") >= 0) {
                        // Version is 6.0 or bigger
                        isBitwig6OrNewer = true;
                    }
                    continue;
                }


                String[] parts = line.split(": ");
                if (parts.length == 2) {
                    if (isBitwig6OrNewer) {
                        // Skip buggy colors for Bitwig 6.0 or newer
                        if (
                                parts[0].equals("Clip Automation Button Color") ||
                                        parts[0].equals("Clip Content Automation Stroke Color") ||
                                        parts[0].equals("Clip Content Automation Fill Color") ||
                                        parts[0].equals("Clip Expression Background Color")
                        ) {
                            System.out.println("-> Skipping buggy color for Bitwig >=6.0: " + parts[0]);
                            continue;
                        }
                    }
                    if (parts[0].equals("Gradient")) {
                        disableGradientCheckBox.setSelected(parts[1].trim().equals("false"));
                        continue;
                    }
                    ColorPanel item;
                    try {
                        item = new ColorPanel(parts[0].trim(), parts[1].trim());
                    } catch (Exception e) {
                        System.out.println("-> Skipping invalid line in theme file: " + line);
                        continue;
                    }
                    listModel.addElement(item);
                }
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }

        colorsSizeLabel.setText("Colors: " + listModel.size());
        modifiedSizeLabel.setText("Modified Colors: " + listModel2.size());
    }

    private void loadThemeColors(String themeFilePath) {
        // Similar to loadDefaultThemeColors but loads from a user-selected theme file
        File themeFile = new File(themeFilePath);
        if (!themeFile.exists()) {
            // Create a new theme file with default values
            try {
                themeFile.createNewFile();
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Failed to create new theme file!", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        DefaultListModel<ColorPanel> listModel = (DefaultListModel<ColorPanel>) list1.getModel();

        try (Scanner scanner = new Scanner(themeFile)) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                String[] parts = line.split(": ");
                if (parts.length == 2) {
                    if (parts[0].equals("Gradient")) {
                        disableGradientCheckBox.setSelected(parts[1].trim().equals("false"));
                        continue;
                    }
                    for (Object _panel : listModel.toArray()) {
                        if (!(_panel instanceof ColorPanel)) continue;
                        ColorPanel panel = (ColorPanel) _panel;
                        String colorKey = compatibilityForOldThemesCheckBox.isSelected() ? matchColorNameToBitwig6(parts[0].trim()) : parts[0].trim();
                        if (panel.getKey().equals(colorKey)) {
                            try {
                                panel.setValue(parts[1].trim());
                                updateModifiedList(panel);
                            } catch (Exception e) {
                                System.out.println("-> Skipping invalid line in theme file: " + line);
                                continue;
                            }
                            panel.getColorDisplayPanel().setBackground(ColorPanel.decodeRGBA(parts[1].trim()));
                            panel.setToolTipText(panel.getKey() + ": " + panel.getValue());
                        }
                    }
                    list1.repaint();
                }
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private void loadThemeColorsFromJSON(String themeFilePath) {
        // Similar to loadDefaultThemeColors but loads from a user-selected theme file
        File themeFile = new File(themeFilePath);
        if (!themeFile.exists()) {
            // Create a new theme file with default values
            try {
                themeFile.createNewFile();
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Failed to create new theme file!", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        DefaultListModel<ColorPanel> listModel = (DefaultListModel<ColorPanel>) list1.getModel();

        try (Scanner scanner = new Scanner(themeFile)) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine()
                        .trim() // Eliminate JSON indentation spaces
                        .replace("\",", "") // Eliminate trailing commas
                        .replace("\"", ""); // Eliminate quotes
                String[] parts = line.split(": ");
                if (parts.length == 2) {
                    if (parts[0].equals("Gradient")) {
                        disableGradientCheckBox.setSelected(parts[1].trim().equals("false"));
                        continue;
                    }
                    for (Object _panel : listModel.toArray()) {
                        if (!(_panel instanceof ColorPanel)) continue;
                        ColorPanel panel = (ColorPanel) _panel;
                        String colorKey = compatibilityForOldThemesCheckBox.isSelected() ? matchColorNameToBitwig6(parts[0].trim()) : parts[0].trim();
                        if (panel.getKey().equals(colorKey)) {
                            try {
                                panel.setValue(parts[1].trim());
                                updateModifiedList(panel);
                            } catch (Exception e) {
                                System.out.println("-> Skipping invalid line in theme file: " + line);
                                continue;
                            }
                            panel.getColorDisplayPanel().setBackground(ColorPanel.decodeRGBA(parts[1].trim()));
                            panel.setToolTipText(panel.getKey() + ": " + panel.getValue());
                        }
                    }
                    list1.repaint();
                }
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private void updateModifiedList(ColorPanel panel) {
        DefaultListModel<ColorPanel> listModel2 = (DefaultListModel<ColorPanel>) list2.getModel();
        if (!panel.isModified()) {
            // If the color is not modified, remove it from modified list
            for (int i = 0; i < listModel2.size(); i++) {
                ColorPanel item = listModel2.getElementAt(i);
                if (item.getKey().equals(panel.getKey())) {
                    listModel2.removeElementAt(i);
                    break;
                }
            }
        } else {
            // If the color is modified, add it to modified list if not already present
            boolean found = false;
            for (int i = 0; i < listModel2.size(); i++) {
                ColorPanel item = listModel2.getElementAt(i);
                if (item.getKey().equals(panel.getKey())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                listModel2.addElement(panel);
            }
        }
        modifiedSizeLabel.setText("Modified Colors: " + listModel2.size());
        list2.repaint();
    }

    private void saveThemeColors(String themeFilePath) {
        // Save current colors to the specified theme file
        File themeFile = new File(themeFilePath);

        // Clear the file content
        if (themeFile.exists()) {
            try (PrintWriter writer = new PrintWriter(themeFile)) {
                writer.print("");
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Failed to clear theme file!", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        try (PrintWriter writer = new PrintWriter(themeFile)) {
            DefaultListModel<ColorPanel> listModel = (DefaultListModel<ColorPanel>) list1.getModel();
            DefaultListModel<ColorPanel> listModel2 = (DefaultListModel<ColorPanel>) list2.getModel();
            writer.println("// Theme file generated by Bitwig Theme Editor " + Main.version);
            writer.println("// Bitwig Studio version: " + Main.bitwigVersion);
            writer.println("Gradient: " + (!disableGradientCheckBox.isSelected()));
            for (Object _panel : minimizeThemeOnExportCheckBox.isSelected() ? listModel2.toArray() : listModel.toArray()) {
                if (!(_panel instanceof ColorPanel)) continue;
                ColorPanel panel = (ColorPanel) _panel;
                writer.println(panel.getKey() + ": " + panel.getValue());
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Failed to save theme file!", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String matchColorNameToBitwig6(String oldName) {
        // Map old color names to new ones for Bitwig 6.0 compatibility
        switch (oldName) {
            case "On":
                return "Accent (default)";
            case "Hitech on":
                return "Accent (hitech)";
            case "Hole (dark)":
                return "Grey 0";
            case "Dark Timeline Background":
                return "Grey 1";
            case "Light Timeline Background":
                return "Grey 2";
            case "Hole (medium)": // and Hole (light)
                return "Grey 3";
            case "Panel body": // and Window Background
                return "Grey 5";
            case "Selected Panel body": // and Window Background
                return "Grey 6";
            // TODO: More to add...
            default:
                return oldName;
        }

    }

    private void showAboutDialog() {
        String version = Main.version;
        String aboutText = format(
                "<html>" +
                        "<div style='text-align: center;padding: 10px;'>" +
                        "<h2>Bitwig Theme Editor</h2>" +
                        "<p><b>Version:</b> %s</p>" +
                        "<p><b>Author:</b> Berikai</p>" +
                        "<p><b>GitHub:</b> <a href='https://github.com/berikai/bitwig-theme-editor'>https://github.com/berikai/bitwig-theme-editor</a></p>" +
                        "<br>" +
                        "<p>A cross-platform theme editor for Bitwig Studio!</p>" +
                        "<br>" +
                        "<p>This is an unofficial free open source 3rd party tool, made with educational purposes only.</p>" +
                        "<p>Author is not responsible for any damage caused by using this tool.</p>" +
                        "<p>Use at your own risk.</p>" +
                        "</div></html>",
                version
        );

        JOptionPane.showMessageDialog(this, aboutText, "About", JOptionPane.PLAIN_MESSAGE);
    }

    private void showCryptoDialog() {
        String ETH_ADDRESS = "0x3aCdA83c0EAD65033cD532357De3c8B71b1C94d5";

        JLabel titleLabel = new JLabel("<html><div style='text-align:center;width:260px;'>Support Development ❤️</div></html>", SwingConstants.CENTER);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));

        JLabel descLabel = new JLabel(
                "<html><div style='text-align:center;width:260px;'>If you’d like to support future development, "
                        + "you can donate Ethereum (ETH) using the address below:</div></html>",
                SwingConstants.CENTER);

        JLabel addressLabel = new JLabel("<html><b>" + ETH_ADDRESS + "</b></html>", SwingConstants.CENTER);
        addressLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        addressLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addressLabel.setToolTipText("Click to copy address");

        addressLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(ETH_ADDRESS), null);
                JOptionPane.showMessageDialog(Editor.this, "Address copied to clipboard!", "Copied",
                        JOptionPane.INFORMATION_MESSAGE);
            }
        });

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(Box.createVerticalStrut(10));
        panel.add(titleLabel);
        panel.add(Box.createVerticalStrut(10));
        panel.add(descLabel);
        panel.add(Box.createVerticalStrut(10));
        panel.add(addressLabel);
        panel.add(Box.createVerticalStrut(10));

        JOptionPane.showMessageDialog(this, panel, "Donate ETH", JOptionPane.PLAIN_MESSAGE);
    }

    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        mainPanel = new JPanel();
        mainPanel.setLayout(new GridLayoutManager(3, 1, new Insets(1, 10, 10, 10), -1, -1));
        tabbedPane1 = new JTabbedPane();
        mainPanel.add(tabbedPane1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, new Dimension(200, 200), null, 0, false));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(3, 1, new Insets(5, 5, 5, 5), -1, -1));
        tabbedPane1.addTab("Colors", panel1);
        final JScrollPane scrollPane1 = new JScrollPane();
        panel1.add(scrollPane1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        list1 = new JList();
        list1.setLayoutOrientation(0);
        final DefaultListModel defaultListModel1 = new DefaultListModel();
        list1.setModel(defaultListModel1);
        scrollPane1.setViewportView(list1);
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        panel1.add(panel2, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        colorsSizeLabel = new JLabel();
        Font colorsSizeLabelFont = this.$$$getFont$$$(null, -1, 9, colorsSizeLabel.getFont());
        if (colorsSizeLabelFont != null) colorsSizeLabel.setFont(colorsSizeLabelFont);
        colorsSizeLabel.setText("Colors: 0");
        panel2.add(colorsSizeLabel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        panel2.add(spacer1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JToolBar toolBar1 = new JToolBar();
        toolBar1.setBackground(new Color(-13619152));
        toolBar1.setRollover(true);
        toolBar1.putClientProperty("JToolBar.isRollover", Boolean.TRUE);
        panel1.add(toolBar1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(-1, 20), null, 0, false));
        textField1 = new JTextField();
        textField1.setText("");
        textField1.setToolTipText("Search color");
        toolBar1.add(textField1);
        undoButton1 = new JButton();
        undoButton1.setBackground(new Color(-12566464));
        undoButton1.setEnabled(false);
        Font undoButton1Font = this.$$$getFont$$$(null, Font.BOLD, -1, undoButton1.getFont());
        if (undoButton1Font != null) undoButton1.setFont(undoButton1Font);
        undoButton1.setText("↩");
        undoButton1.setToolTipText("Undo");
        toolBar1.add(undoButton1);
        redoButton1 = new JButton();
        redoButton1.setBackground(new Color(-12566464));
        redoButton1.setEnabled(false);
        Font redoButton1Font = this.$$$getFont$$$(null, Font.BOLD, -1, redoButton1.getFont());
        if (redoButton1Font != null) redoButton1.setFont(redoButton1Font);
        redoButton1.setText("↪");
        redoButton1.setToolTipText("Redo");
        toolBar1.add(redoButton1);
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayoutManager(3, 1, new Insets(5, 5, 5, 5), -1, -1));
        tabbedPane1.addTab("Modified", panel3);
        final JScrollPane scrollPane2 = new JScrollPane();
        panel3.add(scrollPane2, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        list2 = new JList();
        list2.setLayoutOrientation(0);
        final DefaultListModel defaultListModel2 = new DefaultListModel();
        list2.setModel(defaultListModel2);
        scrollPane2.setViewportView(list2);
        final JPanel panel4 = new JPanel();
        panel4.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        panel3.add(panel4, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        modifiedSizeLabel = new JLabel();
        Font modifiedSizeLabelFont = this.$$$getFont$$$(null, -1, 9, modifiedSizeLabel.getFont());
        if (modifiedSizeLabelFont != null) modifiedSizeLabel.setFont(modifiedSizeLabelFont);
        modifiedSizeLabel.setText("Modified Colors: 0");
        panel4.add(modifiedSizeLabel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer2 = new Spacer();
        panel4.add(spacer2, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JToolBar toolBar2 = new JToolBar();
        toolBar2.setBackground(new Color(-13619152));
        toolBar2.setRollover(true);
        toolBar2.putClientProperty("JToolBar.isRollover", Boolean.TRUE);
        panel3.add(toolBar2, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(-1, 20), null, 0, false));
        textField2 = new JTextField();
        textField2.setToolTipText("Search modified color");
        toolBar2.add(textField2);
        undoButton2 = new JButton();
        undoButton2.setBackground(new Color(-12566464));
        undoButton2.setEnabled(false);
        Font undoButton2Font = this.$$$getFont$$$(null, Font.BOLD, -1, undoButton2.getFont());
        if (undoButton2Font != null) undoButton2.setFont(undoButton2Font);
        undoButton2.setText("↩");
        undoButton2.setToolTipText("Undo");
        toolBar2.add(undoButton2);
        redoButton2 = new JButton();
        redoButton2.setBackground(new Color(-12566464));
        redoButton2.setEnabled(false);
        Font redoButton2Font = this.$$$getFont$$$(null, Font.BOLD, -1, redoButton2.getFont());
        if (redoButton2Font != null) redoButton2.setFont(redoButton2Font);
        redoButton2.setText("↪");
        redoButton2.setToolTipText("Redo");
        toolBar2.add(redoButton2);
        final JPanel panel5 = new JPanel();
        panel5.setLayout(new GridLayoutManager(10, 2, new Insets(10, 10, 10, 10), -1, -1));
        tabbedPane1.addTab("Options", panel5);
        disableGradientCheckBox = new JCheckBox();
        disableGradientCheckBox.setText("Disable gradient");
        panel5.add(disableGradientCheckBox, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer3 = new Spacer();
        panel5.add(spacer3, new GridConstraints(9, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        alwaysOnTopCheckbox = new JCheckBox();
        alwaysOnTopCheckbox.setSelected(true);
        alwaysOnTopCheckbox.setText("Set always on top");
        panel5.add(alwaysOnTopCheckbox, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        Font label1Font = this.$$$getFont$$$(null, Font.BOLD, -1, label1.getFont());
        if (label1Font != null) label1.setFont(label1Font);
        label1.setText("Theme");
        panel5.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label2 = new JLabel();
        Font label2Font = this.$$$getFont$$$(null, Font.BOLD, -1, label2.getFont());
        if (label2Font != null) label2.setFont(label2Font);
        label2.setText("Editor");
        panel5.add(label2, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer4 = new Spacer();
        panel5.add(spacer4, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        minimizeThemeOnExportCheckBox = new JCheckBox();
        minimizeThemeOnExportCheckBox.setSelected(false);
        minimizeThemeOnExportCheckBox.setText("Minimize theme on export");
        panel5.add(minimizeThemeOnExportCheckBox, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label3 = new JLabel();
        Font label3Font = this.$$$getFont$$$(null, -1, 10, label3.getFont());
        if (label3Font != null) label3.setFont(label3Font);
        label3.setForeground(new Color(-9737874));
        label3.setText("More performant, but less compatible across Bitwig Studio versions.");
        panel5.add(label3, new GridConstraints(6, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 3, false));
        compatibilityForOldThemesCheckBox = new JCheckBox();
        compatibilityForOldThemesCheckBox.setText("Compatibility for old themes when importing on 6.x");
        panel5.add(compatibilityForOldThemesCheckBox, new GridConstraints(7, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label4 = new JLabel();
        Font label4Font = this.$$$getFont$$$(null, -1, 10, label4.getFont());
        if (label4Font != null) label4.setFont(label4Font);
        label4.setForeground(new Color(-9737874));
        label4.setText("Match old color value names to work with Bitwig Studio 6.x, while importing.");
        panel5.add(label4, new GridConstraints(8, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 3, false));
        final JLabel label5 = new JLabel();
        Font label5Font = this.$$$getFont$$$(null, -1, 10, label5.getFont());
        if (label5Font != null) label5.setFont(label5Font);
        label5.setForeground(new Color(-9737874));
        label5.setText("Make the UI always on top of all windows.");
        panel5.add(label5, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 3, false));
        final JPanel panel6 = new JPanel();
        panel6.setLayout(new GridLayoutManager(1, 5, new Insets(5, 0, 5, 0), -1, -1));
        mainPanel.add(panel6, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label6 = new JLabel();
        Font label6Font = this.$$$getFont$$$(null, Font.BOLD, -1, label6.getFont());
        if (label6Font != null) label6.setFont(label6Font);
        label6.setText("❤ Support me: ");
        panel6.add(label6, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        buyMeACoffeeLabel = new JLabel();
        buyMeACoffeeLabel.setForeground(new Color(-1134281));
        buyMeACoffeeLabel.setText("Buy Me A Coffee");
        panel6.add(buyMeACoffeeLabel, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        ethereumLabel = new JLabel();
        ethereumLabel.setForeground(new Color(-1134281));
        ethereumLabel.setText("Donate Ethereum");
        panel6.add(ethereumLabel, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer5 = new Spacer();
        panel6.add(spacer5, new GridConstraints(0, 4, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final Spacer spacer6 = new Spacer();
        panel6.add(spacer6, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JPanel panel7 = new JPanel();
        panel7.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
        mainPanel.add(panel7, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label7 = new JLabel();
        Font label7Font = this.$$$getFont$$$(null, -1, 11, label7.getFont());
        if (label7Font != null) label7.setFont(label7Font);
        label7.setText("Resize Bitwig Studio window or click on the 'Dashboard Button' to render changes.");
        panel7.add(label7, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer7 = new Spacer();
        panel7.add(spacer7, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final Spacer spacer8 = new Spacer();
        panel7.add(spacer8, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    private Font $$$getFont$$$(String fontName, int style, int size, Font currentFont) {
        if (currentFont == null) return null;
        String resultName;
        if (fontName == null) {
            resultName = currentFont.getName();
        } else {
            Font testFont = new Font(fontName, Font.PLAIN, 10);
            if (testFont.canDisplay('a') && testFont.canDisplay('1')) {
                resultName = fontName;
            } else {
                resultName = currentFont.getName();
            }
        }
        Font font = new Font(resultName, style >= 0 ? style : currentFont.getStyle(), size >= 0 ? size : currentFont.getSize());
        boolean isMac = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH).startsWith("mac");
        Font fontWithFallback = isMac ? new Font(font.getFamily(), font.getStyle(), font.getSize()) : new StyleContext().getFont(font.getFamily(), font.getStyle(), font.getSize());
        return fontWithFallback instanceof FontUIResource ? fontWithFallback : new FontUIResource(fontWithFallback);
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return mainPanel;
    }

}
