import {
    AbstractUIExtension,
    CenterAction,
    EditorContextService,
    IActionDispatcher,
    IDiagramStartup,
    SelectAction,
    TYPES
} from '@eclipse-glsp/client';
import { injectable, inject } from 'inversify';
import { findMatchingElements } from './feature-search-utils';

/**
 * This Class is AI generated
 * Persistent search bar at the top of the diagram.
 *
 * Typing a feature name selects and centers the first match. Pressing
 * Enter again cycles to the next match, Shift+Enter to the previous one.
 */
@injectable()
export class FeatureSearchBar extends AbstractUIExtension implements IDiagramStartup {
    static readonly ID = 'feature-search-bar';

    @inject(TYPES.IActionDispatcher)
    protected readonly actionDispatcher: IActionDispatcher;

    @inject(EditorContextService)
    protected readonly editorContext: EditorContextService;

    protected matches: string[] = [];
    protected currentIndex = -1;
    protected counterLabel: HTMLElement;

    id(): string {
        return FeatureSearchBar.ID;
    }

    containerClass(): string {
        return FeatureSearchBar.ID;
    }

    protected initializeContents(containerElement: HTMLElement): void {
        const input = document.createElement('input');
        input.type = 'text';
        input.id = 'txt-feature-search';
        input.placeholder = 'Search features...';

        input.oninput = () => this.search(input.value);
        input.onkeydown = event => {
            if (event.key !== 'Enter') {
                return;
            }
            event.preventDefault();
            this.selectMatch(this.currentIndex + (event.shiftKey ? -1 : 1));
        };

        this.counterLabel = document.createElement('span');
        this.counterLabel.className = 'feature-search-bar-counter';

        containerElement.appendChild(input);
        containerElement.appendChild(this.counterLabel);
    }

    /*
     * Shows the search bar once the initial model has been loaded.
     */
    postModelInitialization(): void {
        this.show(this.editorContext.modelRoot);
    }

    protected search(text: string): void {
        this.matches = findMatchingElements(this.editorContext.modelRoot, text).map(match => match.id);
        this.selectMatch(text ? 0 : -1);
    }

    protected selectMatch(index: number): void {
        if (this.matches.length === 0) {
            this.currentIndex = -1;
            this.updateCounter();
            return;
        }

        this.currentIndex = ((index % this.matches.length) + this.matches.length) % this.matches.length;
        const id = this.matches[this.currentIndex];

        this.actionDispatcher.dispatchAll([SelectAction.create({ selectedElementsIDs: [id] }), CenterAction.create([id])]);

        this.updateCounter();
    }

    protected updateCounter(): void {
        this.counterLabel.textContent = this.matches.length > 0 ? `${this.currentIndex + 1} / ${this.matches.length}` : '';
    }
}