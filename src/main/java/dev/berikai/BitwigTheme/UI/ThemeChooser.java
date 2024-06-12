package dev.berikai.BitwigTheme.UI;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.filechooser.FileSystemView;
import java.util.Objects;

public class ThemeChooser extends JFileChooser {
    public ThemeChooser(String dialog_mode) {
        super(FileSystemView.getFileSystemView());
        FileNameExtensionFilter filter = new FileNameExtensionFilter("JSON File", "json");
        setFileHidingEnabled(false);
        setFileFilter(filter);
        setDialogTitle(Objects.equals(dialog_mode, "Open") ? "Select theme file" : "Export theme file");
        int result = Objects.equals(dialog_mode, "Open") ? showOpenDialog(null) : showSaveDialog(null);

        if (result != JFileChooser.APPROVE_OPTION) {
            System.exit(0);
        }
    }
}