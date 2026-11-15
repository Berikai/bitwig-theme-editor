package dev.berikai.BitwigTheme.core.impl;

import dev.berikai.BitwigTheme.asm.JarNode;
import dev.berikai.BitwigTheme.core.PatchClass;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.HashMap;

public class TitlebarAccentPatchClass extends PatchClass {
    // In Bitwig Studio 6.0+, the Arranger "Project" / Transport header left accent stripe (#3ebaff)
    // is returned by Transport.Yrh() (Avn in 6.1.5, hqu in 6.0), which hardcodes QFL.IYM(62, 186, 255)
    // instead of resolving "Accent (hitech)" from the active theme.
    private static final String TRANSPORT_UUID = "66122f49-dfb4-4df7-86b5-6e19dc3f2fe8";

    private String fromRgbOwner;
    private String fromRgbName;
    private String fromRgbDesc;
    private String staticColorFieldName;
    private AbstractInsnNode getStaticInsn;

    public TitlebarAccentPatchClass(HashMap<String, ClassNode> classNodes) {
        super(classNodes, "TitlebarAccentClass");

        findTransportClass(classNodes);
        if (this.classNode == null || this.methodNode == null) {
            System.out.println("NOTE: Arrangement Project/Transport titlebar accent class not found (normal for Bitwig < 6.0).");
            return;
        }

        JarNode.getModifiedNodes().put(this.classNode.name, this.classNode);
        System.out.println(" -> Found Arrangement titlebar accent class (" + this.classNode.name + "." + this.methodNode.name + ").");
    }

    private void findTransportClass(HashMap<String, ClassNode> classNodes) {
        String colorClassName = PatchClass.mappings.get("ColorClass");
        if (colorClassName == null) {
            return;
        }
        String colorDesc = "L" + colorClassName + ";";

        for (ClassNode cn : classNodes.values()) {
            boolean hasUuid = false;
            MethodNode clinit = null;
            for (MethodNode mn : cn.methods) {
                if (mn.name.equals("<clinit>")) {
                    clinit = mn;
                }
                for (int i = 0; i < mn.instructions.size(); i++) {
                    AbstractInsnNode insn = mn.instructions.get(i);
                    if (insn.getOpcode() == Opcodes.LDC && insn instanceof LdcInsnNode) {
                        if (TRANSPORT_UUID.equals(((LdcInsnNode) insn).cst)) {
                            hasUuid = true;
                            break;
                        }
                    }
                }
            }
            if (!hasUuid || clinit == null) {
                continue;
            }

            // Locate static ColorClass field initialized with RGB (62, 186, 255)
            for (int i = 0; i + 4 < clinit.instructions.size(); i++) {
                AbstractInsnNode a0 = clinit.instructions.get(i);
                AbstractInsnNode a1 = clinit.instructions.get(i + 1);
                AbstractInsnNode a2 = clinit.instructions.get(i + 2);
                AbstractInsnNode a3 = clinit.instructions.get(i + 3);
                AbstractInsnNode a4 = clinit.instructions.get(i + 4);
                if (intValue(a0) == 62 && intValue(a1) == 186 && intValue(a2) == 255
                        && a3.getOpcode() == Opcodes.INVOKESTATIC && a3 instanceof MethodInsnNode
                        && a4.getOpcode() == Opcodes.PUTSTATIC && a4 instanceof FieldInsnNode) {
                    MethodInsnNode minsn = (MethodInsnNode) a3;
                    FieldInsnNode finsn = (FieldInsnNode) a4;
                    if (minsn.owner.equals(colorClassName) && minsn.desc.equals("(III)" + colorDesc)
                            && finsn.owner.equals(cn.name) && finsn.desc.equals(colorDesc)) {
                        fromRgbOwner = minsn.owner;
                        fromRgbName = minsn.name;
                        fromRgbDesc = minsn.desc;
                        staticColorFieldName = finsn.name;
                        break;
                    }
                }
            }

            if (fromRgbName == null || staticColorFieldName == null) {
                continue;
            }

            // Locate the ()L<ColorClass>; getter that returns that static field
            for (MethodNode mn : cn.methods) {
                if ((mn.access & Opcodes.ACC_STATIC) != 0) continue;
                if (!mn.desc.equals("()" + colorDesc)) continue;

                FieldInsnNode foundGetStatic = null;
                boolean hasAreturn = false;
                int realInsns = 0;
                for (int i = 0; i < mn.instructions.size(); i++) {
                    AbstractInsnNode insn = mn.instructions.get(i);
                    if (insn instanceof LabelNode || insn instanceof LineNumberNode || insn instanceof FrameNode) {
                        continue;
                    }
                    realInsns++;
                    if (realInsns == 1 && insn.getOpcode() == Opcodes.GETSTATIC && insn instanceof FieldInsnNode) {
                        FieldInsnNode f = (FieldInsnNode) insn;
                        if (f.owner.equals(cn.name) && f.name.equals(staticColorFieldName) && f.desc.equals(colorDesc)) {
                            foundGetStatic = f;
                        }
                    } else if (realInsns == 2 && insn.getOpcode() == Opcodes.ARETURN) {
                        hasAreturn = true;
                    }
                }

                if (realInsns == 2 && foundGetStatic != null && hasAreturn) {
                    this.classNode = cn;
                    this.methodNode = mn;
                    this.getStaticInsn = foundGetStatic;
                    return;
                }
            }
        }
    }

    private static int intValue(AbstractInsnNode insn) {
        if (insn instanceof IntInsnNode && (insn.getOpcode() == Opcodes.BIPUSH || insn.getOpcode() == Opcodes.SIPUSH)) {
            return ((IntInsnNode) insn).operand;
        }
        return -1;
    }

    @Override
    protected void findMethodNode() {
        // Handled in findTransportClass
    }

    @Override
    public void patch() {
        String colorClassName = PatchClass.mappings.get("ColorClass");
        String getColorMethod = PatchClass.mappings.get("ColorClass.getColorMethod");
        String getColorDesc = PatchClass.mappings.get("ColorClass.getColorDesc");

        if (colorClassName == null || getColorMethod == null || getColorDesc == null
                || this.methodNode == null || this.getStaticInsn == null) {
            return;
        }

        InsnList hook = new InsnList();
        hook.add(new IntInsnNode(Opcodes.BIPUSH, 62));
        hook.add(new IntInsnNode(Opcodes.SIPUSH, 186));
        hook.add(new IntInsnNode(Opcodes.SIPUSH, 255));
        hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, fromRgbOwner, fromRgbName, fromRgbDesc, false));
        appendColorLookup(hook, colorClassName, getColorMethod, getColorDesc, "Accent (hitech)");

        this.methodNode.instructions.insertBefore(this.getStaticInsn, hook);
        this.methodNode.instructions.remove(this.getStaticInsn);
        this.methodNode.maxStack = Math.max(this.methodNode.maxStack, 4);

        System.out.println(" -> Successfully patched Arrangement titlebar accent hook (Accent (hitech)).");
    }
}
