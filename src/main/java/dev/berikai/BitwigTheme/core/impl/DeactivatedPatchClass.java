package dev.berikai.BitwigTheme.core.impl;

import dev.berikai.BitwigTheme.asm.JarNode;
import dev.berikai.BitwigTheme.core.PatchClass;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.HashMap;

public class DeactivatedPatchClass extends PatchClass {
    // Deactivated devices use a ColorFilter singleton (aBj in 6.1.5, auT in 6.0, QJ in 5.3.13, Ob in 4.3.10)
    // that desaturates to luminance and blends 0.5 toward hardcoded #323232 (50/255 = 0.19607843137254902).
    // Deactivated tracks, channels, and clips (5.3.13+) use:
    //   1) A static CompositeColorFilter (buD.wtf in 6.1.5, bnT.wiO in 6.0, aDR.lXt in 5.3.13) for clips/items.
    //   2) Widget.c_ (duK.c_ in 6.1.5, dmu.c_ in 6.0, cvG.c_ in 5.3.13) when `this instanceof buD` (track/channel headers).
    //      Other disabled UI controls (toolbar buttons, arm buttons, etc.) are NOT instances of buD and keep
    //      their stock va2() parent-background adaptation untouched.
    // This patcher hooks all three to resolve "Deactivated Overlay" (#RRGGBBAA):
    // RGB sets the tint target color, and Alpha (00..FF) controls the blend strength (0.0..1.0).
    private static final String KEY_NAME = "Deactivated Overlay";
    private static final int DEFAULT_ARGB_SENTINEL = 0x80262626; // #26262680
    private static final String CACHE_FIELD_NAME = "bteDeactivatedOverlayCache";

    private String deviceSingletonMethod;

    private String trackFilterHolderClass;
    private String trackFilterFieldName;
    private String trackFilterFieldDesc;
    private ClassNode compositeFilterClassNode;
    private MethodNode compositeTransformMethodNode;

    private ClassNode widgetClassNode;
    private MethodNode widgetDisabledMethodNode;
    private AbstractInsnNode widgetBgStoreInsn;
    private int widgetBgLocalVar = -1;
    private AbstractInsnNode widgetBlendLdcInsn;

    public DeactivatedPatchClass(HashMap<String, ClassNode> classNodes) {
        super(classNodes, "DeactivatedClass");

        findDeviceFilter(classNodes);
        if (this.classNode == null || this.methodNode == null || this.deviceSingletonMethod == null) {
            System.out.println("NOTE: Deactivated device filter class not found.");
            return;
        }

        JarNode.getModifiedNodes().put(this.classNode.name, this.classNode);
        System.out.println(" -> Found Deactivated device filter (" + this.classNode.name + "." + this.methodNode.name + ").");

        findTrackFilter(classNodes);
        if (this.compositeFilterClassNode != null && this.compositeTransformMethodNode != null) {
            JarNode.getModifiedNodes().put(this.compositeFilterClassNode.name, this.compositeFilterClassNode);
            System.out.println(" -> Found Deactivated track/clip filter (" + this.trackFilterHolderClass + "." + this.trackFilterFieldName + " -> " + this.compositeFilterClassNode.name + ").");

            findWidgetDisabledPaint(classNodes);
            if (this.widgetClassNode != null && this.widgetDisabledMethodNode != null) {
                JarNode.getModifiedNodes().put(this.widgetClassNode.name, this.widgetClassNode);
                System.out.println(" -> Found Deactivated track header hook (" + this.widgetClassNode.name + "." + this.widgetDisabledMethodNode.name + " instanceof " + this.trackFilterHolderClass + ").");
            }
        }
    }

