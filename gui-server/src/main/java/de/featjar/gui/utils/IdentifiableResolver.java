/*
 * Copyright (C) 2026 FeatJAR-Development-Team
 *
 * This file is part of FeatJAR-gui-server.
 *
 * gui-server is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3.0 of the License,
 * or (at your option) any later version.
 *
 * gui-server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with gui-server. If not, see <https://www.gnu.org/licenses/>.
 *
 * See <https://github.com/FeatureIDE> for further information.
 */
package de.featjar.gui.utils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import featJAR.Identifiable;
import java.util.Optional;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.glsp.server.emf.notation.EMFNotationModelState;
import featJAR.Feature;
import featJAR.FeatureModel;
import featJAR.GroupNode;
import java.util.HashSet;
import java.util.Set;
/**
 * Resolves GModel IDs to their semantic elements.
 * This is possible because the {@link FeatureModelIdGenerator} uses
 * the ID of the semantic model also as GModel ID. in the whole project is no distinction
 *
 * IDs that do not belong to a semantic element, such
 * as the graph root or the constraint box, resolve to an empty result.
 */
@Singleton
public class IdentifiableResolver {

    @Inject
    protected EMFNotationModelState modelState;

    /**
     * Resolves a GModel ID to its semantic element and returns it.
     *
     * @param id the semantic ID of the searched element
     * @return the identifiable object or an empty {@link Optional} if nothing is found.
     */
    public Optional<Identifiable> findById(final String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        EObject found = modelState.getSemanticModel().eResource().getEObject(id);
        return found instanceof Identifiable identifiable ? Optional.of(identifiable) : Optional.empty();
    }
        /**
     * Collects the names of all features from the semantic model.
     *
     *to check the references of a constraint:
     * {@link FeatureModelLabelEditValidator#findConstraintProblem(String, Set)}.
     * @return the names of all features, possibly empty
     */
    public Set<String> findFeatureNames() {
        Set<String> names = new HashSet<>();

        // If no model is loaded yet, we return the empty set
        Optional<FeatureModel> model = modelState.getSemanticModel(FeatureModel.class);
        if (model.isPresent()) {

            // the  model can have several root features so we go through all of them
            for (Feature root : model.get().getRoots()) {
                collectFeatureNames(root, names);
            }
        }
        return names;
    }

    /* Walks down the tree: feature -> its group nodes -> the features of each group node */
    private void collectFeatureNames(final Feature feature, final Set<String> names) {
        if (feature.getName() != null) {
            names.add(feature.getName());
        }
        for (GroupNode group : feature.getGroupNodeList()) {
            for (Feature child : group.getFeatureList()) {
                collectFeatureNames(child, names);
            }
        }
    }
}
