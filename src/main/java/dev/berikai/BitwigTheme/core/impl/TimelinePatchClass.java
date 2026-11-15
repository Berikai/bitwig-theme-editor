package dev.berikai.BitwigTheme.core.impl;

import dev.berikai.BitwigTheme.asm.JarNode;
import dev.berikai.BitwigTheme.core.PatchClass;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.HashMap;

public class TimelinePatchClass extends PatchClass {
    // In Bitwig Studio 6.0+, the Arranger TimelineTheme replaced dedicated timeline keys
    // with shared "Grey 2" and "Grid Line (Primary)/(Secondary)" keys, AND the actual
    // Arranger lane/grid painters (pTZ.jPB and bqn) bypass TimelineTheme completely:
    //   - pTZ.jPB(duK) computes the timeline background by lerping Grey 2 / Grey 3 / Grey 4.
    //   - bqn computes primary/secondary/sub-grid colors from that background color via bqq.
    // This patcher restores the 7 independent keys in ColorPalette, rewires TimelineTheme,
    // and hooks pTZ.jPB(duK) + bqn so Dark Timeline Background, Timeline Primary Grid, and
    // Timeline Secondary Grid directly control the Bitwig 6+ Arranger timeline.

    private ClassNode paletteClassNode;
    private MethodNode paletteRegisterMethod;
    private String registerGreyMethod;
    private String registerGreyDesc;
    private String registerRgbaMethod;
    private String registerRgbaDesc;

    private ClassNode timelineThemeClassNode;
    private MethodNode timelineInitMethod;

    // pTZ.jPB(duK) -> timeline background helper
    private ClassNode timelineBgHelperClass;
    private MethodNode timelineBgHelperMethod;
    private String widgetClassName;
    private String lookupColorMethod;
    private String lookupColorDesc;

    // bqn -> timeline grid line color provider
    private ClassNode timelineGridClass;
    private MethodNode timelineGrid5ArgMethod;
    private MethodNode timelineGrid4ArgMethod;
    private String getWidgetMethod;
    private String qflAlphaMethod;
    private String qflAlphaOwner;
    private String qflAlphaDesc;

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

        findTimelineBgHelper(classNodes);
        findTimelineGridClass(classNodes);

        this.classNode = timelineThemeClassNode;
        JarNode.getModifiedNodes().put(paletteClassNode.name, paletteClassNode);
        JarNode.getModifiedNodes().put(timelineThemeClassNode.name, timelineThemeClassNode);
        if (timelineBgHelperClass != null && timelineBgHelperMethod != null) {
            JarNode.getModifiedNodes().put(timelineBgHelperClass.name, timelineBgHelperClass);
        }
        if (timelineGridClass != null && timelineGrid5ArgMethod != null) {
            JarNode.getModifiedNodes().put(timelineGridClass.name, timelineGridClass);
        }
        System.out.println(" -> Found Bitwig 6+ TimelineTheme (" + timelineThemeClassNode.name + ") and ColorPalette (" + paletteClassNode.name + ").");
        if (timelineBgHelperClass != null) {
            System.out.println(" -> Found Timeline background helper (" + timelineBgHelperClass.name + "." + timelineBgHelperMethod.name + ").");
        }
        if (timelineGridClass != null) {
            System.out.println(" -> Found Timeline grid color provider (" + timelineGridClass.name + ").");
        }
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

