package dev.berikai.BitwigTheme.UI;

import dev.berikai.BitwigTheme.Main;
import dev.berikai.BitwigTheme.asm.JarNode;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

public class Frame extends JFrame {
    public Frame() throws IOException {
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
                Main.applyTheme(jar.getPath(), themeChooser.getSelectedFile().getPath(), jar);
                JOptionPane.showMessageDialog(null, "Theme successfully applied from: " + themeChooser.getSelectedFile().getPath(), "Successful!", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });

        JButton export = new JButton("Export Current Theme");
        export.setPreferredSize(new Dimension(225, 26));
        export.addActionListener(e -> {
            ThemeChooser themeChooser = new ThemeChooser("Save");
            try {
                Main.exportCurrentTheme(themeChooser.getSelectedFile().getPath(), jar);
                JOptionPane.showMessageDialog(null, "Theme successfully exported to: " + themeChooser.getSelectedFile().getPath(), "Successful!", JOptionPane.INFORMATION_MESSAGE);
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
