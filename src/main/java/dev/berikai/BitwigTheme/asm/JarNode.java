package dev.berikai.BitwigTheme.asm;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.util.CheckClassAdapter;

public class JarNode {
    // Path to input jar file
    private final String inputPath;

    // ZipFile object for input jar
    private final ZipFile inputFile;

    // All class nodes in jar, mapped by their names
    private final HashMap<String, ClassNode> nodes = new HashMap<>();

    // All files in jar, mapped by their names
    private final HashMap<String, byte[]> files = new HashMap<>();

    // All modified class nodes, mapped by their names
    private final static HashMap<String, ClassNode> modifiedNodes = new HashMap<>();

    // Class names that should be computed with COMPUTE_MAXS instead of COMPUTE_FRAMES
    // This is needed for some classes that have unresolved stack map frames by ASM, which causes issues with COMPUTE_FRAMES
    // It's usually a problem with obfuscated classes that have complex control flow
    private final static ArrayList<String> computeMaxNodes = new ArrayList<>();

    public JarNode(String path) throws IOException {
        inputPath = path;
        inputFile = new ZipFile(path);

        // Read all entries in jar
        readEntries();
    }

    // Utility method to read all bytes from an InputStream
    // Java 9+ has InputStream.readAllBytes(), but we want to support Java 8
    private static byte[] readAllBytesFromInputStream(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[1024];
        int bytesRead;
        while ((bytesRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, bytesRead);
        }
        return buffer.toByteArray();
    }

    // Read all entries in jar file
    private void readEntries() throws IOException {
        // Iterate through all entries in jar
        Enumeration<? extends ZipEntry> entries = inputFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            byte[] stream = readAllBytesFromInputStream(inputFile.getInputStream(entry));

            // Separate class files and other files, parse class files with ASM
            if (entry.getName().endsWith(".class")) {
                ClassReader classReader = new ClassReader(stream);
                ClassNode classNode = new ClassNode();
                classReader.accept(classNode, ClassReader.EXPAND_FRAMES);
                nodes.put(classNode.name, classNode);
            }
            files.put(entry.getName(), stream);
        }
    }

    // If no argument is given, export modified jar to the same path as input jar, overwriting it
    public void export() throws IOException {
        export(inputPath, true);
    }

    // Export modified jar to specified path, with optional backup
    public void export(String path) throws IOException {
        export(path, true);
    }

    public void export(String path, boolean backup) throws IOException {
        String tmpPath = path + ".tmp";

        try {
            // 1. Pre-flight verification: serialize and verify all modified classes in memory first
            HashMap<String, byte[]> serializedClasses = new HashMap<>();
            for (ClassNode classNode : nodes.values()) {
                if (modifiedNodes.containsKey(classNode.name)) {
                    int flag = ClassWriter.COMPUTE_FRAMES;
                    for (String computeMaxNode : computeMaxNodes) {
                        if (classNode.name.startsWith(computeMaxNode)) {
                            flag = ClassWriter.COMPUTE_MAXS;
                            break;
                        }
                    }

                    ClassWriter writer = new CustomClassWriter(flag);
                    classNode.accept(writer);
                    byte[] classBytes = writer.toByteArray();

                    // Run in-memory structural verification
                    try {
                        ClassReader cr = new ClassReader(classBytes);
                        ClassNode verifyNode = new ClassNode();
                        CheckClassAdapter cca = new CheckClassAdapter(verifyNode, false);
                        cr.accept(cca, 0);
                    } catch (Exception ve) {
                        throw new IllegalStateException("Bytecode verification failed for class: " + classNode.name + ". Export aborted to prevent corrupting JAR.", ve);
                    }

                    serializedClasses.put(classNode.name, classBytes);
                }
            }

            System.out.println(" -> Verified bytecode integrity for " + serializedClasses.size() + " modified classes.");

            // 2. Write to temporary JAR file
            try (ZipOutputStream outputStream = new ZipOutputStream(new FileOutputStream(tmpPath))) {
                // Write modified classes
                for (Map.Entry<String, byte[]> entry : serializedClasses.entrySet()) {
                    outputStream.putNextEntry(new ZipEntry(entry.getKey() + ".class"));
                    outputStream.write(entry.getValue());
                    outputStream.closeEntry();
                }

                // Write all unmodified files from original JAR
                outer: for (Map.Entry<String, byte[]> entry : files.entrySet()) {
                    for (String className : modifiedNodes.keySet()) {
                        if (entry.getKey().equals(className + ".class")) continue outer;
                    }
                    outputStream.putNextEntry(new ZipEntry(entry.getKey()));
                    outputStream.write(entry.getValue());
                    outputStream.closeEntry();
                }
            }

            // 3. Verify temporary JAR archive integrity
            try (ZipFile testZip = new ZipFile(tmpPath)) {
                if (testZip.size() == 0) {
                    throw new IOException("Exported temporary JAR is empty or corrupt.");
                }
            }

            // 4. Create backup of original JAR if requested
            if (backup && Files.exists(Paths.get(path))) {
                Files.copy(Paths.get(path), Paths.get(path + ".bak"), StandardCopyOption.REPLACE_EXISTING);
            }

            // 5. Atomically replace original JAR with verified temporary JAR
            try {
                Files.move(Paths.get(tmpPath), Paths.get(path), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                // Fallback to standard replace if filesystem does not support atomic move
                Files.move(Paths.get(tmpPath), Paths.get(path), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            // Clean up temporary file if it remains
            try {
                Files.deleteIfExists(Paths.get(tmpPath));
            } catch (Exception ignored) {}
        }
    }

    // Getters for fields
    public ZipFile getFile() {
        return inputFile;
    }

    public String getPath() {
        return inputPath;
    }

    public HashMap<String, ClassNode> getNodes() {
        return nodes;
    }

    public HashMap<String, byte[]> getFiles() {
        return files;
    }

    public static HashMap<String, ClassNode> getModifiedNodes() {
        return modifiedNodes;
    }

    public static ArrayList<String> getComputeMaxNodes() {
        return computeMaxNodes;
    }
}