    private void findTimelineBgHelper(HashMap<String, ClassNode> classNodes) {
        for (ClassNode cn : classNodes.values()) {
            for (MethodNode mn : cn.methods) {
                if ((mn.access & Opcodes.ACC_STATIC) == 0) continue;
                // Signature: (L<widgetBase>;)L<ColorClass>;
                if (!mn.desc.startsWith("(L") || !mn.desc.contains(";)L")) continue;

                boolean hasGrey2 = false;
                boolean hasGrey3 = false;
                boolean hasGrey4 = false;
                boolean has50f = false;
                String foundLookupMethod = null;
                String foundLookupDesc = null;
                String foundWidgetClass = null;

                for (int i = 0; i < mn.instructions.size(); i++) {
                    AbstractInsnNode insn = mn.instructions.get(i);
                    if (insn.getOpcode() == Opcodes.LDC && insn instanceof LdcInsnNode) {
                        Object cst = ((LdcInsnNode) insn).cst;
                        if ("Grey 2".equals(cst)) {
                            hasGrey2 = true;
                            if (i + 1 < mn.instructions.size() && mn.instructions.get(i + 1) instanceof MethodInsnNode) {
                                MethodInsnNode m = (MethodInsnNode) mn.instructions.get(i + 1);
                                if (m.desc.startsWith("(Ljava/lang/String;)L")) {
                                    foundWidgetClass = m.owner;
                                    foundLookupMethod = m.name;
                                    foundLookupDesc = m.desc;
                                }
                            }
                        } else if ("Grey 3".equals(cst)) {
                            hasGrey3 = true;
                        } else if ("Grey 4".equals(cst)) {
                            hasGrey4 = true;
                        } else if (Float.valueOf(50.0f).equals(cst)) {
                            has50f = true;
                        }
                    }
                }

                if (hasGrey2 && hasGrey3 && hasGrey4 && has50f && foundLookupMethod != null) {
                    this.timelineBgHelperClass = cn;
                    this.timelineBgHelperMethod = mn;
                    this.widgetClassName = foundWidgetClass;
                    this.lookupColorMethod = foundLookupMethod;
                    this.lookupColorDesc = foundLookupDesc;
                    return;
                }
            }
        }
    }

