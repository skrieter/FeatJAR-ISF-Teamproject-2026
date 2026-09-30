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
package de.featjar.gui.operation.handler;

import com.google.inject.Inject;
import de.featjar.base.data.Result;
import de.featjar.gui.operation.MoveNodeOperation;
import de.featjar.gui.utils.CardinalityUtils;
import de.featjar.gui.utils.CollapseUtils;
import de.featjar.gui.utils.IdentifiableResolver;
import featJAR.FeatJARFactory;
import featJAR.FeatJARPackage;
import featJAR.Feature;
import featJAR.GroupNode;
import featJAR.Identifiable;
import java.util.List;
import java.util.Optional;
import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.common.command.CompoundCommand;
import org.eclipse.emf.common.command.IdentityCommand;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.edit.command.AddCommand;
import org.eclipse.emf.edit.command.MoveCommand;
import org.eclipse.emf.edit.command.RemoveCommand;
import org.eclipse.emf.edit.domain.EditingDomain;
import org.eclipse.glsp.server.emf.EMFIdGenerator;
import org.eclipse.glsp.server.emf.EMFOperationHandler;
import org.eclipse.glsp.server.emf.notation.EMFNotationModelState;

/**
 * This file is Ai-Assisted
 * The handler moves a feature or group node within the tree: to another
 * position among its
 * siblings, or below a different parent.
 *
 * Features and group nodes strictly alternate in the tree (a feature holds
 * group nodes, a
 * group node holds features), so a node can only be moved below a parent of the
 * matching kind.
 * A feature that is dropped onto another feature joins that feature's AND
 * group, which is
 * created if there is none yet, the same way newly created features are added.
 *
 * A move that is not possible (for example into the node's own subtree) is no
 * error: it
 * becomes a command that changes nothing. GLSP sends the diagram again after
 * every command,
 * which puts the dragged node back where it was.
 */
public class MoveNodeHandler extends EMFOperationHandler<MoveNodeOperation> {

    @Inject
    protected IdentifiableResolver resolver;

    @Inject
    protected EMFNotationModelState modelState;

    @Inject
    protected EMFIdGenerator idGenerator;

    /**
     * The place a node is moved to: the list it is inserted into and the position
     * in that list.
     * If the list belongs to a group node that was just created for this move,
     * newGroupParent is
     * the feature the group node still has to be attached to.
     */
    private record Destination(EObject owner, EStructuralFeature reference, int index, Feature newGroupParent) {
    }

    @Override
    public Optional<Command> createCommand(final MoveNodeOperation operation) {
        return Optional.of(createMoveCommand(operation).orElse(IdentityCommand.INSTANCE));
    }

    protected Optional<Command> createMoveCommand(final MoveNodeOperation operation) {
        Result<Identifiable> movedResult = resolver.findById(operation.getElementId());
        Result<Identifiable> targetResult = resolver.findById(operation.getTargetId());
        if (movedResult.isEmpty() || targetResult.isEmpty()) {
            return Optional.empty();
        }
        Identifiable moved = movedResult.get();
        Identifiable target = targetResult.get();

        // A node can neither be moved onto itself nor into its own subtree
        if (isSameOrInside(target, moved)) {
            return Optional.empty();
        }

        Destination destination = findDestination(moved, target, operation.getPosition());
        EObject oldOwner = moved.eContainer();
        EStructuralFeature oldReference = moved.eContainingFeature();
        if (destination == null || oldOwner == null || oldReference == null) {
            return Optional.empty();
        }

        EditingDomain domain = modelState.getEditingDomain();
        expandDestination(destination);

        // Staying in the same list only changes the order
        if (destination.owner() == oldOwner && destination.reference() == oldReference) {
            List<?> siblings = (List<?>) oldOwner.eGet(oldReference);
            int oldIndex = siblings.indexOf(moved);
            // The node leaves its old place first, so every later position moves up by one
            int newIndex = destination.index() > oldIndex ? destination.index() - 1 : destination.index();
            if (newIndex == oldIndex) {
                return Optional.empty();
            }
            return Optional.of(MoveCommand.create(domain, oldOwner, oldReference, moved, newIndex));
        }

        CompoundCommand command = new CompoundCommand();
        command.append(RemoveCommand.create(domain, oldOwner, oldReference, moved));
        command.append(
                AddCommand.create(domain, destination.owner(), destination.reference(), moved, destination.index()));
        if (destination.newGroupParent() != null) {
            // The AND group was created just for this move, so it still has to be attached
            // to its feature
            command.append(AddCommand.create(
                    domain,
                    destination.newGroupParent(),
                    FeatJARPackage.Literals.FEATURE__GROUP_NODE_LIST,
                    destination.owner()));
        }
        return Optional.of(command);
    }

