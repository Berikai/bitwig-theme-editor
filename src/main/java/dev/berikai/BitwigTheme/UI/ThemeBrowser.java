package dev.berikai.BitwigTheme.UI;

import dev.berikai.BitwigTheme.Main;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ThemeBrowser extends JPanel {
    private final Editor editor;
    private final JPanel listPanel;
    private final JTextField searchField;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private static final String THEMES_URL = "https://raw.githubusercontent.com/Berikai/awesome-bitwig-themes/refs/heads/main/README.md";

    private List<ThemeEntry> allThemes = new ArrayList<>();

    public ThemeBrowser(Editor editor) {
        this.editor = editor;
        setLayout(new BorderLayout());

        // Header - JToolBar to match Editor style
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        toolBar.setBackground(new Color(48, 48, 48)); // Match the dark background from Editor.form
        toolBar.setBorder(new EmptyBorder(5, 5, 5, 5));

        searchField = new JTextField();
        searchField.putClientProperty("JTextField.placeholderText", "Search themes...");
        searchField.setToolTipText("Search by name or author");
        
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { filterThemes(); }
            @Override
            public void removeUpdate(DocumentEvent e) { filterThemes(); }
            @Override
            public void changedUpdate(DocumentEvent e) { filterThemes(); }
        });

        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> refreshThemes());
        
        toolBar.add(searchField);
        toolBar.add(Box.createHorizontalStrut(5));
        toolBar.add(refreshButton);
        add(toolBar, BorderLayout.NORTH);

        // List
        listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        JScrollPane scrollPane = new JScrollPane(listPanel);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        add(scrollPane, BorderLayout.CENTER);

        refreshThemes();
    }

    private void refreshThemes() {
        listPanel.removeAll();
        listPanel.add(new JLabel("Loading themes..."));
        listPanel.revalidate();
        listPanel.repaint();

        executor.submit(() -> {
            try {
                String markdown = fetchMarkdown(THEMES_URL);
                allThemes = parseThemes(markdown);
                
                checkInstalledStatus(allThemes);

                SwingUtilities.invokeLater(() -> {
                    searchField.setText("");
                    renderThemes(allThemes);
                });
            } catch (Exception e) {
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    listPanel.removeAll();
                    listPanel.add(new JLabel("Failed to load themes."));
                    listPanel.revalidate();
                    listPanel.repaint();
                });
            }
        });
    }

    private void filterThemes() {
        String query = searchField.getText().toLowerCase().trim();
        List<ThemeEntry> filtered = allThemes.stream()
                .filter(t -> t.name.toLowerCase().contains(query) || t.author.toLowerCase().contains(query))
                .collect(Collectors.toList());
        
        checkInstalledStatus(filtered); // Re-check status in case selection changed
        renderThemes(filtered);
    }
    
    private String fetchMarkdown(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        try (Scanner scanner = new Scanner(conn.getInputStream(), StandardCharsets.UTF_8.name())) {
            return scanner.useDelimiter("\\A").next();
        }
    }

    private List<ThemeEntry> parseThemes(String markdown) {
        List<ThemeEntry> themes = new ArrayList<>();
        String[] lines = markdown.split("\\R");
        ThemeEntry current = null;

        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("## [")) {
                if (current != null && current.isValid()) themes.add(current);
                current = new ThemeEntry();
                Pattern namePattern = Pattern.compile("## \\[(.+?)\\]\\((.+?)\\) by \\[@(.+?)\\]\\((.+?)\\)");
                Matcher m = namePattern.matcher(line);
                if (m.find()) {
                    current.name = m.group(1);
                    current.repoUrl = m.group(2);
                    current.author = m.group(3);
                }
            } else if (line.startsWith("<!-- RAW:") && current != null) {
                String raw = line.substring("<!-- RAW:".length(), line.indexOf("-->")).trim();
                if (!"[NOT_AVAILABLE]".equals(raw)) current.rawUrl = raw;
            } else if (line.startsWith("<img") && current != null) {
                Pattern imgPattern = Pattern.compile("src=\"(.+?)\"");
                Matcher m = imgPattern.matcher(line);
                if (m.find()) current.imageUrl = m.group(1);
            }
        }
        if (current != null && current.isValid()) themes.add(current);
        return themes;
    }

    private void checkInstalledStatus(List<ThemeEntry> themes) {
        File themesDir = new File(Main.configPath, "themes");
        if (!themesDir.exists()) themesDir.mkdirs();
        
        String currentPath = editor.getCurrentThemePath();

        for (ThemeEntry theme : themes) {
            if (theme.rawUrl == null) continue;
            String fileName = getFileNameFromUrl(theme.rawUrl);
            File localFile = new File(themesDir, fileName);
            theme.isInstalled = localFile.exists();
            theme.localPath = localFile.getAbsolutePath();
            
            // Check if selected
            if (currentPath != null && currentPath.equals(theme.localPath)) {
                theme.isSelected = true;
            } else {
                theme.isSelected = false;
            }
        }
    }

    private String getFileNameFromUrl(String url) {
        return url.substring(url.lastIndexOf('/') + 1);
    }

    private void renderThemes(List<ThemeEntry> themes) {
        listPanel.removeAll();
        if (themes.isEmpty()) {
             listPanel.add(new JLabel("No themes found."));
        } else {
            for (ThemeEntry theme : themes) {
                listPanel.add(createThemePanel(theme));
                listPanel.add(Box.createVerticalStrut(10));
            }
        }
        listPanel.revalidate();
        listPanel.repaint();
    }

    private JPanel createThemePanel(ThemeEntry theme) {
        JPanel panel = new JPanel(new BorderLayout(8, 0)); // Reduced gap
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY, 1),
                new EmptyBorder(5, 5, 5, 5)));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 145)); 
        panel.setAlignmentX(Component.LEFT_ALIGNMENT); // Important for BoxLayout

        // Highlighting
        if (theme.isSelected) {
            panel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Color.decode("#DAA520"), 2), // Gold border
                    new EmptyBorder(4, 4, 4, 4)));
            panel.setBackground(Color.decode("#202820")); 
        } else if (theme.isInstalled) {
             panel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Color.decode("#408040"), 1), // Green border
                    new EmptyBorder(5, 5, 5, 5)));
        }

        // Image (Left)
        JLabel imageLabel = new JLabel();
        imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        imageLabel.setPreferredSize(new Dimension(150, 100)); // Slightly narrower
        
        if (theme.imageUrl != null) {
            if (theme.cachedIcon != null) {
                imageLabel.setIcon(theme.cachedIcon);
            } else {
                imageLabel.setText("Loading...");
                executor.submit(() -> {
                    try {
                        URL url = new URL(theme.imageUrl);
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setConnectTimeout(5000);
                        conn.setReadTimeout(5000);
                        conn.setRequestMethod("GET");
                        
                        BufferedImage image = ImageIO.read(conn.getInputStream());
                        if (image != null) {
                            Image scaled = image.getScaledInstance(150, 100, Image.SCALE_SMOOTH); 
                            ImageIcon icon = new ImageIcon(scaled);
                            theme.cachedIcon = icon; // Cache results
                            SwingUtilities.invokeLater(() -> {
                                imageLabel.setText("");
                                imageLabel.setIcon(icon);
                            });
                        } else {
                            SwingUtilities.invokeLater(() -> imageLabel.setText("No Image"));
                        }
                    } catch (Exception e) {
                        SwingUtilities.invokeLater(() -> imageLabel.setText("Error"));
                    }
                });
            }
        } else {
            imageLabel.setText("No Preview");
        }
        panel.add(imageLabel, BorderLayout.WEST);

        // Info & Buttons (Right -> Center)
        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        if (theme.isSelected) infoPanel.setBackground(Color.decode("#202820"));
        
        // Push content down slightly or center vertically
        infoPanel.add(Box.createVerticalGlue());

        // Name
        JLabel nameLabel = new JLabel(theme.name);
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 14f));
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoPanel.add(nameLabel);
        
        // Author
        JLabel authorLabel = new JLabel("by " + theme.author);
        authorLabel.setFont(nameLabel.getFont().deriveFont(11f));
        authorLabel.setForeground(Color.GRAY);
        authorLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoPanel.add(authorLabel);
        
        // File Info
        String fileType = "BTE";
        if (theme.rawUrl != null && theme.rawUrl.endsWith(".json")) {
            fileType = "JSON (old)";
        }
        JLabel typeLabel = new JLabel(fileType); 
        typeLabel.setFont(typeLabel.getFont().deriveFont(10f)); // Even smaller
        typeLabel.setForeground(Color.DARK_GRAY);
        typeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoPanel.add(typeLabel);
        
        infoPanel.add(Box.createVerticalStrut(8));

        // Buttons Container (2 Rows)
        JPanel buttonContainer = new JPanel(new GridLayout(2, 1, 0, 5));
        buttonContainer.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttonContainer.setMaximumSize(new Dimension(Integer.MAX_VALUE, 65)); // 2x30 + 5 gap
        if (theme.isSelected) buttonContainer.setBackground(Color.decode("#202820"));

        // Row 1: Actions (Install / Select)
        JPanel row1 = new JPanel(new GridLayout(1, 0, 5, 0));
        if (theme.isSelected) row1.setBackground(Color.decode("#202820"));
        
        Dimension btnDim = new Dimension(0, 30); 

        if (theme.rawUrl != null) {
            String btnText = theme.isInstalled ? "Re-Install" : "Install";
            JButton installBtn = new JButton(btnText);
            installBtn.setPreferredSize(btnDim);
            installBtn.addActionListener(e -> installTheme(theme, installBtn));
            row1.add(installBtn);
            
            if (theme.isInstalled) {
                if (theme.isSelected) {
                     JButton selectedBtn = new JButton("Selected");
                     selectedBtn.setEnabled(false);
                     selectedBtn.setPreferredSize(btnDim);
                     row1.add(selectedBtn);
                } else {
                    JButton selectBtn = new JButton("Select");
                    selectBtn.setPreferredSize(btnDim);
                    selectBtn.addActionListener(e -> {
                         editor.loadTheme(theme.localPath);
                         filterThemes(); 
                    });
                    row1.add(selectBtn);
                }
            }
        }
        buttonContainer.add(row1);

        // Row 2: External (GitHub)
        JPanel row2 = new JPanel(new GridLayout(1, 0, 5, 0));
        if (theme.isSelected) row2.setBackground(Color.decode("#202820"));
        
        JButton repoBtn = new JButton("GitHub");
        repoBtn.setPreferredSize(btnDim);
        repoBtn.addActionListener(e -> openWebpage(theme.repoUrl));
        row2.add(repoBtn);
        
        buttonContainer.add(row2);

        infoPanel.add(buttonContainer);
        infoPanel.add(Box.createVerticalGlue());
        
        panel.add(infoPanel, BorderLayout.CENTER);

        return panel;
    }

    private void installTheme(ThemeEntry theme, JButton button) {
        button.setEnabled(false);
        button.setText("Installing...");
        executor.submit(() -> {
            try {
                URL url = new URL(theme.rawUrl);
                File themesDir = new File(Main.configPath, "themes");
                if (!themesDir.exists()) themesDir.mkdirs();
                String fileName = getFileNameFromUrl(theme.rawUrl);
                File targetFile = new File(themesDir, fileName);
                try (BufferedInputStream in = new BufferedInputStream(url.openStream());
                     FileOutputStream fileOutputStream = new FileOutputStream(targetFile)) {
                    byte dataBuffer[] = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                        fileOutputStream.write(dataBuffer, 0, bytesRead);
                    }
                }
                theme.isInstalled = true;
                theme.localPath = targetFile.getAbsolutePath();
                SwingUtilities.invokeLater(() -> {
                    button.setText("Re-Install");
                    button.setEnabled(true);
                    filterThemes(); // Refresh sorting
                });
            } catch (Exception e) {
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    button.setText("Failed");
                    button.setEnabled(true);
                });
            }
        });
    }

    private void openWebpage(String uri) {
        try { Desktop.getDesktop().browse(new URI(uri)); } catch (Exception e) { e.printStackTrace(); }
    }

    private static class ThemeEntry {
        String name;
        String repoUrl;
        String author;
        String rawUrl;
        String imageUrl;
        boolean isInstalled;
        boolean isSelected;
        String localPath;
        ImageIcon cachedIcon;
        boolean isValid() { return name != null && repoUrl != null; }
    }
}
