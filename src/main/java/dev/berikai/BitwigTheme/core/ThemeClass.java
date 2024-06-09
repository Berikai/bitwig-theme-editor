package dev.berikai.BitwigTheme.core;

import dev.berikai.BitwigTheme.asm.JarNode;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.HashMap;

public abstract class ThemeClass {
    private final HashMap<String, ClassNode> classNodes;

    protected MethodNode methodNode;
    protected ClassNode classNode;

    public ThemeClass(HashMap<String, ClassNode> classNodes) {
        this.classNodes = classNodes;
    }

    protected void findNodes(String search_text) {
        outer: for (ClassNode classNode : this.classNodes.values()) {
            for (MethodNode methodNode : classNode.methods) {
                for (AbstractInsnNode insnNode : methodNode.instructions) {
                    if (insnNode.getOpcode() == Opcodes.LDC && ((LdcInsnNode) insnNode).cst.equals(search_text)) {
                        if (!isAsmNumber(insnNode.getNext())) {
                            continue;
                        }
                        this.methodNode = methodNode;
                        this.classNode = classNode;
                        JarNode.getModifiedNodes().put(classNode.name, classNode);
                        break outer;
                    }
                }
            }
        }
    }

    private boolean isAsmNumber(AbstractInsnNode insnNode) {
        return insnNode.getOpcode() == Opcodes.SIPUSH || insnNode.getOpcode() == Opcodes.BIPUSH

                || insnNode.getOpcode() == Opcodes.ICONST_0 || insnNode.getOpcode() == Opcodes.ICONST_1
                || insnNode.getOpcode() == Opcodes.ICONST_2 || insnNode.getOpcode() == Opcodes.ICONST_3
                || insnNode.getOpcode() == Opcodes.ICONST_4 || insnNode.getOpcode() == Opcodes.ICONST_5

                ;
    }

    private int getAsmNumber(AbstractInsnNode insnNode) {
        if (insnNode.getOpcode() == Opcodes.SIPUSH) return ((IntInsnNode) insnNode).operand;
        if (insnNode.getOpcode() == Opcodes.BIPUSH) return ((IntInsnNode) insnNode).operand;

        if (insnNode.getOpcode() == Opcodes.ICONST_0) return 0;
        if (insnNode.getOpcode() == Opcodes.ICONST_1) return 1;
        if (insnNode.getOpcode() == Opcodes.ICONST_2) return 2;
        if (insnNode.getOpcode() == Opcodes.ICONST_3) return 3;
        if (insnNode.getOpcode() == Opcodes.ICONST_4) return 4;
        if (insnNode.getOpcode() == Opcodes.ICONST_5) return 5;
        return -1;
    }

    public HashMap<String, Color> getTheme() {
        InsnList insnList = methodNode.instructions;

        HashMap<String, Color> theme = new HashMap<>();

        for(int i = 0; i < insnList.size(); i++) {
            int colorType;
            String colorName;

            if (i + 4 >= insnList.size()) {
                break;
            }

            if (insnList.get(i).getOpcode() != Opcodes.LDC) {
                continue;
            }

            colorName = ((LdcInsnNode) insnList.get(i)).cst.toString();

            if(isAsmNumber(insnList.get(i + 4))) {
                colorType = Color.RGBA;
            } else if(isAsmNumber(insnList.get(i + 3))) {
                colorType = Color.RGB;
            } else if(isAsmNumber(insnList.get(i + 1))) {
                colorType = Color.GREYSCALE;
            } else {
                continue;
            }

            if(colorType == Color.GREYSCALE) {
                theme.put(colorName,
                        new Color(getAsmNumber(insnList.get(i + 1)))
                );
            }

            if(colorType == Color.RGB) {
                theme.put(colorName,
                        new Color(
                                getAsmNumber(insnList.get(i + 1)),
                                getAsmNumber(insnList.get(i + 2)),
                                getAsmNumber(insnList.get(i + 3))
                        )
                );
            }

            if(colorType == Color.RGBA) {
                theme.put(colorName,
                        new Color(
                                getAsmNumber(insnList.get(i + 1)),
                                getAsmNumber(insnList.get(i + 2)),
                                getAsmNumber(insnList.get(i + 3)),
                                getAsmNumber(insnList.get(i + 4))
                        )
                );
            }

        }

        return theme;
    }

    public void setTheme(HashMap<String, Color> theme) {
        InsnList insnList = methodNode.instructions;

        for(int i = 0; i < insnList.size(); i++) {
            String colorName;

            if (i + 4 >= insnList.size()) {
                break;
            }

            if (insnList.get(i).getOpcode() != Opcodes.LDC) {
                continue;
            }

            colorName = ((LdcInsnNode) insnList.get(i)).cst.toString();

            Color testColor = new Color(404);
            if(isAsmNumber(insnList.get(i + 4))) {
                if (theme.getOrDefault(colorName, testColor) == testColor) continue;

                int[] rgb = theme.get(colorName).getRGB();

                for (int j = 0; j < 4; j++) {
                    insnList.insert(insnList.get(i + j + 1), new IntInsnNode(Opcodes.SIPUSH, rgb[j]));
                    insnList.remove(insnList.get(i + j + 1));
                }
            } else if(isAsmNumber(insnList.get(i + 3))) {

                if (theme.getOrDefault(colorName, testColor) == testColor) continue;

                int[] rgb = theme.get(colorName).getRGB();

                for (int j = 0; j < 3; j++) {
                    insnList.insert(insnList.get(i + j + 1), new IntInsnNode(Opcodes.SIPUSH, rgb[j]));
                    insnList.remove(insnList.get(i + j + 1));
                }
            } else if(isAsmNumber(insnList.get(i + 1))) {

                if (theme.getOrDefault(colorName, testColor) == testColor) continue;

                int[] rgb = theme.get(colorName).getRGB();

                for (int j = 0; j < 1; j++) {
                    insnList.insert(insnList.get(i + j + 1), new IntInsnNode(Opcodes.SIPUSH, rgb[j]));
                    insnList.remove(insnList.get(i + j + 1));
                }
            }
        }
    }
}