    private void findDeviceFilter(HashMap<String, ClassNode> classNodes) {
        for (ClassNode cn : classNodes.values()) {
            MethodNode foundTransform = null;
            String foundSingleton = null;
            String expectedSingletonDesc = "()L" + cn.name + ";";

            for (MethodNode mn : cn.methods) {
                if ((mn.access & Opcodes.ACC_STATIC) != 0 && mn.desc.equals(expectedSingletonDesc)) {
                    foundSingleton = mn.name;
                    continue;
                }
                if ((mn.access & Opcodes.ACC_STATIC) == 0 && mn.desc.equals("(DDDD)[D")) {
                    boolean hasR = false;
                    boolean hasG = false;
                    boolean hasB = false;
                    boolean has05 = false;
                    boolean has50 = false;
                    for (AbstractInsnNode insn : mn.instructions) {
                        if (insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof Double) {
                            double val = (Double) ((LdcInsnNode) insn).cst;
                            if (val == 0.30000001192092896d) hasR = true;
                            else if (val == 0.5899999737739563d) hasG = true;
                            else if (val == 0.10999999940395355d) hasB = true;
                            else if (val == 0.5d) has05 = true;
                            else if (val == 0.19607843137254902d) has50 = true;
                        }
                    }
                    if (hasR && hasG && hasB && has05 && has50) {
                        foundTransform = mn;
                    }
                }
            }

            if (foundTransform != null && foundSingleton != null) {
                this.classNode = cn;
                this.methodNode = foundTransform;
                this.deviceSingletonMethod = foundSingleton;
                return;
            }
        }
    }

