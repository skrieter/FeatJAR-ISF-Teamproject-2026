import * as path from 'node:path';
import * as vscode from 'vscode';
import { analyzeCoreDead, checkSatisfiable, countConfigurations, printModelStats } from './extension-functions';

type ModelTreeNode = {
	kind: 'model' | 'category' | 'analysis';
	label?: string;
	uri?: vscode.Uri;
	statistics?: { label: string; key: string }[];
	statisticKey?: string;
};

const syntacticalStatistics: { label: string; key: string }[] = [
	{ label: 'Number of features', key: 'NumberOfFeatures' },
	{ label: 'Number of Constraints', key: 'NumberOfConstraints' },
	{ label: 'Maximum depth', key: 'MaxDepth' },
	{ label: 'Average number of children', key: 'AverageNumberOfChildren' },
	{ label: 'Number of leaf nodes', key: 'NumberOfLeafNodes' },
	{ label: 'Number of top nodes', key: 'NumberOfTopNodes' },
	{ label: 'Number of groups', key: 'NumberOfGroups' },
	{ label: 'Number of atoms', key: 'AtomCount' },
	{ label: 'Number of connectives', key: 'ConnectiveCount' },
	{ label: 'Number of distinct variables', key: 'DistinctVariableCount' },
];

const semanticAnalyses: { label: string; key: string }[] = [
	{ label: 'SAT', key: 'SAT' },
	{ label: 'Number of configurations', key: 'NumberOfConfigurations' },
	{ label: 'Core Features', key: 'CoreFeatures' },
	{ label: 'Dead Features', key: 'DeadFeatures' },
];

