package dev.berikai.BitwigTheme.core.advanced;

import dev.berikai.BitwigTheme.asm.JarNode;
import dev.berikai.BitwigTheme.core.BitwigColor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.HashMap;

public class ColorClass {
    private final HashMap<String, ClassNode> classNodes;

    protected ClassNode classNode;

    public ColorClass(HashMap<String, ClassNode> classNodes) {
        this.classNodes = classNodes;
        findNodes("Color[red=");
    }

    protected void findNodes(String search_text) {
        outer: for (ClassNode classNode : this.classNodes.values()) {
            for (MethodNode methodNode : classNode.methods) {
                for (AbstractInsnNode insnNode : methodNode.instructions) {
                    if (insnNode.getOpcode() == Opcodes.INVOKEDYNAMIC && ((InvokeDynamicInsnNode) insnNode).name.equals("makeConcatWithConstants")) {
                        for (Object arg : ((InvokeDynamicInsnNode) insnNode).bsmArgs) {
                            if (arg instanceof String && ((String) arg).contains(search_text)) {
                                this.classNode = classNode;
                                JarNode.getModifiedNodes().put(classNode.name, classNode);
                                break outer;
                            }
                        }
                    }
                }
            }
        }
    }

    public ClassNode getClassNode() {
        return classNode;
    }

    // Create a global color field, return true if success.
    protected boolean createGlobalColorField(String fieldName) {
        for (FieldNode fieldNode : classNode.fields) {
            if (fieldNode.name.equals(fieldName)) {
                // Field already exist.
                return false;
            }
        }
        // Create a new global color field in main color class, so we can use it elsewhere easily.
        classNode.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, fieldName, "L" + classNode.name + ";", null, null));
        return true;
    }

    // Set a global color field, create if it doesn't exist.
    public void setGlobalColorField(String fieldName, BitwigColor color) {
        if (createGlobalColorField(fieldName)) {
            // Add the color to the created field.
            InsnList colorInsnList = new InsnList();
            colorInsnList.add(new TypeInsnNode(Opcodes.NEW, classNode.name));
            colorInsnList.add(new InsnNode(Opcodes.DUP));
            colorInsnList.add(new LdcInsnNode(((double) color.getRGB()[0]) / 255.0));
            colorInsnList.add(new LdcInsnNode(((double) color.getRGB()[1]) / 255.0));
            colorInsnList.add(new LdcInsnNode(((double) color.getRGB()[2]) / 255.0));
            colorInsnList.add(new LdcInsnNode(((double) color.getRGB()[3]) / 255.0));
            colorInsnList.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, classNode.name, "<init>", "(DDDD)V"));
            colorInsnList.add(new FieldInsnNode(Opcodes.PUTSTATIC, classNode.name, fieldName, "L" + classNode.name + ";"));
            colorInsnList.add(new InsnNode(Opcodes.RETURN));

            for (MethodNode methodNode : classNode.methods) {
                if (methodNode.name.equals("<clinit>")) {
                    InsnList insnList = methodNode.instructions;
                    insnList.remove(insnList.get(insnList.size() - 1));
                    insnList.add(colorInsnList);
                    break;
                }
            }
        } else {
            // Change the color values of existing field.
            for (MethodNode methodNode : classNode.methods) {
                if (methodNode.name.equals("<clinit>")) {
                    InsnList insnList = methodNode.instructions;
                    for (int i = 0; i < insnList.size(); i++) {
                        if (insnList.get(i).getOpcode() == Opcodes.PUTSTATIC && ((FieldInsnNode) insnList.get(i)).name.equals(fieldName)) {
                            for (int j = 0; j < 4; j++) {
                                insnList.insert(insnList.get(i - 5 + j), new LdcInsnNode(((double) color.getRGB()[j]) / 255.0));
                                insnList.remove(insnList.get(i - 5 + j));
                            }
                        }
                    }
                }
            }
        }
    }
}
