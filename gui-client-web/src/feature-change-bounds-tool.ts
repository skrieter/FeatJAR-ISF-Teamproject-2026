import {
    Action,
    ChangeBoundsListener,
    ChangeBoundsTool,
    GModelElement,
    GNode,
    IMovementRestrictor,
    ISelectionListener,
    MouseListener,
    Point
} from '@eclipse-glsp/client';
import { injectable } from 'inversify';
import { isMovableNode, resolveMoveTarget } from './feature-move-target';
import { MoveNodeAction } from './move-node-action';

/**
 * Only features (except the root) and group nodes may be dragged. Dragging anything else,
 * such as the root or a constraint, is refused and the element jumps back.
 */
@injectable()
export class FeatureMoveRestrictor implements IMovementRestrictor {
    cssClasses = ['movement-not-allowed'];

    validate(element: GModelElement, newLocation?: Point): boolean {
        return newLocation !== undefined && isMovableNode(element);
    }
}

/**
 * GLSP's default tool reports a drag as new pixel bounds. This diagram is laid out
 * automatically from the tree, so pixel positions mean nothing here. This tool
 * reports a drag as a change of the tree instead (see {@link MoveNodeAction}).
 */
@injectable()
export class FeatureChangeBoundsTool extends ChangeBoundsTool {
    protected override createChangeBoundsListener(): MouseListener & ISelectionListener {
        return new FeatureChangeBoundsListener(this);
    }
}

export class FeatureChangeBoundsListener extends ChangeBoundsListener {
    protected override handleMoveOnServer(target: GModelElement): Action[] {
        const elementsToMove = this.getElementsToMove(target);
        if (elementsToMove.length === 0) {
            return [];
        }

        // Only one node can be moved at a time. For a multi-selection, or when there is nothing
        // to order against, an empty move is sent: the server rejects it and re-sends the
        // diagram, which puts the dragged nodes back.
        const moved = elementsToMove[0];
        const resolved = elementsToMove.length === 1 && moved instanceof GNode ? resolveMoveTarget(moved) : undefined;

        return [MoveNodeAction.create(moved.id, resolved?.targetId ?? '', resolved?.position ?? 'inside')];
    }
}