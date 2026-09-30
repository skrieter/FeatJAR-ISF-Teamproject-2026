import * as path from 'node:path';
import * as vscode from 'vscode';

type ModelTreeNode = {
	kind: 'model' | 'category' | 'analysis';
	label?: string;
	uri?: vscode.Uri;
	analyses?: AnalysisDefinition[];
	command?: string;
};

interface AnalysisDefinition {
	label: string;
	command: string;
}

const syntacticalAnalyses: AnalysisDefinition[] = [
	{ label: 'Model statistics', command: 'featjar-extension.modelTest' },
];

const semanticAnalyses: AnalysisDefinition[] = [
	{ label: 'Satisfiability', command: 'featjar-extension.checkSatisfiability' },
	{ label: 'Number of configurations', command: 'featjar-extension.countConfigurations' },
	{ label: 'Core and dead features', command: 'featjar-extension.coreDeadFeatures' },
];

class UvlFileProvider implements vscode.TreeDataProvider<ModelTreeNode> {
	private readonly onDidChangeTreeDataEmitter = new vscode.EventEmitter<ModelTreeNode | undefined>();
	readonly onDidChangeTreeData = this.onDidChangeTreeDataEmitter.event;

	getTreeItem(element: ModelTreeNode): vscode.TreeItem {
		if (element.kind === 'model') {
			if (!element.uri) {
				return new vscode.TreeItem('Unnamed model');
			}
			const item = new vscode.TreeItem(
				path.basename(element.uri.fsPath),
				vscode.TreeItemCollapsibleState.Collapsed,
			);
			item.resourceUri = element.uri;
			item.contextValue = 'uvlModel';
			item.iconPath = new vscode.ThemeIcon('file-code');
			return item;
		}

		if (element.kind === 'category') {
			const item = new vscode.TreeItem(
				element.label ?? 'Analyses',
				vscode.TreeItemCollapsibleState.Collapsed,
			);
			item.contextValue = 'analysisCategory';
			item.iconPath = new vscode.ThemeIcon('list-tree');
			return item;
		}

		const item = new vscode.TreeItem(element.label ?? 'Analysis', vscode.TreeItemCollapsibleState.None);
		item.contextValue = 'modelAnalysis';
		item.iconPath = new vscode.ThemeIcon('play');
		if (element.command && element.uri) {
			item.command = {
				command: element.command,
				title: element.label ?? 'Run analysis',
				arguments: [element.uri],
			};
		}
		return item;
	}

	async getChildren(element?: ModelTreeNode): Promise<ModelTreeNode[]> {
		if (!element) {
			const files = await vscode.workspace.findFiles('**/*.uvl', '**/{node_modules,.git}/**');
			return files.map(uri => ({ kind: 'model', uri }));
		}

		if (element.kind === 'model') {
			if (!element.uri) {
				return [];
			}
			return [
				{ kind: 'category', label: 'Syntactical Analyses', uri: element.uri, analyses: syntacticalAnalyses },
				{ kind: 'category', label: 'Semantic Analyses', uri: element.uri, analyses: semanticAnalyses },
			];
		}

		if (element.kind === 'category') {
			return (element.analyses ?? []).map(analysis => ({
				kind: 'analysis',
				label: analysis.label,
				command: analysis.command,
				uri: element.uri,
			}));
		}

		return [];
	}

	refresh(): void {
		this.onDidChangeTreeDataEmitter.fire(undefined);
	}

	dispose(): void {
		this.onDidChangeTreeDataEmitter.dispose();
	}
}

export function registerSidebar(context: vscode.ExtensionContext): void {
	const uvlFileProvider = new UvlFileProvider();
	const refreshCommand = vscode.commands.registerCommand('featjar-extension.refreshUvlFiles', () => {
		uvlFileProvider.refresh();
	});
	const tree = vscode.window.registerTreeDataProvider('uvlFiles', uvlFileProvider);
	context.subscriptions.push(tree, refreshCommand, uvlFileProvider);
}
