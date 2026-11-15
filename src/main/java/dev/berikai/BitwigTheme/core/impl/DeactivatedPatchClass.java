package dev.berikai.BitwigTheme.core.impl;

import dev.berikai.BitwigTheme.asm.JarNode;
import dev.berikai.BitwigTheme.core.PatchClass;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.HashMap;

public class DeactivatedPatchClass extends PatchClass {
    // In Bitwig Studio, deactivated/disabled tracks, devices, and knobs are rendered by
    // Widget.c_(GraphicsOutput) (duK.c_ in 6.1.5, dmu.c_ in 6.0, cvG.c_ in 5.3.13, cHH.d_ in 4.3.10),
    // which wraps GraphicsOutput in a ColorFilter that desaturates by 0.25 and blends 0.5 toward
    // the parent background (va2()). On light themes, parent container backgrounds remain dark grey,
    // turning deactivated devices and tracks muddy dark grey.
    // This patcher hooks that method to resolve "Deactivated Overlay" (#RRGGBBAA):
    // RGB sets the tint target color, and Alpha (00..FF) controls the blend strength (0.0..1.0).
    private static final String KEY_NAME = "Deactivated Overlay";
    private static final int DEFAULT_ARGB_SENTINEL = 0x80262626; // #26262680

    private AbstractInsnNode bgStoreInsn;
    private int bgLocalVar = -1;
    private AbstractInsnNode blendLdcInsn;

    public DeactivatedPatchClass(HashMap<String, ClassNode> classNodes) {
        super(classNodes, "DeactivatedClass");

        findDisabledPaintMethod(classNodes);
        if (this.classNode == null || this.methodNode == null) {
            System.out.println("NOTE: Deactivated widget tint method not found.");
            return;
        }

        JarNode.getModifiedNodes().put(this.classNode.name, this.classNode);
        System.out.println(" -> Found Deactivated widget tint hook (" + this.classNode.name + "." + this.methodNode.name + ").");
    }

    private void findDisabledPaintMethod(HashMap<String, ClassNode> classNodes) {
        String bridgeClassName = PatchClass.mappings.get("ColorBridgeClass");
        String colorClassName = PatchClass.mappings.get("ColorClass");
        if (bridgeClassName == null || colorClassName == null) {
            return;
        }

        ClassNode bridgeCn = classNodes.get(bridgeClassName);
        if (bridgeCn == null) {
            return;
        }

        String colorDesc = "L" + colorClassName + ";";
        String widgetClassName = null;
        for (MethodNode mn : bridgeCn.methods) {
            if (mn.desc.startsWith("(L") && mn.desc.endsWith(")" + colorDesc)) {
                widgetClassName = mn.desc.substring(2, mn.desc.indexOf(';'));
                break;
            }
        }
        if (widgetClassName == null) {
            return;
        }

        ClassNode widgetCn = classNodes.get(widgetClassName);
        if (widgetCn == null) {
            return;
        }

        for (MethodNode mn : widgetCn.methods) {
            if ((mn.access & Opcodes.ACC_STATIC) != 0) continue;
            if (!mn.desc.startsWith("(L") || !mn.desc.endsWith(";)V")) continue;

            boolean has025 = false;
            boolean has050 = false;
            boolean hasAnewarray = false;
            AbstractInsnNode foundBgStore = null;
            int foundBgVar = -1;
            AbstractInsnNode foundBlendLdc = null;

            for (int i = 0; i < mn.instructions.size(); i++) {
                AbstractInsnNode insn = mn.instructions.get(i);
                if (insn.getOpcode() == Opcodes.ANEWARRAY) {
                    hasAnewarray = true;
                }
                if (insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof Double) {
                    double val = (Double) ((LdcInsnNode) insn).cst;
                    if (val == 0.25d) has025 = true;
                    if (val == 0.5d) {
                        has050 = true;
                        if (i + 1 < mn.instructions.size()) {
                            AbstractInsnNode next = mn.instructions.get(i + 1);
                            if (next.getOpcode() == Opcodes.INVOKESPECIAL && next instanceof MethodInsnNode) {
                                MethodInsnNode minsn = (MethodInsnNode) next;
                                if (minsn.name.equals("<init>") && minsn.desc.equals("(" + colorDesc + "D)V")) {
                                    foundBlendLdc = insn;
                                }
                            }
                        }
                    }
                }
                if (foundBgStore == null && insn.getOpcode() == Opcodes.INVOKEVIRTUAL && insn instanceof MethodInsnNode) {
                    MethodInsnNode minsn = (MethodInsnNode) insn;
                    if (minsn.owner.equals(widgetClassName) && minsn.desc.equals("()" + colorDesc)) {
                        if (i + 1 < mn.instructions.size() && mn.instructions.get(i + 1).getOpcode() == Opcodes.ASTORE) {
                            foundBgStore = mn.instructions.get(i + 1);
                            foundBgVar = ((VarInsnNode) foundBgStore).var;
                        }
                    }
                }
            }

            if (has025 && has050 && hasAnewarray && foundBgStore != null && foundBlendLdc != null) {
                this.classNode = widgetCn;
                this.methodNode = mn;
                this.bgStoreInsn = foundBgStore;
                this.bgLocalVar = foundBgVar;
                this.blendLdcInsn = foundBlendLdc;
                return;
            }
        }
    }

