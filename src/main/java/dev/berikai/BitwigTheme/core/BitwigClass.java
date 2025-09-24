package dev.berikai.BitwigTheme.core;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.HashMap;

public class BitwigClass {
    // A map of all classes in jar, along with their names
    private final HashMap<String, ClassNode> classNodes;

    // Assign the target classNode, when found
    protected ClassNode classNode;

    public BitwigClass(HashMap<String, ClassNode> classNodes) {
        // Initialize classNodes on object creation
        this.classNodes = classNodes;
        findClassNode();
    }

    protected void findClassNode() {
        // Find class with name com.bitwig.flt.control_surface.proxy.BitwigStudioHost
        this.classNode = this.classNodes.get("com/bitwig/flt/control_surface/proxy/BitwigStudioHost");
    }

    public boolean isBitwigJAR() {
        return this.classNode != null;
    }

    public String getVersion() {
        // Get method with name getHostVersion and descriptor ()Ljava/lang/String;
        MethodNode methodNode = this.classNode.methods.stream()
                .filter(m -> m.name.equals("getHostVersion") && m.desc.equals("()Ljava/lang/String;"))
                .findFirst()
                .orElse(null);

        // Get the LDC instruction that loads the version string
        for (AbstractInsnNode insn : methodNode.instructions) {
            if (insn.getOpcode() == Opcodes.LDC) {
                LdcInsnNode ldcInsn = (LdcInsnNode) insn;
                if (ldcInsn.cst instanceof String) {
                    return (String) ldcInsn.cst;
                }
            }
        }

        return null;
    }
}
