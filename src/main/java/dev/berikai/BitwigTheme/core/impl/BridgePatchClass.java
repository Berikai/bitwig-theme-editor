package dev.berikai.BitwigTheme.core.impl;

import dev.berikai.BitwigTheme.Main;
import dev.berikai.BitwigTheme.core.PatchClass;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.HashMap;

public class BridgePatchClass extends PatchClass {
    // Note: I don't know the unobfuscated original names of code components. I'll refer our target class here as ColorBridge
    // Because it's method, **which is also what we are looking for**, is being called once for each element from UI element initialization methods to initialize related colors
    // At least that's what I interpret that is happening, it's something like that

    public BridgePatchClass(HashMap<String, ClassNode> classNodes) {
        // Call super class constructor
        super(classNodes, "ColorBridgeClass");

        // Find the class that contains the string "Same as background"
        // ColorBridge class is the only class that has this string, which makes our lives easier
        findClassNode("Same as background");

        // If classNode is null, inform the user
        if (classNode == null) {
            System.out.println("ERROR: ColorBridge class not found. Your Bitwig Studio version may not be supported.");
            System.out.println(" -> Known working versions: 4.x, 5.x, 6.x");
        }

        // Find the method node that we will be injecting code into
        findMethodNode();
    }

    // Let's find our target method, also mentioned in the above comments, to inject our bytecode
    protected void findMethodNode() {
        // Iterate through methods of class
        for (MethodNode bridgeMethod : classNode.methods) {
            // Check if method has the attribute "protected", it's last 3 arguments are floats, and it's a void function
            if ((bridgeMethod.access & Opcodes.ACC_PROTECTED) != 0 && bridgeMethod.desc.endsWith("FFF)V")) {
                // Assign found method to the methodNode field of PatchClass
                this.methodNode = bridgeMethod;

                // We don't actually need to add this method to mappings, but why not inform the user
                // It may help the folks who want to explore the bytecode later
                PatchClass.mappings.put("ColorBridgeClass.addColorToUI", this.methodNode.name);

                break;
            }
        }
    }

    // The code we will inject here to the found method is responsible for printing "default.bte: the reference color theme file"
    // It will export the default color values with their names, in this theme format
    // The file will be exported to the same directory of Bitwig Studio executable
    public void patch() {
        // Get the last return instruction of the method
        AbstractInsnNode ret = null;
        for (int i = methodNode.instructions.size() - 1; i >= 0; i--) {
            AbstractInsnNode insn = methodNode.instructions.get(i);
            int op = insn.getOpcode();
            if (op == Opcodes.RETURN) {
                ret = insn;
                break;
            }
        }

        // Create a new instruction list to write new bytecode on
        InsnList exportIl = new InsnList();
        writeBytecode(exportIl);

        // Insert new instructions before return
        methodNode.instructions.insertBefore(ret, exportIl);
    }

    // --------- Bytecode writing section ---------

