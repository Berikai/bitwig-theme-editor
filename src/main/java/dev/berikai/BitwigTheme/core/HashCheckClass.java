package dev.berikai.BitwigTheme.core;

import dev.berikai.BitwigTheme.asm.JarNode;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.HashMap;

public class HashCheckClass {
    private final HashMap<String, ClassNode> classNodes;

    protected ClassNode classNode;

    public HashCheckClass(HashMap<String, ClassNode> classNodes) {
        this.classNodes = classNodes;
        findNodes("MD5");
    }

    protected void findNodes(String search_text) {
        outer: for (ClassNode classNode : this.classNodes.values()) {
            for (MethodNode methodNode : classNode.methods) {
                for (AbstractInsnNode insnNode : methodNode.instructions) {
                    if (insnNode.getOpcode() == Opcodes.LDC && ((LdcInsnNode) insnNode).cst.equals(search_text)) {
                        if (!classNode.name.startsWith("com/bitwig/flt/document/core/master")) continue outer;
                        this.classNode = classNode;
                        JarNode.getModifiedNodes().put(classNode.name, classNode);
                        break outer;
                    }
                }
            }
        }
    }

    public void disableHashCheck() {
        if (classNode == null) {
            System.out.println("WARNING: Couldn't disable integrity check!");
            return;
        }

        outer: for (MethodNode hashMethod : classNode.methods) {
            if (hashMethod.access == Opcodes.ACC_PRIVATE && hashMethod.desc.equals("(II)V")) {
                InsnList insnList = hashMethod.instructions;
                for(int i = 0; i < insnList.size(); i++) {
                    AbstractInsnNode insnNode = insnList.get(i);
                    if (insnNode.getOpcode() == Opcodes.ICONST_1) {
                        insnList.insert(insnNode, new InsnNode(Opcodes.ICONST_0));
                        insnList.remove(insnNode);
                        break outer;
                    }
                }
            }
        }
    }

}
