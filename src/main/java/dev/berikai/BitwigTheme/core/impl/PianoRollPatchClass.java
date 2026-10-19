package dev.berikai.BitwigTheme.core.impl;

import dev.berikai.BitwigTheme.asm.JarNode;
import dev.berikai.BitwigTheme.core.PatchClass;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class PianoRollPatchClass extends PatchClass {
    // Note: In Bitwig Studio, Piano Roll keys don't originally use named theme palette colors
    // Instead, black keys use hardcoded dark colors (0.1f), and white keys use light colors (0.7f)
    // That means these color instances originally have null/unnamed colorName fields
    // We locate the Piano Roll keyboard widget class to name and customize them

    public PianoRollPatchClass(HashMap<String, ClassNode> classNodes) {
        // Call super class constructor
        super(classNodes, "PianoRollClass");

        // Find the Piano Roll keys widget class (Apa in 6.1.5, nLG in 6.0, bZW in 5.3.13, yzC in 4.3.10)
        findClassNode(classNodes);

        // If classNode is null, inform the user
        if (classNode == null) {
            System.out.println("ERROR: Piano Roll keys widget class not found. Your Bitwig Studio version may not be supported.");
            System.out.println(" -> Known working versions: 4.x, 5.x, 6.x");
            return;
        }

        // Find the method node that we will be injecting code into
        findMethodNode();
    }

    // Find the Piano Roll keys widget class (contains 0.7f and 0.1f in timeline package)
    private void findClassNode(HashMap<String, ClassNode> classNodes) {
        String prefix = "com/bitwig/flt/widget/core/timeline/";

        outer: for (ClassNode cn : classNodes.values()) {
            // Must be a direct class in timeline package (exclude subpackages like interaction/renderer and inner classes)
            if (!cn.name.startsWith(prefix) || cn.name.substring(prefix.length()).contains("/") || cn.name.contains("$")) {
                continue;
            }

            for (MethodNode mn : cn.methods) {
                boolean has07 = false;
                boolean has01 = false;

                for (int i = 0; i < mn.instructions.size(); i++) {
                    AbstractInsnNode insn = mn.instructions.get(i);
                    if (insn.getOpcode() == Opcodes.LDC && insn instanceof LdcInsnNode) {
                        Object cst = ((LdcInsnNode) insn).cst;
                        if (Float.valueOf(0.7f).equals(cst)) has07 = true;
                        if (Float.valueOf(0.1f).equals(cst)) has01 = true;
                    }
                }

                if (has07 && has01) {
                    this.classNode = cn;
                    PatchClass.mappings.put("PianoRollKeysClass", cn.name);
                    JarNode.getModifiedNodes().put(cn.name, cn);
                    break outer;
                }
            }
        }
    }

    // Let's find our target method that paints the piano roll keys
    @Override
    protected void findMethodNode() {
        if (classNode == null) return;

        for (MethodNode mn : classNode.methods) {
            boolean has07 = false;
            boolean has01 = false;

            for (int i = 0; i < mn.instructions.size(); i++) {
                AbstractInsnNode insn = mn.instructions.get(i);
                if (insn.getOpcode() == Opcodes.LDC && insn instanceof LdcInsnNode) {
                    Object cst = ((LdcInsnNode) insn).cst;
                    if (Float.valueOf(0.7f).equals(cst)) has07 = true;
                    if (Float.valueOf(0.1f).equals(cst)) has01 = true;
                }
            }

            if (has07 && has01) {
                this.methodNode = mn;
                PatchClass.mappings.put("PianoRollKeysClass.paintMethod", mn.name);
                break;
            }
        }
    }

    // The code we will inject here is responsible for naming the Piano Roll colors
    // In keys render method: inject colorInstance.colorName after Color.<init>(FFF)V
    // We also call getColorMethod to immediately load the color from theme.bte
    @Override
    public void patch() {
        if (classNode == null || methodNode == null) {
            System.out.println("WARNING: Skipping Piano Roll patch (class or method not identified).");
            return;
        }

        String colorClassName = PatchClass.mappings.get("ColorClass");
        String getColorMethod = PatchClass.mappings.get("ColorClass.getColorMethod");
        String getColorDesc = PatchClass.mappings.get("ColorClass.getColorDesc");

        if (colorClassName == null || getColorMethod == null || getColorDesc == null) {
            System.out.println("WARNING: Skipping Piano Roll patch (ColorClass mappings missing).");
            return;
        }

        List<AbstractInsnNode> targets = new ArrayList<>();
        List<String> targetNames = new ArrayList<>();

        for (int i = 0; i < methodNode.instructions.size(); i++) {
            AbstractInsnNode insn = methodNode.instructions.get(i);
            if (insn.getOpcode() == Opcodes.INVOKESPECIAL && insn instanceof MethodInsnNode) {
                MethodInsnNode minsn = (MethodInsnNode) insn;
                if (minsn.owner.equals(colorClassName) && minsn.name.equals("<init>") && minsn.desc.equals("(FFF)V")) {
                    Float floatCst = null;
                    for (int j = i - 1; j >= Math.max(0, i - 10); j--) {
                        AbstractInsnNode prev = methodNode.instructions.get(j);
                        if (prev.getOpcode() == Opcodes.LDC && prev instanceof LdcInsnNode) {
                            Object cst = ((LdcInsnNode) prev).cst;
                            if (cst instanceof Float) {
                                floatCst = (Float) cst;
                                break;
                            }
                        }
                    }

                    if (floatCst != null) {
                        if (Float.valueOf(0.7f).equals(floatCst)) {
                            targets.add(insn);
                            targetNames.add("Piano Roll White Key");
                        } else if (Float.valueOf(0.1f).equals(floatCst)) {
                            targets.add(insn);
                            targetNames.add("Piano Roll Black Key");
                        }
                    }
                }
            }
        }

        for (int i = 0; i < targets.size(); i++) {
            AbstractInsnNode targetInsn = targets.get(i);
            String colorName = targetNames.get(i);

            InsnList hook = new InsnList();
            writeBytecode(hook, colorClassName, getColorMethod, getColorDesc, colorName);
            methodNode.instructions.insert(targetInsn, hook);
        }

        methodNode.maxStack += 2;
    }

    // --------- Bytecode writing section ---------

    // Write bytecode to name the color and trigger immediate theme fetch
    private void writeBytecode(InsnList il, String colorClassName, String getColorMethod, String getColorDesc, String colorName) {
        // Here is the java code:
        //    colorInstance.colorName = colorName;
        //    colorInstance.getColorInteger(); // or getRed() in pre-5.2 to trigger theme reload
        //
        // And, here is the corresponding bytecode:
        //    aload colorInstance (already on top of stack from Color instantiation)
        //    dup
        //    ldc colorName
        //    putfield ColorClass.colorName Ljava/lang/String;
        //    dup
        //    invokevirtual ColorClass.<getColorMethod> ()<getColorDesc>;
        //    pop

        il.add(new LabelNode());
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new LdcInsnNode(colorName));
        il.add(new FieldInsnNode(Opcodes.PUTFIELD, colorClassName, "colorName", "Ljava/lang/String;"));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, colorClassName, getColorMethod, getColorDesc, false));
        il.add(new InsnNode(Opcodes.POP));
    }
}
