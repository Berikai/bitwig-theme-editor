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
    // Instead, black keys and pitch lane background blend with black by 0.33f (a base color multiplied by 0.33f),
    // and white keys draw a highlight overlay with white at 10% opacity (0.10f)
    // That means these color instances originally have null/unnamed colorName fields
    // We locate both the base Pitches class (lane background & black keys) and Piano Roll keys widget class to name and customize them

    protected ClassNode pitchesClassNode;
    protected MethodNode pitchesMethodNode;
    protected ClassNode keysClassNode;
    protected MethodNode keysMethodNode;

    public PianoRollPatchClass(HashMap<String, ClassNode> classNodes) {
        // Call super class constructor
        super(classNodes, "PianoRollClass");

        // Find PitchesClass (black keys and pitch lane background)
        findPitchesClass(classNodes);

        // Find PianoRollKeysClass (white keys highlight overlay)
        findKeysClass(classNodes);

        if (pitchesClassNode == null) {
            System.out.println("ERROR: Pitches base class not found. Your Bitwig Studio version may not be supported.");
            System.out.println(" -> Known working versions: 4.x, 5.x, 6.x");
        }

        if (keysClassNode == null) {
            System.out.println("ERROR: Piano Roll keys widget class not found. Your Bitwig Studio version may not be supported.");
            System.out.println(" -> Known working versions: 4.x, 5.x, 6.x");
        }
    }

    // Find the base Pitches class containing the 0.33f blend calculation for black keys and lane backgrounds
    private void findPitchesClass(HashMap<String, ClassNode> classNodes) {
        String colorClassName = PatchClass.mappings.get("ColorClass");
        if (colorClassName == null) return;

        String targetDesc = "(I)L" + colorClassName + ";";

        outer: for (ClassNode cn : classNodes.values()) {
            for (MethodNode mn : cn.methods) {
                if (mn.desc.equals(targetDesc)) {
                    boolean has033 = false;
                    for (int i = 0; i < mn.instructions.size(); i++) {
                        AbstractInsnNode insn = mn.instructions.get(i);
                        if (insn.getOpcode() == Opcodes.LDC && insn instanceof LdcInsnNode) {
                            if (Float.valueOf(0.33f).equals(((LdcInsnNode) insn).cst)) {
                                has033 = true;
                                break;
                            }
                        }
                    }

                    if (has033) {
                        this.pitchesClassNode = cn;
                        this.pitchesMethodNode = mn;
                        PatchClass.mappings.put("PitchesClass", cn.name);
                        PatchClass.mappings.put("PitchesClass.getPitchColor", mn.name);
                        JarNode.getModifiedNodes().put(cn.name, cn);
                        break outer;
                    }
                }
            }
        }
    }

    // Find the Piano Roll keys widget class by tracing the provider class that returns "Pitches"
    private void findKeysClass(HashMap<String, ClassNode> classNodes) {
        // 1. Find the provider class that returns string constant "Pitches"
        ClassNode providerNode = null;
        for (ClassNode cn : classNodes.values()) {
            if (cn.name.startsWith("com/bitwig/flt/packaging")) continue;
            for (MethodNode mn : cn.methods) {
                for (int i = 0; i < mn.instructions.size(); i++) {
                    AbstractInsnNode insn = mn.instructions.get(i);
                    if (insn.getOpcode() == Opcodes.LDC && insn instanceof LdcInsnNode) {
                        if ("Pitches".equals(((LdcInsnNode) insn).cst)) {
                            providerNode = cn;
                            break;
                        }
                    }
                }
                if (providerNode != null) break;
            }
            if (providerNode != null) break;
        }

        if (providerNode == null) return;

        // 2. In provider methods, find the invokestatic call that instantiates the keys widget
        String widgetClassName = null;
        for (MethodNode mn : providerNode.methods) {
            for (int i = 0; i < mn.instructions.size(); i++) {
                AbstractInsnNode insn = mn.instructions.get(i);
                if (insn.getOpcode() == Opcodes.INVOKESTATIC && insn instanceof MethodInsnNode) {
                    MethodInsnNode minsn = (MethodInsnNode) insn;
                    if (!minsn.owner.equals(providerNode.name) && classNodes.containsKey(minsn.owner)) {
                        widgetClassName = minsn.owner;
                        break;
                    }
                }
            }
            if (widgetClassName != null) break;
        }

        if (widgetClassName == null) return;
        ClassNode widgetNode = classNodes.get(widgetClassName);
        if (widgetNode == null) return;

        this.keysClassNode = widgetNode;
        PatchClass.mappings.put("PianoRollKeysClass", widgetNode.name);
        JarNode.getModifiedNodes().put(widgetNode.name, widgetNode);

        // 3. Find the render method that contains float constant 0.1f (white keys overlay)
        for (MethodNode mn : widgetNode.methods) {
            for (int i = 0; i < mn.instructions.size(); i++) {
                AbstractInsnNode insn = mn.instructions.get(i);
                if (insn.getOpcode() == Opcodes.LDC && insn instanceof LdcInsnNode) {
                    if (Float.valueOf(0.1f).equals(((LdcInsnNode) insn).cst)) {
                        this.keysMethodNode = mn;
                        PatchClass.mappings.put("PianoRollKeysClass.renderWhiteKeys", mn.name);
                        return;
                    }
                }
            }
        }
    }

    @Override
    protected void findMethodNode() {
        // Method discovery handled in findPitchesClass and findKeysClass
    }

    // The code we will inject here is responsible for naming the Piano Roll colors
    // 1. In PitchesClass.getPitchColor: inject colorInstance.colorName = "Piano Roll Black Key" before ARETURN
    // 2. In PianoRollKeysClass: inject colorInstance.colorName = "Piano Roll White Key" after ColorClass.withAlpha(0.1f)
    @Override
    public void patch() {
        String colorClassName = PatchClass.mappings.get("ColorClass");
        if (colorClassName == null) return;

        if (pitchesMethodNode != null) {
            patchPitchesMethod(colorClassName);
        }

        if (keysMethodNode != null) {
            patchKeysMethod(colorClassName);
        }
    }

    // Patch PitchesClass method to name black keys and pitch lane backgrounds
    private void patchPitchesMethod(String colorClassName) {
        List<AbstractInsnNode> returns = new ArrayList<>();
        for (int i = pitchesMethodNode.instructions.size() - 1; i >= 0; i--) {
            AbstractInsnNode insn = pitchesMethodNode.instructions.get(i);
            if (insn.getOpcode() == Opcodes.ARETURN) {
                returns.add(insn);
            }
        }

        for (AbstractInsnNode ret : returns) {
            // Here is the java code:
            //    colorInstance.colorName = "Piano Roll Black Key";
            // And, here is the corresponding bytecode:
            //    aload colorInstance (already on top of stack from blend calculation)
            //    dup
            //    ldc "Piano Roll Black Key"
            //    putfield ColorClass.colorName Ljava/lang/String;

            InsnList il = new InsnList();
            il.add(new LabelNode());
            il.add(new InsnNode(Opcodes.DUP));
            il.add(new LdcInsnNode("Piano Roll Black Key"));
            il.add(new FieldInsnNode(Opcodes.PUTFIELD, colorClassName, "colorName", "Ljava/lang/String;"));
            pitchesMethodNode.instructions.insertBefore(ret, il);
        }
    }

    // Patch PianoRollKeysClass method to name white keys overlay
    private void patchKeysMethod(String colorClassName) {
        String withAlphaDesc = "(F)L" + colorClassName + ";";
        AbstractInsnNode targetInsn = null;

        for (int i = 0; i < keysMethodNode.instructions.size(); i++) {
            AbstractInsnNode insn = keysMethodNode.instructions.get(i);
            if (insn.getOpcode() == Opcodes.LDC && insn instanceof LdcInsnNode) {
                if (Float.valueOf(0.1f).equals(((LdcInsnNode) insn).cst)) {
                    // The next instruction should be invokevirtual ColorClass.<withAlpha>:(F)LColorClass;
                    for (int j = i + 1; j < Math.min(keysMethodNode.instructions.size(), i + 4); j++) {
                        AbstractInsnNode next = keysMethodNode.instructions.get(j);
                        if (next.getOpcode() == Opcodes.INVOKEVIRTUAL && next instanceof MethodInsnNode) {
                            MethodInsnNode minsn = (MethodInsnNode) next;
                            if (minsn.desc.equals(withAlphaDesc)) {
                                targetInsn = next;
                                break;
                            }
                        }
                    }
                    if (targetInsn != null) break;
                }
            }
        }

        if (targetInsn != null) {
            // Here is the java code:
            //    colorInstance.colorName = "Piano Roll White Key";
            // And, here is the corresponding bytecode:
            //    aload colorInstance (returned by withAlpha method call, on top of stack)
            //    dup
            //    ldc "Piano Roll White Key"
            //    putfield ColorClass.colorName Ljava/lang/String;

            InsnList il = new InsnList();
            il.add(new LabelNode());
            il.add(new InsnNode(Opcodes.DUP));
            il.add(new LdcInsnNode("Piano Roll White Key"));
            il.add(new FieldInsnNode(Opcodes.PUTFIELD, colorClassName, "colorName", "Ljava/lang/String;"));
            keysMethodNode.instructions.insert(targetInsn, il);
        }
    }
}
