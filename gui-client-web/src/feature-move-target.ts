import { Bounds, GEdge, GModelElement, GNode, Point } from '@eclipse-glsp/client';
import { MovePosition } from './move-node-action';

/** Where a dragged node should end up, relative to another node. */
export interface MoveTarget {
    targetId: string;
    position: MovePosition;
}

const GROUP_NODE_CLASSES = ['node-and', 'node-or', 'node-xor', 'node-cardinality'];

/**
 * Share of a feature's width, measured from each side, that counts as "next to
 * it" (drop as its sibling) instead of "onto it" (drop as its child).
 */
const SIDE_ZONE = 0.3;

export function isFeatureNode(element: GModelElement): boolean {
    return element instanceof GNode && (element.cssClasses ?? []).some(css => css.startsWith('feature-'));
}

export function isRootFeatureNode(element: GModelElement): boolean {
    return element instanceof GNode && (element.cssClasses ?? []).includes('feature-root');
}

export function isGroupNode(element: GModelElement): boolean {
    return element instanceof GNode && (element.cssClasses ?? []).some(css => GROUP_NODE_CLASSES.includes(css));
}

/** Features (except the root) and group nodes can be moved; constraints and the root cannot. */
export function isMovableNode(element: GModelElement): boolean {
    return (isFeatureNode(element) && !isRootFeatureNode(element)) || isGroupNode(element);
}

/**
 * Decides where a dragged node belongs in the tree, based on where it was dropped.
 *
 * - Dropped onto another node: it becomes that node's child ("inside"), or, when
 *   dropped on the left/right edge of a feature, its sibling ("before"/"after").
 * - Dropped into free space: it keeps its parent and is only reordered among its
 *   siblings, according to how far left or right it was dropped.
 *
 * @returns the target, or undefined if the node has no siblings to be ordered against
 */
export function resolveMoveTarget(moved: GNode): MoveTarget | undefined {
    const point = center(moved.bounds);
    const nodes = Array.from(moved.root.index.all()).filter(
        (element): element is GNode => element !== moved && (isFeatureNode(element) || isGroupNode(element))
    );

    // If several nodes overlap the drop point, the smallest one is the most specific
    const hit = nodes.filter(node => contains(node.bounds, point)).sort((a, b) => area(a.bounds) - area(b.bounds))[0];

    return hit ? targetForHit(moved, hit, point) : targetBetweenSiblings(moved, point);
}

function targetForHit(moved: GNode, hit: GNode, point: Point): MoveTarget {
    const relativeX = (point.x - hit.bounds.x) / Math.max(hit.bounds.width, 1);

    if (isFeatureNode(moved)) {
        // A group node (or the root, which has no siblings) can only be joined as a child
        if (isGroupNode(hit) || isRootFeatureNode(hit)) {
            return { targetId: hit.id, position: 'inside' };
        }
        if (relativeX < SIDE_ZONE) {
            return { targetId: hit.id, position: 'before' };
        }
        if (relativeX > 1 - SIDE_ZONE) {
            return { targetId: hit.id, position: 'after' };
        }
        return { targetId: hit.id, position: 'inside' };
    }

    // A moved group node belongs to a feature, or next to another group node
    if (isFeatureNode(hit)) {
        return { targetId: hit.id, position: 'inside' };
    }
    return { targetId: hit.id, position: relativeX < 0.5 ? 'before' : 'after' };
}

/**
 * Finds the slot among the node's current siblings that matches the drop position.
 * Siblings are found through the edges of the diagram, which always run from a parent to its children.
 */
function targetBetweenSiblings(moved: GNode, point: Point): MoveTarget | undefined {
    const index = moved.root.index;
    const edges = Array.from(index.all()).filter((element): element is GEdge => element instanceof GEdge);

    const parentEdge = edges.find(edge => edge.targetId === moved.id);
    if (!parentEdge) {
        return undefined;
    }

    const siblings = edges
        .filter(edge => edge.sourceId === parentEdge.sourceId && edge.targetId !== moved.id)
        .map(edge => index.getById(edge.targetId))
        .filter((element): element is GNode => element instanceof GNode)
        .sort((a, b) => center(a.bounds).x - center(b.bounds).x);

    if (siblings.length === 0) {
        return undefined;
    }

    const next = siblings.find(sibling => center(sibling.bounds).x > point.x);
    return next ? { targetId: next.id, position: 'before' } : { targetId: siblings[siblings.length - 1].id, position: 'after' };
}

function center(bounds: Bounds): Point {
    return { x: bounds.x + bounds.width / 2, y: bounds.y + bounds.height / 2 };
}

function contains(bounds: Bounds, point: Point): boolean {
    return point.x >= bounds.x && point.x <= bounds.x + bounds.width && point.y >= bounds.y && point.y <= bounds.y + bounds.height;
}

function area(bounds: Bounds): number {
    return bounds.width * bounds.height;
}