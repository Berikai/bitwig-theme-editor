package dev.berikai.BitwigTheme.core.impl;

import dev.berikai.BitwigTheme.asm.JarNode;
import dev.berikai.BitwigTheme.core.PatchClass;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.HashMap;

public class SceneHeaderPatchClass extends PatchClass {
    // In Bitwig Studio 6.0+, uncolored Scene headers (Scene 1, Scene 2, ...) get their background
    // from Scene.APa()/fCq()/Rj(), which hardcodes QFL.IYM(73) (#494949) instead of resolving a
    // theme color. Patching it to resolve "Grey 4" (which Editor maps from "Panel body" for 4.x/5.x
    // compatibility themes) restores themeable default Scene header backgrounds in 6.x.
    private static final String COLOR_KEY = "Grey 4";

    private AbstractInsnNode fromGrayInsn;
    private ClassNode widgetClassNode;
    private MethodNode widgetPaintMethod;
    private AbstractInsnNode widgetSetColorInsn;

    public SceneHeaderPatchClass(HashMap<String, ClassNode> classNodes) {
        super(classNodes, "SceneHeaderClass");

        findSceneDefaultColorMethod(classNodes);
        if (this.classNode == null || this.methodNode == null || this.fromGrayInsn == null) {
            System.out.println("NOTE: Scene header default color class not found (normal for Bitwig < 6.0).");
            return;
        }

        findSceneWidgetPaintMethod(classNodes);

        JarNode.getModifiedNodes().put(this.classNode.name, this.classNode);
        if (this.widgetClassNode != null && this.widgetPaintMethod != null && this.widgetSetColorInsn != null) {
            JarNode.getModifiedNodes().put(this.widgetClassNode.name, this.widgetClassNode);
        }
        System.out.println(" -> Found Scene header default color class (" + this.classNode.name + "." + this.methodNode.name + ").");
    }

    private ClassNode findMetadataTargetClass(HashMap<String, ClassNode> classNodes, String strA, String strB) {
        for (ClassNode cn : classNodes.values()) {
            boolean hasA = false;
            boolean hasB = false;
            for (MethodNode mn : cn.methods) {
                for (int i = 0; i < mn.instructions.size(); i++) {
                    AbstractInsnNode insn = mn.instructions.get(i);
                    if (insn.getOpcode() == Opcodes.LDC && insn instanceof LdcInsnNode) {
                        Object cst = ((LdcInsnNode) insn).cst;
                        if (strA.equals(cst)) hasA = true;
                        else if (strB.equals(cst)) hasB = true;
                    }
                }
            }
            if (!hasA || !hasB) {
                continue;
            }

            for (MethodNode mn : cn.methods) {
                if ((mn.access & Opcodes.ACC_STATIC) != 0) continue;
                if (!mn.desc.equals("()Ljava/lang/Class;")) continue;
                for (int i = 0; i < mn.instructions.size(); i++) {
                    AbstractInsnNode insn = mn.instructions.get(i);
                    if (insn.getOpcode() == Opcodes.LDC && insn instanceof LdcInsnNode) {
                        Object cst = ((LdcInsnNode) insn).cst;
                        if (cst instanceof Type && ((Type) cst).getSort() == Type.OBJECT) {
                            return classNodes.get(((Type) cst).getInternalName());
                        }
                    }
                }
            }
        }
        return null;
    }

    private void findSceneDefaultColorMethod(HashMap<String, ClassNode> classNodes) {
        String colorClassName = PatchClass.mappings.get("ColorClass");
        if (colorClassName == null) {
            return;
        }
        String colorDesc = "L" + colorClassName + ";";

        ClassNode sceneClass = findMetadataTargetClass(classNodes, "matrix_scene", "display_color");
        if (sceneClass == null) {
            return;
        }

        for (MethodNode mn : sceneClass.methods) {
            if ((mn.access & Opcodes.ACC_STATIC) != 0 || !mn.desc.equals("()V")) {
                continue;
            }
            InsnList il = mn.instructions;
            for (int i = 0; i + 2 < il.size(); i++) {
                AbstractInsnNode a0 = il.get(i);
                AbstractInsnNode a1 = il.get(i + 1);
                AbstractInsnNode a2 = il.get(i + 2);
                if (a0.getOpcode() == Opcodes.BIPUSH && ((IntInsnNode) a0).operand == 73
                        && a1.getOpcode() == Opcodes.INVOKESTATIC && a1 instanceof MethodInsnNode
                        && a2.getOpcode() == Opcodes.INVOKEVIRTUAL && a2 instanceof MethodInsnNode) {
                    MethodInsnNode m1 = (MethodInsnNode) a1;
                    MethodInsnNode m2 = (MethodInsnNode) a2;
                    if (m1.owner.equals(colorClassName) && m1.desc.equals("(I)" + colorDesc)
                            && m2.owner.equals(sceneClass.name) && m2.desc.equals("(" + colorDesc + ")V")) {
                        this.classNode = sceneClass;
                        this.methodNode = mn;
                        this.fromGrayInsn = m1;
                        return;
                    }
                }
            }
        }
    }