    private void findTrackFilter(HashMap<String, ClassNode> classNodes) {
        for (ClassNode cn : classNodes.values()) {
            for (MethodNode mn : cn.methods) {
                if (!mn.name.equals("<clinit>") || !mn.desc.equals("()V")) continue;

                boolean has066 = false;
                boolean has035 = false;
                FieldInsnNode putStaticField = null;

                for (AbstractInsnNode insn : mn.instructions) {
                    if (insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof Double) {
                        double val = (Double) ((LdcInsnNode) insn).cst;
                        if (val == 0.6600000262260437d) has066 = true;
                        else if (val == 0.3499999940395355d) has035 = true;
                    } else if (insn.getOpcode() == Opcodes.PUTSTATIC && insn instanceof FieldInsnNode) {
                        FieldInsnNode fin = (FieldInsnNode) insn;
                        if (fin.owner.equals(cn.name) && fin.desc.startsWith("L") && fin.desc.endsWith(";")) {
                            putStaticField = fin;
                        }
                    }
                }

                if (has066 && has035 && putStaticField != null) {
                    String compositeName = putStaticField.desc.substring(1, putStaticField.desc.length() - 1);
                    ClassNode compositeCn = classNodes.get(compositeName);
                    if (compositeCn != null) {
                        for (MethodNode cmn : compositeCn.methods) {
                            if ((cmn.access & Opcodes.ACC_STATIC) == 0 && cmn.desc.equals("(DDDD)[D")) {
                                this.trackFilterHolderClass = cn.name;
                                this.trackFilterFieldName = putStaticField.name;
                                this.trackFilterFieldDesc = putStaticField.desc;
                                this.compositeFilterClassNode = compositeCn;
                                this.compositeTransformMethodNode = cmn;
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    private void findWidgetDisabledPaint(HashMap<String, ClassNode> classNodes) {
        String bridgeClassName = PatchClass.mappings.get("ColorBridgeClass");
        String colorClassName = PatchClass.mappings.get("ColorClass");
        if (bridgeClassName == null || colorClassName == null) {
            return;
        }

        ClassNode bridgeCn = classNodes.get(bridgeClassName);
        if (bridgeCn == null) {
            return;
        }

        String colorDesc = "L" + colorClassName + ";";
        String widgetClassName = null;
        for (MethodNode mn : bridgeCn.methods) {
            if (mn.desc.startsWith("(L") && mn.desc.endsWith(")" + colorDesc)) {
                widgetClassName = mn.desc.substring(2, mn.desc.indexOf(';'));
                break;
            }
        }
        if (widgetClassName == null) {
            return;
        }

        ClassNode widgetCn = classNodes.get(widgetClassName);
        if (widgetCn == null) {
            return;
        }

        for (MethodNode mn : widgetCn.methods) {
            if ((mn.access & Opcodes.ACC_STATIC) != 0) continue;
            if (!mn.desc.startsWith("(L") || !mn.desc.endsWith(";)V")) continue;

            boolean has025 = false;
            boolean has050 = false;
            boolean hasAnewarray = false;
            AbstractInsnNode foundBgStore = null;
            int foundBgVar = -1;
            AbstractInsnNode foundBlendLdc = null;

            for (int i = 0; i < mn.instructions.size(); i++) {
                AbstractInsnNode insn = mn.instructions.get(i);
                if (insn.getOpcode() == Opcodes.ANEWARRAY) {
                    hasAnewarray = true;
                }
                if (insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof Double) {
                    double val = (Double) ((LdcInsnNode) insn).cst;
                    if (val == 0.25d) has025 = true;
                    if (val == 0.5d) {
                        has050 = true;
                        if (i + 1 < mn.instructions.size()) {
                            AbstractInsnNode next = mn.instructions.get(i + 1);
                            if (next.getOpcode() == Opcodes.INVOKESPECIAL && next instanceof MethodInsnNode) {
                                MethodInsnNode minsn = (MethodInsnNode) next;
                                if (minsn.name.equals("<init>") && minsn.desc.equals("(" + colorDesc + "D)V")) {
                                    foundBlendLdc = insn;
                                }
                            }
                        }
                    }
                }
                if (foundBgStore == null && insn.getOpcode() == Opcodes.INVOKEVIRTUAL && insn instanceof MethodInsnNode) {
                    MethodInsnNode minsn = (MethodInsnNode) insn;
                    if (minsn.owner.equals(widgetClassName) && minsn.desc.equals("()" + colorDesc)) {
                        if (i + 1 < mn.instructions.size() && mn.instructions.get(i + 1).getOpcode() == Opcodes.ASTORE) {
                            foundBgStore = mn.instructions.get(i + 1);
                            foundBgVar = ((VarInsnNode) foundBgStore).var;
                        }
                    }
                }
            }

            if (has025 && has050 && hasAnewarray && foundBgStore != null && foundBlendLdc != null) {
                this.widgetClassNode = widgetCn;
                this.widgetDisabledMethodNode = mn;
                this.widgetBgStoreInsn = foundBgStore;
                this.widgetBgLocalVar = foundBgVar;
                this.widgetBlendLdcInsn = foundBlendLdc;
                return;
            }
        }
    }

    @Override
    protected void findMethodNode() {
        // Handled in findDeviceFilter / findTrackFilter / findWidgetDisabledPaint
    }

    private void ensureCacheField(ClassNode owner, String colorClassName) {
        String fieldDesc = "L" + colorClassName + ";";
        for (FieldNode fn : owner.fields) {
            if (fn.name.equals(CACHE_FIELD_NAME)) return;
        }
        owner.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, CACHE_FIELD_NAME, fieldDesc, null, null));
    }

    private void appendCachedColorLookup(InsnList hook, String cacheOwner, String colorClassName, String getColorMethod,
                                         String getColorDesc, String colorIntField, LabelNode skipLabel) {
        String colorDesc = "L" + colorClassName + ";";
        LabelNode haveCachedLabel = new LabelNode();

        hook.add(new FieldInsnNode(Opcodes.GETSTATIC, cacheOwner, CACHE_FIELD_NAME, colorDesc));
        hook.add(new InsnNode(Opcodes.DUP));
        hook.add(new JumpInsnNode(Opcodes.IFNONNULL, haveCachedLabel));
        hook.add(new InsnNode(Opcodes.POP));

        hook.add(new TypeInsnNode(Opcodes.NEW, colorClassName));
        hook.add(new InsnNode(Opcodes.DUP));
        hook.add(new InsnNode(Opcodes.FCONST_0));
        hook.add(new InsnNode(Opcodes.FCONST_0));
        hook.add(new InsnNode(Opcodes.FCONST_0));
        hook.add(new InsnNode(Opcodes.FCONST_0));
        hook.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, colorClassName, "<init>", "(FFFF)V", false));
        hook.add(new InsnNode(Opcodes.DUP));
        hook.add(new LdcInsnNode(KEY_NAME));
        hook.add(new FieldInsnNode(Opcodes.PUTFIELD, colorClassName, "colorName", "Ljava/lang/String;"));
        hook.add(new InsnNode(Opcodes.DUP));
        hook.add(new FieldInsnNode(Opcodes.PUTSTATIC, cacheOwner, CACHE_FIELD_NAME, colorDesc));

        hook.add(haveCachedLabel);
        hook.add(new InsnNode(Opcodes.DUP));
        hook.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, colorClassName, getColorMethod, getColorDesc, false));
        if (!getColorDesc.endsWith(")V")) {
            hook.add(new InsnNode(Opcodes.POP));
        }

        hook.add(new InsnNode(Opcodes.DUP));
        hook.add(new FieldInsnNode(Opcodes.GETFIELD, colorClassName, "isCustomized", "Z"));
        hook.add(new JumpInsnNode(Opcodes.IFEQ, skipLabel));

        if (colorIntField != null) {
            hook.add(new InsnNode(Opcodes.DUP));
            hook.add(new FieldInsnNode(Opcodes.GETFIELD, colorClassName, colorIntField, "I"));
            hook.add(new LdcInsnNode(DEFAULT_ARGB_SENTINEL));
            hook.add(new JumpInsnNode(Opcodes.IF_ICMPEQ, skipLabel));
        }
    }

    @Override
    public void patch() {
        String colorClassName = PatchClass.mappings.get("ColorClass");
        String getColorMethod = PatchClass.mappings.get("ColorClass.getColorMethod");
        String getColorDesc = PatchClass.mappings.get("ColorClass.getColorDesc");
        String colorIntField = PatchClass.mappings.get("ColorClass.colorIntegerValue");
        String redField = PatchClass.mappings.get("ColorClass.redValue");
        String greenField = PatchClass.mappings.get("ColorClass.greenValue");
        String blueField = PatchClass.mappings.get("ColorClass.blueValue");
        String alphaFloatField = PatchClass.mappings.get("ColorClass.alphaValue");

        boolean hasIntColor = colorIntField != null;
        boolean hasFloatColor = redField != null && greenField != null && blueField != null && alphaFloatField != null;
        if (colorClassName == null || getColorMethod == null || getColorDesc == null
                || (!hasIntColor && !hasFloatColor)
                || this.classNode == null || this.methodNode == null || this.deviceSingletonMethod == null) {
            return;
        }

        ensureCacheField(this.classNode, colorClassName);
        patchDeviceFilter(colorClassName, getColorMethod, getColorDesc, colorIntField, redField, greenField, blueField, alphaFloatField);

        if (this.compositeFilterClassNode != null && this.compositeTransformMethodNode != null) {
            patchCompositeTrackFilter(colorClassName, getColorMethod, getColorDesc, colorIntField);
        }

        if (this.widgetClassNode != null && this.widgetDisabledMethodNode != null
                && this.widgetBgStoreInsn != null && this.widgetBlendLdcInsn != null) {
            patchWidgetDisabledPaint(colorClassName, getColorMethod, getColorDesc, colorIntField, alphaFloatField);
        }

        System.out.println(" -> Successfully patched Deactivated Track & Device filter hooks (Deactivated Overlay).");
    }

    private void patchDeviceFilter(String colorClassName, String getColorMethod, String getColorDesc,
                                   String colorIntField, String redField, String greenField, String blueField, String alphaFloatField) {
        int baseLocal = Math.max(this.methodNode.maxLocals, 9);
        int colorSlot = baseLocal;
        int lumSlot = baseLocal + 1;
        int alphaSlot = baseLocal + 3;
        int oneMinusAlphaSlot = baseLocal + 5;
        int targetRSlot = baseLocal + 7;
        int targetGSlot = baseLocal + 9;
        int targetBSlot = baseLocal + 11;
        this.methodNode.maxLocals = baseLocal + 13;

        InsnList hook = new InsnList();
        LabelNode skipPopLabel = new LabelNode();

        appendCachedColorLookup(hook, this.classNode.name, colorClassName, getColorMethod, getColorDesc, colorIntField, skipPopLabel);
        hook.add(new VarInsnNode(Opcodes.ASTORE, colorSlot));

        // lum = clamp(0.30 * r + 0.59 * g + 0.11 * b, 0.0, 1.0)
        hook.add(new LdcInsnNode(0.30000001192092896d));
        hook.add(new VarInsnNode(Opcodes.DLOAD, 1));
        hook.add(new InsnNode(Opcodes.DMUL));
        hook.add(new LdcInsnNode(0.5899999737739563d));
        hook.add(new VarInsnNode(Opcodes.DLOAD, 3));
        hook.add(new InsnNode(Opcodes.DMUL));
        hook.add(new InsnNode(Opcodes.DADD));
        hook.add(new LdcInsnNode(0.10999999940395355d));
        hook.add(new VarInsnNode(Opcodes.DLOAD, 5));
        hook.add(new InsnNode(Opcodes.DMUL));
        hook.add(new InsnNode(Opcodes.DADD));
        hook.add(new InsnNode(Opcodes.DCONST_0));
        hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Math", "max", "(DD)D", false));
        hook.add(new InsnNode(Opcodes.DCONST_1));
        hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Math", "min", "(DD)D", false));
        hook.add(new VarInsnNode(Opcodes.DSTORE, lumSlot));

        if (colorIntField != null) {
            hook.add(new VarInsnNode(Opcodes.ALOAD, colorSlot));
            hook.add(new FieldInsnNode(Opcodes.GETFIELD, colorClassName, colorIntField, "I"));
            hook.add(new InsnNode(Opcodes.DUP));
            hook.add(new IntInsnNode(Opcodes.BIPUSH, 24));
            hook.add(new InsnNode(Opcodes.IUSHR));
            hook.add(new IntInsnNode(Opcodes.SIPUSH, 255));
            hook.add(new InsnNode(Opcodes.IAND));
            hook.add(new InsnNode(Opcodes.I2D));
            hook.add(new LdcInsnNode(255.0d));
            hook.add(new InsnNode(Opcodes.DDIV));
            hook.add(new VarInsnNode(Opcodes.DSTORE, alphaSlot));

            hook.add(new InsnNode(Opcodes.DUP));
            hook.add(new IntInsnNode(Opcodes.BIPUSH, 16));
            hook.add(new InsnNode(Opcodes.IUSHR));
            hook.add(new IntInsnNode(Opcodes.SIPUSH, 255));
            hook.add(new InsnNode(Opcodes.IAND));
            hook.add(new InsnNode(Opcodes.I2D));
            hook.add(new LdcInsnNode(255.0d));
            hook.add(new InsnNode(Opcodes.DDIV));
            hook.add(new VarInsnNode(Opcodes.DSTORE, targetRSlot));

            hook.add(new InsnNode(Opcodes.DUP));
            hook.add(new IntInsnNode(Opcodes.BIPUSH, 8));
            hook.add(new InsnNode(Opcodes.IUSHR));
            hook.add(new IntInsnNode(Opcodes.SIPUSH, 255));
            hook.add(new InsnNode(Opcodes.IAND));
            hook.add(new InsnNode(Opcodes.I2D));
            hook.add(new LdcInsnNode(255.0d));
            hook.add(new InsnNode(Opcodes.DDIV));
            hook.add(new VarInsnNode(Opcodes.DSTORE, targetGSlot));

            hook.add(new IntInsnNode(Opcodes.SIPUSH, 255));
            hook.add(new InsnNode(Opcodes.IAND));
            hook.add(new InsnNode(Opcodes.I2D));
            hook.add(new LdcInsnNode(255.0d));
            hook.add(new InsnNode(Opcodes.DDIV));
            hook.add(new VarInsnNode(Opcodes.DSTORE, targetBSlot));
        } else {
            hook.add(new VarInsnNode(Opcodes.ALOAD, colorSlot));
            hook.add(new FieldInsnNode(Opcodes.GETFIELD, colorClassName, alphaFloatField, "F"));
            hook.add(new InsnNode(Opcodes.F2D));
            hook.add(new VarInsnNode(Opcodes.DSTORE, alphaSlot));

            hook.add(new VarInsnNode(Opcodes.ALOAD, colorSlot));
            hook.add(new FieldInsnNode(Opcodes.GETFIELD, colorClassName, redField, "F"));
            hook.add(new InsnNode(Opcodes.F2D));
            hook.add(new VarInsnNode(Opcodes.DSTORE, targetRSlot));

            hook.add(new VarInsnNode(Opcodes.ALOAD, colorSlot));
            hook.add(new FieldInsnNode(Opcodes.GETFIELD, colorClassName, greenField, "F"));
            hook.add(new InsnNode(Opcodes.F2D));
            hook.add(new VarInsnNode(Opcodes.DSTORE, targetGSlot));

            hook.add(new VarInsnNode(Opcodes.ALOAD, colorSlot));
            hook.add(new FieldInsnNode(Opcodes.GETFIELD, colorClassName, blueField, "F"));
            hook.add(new InsnNode(Opcodes.F2D));
            hook.add(new VarInsnNode(Opcodes.DSTORE, targetBSlot));
        }

        // oneMinusAlpha = 1.0 - alpha
        hook.add(new InsnNode(Opcodes.DCONST_1));
        hook.add(new VarInsnNode(Opcodes.DLOAD, alphaSlot));
        hook.add(new InsnNode(Opcodes.DSUB));
        hook.add(new VarInsnNode(Opcodes.DSTORE, oneMinusAlphaSlot));

        // return new double[] { lum * (1 - alpha) + targetR * alpha, ..., a }
        hook.add(new InsnNode(Opcodes.ICONST_4));
        hook.add(new IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_DOUBLE));

        int[] targetSlots = new int[]{targetRSlot, targetGSlot, targetBSlot};
        for (int i = 0; i < 3; i++) {
            hook.add(new InsnNode(Opcodes.DUP));
            hook.add(new IntInsnNode(Opcodes.BIPUSH, i));
            hook.add(new VarInsnNode(Opcodes.DLOAD, lumSlot));
            hook.add(new VarInsnNode(Opcodes.DLOAD, oneMinusAlphaSlot));
            hook.add(new InsnNode(Opcodes.DMUL));
            hook.add(new VarInsnNode(Opcodes.DLOAD, targetSlots[i]));
            hook.add(new VarInsnNode(Opcodes.DLOAD, alphaSlot));
            hook.add(new InsnNode(Opcodes.DMUL));
            hook.add(new InsnNode(Opcodes.DADD));
            hook.add(new InsnNode(Opcodes.DASTORE));
        }

        hook.add(new InsnNode(Opcodes.DUP));
        hook.add(new InsnNode(Opcodes.ICONST_3));
        hook.add(new VarInsnNode(Opcodes.DLOAD, 7));
        hook.add(new InsnNode(Opcodes.DASTORE));
        hook.add(new InsnNode(Opcodes.ARETURN));

        hook.add(skipPopLabel);
        hook.add(new InsnNode(Opcodes.POP));

        this.methodNode.instructions.insert(hook);
        this.methodNode.maxStack = Math.max(this.methodNode.maxStack, 8);
    }

