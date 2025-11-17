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
        export(inputPath);
    }

    // Export modified jar to specified path
    public void export(String path) throws IOException {
        // Backup original jar file, just in case
        Files.copy(Paths.get(inputPath), Paths.get(inputPath + ".bak"), StandardCopyOption.REPLACE_EXISTING);

        // Create output ZipOutputStream
        ZipOutputStream outputStream = new ZipOutputStream(new FileOutputStream(path));

        // Iterate through all class nodes and modified nodes, write modified classes
        for (ClassNode classNode : nodes.values()) {
            for (String className : modifiedNodes.keySet()) {
                if (classNode.name.equals(className)) {
                    outputStream.putNextEntry(new ZipEntry(classNode.name + ".class"));

                    // Use COMPUTE_FRAMES by default, unless the class is in computeMaxNodes list
                    int flag = ClassWriter.COMPUTE_FRAMES;
                    for (String computeMaxNode : computeMaxNodes) {
                        if (classNode.name.startsWith(computeMaxNode)) {
                            flag = ClassWriter.COMPUTE_MAXS;
                            break;
                        }
                    }

                    // Write class to output stream
                    // We use custom ClassWriter to avoid issues with certain class structures
                    ClassWriter writer = new CustomClassWriter(flag);
                    classNode.accept(writer);
                    outputStream.write(writer.toByteArray());
                    outputStream.closeEntry();
                }
            }
        }

        // Write all other files that are not class files or modified
        outer: for (Map.Entry<String, byte[]>  entry : files.entrySet()) {
            for (String className : modifiedNodes.keySet()) {
                if (entry.getKey().equals(className + ".class")) continue outer;
            }
            outputStream.putNextEntry(new ZipEntry(entry.getKey()));
            outputStream.write(entry.getValue());
            outputStream.closeEntry();
        }
        outputStream.close();
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

