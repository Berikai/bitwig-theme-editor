package dev.berikai.BitwigTheme.core.advanced;

import dev.berikai.BitwigTheme.core.BitwigColor;
import dev.berikai.BitwigTheme.core.advanced.components.TimelinePlayheadClass;
import org.objectweb.asm.tree.ClassNode;

import java.util.HashMap;

public class AdvancedThemeManagerClass {
    private final HashMap<String, ClassNode> classNodes;

    public AdvancedThemeManagerClass(HashMap<String, ClassNode> classNodes) {
        this.classNodes = classNodes;
    }

    public void setTheme(HashMap<String, BitwigColor> theme) {
        if (theme == null) {
            return;
        }

        for (String key : theme.keySet()) {
            if (key.equals("Timeline Playhead"))
                new TimelinePlayheadClass(classNodes).setTheme(theme);
        }
    }

    public HashMap<String, BitwigColor> getTheme() {
        HashMap<String, BitwigColor> theme_bundled = new HashMap<>();

        TimelinePlayheadClass timelinePlayheadClass = new TimelinePlayheadClass(classNodes);

        for (String key : timelinePlayheadClass.getTheme().keySet()) {
            if (key.equals("Timeline Playhead"))
                theme_bundled.put("Timeline Playhead", timelinePlayheadClass.getTheme().get("Timeline Playhead"));
        }

        return theme_bundled;
    }
}