    @Override
    protected void findMethodNode() {
        // Handled in findDisabledPaintMethod
    }

    @Override
    public void patch() {
        String colorClassName = PatchClass.mappings.get("ColorClass");
        String getColorMethod = PatchClass.mappings.get("ColorClass.getColorMethod");
        String getColorDesc = PatchClass.mappings.get("ColorClass.getColorDesc");
        String colorIntField = PatchClass.mappings.get("ColorClass.colorIntegerValue");
        String alphaFloatField = PatchClass.mappings.get("ColorClass.alphaValue");

        if (colorClassName == null || getColorMethod == null || getColorDesc == null
                || (colorIntField == null && alphaFloatField == null)
                || this.methodNode == null || this.bgStoreInsn == null || this.blendLdcInsn == null) {
            return;
        }

        int blendSlot = this.methodNode.maxLocals;
        this.methodNode.maxLocals += 2;

        InsnList hook = new InsnList();
        LabelNode skipLabel = new LabelNode();
        LabelNode doneLabel = new LabelNode();

        // Default blend = 0.5d
        hook.add(new LdcInsnNode(0.5d));
        hook.add(new VarInsnNode(Opcodes.DSTORE, blendSlot));

        // Allocate isolated ColorClass instance and look up "Deactivated Overlay"
        hook.add(new TypeInsnNode(Opcodes.NEW, colorClassName));
        hook.add(new InsnNode(Opcodes.DUP));
        hook.add(new InsnNode(Opcodes.FCONST_0));
        hook.add(new InsnNode(Opcodes.FCONST_0));
        hook.add(new InsnNode(Opcodes.FCONST_0));
        hook.add(new InsnNode(Opcodes.FCONST_0));
        hook.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, colorClassName, "<init>", "(FFFF)V", false));
        hook.add(new InsnNode(Opcodes.DUP));
        hook.add(new LdcInsnNode(KEY_NAME));
        hook.add(new FieldInsnNode(Opcodes.PUTFIELD, colorClassName, "colorName", "Ljava/lang/String;"));
        hook.add(new InsnNode(Opcodes.DUP));
        hook.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, colorClassName, getColorMethod, getColorDesc, false));
        if (!getColorDesc.endsWith(")V")) {
            hook.add(new InsnNode(Opcodes.POP));
        }

        // Only override when customized (and not equal to the default sentinel #26262680)
        hook.add(new InsnNode(Opcodes.DUP));
        hook.add(new FieldInsnNode(Opcodes.GETFIELD, colorClassName, "isCustomized", "Z"));
        hook.add(new JumpInsnNode(Opcodes.IFEQ, skipLabel));

        if (colorIntField != null) {
            hook.add(new InsnNode(Opcodes.DUP));
            hook.add(new FieldInsnNode(Opcodes.GETFIELD, colorClassName, colorIntField, "I"));
            hook.add(new LdcInsnNode(DEFAULT_ARGB_SENTINEL));
            hook.add(new JumpInsnNode(Opcodes.IF_ICMPEQ, skipLabel));
        }

        // Override bg local and blendSlot with custom color & alpha
        hook.add(new InsnNode(Opcodes.DUP));
        hook.add(new VarInsnNode(Opcodes.ASTORE, this.bgLocalVar));
        if (colorIntField != null) {
            hook.add(new FieldInsnNode(Opcodes.GETFIELD, colorClassName, colorIntField, "I"));
            hook.add(new IntInsnNode(Opcodes.BIPUSH, 24));
            hook.add(new InsnNode(Opcodes.IUSHR));
            hook.add(new IntInsnNode(Opcodes.SIPUSH, 255));
            hook.add(new InsnNode(Opcodes.IAND));
            hook.add(new InsnNode(Opcodes.I2D));
            hook.add(new LdcInsnNode(255.0d));
            hook.add(new InsnNode(Opcodes.DDIV));
        } else {
            hook.add(new FieldInsnNode(Opcodes.GETFIELD, colorClassName, alphaFloatField, "F"));
            hook.add(new InsnNode(Opcodes.F2D));
        }
        hook.add(new VarInsnNode(Opcodes.DSTORE, blendSlot));
        hook.add(new JumpInsnNode(Opcodes.GOTO, doneLabel));

        hook.add(skipLabel);
        hook.add(new InsnNode(Opcodes.POP));
        hook.add(doneLabel);

        this.methodNode.instructions.insert(this.bgStoreInsn, hook);
        this.methodNode.instructions.insertBefore(this.blendLdcInsn, new VarInsnNode(Opcodes.DLOAD, blendSlot));
        this.methodNode.instructions.remove(this.blendLdcInsn);
        this.methodNode.maxStack = Math.max(this.methodNode.maxStack, 7);

        System.out.println(" -> Successfully patched Deactivated widget tint hook (Deactivated Overlay).");
    }
}
