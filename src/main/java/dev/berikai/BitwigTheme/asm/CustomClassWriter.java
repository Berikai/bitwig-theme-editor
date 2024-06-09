package dev.berikai.BitwigTheme.asm;

import org.objectweb.asm.ClassWriter;

public class CustomClassWriter extends ClassWriter {

    public CustomClassWriter(final int flags) {
        super(flags);
    }

    protected String getCommonSuperClass(final String type1, final String type2) {
        try {
            return super.getCommonSuperClass(type1, type2);
        }
        catch (Throwable ex) {
            return "java/lang/Object";
        }
    }
}
