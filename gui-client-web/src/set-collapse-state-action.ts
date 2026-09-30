// Generated with Claude Opus 5.5 (Anthropic, via claude.ai)
import { Action } from '@eclipse-glsp/client';

// Collapses or expands several features at once.

export type CollapseMode = 'collapseAll' | 'collapseAllButRoot' | 'collapseFromLevel' | 'expandAll' | 'expandUpToLevel' | 'expandSubtree';

export interface SetCollapseStateAction extends Action {
    kind: typeof SetCollapseStateAction.KIND;
    mode: CollapseMode;
    level?: number;
    elementId?: string;
}

export namespace SetCollapseStateAction {
    export const KIND = 'setCollapseState';

    export function create(mode: CollapseMode, level?: number, elementId?: string): SetCollapseStateAction {
        return { kind: KIND, mode, level, elementId };
    }
}
