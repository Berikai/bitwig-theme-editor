package dev.berikai.BitwigTheme;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigPathsTest {

    @TempDir
    Path temp;

    @Test
    void linuxUsesXdgDefaultWhenNothingIsConfigured() throws IOException {
        File home = home("linux-fresh");

        ConfigPaths.Resolution resolution = ConfigPaths.resolve("Linux", home.getPath(), null, null, null);

        assertEquals(new File(home, ".config/bitwig-theme-editor"), resolution.directory());
        assertEquals("default (~/.config)", resolution.origin());
        assertTrue(resolution.warnings().isEmpty());
    }

    @Test
    void linuxUsesXdgConfigHomeWhenItIsAbsolute() throws IOException {
        File home = home("linux-home");
        File xdg = dir("xdg");

        ConfigPaths.Resolution resolution = ConfigPaths.resolve("Linux", home.getPath(), null, xdg.getPath(), null);

        assertEquals(new File(xdg, "bitwig-theme-editor"), resolution.directory());
        assertEquals("XDG_CONFIG_HOME", resolution.origin());
    }

    @Test
    void linuxIgnoresEmptyXdgConfigHome() throws IOException {
        File home = home("linux-home");

        for (String empty : new String[]{"", "   "}) {
            ConfigPaths.Resolution resolution = ConfigPaths.resolve("Linux", home.getPath(), null, empty, null);
            assertEquals(new File(home, ".config/bitwig-theme-editor"), resolution.directory());
        }
    }

    @Test
    void linuxIgnoresRelativeXdgConfigHome() throws IOException {
        File home = home("linux-home");

        ConfigPaths.Resolution resolution = ConfigPaths.resolve("Linux", home.getPath(), null, "relative-config", null);

        assertEquals(new File(home, ".config/bitwig-theme-editor"), resolution.directory());
        assertWarning(resolution, "not an absolute path");
    }

    @Test
    void linuxIgnoresXdgConfigHomeThatIsAFile() throws IOException {
        File home = home("linux-home");
        File file = Files.createFile(temp.resolve("xdg-file")).toFile();

        ConfigPaths.Resolution resolution = ConfigPaths.resolve("Linux", home.getPath(), null, file.getPath(), null);

        assertEquals(new File(home, ".config/bitwig-theme-editor"), resolution.directory());
        assertWarning(resolution, "not a directory");
    }

    @Test
    void linuxKeepsLegacyDirectoryWhenItContainsData() throws IOException {
        File home = home("linux-legacy");
        Files.createDirectories(home.toPath().resolve(".bitwig-theme-editor/versions/5.0"));
        File xdg = dir("xdg-legacy");

        ConfigPaths.Resolution resolution = ConfigPaths.resolve("Linux", home.getPath(), null, xdg.getPath(), null);

        assertEquals(new File(home, ".bitwig-theme-editor"), resolution.directory());
        assertEquals("legacy location", resolution.origin());
    }

    @Test
    void linuxIgnoresEmptyLegacyDirectory() throws IOException {
        File home = home("linux-empty-legacy");
        Files.createDirectories(home.toPath().resolve(".bitwig-theme-editor"));

        ConfigPaths.Resolution resolution = ConfigPaths.resolve("Linux", home.getPath(), null, null, null);

        assertEquals(new File(home, ".config/bitwig-theme-editor"), resolution.directory());
    }

    @Test
    void linuxWarnsWhenLegacyAndXdgDirectoriesBothContainData() throws IOException {
        File home = home("linux-both");
        Path legacy = home.toPath().resolve(".bitwig-theme-editor");
        Files.createDirectories(legacy);
        Files.write(legacy.resolve("bte_config.txt"), "bitwig_path=/tmp/bitwig.jar\n".getBytes(StandardCharsets.UTF_8));
        File xdg = dir("xdg-both");
        Path xdgApp = xdg.toPath().resolve("bitwig-theme-editor/themes");
        Files.createDirectories(xdgApp);
        Files.write(xdgApp.resolve("theme.bte"), "x".getBytes(StandardCharsets.UTF_8));

        ConfigPaths.Resolution resolution = ConfigPaths.resolve("Linux", home.getPath(), null, xdg.getPath(), null);

        assertEquals(new File(home, ".bitwig-theme-editor"), resolution.directory());
        assertWarning(resolution, "Both the legacy config directory");
    }

    @Test
    void explicitOverrideWinsOnEveryPlatform() throws IOException {
        File home = home("override-home");
        File appData = dir("override-appdata");
        File xdg = dir("override-xdg");
        File custom = dir("custom-config");

        ConfigPaths.Resolution resolution = ConfigPaths.resolve(
                "Windows 11", home.getPath(), appData.getPath(), xdg.getPath(), custom.getPath(), "--config-dir");

        assertEquals(custom, resolution.directory());
        assertEquals("--config-dir", resolution.origin());
    }

    @Test
    void overrideExpandsTilde() throws IOException {
        File home = home("tilde-home");

        ConfigPaths.Resolution resolution = ConfigPaths.resolve("Linux", home.getPath(), null, null, "~/custom");

        assertEquals(new File(home, "custom"), resolution.directory());
        assertWarning(resolution, "Expanded '~'");
    }

    @Test
    void overrideResolvesRelativePathAgainstHome() throws IOException {
        File home = home("relative-home");

        ConfigPaths.Resolution resolution = ConfigPaths.resolve("Linux", home.getPath(), null, null, "relative-dir");

        assertEquals(new File(home, "relative-dir"), resolution.directory());
        assertWarning(resolution, "not an absolute path");
    }

    @Test
    void overridePointingToAFileIsIgnored() throws IOException {
        File home = home("override-file-home");
        File file = Files.createFile(temp.resolve("override-file")).toFile();

        ConfigPaths.Resolution resolution = ConfigPaths.resolve("Linux", home.getPath(), null, null, file.getPath());

        assertEquals(new File(home, ".config/bitwig-theme-editor"), resolution.directory());
        assertWarning(resolution, "not a directory");
    }

    @Test
    void windowsUsesAppDataDirectory() throws IOException {
        File home = home("windows-home");
        File appData = dir("appdata");

        ConfigPaths.Resolution resolution = ConfigPaths.resolve(
                "Windows 11", home.getPath(), appData.getPath(), null, null);

        assertEquals(new File(appData, ".bitwig-theme-editor"), resolution.directory());
        assertEquals("APPDATA", resolution.origin());
    }

    @Test
    void windowsFallsBackToHomeWhenAppDataIsMissing() throws IOException {
        File home = home("windows-no-appdata");

        ConfigPaths.Resolution resolution = ConfigPaths.resolve("Windows 10", home.getPath(), null, null, null);

        assertEquals(new File(home, ".bitwig-theme-editor"), resolution.directory());
        assertWarning(resolution, "APPDATA is not set");
        assertEquals("user home (APPDATA unset)", resolution.origin());
    }

    @Test
    void windowsFallsBackToHomeWhenAppDataIsRelative() throws IOException {
        File home = home("windows-relative-appdata");

        ConfigPaths.Resolution resolution = ConfigPaths.resolve("Windows", home.getPath(), "relative-appdata", null, null);

        assertEquals(new File(home, ".bitwig-theme-editor"), resolution.directory());
        assertWarning(resolution, "not an absolute path");
        assertEquals("user home (APPDATA invalid)", resolution.origin());
    }

    @Test
    void macOsKeepsLegacyDirectoryAndIgnoresXdg() throws IOException {
        File home = home("mac-home");
        File xdg = dir("mac-xdg");

        ConfigPaths.Resolution resolution = ConfigPaths.resolve("Mac OS X", home.getPath(), null, xdg.getPath(), null);

        assertEquals(new File(home, ".bitwig-theme-editor"), resolution.directory());
        assertEquals("macOS legacy location", resolution.origin());
        assertTrue(resolution.warnings().isEmpty());
    }

    @Test
    void darwinIsNotTreatedAsWindows() throws IOException {
        File home = home("darwin-home");
        File appData = dir("darwin-appdata");

        ConfigPaths.Resolution resolution = ConfigPaths.resolve("Darwin", home.getPath(), appData.getPath(), null, null);

        assertEquals(new File(home, ".bitwig-theme-editor"), resolution.directory());
        assertEquals("macOS legacy location", resolution.origin());
    }

    private File home(String name) throws IOException {
        return dir(name);
    }

    private File dir(String name) throws IOException {
        return Files.createDirectories(temp.resolve(name)).toFile();
    }

    private static void assertWarning(ConfigPaths.Resolution resolution, String expectedFragment) {
        assertTrue(resolution.warnings().stream().anyMatch(warning -> warning.contains(expectedFragment)),
                () -> "Expected a warning containing '" + expectedFragment + "' but got " + resolution.warnings());
    }
}