    private void patchCompositeTrackFilter(String colorClassName, String getColorMethod, String getColorDesc, String colorIntField) {
        InsnList hook = new InsnList();
        LabelNode skipPopLabel = new LabelNode();
        LabelNode fallThroughLabel = new LabelNode();

        hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
        hook.add(new FieldInsnNode(Opcodes.GETSTATIC, this.trackFilterHolderClass, this.trackFilterFieldName, this.trackFilterFieldDesc));
        hook.add(new JumpInsnNode(Opcodes.IF_ACMPNE, fallThroughLabel));

        appendCachedColorLookup(hook, this.classNode.name, colorClassName, getColorMethod, getColorDesc, colorIntField, skipPopLabel);
        hook.add(new InsnNode(Opcodes.POP));

        hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, this.classNode.name, this.deviceSingletonMethod, "()L" + this.classNode.name + ";", false));
        hook.add(new VarInsnNode(Opcodes.DLOAD, 1));
        hook.add(new VarInsnNode(Opcodes.DLOAD, 3));
        hook.add(new VarInsnNode(Opcodes.DLOAD, 5));
        hook.add(new VarInsnNode(Opcodes.DLOAD, 7));
        hook.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, this.classNode.name, this.methodNode.name, "(DDDD)[D", false));
        hook.add(new InsnNode(Opcodes.ARETURN));

        hook.add(skipPopLabel);
        hook.add(new InsnNode(Opcodes.POP));
        hook.add(fallThroughLabel);

        this.compositeTransformMethodNode.instructions.insert(hook);
        this.compositeTransformMethodNode.maxStack = Math.max(this.compositeTransformMethodNode.maxStack, 9);
    }

    private void patchWidgetDisabledPaint(String colorClassName, String getColorMethod, String getColorDesc,
                                          String colorIntField, String alphaFloatField) {
        int blendSlot = this.widgetDisabledMethodNode.maxLocals;
        this.widgetDisabledMethodNode.maxLocals += 2;

        InsnList hook = new InsnList();
        LabelNode skipPopLabel = new LabelNode();
        LabelNode doneLabel = new LabelNode();

        // Default blend = 0.5d
        hook.add(new LdcInsnNode(0.5d));
        hook.add(new VarInsnNode(Opcodes.DSTORE, blendSlot));

        // Only apply Deactivated Overlay to track/channel headers (instanceof buD), not generic disabled buttons
        hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
        hook.add(new TypeInsnNode(Opcodes.INSTANCEOF, this.trackFilterHolderClass));
        hook.add(new JumpInsnNode(Opcodes.IFEQ, doneLabel));

        appendCachedColorLookup(hook, this.classNode.name, colorClassName, getColorMethod, getColorDesc, colorIntField, skipPopLabel);

        hook.add(new InsnNode(Opcodes.DUP));
        hook.add(new VarInsnNode(Opcodes.ASTORE, this.widgetBgLocalVar));
        if (colorIntField != null) {
            hook.add(new FieldInsnNode(Opcodes.GETFIELD, colorClassName, colorIntField, "I"));
            hook.add(new IntInsnNode(Opcodes.BIPUSH, 24));
            hook.add(new InsnNode(Opcodes.IUSHR));
            hook.add(new IntInsnNode(Opcodes.SIPUSH, 255));
            hook.add(new InsnNode(Opcodes.IAND));
            hook.add(new InsnNode(Opcodes.I2D));
            hook.add(new LdcInsnNode(255.0d));
            hook.add(new InsnNode(Opcodes.DDIV));
        } else {
            hook.add(new FieldInsnNode(Opcodes.GETFIELD, colorClassName, alphaFloatField, "F"));
            hook.add(new InsnNode(Opcodes.F2D));
        }
        hook.add(new VarInsnNode(Opcodes.DSTORE, blendSlot));
        hook.add(new JumpInsnNode(Opcodes.GOTO, doneLabel));

        hook.add(skipPopLabel);
        hook.add(new InsnNode(Opcodes.POP));
        hook.add(doneLabel);

        this.widgetDisabledMethodNode.instructions.insert(this.widgetBgStoreInsn, hook);
        this.widgetDisabledMethodNode.instructions.insertBefore(this.widgetBlendLdcInsn, new VarInsnNode(Opcodes.DLOAD, blendSlot));
        this.widgetDisabledMethodNode.instructions.remove(this.widgetBlendLdcInsn);
        this.widgetDisabledMethodNode.maxStack = Math.max(this.widgetDisabledMethodNode.maxStack, 7);
    }
}