    /**
     * Works out where the moved node goes.
     *
     * @return the destination, or null if this kind of node can not be moved there
     */
    private Destination findDestination(final Identifiable moved, final Identifiable target, final String position) {
        boolean inside = false;
        boolean before = false;
        boolean after = false;

        switch (position) {
            case MoveNodeOperation.INSIDE -> inside = true;
            case MoveNodeOperation.BEFORE -> before = true;
            case MoveNodeOperation.AFTER -> after = true;
            default -> {
                return null;
            }
        }

        if (moved instanceof Feature) {
            // The root feature has no group node above it, so it stays where it is
            if (!(moved.eContainer() instanceof GroupNode)) {
                return null;
            }
            if (inside) {
                if (target instanceof GroupNode targetGroup) {
                    return new Destination(
                            targetGroup,
                            FeatJARPackage.Literals.GROUP_NODE__FEATURE_LIST,
                            targetGroup.getFeatureList().size(),
                            null);
                }
                if (target instanceof Feature targetFeature) {
                    return findAndGroup(targetFeature);
                }
                return null;
            }
            if (target instanceof Feature && target.eContainer() instanceof GroupNode siblingGroup) {
                int index = siblingGroup.getFeatureList().indexOf(target) + (after ? 1 : 0);
                return new Destination(siblingGroup, FeatJARPackage.Literals.GROUP_NODE__FEATURE_LIST, index, null);
            }
            return null;
        }

        if (moved instanceof GroupNode) {
            if (inside) {
                if (target instanceof Feature targetFeature) {
                    return new Destination(
                            targetFeature,
                            FeatJARPackage.Literals.FEATURE__GROUP_NODE_LIST,
                            targetFeature.getGroupNodeList().size(),
                            null);
                }
                return null;
            }
            if (target instanceof GroupNode && target.eContainer() instanceof Feature siblingParent) {
                int index = siblingParent.getGroupNodeList().indexOf(target) + (after ? 1 : 0);
                return new Destination(siblingParent, FeatJARPackage.Literals.FEATURE__GROUP_NODE_LIST, index, null);
            }
        }
        return null;
    }

    /**
     * Finds the AND group of the given feature, or prepares a new one if it has
     * none.
     */
    private Destination findAndGroup(final Feature parent) {
        for (GroupNode group : parent.getGroupNodeList()) {
            if (CardinalityUtils.isAnd(group.getCardinality())) {
                return new Destination(
                        group,
                        FeatJARPackage.Literals.GROUP_NODE__FEATURE_LIST,
                        group.getFeatureList().size(),
                        null);
            }
        }

        GroupNode andGroup = FeatJARFactory.eINSTANCE.createGroupNode();
        idGenerator.getOrCreateId(andGroup);
        andGroup.setName("");
        andGroup.setCardinality(CardinalityUtils.createAndCardinality());
        return new Destination(andGroup, FeatJARPackage.Literals.GROUP_NODE__FEATURE_LIST, 0, parent);
    }

    /**
     * A collapsed feature hides its subtree, so it is expanded to keep the moved
     * node visible.
     */
    private void expandDestination(final Destination destination) {
        EObject owner = destination.newGroupParent() != null ? destination.newGroupParent() : destination.owner();
        if (owner instanceof GroupNode) {
            owner = owner.eContainer();
        }
        if (owner instanceof Feature feature) {
            CollapseUtils.expand(modelState, feature.getId());
        }
    }

    /**
     * @return true if the node is the ancestor itself or lies somewhere below it
     */
    private static boolean isSameOrInside(final EObject node, final EObject ancestor) {
        for (EObject current = node; current != null; current = current.eContainer()) {
            if (current == ancestor) {
                return true;
            }
        }
        return false;
    }
}