/** 
 * Registers the sidebar view for UVL files in the VS Code extension.
 * @param context The extension context.
*/
class UvlFileProvider implements vscode.TreeDataProvider<ModelTreeNode> {
	private readonly onDidChangeTreeDataEmitter = new vscode.EventEmitter<ModelTreeNode | undefined>();
	private readonly statistics = new Map<string, {
		values: Record<string, string>;
		isComputing?: boolean;
	}>();
	private readonly loadedStatistics = new Set<string>();
	private readonly runningAnalyses = new Set<string>();
	readonly onDidChangeTreeData = this.onDidChangeTreeDataEmitter.event;
/**
 * Gets the tree item representation for a given model tree node.
 * @param element The model tree node.
 * @returns A TreeItem representing the node.
 */
	getTreeItem(element: ModelTreeNode): vscode.TreeItem {
		if (element.kind === 'model') {
			const item = new vscode.TreeItem(
				path.basename(element.uri?.fsPath ?? 'Model'),
				vscode.TreeItemCollapsibleState.Collapsed,
			);
			if (element.uri) {
				item.resourceUri = element.uri;
			}
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

		const statistic = [...syntacticalStatistics, ...semanticAnalyses]
			.find(candidate => candidate.key === element.statisticKey);
		const modelStats = element.uri ? this.statistics.get(element.uri.toString()) : undefined;
		const value = element.statisticKey ? modelStats?.values[element.statisticKey] : undefined;
		const label = statistic?.label ?? element.label ?? 'Statistic';
		const item = new vscode.TreeItem(`${label}: ${value ?? '?'}`, vscode.TreeItemCollapsibleState.None);
		item.contextValue = 'modelAnalysis';
		const runningKey = element.uri && element.statisticKey
			? `${element.uri.toString()}::${element.statisticKey}`
			: undefined;
		if (modelStats?.isComputing && statistic && syntacticalStatistics.includes(statistic)) {
			item.description = 'Computing';
		} else if (runningKey && this.runningAnalyses.has(runningKey)) {
			item.description = 'Computing';
		}
		if (element.uri && semanticAnalyses.some(analysis => analysis.key === element.statisticKey)) {
			item.command = {
				command: 'featjar-extension.runSemanticAnalysis',
				title: `Run ${label}`,
				arguments: [element.uri, element.statisticKey],
			};
		}
		return item;
	}

	async getChildren(element?: ModelTreeNode): Promise<ModelTreeNode[]> {
		if (!element) {
			const files = await vscode.workspace.findFiles('**/*.uvl', '**/{node_modules,.git}/**');
			files.sort((a, b) => a.fsPath.localeCompare(b.fsPath));
			return files.map(uri => ({ kind: 'model', uri }));
		}

		if (element.kind === 'model' && element.uri) {
			return [
				{ kind: 'category', label: 'Syntactical Analyses', uri: element.uri, statistics: syntacticalStatistics },
				{ kind: 'category', label: 'Semantic Analyses', uri: element.uri, statistics: semanticAnalyses },
			];
		}

		if (element.kind === 'category' && element.statistics && element.uri) {
			if (element.label === 'Syntactical Analyses') {
				this.loadStatistics(element.uri, element);
			}
			return element.statistics.map(statistic => ({
				kind: 'analysis',
				label: statistic.label,
				statisticKey: statistic.key,
				uri: element.uri,
			}));
		}

		return [];
	}

	private loadStatistics(uri: vscode.Uri, category: ModelTreeNode): void {
		const key = uri.toString();
		if (this.loadedStatistics.has(key)) {
			return;
		}

		this.loadedStatistics.add(key);
		const modelStatistics = this.statistics.get(key) ?? { values: {} };
		modelStatistics.isComputing = true;
		this.statistics.set(key, modelStatistics);
		this.refresh(category);
		void printModelStats(uri).then(result => {
			modelStatistics.isComputing = false;
			Object.assign(modelStatistics.values, this.parseStatistics(result ?? ''));
			this.statistics.set(key, modelStatistics);
			this.refresh(category);
		});
	}

	// AI-assisted: parses FeatJAR's text output into statistic values keyed by name.
	private parseStatistics(output: string): Record<string, string> {
		const values: Record<string, string> = {};
		for (const statistic of syntacticalStatistics) {
			const escapedKey = statistic.key.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
			const match = output.match(new RegExp(`${escapedKey}:\\s*([^\\r\\n|]+)`));
			if (match) {
				values[statistic.key] = match[1].trim();
			}
		}
		return values;
	}

	registerCommands(context: vscode.ExtensionContext): void {
		const command = vscode.commands.registerCommand(
			'featjar-extension.runSemanticAnalysis',
			(uri: vscode.Uri, key: string) => this.runSemanticAnalysis(uri, key),
		);
		context.subscriptions.push(command);
	}

	private async runSemanticAnalysis(uri: vscode.Uri, key: string): Promise<void> {
		let keys: string[];
		if (key === 'SAT') {
			keys = ['SAT'];
		} else if (key === 'NumberOfConfigurations') {
			keys = ['NumberOfConfigurations'];
		} else {
			keys = ['SAT', 'CoreFeatures', 'DeadFeatures'];
		}
		const runningKeys = keys.map(analysisKey => `${uri.toString()}::${analysisKey}`);
		if (runningKeys.some(runningKey => this.runningAnalyses.has(runningKey))) {
			return;
		}
		runningKeys.forEach(runningKey => this.runningAnalyses.add(runningKey));
		this.refresh();

		try {
			if (key === 'SAT') {
				const satisfiable = await checkSatisfiable(uri);
				this.setValue(uri, 'SAT', satisfiable === undefined ? '?' : satisfiable ? 'True' : 'False');
			} else if (key === 'NumberOfConfigurations') {
				const result = await countConfigurations(uri);
				this.setValue(uri, key, result?.trim() ?? '?');
			} else {
				let satisfiable = this.statistics.get(uri.toString())?.values.SAT;
				if (satisfiable !== 'True' && satisfiable !== 'False') {
					const result = await checkSatisfiable(uri);
					satisfiable = result === undefined ? undefined : result ? 'True' : 'False';
					this.setValue(uri, 'SAT', satisfiable ?? '?');
				}

				if (satisfiable === 'False') {
					this.setValue(uri, 'CoreFeatures', 'N/A');
					this.setValue(uri, 'DeadFeatures', 'N/A');
				} else if (satisfiable === 'True') {
					const result = await analyzeCoreDead(uri);
					this.setValue(uri, 'CoreFeatures', result === undefined ? '?' : String(result.core));
					this.setValue(uri, 'DeadFeatures', result === undefined ? '?' : String(result.dead));
				}
			}
		} catch (error) {
			if (error instanceof Error && error.message.startsWith('FeatJAR returned no valid core/dead literal list.')) {
				this.setValue(uri, 'CoreFeatures', 'N/A');
				this.setValue(uri, 'DeadFeatures', 'N/A');
			} else {
				throw error;
			}
		} finally {
			runningKeys.forEach(runningKey => this.runningAnalyses.delete(runningKey));
			this.refresh();
		}
	}

	private setValue(uri: vscode.Uri, key: string, value: string): void {
		const modelKey = uri.toString();
		const modelStatistics = this.statistics.get(modelKey) ?? { values: {} };
		modelStatistics.values[key] = value;
		this.statistics.set(modelKey, modelStatistics);
	}

	refresh(element?: ModelTreeNode): void {
		this.onDidChangeTreeDataEmitter.fire(element);
	}

	dispose(): void {
		this.onDidChangeTreeDataEmitter.dispose();
	}
}

export function registerSidebar(context: vscode.ExtensionContext): void {
	const provider = new UvlFileProvider();
	provider.registerCommands(context);
	const refreshCommand = vscode.commands.registerCommand('featjar-extension.refreshUvlFiles', () => provider.refresh());
	const tree = vscode.window.registerTreeDataProvider('uvlFiles', provider);
	context.subscriptions.push(tree, refreshCommand, provider);
}
