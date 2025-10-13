package dev.berikai.BitwigTheme.core;

import dev.berikai.BitwigTheme.asm.JarNode;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.HashMap;
import java.util.LinkedHashMap;

public abstract class PatchClass {
    // Name of target class
    private final String targetClassMappingName;

    // A map of name mappings for all nodes
    public static HashMap<String, String> mappings = new LinkedHashMap<>(); // LinkedHashMap for ordered entries

    // A map of all classes in jar, along with their names
    private final HashMap<String, ClassNode> classNodes;

    // Assign the target classNode, when found
    protected ClassNode classNode;

    // Assign the target methodNode, when found
    protected MethodNode methodNode;

    public PatchClass(HashMap<String, ClassNode> classNodes, String targetClassMappingName) {
        // Initialize classNodes on object creation
        this.classNodes = classNodes;
        this.targetClassMappingName = targetClassMappingName;
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

                        // Map the obfuscated name of target class
                        PatchClass.mappings.put(this.targetClassMappingName, this.classNode.name);

                        // Add found class to modified class nodes map
                        JarNode.getModifiedNodes().put(classNode.name, classNode);

                        // Break the outer for loop, we don't need to look for the rest
                        break outer;
                    }
                }
            }
        }
    }

    // We create an abstract findMethodNode method, because we have to search for different properties for every method
    protected abstract void findMethodNode();

    // All patching operations for the class
    public abstract void patch();

    public ClassNode getClassNode() {
        return classNode;
    }
}
