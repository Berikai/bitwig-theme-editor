package dev.berikai.BitwigTheme.core.impl;

import dev.berikai.BitwigTheme.core.PatchClass;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.HashMap;

public class ColorPatchClass extends PatchClass {
    // There is two type of color classes in Bitwig Studio, pre 5.2 and 5.2+
    // The difference is that in 5.2+ color is stored as an integer value instead of 4 different float values for RGBA
    // pre 5.2: 0
    // 5.2+: 1
    private int colorClassType;

    public ColorPatchClass(HashMap<String, ClassNode> classNodes) {
        // Call super class constructor
        super(classNodes, "ColorClass");

        // Find the class that contains the string "transparent" (lowercase matters)
        // Color class is the only class that has this string, which makes our lives easier
        // Note: transparent is not seem like such a unique identifier word though, let's hope it won't cause any issues in future releases
        findClassNode("transparent");

        // If classNode is null, inform the user
        if (classNode == null) {
            System.out.println("ERROR: Color class not found. Your Bitwig Studio version may not be supported.");
            System.out.println(" -> Known working versions: 4.x, 5.x, 6.x");
            // Let the program crash, since we can't proceed without this class
        }

        // Determine the type of color class
        findColorClassType();

        // Find method names and nodes that we will be using in the bytecode
        findMappingMethods();

        // Find the method node that returns red color value
        findMethodNode();
    }

    // Determine the type of color class by checking its fields
    private void findColorClassType() {
        int type = 0;
        int floatFieldCount = 0;

        for (FieldNode field : classNode.fields) {
            // If we find an integer field, it's the new color class type
            if (field.desc.equals("I")) {
                type = 1;
                PatchClass.mappings.put("ColorClass.colorIntegerValue", field.name);

                // Remove final from colorIntegerValue field if it exists, because we want to change
                field.access = field.access & ~Opcodes.ACC_FINAL;
                break;
            }

            // If we find a float fields, it's the old color class type
            if (field.desc.equals("F")) {
                String floatFieldName;
                switch (floatFieldCount) {
                    case 0:
                        floatFieldName = "redValue";
                        break;
                    case 1:
                        floatFieldName = "greenValue";
                        break;
                    case 2:
                        floatFieldName = "blueValue";
                        break;
                    case 3:
                        floatFieldName = "alphaValue";
                        break;
                    default:
                        floatFieldName = null;
                        this.colorClassType = type;
                        return;
                }

                PatchClass.mappings.put("ColorClass." + floatFieldName, field.name);

                // Remove final from colorIntegerValue field if it exists, because we want to change
                field.access = field.access & ~Opcodes.ACC_FINAL;

                floatFieldCount++;
            }
        }
        this.colorClassType = type;
    }

    private void findMappingMethods() {
        // Iterate through all the methods of class
        outer: for (MethodNode getColorHexMethod : classNode.methods) {
            // Check if the description aligns with our target method: getColorHex ()Ljava/lang/String;
            if (getColorHexMethod.access == Opcodes.ACC_PUBLIC && getColorHexMethod.desc.equals("()Ljava/lang/String;")) {
                // Iterate through instructions
                InsnList insnList = getColorHexMethod.instructions;
                for(int i = 0; i < insnList.size(); i++) {
                    AbstractInsnNode insnNode = insnList.get(i);
                    // If it has the string "#", we have found our method
                    if (insnNode.getOpcode() == Opcodes.LDC && ((LdcInsnNode) insnNode).cst.equals("#")) {
                        PatchClass.mappings.put("ColorClass.getColorHex", getColorHexMethod.name);
                        break outer;
                    }
                }
            }
        }

        // If color class type is pre 5.2, we don't need to find convertRGBtoInt method
        if (colorClassType == 0) return;

        // Iterate through all the methods of class to find convertRGBtoInt method
        for (MethodNode getColorHexMethod : classNode.methods) {
            // Check if the description aligns with our target method: public static int convertRGBtoInt(float alpha, float red, float green, float blue)
            if (getColorHexMethod.access == (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC) && getColorHexMethod.desc.equals("(FFFF)I")) {
                PatchClass.mappings.put("ColorClass.convertRGBtoInt", getColorHexMethod.name);
                break;
            }
        }

    }

    // Find the method that returns red value
    protected void findMethodNode() {
        int methodCounter = 0;
        for (MethodNode mn : classNode.methods) {
            // Check if the method is public and returns float or int based on color class type
            // Pre 5.2: public float getRed()
            // 5.2+: public int getRed()
            if ((mn.access & Opcodes.ACC_PUBLIC) != 0 && mn.desc.equals(colorClassType == 0 ? "()F" : "()I")) {
                // Increment method counter, the 2nd method that matches this criteria is getRed()
                if (methodCounter != 1) {
                    methodCounter++;
                } else {
                    methodNode = mn;
                    break;
                }
            }
        }
    }

