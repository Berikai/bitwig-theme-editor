package dev.berikai.BitwigTheme.core.impl;

import dev.berikai.BitwigTheme.asm.JarNode;
import dev.berikai.BitwigTheme.core.PatchClass;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.HashMap;

public class TimelinePatchClass extends PatchClass {
    // In Bitwig Studio 6.0+, the Arranger TimelineTheme replaced dedicated timeline keys
    // ("Top Level Timeline Background", "Dark Timeline Background", "Light Timeline Background",
    // "Irrelevant Timeline Background", "Timeline Background Pattern", "Timeline Primary Grid",
    // "Timeline Secondary Grid") with shared "Grey 2" and "Grid Line (Primary)/(Secondary)" keys.
    // This patcher restores those 7 independent keys in ColorPalette and rewires TimelineTheme
    // to use them, while gracefully skipping Bitwig 4.x/5.x where they already exist natively.

    private ClassNode paletteClassNode;
    private MethodNode paletteRegisterMethod;
    private String registerGreyMethod;
    private String registerGreyDesc;
    private String registerRgbaMethod;
    private String registerRgbaDesc;

    private ClassNode timelineThemeClassNode;
    private MethodNode timelineInitMethod;

    public TimelinePatchClass(HashMap<String, ClassNode> classNodes) {
        super(classNodes, "TimelineClass");

        findTimelineThemeClass(classNodes);
        if (timelineThemeClassNode == null || timelineInitMethod == null) {
            return;
        }

        findColorPaletteClass(classNodes);
        if (paletteClassNode == null || paletteRegisterMethod == null || registerGreyMethod == null || registerRgbaMethod == null) {
            System.out.println("WARNING: Could not identify ColorPalette registration methods for Timeline patch.");
            return;
        }

        this.classNode = timelineThemeClassNode;
        JarNode.getModifiedNodes().put(paletteClassNode.name, paletteClassNode);
        JarNode.getModifiedNodes().put(timelineThemeClassNode.name, timelineThemeClassNode);
        System.out.println(" -> Found Bitwig 6+ TimelineTheme (" + timelineThemeClassNode.name + ") and ColorPalette (" + paletteClassNode.name + ").");
    }

    private void findTimelineThemeClass(HashMap<String, ClassNode> classNodes) {
        for (ClassNode cn : classNodes.values()) {
            for (MethodNode mn : cn.methods) {
                boolean hasTooltipBg = false;
                boolean hasTooltipText = false;
                boolean hasGridPrimary = false;
                boolean hasTimelinePrimary = false;

                for (int i = 0; i < mn.instructions.size(); i++) {
                    AbstractInsnNode insn = mn.instructions.get(i);
                    if (insn.getOpcode() == Opcodes.LDC && insn instanceof LdcInsnNode) {
                        Object cst = ((LdcInsnNode) insn).cst;
                        if ("Timeline Tooltip Background".equals(cst)) hasTooltipBg = true;
                        else if ("Timeline Tooltip Text".equals(cst)) hasTooltipText = true;
                        else if ("Grid Line (Primary)".equals(cst)) hasGridPrimary = true;
                        else if ("Timeline Primary Grid".equals(cst)) hasTimelinePrimary = true;
                    }
                }

                if (hasTooltipBg && hasTooltipText && hasGridPrimary && !hasTimelinePrimary) {
                    this.timelineThemeClassNode = cn;
                    this.timelineInitMethod = mn;
                    return;
                }
            }
        }
    }

