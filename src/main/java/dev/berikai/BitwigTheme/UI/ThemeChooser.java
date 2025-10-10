package dev.berikai.BitwigTheme.UI;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.filechooser.FileSystemView;
import java.util.Objects;

public class ThemeChooser extends JFileChooser {
    public ThemeChooser(String dialog_mode) {
        super(FileSystemView.getFileSystemView());
        FileNameExtensionFilter bte_filter = new FileNameExtensionFilter("Bitwig Theme Editor Files (*.bte)", "bte");
        FileNameExtensionFilter json_filter = new FileNameExtensionFilter("JSON Files (deprecated) (*.json)", "json");
        setFileHidingEnabled(false);
        setAcceptAllFileFilterUsed(false);
        addChoosableFileFilter(bte_filter);
        if (Objects.equals(dialog_mode, "Open")) addChoosableFileFilter(json_filter); // Only allow .bte for saving
        setDialogTitle(Objects.equals(dialog_mode, "Open") ? "Select theme file" : "Export theme file");
        int result = Objects.equals(dialog_mode, "Open") ? showOpenDialog(null) : showSaveDialog(null);
    }
}