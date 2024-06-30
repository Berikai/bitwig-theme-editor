package dev.berikai.BitwigTheme.core.advanced.components;

import dev.berikai.BitwigTheme.asm.JarNode;
import dev.berikai.BitwigTheme.core.BitwigColor;
import dev.berikai.BitwigTheme.core.advanced.ColorClass;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.HashMap;

public class TimelinePlayheadClass {
    private final HashMap<String, ClassNode> classNodes;

    protected ClassNode classNode;
    protected MethodNode methodNode;

    public TimelinePlayheadClass(HashMap<String, ClassNode> classNodes) {
        this.classNodes = classNodes;
        findNodes();
    }

    protected void findNodes() {
        outer: for (ClassNode classNode : this.classNodes.values()) {
            for (MethodNode methodNode : classNode.methods) {
                if (classNode.name.contains("com/bitwig/flt/widget/core/timeline/renderer") && methodNode.desc.contains("(Lcom/bitwig/graphics/") && methodNode.desc.contains(";D)V") && methodNode.desc.length() < 26 + 10) {
                    this.classNode = classNode;
                    this.methodNode = methodNode;
                    JarNode.getModifiedNodes().put(classNode.name, classNode);
                    break outer;
                }
            }
        }
    }

    protected String getThemeFieldName() {
        InsnList insnList = methodNode.instructions;
        for (AbstractInsnNode insnNode : insnList) {
            if (insnNode.getOpcode() == Opcodes.GETSTATIC) {
                return ((FieldInsnNode) insnNode).name;
            }
        }
        return "";
    }

    public void setTheme(HashMap<String, BitwigColor> theme) {
        ColorClass colorClass = new ColorClass(classNodes);

        if (theme.get("Timeline Playhead") == null) {
            return;
        }

        colorClass.setGlobalColorField("timeline_playhead", theme.get("Timeline Playhead"));

        InsnList insnList = methodNode.instructions;
        for (AbstractInsnNode insnNode : insnList) {
            if (insnNode.getOpcode() == Opcodes.GETSTATIC) {
                insnList.insert(insnNode, new FieldInsnNode(Opcodes.GETSTATIC, colorClass.getClassNode().name, "timeline_playhead", "L" + colorClass.getClassNode().name + ";"));
                insnList.remove(insnNode);
            }
        }
    }

    public HashMap<String, BitwigColor> getTheme() {
        ColorClass colorClass = new ColorClass(classNodes);

        HashMap<String, BitwigColor> theme = new HashMap<>();

        theme.put("Timeline Playhead", colorClass.getGlobalColor(getThemeFieldName()));

        return theme;
    }
}
