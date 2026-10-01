// Generated with Claude Opus 5.5 (Anthropic, via claude.ai)
package de.featjar.gui.action;

import org.eclipse.glsp.server.actions.Action;

/**
 * Collapses or expands several features at once.
 */
public class SetCollapseStateAction extends Action {

    public static final String KIND = "setCollapseState";

    public static final String COLLAPSE_ALL = "collapseAll";
    public static final String COLLAPSE_ALL_BUT_ROOT = "collapseAllButRoot";
    public static final String COLLAPSE_FROM_LEVEL = "collapseFromLevel";
    public static final String EXPAND_ALL = "expandAll";
    public static final String EXPAND_UP_TO_LEVEL = "expandUpToLevel";
    public static final String EXPAND_SUBTREE = "expandSubtree";

    private String mode;
    private int level;
    private String elementId;

    public SetCollapseStateAction() {
        super(KIND);
    }

    public String getMode() {
        return mode;
    }

    public void setMode(final String mode) {
        this.mode = mode;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(final int level) {
        this.level = level;
    }

    public String getElementId() {
        return elementId;
    }

    public void setElementId(final String elementId) {
        this.elementId = elementId;
    }
}
