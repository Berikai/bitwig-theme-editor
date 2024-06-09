package dev.berikai.BitwigTheme.core.impl;

import dev.berikai.BitwigTheme.core.ThemeClass;
import org.objectweb.asm.tree.ClassNode;

import java.util.HashMap;

public class ArrangerThemeClass extends ThemeClass {
    public ArrangerThemeClass(HashMap<String, ClassNode> classNodes) {
        super(classNodes);
        findNodes("Dark Timeline Background");
    }


}
