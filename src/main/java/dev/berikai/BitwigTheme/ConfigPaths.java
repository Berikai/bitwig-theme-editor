package dev.berikai.BitwigTheme;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Resolves the directory where Bitwig Theme Editor keeps its user data
 * (configuration, downloaded themes and per-Bitwig-version theme files).
 *
 * <p>Precedence:
 * <ol>
 *     <li>Explicit override ({@code --config-dir} or {@code BTE_CONFIG_DIR})</li>
 *     <li>Windows: {@code %APPDATA%\.bitwig-theme-editor}</li>
 *     <li>macOS: {@code ~/.bitwig-theme-editor} (historical location)</li>
 *     <li>Linux/Unix: {@code ~/.bitwig-theme-editor} if it already contains data,
 *     otherwise {@code $XDG_CONFIG_HOME/bitwig-theme-editor} (default {@code ~/.config/...})</li>
 * </ol>
 *
 * <p>The resolution is deliberately free of UI and environment dependencies so it can be
 * unit tested with different operating systems, home directories and environment values.
 */
public final class ConfigPaths {
    public static final String ENV_OVERRIDE = "BTE_CONFIG_DIR";
    public static final String ENV_XDG_CONFIG_HOME = "XDG_CONFIG_HOME";

    public static final String LEGACY_DIR_NAME = ".bitwig-theme-editor";
    public static final String XDG_DIR_NAME = "bitwig-theme-editor";

    // Files/directories that indicate a config directory is actually in use.
    // An empty leftover directory should not keep users on the legacy location.
    private static final String[] USER_FILES = {"bte_config.txt", "theme.bte", "default.bte"};
    private static final String[] USER_DIRS = {"themes", "versions"};

    private ConfigPaths() {
    }

    /**
     * Result of a config directory resolution.
     */
    public static final class Resolution {
        private final File directory;
        private final String origin;
        private final List<String> warnings;

        private Resolution(File directory, String origin, List<String> warnings) {
            this.directory = directory;
            this.origin = origin;
            this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
        }

        public File directory() {
            return directory;
        }

        /**
         * Short description of the rule that produced the directory, used for logging.
         */
        public String origin() {
            return origin;
        }

        /**
         * Non-fatal problems (invalid environment values, ambiguous locations, ...).
         */
        public List<String> warnings() {
            return warnings;
        }
    }

    public static Resolution resolve(String osName, String userHome, String appData,
                                     String xdgConfigHome, String override) {
        return resolve(osName, userHome, appData, xdgConfigHome, override, ENV_OVERRIDE);
    }

    public static Resolution resolve(String osName, String userHome, String appData,
                                     String xdgConfigHome, String override, String overrideOrigin) {
        List<String> warnings = new ArrayList<>();

        if (overrideOrigin == null || overrideOrigin.trim().isEmpty()) {
            overrideOrigin = ENV_OVERRIDE;
        }

        String home = trimToNull(userHome);
        if (home == null) {
            home = absolute(new File(".")).getPath();
            warnings.add("The 'user.home' system property is not set; falling back to the current directory.");
        } else {
            home = absolute(new File(home)).getPath();
        }

        // 1. Explicit override, highest priority on every OS.
        String overrideValue = trimToNull(override);
        if (overrideValue != null) {
            File overrideDir = resolveOverride(overrideValue, home, overrideOrigin, warnings);
            if (overrideDir != null) {
                return new Resolution(overrideDir, overrideOrigin, warnings);
            }
        }

        String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);

        // 2. macOS keeps its historical location. This must be checked before Windows
        //    because "darwin" also contains "win".
        if (os.contains("mac") || os.contains("darwin")) {
            return new Resolution(absolute(new File(home, LEGACY_DIR_NAME)), "macOS legacy location", warnings);
        }

        // 3. Windows uses roaming app data, falling back to the home directory.
        if (os.contains("win")) {
            String origin = "APPDATA";
            String base = trimToNull(appData);
            if (base == null) {
                warnings.add("APPDATA is not set; falling back to the user home directory.");
                base = home;
                origin = "user home (APPDATA unset)";
            } else if (!new File(base).isAbsolute()) {
                warnings.add("APPDATA is not an absolute path; falling back to the user home directory.");
                base = home;
                origin = "user home (APPDATA invalid)";
            }
            return new Resolution(absolute(new File(base, LEGACY_DIR_NAME)), origin, warnings);
        }

        // 4. Linux and other Unix-like systems follow the XDG Base Directory Specification.
        File xdgCandidate = null;
        String xdgOrigin = "default (~/.config)";
        String xdgValue = trimToNull(xdgConfigHome);
        if (xdgValue != null) {
            File xdgRoot = new File(xdgValue);
            if (!xdgRoot.isAbsolute()) {
                // The specification requires absolute paths; ignore invalid values.
                warnings.add("Ignoring XDG_CONFIG_HOME because it is not an absolute path: " + xdgValue);
            } else if (xdgRoot.exists() && !xdgRoot.isDirectory()) {
                warnings.add("Ignoring XDG_CONFIG_HOME because it is not a directory: " + xdgValue);
            } else {
                xdgCandidate = absolute(new File(xdgRoot, XDG_DIR_NAME));
                xdgOrigin = "XDG_CONFIG_HOME";
            }
        }
        if (xdgCandidate == null) {
            xdgCandidate = absolute(new File(new File(home, ".config"), XDG_DIR_NAME));
        }

        File legacy = absolute(new File(home, LEGACY_DIR_NAME));
        if (hasUserData(legacy)) {
            if (hasUserData(xdgCandidate)) {
                warnings.add("Both the legacy config directory (" + legacy + ") and the XDG config directory ("
                        + xdgCandidate + ") contain user data; using the legacy one. Set " + ENV_OVERRIDE
                        + " to choose one explicitly.");
            }
            return new Resolution(legacy, "legacy location", warnings);
        }

        return new Resolution(xdgCandidate, xdgOrigin, warnings);
    }

    /**
     * Normalizes an explicit override. Returns {@code null} when the value cannot be used,
     * in which case the caller falls back to the platform defaults.
     */
    private static File resolveOverride(String value, String home, String origin, List<String> warnings) {
        if (value.equals("~")) {
            value = home;
        } else if (value.startsWith("~/") || value.startsWith("~\\")) {
            value = new File(home, value.substring(2)).getPath();
            warnings.add("Expanded '~' in " + origin + " to " + value);
        }

        File dir = new File(value);
        if (!dir.isAbsolute()) {
            dir = new File(home, value);
            warnings.add(origin + " is not an absolute path; resolving it against the home directory: "
                    + absolute(dir));
        }
        dir = absolute(dir);

        if (dir.exists() && !dir.isDirectory()) {
            warnings.add("Ignoring " + origin + " because it is not a directory: " + dir);
            return null;
        }

        return dir;
    }

    private static boolean hasUserData(File dir) {
        if (!dir.isDirectory()) {
            return false;
        }
        for (String name : USER_FILES) {
            if (new File(dir, name).isFile()) {
                return true;
            }
        }
        for (String name : USER_DIRS) {
            if (hasEntries(new File(dir, name))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasEntries(File dir) {
        if (!dir.isDirectory()) {
            return false;
        }
        String[] entries = dir.list();
        return entries != null && entries.length > 0;
    }

    private static File absolute(File file) {
        return file.getAbsoluteFile().toPath().normalize().toFile();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
