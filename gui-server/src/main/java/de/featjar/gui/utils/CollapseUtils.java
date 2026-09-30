// Generated with Claude Opus 5.5 (Anthropic, via claude.ai)
package de.featjar.gui.utils;

import featJAR.Feature;
import featJAR.GroupNode;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import org.eclipse.glsp.server.model.GModelState;

/**
 * Stores which features are currently collapsed in the diagram.
 * Collapsing is only a view setting: the IDs are kept in the {@link GModelState}
 * of the current session and are neither part of the feature model nor saved to the file.
 */
public class CollapseUtils {

    public static final String COLLAPSED_FEATURES = "collapsedFeatures";
    public static final String CSS_COLLAPSIBLE = "collapsible";
    public static final String CSS_COLLAPSED = "collapsed";
    public static final String ARG_COLLAPSED_COUNT = "collapsedCount";

    private CollapseUtils() {}

    @SuppressWarnings("unchecked")
    public static Set<String> getCollapsedFeatures(final GModelState modelState) {
        Set<String> collapsed =
                modelState.getProperty(COLLAPSED_FEATURES, Set.class).orElse(null);
        if (collapsed == null) {
            collapsed = new LinkedHashSet<>();
            modelState.setProperty(COLLAPSED_FEATURES, collapsed);
        }
        return collapsed;
    }

    public static boolean isCollapsed(final GModelState modelState, final String featureId) {
        return getCollapsedFeatures(modelState).contains(featureId);
    }

    public static void toggle(final GModelState modelState, final String featureId) {
        Set<String> collapsed = getCollapsedFeatures(modelState);
        if (!collapsed.remove(featureId)) {
            collapsed.add(featureId);
        }
    }

    public static void expand(final GModelState modelState, final String featureId) {
        getCollapsedFeatures(modelState).remove(featureId);
    }
    /**
     * {@return whether the feature has child nodes and can therefore be collapsed}
     * Uses the same check as {@code FeatureModelGModelFactory}.
     *
     * @param feature the feature
     */
    public static boolean hasChildren(final Feature feature) {
        return !feature.getGroupNodeList().isEmpty();
    }

    /**
     * Collapses all features at the given level and below.
     */
    public static void collapseFromLevel(final GModelState modelState, final Feature root, final int level) {
        Set<String> collapsed = getCollapsedFeatures(modelState);
        visit(root, 0, (feature, depth) -> {
            if (depth >= level && hasChildren(feature)) {
                collapsed.add(feature.getId());
            }
        });
    }
    /**
     * Calls the visitor for the feature and all features below it.
     */
    protected static void visit(final Feature feature, final int depth, final BiConsumer<Feature, Integer> visitor) {
        visitor.accept(feature, depth);
        for (GroupNode groupNode : feature.getGroupNodeList()) {
            for (Feature child : groupNode.getFeatureList()) {
                visit(child, depth + 1, visitor);
            }
        }
    }
    /**
     * Collapses all features, including the root.
     */
    public static void collapseAll(final GModelState modelState, final Feature root) {
        collapseFromLevel(modelState, root, 0);
    }

    /**
     * Collapses all features except the root.
     */
    public static void collapseAllButRoot(final GModelState modelState, final Feature root) {
        collapseFromLevel(modelState, root, 1);
        expand(modelState, root.getId());
    }
    /**
     * Expands all features.
     */
    public static void expandAll(final GModelState modelState) {
        getCollapsedFeatures(modelState).clear();
    }

    /**
     * Expands all features above the given level.
     */
    public static void expandUpToLevel(final GModelState modelState, final Feature root, final int level) {
        Set<String> collapsed = getCollapsedFeatures(modelState);
        visit(root, 0, (feature, depth) -> {
            if (depth < level) {
                collapsed.remove(feature.getId());
            }
        });
    }

    /**
     * Expands the given feature and all features below it.
     */
    public static void expandSubtree(final GModelState modelState, final Feature subtreeRoot) {
        Set<String> collapsed = getCollapsedFeatures(modelState);
        visit(subtreeRoot, 0, (feature, depth) -> collapsed.remove(feature.getId()));
    }
}
