import { GModelElement, GModelRoot } from '@eclipse-glsp/client';

export interface FeatureMatch {
    id: string;
    label: string;
}

/**
 * Finds every named, non-constraint element whose label contains the given
 * search text (case-insensitive). An empty search text matches everything.
 */
export function findMatchingElements(root: Readonly<GModelRoot>, text: string): FeatureMatch[] {
    const matches: FeatureMatch[] = [];

    for (const element of root.index.all()) {
        const label = getElementLabel(element);
        const css: string[] = (element as any).cssClasses ?? [];
        if (css.some(c => c.includes('constraint'))) {
            continue;
        }
        if (!label) {
            continue;
        }
        if (text && !label.toLowerCase().includes(text.toLowerCase())) {
            continue;
        }

        matches.push({ id: element.id, label });
    }

    return matches;
}

/**
 * Reads the label of a node. Feature names are attached as a child label and the type starts with "label"
 */
export function getElementLabel(element: GModelElement): string | undefined {
    const children = (element as any).children ?? [];
    const labelChild = children.find((c: any) => typeof c.type === 'string' && c.type.startsWith('label') && c.text);
    return labelChild?.text;
}