package dev.berikai.BitwigTheme.core.impl;

import dev.berikai.BitwigTheme.core.ThemeClass;
import org.objectweb.asm.tree.ClassNode;

import java.util.HashMap;

public class WindowThemeClass extends ThemeClass {
    public WindowThemeClass(HashMap<String, ClassNode> classNodes) {
        super(classNodes);
        findNodes("Default text");
    }

}
