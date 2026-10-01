import { Action } from '@eclipse-glsp/client';

/**
 * Where the moved node ends up, relative to the target node:
 * next to it (before / after, as its sibling) or below it (inside, as its child).
 */
export type MovePosition = 'before' | 'after' | 'inside';

/**
 * Moves a feature or group node within the tree, either to change its order
 * among its siblings or to change its parent. The server applies the change.
 * An empty target makes the server reject the move, which puts the dragged node back.
 */
export interface MoveNodeAction extends Action {
    kind: typeof MoveNodeAction.KIND;
    elementId: string;
    targetId: string;
    position: MovePosition;
}

export namespace MoveNodeAction {
    export const KIND = 'moveNode';
    export function create(elementId: string, targetId: string, position: MovePosition): MoveNodeAction {
        return { kind: KIND, elementId, targetId, position };
    }
}