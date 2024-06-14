package dev.berikai.BitwigTheme.UI;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.filechooser.FileSystemView;

public class JarChooser extends JFileChooser {
    public JarChooser() {
        super(FileSystemView.getFileSystemView());
        FileNameExtensionFilter filter = new FileNameExtensionFilter("JAR File", "jar");
        setFileHidingEnabled(false);
        setAcceptAllFileFilterUsed(false);
        setFileFilter(filter);
        setDialogTitle("Select bitwig.jar");
        int result = showOpenDialog(null);

        if (result != JFileChooser.APPROVE_OPTION) {
            System.exit(0);
        }
    }
}