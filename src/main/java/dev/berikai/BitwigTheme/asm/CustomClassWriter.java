package dev.berikai.BitwigTheme.asm;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class CustomClassWriter extends ClassWriter {
    private final HashMap<String, ClassNode> nodes;

    public CustomClassWriter(final int flags) {
        this(flags, null);
    }

    public CustomClassWriter(final int flags, final HashMap<String, ClassNode> nodes) {
        super(flags);
        this.nodes = nodes;
    }

    @Override
    protected String getCommonSuperClass(final String type1, final String type2) {
        if (type1.equals(type2)) {
            return type1;
        }
        if ("java/lang/Object".equals(type1) || "java/lang/Object".equals(type2)) {
            return "java/lang/Object";
        }

        if (nodes != null && (nodes.containsKey(type1) || nodes.containsKey(type2))) {
            if (isInterface(type1) || isInterface(type2)) {
                if (isAssignableFrom(type1, type2)) {
                    return type1;
                }
                if (isAssignableFrom(type2, type1)) {
                    return type2;
                }
                return "java/lang/Object";
            }

            Set<String> supers = new HashSet<>();
            String cur = type1;
            while (cur != null) {
                supers.add(cur);
                cur = getSuperName(cur);
            }
            cur = type2;
            while (cur != null) {
                if (supers.contains(cur)) {
                    return cur;
                }
                cur = getSuperName(cur);
            }
            return "java/lang/Object";
        }

        try {
            return super.getCommonSuperClass(type1, type2);
        } catch (Throwable ex) {
            return "java/lang/Object";
        }
    }

    private boolean isInterface(final String type) {
        if (nodes != null) {
            ClassNode cn = nodes.get(type);
            if (cn != null) {
                return (cn.access & Opcodes.ACC_INTERFACE) != 0;
            }
        }
        try {
            return Class.forName(type.replace('/', '.'), false, getClassLoader()).isInterface();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private String getSuperName(final String type) {
        if ("java/lang/Object".equals(type)) {
            return null;
        }
        if (nodes != null) {
            ClassNode cn = nodes.get(type);
            if (cn != null) {
                return cn.superName;
            }
        }
        try {
            Class<?> cls = Class.forName(type.replace('/', '.'), false, getClassLoader());
            Class<?> sup = cls.getSuperclass();
            return sup != null ? sup.getName().replace('.', '/') : null;
        } catch (Throwable ignored) {
            return "java/lang/Object";
        }
    }

    private boolean isAssignableFrom(final String target, final String source) {
        if (target.equals(source)) {
            return true;
        }
        if (source == null || "java/lang/Object".equals(source)) {
            return "java/lang/Object".equals(target);
        }
        if (nodes != null) {
            ClassNode cn = nodes.get(source);
            if (cn != null) {
                if (cn.interfaces != null) {
                    for (String iface : cn.interfaces) {
                        if (isAssignableFrom(target, iface)) {
                            return true;
                        }
                    }
                }
                return isAssignableFrom(target, cn.superName);
            }
        }
        try {
            Class<?> t = Class.forName(target.replace('/', '.'), false, getClassLoader());
            Class<?> s = Class.forName(source.replace('/', '.'), false, getClassLoader());
            return t.isAssignableFrom(s);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
