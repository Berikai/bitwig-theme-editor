package dev.berikai.BitwigTheme.asm;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.FileOutputStream;
import java.io.InputStream;
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
    private final String inputPath;
    private final ZipFile inputFile;
    private final HashMap<String, ClassNode> nodes = new HashMap<>();
    private final HashMap<String, byte[]> files = new HashMap<>();

    private final static HashMap<String, ClassNode> modifiedNodes = new HashMap<>();

    public JarNode(String path) throws IOException {
        inputPath = path;
        inputFile = new ZipFile(path);

        readEntries();
    }

    private static byte[] readAllBytesFromInputStream(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[1024];
        int bytesRead;
        while ((bytesRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, bytesRead);
        }
        return buffer.toByteArray();
    }

    private void readEntries() throws IOException {
        Enumeration<? extends ZipEntry> entries = inputFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            byte[] stream = readAllBytesFromInputStream(inputFile.getInputStream(entry));
            if (entry.getName().endsWith(".class")) {
                ClassReader classReader = new ClassReader(stream);
                ClassNode classNode = new ClassNode();
                classReader.accept(classNode, ClassReader.EXPAND_FRAMES);
                nodes.put(classNode.name, classNode);
            }
            files.put(entry.getName(), stream);
        }
    }

    public void export() throws IOException {
        export(inputPath);
    }

    public void export(String path) throws IOException {
        ZipOutputStream outputStream = new ZipOutputStream(new FileOutputStream(path));
        for (ClassNode classNode : nodes.values()) {
            for (String className : modifiedNodes.keySet()) {
                if (classNode.name.equals(className)) {
                    outputStream.putNextEntry(new ZipEntry(classNode.name + ".class"));
                    ClassWriter writer = new CustomClassWriter(ClassWriter.COMPUTE_MAXS);
                    classNode.accept(writer);
                    outputStream.write(writer.toByteArray());
                    outputStream.closeEntry();
                }
            }
        }
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
}

