package dev.berikai.BitwigTheme.core;

import dev.berikai.BitwigTheme.asm.JarNode;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.HashMap;

public class IntegrityClass {
    // A map of all classes in jar, along with their names
    private final HashMap<String, ClassNode> classNodes;

    // Assign the target classNode, when found
    protected ClassNode classNode;

    public IntegrityClass(HashMap<String, ClassNode> classNodes) {
        // Initialize classNodes on object creation
        this.classNodes = classNodes;

        // Find the class that contains the string "Simulated engine crash"
        // Integrity class is the only class that has this string, which makes our lives easier
        findClassNode("Simulated engine crash");
    }

    // Find the classNode that contains search_text
    protected void findClassNode(String search_text) {
        // Iterate through classes
        outer: for (ClassNode classNode : this.classNodes.values()) {
            // Iterate through methods of class
            for (MethodNode methodNode : classNode.methods) {
                // Iterate through instructions of method
                for (AbstractInsnNode insnNode : methodNode.instructions) {
                    // If instruction opcode is LDC and is equal to search_text, we found the class we were looking for
                    if (insnNode.getOpcode() == Opcodes.LDC && ((LdcInsnNode) insnNode).cst.equals(search_text)) {
                        // Assign found classNode to the field
                        this.classNode = classNode;

                        // Add found class to modified class nodes map
                        JarNode.getModifiedNodes().put(classNode.name, classNode);

                        // Add found class to computeMaxNodes list, to call the ClassWriter with the flag COMPUTE_MAXS in JarNode class
                        JarNode.getComputeMaxNodes().add(classNode.name);

                        // Break the outer for loop, we don't need to look for the rest
                        break outer;
                    }
                }
            }
        }
    }

    public void disableIntegrity() {
        // If classNode is null, that means we couldn't find the integrity class
        // Inform the user about the drawbacks, but let the program continue
        if (classNode == null) {
            System.out.println("WARNING: Couldn't disable integrity check!");
            System.out.println(" -> You may experience audio engine crashes.");
            return;
        }

        // Iterate through all the methods of class, to find the integrity check method
        outer: for (MethodNode hashMethod : classNode.methods) {
            // Check if the description aligns with our target method: private void methodName(int, int)
            if (hashMethod.access == Opcodes.ACC_PRIVATE && hashMethod.desc.equals("(II)V")) {
                // Iterate through instructions
                InsnList insnList = hashMethod.instructions;
                for(int i = 0; i < insnList.size(); i++) {
                    AbstractInsnNode insnNode = insnList.get(i);
                    // If we find an ICONST_1 (true) instruction, we can replace it with ICONST_0 (false) to disable integrity check
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