    private void createBytecodeFields() {
        // Add a new field to class to store color name
        classNode.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "colorName", "Ljava/lang/String;", null, null));

        // Add a new field to class to store last fetch timestamp
        // This will be used to prevent excessive file reads, *hopefully* it'll be enough to prevent performance issues
        classNode.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "lastColorFetch", "J", null, null));
    }

    // The code we will inject here to the found method is responsible for reading "theme.bte"
    // It will read the color values with their names, in this theme format
    // The file will be located to the same directory of Bitwig Studio executable
    public void patch() {
        // Create new fields in Color class to store color name and last fetch timestamp
        createBytecodeFields();

        // Create a new instruction list to write new bytecode on
        InsnList exportIl = new InsnList();
        writeBytecode(exportIl);

        // Insert new instructions before return
        methodNode.instructions.insert(exportIl);
    }

    // --------- Bytecode writing section ---------

    // Write bytecode to be injected to method
    private void writeBytecode(InsnList il) {
        // We don't need mArgIndex as in BridgePatchClass since this method doesn't have any arguments

        // Create a label to jump to return
        LabelNode returnLabel = new LabelNode();

        // Let's begin with checking lastColorFetch timestamp
        // If lastColorFetch is 0, it's the first time we are fetching color, so we need to read the file
        // If currentTimeMillis - lastColorFetch <= 2000L, skip file read
        // Here is the corresponding bytecode:
        //        aload this
        //        getfield YhQ.colorName Ljava/lang/String;
        //        ifnull BC
        //        invokestatic java/lang/System.currentTimeMillis ()J
        //        aload this
        //        getfield ColorClass.lastColorFetch J
        //        lsub
        //        ldc 3000L
        //        lcmp
        //        ifle returnLabel
        //    B:
        //        aload this
        //        invokestatic java/lang/System.currentTimeMillis ()J
        //        putfield ColorClass.lastColorFetch J

        LabelNode labelNode_B = new LabelNode();

        il.add(new LabelNode());
        il.add(new VarInsnNode(Opcodes.ALOAD, 0/*this*/));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, PatchClass.mappings.get("ColorClass"), "colorName", "Ljava/lang/String;"));
        il.add(new JumpInsnNode(Opcodes.IFNULL, returnLabel));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/System", "currentTimeMillis", "()J"));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0/*this*/));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, PatchClass.mappings.get("ColorClass"), "lastColorFetch", "J"));
        il.add(new InsnNode(Opcodes.LSUB));
        il.add(new LdcInsnNode(3000L));
        il.add(new InsnNode(Opcodes.LCMP));
        il.add(new JumpInsnNode(Opcodes.IFLE, returnLabel));
        il.add(labelNode_B);
        il.add(new VarInsnNode(Opcodes.ALOAD, 0/*this*/));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/System", "currentTimeMillis", "()J"));
        il.add(new FieldInsnNode(Opcodes.PUTFIELD, PatchClass.mappings.get("ColorClass"), "lastColorFetch", "J"));

        // "try (BufferedReader br = new BufferedReader(new FileReader("theme.bte"));) {"
        // Here is the corresponding bytecode:
        //        new java/io/BufferedReader
        //        dup
        //        new java/io/FileReader
        //        dup
        //        ldc "theme.bte"
        //        invokespecial java/io/FileReader.<init> (Ljava/lang/String;)V
        //        invokespecial java/io/BufferedReader.<init> (Ljava/io/Reader;)V
        //        astore br

        // We need to create try/catch block: A (C-AJ)
        // java/io/IOException
        LabelNode tryStart_A = new LabelNode();
        LabelNode tryEnd_A = new LabelNode();
        LabelNode tryHandler_A = new LabelNode();

        il.add(tryStart_A);
        il.add(new TypeInsnNode(Opcodes.NEW, "java/io/BufferedReader"));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new TypeInsnNode(Opcodes.NEW, "java/io/FileReader"));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new LdcInsnNode("theme.bte"));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/io/FileReader", "<init>", "(Ljava/lang/String;)V"));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/io/BufferedReader", "<init>", "(Ljava/io/Reader;)V"));
        int brSlot = methodNode.maxLocals++;
        il.add(new VarInsnNode(Opcodes.ASTORE, brSlot));

        // "String line; while ((line = br.readLine()) != null) {"
        // Here is the corresponding bytecode:
        //    tryStart_B:
        //        aload br
        //        invokevirtual java/io/BufferedReader.readLine ()Ljava/lang/String;
        //        dup
        //        astore line
        //    E:
        //        ifnull AN

        // We will insert these label nodes later
        // But we have to initialize them here to insert necessary jumps
        LabelNode labelNode_AN = new LabelNode();

        // We need to create another try/catch block: B (D-AI)
        // java/lang/Throwable
        LabelNode tryStart_B = new LabelNode();
        LabelNode tryEnd_B = new LabelNode();
        LabelNode tryHandler_B = new LabelNode();

        il.add(tryStart_B);
        il.add(new VarInsnNode(Opcodes.ALOAD, brSlot));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/io/BufferedReader", "readLine", "()Ljava/lang/String;"));
        il.add(new InsnNode(Opcodes.DUP));
        int lineSlot = methodNode.maxLocals++;
        il.add(new VarInsnNode(Opcodes.ASTORE, lineSlot));
        il.add(new JumpInsnNode(Opcodes.IFNULL, labelNode_AN));

        // We have to seperate the line with ": ", in order to get the color name and hex value
        // Here is the corresponding bytecode:
        //        aload line
        //        ldc ": "
        //        invokevirtual java/lang/String.indexOf (Ljava/lang/String;)I
        //        istore idx

        il.add(new LabelNode());
        il.add(new VarInsnNode(Opcodes.ALOAD, lineSlot));
        il.add(new LdcInsnNode(": "));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "indexOf", "(Ljava/lang/String;)I"));
        int idxSlot = methodNode.maxLocals++;
        il.add(new VarInsnNode(Opcodes.ISTORE, idxSlot));

        // "if (idx == -1) continue;"
        // If idx is -1, continue to next line
        // Here is the corresponding bytecode:
        //        iload idx
        //        iconst_m1
        //        if_icmpne H
        //        goto tryStart_B

        LabelNode labelNode_H = new LabelNode();

        il.add(new LabelNode());
        il.add(new VarInsnNode(Opcodes.ILOAD, idxSlot));
        il.add(new InsnNode(Opcodes.ICONST_M1));
        il.add(new JumpInsnNode(Opcodes.IF_ICMPNE, labelNode_H));
        il.add(new JumpInsnNode(Opcodes.GOTO, tryStart_B));

        // "String hex = line.substring(idx + 2).split("//")[0].trim();"
        // Here is the corresponding bytecode:
        //        aload line
        //        iload idx
        //        iconst_2
        //        iadd
        //        invokevirtual java/lang/String.substring (I)Ljava/lang/String;
        //        ldc "//"
        //        invokevirtual java/lang/String.split (Ljava/lang/String;)[Ljava/lang/String;
        //        iconst_0
        //        aaload
        //        invokevirtual java/lang/String.trim ()Ljava/lang/String;
        //        astore hex

        il.add(labelNode_H);
        il.add(new VarInsnNode(Opcodes.ALOAD, lineSlot));
        il.add(new VarInsnNode(Opcodes.ILOAD, idxSlot));
        il.add(new InsnNode(Opcodes.ICONST_2));
        il.add(new InsnNode(Opcodes.IADD));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "substring", "(I)Ljava/lang/String;"));
        il.add(new LdcInsnNode("//"));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "split", "(Ljava/lang/String;)[Ljava/lang/String;"));
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new InsnNode(Opcodes.AALOAD));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "trim", "()Ljava/lang/String;"));
        int hexSlot = methodNode.maxLocals++;
        il.add(new VarInsnNode(Opcodes.ASTORE, hexSlot));

        // "if ((line = line.trim()).startsWith("//") || line.isEmpty() || !hex.startsWith("#") || this.colorName == null || !line.startsWith(this.colorName)) continue;"
        // This line is really complex in terms of bytecode control flow
        // Here is the corresponding bytecode:
        //        aload line
        //        invokevirtual java/lang/String.trim ()Ljava/lang/String;
        //        dup
        //        astore line
        //        ldc "//"
        //        invokevirtual java/lang/String.startsWith (Ljava/lang/String;)Z
        //        ifne tryStart_B
        //        aload line
        //        invokevirtual java/lang/String.isEmpty ()Z
        //        ifne tryStart_B
        //        aload hex
        //        ldc "#"
        //        invokevirtual java/lang/String.startsWith (Ljava/lang/String;)Z
        //        ifeq tryStart_B
        //        aload this
        //        getfield gPj.colorName Ljava/lang/String;
        //        ifnull tryStart_B
        //        aload line
        //        aload this
        //        getfield gPj.colorName Ljava/lang/String;
        //        invokevirtual java/lang/String.startsWith (Ljava/lang/String;)Z
        //        ifne J
        //        goto tryStart_B

        LabelNode labelNode_J = new LabelNode();

        il.add(new LabelNode());
        il.add(new VarInsnNode(Opcodes.ALOAD, lineSlot));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "trim", "()Ljava/lang/String;"));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new VarInsnNode(Opcodes.ASTORE, lineSlot));
        il.add(new LdcInsnNode("//"));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "startsWith", "(Ljava/lang/String;)Z"));
        il.add(new JumpInsnNode(Opcodes.IFNE, tryStart_B));
        il.add(new VarInsnNode(Opcodes.ALOAD, lineSlot));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "isEmpty", "()Z"));
        il.add(new JumpInsnNode(Opcodes.IFNE, tryStart_B));
        il.add(new VarInsnNode(Opcodes.ALOAD, hexSlot));
        il.add(new LdcInsnNode("#"));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "startsWith", "(Ljava/lang/String;)Z"));
        il.add(new JumpInsnNode(Opcodes.IFEQ, tryStart_B));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0/*this*/));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, PatchClass.mappings.get("ColorClass"), "colorName", "Ljava/lang/String;"));
        il.add(new JumpInsnNode(Opcodes.IFNULL, tryStart_B));
        il.add(new VarInsnNode(Opcodes.ALOAD, lineSlot));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0/*this*/));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, PatchClass.mappings.get("ColorClass"), "colorName", "Ljava/lang/String;"));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "startsWith", "(Ljava/lang/String;)Z"));
        il.add(new JumpInsnNode(Opcodes.IFNE, labelNode_J));
        il.add(new JumpInsnNode(Opcodes.GOTO, tryStart_B));

        // "if ((hex = hex.substring(1)).length() == 3) {"
        // Here is the corresponding bytecode:
        //    J:
        //        aload hex
        //        iconst_1
        //        invokevirtual java/lang/String.substring (I)Ljava/lang/String;
        //        dup
        //        astore hex
        //        invokevirtual java/lang/String.length ()I
        //        iconst_3
        //        if_icmpne P

        LabelNode labelNode_P = new LabelNode();

        il.add(labelNode_J);
        il.add(new VarInsnNode(Opcodes.ALOAD, hexSlot));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "substring", "(I)Ljava/lang/String;"));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new VarInsnNode(Opcodes.ASTORE, hexSlot));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "length", "()I"));
        il.add(new InsnNode(Opcodes.ICONST_3));
        il.add(new JumpInsnNode(Opcodes.IF_ICMPNE, labelNode_P));

        // If length is 3, it's a short hex code, we need to convert it to full hex code
        // Here is the corresponding Java code:
        //                    r = (float)Integer.parseInt(hex.substring(0, 1) + hex.substring(0, 1), 16) / 255.0f;
        //                    g = (float)Integer.parseInt(hex.substring(1, 2) + hex.substring(1, 2), 16) / 255.0f;
        //                    b = (float)Integer.parseInt(hex.substring(2, 3) + hex.substring(2, 3), 16) / 255.0f;
        //                    a = 1.0f;
        // Here is the corresponding bytecode:
        //    K:
        //        line 620
        //        aload hex
        //        iconst_0
        //        iconst_1
        //        invokevirtual java/lang/String.substring (II)Ljava/lang/String;
        //        aload hex
        //        iconst_0
        //        iconst_1
        //        invokevirtual java/lang/String.substring (II)Ljava/lang/String;
        //        invokedynamic makeConcatWithConstants (Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String; { invokestatic, java/lang/invoke/StringConcatFactory.makeConcatWithConstants, (Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite; } { "\u0001\u0001" }
        //        bipush 16
        //        invokestatic java/lang/Integer.parseInt (Ljava/lang/String;I)I
        //        i2f
        //        ldc 255F
        //        fdiv
        //        fstore r
        //    L:
        //        line 621
        //        aload hex
        //        iconst_1
        //        iconst_2
        //        invokevirtual java/lang/String.substring (II)Ljava/lang/String;
        //        aload hex
        //        iconst_1
        //        iconst_2
        //        invokevirtual java/lang/String.substring (II)Ljava/lang/String;
        //        invokedynamic makeConcatWithConstants (Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String; { invokestatic, java/lang/invoke/StringConcatFactory.makeConcatWithConstants, (Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite; } { "\u0001\u0001" }
        //        bipush 16
        //        invokestatic java/lang/Integer.parseInt (Ljava/lang/String;I)I
        //        i2f
        //        ldc 255F
        //        fdiv
        //        fstore g
        //    M:
        //        line 622
        //        aload hex
        //        iconst_2
        //        iconst_3
        //        invokevirtual java/lang/String.substring (II)Ljava/lang/String;
        //        aload hex
        //        iconst_2
        //        iconst_3
        //        invokevirtual java/lang/String.substring (II)Ljava/lang/String;
        //        invokedynamic makeConcatWithConstants (Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String; { invokestatic, java/lang/invoke/StringConcatFactory.makeConcatWithConstants, (Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite; } { "\u0001\u0001" }
        //        bipush 16
        //        invokestatic java/lang/Integer.parseInt (Ljava/lang/String;I)I
        //        i2f
        //        ldc 255F
        //        fdiv
        //        fstore b
        //    N:
        //        line 623
        //        fconst_1
        //        fstore a

        il.add(new LabelNode());
        il.add(new VarInsnNode(Opcodes.ALOAD, hexSlot));
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "substring", "(II)Ljava/lang/String;"));
        il.add(new VarInsnNode(Opcodes.ALOAD, hexSlot));
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "substring", "(II)Ljava/lang/String;"));
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
                "\u0001\u0001"
        ));
        il.add(new IntInsnNode(Opcodes.BIPUSH, 16));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Integer", "parseInt", "(Ljava/lang/String;I)I"));
        il.add(new InsnNode(Opcodes.I2F));
        il.add(new LdcInsnNode(255.0f));
        il.add(new InsnNode(Opcodes.FDIV));
        int rSlot = methodNode.maxLocals++; // r for red
        il.add(new VarInsnNode(Opcodes.FSTORE, rSlot));

        il.add(new LabelNode());
        il.add(new VarInsnNode(Opcodes.ALOAD, hexSlot));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new InsnNode(Opcodes.ICONST_2));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "substring", "(II)Ljava/lang/String;"));
        il.add(new VarInsnNode(Opcodes.ALOAD, hexSlot));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new InsnNode(Opcodes.ICONST_2));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "substring", "(II)Ljava/lang/String;"));
        il.add(new InvokeDynamicInsnNode(
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
                "\u0001\u0001"
        ));
        il.add(new IntInsnNode(Opcodes.BIPUSH, 16));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Integer", "parseInt", "(Ljava/lang/String;I)I"));
        il.add(new InsnNode(Opcodes.I2F));
        il.add(new LdcInsnNode(255.0f));
        il.add(new InsnNode(Opcodes.FDIV));
        int gSlot = methodNode.maxLocals++; // g for green
        il.add(new VarInsnNode(Opcodes.FSTORE, gSlot));

        il.add(new LabelNode());
        il.add(new VarInsnNode(Opcodes.ALOAD, hexSlot));
        il.add(new InsnNode(Opcodes.ICONST_2));
        il.add(new InsnNode(Opcodes.ICONST_3));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "substring", "(II)Ljava/lang/String;"));
        il.add(new VarInsnNode(Opcodes.ALOAD, hexSlot));
        il.add(new InsnNode(Opcodes.ICONST_2));
        il.add(new InsnNode(Opcodes.ICONST_3));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "substring", "(II)Ljava/lang/String;"));
        il.add(new InvokeDynamicInsnNode(
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
                "\u0001\u0001"
        ));
        il.add(new IntInsnNode(Opcodes.BIPUSH, 16));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Integer", "parseInt", "(Ljava/lang/String;I)I"));
        il.add(new InsnNode(Opcodes.I2F));
        il.add(new LdcInsnNode(255.0f));
        il.add(new InsnNode(Opcodes.FDIV));
        int bSlot = methodNode.maxLocals++; // b for blue
        il.add(new VarInsnNode(Opcodes.FSTORE, bSlot));

        il.add(new LabelNode());
        il.add(new InsnNode(Opcodes.FCONST_1));
        int aSlot = methodNode.maxLocals++; // a for alpha
        il.add(new VarInsnNode(Opcodes.FSTORE, aSlot));

        // In the end of every if block in the if/else chain, we'll jump to afterwards of all of them
        // goto tryStart_C

        // Let's create the try/catch blocks very early, since we need the jumps: C and D (AK-AN, AK-AT)
        // C: java/lang/Throwable
        // D: java/io/IOException
        LabelNode tryStart_C = new LabelNode();
        LabelNode tryEnd_C = new LabelNode();
        LabelNode tryHandler_C = new LabelNode();
        LabelNode tryStart_D = new LabelNode();
        LabelNode tryEnd_D = new LabelNode();
        LabelNode tryHandler_D = new LabelNode();

        il.add(new LabelNode());
        il.add(new JumpInsnNode(Opcodes.GOTO, tryStart_C));

        // "} else if (hex.length() == 4) {"
        // (!) This become a bit repetitive, but oh well...
        // Here is the corresponding bytecode:
        //        aload hex
        //        invokevirtual java/lang/String.length ()I
        //        iconst_4
        //        if_icmpne V

        LabelNode labelNode_V = new LabelNode();

        il.add(labelNode_P);
        il.add(new VarInsnNode(Opcodes.ALOAD, hexSlot));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "length", "()I"));
        il.add(new InsnNode(Opcodes.ICONST_4));
        il.add(new JumpInsnNode(Opcodes.IF_ICMPNE, labelNode_V));

        // If length is 4, it's a short hex code with alpha, we need to convert it to full hex code
        // Here is the corresponding bytecode:
        //    Q:
        //        aload hex
        //        iconst_0
        //        iconst_1
        //        invokevirtual java/lang/String.substring (II)Ljava/lang/String;
        //        aload hex
        //        iconst_0
        //        iconst_1
        //        invokevirtual java/lang/String.substring (II)Ljava/lang/String;
        //        invokedynamic makeConcatWithConstants (Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String; { invokestatic, java/lang/invoke/StringConcatFactory.makeConcatWithConstants, (Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite; } { "\u0001\u0001" }
        //        bipush 16
        //        invokestatic java/lang/Integer.parseInt (Ljava/lang/String;I)I
        //        i2f
        //        ldc 255F
        //        fdiv
        //        fstore r
        //    R:
        //        aload hex
        //        iconst_1
        //        iconst_2
        //        invokevirtual java/lang/String.substring (II)Ljava/lang/String;
        //        aload hex
        //        iconst_1
        //        iconst_2
        //        invokevirtual java/lang/String.substring (II)Ljava/lang/String;
        //        invokedynamic makeConcatWithConstants (Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String; { invokestatic, java/lang/invoke/StringConcatFactory.makeConcatWithConstants, (Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite; } { "\u0001\u0001" }
        //        bipush 16
        //        invokestatic java/lang/Integer.parseInt (Ljava/lang/String;I)I
        //        i2f
        //        ldc 255F
        //        fdiv
        //        fstore g
        //    S:
        //        aload hex
        //        iconst_2
        //        iconst_3
        //        invokevirtual java/lang/String.substring (II)Ljava/lang/String;
        //        aload hex
        //        iconst_2
        //        iconst_3
        //        invokevirtual java/lang/String.substring (II)Ljava/lang/String;
        //        invokedynamic makeConcatWithConstants (Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String; { invokestatic, java/lang/invoke/StringConcatFactory.makeConcatWithConstants, (Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite; } { "\u0001\u0001" }
        //        bipush 16
        //        invokestatic java/lang/Integer.parseInt (Ljava/lang/String;I)I
        //        i2f
        //        ldc 255F
        //        fdiv
        //        fstore b
        //    T:
        //        aload hex
        //        iconst_3
        //        iconst_4
        //        invokevirtual java/lang/String.substring (II)Ljava/lang/String;
        //        aload hex
        //        iconst_3
        //        iconst_4
        //        invokevirtual java/lang/String.substring (II)Ljava/lang/String;
        //        invokedynamic makeConcatWithConstants (Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String; { invokestatic, java/lang/invoke/StringConcatFactory.makeConcatWithConstants, (Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite; } { "\u0001\u0001" }
        //        bipush 16
        //        invokestatic java/lang/Integer.parseInt (Ljava/lang/String;I)I
        //        i2f
        //        ldc 255F
        //        fdiv
        //        fstore a

        il.add(new LabelNode());
        il.add(new VarInsnNode(Opcodes.ALOAD, hexSlot));
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "substring", "(II)Ljava/lang/String;"));
        il.add(new VarInsnNode(Opcodes.ALOAD, hexSlot));
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "substring", "(II)Ljava/lang/String;"));
        il.add(new InvokeDynamicInsnNode(
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
                "\u0001\u0001"
        ));
        il.add(new IntInsnNode(Opcodes.BIPUSH, 16));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Integer", "parseInt", "(Ljava/lang/String;I)I"));
        il.add(new InsnNode(Opcodes.I2F));
        il.add(new LdcInsnNode(255.0f));
        il.add(new InsnNode(Opcodes.FDIV));
        // We won't create new slots for r, g, b, a, since they are already created
        il.add(new VarInsnNode(Opcodes.FSTORE, rSlot));

        il.add(new LabelNode());
        il.add(new VarInsnNode(Opcodes.ALOAD, hexSlot));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new InsnNode(Opcodes.ICONST_2));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "substring", "(II)Ljava/lang/String;"));
        il.add(new VarInsnNode(Opcodes.ALOAD, hexSlot));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new InsnNode(Opcodes.ICONST_2));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "substring", "(II)Ljava/lang/String;"));
        il.add(new InvokeDynamicInsnNode(
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
                "\u0001\u0001"
        ));
        il.add(new IntInsnNode(Opcodes.BIPUSH, 16));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Integer", "parseInt", "(Ljava/lang/String;I)I"));
        il.add(new InsnNode(Opcodes.I2F));
        il.add(new LdcInsnNode(255.0f));
        il.add(new InsnNode(Opcodes.FDIV));
        il.add(new VarInsnNode(Opcodes.FSTORE, gSlot));

        il.add(new LabelNode());
        il.add(new VarInsnNode(Opcodes.ALOAD, hexSlot));
        il.add(new InsnNode(Opcodes.ICONST_2));
        il.add(new InsnNode(Opcodes.ICONST_3));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "substring", "(II)Ljava/lang/String;"));
        il.add(new VarInsnNode(Opcodes.ALOAD, hexSlot));
        il.add(new InsnNode(Opcodes.ICONST_2));
        il.add(new InsnNode(Opcodes.ICONST_3));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "substring", "(II)Ljava/lang/String;"));
        il.add(new InvokeDynamicInsnNode(
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
                "\u0001\u0001"
        ));
        il.add(new IntInsnNode(Opcodes.BIPUSH, 16));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Integer", "parseInt", "(Ljava/lang/String;I)I"));
        il.add(new InsnNode(Opcodes.I2F));
        il.add(new LdcInsnNode(255.0f));
        il.add(new InsnNode(Opcodes.FDIV));
        il.add(new VarInsnNode(Opcodes.FSTORE, bSlot));

        il.add(new LabelNode());
        il.add(new VarInsnNode(Opcodes.ALOAD, hexSlot));
        il.add(new InsnNode(Opcodes.ICONST_3));
        il.add(new InsnNode(Opcodes.ICONST_4));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "substring", "(II)Ljava/lang/String;"));
        il.add(new VarInsnNode(Opcodes.ALOAD, hexSlot));
        il.add(new InsnNode(Opcodes.ICONST_3));
        il.add(new InsnNode(Opcodes.ICONST_4));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "substring", "(II)Ljava/lang/String;"));
        il.add(new InvokeDynamicInsnNode(
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
                "\u0001\u0001"
        ));
        il.add(new IntInsnNode(Opcodes.BIPUSH, 16));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Integer", "parseInt", "(Ljava/lang/String;I)I"));
        il.add(new InsnNode(Opcodes.I2F));
        il.add(new LdcInsnNode(255.0f));
        il.add(new InsnNode(Opcodes.FDIV));
        il.add(new VarInsnNode(Opcodes.FSTORE, aSlot));

        // goto tryStart_C
        il.add(new LabelNode());
        il.add(new JumpInsnNode(Opcodes.GOTO, tryStart_C));

        // "} else if (hex.length() == 6) {"
        // Here is the corresponding bytecode:
        //        aload hex
        //        invokevirtual java/lang/String.length ()I
        //        bipush 6
        //        if_icmpne AB

        LabelNode labelNode_AB = new LabelNode();

        il.add(labelNode_V);
        il.add(new VarInsnNode(Opcodes.ALOAD, hexSlot));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "length", "()I"));
        il.add(new IntInsnNode(Opcodes.BIPUSH, 6));
        il.add(new JumpInsnNode(Opcodes.IF_ICMPNE, labelNode_AB));

        // If length is 6, it's a full hex code without alpha
        // Here is the corresponding bytecode:
        //    W:
        //        line 630
        //        aload hex
        //        iconst_0
        //        iconst_2
        //        invokevirtual java/lang/String.substring (II)Ljava/lang/String;
        //        bipush 16
        //        invokestatic java/lang/Integer.parseInt (Ljava/lang/String;I)I
        //        i2f
        //        ldc 255F
        //        fdiv
        //        fstore r
        //    X:
        //        line 631
        //        aload hex
        //        iconst_2
        //        iconst_4
        //        invokevirtual java/lang/String.substring (II)Ljava/lang/String;
        //        bipush 16
        //        invokestatic java/lang/Integer.parseInt (Ljava/lang/String;I)I
        //        i2f
        //        ldc 255F
        //        fdiv
        //        fstore g
        //    Y:
        //        line 632
        //        aload hex
        //        iconst_4
        //        bipush 6
        //        invokevirtual java/lang/String.substring (II)Ljava/lang/String;
        //        bipush 16
        //        invokestatic java/lang/Integer.parseInt (Ljava/lang/String;I)I
        //        i2f
        //        ldc 255F
        //        fdiv
        //        fstore b
        //    Z:
        //        line 633
        //        fconst_1
        //        fstore a

        il.add(new LabelNode());
        il.add(new VarInsnNode(Opcodes.ALOAD, hexSlot));
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new InsnNode(Opcodes.ICONST_2));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "substring", "(II)Ljava/lang/String;"));
        il.add(new IntInsnNode(Opcodes.BIPUSH, 16));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Integer", "parseInt", "(Ljava/lang/String;I)I"));
        il.add(new InsnNode(Opcodes.I2F));
        il.add(new LdcInsnNode(255.0f));
        il.add(new InsnNode(Opcodes.FDIV));
        il.add(new VarInsnNode(Opcodes.FSTORE, rSlot));

        il.add(new LabelNode());
        il.add(new VarInsnNode(Opcodes.ALOAD, hexSlot));
        il.add(new InsnNode(Opcodes.ICONST_2));
        il.add(new InsnNode(Opcodes.ICONST_4));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "substring", "(II)Ljava/lang/String;"));
        il.add(new IntInsnNode(Opcodes.BIPUSH, 16));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Integer", "parseInt", "(Ljava/lang/String;I)I"));
        il.add(new InsnNode(Opcodes.I2F));
        il.add(new LdcInsnNode(255.0f));
        il.add(new InsnNode(Opcodes.FDIV));
        il.add(new VarInsnNode(Opcodes.FSTORE, gSlot));

        il.add(new LabelNode());
        il.add(new VarInsnNode(Opcodes.ALOAD, hexSlot));
        il.add(new InsnNode(Opcodes.ICONST_4));
        il.add(new IntInsnNode(Opcodes.BIPUSH, 6));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "substring", "(II)Ljava/lang/String;"));
        il.add(new IntInsnNode(Opcodes.BIPUSH, 16));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Integer", "parseInt", "(Ljava/lang/String;I)I"));
        il.add(new InsnNode(Opcodes.I2F));
        il.add(new LdcInsnNode(255.0f));
        il.add(new InsnNode(Opcodes.FDIV));
        il.add(new VarInsnNode(Opcodes.FSTORE, bSlot));

        il.add(new LabelNode());
        il.add(new InsnNode(Opcodes.FCONST_1));
        il.add(new VarInsnNode(Opcodes.FSTORE, aSlot));

        // goto tryStart_C
        il.add(new LabelNode());
        il.add(new JumpInsnNode(Opcodes.GOTO, tryStart_C));

        // ""} else { // if (hex.length() != 8) {"
        // Here is the corresponding bytecode:
        //        aload hex
        //        invokevirtual java/lang/String.length ()I
        //        bipush 8
        //        if_icmpne AH

        LabelNode labelNode_AH = new LabelNode();

        il.add(labelNode_AB);
        il.add(new VarInsnNode(Opcodes.ALOAD, hexSlot));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "length", "()I"));
        il.add(new IntInsnNode(Opcodes.BIPUSH, 8));
        il.add(new JumpInsnNode(Opcodes.IF_ICMPNE, labelNode_AH));

        // If length is 8, it's a full hex code with alpha
        // Here is the corresponding bytecode:
        //    AC:
        //        line 635
        //        aload hex
        //        iconst_0
        //        iconst_2
        //        invokevirtual java/lang/String.substring (II)Ljava/lang/String;
        //        bipush 16
        //        invokestatic java/lang/Integer.parseInt (Ljava/lang/String;I)I
        //        i2f
        //        ldc 255F
        //        fdiv
        //        fstore r
        //    AD:
        //        line 636
        //        aload hex
        //        iconst_2
        //        iconst_4
        //        invokevirtual java/lang/String.substring (II)Ljava/lang/String;
        //        bipush 16
        //        invokestatic java/lang/Integer.parseInt (Ljava/lang/String;I)I
        //        i2f
        //        ldc 255F
        //        fdiv
        //        fstore g
        //    AE:
        //        line 637
        //        aload hex
        //        iconst_4
        //        bipush 6
        //        invokevirtual java/lang/String.substring (II)Ljava/lang/String;
        //        bipush 16
        //        invokestatic java/lang/Integer.parseInt (Ljava/lang/String;I)I
        //        i2f
        //        ldc 255F
        //        fdiv
        //        fstore b
        //    AF:
        //        line 638
        //        aload hex
        //        bipush 6
        //        bipush 8
        //        invokevirtual java/lang/String.substring (II)Ljava/lang/String;
        //        bipush 16
        //        invokestatic java/lang/Integer.parseInt (Ljava/lang/String;I)I
        //        i2f
        //        ldc 255F
        //        fdiv
        //        fstore a

        il.add(new LabelNode());
        il.add(new VarInsnNode(Opcodes.ALOAD, hexSlot));
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new InsnNode(Opcodes.ICONST_2));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "substring", "(II)Ljava/lang/String;"));
        il.add(new IntInsnNode(Opcodes.BIPUSH, 16));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Integer", "parseInt", "(Ljava/lang/String;I)I"));
        il.add(new InsnNode(Opcodes.I2F));
        il.add(new LdcInsnNode(255.0f));
        il.add(new InsnNode(Opcodes.FDIV));
        il.add(new VarInsnNode(Opcodes.FSTORE, rSlot));

        il.add(new LabelNode());
        il.add(new VarInsnNode(Opcodes.ALOAD, hexSlot));
        il.add(new InsnNode(Opcodes.ICONST_2));
        il.add(new InsnNode(Opcodes.ICONST_4));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "substring", "(II)Ljava/lang/String;"));
        il.add(new IntInsnNode(Opcodes.BIPUSH, 16));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Integer", "parseInt", "(Ljava/lang/String;I)I"));
        il.add(new InsnNode(Opcodes.I2F));
        il.add(new LdcInsnNode(255.0f));
        il.add(new InsnNode(Opcodes.FDIV));
        il.add(new VarInsnNode(Opcodes.FSTORE, gSlot));

        il.add(new LabelNode());
        il.add(new VarInsnNode(Opcodes.ALOAD, hexSlot));
        il.add(new InsnNode(Opcodes.ICONST_4));
        il.add(new IntInsnNode(Opcodes.BIPUSH, 6));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "substring", "(II)Ljava/lang/String;"));
        il.add(new IntInsnNode(Opcodes.BIPUSH, 16));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Integer", "parseInt", "(Ljava/lang/String;I)I"));
        il.add(new InsnNode(Opcodes.I2F));
        il.add(new LdcInsnNode(255.0f));
        il.add(new InsnNode(Opcodes.FDIV));
        il.add(new VarInsnNode(Opcodes.FSTORE, bSlot));

        il.add(new LabelNode());
        il.add(new VarInsnNode(Opcodes.ALOAD, hexSlot));
        il.add(new IntInsnNode(Opcodes.BIPUSH, 6));
        il.add(new IntInsnNode(Opcodes.BIPUSH, 8));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "substring", "(II)Ljava/lang/String;"));
        il.add(new IntInsnNode(Opcodes.BIPUSH, 16));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Integer", "parseInt", "(Ljava/lang/String;I)I"));
        il.add(new InsnNode(Opcodes.I2F));
        il.add(new LdcInsnNode(255.0f));
        il.add(new InsnNode(Opcodes.FDIV));
        il.add(new VarInsnNode(Opcodes.FSTORE, aSlot));

        // goto tryStart_C
        il.add(new LabelNode());
        il.add(new JumpInsnNode(Opcodes.GOTO, tryStart_C));

        // Prepare colorIntegerValue for ending try/catch block: A
        // Here is the corresponding bytecode:
        //    AH:
        //        line 640
        //        aload this
        //        getfield ColorClass.colorIntegerValue I
        //        bipush 16
        //        ishr
        //        sipush 255
        //        iand
        //        istore i10

        il.add(labelNode_AH);
        int f_i_10Slot = methodNode.maxLocals++;
        if(colorClassType == 1/* 5.2+ */) {
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new FieldInsnNode(Opcodes.GETFIELD, PatchClass.mappings.get("ColorClass"), PatchClass.mappings.get("ColorClass.colorIntegerValue"), "I"));
            il.add(new IntInsnNode(Opcodes.BIPUSH, 16));
            il.add(new InsnNode(Opcodes.ISHR));
            il.add(new IntInsnNode(Opcodes.SIPUSH, 255));
            il.add(new InsnNode(Opcodes.IAND));

        } else /* Pre 5.2+ */ {
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new FieldInsnNode(Opcodes.GETFIELD, PatchClass.mappings.get("ColorClass"), PatchClass.mappings.get("ColorClass.redValue"), "F"));
        }
        il.add(new VarInsnNode(colorClassType == 1 ? Opcodes.ISTORE : Opcodes.FSTORE, f_i_10Slot));

        // We need to close some try/catch blocks first before continuing
        // Here is the corresponding bytecode:
        //    tryEnd_B:
        //        // try-end:     range=[D-AI] handler=AO:java/lang/Throwable
        //        line 644
        //        aload br
        //        invokevirtual java/io/BufferedReader.close ()V
        //    tryEnd_A:
        //        // try-end:     range=[C-AJ] handler=AU:java/io/IOException
        //        line 640
        //        iload i10
        //        ireturn

        il.add(tryEnd_B);
        il.add(new VarInsnNode(Opcodes.ALOAD, brSlot));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/io/BufferedReader", "close", "()V"));
        il.add(tryEnd_A);
        il.add(new VarInsnNode(colorClassType == 1 ? Opcodes.ILOAD : Opcodes.FLOAD, f_i_10Slot));
        il.add(new InsnNode(colorClassType == 1 ? Opcodes.IRETURN : Opcodes.FRETURN));

        // "this.mJV = color = gPj.jxy(a, r, g, b);"
        // Here is the corresponding bytecode:
        //    AK:
        //        // try-start:   range=[AK-AN] handler=AO:java/lang/Throwable
        //        // try-start:   range=[AK-AT] handler=AU:java/io/IOException
        //        line 642
        //        aload this
        //        fload a
        //        fload r
        //        fload g
        //        fload b
        //        invokestatic gPj.jxy (FFFF)I
        //        dup
        //        istore color
        //    AL:
        //        putfield gPj.mJV I
        //    AM:
        //        line 643
        //        goto D
        //    AN:
        //        // try-end:     range=[AK-AN] handler=AO:java/lang/Throwable
        //        line 644
        //        aload br
        //        invokevirtual java/io/BufferedReader.close ()V
        //        goto AT

        il.add(tryStart_C); il.add(tryStart_D);
        if (colorClassType == 1/* 5.2+ */) {
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new VarInsnNode(Opcodes.FLOAD, aSlot));
            il.add(new VarInsnNode(Opcodes.FLOAD, rSlot));
            il.add(new VarInsnNode(Opcodes.FLOAD, gSlot));
            il.add(new VarInsnNode(Opcodes.FLOAD, bSlot));
            il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, PatchClass.mappings.get("ColorClass"), PatchClass.mappings.get("ColorClass.convertRGBtoInt"), "(FFFF)I"));
            il.add(new InsnNode(Opcodes.DUP));
            int colorSlot = methodNode.maxLocals++;
            il.add(new VarInsnNode(Opcodes.ISTORE, colorSlot));
            il.add(new LabelNode());
            il.add(new FieldInsnNode(Opcodes.PUTFIELD, PatchClass.mappings.get("ColorClass"), PatchClass.mappings.get("ColorClass.colorIntegerValue"), "I"));
        } else /* Pre 5.2 */ {
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new VarInsnNode(Opcodes.FLOAD, rSlot));
            il.add(new FieldInsnNode(Opcodes.PUTFIELD, PatchClass.mappings.get("ColorClass"), PatchClass.mappings.get("ColorClass.redValue"), "F"));
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new VarInsnNode(Opcodes.FLOAD, gSlot));
            il.add(new FieldInsnNode(Opcodes.PUTFIELD, PatchClass.mappings.get("ColorClass"), PatchClass.mappings.get("ColorClass.greenValue"), "F"));
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new VarInsnNode(Opcodes.FLOAD, bSlot));
            il.add(new FieldInsnNode(Opcodes.PUTFIELD, PatchClass.mappings.get("ColorClass"), PatchClass.mappings.get("ColorClass.blueValue"), "F"));
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new VarInsnNode(Opcodes.FLOAD, aSlot));
            il.add(new FieldInsnNode(Opcodes.PUTFIELD, PatchClass.mappings.get("ColorClass"), PatchClass.mappings.get("ColorClass.alphaValue"), "F"));
        }
        il.add(new LabelNode());
        il.add(new JumpInsnNode(Opcodes.GOTO, returnLabel)); //  It was tryStart_B
        il.add(tryEnd_C);il.add(labelNode_AN);
        il.add(new VarInsnNode(Opcodes.ALOAD, brSlot));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/io/BufferedReader", "close", "()V"));
        il.add(new JumpInsnNode(Opcodes.GOTO, tryEnd_D));

        // Continue with the try/catch handlers
        il.add(tryHandler_B); il.add(tryHandler_C);
        int v2Slot = methodNode.maxLocals++;
        il.add(new VarInsnNode(Opcodes.ASTORE, v2Slot));

        // And again, continuing to some compiler produced complex try/catch control flow
        // java/lang/Throwable (AP-AQ)
        LabelNode tryStart_E = new LabelNode();
        LabelNode tryEnd_E = new LabelNode();
        LabelNode tryHandler_E = new LabelNode();

        LabelNode labelNode_AS = new LabelNode();

        il.add(tryStart_E);
        il.add(new VarInsnNode(Opcodes.ALOAD, brSlot));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/io/BufferedReader", "close", "()V"));
        il.add(tryEnd_E);
        il.add(new JumpInsnNode(Opcodes.GOTO, labelNode_AS));
        il.add(tryHandler_E);
        int v3Slot = methodNode.maxLocals++;
        il.add(new VarInsnNode(Opcodes.ASTORE, v3Slot));
        il.add(new VarInsnNode(Opcodes.ALOAD, v2Slot));
        il.add(new VarInsnNode(Opcodes.ALOAD, v3Slot));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Throwable", "addSuppressed", "(Ljava/lang/Throwable;)V"));
        il.add(labelNode_AS);
        il.add(new VarInsnNode(Opcodes.ALOAD, v2Slot));
        il.add(new InsnNode(Opcodes.ATHROW));
        il.add(tryEnd_D);
        il.add(new JumpInsnNode(Opcodes.GOTO, returnLabel));
        il.add(tryHandler_A); il.add(tryHandler_D);
        int v1Slot = methodNode.maxLocals++;
        il.add(new VarInsnNode(Opcodes.ASTORE, v1Slot));

        // returnLabel
        il.add(returnLabel);

        // Finally, we need to add all the try/catch blocks to methodNode's tryCatchBlocks list
        methodNode.tryCatchBlocks.add(new TryCatchBlockNode(tryStart_A, tryEnd_A, tryHandler_A, "java/io/IOException"));
        methodNode.tryCatchBlocks.add(new TryCatchBlockNode(tryStart_B, tryEnd_B, tryHandler_B, "java/lang/Throwable"));
        methodNode.tryCatchBlocks.add(new TryCatchBlockNode(tryStart_C, tryEnd_C, tryHandler_C, "java/lang/Throwable"));
        methodNode.tryCatchBlocks.add(new TryCatchBlockNode(tryStart_D, tryEnd_D, tryHandler_D, "java/io/IOException"));
        methodNode.tryCatchBlocks.add(new TryCatchBlockNode(tryStart_E, tryEnd_E, tryHandler_E, "java/lang/Throwable"));


        // Here is the written Java code as a whole, of written bytecode, as a reference:
        /*
        if (this.colorName == null) return this.mJV >> 16 & 0xFF;
        if (System.currentTimeMillis() - this.lastColorFetch <= 3000L) return this.mJV >> 16 & 0xFF;
        this.lastColorFetch = System.currentTimeMillis();
        try (BufferedReader br = new BufferedReader(new FileReader("theme.bte"));){
            String line;
            while ((line = br.readLine()) != null) {
                int color;
                float a;
                float b;
                float g;
                float r;
                int idx = line.indexOf(": ");
                if (idx == -1) continue;
                String hex = line.substring(idx + 2).split("//")[0].trim();
                if ((line = line.trim()).startsWith("//") || line.isEmpty() || !hex.startsWith("#") || this.colorName == null || !line.startsWith(this.colorName)) continue;
                if ((hex = hex.substring(1)).length() == 3) {
                    r = (float)Integer.parseInt(hex.substring(0, 1) + hex.substring(0, 1), 16) / 255.0f;
                    g = (float)Integer.parseInt(hex.substring(1, 2) + hex.substring(1, 2), 16) / 255.0f;
                    b = (float)Integer.parseInt(hex.substring(2, 3) + hex.substring(2, 3), 16) / 255.0f;
                    a = 1.0f;
                } else if (hex.length() == 4) {
                    r = (float)Integer.parseInt(hex.substring(0, 1) + hex.substring(0, 1), 16) / 255.0f;
                    g = (float)Integer.parseInt(hex.substring(1, 2) + hex.substring(1, 2), 16) / 255.0f;
                    b = (float)Integer.parseInt(hex.substring(2, 3) + hex.substring(2, 3), 16) / 255.0f;
                    a = (float)Integer.parseInt(hex.substring(3, 4) + hex.substring(3, 4), 16) / 255.0f;
                } else if (hex.length() == 6) {
                    r = (float)Integer.parseInt(hex.substring(0, 2), 16) / 255.0f;
                    g = (float)Integer.parseInt(hex.substring(2, 4), 16) / 255.0f;
                    b = (float)Integer.parseInt(hex.substring(4, 6), 16) / 255.0f;
                    a = 1.0f;
                } else {
                    if (hex.length() != 8) {
                        int n = this.mJV >> 16 & 0xFF;
                        return n;
                    }
                    r = (float)Integer.parseInt(hex.substring(0, 2), 16) / 255.0f;
                    g = (float)Integer.parseInt(hex.substring(2, 4), 16) / 255.0f;
                    b = (float)Integer.parseInt(hex.substring(4, 6), 16) / 255.0f;
                    a = (float)Integer.parseInt(hex.substring(6, 8), 16) / 255.0f;
                }
                this.mJV = color = gPj.jxy(a, r, g, b);
            }
            return this.mJV >> 16 & 0xFF;
        }
        catch (IOException iOException) {
            // empty catch block
        }
        return this.mJV >> 16 & 0xFF;
        */
    }
}
