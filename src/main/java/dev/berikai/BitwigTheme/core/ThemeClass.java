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
                || insnNode.getOpcode() == Opcodes.ICONST_4 || insnNode.getOpcode() == Opcodes.ICONST_5;
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

        // Iterate through each insnNode in target method that contains color information.
        for(int i = 0; i < insnList.size(); i++) {
            int colorType;
            String colorName;

            // Check for further possible out of boundaries error.
            if (i + 4 >= insnList.size()) {
                break;
            }

            // We are searching for LDC instructions that contains color value string.
            if (insnList.get(i).getOpcode() != Opcodes.LDC) {
                continue;
            }

            colorName = ((LdcInsnNode) insnList.get(i)).cst.toString();

            // Check how many instructions in order are numbers to determine color type.
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

        // Iterate through each insnNode in target method that contains color information.
        for(int i = 0; i < insnList.size(); i++) {
            String colorName;

            // Check for further possible out of boundaries error.
            if (i + 4 >= insnList.size()) {
                break;
            }

            // We are searching for LDC instructions that contains color value string.
            if (insnList.get(i).getOpcode() != Opcodes.LDC) {
                continue;
            }

            colorName = ((LdcInsnNode) insnList.get(i)).cst.toString();

            Color testColor = new Color(404); // Max color value is 255 each, so 404 greyscale color doesn't exist.
            if(isAsmNumber(insnList.get(i + 4))) {
                // We use this imaginary testColor to check if color key we found in the bytecode exists in the provided theme file, without error handling.
                if (theme.getOrDefault(colorName, testColor) == testColor) continue;

                int[] rgb = theme.get(colorName).getRGB();

                // RGBA values have 4 values. Iterate through each and inject instruction nodes in bytecode for each of them.
                for (int j = 0; j < 4; j++) {
                    insnList.insert(insnList.get(i + j + 1), new IntInsnNode(Opcodes.SIPUSH, rgb[j]));
                    insnList.remove(insnList.get(i + j + 1));
                }
            } else if(isAsmNumber(insnList.get(i + 3))) {

                if (theme.getOrDefault(colorName, testColor) == testColor) continue;

                int[] rgb = theme.get(colorName).getRGB();

                // RGB values have 3 values. Iterate through each and inject instruction nodes in bytecode for each of them.
                for (int j = 0; j < 3; j++) {
                    insnList.insert(insnList.get(i + j + 1), new IntInsnNode(Opcodes.SIPUSH, rgb[j]));
                    insnList.remove(insnList.get(i + j + 1));
                }
            } else if(isAsmNumber(insnList.get(i + 1))) {

                if (theme.getOrDefault(colorName, testColor) == testColor) continue;

                int[] rgb = theme.get(colorName).getRGB();

                // GREYSCALE values have 1 value, but we want to expand them as RGB values. More values mean more colors!
                for (int j = 0; j < 3; j++) {
                    // There is already one bytecode instruction exist in the bytecode for greyscale value.
                    // So we are going to remove that in iteration, but we also want to turn it into an RGB value.
                    // Thus, we are going to inject extra values to make it RGB.
                    boolean isFirstInsnLine = (j == 0);
                    insnList.insert(insnList.get(i + j + (isFirstInsnLine ? 1 : 0)), new IntInsnNode(Opcodes.SIPUSH, rgb[j]));
                    if (isFirstInsnLine) insnList.remove(insnList.get(i + j + 1));
                }

                MethodInsnNode invokeVirtual = (MethodInsnNode) insnList.get(i + 4);

                // Change methodInsnNode's descriptor to use the RGB version of the color method.
                insnList.insert(invokeVirtual, new MethodInsnNode(Opcodes.INVOKEVIRTUAL, invokeVirtual.owner, invokeVirtual.name, invokeVirtual.desc.replaceFirst(";I\\)", ";III)")));
                insnList.remove(invokeVirtual);
            }
        }
    }
}
