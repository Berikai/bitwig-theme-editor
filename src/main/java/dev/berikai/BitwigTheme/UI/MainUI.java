package dev.berikai.BitwigTheme.UI;

import dev.berikai.BitwigTheme.Main;
import dev.berikai.BitwigTheme.asm.JarNode;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.IOException;

public class MainUI extends JFrame {
    public MainUI() throws IOException {
        setTitle("Select Operation");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(250, 75);
        setResizable(false);
        setLayout(new GridLayout(1, 2));

        JarChooser jarChooser = new JarChooser();

        JarNode jar = new JarNode(jarChooser.getSelectedFile().getPath());

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 7));

        JButton change = new JButton("Change Theme");
        change.setPreferredSize(new Dimension(225, 26));
        change.addActionListener(e -> {
            ThemeChooser themeChooser = new ThemeChooser("Open");
            try {
                final int result = Main.applyTheme(jar.getPath(), themeChooser.getSelectedFile().getPath(), jar);
                if (result == 0) {
                    JOptionPane.showMessageDialog(null,
                            "Theme " + themeChooser.getSelectedFile().getPath() + " does not exist, could not be accessed, or is corrupted.",
                            "Error!",
                            JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(null,
                            "Theme successfully applied from: " + themeChooser.getSelectedFile().getPath(),
                            "Successful!",
                            JOptionPane.INFORMATION_MESSAGE);
                }
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });

        JButton export = new JButton("Export Current Theme");
        export.setPreferredSize(new Dimension(225, 26));
        export.addActionListener(e -> {
            ThemeChooser themeChooser = new ThemeChooser("Save");
            try {
                String path = "";
                try {
                    path = themeChooser.getSelectedFile().getPath() + (themeChooser.getSelectedFile().getPath().contains(".") ? "" : "." + ((FileNameExtensionFilter) themeChooser.getFileFilter()).getExtensions()[0].toLowerCase());
                } catch (Exception ignored) {
                    JOptionPane.showMessageDialog(null, "Please use YAML or JSON file format while exporting current theme!", "ERROR!", JOptionPane.ERROR_MESSAGE);
                    System.exit(1);
                }
                Main.exportCurrentTheme(path, jar);
                JOptionPane.showMessageDialog(null, "Theme successfully exported to: " + path, "Successful!", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });

        panel.add(change);
        panel.add(export);

        add(panel);
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }
}
