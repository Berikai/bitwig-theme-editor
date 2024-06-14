package dev.berikai.BitwigTheme.UI;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.filechooser.FileSystemView;
import java.util.Objects;

public class ThemeChooser extends JFileChooser {
    public ThemeChooser(String dialog_mode) {
        super(FileSystemView.getFileSystemView());
        FileNameExtensionFilter json_filter = new FileNameExtensionFilter("JSON Files (*.json)", "json");
        FileNameExtensionFilter yaml_filter = new FileNameExtensionFilter("YAML Files (*.yaml)", "yaml");
        setFileHidingEnabled(false);
        setAcceptAllFileFilterUsed(false);
        addChoosableFileFilter(json_filter);
        addChoosableFileFilter(yaml_filter);
        setDialogTitle(Objects.equals(dialog_mode, "Open") ? "Select theme file" : "Export theme file");
        int result = Objects.equals(dialog_mode, "Open") ? showOpenDialog(null) : showSaveDialog(null);

        if (result != JFileChooser.APPROVE_OPTION) {
            System.exit(0);
        }
    }
}