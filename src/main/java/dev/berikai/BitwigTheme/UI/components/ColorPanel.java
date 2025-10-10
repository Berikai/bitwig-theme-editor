package dev.berikai.BitwigTheme.UI.components;

import javax.swing.*;
import java.awt.*;

public class ColorPanel extends JPanel {
    private String value = "#000000";
    private String key = "color_key";
    private final JPanel colorDisplayPanel;

    public ColorPanel(String key, String value) {
        this.key = key;
        this.value = value;

        this.setLayout(new FlowLayout(FlowLayout.LEFT, 12, 9));

        colorDisplayPanel = new JPanel();
        colorDisplayPanel.setBackground(decodeRGBA(value));
        colorDisplayPanel.setPreferredSize(new Dimension(30, 30));
        this.add(colorDisplayPanel);

        JLabel keyLabel = new JLabel(key);
        keyLabel.setForeground(Color.LIGHT_GRAY);
        keyLabel.setBorder(BorderFactory.createEmptyBorder(0, 7, 0, 0));
        this.add(keyLabel);

        this.setPreferredSize(new Dimension(Integer.MAX_VALUE, 52));

        this.setToolTipText(key + ": " + value);
        this.setBackground(Color.DARK_GRAY);
        this.setBorder(BorderFactory.createMatteBorder(0, 0, 4, 0, Color.decode("#202020")));
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public JPanel getColorDisplayPanel() {
        return colorDisplayPanel;
    }

    public static Color decodeRGBA(String var0) {
        if (var0 == null) throw new NumberFormatException("null string");
        String hex = var0.trim();

        if (hex.startsWith("#")) hex = hex.substring(1);
        else if (hex.startsWith("0x") || hex.startsWith("0X")) hex = hex.substring(2);

        // Expand shorthand forms (#fff, #ffff) to full length
        if (hex.length() == 3) {
            // #RGB -> #RRGGBB
            hex = "" + hex.charAt(0) + hex.charAt(0)
                    + hex.charAt(1) + hex.charAt(1)
                    + hex.charAt(2) + hex.charAt(2);
        } else if (hex.length() == 4) {
            // #RGBA -> #RRGGBBAA
            hex = "" + hex.charAt(0) + hex.charAt(0)
                    + hex.charAt(1) + hex.charAt(1)
                    + hex.charAt(2) + hex.charAt(2)
                    + hex.charAt(3) + hex.charAt(3);
        }

        long value = Long.parseLong(hex, 16);

        int r, g, b, a;

        if (hex.length() == 8) {
            // RGBA
            r = (int) ((value >> 24) & 0xFF);
            g = (int) ((value >> 16) & 0xFF);
            b = (int) ((value >> 8) & 0xFF);
            a = (int) (value & 0xFF);
        } else if (hex.length() == 6) {
            // RGB
            r = (int) ((value >> 16) & 0xFF);
            g = (int) ((value >> 8) & 0xFF);
            b = (int) (value & 0xFF);
            a = 0xFF; // fully opaque
        } else {
            throw new NumberFormatException("Invalid hex color length: " + hex.length());
        }

        return new Color(r, g, b, a);
    }


}
