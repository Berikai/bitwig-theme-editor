package dev.berikai.BitwigTheme.core.impl;

import dev.berikai.BitwigTheme.core.ThemeClass;
import org.objectweb.asm.tree.ClassNode;

import java.util.HashMap;

public class ArrangerThemeClass extends ThemeClass {
    public ArrangerThemeClass(HashMap<String, ClassNode> classNodes) {
        super(classNodes);
        // We were using "Dark Timeline Background" string as a reference to find its node, to locate arranger theme class.
        // It seems like following 6.0 Beta 1 update, they seperated some group of color values into some bunch of classes.
        // Which means we need to find a pragmatical solution for backwards and onwords compatibility. (at least something that won't break things up)
        // I'm choosing "Time Selection Stroke" as a reference string just for now, as a workaround.
        findNodes("Time Selection Stroke");
    }


}