    private void findSceneWidgetPaintMethod(HashMap<String, ClassNode> classNodes) {
        String colorClassName = PatchClass.mappings.get("ColorClass");
        if (colorClassName == null) {
            return;
        }
        String colorDesc = "L" + colorClassName + ";";

        ClassNode widgetClass = findMetadataTargetClass(classNodes, "matrix_scene_widget", "scene_color");
        if (widgetClass == null) {
            return;
        }

        for (MethodNode mn : widgetClass.methods) {
            if ((mn.access & Opcodes.ACC_STATIC) != 0 || !mn.desc.equals("(Lcom/bitwig/graphics/CPO;)V")) {
                continue;
            }
            InsnList il = mn.instructions;
            for (int i = 0; i + 1 < il.size(); i++) {
                AbstractInsnNode a0 = il.get(i);
                AbstractInsnNode a1 = il.get(i + 1);
                if (a0.getOpcode() == Opcodes.INVOKEVIRTUAL && a0 instanceof MethodInsnNode
                        && a1.getOpcode() == Opcodes.INVOKEINTERFACE && a1 instanceof MethodInsnNode) {
                    MethodInsnNode m0 = (MethodInsnNode) a0;
                    MethodInsnNode m1 = (MethodInsnNode) a1;
                    if (m0.desc.equals("()" + colorDesc)
                            && m1.owner.equals("com/bitwig/graphics/CPO")
                            && m1.desc.equals("(" + colorDesc + ")V")) {
                        this.widgetClassNode = widgetClass;
                        this.widgetPaintMethod = mn;
                        this.widgetSetColorInsn = m1;
                        return;
                    }
                }
            }
        }
    }

    @Override
    protected void findMethodNode() {
        // Handled in findSceneDefaultColorMethod
    }

    @Override
    public void patch() {
        String colorClassName = PatchClass.mappings.get("ColorClass");
        String getColorMethod = PatchClass.mappings.get("ColorClass.getColorMethod");
        String getColorDesc = PatchClass.mappings.get("ColorClass.getColorDesc");

        if (colorClassName == null || getColorMethod == null || getColorDesc == null
                || this.methodNode == null || this.fromGrayInsn == null) {
            return;
        }

        InsnList hook = new InsnList();
        appendColorLookup(hook, colorClassName, getColorMethod, getColorDesc, COLOR_KEY);

        this.methodNode.instructions.insert(this.fromGrayInsn, hook);
        this.methodNode.maxStack = Math.max(this.methodNode.maxStack, 4);

        if (this.widgetPaintMethod != null && this.widgetSetColorInsn != null) {
            InsnList refreshHook = new InsnList();
            LabelNode skipRefresh = new LabelNode();
            refreshHook.add(new InsnNode(Opcodes.DUP));
            refreshHook.add(new FieldInsnNode(Opcodes.GETFIELD, colorClassName, "colorName", "Ljava/lang/String;"));
            refreshHook.add(new JumpInsnNode(Opcodes.IFNULL, skipRefresh));
            refreshHook.add(new InsnNode(Opcodes.DUP));
            refreshHook.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, colorClassName, getColorMethod, getColorDesc, false));
            if (!getColorDesc.endsWith(")V")) {
                refreshHook.add(new InsnNode(Opcodes.POP));
            }
            refreshHook.add(skipRefresh);

            this.widgetPaintMethod.instructions.insertBefore(this.widgetSetColorInsn, refreshHook);
            this.widgetPaintMethod.maxStack = this.widgetPaintMethod.maxStack + 2;
        }

        System.out.println(" -> Successfully patched Scene header default color hook (" + COLOR_KEY + ").");
    }
}
