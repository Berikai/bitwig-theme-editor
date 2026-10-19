package dev.berikai.BitwigTheme.core.impl;

import dev.berikai.BitwigTheme.asm.JarNode;
import dev.berikai.BitwigTheme.core.PatchClass;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class NoteFXPatchClass extends PatchClass {
    // Note: In Bitwig Studio, Note FX chains (and container device chains) don't originally use named theme palette colors
    // Instead, they use a procedural HSB formula to calculate colors based on chain index (200f - (i - 1) * 70f) % 360f
    // That means these colors never touch ColorBridge, and their colorName was originally null/unnamed
    // We refer to this device chain color generator class as NoteFXClass

    public NoteFXPatchClass(HashMap<String, ClassNode> classNodes) {
        // Call super class constructor
        super(classNodes, "NoteFXClass");

        // Find the class that contains the unique HSB float constants 200.0f and 70.0f
        // This device chain helper class is the only class that contains both constants across all versions, which makes our lives easier
        findClassNode(classNodes);

        // If classNode is null, inform the user
        if (classNode == null) {
            System.out.println("ERROR: NoteFX class not found. Your Bitwig Studio version may not be supported.");
            System.out.println(" -> Known working versions: 4.x, 5.x, 6.x");
            return;
        }

        // Find the method node that we will be injecting code into
        findMethodNode();
    }

    // Find the class containing the procedural HSB constants (200.0f and 70.0f)
    private void findClassNode(HashMap<String, ClassNode> classNodes) {
        outer: for (ClassNode cn : classNodes.values()) {
            for (MethodNode mn : cn.methods) {
                boolean has200 = false;
                boolean has70 = false;
                for (int i = 0; i < mn.instructions.size(); i++) {
                    AbstractInsnNode insn = mn.instructions.get(i);
                    if (insn.getOpcode() == Opcodes.LDC && insn instanceof LdcInsnNode) {
                        Object cst = ((LdcInsnNode) insn).cst;
                        if (Float.valueOf(200.0f).equals(cst)) has200 = true;
                        if (Float.valueOf(70.0f).equals(cst)) has70 = true;
                    }
                }
                if (has200 && has70) {
                    this.classNode = cn;
                    PatchClass.mappings.put("NoteFXClass", cn.name);
                    JarNode.getModifiedNodes().put(cn.name, cn);
                    break outer;
                }
            }
        }
    }

    // Let's find our target method that generates the procedural chain color from chain index
    @Override
    protected void findMethodNode() {
        if (classNode == null) return;

        String colorClassName = PatchClass.mappings.get("ColorClass");
        if (colorClassName == null) return;

        // Target method signature: public static ColorClass (int index)
        String targetDesc = "(I)L" + colorClassName + ";";

        // Iterate through methods of class
        for (MethodNode mn : classNode.methods) {
            // Check if method is static, takes an integer, and returns a ColorClass instance
            if ((mn.access & Opcodes.ACC_STATIC) != 0 && mn.desc.equals(targetDesc)) {
                // Confirm it contains the unique HSB float constant 200.0f
                boolean has200 = false;
                for (int i = 0; i < mn.instructions.size(); i++) {
                    AbstractInsnNode insn = mn.instructions.get(i);
                    if (insn.getOpcode() == Opcodes.LDC && insn instanceof LdcInsnNode) {
                        if (Float.valueOf(200.0f).equals(((LdcInsnNode) insn).cst)) {
                            has200 = true;
                            break;
                        }
                    }
                }

                if (has200) {
                    // Assign found method to the methodNode field of PatchClass
                    this.methodNode = mn;

                    PatchClass.mappings.put("NoteFXClass.getChainColor", this.methodNode.name);
                    break;
                }
            }
        }
    }

    // The code we will inject here is responsible for assigning a name to the procedural color instance
    // We will inject "colorInstance.colorName = "Note FX Chain " + (index + 1);" right before returning the color
    // Once colorName is set, ColorPatchClass will automatically check theme.bte and replace it with user's theme color!
    @Override
    public void patch() {
        if (classNode == null || methodNode == null) {
            System.out.println("WARNING: Skipping Note FX patch (class or method not identified).");
            return;
        }

        String colorClassName = PatchClass.mappings.get("ColorClass");
        if (colorClassName == null) return;

        // Get all return instructions of the method
        List<AbstractInsnNode> returns = new ArrayList<>();
        for (int i = methodNode.instructions.size() - 1; i >= 0; i--) {
            AbstractInsnNode insn = methodNode.instructions.get(i);
            int op = insn.getOpcode();
            if (op == Opcodes.ARETURN) {
                returns.add(insn);
            }
        }

        // Insert bytecode before each return
        for (AbstractInsnNode ret : returns) {
            InsnList il = new InsnList();
            writeBytecode(il, colorClassName);
            methodNode.instructions.insertBefore(ret, il);
        }
    }

    // --------- Bytecode writing section ---------

    // Write bytecode to be injected before returning the color
    private void writeBytecode(InsnList il, String colorClassName) {
        // Here is the java code:
        //    colorInstance.colorName = "Note FX Chain " + (index + 1);
        // And, here is the corresponding bytecode:
        //    aload colorInstance (already on top of stack from preceding instructions)
        //    dup
        //    ldc "Note FX Chain "
        //    iload 0 (index)
        //    iconst_1
        //    iadd
        //    invokestatic java/lang/String.valueOf (I)Ljava/lang/String;
        //    invokevirtual java/lang/String.concat (Ljava/lang/String;)Ljava/lang/String;
        //    putfield ColorClass.colorName Ljava/lang/String;

        il.add(new LabelNode());
        il.add(new InsnNode(Opcodes.DUP)); // Duplicate ColorClass instance on top of stack
        il.add(new LdcInsnNode("Note FX Chain "));
        il.add(new VarInsnNode(Opcodes.ILOAD, 0)); // Load chain index (argument 0)
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new InsnNode(Opcodes.IADD)); // index + 1, so chain 0 becomes "Note FX Chain 1"
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/String", "valueOf", "(I)Ljava/lang/String;", false));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "concat", "(Ljava/lang/String;)Ljava/lang/String;", false));
        il.add(new FieldInsnNode(Opcodes.PUTFIELD, colorClassName, "colorName", "Ljava/lang/String;"));
    }
}