    private void findTimelineGridClass(HashMap<String, ClassNode> classNodes) {
        if (widgetClassName == null || lookupColorDesc == null) return;
        String colorRetDesc = lookupColorDesc.substring(lookupColorDesc.indexOf(')') + 1); // e.g. LQFL;
        String desc5 = "(ZZZ" + colorRetDesc + "Z)" + colorRetDesc;
        String desc4 = "(ZZ" + colorRetDesc + "Z)" + colorRetDesc;

        for (ClassNode cn : classNodes.values()) {
            MethodNode m5 = null;
            MethodNode m4 = null;
            String alphaMethod = null;
            String alphaOwner = null;
            String alphaDesc = null;

            for (MethodNode mn : cn.methods) {
                if ((mn.access & Opcodes.ACC_STATIC) != 0) continue;
                if (mn.desc.equals(desc5) && mn.instructions.size() < 20) {
                    m5 = mn;
                } else if (mn.desc.equals(desc4) && mn.instructions.size() < 20) {
                    for (int i = 0; i < mn.instructions.size(); i++) {
                        AbstractInsnNode insn = mn.instructions.get(i);
                        if (insn.getOpcode() == Opcodes.LDC && insn instanceof LdcInsnNode
                                && Float.valueOf(0.25f).equals(((LdcInsnNode) insn).cst)) {
                            m4 = mn;
                            if (i + 1 < mn.instructions.size() && mn.instructions.get(i + 1) instanceof MethodInsnNode) {
                                MethodInsnNode am = (MethodInsnNode) mn.instructions.get(i + 1);
                                alphaMethod = am.name;
                                alphaOwner = am.owner;
                                alphaDesc = am.desc;
                            }
                            break;
                        }
                    }
                }
            }

            if (m5 != null && m4 != null) {
                // Find ()L<widgetClassName>; on cn or its superclasses
                String foundGetWidget = null;
                String widgetRetDesc = "()L" + widgetClassName + ";";
                String cur = cn.name;
                while (cur != null && !cur.equals("java/lang/Object")) {
                    ClassNode curNode = classNodes.get(cur);
                    if (curNode == null) break;
                    for (MethodNode mn : curNode.methods) {
                        if ((mn.access & Opcodes.ACC_STATIC) == 0 && (mn.access & Opcodes.ACC_PUBLIC) != 0
                                && mn.desc.equals(widgetRetDesc)) {
                            foundGetWidget = mn.name;
                            break;
                        }
                    }
                    if (foundGetWidget != null) break;
                    cur = curNode.superName;
                }

                if (foundGetWidget != null) {
                    this.timelineGridClass = cn;
                    this.timelineGrid5ArgMethod = m5;
                    this.timelineGrid4ArgMethod = m4;
                    this.getWidgetMethod = foundGetWidget;
                    this.qflAlphaMethod = alphaMethod;
                    this.qflAlphaOwner = alphaOwner;
                    this.qflAlphaDesc = alphaDesc;
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

        // Timeline Primary Grid: rgba(0, 0, 0, 255)
        reg.add(new VarInsnNode(Opcodes.ALOAD, 0));
        reg.add(new LdcInsnNode("Timeline Primary Grid"));
        reg.add(new InsnNode(Opcodes.ICONST_0));
        reg.add(new InsnNode(Opcodes.ICONST_0));
        reg.add(new InsnNode(Opcodes.ICONST_0));
        reg.add(new IntInsnNode(Opcodes.SIPUSH, 255));
        reg.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, paletteClassNode.name, registerRgbaMethod, rgbaDesc, false));
        reg.add(new InsnNode(Opcodes.POP));

        // Timeline Secondary Grid: rgba(0, 0, 0, 80)
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

        // 3. Hook pTZ.jPB(duK) so Dark / Light Timeline Background override Grey 2/3/4 when customized to a non-default value
        String colorClassName = PatchClass.mappings.get("ColorClass");
        String colorIntField = PatchClass.mappings.get("ColorClass.colorIntegerValue");
        if (timelineBgHelperMethod != null && colorClassName != null && colorIntField != null) {
            InsnList bgHook = new InsnList();
            LabelNode checkLight = new LabelNode();
            LabelNode fallthrough = new LabelNode();

            bgHook.add(new VarInsnNode(Opcodes.ALOAD, 0));
            bgHook.add(new LdcInsnNode("Dark Timeline Background"));
            bgHook.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, widgetClassName, lookupColorMethod, lookupColorDesc, false));
            bgHook.add(new InsnNode(Opcodes.DUP));
            bgHook.add(new FieldInsnNode(Opcodes.GETFIELD, colorClassName, "isCustomized", "Z"));
            bgHook.add(new JumpInsnNode(Opcodes.IFEQ, checkLight));
            bgHook.add(new InsnNode(Opcodes.DUP));
            bgHook.add(new FieldInsnNode(Opcodes.GETFIELD, colorClassName, colorIntField, "I"));
            bgHook.add(new LdcInsnNode(0xff262626));
            bgHook.add(new JumpInsnNode(Opcodes.IF_ICMPEQ, checkLight));
            bgHook.add(new InsnNode(Opcodes.ARETURN));

            bgHook.add(checkLight);
            bgHook.add(new InsnNode(Opcodes.POP));
            bgHook.add(new VarInsnNode(Opcodes.ALOAD, 0));
            bgHook.add(new LdcInsnNode("Light Timeline Background"));
            bgHook.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, widgetClassName, lookupColorMethod, lookupColorDesc, false));
            bgHook.add(new InsnNode(Opcodes.DUP));
            bgHook.add(new FieldInsnNode(Opcodes.GETFIELD, colorClassName, "isCustomized", "Z"));
            bgHook.add(new JumpInsnNode(Opcodes.IFEQ, fallthrough));
            bgHook.add(new InsnNode(Opcodes.DUP));
            bgHook.add(new FieldInsnNode(Opcodes.GETFIELD, colorClassName, colorIntField, "I"));
            bgHook.add(new LdcInsnNode(0xff262626));
            bgHook.add(new JumpInsnNode(Opcodes.IF_ICMPEQ, fallthrough));
            bgHook.add(new InsnNode(Opcodes.ARETURN));

            bgHook.add(fallthrough);
            bgHook.add(new InsnNode(Opcodes.POP));

            timelineBgHelperMethod.instructions.insert(bgHook);
        }

        // 4. Hook bqn grid color methods so Timeline Primary/Secondary Grid override bqq calculation when non-default
        if (timelineGrid5ArgMethod != null && colorClassName != null && colorIntField != null) {
            InsnList g5Hook = new InsnList();
            LabelNode checkSecondary = new LabelNode();
            LabelNode fallthrough5 = new LabelNode();

            // Primary grid (iload_3 == 0)
            g5Hook.add(new VarInsnNode(Opcodes.ILOAD, 3));
            g5Hook.add(new JumpInsnNode(Opcodes.IFNE, checkSecondary));
            g5Hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
            g5Hook.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, timelineGridClass.name, getWidgetMethod, "()L" + widgetClassName + ";", false));
            g5Hook.add(new LdcInsnNode("Timeline Primary Grid"));
            g5Hook.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, widgetClassName, lookupColorMethod, lookupColorDesc, false));
            g5Hook.add(new InsnNode(Opcodes.DUP));
            g5Hook.add(new FieldInsnNode(Opcodes.GETFIELD, colorClassName, "isCustomized", "Z"));
            g5Hook.add(new JumpInsnNode(Opcodes.IFEQ, fallthrough5));
            g5Hook.add(new InsnNode(Opcodes.DUP));
            g5Hook.add(new FieldInsnNode(Opcodes.GETFIELD, colorClassName, colorIntField, "I"));
            g5Hook.add(new LdcInsnNode(0xff000000));
            g5Hook.add(new JumpInsnNode(Opcodes.IF_ICMPEQ, fallthrough5));
            g5Hook.add(new InsnNode(Opcodes.ARETURN));

            // Secondary grid (iload_3 != 0)
            g5Hook.add(checkSecondary);
            g5Hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
            g5Hook.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, timelineGridClass.name, getWidgetMethod, "()L" + widgetClassName + ";", false));
            g5Hook.add(new LdcInsnNode("Timeline Secondary Grid"));
            g5Hook.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, widgetClassName, lookupColorMethod, lookupColorDesc, false));
            g5Hook.add(new InsnNode(Opcodes.DUP));
            g5Hook.add(new FieldInsnNode(Opcodes.GETFIELD, colorClassName, "isCustomized", "Z"));
            g5Hook.add(new JumpInsnNode(Opcodes.IFEQ, fallthrough5));
            g5Hook.add(new InsnNode(Opcodes.DUP));
            g5Hook.add(new FieldInsnNode(Opcodes.GETFIELD, colorClassName, colorIntField, "I"));
            g5Hook.add(new LdcInsnNode(0x50000000));
            g5Hook.add(new JumpInsnNode(Opcodes.IF_ICMPEQ, fallthrough5));
            g5Hook.add(new InsnNode(Opcodes.ARETURN));

            g5Hook.add(fallthrough5);
            g5Hook.add(new InsnNode(Opcodes.POP));

            timelineGrid5ArgMethod.instructions.insert(g5Hook);

            // 4-arg sub-grid method: use Timeline Secondary Grid (scaled by 0.5f) if non-default
            if (timelineGrid4ArgMethod != null && qflAlphaMethod != null) {
                InsnList g4Hook = new InsnList();
                LabelNode fallthrough4 = new LabelNode();

                g4Hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                g4Hook.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, timelineGridClass.name, getWidgetMethod, "()L" + widgetClassName + ";", false));
                g4Hook.add(new LdcInsnNode("Timeline Secondary Grid"));
                g4Hook.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, widgetClassName, lookupColorMethod, lookupColorDesc, false));
                g4Hook.add(new InsnNode(Opcodes.DUP));
                g4Hook.add(new FieldInsnNode(Opcodes.GETFIELD, colorClassName, "isCustomized", "Z"));
                g4Hook.add(new JumpInsnNode(Opcodes.IFEQ, fallthrough4));
                g4Hook.add(new InsnNode(Opcodes.DUP));
                g4Hook.add(new FieldInsnNode(Opcodes.GETFIELD, colorClassName, colorIntField, "I"));
                g4Hook.add(new LdcInsnNode(0x50000000));
                g4Hook.add(new JumpInsnNode(Opcodes.IF_ICMPEQ, fallthrough4));
                g4Hook.add(new LdcInsnNode(0.5f));
                g4Hook.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, qflAlphaOwner, qflAlphaMethod, qflAlphaDesc, false));
                g4Hook.add(new InsnNode(Opcodes.ARETURN));

                g4Hook.add(fallthrough4);
                g4Hook.add(new InsnNode(Opcodes.POP));

                timelineGrid4ArgMethod.instructions.insert(g4Hook);
            }
        }

        System.out.println(" -> Successfully decoupled " + rewiredCount + " Bitwig 6+ Timeline theme slots.");
    }
}
