package dev.berikai.BitwigTheme.core.impl;

import dev.berikai.BitwigTheme.asm.JarNode;
import dev.berikai.BitwigTheme.core.PatchClass;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class DynamicsPatchClass extends PatchClass {
    // In Bitwig Studio, the Dynamics device upper display has two sections:
    // Left: Transfer function / compressor curve graph (styled with "Display Background")
    // Right: Gain-reduction / history waveform visualizer ("Frozen" cyan text / scrolling gain graph)
    // The right visualizer draws its background rectangle with a hardcoded dark grey color (0.2f, 0.2f, 0.2f)
    // We locate the widget classes containing the string "Frozen" (atf & aSI in 6.1.5, aMm & anx in 6.0, Hy & aid in 5.3.13, Fq & amZ in 4.3.10)
    // and override the background color with a dedicated "Dynamics Background" key!

    private final List<ClassNode> targetClassNodes = new ArrayList<>();

    public DynamicsPatchClass(HashMap<String, ClassNode> classNodes) {
        super(classNodes, "DynamicsClass");

        findClassNodes(classNodes);

        if (targetClassNodes.isEmpty()) {
            System.out.println("NOTE: Dynamics history visualizer classes not found.");
            return;
        }

        this.classNode = targetClassNodes.get(0);
        System.out.println(" -> Found " + targetClassNodes.size() + " Dynamics history visualizer classes.");
    }

    private void findClassNodes(HashMap<String, ClassNode> classNodes) {
        for (ClassNode cn : classNodes.values()) {
            boolean hasFrozen = false;
            for (MethodNode mn : cn.methods) {
                for (int i = 0; i < mn.instructions.size(); i++) {
                    AbstractInsnNode insn = mn.instructions.get(i);
                    if (insn.getOpcode() == Opcodes.LDC && insn instanceof LdcInsnNode) {
                        if ("Frozen".equals(((LdcInsnNode) insn).cst)) {
                            hasFrozen = true;
                            break;
                        }
                    }
                }
                if (hasFrozen) break;
            }
            if (hasFrozen) {
                targetClassNodes.add(cn);
                JarNode.getModifiedNodes().put(cn.name, cn);
            }
        }
    }

    @Override
    protected void findMethodNode() {
        // Multi-class patch: each target class is handled directly in patch()
    }

    @Override
    public void patch() {
        String colorClassName = PatchClass.mappings.get("ColorClass");
        String getColorMethod = PatchClass.mappings.get("ColorClass.getColorMethod");
        String getColorDesc = PatchClass.mappings.get("ColorClass.getColorDesc");

        if (colorClassName == null || getColorMethod == null || getColorDesc == null) {
            return;
        }

        String colorDesc = "L" + colorClassName + ";";
        int patchedCount = 0;

        for (ClassNode cn : targetClassNodes) {
            for (MethodNode mn : cn.methods) {
                // Must be a non-static paint method taking 1 graphics parameter and returning void
                if ((mn.access & Opcodes.ACC_STATIC) != 0) continue;
                if (!mn.desc.endsWith(")V") || mn.desc.indexOf(';') == -1) continue;

                // Look for the call that returns ColorClass right before setColor(ColorClass)
                for (int i = 0; i < mn.instructions.size(); i++) {
                    AbstractInsnNode insn = mn.instructions.get(i);
                    if (insn instanceof MethodInsnNode) {
                        MethodInsnNode minsn = (MethodInsnNode) insn;
                        if (minsn.desc.endsWith("()" + colorDesc)) {
                            // Check if followed by setColor(ColorClass)
                            boolean followedBySetColor = false;
                            for (int j = i + 1; j < Math.min(mn.instructions.size(), i + 6); j++) {
                                AbstractInsnNode next = mn.instructions.get(j);
                                if (next instanceof MethodInsnNode) {
                                    MethodInsnNode nmin = (MethodInsnNode) next;
                                    if (nmin.desc.contains("(" + colorDesc + ")")) {
                                        followedBySetColor = true;
                                        break;
                                    }
                                }
                            }

                            if (followedBySetColor) {
                                // Stack has: [..., colorInstance]
                                // Override color with dedicated "Dynamics Background" key
                                InsnList hook = new InsnList();
                                appendColorLookup(hook, colorClassName, getColorMethod, getColorDesc, "Dynamics Background");
                                mn.instructions.insert(minsn, hook);
                                patchedCount++;
                                break; // Only hook the background fill of the widget
                            }
                        }
                    }
                }
            }
        }

        if (patchedCount > 0) {
            System.out.println(" -> Successfully patched " + patchedCount + " Dynamics history background hooks.");
        }
    }
}