    private void findColorPaletteClass(HashMap<String, ClassNode> classNodes) {
        for (ClassNode cn : classNodes.values()) {
            for (MethodNode mn : cn.methods) {
                if ((mn.access & Opcodes.ACC_STATIC) != 0) continue;
                if (!mn.desc.equals("()V")) continue;

                boolean hasGrey2 = false;
                boolean hasGridPrimary = false;
                String foundGreyMethod = null;
                String foundGreyDesc = null;
                String foundRgbaMethod = null;
                String foundRgbaDesc = null;

                for (int i = 0; i < mn.instructions.size(); i++) {
                    AbstractInsnNode insn = mn.instructions.get(i);
                    if (insn.getOpcode() == Opcodes.LDC && insn instanceof LdcInsnNode) {
                        Object cst = ((LdcInsnNode) insn).cst;
                        if ("Grey 2".equals(cst)) {
                            hasGrey2 = true;
                            for (int j = i + 1; j < Math.min(mn.instructions.size(), i + 6); j++) {
                                AbstractInsnNode next = mn.instructions.get(j);
                                if (next instanceof MethodInsnNode) {
                                    MethodInsnNode mnext = (MethodInsnNode) next;
                                    if (mnext.owner.equals(cn.name) && mnext.desc.startsWith("(Ljava/lang/String;I)L")) {
                                        foundGreyMethod = mnext.name;
                                        foundGreyDesc = mnext.desc;
                                        break;
                                    }
                                }
                            }
                        } else if ("Grid Line (Primary)".equals(cst)) {
                            hasGridPrimary = true;
                            for (int j = i + 1; j < Math.min(mn.instructions.size(), i + 8); j++) {
                                AbstractInsnNode next = mn.instructions.get(j);
                                if (next instanceof MethodInsnNode) {
                                    MethodInsnNode mnext = (MethodInsnNode) next;
                                    if (mnext.owner.equals(cn.name) && mnext.desc.startsWith("(Ljava/lang/String;IIII)L")) {
                                        foundRgbaMethod = mnext.name;
                                        foundRgbaDesc = mnext.desc;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }

                if (hasGrey2 && hasGridPrimary && foundGreyMethod != null && foundRgbaMethod != null) {
                    this.paletteClassNode = cn;
                    this.paletteRegisterMethod = mn;
                    this.registerGreyMethod = foundGreyMethod;
                    this.registerGreyDesc = foundGreyDesc;
                    this.registerRgbaMethod = foundRgbaMethod;
                    this.registerRgbaDesc = foundRgbaDesc;
                    return;
                }
            }
        }
    }

    @Override
    protected void findMethodNode() {
        // Handled in constructor
    }

    @Override
    public void patch() {
        if (paletteRegisterMethod == null || timelineInitMethod == null || registerGreyDesc == null || registerRgbaDesc == null) {
            return;
        }

        String greyDesc = registerGreyDesc;
        String rgbaDesc = registerRgbaDesc;

        // 1. Register the 7 decoupled timeline colors in ColorPalette before RETURN
        AbstractInsnNode returnInsn = null;
        for (int i = paletteRegisterMethod.instructions.size() - 1; i >= 0; i--) {
            AbstractInsnNode insn = paletteRegisterMethod.instructions.get(i);
            if (insn.getOpcode() == Opcodes.RETURN) {
                returnInsn = insn;
                break;
            }
        }
        if (returnInsn == null) return;

        InsnList reg = new InsnList();
        String[] bgKeys = new String[]{
            "Top Level Timeline Background",
            "Dark Timeline Background",
            "Light Timeline Background",
            "Irrelevant Timeline Background",
            "Timeline Background Pattern"
        };
        for (String key : bgKeys) {
            reg.add(new VarInsnNode(Opcodes.ALOAD, 0));
            reg.add(new LdcInsnNode(key));
            reg.add(new IntInsnNode(Opcodes.BIPUSH, 38)); // #262626 (matches Bitwig 6 Grey 2)
            reg.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, paletteClassNode.name, registerGreyMethod, greyDesc, false));
            reg.add(new InsnNode(Opcodes.POP));
        }

        // Timeline Primary Grid: rgba(0, 0, 0, 255) (matches Bitwig 6 Grid Line (Primary))
        reg.add(new VarInsnNode(Opcodes.ALOAD, 0));
        reg.add(new LdcInsnNode("Timeline Primary Grid"));
        reg.add(new InsnNode(Opcodes.ICONST_0));
        reg.add(new InsnNode(Opcodes.ICONST_0));
        reg.add(new InsnNode(Opcodes.ICONST_0));
        reg.add(new IntInsnNode(Opcodes.SIPUSH, 255));
        reg.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, paletteClassNode.name, registerRgbaMethod, rgbaDesc, false));
        reg.add(new InsnNode(Opcodes.POP));

        // Timeline Secondary Grid: rgba(0, 0, 0, 80) (matches Bitwig 6 Grid Line (Secondary))
        reg.add(new VarInsnNode(Opcodes.ALOAD, 0));
        reg.add(new LdcInsnNode("Timeline Secondary Grid"));
        reg.add(new InsnNode(Opcodes.ICONST_0));
        reg.add(new InsnNode(Opcodes.ICONST_0));
        reg.add(new InsnNode(Opcodes.ICONST_0));
        reg.add(new IntInsnNode(Opcodes.BIPUSH, 80));
        reg.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, paletteClassNode.name, registerRgbaMethod, rgbaDesc, false));
        reg.add(new InsnNode(Opcodes.POP));

        paletteRegisterMethod.instructions.insertBefore(returnInsn, reg);

        // 2. Rewire TimelineTheme's LDCs from shared Grey 2 / Grid Line keys to the dedicated timeline keys
        int grey2Index = 0;
        int rewiredCount = 0;
        for (int i = 0; i < timelineInitMethod.instructions.size(); i++) {
            AbstractInsnNode insn = timelineInitMethod.instructions.get(i);
            if (insn.getOpcode() == Opcodes.LDC && insn instanceof LdcInsnNode) {
                LdcInsnNode ldc = (LdcInsnNode) insn;
                if ("Grey 2".equals(ldc.cst) && grey2Index < bgKeys.length) {
                    ldc.cst = bgKeys[grey2Index++];
                    rewiredCount++;
                } else if ("Grid Line (Primary)".equals(ldc.cst)) {
                    ldc.cst = "Timeline Primary Grid";
                    rewiredCount++;
                } else if ("Grid Line (Secondary)".equals(ldc.cst)) {
                    ldc.cst = "Timeline Secondary Grid";
                    rewiredCount++;
                }
            }
        }

        System.out.println(" -> Successfully decoupled " + rewiredCount + " Bitwig 6+ Timeline theme slots.");
    }
}
