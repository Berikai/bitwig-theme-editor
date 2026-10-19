package dev.berikai.BitwigTheme.core.impl;

import dev.berikai.BitwigTheme.asm.JarNode;
import dev.berikai.BitwigTheme.core.PatchClass;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class PianoRollPatchClass extends PatchClass {
    // Note: In Bitwig Studio, Piano Roll keys and pitch background lanes don't originally use named theme palette colors
    // Instead, black keys and pitch lane backgrounds use hardcoded dark colors (0.1f, 0.2f blend),
    // and white keys draw highlights with white/light colors (0.7f, 0.1f overlay)
    // That means these color instances originally have null/unnamed colorName fields
    // We locate both the Piano Roll keys widget class and the timeline pitch lanes painter class to name and customize them

    protected ClassNode keysClassNode;
    protected MethodNode keysMethodNode;
    protected ClassNode timelineClassNode;
    protected MethodNode timelineMethodNode;

    public PianoRollPatchClass(HashMap<String, ClassNode> classNodes) {
        // Call super class constructor
        super(classNodes, "PianoRollClass");

        // Find the Piano Roll keys widget class and render method (contains 0.7f and 0.1f)
        findKeysClass(classNodes);

        // In Bitwig 6.x, timeline pitch lanes are in a separate painter class (contains 0.2f and 0.1f)
        findTimelineClass(classNodes);

        if (keysClassNode == null) {
            System.out.println("ERROR: Piano Roll keys widget class not found. Your Bitwig Studio version may not be supported.");
            System.out.println(" -> Known working versions: 4.x, 5.x, 6.x");
        }
    }

    // Find the Piano Roll keys widget class (Apa in 6.1.5, nLG in 6.0, bZW in 5.3.13, yzC in 4.3.10)
    private void findKeysClass(HashMap<String, ClassNode> classNodes) {
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
                    this.keysClassNode = cn;
                    this.keysMethodNode = mn;
                    PatchClass.mappings.put("PianoRollKeysClass", cn.name);
                    PatchClass.mappings.put("PianoRollKeysClass.paintMethod", mn.name);
                    JarNode.getModifiedNodes().put(cn.name, cn);
                    break outer;
                }
            }
        }
    }

    // Find the timeline pitch lanes painter class in Bitwig 6.x (CgT in 6.1.5, TrL in 6.0)
    // Note: In 4.x and 5.x, timeline pitch lanes are already rendered inside keysClassNode
    private void findTimelineClass(HashMap<String, ClassNode> classNodes) {
        String prefix = "com/bitwig/flt/widget/core/timeline/";

        outer: for (ClassNode cn : classNodes.values()) {
            if (!cn.name.startsWith(prefix) || cn.name.substring(prefix.length()).contains("/") || cn.name.contains("$")) {
                continue;
            }
            if (cn == this.keysClassNode) {
                continue;
            }

            for (MethodNode mn : cn.methods) {
                boolean has02 = false;
                boolean has01 = false;

                for (int i = 0; i < mn.instructions.size(); i++) {
                    AbstractInsnNode insn = mn.instructions.get(i);
                    if (insn.getOpcode() == Opcodes.LDC && insn instanceof LdcInsnNode) {
                        Object cst = ((LdcInsnNode) insn).cst;
                        if (Float.valueOf(0.2f).equals(cst)) has02 = true;
                        if (Float.valueOf(0.1f).equals(cst)) has01 = true;
                    }
                }

                if (has02 && has01) {
                    this.timelineClassNode = cn;
                    this.timelineMethodNode = mn;
                    PatchClass.mappings.put("PianoRollTimelineClass", cn.name);
                    PatchClass.mappings.put("PianoRollTimelineClass.paintMethod", mn.name);
                    JarNode.getModifiedNodes().put(cn.name, cn);
                    break outer;
                }
            }
        }
    }

    @Override
    protected void findMethodNode() {
        // Method discovery is handled dynamically in findKeysClass and findTimelineClass
    }

    // The code we will inject here is responsible for naming the Piano Roll colors
    // 1. In keysClassNode: inject colorInstance.colorName after Color.<init>(FFF)V
    // 2. In timelineClassNode: inject colorInstance.colorName after Color.withAlpha(F)
    // In both cases, we also call getColorMethod to immediately load the color from theme.bte
    @Override
    public void patch() {
        String colorClassName = PatchClass.mappings.get("ColorClass");
        String getColorMethod = PatchClass.mappings.get("ColorClass.getColorMethod");
        String getColorDesc = PatchClass.mappings.get("ColorClass.getColorDesc");

        if (colorClassName == null || getColorMethod == null || getColorDesc == null) {
            System.out.println("WARNING: Skipping Piano Roll patch (ColorClass mappings missing).");
            return;
        }

        if (keysMethodNode != null) {
            patchKeysMethod(colorClassName, getColorMethod, getColorDesc);
        }

        if (timelineMethodNode != null) {
            patchTimelineMethod(colorClassName, getColorMethod, getColorDesc);
        }
    }

    // Patch Piano Roll keys widget method (constructs Color instances with 0.7f, 0.1f, and 0.2f)
    private void patchKeysMethod(String colorClassName, String getColorMethod, String getColorDesc) {
        List<AbstractInsnNode> targets = new ArrayList<>();
        List<String> targetNames = new ArrayList<>();

        for (int i = 0; i < keysMethodNode.instructions.size(); i++) {
            AbstractInsnNode insn = keysMethodNode.instructions.get(i);
            if (insn.getOpcode() == Opcodes.INVOKESPECIAL && insn instanceof MethodInsnNode) {
                MethodInsnNode minsn = (MethodInsnNode) insn;
                if (minsn.owner.equals(colorClassName) && minsn.name.equals("<init>") && minsn.desc.equals("(FFF)V")) {
                    Float floatCst = null;
                    for (int j = i - 1; j >= Math.max(0, i - 10); j--) {
                        AbstractInsnNode prev = keysMethodNode.instructions.get(j);
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
                        } else if (Float.valueOf(0.1f).equals(floatCst) || Float.valueOf(0.2f).equals(floatCst)) {
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
            keysMethodNode.instructions.insert(targetInsn, hook);
        }

        keysMethodNode.maxStack += 2;
    }

    // Patch timeline pitch lanes painter method (calls Color.withAlpha with 0.2f and 0.1f)
    private void patchTimelineMethod(String colorClassName, String getColorMethod, String getColorDesc) {
        String withAlphaDesc = "(F)L" + colorClassName + ";";
        List<AbstractInsnNode> targets = new ArrayList<>();
        List<String> targetNames = new ArrayList<>();

        for (int i = 0; i < timelineMethodNode.instructions.size(); i++) {
            AbstractInsnNode insn = timelineMethodNode.instructions.get(i);
            if (insn.getOpcode() == Opcodes.INVOKEVIRTUAL && insn instanceof MethodInsnNode) {
                MethodInsnNode minsn = (MethodInsnNode) insn;
                if (minsn.owner.equals(colorClassName) && minsn.desc.equals(withAlphaDesc)) {
                    Float floatCst = null;
                    for (int j = i - 1; j >= Math.max(0, i - 5); j--) {
                        AbstractInsnNode prev = timelineMethodNode.instructions.get(j);
                        if (prev.getOpcode() == Opcodes.LDC && prev instanceof LdcInsnNode) {
                            Object cst = ((LdcInsnNode) prev).cst;
                            if (cst instanceof Float) {
                                floatCst = (Float) cst;
                                break;
                            }
                        }
                    }

                    if (floatCst != null) {
                        if (Float.valueOf(0.1f).equals(floatCst)) {
                            targets.add(insn);
                            targetNames.add("Piano Roll White Key");
                        } else if (Float.valueOf(0.2f).equals(floatCst)) {
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
            timelineMethodNode.instructions.insert(targetInsn, hook);
        }

        timelineMethodNode.maxStack += 2;
    }

    // --------- Bytecode writing section ---------

    // Write bytecode to name the color and trigger immediate theme fetch
    private void writeBytecode(InsnList il, String colorClassName, String getColorMethod, String getColorDesc, String colorName) {
        // Here is the java code:
        //    colorInstance.colorName = colorName;
        //    colorInstance.getColorInteger(); // or getRed() in pre-5.2 to trigger theme reload
        //
        // And, here is the corresponding bytecode:
        //    aload colorInstance (already on top of stack from instantiation or withAlpha)
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
