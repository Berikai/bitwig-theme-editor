package dev.berikai.BitwigTheme.asm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JarNodeTest {

    @TempDir
    Path temp;

    @Test
    void closesInputZipFileAfterReadingAndExportsWithBackup() throws IOException {
        Path jarPath = temp.resolve("bitwig.jar");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(jarPath.toFile()))) {
            out.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            out.write("Manifest-Version: 1.0\n".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }

        JarNode.getModifiedNodes().clear();
        JarNode.getComputeMaxNodes().clear();
        JarNode jar = new JarNode(jarPath.toString());

        // ZipFile.entries() throws IllegalStateException once the underlying ZipFile is closed.
        assertThrows(IllegalStateException.class, () -> jar.getFile().entries());

        jar.export(jarPath.toString(), true);

        Path bakPath = Paths.get(jarPath + ".bak");
        assertTrue(Files.exists(jarPath));
        assertTrue(Files.exists(bakPath));
        try (ZipFile exported = new ZipFile(jarPath.toFile())) {
            assertEquals(1, exported.size());
        }
    }
}
