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
    // Color instances for active/inactive white/black keys, pressed keys, and octave text inside
    // the per-key rendering loop (called 128 times per frame).
    // To prevent opening theme.bte 30,000+ times per second (which exhausts OS file descriptors and
    // causes java.io.FileNotFoundException: Too many open files), we inject a static color cache
    // refreshed at most once every 3000ms.

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

        String colorDesc = "L" + colorClassName + ";";

        // Add static cache fields to Sampler class
        classNode.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "bte_wActive", colorDesc, null, null));
        classNode.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "bte_wInactive", colorDesc, null, null));
        classNode.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "bte_wPressed", colorDesc, null, null));
        classNode.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "bte_bActive", colorDesc, null, null));
        classNode.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "bte_bInactive", colorDesc, null, null));
        classNode.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "bte_bPressed", colorDesc, null, null));
        classNode.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "bte_octText", colorDesc, null, null));
        classNode.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "bte_lastFetch", "J", null, null));

        // Create the static cache updater method
        createUpdateColorsMethod(colorClassName, getColorMethod, getColorDesc);

        boolean patchedAny = false;

        // 1. Patch key painting method
        List<AbstractInsnNode> keyTargets = new ArrayList<>();
        List<String> keyFieldNames = new ArrayList<>();

        for (int i = 0; i < keyMethodNode.instructions.size(); i++) {
            AbstractInsnNode insn = keyMethodNode.instructions.get(i);
            if (insn.getOpcode() == Opcodes.INVOKESPECIAL && insn instanceof MethodInsnNode) {
                MethodInsnNode minsn = (MethodInsnNode) insn;
                if (minsn.owner.equals(colorClassName) && minsn.name.equals("<init>") && minsn.desc.equals("(FFF)V")) {
                    Float floatCst = findPrecedingFloatConstant(keyMethodNode, i);
                    if (floatCst != null) {
                        if (Float.valueOf(0.7f).equals(floatCst)) {
                            keyTargets.add(insn);
                            keyFieldNames.add("bte_wActive");
                        } else if (Float.valueOf(0.5f).equals(floatCst)) {
                            keyTargets.add(insn);
                            keyFieldNames.add("bte_wInactive");
                        } else if (Float.valueOf(0.8f).equals(floatCst)) {
                            keyTargets.add(insn);
                            keyFieldNames.add("bte_wPressed");
                        } else if (Float.valueOf(0.1f).equals(floatCst)) {
                            keyTargets.add(insn);
                            keyFieldNames.add("bte_bActive");
                        } else if (Float.valueOf(0.3f).equals(floatCst)) {
                            keyTargets.add(insn);
                            keyFieldNames.add("bte_bInactive");
                        } else if (Float.valueOf(0.55f).equals(floatCst)) {
                            keyTargets.add(insn);
                            keyFieldNames.add("bte_bPressed");
                        }
                    }
                }
            }
        }

        if (!keyTargets.isEmpty()) {
            // Call bte_updateColors at the start of key painting method
            keyMethodNode.instructions.insertBefore(
                keyMethodNode.instructions.getFirst(),
                new MethodInsnNode(Opcodes.INVOKESTATIC, classNode.name, "bte_updateColors", "()V", false)
            );

            // Replace newly instantiated Color with cached static Color
            for (int i = 0; i < keyTargets.size(); i++) {
                AbstractInsnNode targetInsn = keyTargets.get(i);
                String fieldName = keyFieldNames.get(i);
                InsnList hook = new InsnList();
                hook.add(new InsnNode(Opcodes.POP));
                hook.add(new FieldInsnNode(Opcodes.GETSTATIC, classNode.name, fieldName, colorDesc));
                keyMethodNode.instructions.insert(targetInsn, hook);
            }
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
                            octaveMethodNode.instructions.insertBefore(
                                octaveMethodNode.instructions.getFirst(),
                                new MethodInsnNode(Opcodes.INVOKESTATIC, classNode.name, "bte_updateColors", "()V", false)
                            );
                            InsnList hook = new InsnList();
                            hook.add(new InsnNode(Opcodes.POP));
                            hook.add(new FieldInsnNode(Opcodes.GETSTATIC, classNode.name, "bte_octText", colorDesc));
                            octaveMethodNode.instructions.insert(insn, hook);
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

    private void createUpdateColorsMethod(String colorClassName, String getColorMethod, String getColorDesc) {
        MethodNode updateMethod = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "bte_updateColors", "()V", null, null);
        InsnList il = updateMethod.instructions;
        LabelNode returnLabel = new LabelNode();
        LabelNode fetchLabel = new LabelNode();

        // long now = System.currentTimeMillis();
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/System", "currentTimeMillis", "()J", false));
        il.add(new VarInsnNode(Opcodes.LSTORE, 0)); // local 0 & 1: now

        // if (bte_lastFetch != 0L && (now - bte_lastFetch) <= 3000L) return;
        il.add(new FieldInsnNode(Opcodes.GETSTATIC, classNode.name, "bte_lastFetch", "J"));
        il.add(new InsnNode(Opcodes.LCONST_0));
        il.add(new InsnNode(Opcodes.LCMP));
        il.add(new JumpInsnNode(Opcodes.IFEQ, fetchLabel));

        il.add(new VarInsnNode(Opcodes.LLOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETSTATIC, classNode.name, "bte_lastFetch", "J"));
        il.add(new InsnNode(Opcodes.LSUB));
        il.add(new LdcInsnNode(3000L));
        il.add(new InsnNode(Opcodes.LCMP));
        il.add(new JumpInsnNode(Opcodes.IFLE, returnLabel));

        il.add(fetchLabel);
        // bte_lastFetch = now;
        il.add(new VarInsnNode(Opcodes.LLOAD, 0));
        il.add(new FieldInsnNode(Opcodes.PUTSTATIC, classNode.name, "bte_lastFetch", "J"));

        // Instantiate and fetch the 7 cached colors
        createColorAndFetch(il, colorClassName, getColorMethod, getColorDesc, 0.7f, 0.7f, 0.7f, "Sampler White Key (Active)", null, "bte_wActive");
        createColorAndFetch(il, colorClassName, getColorMethod, getColorDesc, 0.5f, 0.5f, 0.5f, "Sampler White Key (Inactive)", null, "bte_wInactive");
        createColorAndFetch(il, colorClassName, getColorMethod, getColorDesc, 0.8f, 1.0f, 0.8f, "Sampler White Key (Pressed)", "Sampler White Key (Active)", "bte_wPressed");
        createColorAndFetch(il, colorClassName, getColorMethod, getColorDesc, 0.1f, 0.1f, 0.1f, "Sampler Black Key (Active)", null, "bte_bActive");
        createColorAndFetch(il, colorClassName, getColorMethod, getColorDesc, 0.3f, 0.3f, 0.3f, "Sampler Black Key (Inactive)", null, "bte_bInactive");
        createColorAndFetch(il, colorClassName, getColorMethod, getColorDesc, 0.55f, 0.7f, 0.55f, "Sampler Black Key (Pressed)", "Sampler Black Key (Active)", "bte_bPressed");
        createColorAndFetch(il, colorClassName, getColorMethod, getColorDesc, 0.1f, 0.1f, 0.1f, "Sampler Octave Text", null, "bte_octText");

        il.add(returnLabel);
        il.add(new InsnNode(Opcodes.RETURN));
        updateMethod.maxStack = 6;
        updateMethod.maxLocals = 2;
        classNode.methods.add(updateMethod);
    }

    private void createColorAndFetch(InsnList il, String colorClassName, String getColorMethod, String getColorDesc,
                                     float r, float g, float b, String primaryName, String fallbackName, String staticFieldName) {
        // new Color(r, g, b)
        il.add(new TypeInsnNode(Opcodes.NEW, colorClassName));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new LdcInsnNode(r));
        il.add(new LdcInsnNode(g));
        il.add(new LdcInsnNode(b));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, colorClassName, "<init>", "(FFF)V", false));

        // Assign primary colorName
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new LdcInsnNode(primaryName));
        il.add(new FieldInsnNode(Opcodes.PUTFIELD, colorClassName, "colorName", "Ljava/lang/String;"));

        // Invoke getColorMethod
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, colorClassName, getColorMethod, getColorDesc, false));
        il.add(new InsnNode(Opcodes.POP));

        if (fallbackName != null) {
            LabelNode done = new LabelNode();
            // Check isCustomized
            il.add(new InsnNode(Opcodes.DUP));
            il.add(new FieldInsnNode(Opcodes.GETFIELD, colorClassName, "isCustomized", "Z"));
            il.add(new JumpInsnNode(Opcodes.IFNE, done));

            // If primary was not customized, assign fallbackName and reset lastColorFetch to 0L so it re-reads
            il.add(new InsnNode(Opcodes.DUP));
            il.add(new LdcInsnNode(fallbackName));
            il.add(new FieldInsnNode(Opcodes.PUTFIELD, colorClassName, "colorName", "Ljava/lang/String;"));

            il.add(new InsnNode(Opcodes.DUP));
            il.add(new InsnNode(Opcodes.LCONST_0));
            il.add(new FieldInsnNode(Opcodes.PUTFIELD, colorClassName, "lastColorFetch", "J"));

            il.add(new InsnNode(Opcodes.DUP));
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, colorClassName, getColorMethod, getColorDesc, false));
            il.add(new InsnNode(Opcodes.POP));

            il.add(done);
        }

        // Store into static field
        il.add(new FieldInsnNode(Opcodes.PUTSTATIC, classNode.name, staticFieldName, "L" + colorClassName + ";"));
    }
}
