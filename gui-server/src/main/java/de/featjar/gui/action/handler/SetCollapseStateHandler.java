// Generated with Claude Opus 5.5 (Anthropic, via claude.ai)
package de.featjar.gui.action.handler;

import com.google.inject.Inject;
import de.featjar.gui.action.SetCollapseStateAction;
import de.featjar.gui.utils.CollapseUtils;
import de.featjar.gui.utils.IdentifiableResolver;
import featJAR.Feature;
import featJAR.FeatureModel;
import featJAR.Identifiable;
import java.util.List;
import java.util.Optional;
import org.eclipse.glsp.server.actions.AbstractActionHandler;
import org.eclipse.glsp.server.actions.Action;
import org.eclipse.glsp.server.emf.notation.EMFNotationModelState;
import org.eclipse.glsp.server.features.core.model.ModelSubmissionHandler;

/**
 * Handles the {@link SetCollapseStateAction} and sends the updated diagram to the client.
 */
public class SetCollapseStateHandler extends AbstractActionHandler<SetCollapseStateAction> {

    @Inject
    protected EMFNotationModelState modelState;

    @Inject
    protected IdentifiableResolver resolver;

    @Inject
    protected ModelSubmissionHandler modelSubmissionHandler;

    @Override
    protected List<Action> executeAction(final SetCollapseStateAction action) {
        Optional<Feature> root = findRoot();
        if (root.isEmpty() || action.getMode() == null) {
            return none();
        }

        switch (action.getMode()) {
            case SetCollapseStateAction.COLLAPSE_ALL:
                CollapseUtils.collapseAll(modelState, root.get());
                break;
            case SetCollapseStateAction.COLLAPSE_ALL_BUT_ROOT:
                CollapseUtils.collapseAllButRoot(modelState, root.get());
                break;
            case SetCollapseStateAction.COLLAPSE_FROM_LEVEL:
                CollapseUtils.collapseFromLevel(modelState, root.get(), action.getLevel());
                break;
            case SetCollapseStateAction.EXPAND_ALL:
                CollapseUtils.expandAll(modelState);
                break;
            case SetCollapseStateAction.EXPAND_UP_TO_LEVEL:
                CollapseUtils.expandUpToLevel(modelState, root.get(), action.getLevel());
                break;
            case SetCollapseStateAction.EXPAND_SUBTREE:
                Optional<Identifiable> element =
                        resolver.findById(action.getElementId()).toOptional();
                if (element.isEmpty() || !(element.get() instanceof Feature feature)) {
                    return none();
                }
                CollapseUtils.expandSubtree(modelState, feature);
                break;
            default:
                return none();
        }
        return modelSubmissionHandler.submitModel();
    }

    /**
     * Returns the root feature of the feature model.
     */
    protected Optional<Feature> findRoot() {
        return modelState
                .getSemanticModel(FeatureModel.class)
                .filter(model -> !model.getRoots().isEmpty())
                .map(model -> model.getRoots().get(0));
    }
}
