package dev.berikai.BitwigTheme.core.impl;

import dev.berikai.BitwigTheme.asm.JarNode;
import dev.berikai.BitwigTheme.core.PatchClass;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TunerPatchClass extends PatchClass {
    // In Bitwig Studio 6.1+, the Tuner device renders its display ("TunerDisplay") and history
    // ("TunerHistory") via native GPU shaders ("TunerVisualizer" and "TunerHistoryVisualizer").
    // We locate the widget peer classes invoking those shaders, and paint the container background
    // with a dedicated cached "Tuner Background" key (refreshed at most once every 3000ms).

    private final List<ClassNode> targetClassNodes = new ArrayList<>();
    private final Set<String> shaderClassNames = new HashSet<>();

    private String cpoName = "com/bitwig/graphics/CPO";
    private String rectMethod;
    private String setColorMethod;
    private boolean setColorIsInterface = true;
    private String fillMethod;

    public TunerPatchClass(HashMap<String, ClassNode> classNodes) {
        super(classNodes, "TunerClass");

        findShaderClasses(classNodes);
        if (shaderClassNames.isEmpty()) {
            return;
        }

        findGraphicsMethods(classNodes);
        if (rectMethod == null || setColorMethod == null || fillMethod == null) {
            System.out.println("WARNING: Could not identify CPO graphics methods for Tuner patch.");
            return;
        }

        findWidgetClasses(classNodes);
        if (targetClassNodes.isEmpty()) {
            return;
        }

        this.classNode = targetClassNodes.get(0);
        System.out.println(" -> Found " + targetClassNodes.size() + " Tuner visualizer widget classes.");
    }

    private void findShaderClasses(HashMap<String, ClassNode> classNodes) {
        for (ClassNode cn : classNodes.values()) {
            for (MethodNode mn : cn.methods) {
                for (int i = 0; i < mn.instructions.size(); i++) {
                    AbstractInsnNode insn = mn.instructions.get(i);
                    if (insn.getOpcode() == Opcodes.LDC && insn instanceof LdcInsnNode) {
                        Object cst = ((LdcInsnNode) insn).cst;
                        if ("TunerVisualizer".equals(cst) || "TunerHistoryVisualizer".equals(cst)) {
                            shaderClassNames.add(cn.name);
                        }
                    }
                }
            }
        }
    }

    private void findGraphicsMethods(HashMap<String, ClassNode> classNodes) {
        String colorClassName = PatchClass.mappings.get("ColorClass");
        if (colorClassName == null) return;
        String colorDesc = "L" + colorClassName + ";";

        for (ClassNode cn : classNodes.values()) {
            for (MethodNode mn : cn.methods) {
                if ((mn.access & Opcodes.ACC_STATIC) != 0) continue;
                if (!mn.desc.equals("(L" + cpoName + ";)V")) continue;

                for (int i = 0; i < mn.instructions.size(); i++) {
                    AbstractInsnNode insn = mn.instructions.get(i);
                    if (insn instanceof MethodInsnNode) {
                        MethodInsnNode rectCall = (MethodInsnNode) insn;
                        if (rectCall.owner.equals(cpoName) && rectCall.desc.equals("(JJJJ)V")) {
                            // Look ahead for setColor(ColorClass) and fill()
                            String foundSetColor = null;
                            boolean foundSetColorItf = true;
                            String foundFill = null;
                            for (int j = i + 1; j < Math.min(mn.instructions.size(), i + 10); j++) {
                                AbstractInsnNode next = mn.instructions.get(j);
                                if (next instanceof MethodInsnNode) {
                                    MethodInsnNode mnext = (MethodInsnNode) next;
                                    if (mnext.owner.equals(cpoName) && mnext.desc.equals("(" + colorDesc + ")V") && foundSetColor == null) {
                                        foundSetColor = mnext.name;
                                        foundSetColorItf = (mnext.getOpcode() == Opcodes.INVOKEINTERFACE);
                                    } else if (mnext.owner.equals(cpoName) && mnext.desc.equals("()V") && foundSetColor != null) {
                                        foundFill = mnext.name;
                                        break;
                                    }
                                }
                            }
                            if (foundSetColor != null && foundFill != null) {
                                this.rectMethod = rectCall.name;
                                this.setColorMethod = foundSetColor;
                                this.setColorIsInterface = foundSetColorItf;
                                this.fillMethod = foundFill;
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    private void findWidgetClasses(HashMap<String, ClassNode> classNodes) {
        for (ClassNode cn : classNodes.values()) {
            for (MethodNode mn : cn.methods) {
                if ((mn.access & Opcodes.ACC_STATIC) != 0) continue;
                if (!mn.desc.equals("(L" + cpoName + ";)V")) continue;

                boolean callsShader = false;
                for (int i = 0; i < mn.instructions.size(); i++) {
                    AbstractInsnNode insn = mn.instructions.get(i);
                    if (insn instanceof MethodInsnNode) {
                        MethodInsnNode minsn = (MethodInsnNode) insn;
                        if (shaderClassNames.contains(minsn.owner) && minsn.desc.contains("(L" + cpoName + ";")) {
                            callsShader = true;
                            break;
                        }
                    }
                }
                if (callsShader) {
                    targetClassNodes.add(cn);
                    JarNode.getModifiedNodes().put(cn.name, cn);
                    break;
                }
            }
        }
    }

    @Override
    protected void findMethodNode() {
        // Multi-class patch: handled directly in patch()
    }

    @Override
    public void patch() {
        String colorClassName = PatchClass.mappings.get("ColorClass");
        String getColorMethod = PatchClass.mappings.get("ColorClass.getColorMethod");
        String getColorDesc = PatchClass.mappings.get("ColorClass.getColorDesc");

        if (colorClassName == null || getColorMethod == null || getColorDesc == null) {
            return;
        }

        String colorDesc = "L" + colorClassName + ";";
        int patchedCount = 0;

        for (ClassNode cn : targetClassNodes) {
            // Add static cache fields to widget peer class
            cn.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "bte_tunerBg", colorDesc, null, null));
            cn.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "bte_lastFetch", "J", null, null));

            for (MethodNode mn : cn.methods) {
                if ((mn.access & Opcodes.ACC_STATIC) != 0) continue;
                if (!mn.desc.equals("(L" + cpoName + ";)V")) continue;

                // Locate super.paint(CPO) and the width/height ()J getters before L2I
                MethodInsnNode superPaint = null;
                String widthMethod = null;
                String heightMethod = null;

                for (int i = 0; i < mn.instructions.size(); i++) {
                    AbstractInsnNode insn = mn.instructions.get(i);
                    if (insn.getOpcode() == Opcodes.INVOKESPECIAL && insn instanceof MethodInsnNode) {
                        MethodInsnNode minsn = (MethodInsnNode) insn;
                        if (minsn.owner.equals(cn.superName) && minsn.desc.equals(mn.desc) && superPaint == null) {
                            superPaint = minsn;
                        }
                    } else if (insn.getOpcode() == Opcodes.INVOKEVIRTUAL && insn instanceof MethodInsnNode) {
                        MethodInsnNode minsn = (MethodInsnNode) insn;
                        if (minsn.desc.equals("()J") && i + 1 < mn.instructions.size() && mn.instructions.get(i + 1).getOpcode() == Opcodes.L2I) {
                            if (widthMethod == null) {
                                widthMethod = minsn.name;
                            } else if (heightMethod == null) {
                                heightMethod = minsn.name;
                            }
                        }
                    }
                }

                if (superPaint == null || widthMethod == null || heightMethod == null) {
                    continue;
                }

                InsnList hook = new InsnList();
                LabelNode hasCache = new LabelNode();
                LabelNode skipFetch = new LabelNode();
                LabelNode skipDraw = new LabelNode();

                // 1. Initialize cached ColorClass instance if null
                hook.add(new FieldInsnNode(Opcodes.GETSTATIC, cn.name, "bte_tunerBg", colorDesc));
                hook.add(new JumpInsnNode(Opcodes.IFNONNULL, hasCache));
                hook.add(new TypeInsnNode(Opcodes.NEW, colorClassName));
                hook.add(new InsnNode(Opcodes.DUP));
                hook.add(new InsnNode(Opcodes.FCONST_0));
                hook.add(new InsnNode(Opcodes.FCONST_0));
                hook.add(new InsnNode(Opcodes.FCONST_0));
                hook.add(new InsnNode(Opcodes.FCONST_0));
                hook.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, colorClassName, "<init>", "(FFFF)V", false));
                hook.add(new InsnNode(Opcodes.DUP));
                hook.add(new LdcInsnNode("Tuner Background"));
                hook.add(new FieldInsnNode(Opcodes.PUTFIELD, colorClassName, "colorName", "Ljava/lang/String;"));
                hook.add(new FieldInsnNode(Opcodes.PUTSTATIC, cn.name, "bte_tunerBg", colorDesc));
                hook.add(hasCache);

                // 2. Refresh cache at most once every 3000ms
                hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/System", "currentTimeMillis", "()J", false));
                hook.add(new FieldInsnNode(Opcodes.GETSTATIC, cn.name, "bte_lastFetch", "J"));
                hook.add(new InsnNode(Opcodes.LSUB));
                hook.add(new LdcInsnNode(3000L));
                hook.add(new InsnNode(Opcodes.LCMP));
                hook.add(new JumpInsnNode(Opcodes.IFLE, skipFetch));
                hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/System", "currentTimeMillis", "()J", false));
                hook.add(new FieldInsnNode(Opcodes.PUTSTATIC, cn.name, "bte_lastFetch", "J"));
                hook.add(new FieldInsnNode(Opcodes.GETSTATIC, cn.name, "bte_tunerBg", colorDesc));
                hook.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, colorClassName, getColorMethod, getColorDesc, false));
                hook.add(new InsnNode(Opcodes.POP));
                hook.add(skipFetch);

                // 3. If customized, fill background rectangle (0, 0, width, height)
                hook.add(new FieldInsnNode(Opcodes.GETSTATIC, cn.name, "bte_tunerBg", colorDesc));
                hook.add(new FieldInsnNode(Opcodes.GETFIELD, colorClassName, "isCustomized", "Z"));
                hook.add(new JumpInsnNode(Opcodes.IFEQ, skipDraw));

                hook.add(new VarInsnNode(Opcodes.ALOAD, 1)); // CPO graphics
                hook.add(new InsnNode(Opcodes.LCONST_0));
                hook.add(new InsnNode(Opcodes.LCONST_0));
                hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                hook.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, cn.name, widthMethod, "()J", false));
                hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                hook.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, cn.name, heightMethod, "()J", false));
                hook.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, cpoName, rectMethod, "(JJJJ)V", true));

                hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
                hook.add(new FieldInsnNode(Opcodes.GETSTATIC, cn.name, "bte_tunerBg", colorDesc));
                hook.add(new MethodInsnNode(setColorIsInterface ? Opcodes.INVOKEINTERFACE : Opcodes.INVOKEVIRTUAL,
                        cpoName, setColorMethod, "(" + colorDesc + ")V", setColorIsInterface));

                hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
                hook.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, cpoName, fillMethod, "()V", true));

                hook.add(skipDraw);

                mn.instructions.insert(superPaint, hook);
                patchedCount++;
                break;
            }
        }

        if (patchedCount > 0) {
            System.out.println(" -> Successfully patched " + patchedCount + " Tuner background hooks.");
        }
    }
}
