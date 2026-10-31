package dev.berikai.BitwigTheme.core.impl;

import dev.berikai.BitwigTheme.asm.JarNode;
import dev.berikai.BitwigTheme.core.PatchClass;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class SamplerPianoPatchClass extends PatchClass {
    // Note: In Bitwig Studio, the Sampler device has its own horizontal 128-key keyboard widget.
    // Unlike the Note Editor piano roll (which uses vertical keys), the Sampler keyboard instantiates
    // hardcoded Color instances for active/inactive white/black keys and octave text.
    // Across Bitwig 4.3.10 through 6.1.5, this class is uniquely fingerprinted by IEEE-754 float
    // constants 0.55f (key geometry width ratio) and 0.8f (black key height ratio).

    protected MethodNode keyMethodNode;
    protected MethodNode octaveMethodNode;

    public SamplerPianoPatchClass(HashMap<String, ClassNode> classNodes) {
        super(classNodes, "SamplerPianoClass");
        findClassNode(classNodes);

        if (classNode == null) {
            System.out.println("WARNING: Sampler keyboard class not found. Skipping Sampler piano patch.");
            return;
        }

        findMethodNode();
    }

    private void findClassNode(HashMap<String, ClassNode> classNodes) {
        outer: for (ClassNode cn : classNodes.values()) {
            boolean has055 = false;
            boolean has080 = false;

            for (MethodNode mn : cn.methods) {
                for (int i = 0; i < mn.instructions.size(); i++) {
                    AbstractInsnNode insn = mn.instructions.get(i);
                    if (insn.getOpcode() == Opcodes.LDC && insn instanceof LdcInsnNode) {
                        Object cst = ((LdcInsnNode) insn).cst;
                        if (Float.valueOf(0.55f).equals(cst)) has055 = true;
                        if (Float.valueOf(0.8f).equals(cst)) has080 = true;
                    }
                }
            }

            if (has055 && has080) {
                this.classNode = cn;
                PatchClass.mappings.put("SamplerPianoClass", cn.name);
                break outer;
            }
        }
    }

    @Override
    protected void findMethodNode() {
        if (classNode == null) return;

        for (MethodNode mn : classNode.methods) {
            if (mn.desc.endsWith("DIDI)V")) {
                // Key painting method
                boolean has07 = false;
                for (int i = 0; i < mn.instructions.size(); i++) {
                    AbstractInsnNode insn = mn.instructions.get(i);
                    if (insn.getOpcode() == Opcodes.LDC && insn instanceof LdcInsnNode) {
                        if (Float.valueOf(0.7f).equals(((LdcInsnNode) insn).cst)) {
                            has07 = true;
                            break;
                        }
                    }
                }
                if (has07) {
                    this.keyMethodNode = mn;
                    this.methodNode = mn; // primary method node
                    PatchClass.mappings.put("SamplerPianoClass.paintKeys", mn.name);
                }
            } else if (mn.desc.endsWith("DIDII)V")) {
                // Octave text painting method
                this.octaveMethodNode = mn;
                PatchClass.mappings.put("SamplerPianoClass.paintOctaveText", mn.name);
            }
        }
    }

    @Override
    public void patch() {
        if (classNode == null || keyMethodNode == null) {
            System.out.println("WARNING: Skipping Sampler piano patch (class or key paint method not identified).");
            return;
        }

        String colorClassName = PatchClass.mappings.get("ColorClass");
        String getColorMethod = PatchClass.mappings.get("ColorClass.getColorMethod");
        String getColorDesc = PatchClass.mappings.get("ColorClass.getColorDesc");

        if (colorClassName == null || getColorMethod == null || getColorDesc == null) {
            System.out.println("WARNING: Skipping Sampler piano patch (ColorClass mappings missing).");
            return;
        }

        boolean patchedAny = false;

        // 1. Patch key painting method
        List<AbstractInsnNode> keyTargets = new ArrayList<>();
        List<String> keyTargetNames = new ArrayList<>();
        List<String> keyFallbackNames = new ArrayList<>();

        for (int i = 0; i < keyMethodNode.instructions.size(); i++) {
            AbstractInsnNode insn = keyMethodNode.instructions.get(i);
            if (insn.getOpcode() == Opcodes.INVOKESPECIAL && insn instanceof MethodInsnNode) {
                MethodInsnNode minsn = (MethodInsnNode) insn;
                if (minsn.owner.equals(colorClassName) && minsn.name.equals("<init>") && minsn.desc.equals("(FFF)V")) {
                    Float floatCst = findPrecedingFloatConstant(keyMethodNode, i);
                    if (floatCst != null) {
                        if (Float.valueOf(0.7f).equals(floatCst)) {
                            keyTargets.add(insn);
                            keyTargetNames.add("Sampler White Key (Active)");
                            keyFallbackNames.add(null);
                        } else if (Float.valueOf(0.5f).equals(floatCst)) {
                            keyTargets.add(insn);
                            keyTargetNames.add("Sampler White Key (Inactive)");
                            keyFallbackNames.add(null);
                        } else if (Float.valueOf(0.8f).equals(floatCst)) {
                            keyTargets.add(insn);
                            keyTargetNames.add("Sampler White Key (Pressed)");
                            keyFallbackNames.add("Sampler White Key (Active)");
                        } else if (Float.valueOf(0.1f).equals(floatCst)) {
                            keyTargets.add(insn);
                            keyTargetNames.add("Sampler Black Key (Active)");
                            keyFallbackNames.add(null);
                        } else if (Float.valueOf(0.3f).equals(floatCst)) {
                            keyTargets.add(insn);
                            keyTargetNames.add("Sampler Black Key (Inactive)");
                            keyFallbackNames.add(null);
                        } else if (Float.valueOf(0.55f).equals(floatCst)) {
                            keyTargets.add(insn);
                            keyTargetNames.add("Sampler Black Key (Pressed)");
                            keyFallbackNames.add("Sampler Black Key (Active)");
                        }
                    }
                }
            }
        }

        if (!keyTargets.isEmpty()) {
            for (int i = 0; i < keyTargets.size(); i++) {
                AbstractInsnNode targetInsn = keyTargets.get(i);
                String colorName = keyTargetNames.get(i);
                String fallbackName = keyFallbackNames.get(i);
                InsnList hook = new InsnList();
                if (fallbackName != null) {
                    writeBytecodeWithFallback(hook, colorClassName, getColorMethod, getColorDesc, colorName, fallbackName);
                } else {
                    writeBytecode(hook, colorClassName, getColorMethod, getColorDesc, colorName);
                }
                keyMethodNode.instructions.insert(targetInsn, hook);
            }
            keyMethodNode.maxStack += 6;
            patchedAny = true;
        }

        // 2. Patch octave text method if present
        if (octaveMethodNode != null) {
            for (int i = 0; i < octaveMethodNode.instructions.size(); i++) {
                AbstractInsnNode insn = octaveMethodNode.instructions.get(i);
                if (insn.getOpcode() == Opcodes.INVOKESPECIAL && insn instanceof MethodInsnNode) {
                    MethodInsnNode minsn = (MethodInsnNode) insn;
                    if (minsn.owner.equals(colorClassName) && minsn.name.equals("<init>") && minsn.desc.equals("(FFF)V")) {
                        Float floatCst = findPrecedingFloatConstant(octaveMethodNode, i);
                        if (floatCst != null && Float.valueOf(0.1f).equals(floatCst)) {
                            InsnList hook = new InsnList();
                            writeBytecode(hook, colorClassName, getColorMethod, getColorDesc, "Sampler Octave Text");
                            octaveMethodNode.instructions.insert(insn, hook);
                            octaveMethodNode.maxStack += 2;
                            patchedAny = true;
                            break;
                        }
                    }
                }
            }
        }

        if (patchedAny) {
            JarNode.getModifiedNodes().put(classNode.name, classNode);
        }
    }

    private Float findPrecedingFloatConstant(MethodNode mn, int invokeIndex) {
        for (int j = invokeIndex - 1; j >= Math.max(0, invokeIndex - 10); j--) {
            AbstractInsnNode prev = mn.instructions.get(j);
            if (prev.getOpcode() == Opcodes.LDC && prev instanceof LdcInsnNode) {
                Object cst = ((LdcInsnNode) prev).cst;
                if (cst instanceof Float) {
                    return (Float) cst;
                }
            }
        }
        return null;
    }

    private void writeBytecode(InsnList il, String colorClassName, String getColorMethod, String getColorDesc, String colorName) {
        il.add(new LabelNode());
        il.add(new InsnNode(Opcodes.DUP)); // Duplicate newly constructed Color instance on stack
        il.add(new LdcInsnNode(colorName));
        il.add(new FieldInsnNode(Opcodes.PUTFIELD, colorClassName, "colorName", "Ljava/lang/String;"));

        il.add(new LabelNode());
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, colorClassName, getColorMethod, getColorDesc, false));
        il.add(new InsnNode(Opcodes.POP));
    }

    private void writeBytecodeWithFallback(InsnList il, String colorClassName, String getColorMethod, String getColorDesc, String primaryName, String fallbackName) {
        LabelNode done = new LabelNode();

        // 1. Assign primary colorName and fetch from theme
        il.add(new LabelNode());
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new LdcInsnNode(primaryName));
        il.add(new FieldInsnNode(Opcodes.PUTFIELD, colorClassName, "colorName", "Ljava/lang/String;"));

        il.add(new InsnNode(Opcodes.DUP));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, colorClassName, getColorMethod, getColorDesc, false));
        il.add(new InsnNode(Opcodes.POP));

        // 2. Check if primaryName was customized in theme.bte
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, colorClassName, "isCustomized", "Z"));
        il.add(new JumpInsnNode(Opcodes.IFNE, done));

        // 3. If primary was not customized, fall back to fallbackName (e.g. Sampler White Key (Active))
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new LdcInsnNode(fallbackName));
        il.add(new FieldInsnNode(Opcodes.PUTFIELD, colorClassName, "colorName", "Ljava/lang/String;"));

        // Reset lastColorFetch so getColorMethod will re-read theme for fallbackName without hitting the 3000ms throttle
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new InsnNode(Opcodes.LCONST_0));
        il.add(new FieldInsnNode(Opcodes.PUTFIELD, colorClassName, "lastColorFetch", "J"));

        il.add(new InsnNode(Opcodes.DUP));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, colorClassName, getColorMethod, getColorDesc, false));
        il.add(new InsnNode(Opcodes.POP));

        il.add(done);
    }
}