    // Write bytecode to be injected to method
    private void writeBytecode(InsnList il) {
        // Get argument base index, avoid this if not static
        int mArgIndex = ((methodNode.access & Opcodes.ACC_STATIC) == 0) ? 1 : 0;

        // Get path to "default.bte" file
        // Here is the corresponding Java code:
        //        ldc Lcom/bitwig/flt/app/BitwigStudioMain;
        //        invokevirtual java/lang/Class.getProtectionDomain ()Ljava/security/ProtectionDomain;
        //        invokevirtual java/security/ProtectionDomain.getCodeSource ()Ljava/security/CodeSource;
        //        invokevirtual java/security/CodeSource.getLocation ()Ljava/net/URL;
        //        invokevirtual java/net/URL.toURI ()Ljava/net/URI;
        //        invokestatic java/nio/file/Paths.get (Ljava/net/URI;)Ljava/nio/file/Path;
        //        invokeinterface java/nio/file/Path.getParent ()Ljava/nio/file/Path;
        //        ldc "default.bte"
        //        invokeinterface java/nio/file/Path.resolve (Ljava/lang/String;)Ljava/nio/file/Path;
        //        invokeinterface java/nio/file/Path.toString ()Ljava/lang/String;
        //        astore themePath

        il.add(new LabelNode());
        il.add(new LdcInsnNode(Type.getObjectType("com/bitwig/flt/app/BitwigStudioMain")));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getProtectionDomain", "()Ljava/security/ProtectionDomain;"));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/security/ProtectionDomain", "getCodeSource", "()Ljava/security/CodeSource;"));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/security/CodeSource", "getLocation", "()Ljava/net/URL;"));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/net/URL", "toURI", "()Ljava/net/URI;"));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/nio/file/Paths", "get", "(Ljava/net/URI;)Ljava/nio/file/Path;"));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/nio/file/Path", "getParent", "()Ljava/nio/file/Path;", true));
        il.add(new LdcInsnNode("default.bte"));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/nio/file/Path", "resolve", "(Ljava/lang/String;)Ljava/nio/file/Path;", true));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/nio/file/Path", "toString", "()Ljava/lang/String;", true));
        int defaultPathSlot = methodNode.maxLocals++;
        il.add(new VarInsnNode(Opcodes.ASTORE, defaultPathSlot));

        // Get path to "theme.bte" file
        // Here is the corresponding Java code:
        //        ldc Lcom/bitwig/flt/app/BitwigStudioMain;
        //        invokevirtual java/lang/Class.getProtectionDomain ()Ljava/security/ProtectionDomain;
        //        invokevirtual java/security/ProtectionDomain.getCodeSource ()Ljava/security/CodeSource;
        //        invokevirtual java/security/CodeSource.getLocation ()Ljava/net/URL;
        //        invokevirtual java/net/URL.toURI ()Ljava/net/URI;
        //        invokestatic java/nio/file/Paths.get (Ljava/net/URI;)Ljava/nio/file/Path;
        //        invokeinterface java/nio/file/Path.getParent ()Ljava/nio/file/Path;
        //        ldc "theme.bte"
        //        invokeinterface java/nio/file/Path.resolve (Ljava/lang/String;)Ljava/nio/file/Path;
        //        invokeinterface java/nio/file/Path.toString ()Ljava/lang/String;
        //        putstatic ColorClass.themeFilePath Ljava/lang/String;

        il.add(new LabelNode());
        il.add(new LdcInsnNode(Type.getObjectType("com/bitwig/flt/app/BitwigStudioMain")));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getProtectionDomain", "()Ljava/security/ProtectionDomain;"));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/security/ProtectionDomain", "getCodeSource", "()Ljava/security/CodeSource;"));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/security/CodeSource", "getLocation", "()Ljava/net/URL;"));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/net/URL", "toURI", "()Ljava/net/URI;"));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/nio/file/Paths", "get", "(Ljava/net/URI;)Ljava/nio/file/Path;"));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/nio/file/Path", "getParent", "()Ljava/nio/file/Path;", true));
        il.add(new LdcInsnNode("theme.bte"));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/nio/file/Path", "resolve", "(Ljava/lang/String;)Ljava/nio/file/Path;", true));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/nio/file/Path", "toString", "()Ljava/lang/String;", true));
        il.add(new FieldInsnNode(Opcodes.PUTSTATIC, PatchClass.mappings.get("ColorClass"), "themeFilePath", "Ljava/lang/String;"));

        // Color class doesn't store color names originally
        // That's why we manually created them in ColorPatchClass
        // So, first of all, we need to run "colorInstance.colorName = colorName;"
        // Here is the corresponding bytecode:
        //         aload colorInstance
        //         aload colorName
        //         putfield ColorClass.colorName Ljava/lang/String;

        il.add(new LabelNode()); // We will start every part with a LabelNode (not needed, but a good practice)
        il.add(new VarInsnNode(Opcodes.ALOAD, mArgIndex + 2)); // Load colorInstance (3rd argument, index 2)
        il.add(new VarInsnNode(Opcodes.ALOAD, mArgIndex)); // Load colorName (1st argument, index 0)
        il.add(new FieldInsnNode(Opcodes.PUTFIELD, PatchClass.mappings.get("ColorClass"), "colorName", "Ljava/lang/String;"));

        // Now, we need to create a file object for "default.bte"
        // So, we need to run "File file = new File("default.bte");"
        // Here is the corresponding bytecode:
        //        new java/io/File
        //        dup
        //        ldc "default.bte"
        //        invokespecial java/io/File.<init> (Ljava/lang/String;)V
        //        astore file

        il.add(new LabelNode());
        il.add(new TypeInsnNode(Opcodes.NEW, "java/io/File"));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new VarInsnNode(Opcodes.ALOAD, defaultPathSlot));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/io/File", "<init>", "(Ljava/lang/String;)V"));
        int fileSlot = methodNode.maxLocals++; // Let's reserve space for our new variable
        il.add(new VarInsnNode(Opcodes.ASTORE, fileSlot));
        // Note: we can use maxLocals since we use ClassReader.EXPAND_FRAMES and ClassWriter.COMPUTE_MAXS in JarNode class

        // We need to initialize a try/catch block: A
        // java/io/IOException
        LabelNode tryStart_A = new LabelNode();
        LabelNode tryEnd_A = new LabelNode();
        LabelNode tryHandler_A = new LabelNode();

        // "boolean fileExists = file.exists();"
        // Here is the corresponding bytecode:
        //        aload file
        //        invokevirtual java/io/File.exists ()Z
        //        istore fileExists

        il.add(tryStart_A); // LabelNode as tryStart_A
        il.add(new VarInsnNode(Opcodes.ALOAD, fileSlot));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/io/File", "exists", "()Z"));
        int fileExistsSlot = methodNode.maxLocals++;
        il.add(new VarInsnNode(Opcodes.ISTORE, fileExistsSlot));

        // "boolean isEmpty = !fileExists || file.length() == 0L;"
        // Here is the corresponding bytecode:
        //        iload fileExists
        //        ifeq L
        //        aload file
        //        invokevirtual java/io/File.length ()J
        //        lconst_0
        //        lcmp
        //        ifne M
        //    L:
        //        iconst_1
        //        goto N
        //    M:
        //        iconst_0
        //    N:
        //        istore isEmpty
        
        LabelNode labelNode_L = new LabelNode();
        LabelNode labelNode_M = new LabelNode();
        LabelNode labelNode_N = new LabelNode();

        il.add(new LabelNode());
        il.add(new VarInsnNode(Opcodes.ILOAD, fileExistsSlot));
        il.add(new JumpInsnNode(Opcodes.IFEQ, labelNode_L));
        il.add(new VarInsnNode(Opcodes.ALOAD, fileSlot));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/io/File", "length", "()J"));
        il.add(new InsnNode(Opcodes.LCONST_0));
        il.add(new InsnNode(Opcodes.LCMP));
        il.add(new JumpInsnNode(Opcodes.IFNE, labelNode_M));
        il.add(labelNode_L);
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new JumpInsnNode(Opcodes.GOTO, labelNode_N));
        il.add(labelNode_M);
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(labelNode_N);
        int isEmptySlot = methodNode.maxLocals++;
        il.add(new VarInsnNode(Opcodes.ISTORE, isEmptySlot));

        // "boolean containsEnd = false;"
        // Here is the corresponding bytecode:
        //        iconst_0
        //        istore containsEnd

        il.add(new LabelNode());
        il.add(new InsnNode(Opcodes.ICONST_0));
        int containsEndSlot = methodNode.maxLocals++;
        il.add(new VarInsnNode(Opcodes.ISTORE, containsEndSlot));

        // "if (fileExists) {"
        // Here is the corresponding bytecode:
        //        iload fileExists
        //        ifeq AB

        LabelNode labelNode_AB = new LabelNode();

        il.add(new LabelNode());
        il.add(new VarInsnNode(Opcodes.ILOAD, fileExistsSlot));
        il.add(new JumpInsnNode(Opcodes.IFEQ, labelNode_AB));

        // "try (BufferedReader reader = new BufferedReader(new FileReader(file));) {"
        // Here is the corresponding bytecode:
        //        new java/io/BufferedReader
        //        dup
        //        new java/io/FileReader
        //        dup
        //        aload file
        //        invokespecial java/io/FileReader.<init> (Ljava/io/File;)V
        //        invokespecial java/io/BufferedReader.<init> (Ljava/io/Reader;)V
        //        astore reader

        il.add(new LabelNode());
        il.add(new TypeInsnNode(Opcodes.NEW, "java/io/BufferedReader"));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new TypeInsnNode(Opcodes.NEW, "java/io/FileReader"));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new VarInsnNode(Opcodes.ALOAD, fileSlot));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/io/FileReader", "<init>", "(Ljava/io/File;)V"));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/io/BufferedReader", "<init>", "(Ljava/io/Reader;)V"));
        int readerSlot = methodNode.maxLocals++;
        il.add(new VarInsnNode(Opcodes.ASTORE, readerSlot));

        // We need to initialize a try/catch block: B
        // java/lang/Throwable
        LabelNode tryStart_B = new LabelNode();
        LabelNode tryEnd_B = new LabelNode();
        LabelNode tryHandler_B = new LabelNode();

        // "String line; while ((line = reader.readLine()) != null) {"
        // Here is the corresponding bytecode:
        //        aload reader
        //        invokevirtual java/io/BufferedReader.readLine ()Ljava/lang/String;
        //        dup
        //        astore line
        //        ifnull tryEnd_B

        il.add(tryStart_B); // LabelNode as tryStart_B
        il.add(new VarInsnNode(Opcodes.ALOAD, readerSlot));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/io/BufferedReader", "readLine", "()Ljava/lang/String;"));
        il.add(new InsnNode(Opcodes.DUP));
        int lineSlot = methodNode.maxLocals++;
        il.add(new VarInsnNode(Opcodes.ASTORE, lineSlot));
        il.add(new JumpInsnNode(Opcodes.IFNULL, tryEnd_B));

        // "if (!line.trim().equals("// <end>")) continue;"
        // Here is the corresponding bytecode:
        //        aload line
        //        invokevirtual java/lang/String.trim ()Ljava/lang/String;
        //        ldc "// <end>"
        //        invokevirtual java/lang/String.equals (Ljava/lang/Object;)Z
        //        ifeq tryStart_B

        il.add(new LabelNode());
        il.add(new VarInsnNode(Opcodes.ALOAD, lineSlot));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "trim", "()Ljava/lang/String;"));
        il.add(new LdcInsnNode("// <end>"));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z"));
        il.add(new JumpInsnNode(Opcodes.IFEQ, tryStart_B));

        // "containsEnd = true;"
        // Here is the corresponding bytecode:
        //        iconst_1
        //        istore containsEnd

        il.add(new LabelNode());
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new VarInsnNode(Opcodes.ISTORE, containsEndSlot));

        // "break;"
        // Here is the corresponding bytecode:
        //         goto tryEnd_B

        il.add(new LabelNode());
        il.add(new JumpInsnNode(Opcodes.GOTO, tryEnd_B));

        // Compiler automatically inserts close calls for BufferedReader when try ends, so we have to do the same
        // Here is the corresponding bytecode:
        //        aload reader
        //        invokevirtual java/io/BufferedReader.close ()V
        //        goto AB

        il.add(tryEnd_B);
        il.add(new VarInsnNode(Opcodes.ALOAD, readerSlot));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/io/BufferedReader", "close", "()V"));
        il.add(new JumpInsnNode(Opcodes.GOTO, labelNode_AB));

        // Handle exceptions for try/catch block: B
        // Here is the corresponding bytecode:
        //        astore v9
        il.add(tryHandler_B);
        int v9Slot = methodNode.maxLocals++;
        il.add(new VarInsnNode(Opcodes.ASTORE, v9Slot));

        // This part seems to be some kind of inner try/catch for tryHandler_B
        // Though, I don't really know the exact principles behind it
        // One way or another, we have to also add these
        // Here is the corresponding bytecode:
        //    tryStart_C:
        //        aload reader
        //        invokevirtual java/io/BufferedReader.close ()V
        //    tryEnd_C:
        //        goto AA
        //    tryHandler_C:
        //        astore v10
        //        aload v9
        //        aload v10
        //        invokevirtual java/lang/Throwable.addSuppressed (Ljava/lang/Throwable;)V
        //    AA:
        //        aload v9
        //        athrow

        // We need to initialize a try/catch block: C
        // java/lang/Throwable
        LabelNode tryStart_C = new LabelNode();
        LabelNode tryEnd_C = new LabelNode();
        LabelNode tryHandler_C = new LabelNode();
        LabelNode labelNode_AA = new LabelNode();

        il.add(tryStart_C);
        il.add(new VarInsnNode(Opcodes.ALOAD, readerSlot));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/io/BufferedReader", "close", "()V"));
        il.add(tryEnd_C);
        il.add(new JumpInsnNode(Opcodes.GOTO, labelNode_AA));
        il.add(tryHandler_C);
        int v10Slot = methodNode.maxLocals++;
        il.add(new VarInsnNode(Opcodes.ASTORE, v10Slot));
        il.add(new VarInsnNode(Opcodes.ALOAD, v9Slot));
        il.add(new VarInsnNode(Opcodes.ALOAD, v10Slot));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Throwable", "addSuppressed", "(Ljava/lang/Throwable;)V"));
        il.add(labelNode_AA);
        il.add(new VarInsnNode(Opcodes.ALOAD, v9Slot));
        il.add(new InsnNode(Opcodes.ATHROW));

        // Here we can finally insert labelNode_AB
        // "if (containsEnd) return;"
        // Here is the corresponding bytecode:
        //        iload containsEnd
        //        ifeq AD
        //    tryEnd_A:
        //        return

        // But first we need to initialize a try/catch block: D
        // java/io/IOException
        // We'll start using it after the next sequence of manipulations
        LabelNode tryStart_D = new LabelNode();
        LabelNode tryEnd_D = new LabelNode();
        LabelNode tryHandler_D = new LabelNode();

        il.add(labelNode_AB);
        il.add(new VarInsnNode(Opcodes.ILOAD, containsEndSlot));
        il.add(new JumpInsnNode(Opcodes.IFEQ, tryStart_D));
        il.add(tryEnd_A);
        il.add(new InsnNode(Opcodes.RETURN));

        // "try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, true));){"
        // Here is the corresponding bytecode:
        //        new java/io/BufferedWriter
        //        dup
        //        new java/io/FileWriter
        //        dup
        //        aload file
        //        iconst_1
        //        invokespecial java/io/FileWriter.<init> (Ljava/io/File;Z)V
        //        invokespecial java/io/BufferedWriter.<init> (Ljava/io/Writer;)V
        //        astore writer

        il.add(tryStart_D);
        il.add(new TypeInsnNode(Opcodes.NEW, "java/io/BufferedWriter"));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new TypeInsnNode(Opcodes.NEW, "java/io/FileWriter"));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new VarInsnNode(Opcodes.ALOAD, fileSlot));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/io/FileWriter", "<init>", "(Ljava/io/File;Z)V"));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/io/BufferedWriter", "<init>", "(Ljava/io/Writer;)V"));
        int writerSlot = methodNode.maxLocals++;
        il.add(new VarInsnNode(Opcodes.ASTORE, writerSlot));

        // We need one more inner try/catch block again
        // Again, it's probably needed to handle close calls, this time for BufferWriter

        // But first we need to initialize a try/catch block: E
        // java/lang/Throwable
        LabelNode tryStart_E = new LabelNode();
        LabelNode tryEnd_E = new LabelNode();
        LabelNode tryHandler_E = new LabelNode();

        // "if (isEmpty) {"
        // Here is the corresponding bytecode:
        //        iload isEmpty
        //        ifeq AJ

        LabelNode labelNode_AJ = new LabelNode();

        il.add(tryStart_E);
        il.add(new VarInsnNode(Opcodes.ILOAD, isEmptySlot));
        il.add(new JumpInsnNode(Opcodes.IFEQ, labelNode_AJ));

        // Here is the java code:
        //                    writer.write("// Default color values for Bitwig Studio");
        //                    writer.newLine();
        //                    writer.write("// This file is auto-generated, please DO NOT edit.");
        //                    writer.newLine();
        // And, here is the corresponding bytecode:
        // Note: These label nodes aren't necessary actually, since there isn't any jump to them
        //        aload writer
        //        ldc "// Default color values for Bitwig Studio"
        //        invokevirtual java/io/BufferedWriter.write (Ljava/lang/String;)V
        //    AG:
        //        aload writer
        //        invokevirtual java/io/BufferedWriter.newLine ()V
        //    AH:
        //        aload writer
        //        ldc "// This file is auto-generated, please DO NOT edit."
        //        invokevirtual java/io/BufferedWriter.write (Ljava/lang/String;)V
        //    AI:
        //        aload writer
        //        invokevirtual java/io/BufferedWriter.newLine ()V

        il.add(new LabelNode());
        il.add(new VarInsnNode(Opcodes.ALOAD, writerSlot));
        il.add(new LdcInsnNode("// Default color values for Bitwig Studio " + Main.bitwigVersion));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/io/BufferedWriter", "write", "(Ljava/lang/String;)V"));
        il.add(new LabelNode());
        il.add(new VarInsnNode(Opcodes.ALOAD, writerSlot));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/io/BufferedWriter", "newLine", "()V"));
        il.add(new LabelNode());
        il.add(new VarInsnNode(Opcodes.ALOAD, writerSlot));
        il.add(new LdcInsnNode("// This file is auto-generated, please DO NOT edit."));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/io/BufferedWriter", "write", "(Ljava/lang/String;)V"));
        il.add(new LabelNode());
        il.add(new VarInsnNode(Opcodes.ALOAD, writerSlot));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/io/BufferedWriter", "newLine", "()V"));

        // Let's also manually add a line for "Gradient":
        il.add(new LabelNode());
        il.add(new VarInsnNode(Opcodes.ALOAD, writerSlot));
        il.add(new LdcInsnNode("Gradient: true")); // Gradient is initially on in Bitwig Studio
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/io/BufferedWriter", "write", "(Ljava/lang/String;)V"));
        il.add(new LabelNode());
        il.add(new VarInsnNode(Opcodes.ALOAD, writerSlot));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/io/BufferedWriter", "newLine", "()V"));

        // Here is the java code:
        //                if ("track".equals(colorName)) {
        //                    writer.write("// <end>");
        //                } else {
        //                    writer.write(v1 + ": " + v3.vpO());
        //                }
        //                writer.newLine();
        // And, here is the corresponding bytecode:
        //    AJ:
        //        ldc "track"
        //        aload colorName
        //        invokevirtual java/lang/String.equals (Ljava/lang/Object;)Z
        //        ifne AK
        //        ldc "highlighted_mapping_background"
        //        aload colorName
        //        invokevirtual java/lang/String.equals (Ljava/lang/Object;)Z
        //        ifeq AL
        //    AK:
        //        aload writer
        //        ldc "// <end>"
        //        invokevirtual java/io/BufferedWriter.write (Ljava/lang/String;)V
        //        goto AM
        //    AL:
        //        aload writer
        //        aload colorName
        //        aload colorInstance
        //        invokevirtual ColorClass.getColorHex ()Ljava/lang/String;
        //        invokedynamic makeConcatWithConstants (Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String; { invokestatic, java/lang/invoke/StringConcatFactory.makeConcatWithConstants, (Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite; } { "\u0001: \u0001" }
        //        invokevirtual java/io/BufferedWriter.write (Ljava/lang/String;)V
        //    AM:
        //        aload writer
        //        invokevirtual java/io/BufferedWriter.newLine ()V

        LabelNode labelNode_AK = new LabelNode();
        LabelNode labelNode_AL = new LabelNode();
        LabelNode labelNode_AM = new LabelNode();

        il.add(labelNode_AJ);
        il.add(new LdcInsnNode("track"));
        il.add(new VarInsnNode(Opcodes.ALOAD, mArgIndex)); // Load colorName (1st argument, index 0)
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z"));
        il.add(new JumpInsnNode(Opcodes.IFNE, labelNode_AK));
        il.add(new LdcInsnNode("highlighted_mapping_background"));
        il.add(new VarInsnNode(Opcodes.ALOAD, mArgIndex)); // Load colorName (1st argument, index 0)
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z"));
        il.add(new JumpInsnNode(Opcodes.IFEQ, labelNode_AL));
        il.add(labelNode_AK);
        il.add(new VarInsnNode(Opcodes.ALOAD, writerSlot));
        il.add(new LdcInsnNode("// <end>"));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/io/BufferedWriter", "write", "(Ljava/lang/String;)V"));
        il.add(new JumpInsnNode(Opcodes.GOTO, labelNode_AM));
        il.add(labelNode_AL);
        il.add(new VarInsnNode(Opcodes.ALOAD, writerSlot));
        il.add(new VarInsnNode(Opcodes.ALOAD, mArgIndex)); // Load colorName (1st argument, index 0)
        il.add(new VarInsnNode(Opcodes.ALOAD, mArgIndex + 2)); // Load colorInstance (3rd argument, index 2)
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, PatchClass.mappings.get("ColorClass"), PatchClass.mappings.get("ColorClass.getColorHex"), "()Ljava/lang/String;"));
        il.add(/*This one is tricky for sure!*/new InvokeDynamicInsnNode(
                "makeConcatWithConstants",
                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                new Handle(
                        Opcodes.H_INVOKESTATIC,
                        "java/lang/invoke/StringConcatFactory",
                        "makeConcatWithConstants",
                        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;"
                                + "Ljava/lang/invoke/MethodType;Ljava/lang/String;"
                                + "[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
                        false
                ),
                "\u0001: \u0001"
        ));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/io/BufferedWriter", "write", "(Ljava/lang/String;)V"));
        il.add(labelNode_AM);
        il.add(new VarInsnNode(Opcodes.ALOAD, writerSlot));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/io/BufferedWriter", "newLine", "()V"));

        // Let's finish all the bytecode mess by closing the try/catch blocks
        // Here is the corresponding bytecode:
        //     AN:
        //        aload writer
        //        invokevirtual java/io/BufferedWriter.close ()V
        //        goto tryEnd_D
        //    AO:
        //        astore v9
        //    AP:
        //        aload writer
        //        invokevirtual java/io/BufferedWriter.close ()V
        //    AQ:
        //        goto AS
        //    AR:
        //        astore v10
        //        aload v9
        //        aload v10
        //        invokevirtual java/lang/Throwable.addSuppressed (Ljava/lang/Throwable;)V
        //    AS:
        //        aload v9
        //        athrow
        //    AT:
        //        goto AW
        //    AU:
        //        astore stackTrace
        //    AV:
        //        aload stackTrace
        //        invokevirtual java/io/IOException.printStackTrace ()V
        //    AW:

        // But first we need to initialize a try/catch block: F
        // java/lang/Throwable
        LabelNode tryStart_F = new LabelNode();
        LabelNode tryEnd_F = new LabelNode();
        LabelNode tryHandler_F = new LabelNode();

        // Initialize label nodes for jumps (goto)
        LabelNode labelNode_AS = new LabelNode();
        LabelNode labelNode_AW = new LabelNode();

        il.add(tryEnd_E);
        il.add(new VarInsnNode(Opcodes.ALOAD, writerSlot));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/io/BufferedWriter", "close", "()V"));
        il.add(new JumpInsnNode(Opcodes.GOTO, tryEnd_D));
        il.add(tryHandler_E);
        il.add(new VarInsnNode(Opcodes.ASTORE, v9Slot));
        il.add(tryStart_F);
        il.add(new VarInsnNode(Opcodes.ALOAD, writerSlot));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/io/BufferedWriter", "close", "()V"));
        il.add(tryEnd_F);
        il.add(new JumpInsnNode(Opcodes.GOTO, labelNode_AS));
        il.add(tryHandler_F);
        il.add(new VarInsnNode(Opcodes.ASTORE, v10Slot));
        il.add(new VarInsnNode(Opcodes.ALOAD, v9Slot));
        il.add(new VarInsnNode(Opcodes.ALOAD, v10Slot));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Throwable", "addSuppressed", "(Ljava/lang/Throwable;)V"));
        il.add(labelNode_AS);
        il.add(new VarInsnNode(Opcodes.ALOAD, v9Slot));
        il.add(new InsnNode(Opcodes.ATHROW));
        il.add(tryEnd_D);
        il.add(new JumpInsnNode(Opcodes.GOTO, labelNode_AW));
        il.add(tryHandler_A); /*and*/ il.add(tryHandler_D);
        int stackTraceSlot = methodNode.maxLocals++;
        il.add(new VarInsnNode(Opcodes.ASTORE, stackTraceSlot));
        il.add(new LabelNode());
        il.add(new VarInsnNode(Opcodes.ALOAD, stackTraceSlot));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/io/IOException", "printStackTrace", "()V"));
        il.add(labelNode_AW);

        // Finally, we need to add all the try/catch blocks to methodNode's tryCatchBlocks list
        methodNode.tryCatchBlocks.add(new TryCatchBlockNode(tryStart_A, tryEnd_A, tryHandler_A, "java/io/IOException"));
        methodNode.tryCatchBlocks.add(new TryCatchBlockNode(tryStart_B, tryEnd_B, tryHandler_B, "java/lang/Throwable"));
        methodNode.tryCatchBlocks.add(new TryCatchBlockNode(tryStart_C, tryEnd_C, tryHandler_C, "java/lang/Throwable"));
        methodNode.tryCatchBlocks.add(new TryCatchBlockNode(tryStart_D, tryEnd_D, tryHandler_D, "java/io/IOException"));
        methodNode.tryCatchBlocks.add(new TryCatchBlockNode(tryStart_E, tryEnd_E, tryHandler_E, "java/lang/Throwable"));
        methodNode.tryCatchBlocks.add(new TryCatchBlockNode(tryStart_F, tryEnd_F, tryHandler_F, "java/lang/Throwable"));

        // Here is the written Java code as a whole, of written bytecode, as a reference:
        /*
            colorInstance.colorName = colorName;
            File file = new File("default.bte");
            try {
                boolean fileExists = file.exists();
                boolean isEmpty = !fileExists || file.length() == 0L;
                boolean containsEnd = false;
                if (fileExists) {
                    try (BufferedReader reader = new BufferedReader(new FileReader(file));){
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (!line.trim().equals("// <end>")) continue;
                            containsEnd = true;
                            break;
                        }
                    }
                }
                if (containsEnd) {
                    return;
                }
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, true));){
                    if (isEmpty) {
                        writer.write("// Default color values for Bitwig Studio");
                        writer.newLine();
                        writer.write("// This file is auto-generated, please DO NOT edit.");
                        writer.newLine();
                    }
                    if ("track".equals(v1) || "highlighted_mapping_background".equals(v1)) {
                        writer.write("// <end>");
                    } else {
                        writer.write(colorName + ": " + ColorClass.getColorHex());
                    }
                    writer.newLine();
                }
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        */
    }
}
