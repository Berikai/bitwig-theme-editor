package dev.berikai.BitwigTheme.core.impl;

import dev.berikai.BitwigTheme.asm.JarNode;
import dev.berikai.BitwigTheme.core.PatchClass;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class EQPatchClass extends PatchClass {
    // Note: In Bitwig Studio, EQ frequency bands and the spectrum curve don't originally use named theme palette colors
    // Instead, they use a procedural frequency-to-HSB mathematical formula:
    //    Hue = clamp((pitch - 44.0) * 3.2, 0.0, 260.0)
    //    Saturation = max(0.0, 0.85 - max(0.0, Hue - 170.0) * 0.01)
    //    Brightness = flag ? max(0.125, 1.0 - penalty * 0.005) : 1.0
    // That means these colors never touch ColorBridge, and their colorName was originally null/unnamed
    // We refer to this EQ frequency color generator class as EQClass

    public EQPatchClass(HashMap<String, ClassNode> classNodes) {
        // Call super class constructor
        super(classNodes, "EQClass");

        // Find the class that contains the unique double constants 44.0d, 3.2d, and 260.0d
        // This EQ curve and frequency color calculation class is the only class that contains all three constants across all versions, which makes our lives easier
        findClassNode(classNodes);

        // If classNode is null, inform the user
        if (classNode == null) {
            System.out.println("ERROR: EQ class not found. Your Bitwig Studio version may not be supported.");
            System.out.println(" -> Known working versions: 4.x, 5.x, 6.x");
            return;
        }

        // Find the method node that we will be injecting code into
        findMethodNode();
    }

    // Find the class containing the procedural frequency-to-HSB constants (44.0d, 3.2d, and 260.0d)
    private void findClassNode(HashMap<String, ClassNode> classNodes) {
        outer: for (ClassNode cn : classNodes.values()) {
            for (MethodNode mn : cn.methods) {
                boolean has44 = false;
                boolean has3_2 = false;
                boolean has260 = false;
                for (int i = 0; i < mn.instructions.size(); i++) {
                    AbstractInsnNode insn = mn.instructions.get(i);
                    if (insn.getOpcode() == Opcodes.LDC && insn instanceof LdcInsnNode) {
                        Object cst = ((LdcInsnNode) insn).cst;
                        if (Double.valueOf(44.0d).equals(cst)) has44 = true;
                        if (Double.valueOf(3.2d).equals(cst)) has3_2 = true;
                        if (Double.valueOf(260.0d).equals(cst)) has260 = true;
                    }
                }
                if (has44 && has3_2 && has260) {
                    this.classNode = cn;
                    PatchClass.mappings.put("EQClass", cn.name);
                    JarNode.getModifiedNodes().put(cn.name, cn);
                    break outer;
                }
            }
        }
    }

    // Let's find our target method that generates the procedural frequency color from pitch
    @Override
    protected void findMethodNode() {
        if (classNode == null) return;

        String colorClassName = PatchClass.mappings.get("ColorClass");
        if (colorClassName == null) return;

        // Target method signature: public static ColorClass (double pitch, boolean flag)
        String targetDesc = "(DZ)L" + colorClassName + ";";

        // Iterate through methods of class
        for (MethodNode mn : classNode.methods) {
            // Check if method is static, takes a double and boolean, and returns a ColorClass instance
            if ((mn.access & Opcodes.ACC_STATIC) != 0 && mn.desc.equals(targetDesc)) {
                // Confirm it contains the unique frequency constant 44.0d
                boolean has44 = false;
                for (int i = 0; i < mn.instructions.size(); i++) {
                    AbstractInsnNode insn = mn.instructions.get(i);
                    if (insn.getOpcode() == Opcodes.LDC && insn instanceof LdcInsnNode) {
                        if (Double.valueOf(44.0d).equals(((LdcInsnNode) insn).cst)) {
                            has44 = true;
                            break;
                        }
                    }
                }

                if (has44) {
                    // Assign found method to the methodNode field of PatchClass
                    this.methodNode = mn;

                    PatchClass.mappings.put("EQClass.getFrequencyColor", this.methodNode.name);
                    break;
                }
            }
        }
    }

    // The code we will inject here is responsible for assigning a name to the procedural color instance
    // We will inject logic to assign "colorInstance.colorName" based on the frequency pitch:
    //    pitch <= 24.0 -> "EQ Band 1"
    //    pitch <= 39.0 -> "EQ Band 2"
    //    pitch <= 57.0 -> "EQ Band 3"
    //    pitch <= 78.0 -> "EQ Band 4"
    //    pitch <= 101.0 -> "EQ Band 5"
    //    pitch <= 123.0 -> "EQ Band 6"
    //    pitch <= 140.0 -> "EQ Band 7"
    //    pitch > 140.0 -> "EQ Band 8"
    //    or "EQ Curve Line" when disableGradient is active
    // Once colorName is set, ColorPatchClass will automatically check theme.bte and replace it with user's theme color!
    @Override
    public void patch() {
        if (classNode == null || methodNode == null) {
            System.out.println("WARNING: Skipping EQ patch (class or method not identified).");
            return;
        }

        String colorClassName = PatchClass.mappings.get("ColorClass");
        if (colorClassName == null) return;

        // Get all return instructions of the method
        List<AbstractInsnNode> returns = new ArrayList<>();
        for (int i = methodNode.instructions.size() - 1; i >= 0; i--) {
            AbstractInsnNode insn = methodNode.instructions.get(i);
            int op = insn.getOpcode();
            if (op == Opcodes.ARETURN) {
                returns.add(insn);
            }
        }

        // Insert bytecode before each return
        for (AbstractInsnNode ret : returns) {
            InsnList il = new InsnList();
            writeBytecode(il, colorClassName);
            methodNode.instructions.insertBefore(ret, il);
        }
    }

    // --------- Bytecode writing section ---------

    // Write bytecode to be injected before returning the color
    private void writeBytecode(InsnList il, String colorClassName) {
        // Here is the java code:
        //    if (ColorClass.disableGradient) {
        //        colorInstance.colorName = "EQ Curve Line";
        //    } else if (pitch <= 24.0) {
        //        colorInstance.colorName = "EQ Band 1";
        //    } else if (pitch <= 39.0) {
        //        colorInstance.colorName = "EQ Band 2";
        //    } else if (pitch <= 57.0) {
        //        colorInstance.colorName = "EQ Band 3";
        //    } else if (pitch <= 78.0) {
        //        colorInstance.colorName = "EQ Band 4";
        //    } else if (pitch <= 101.0) {
        //        colorInstance.colorName = "EQ Band 5";
        //    } else if (pitch <= 123.0) {
        //        colorInstance.colorName = "EQ Band 6";
        //    } else if (pitch <= 140.0) {
        //        colorInstance.colorName = "EQ Band 7";
        //    } else {
        //        colorInstance.colorName = "EQ Band 8";
        //    }
        //
        // And, here is the corresponding bytecode:
        //    dup (duplicate ColorClass on stack: [color, color])
        //    getstatic ColorClass.disableGradient Z
        //    ifeq notDisabled
        //    ldc "EQ Curve Line"
        //    goto setName
        //    notDisabled:
        //    dload_0 (load pitch)
        //    ldc2_w 24.0d
        //    dcmpl
        //    ifgt checkBand2
        //    ldc "EQ Band 1"
        //    goto setName
        //    checkBand2:
        //    dload_0
        //    ldc2_w 39.0d
        //    dcmpl
        //    ifgt checkBand3
        //    ldc "EQ Band 2"
        //    goto setName
        //    checkBand3:
        //    dload_0
        //    ldc2_w 57.0d
        //    dcmpl
        //    ifgt checkBand4
        //    ldc "EQ Band 3"
        //    goto setName
        //    checkBand4:
        //    dload_0
        //    ldc2_w 78.0d
        //    dcmpl
        //    ifgt checkBand5
        //    ldc "EQ Band 4"
        //    goto setName
        //    checkBand5:
        //    dload_0
        //    ldc2_w 101.0d
        //    dcmpl
        //    ifgt checkBand6
        //    ldc "EQ Band 5"
        //    goto setName
        //    checkBand6:
        //    dload_0
        //    ldc2_w 123.0d
        //    dcmpl
        //    ifgt checkBand7
        //    ldc "EQ Band 6"
        //    goto setName
        //    checkBand7:
        //    dload_0
        //    ldc2_w 140.0d
        //    dcmpl
        //    ifgt checkBand8
        //    ldc "EQ Band 7"
        //    goto setName
        //    checkBand8:
        //    ldc "EQ Band 8"
        //    setName:
        //    putfield ColorClass.colorName Ljava/lang/String;

        LabelNode notDisabled = new LabelNode();
        LabelNode checkBand2 = new LabelNode();
        LabelNode checkBand3 = new LabelNode();
        LabelNode checkBand4 = new LabelNode();
        LabelNode checkBand5 = new LabelNode();
        LabelNode checkBand6 = new LabelNode();
        LabelNode checkBand7 = new LabelNode();
        LabelNode checkBand8 = new LabelNode();
        LabelNode setName = new LabelNode();

        il.add(new LabelNode());
        il.add(new InsnNode(Opcodes.DUP)); // Duplicate ColorClass on stack: [color, color]

        // Check if gradient is disabled globally via "Gradient: false"
        il.add(new FieldInsnNode(Opcodes.GETSTATIC, colorClassName, "disableGradient", "Z"));
        il.add(new JumpInsnNode(Opcodes.IFEQ, notDisabled));
        il.add(new LdcInsnNode("EQ Curve Line"));
        il.add(new JumpInsnNode(Opcodes.GOTO, setName));

        // Band 1: pitch <= 24.0 (~20 Hz)
        il.add(notDisabled);
        il.add(new VarInsnNode(Opcodes.DLOAD, 0)); // Load pitch (argument 0)
        il.add(new LdcInsnNode(24.0d));
        il.add(new InsnNode(Opcodes.DCMPL));
        il.add(new JumpInsnNode(Opcodes.IFGT, checkBand2));
        il.add(new LdcInsnNode("EQ Band 1"));
        il.add(new JumpInsnNode(Opcodes.GOTO, setName));

        // Band 2: 24.0 < pitch <= 39.0 (~50 Hz)
        il.add(checkBand2);
        il.add(new VarInsnNode(Opcodes.DLOAD, 0));
        il.add(new LdcInsnNode(39.0d));
        il.add(new InsnNode(Opcodes.DCMPL));
        il.add(new JumpInsnNode(Opcodes.IFGT, checkBand3));
        il.add(new LdcInsnNode("EQ Band 2"));
        il.add(new JumpInsnNode(Opcodes.GOTO, setName));

        // Band 3: 39.0 < pitch <= 57.0 (~120 Hz)
        il.add(checkBand3);
        il.add(new VarInsnNode(Opcodes.DLOAD, 0));
        il.add(new LdcInsnNode(57.0d));
        il.add(new InsnNode(Opcodes.DCMPL));
        il.add(new JumpInsnNode(Opcodes.IFGT, checkBand4));
        il.add(new LdcInsnNode("EQ Band 3"));
        il.add(new JumpInsnNode(Opcodes.GOTO, setName));

        // Band 4: 57.0 < pitch <= 78.0 (~400 Hz)
        il.add(checkBand4);
        il.add(new VarInsnNode(Opcodes.DLOAD, 0));
        il.add(new LdcInsnNode(78.0d));
        il.add(new InsnNode(Opcodes.DCMPL));
        il.add(new JumpInsnNode(Opcodes.IFGT, checkBand5));
        il.add(new LdcInsnNode("EQ Band 4"));
        il.add(new JumpInsnNode(Opcodes.GOTO, setName));

        // Band 5: 78.0 < pitch <= 101.0 (~1.45 kHz)
        il.add(checkBand5);
        il.add(new VarInsnNode(Opcodes.DLOAD, 0));
        il.add(new LdcInsnNode(101.0d));
        il.add(new InsnNode(Opcodes.DCMPL));
        il.add(new JumpInsnNode(Opcodes.IFGT, checkBand6));
        il.add(new LdcInsnNode("EQ Band 5"));
        il.add(new JumpInsnNode(Opcodes.GOTO, setName));

        // Band 6: 101.0 < pitch <= 123.0 (~5.45 kHz)
        il.add(checkBand6);
        il.add(new VarInsnNode(Opcodes.DLOAD, 0));
        il.add(new LdcInsnNode(123.0d));
        il.add(new InsnNode(Opcodes.DCMPL));
        il.add(new JumpInsnNode(Opcodes.IFGT, checkBand7));
        il.add(new LdcInsnNode("EQ Band 6"));
        il.add(new JumpInsnNode(Opcodes.GOTO, setName));

        // Band 7: 123.0 < pitch <= 140.0 (~18.1 kHz)
        il.add(checkBand7);
        il.add(new VarInsnNode(Opcodes.DLOAD, 0));
        il.add(new LdcInsnNode(140.0d));
        il.add(new InsnNode(Opcodes.DCMPL));
        il.add(new JumpInsnNode(Opcodes.IFGT, checkBand8));
        il.add(new LdcInsnNode("EQ Band 7"));
        il.add(new JumpInsnNode(Opcodes.GOTO, setName));

        // Band 8: pitch > 140.0
        il.add(checkBand8);
        il.add(new LdcInsnNode("EQ Band 8"));

        // Assign colorName field on ColorClass
        il.add(setName);
        il.add(new FieldInsnNode(Opcodes.PUTFIELD, colorClassName, "colorName", "Ljava/lang/String;"));
    }
}

