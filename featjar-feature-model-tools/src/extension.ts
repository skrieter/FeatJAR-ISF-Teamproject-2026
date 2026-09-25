import * as vscode from 'vscode';
import {
	analyzeCoreDead,
	checkSatisfiable,
	countConfigurations,
	featJarDownload,
	featJarPath,
	openGui,
	printModelStats,
	shutdownExtensionShell,
	startExtensionShell,
} from './extension-functions';
import { registerSidebar } from './sidebar';

export async function activate(context: vscode.ExtensionContext): Promise<void> {
	await featJarDownload();
	await startExtensionShell(featJarPath());
	registerSidebar(context);
	const output = vscode.window.createOutputChannel('FeatJAR');
	context.subscriptions.push(output);
	
	const checkSatisfiability = vscode.commands.registerCommand(
		'featjar-extension.checkSatisfiability',
		(uri: vscode.Uri) => checkSatisfiable(uri, output)
	);

	const openFeatJarGui = vscode.commands.registerCommand(
		'featjar-extension.openGui',
		(uri: vscode.Uri) => openGui(uri),
	);

	const uvlEditorProvider = vscode.window.registerCustomEditorProvider(
		'featjar-extension.uvlEditor',
		{
			resolveCustomTextEditor(
				document: vscode.TextDocument,
				webviewPanel: vscode.WebviewPanel
			) {
				openGui(document.uri);
			}
		});
	const modelTest = vscode.commands.registerCommand(
        'featjar-extension.modelTest',
		(uri: vscode.Uri) => printModelStats(uri, output),
    );
	const countConfigurationsCommand = vscode.commands.registerCommand(
    	'featjar-extension.countConfigurations',
		(uri: vscode.Uri) => countConfigurations(uri, output),
    );
	const coreDeadFeatures = vscode.commands.registerCommand(
        'featjar-extension.coreDeadFeatures',
        (uri: vscode.Uri | undefined) => analyzeCoreDead(uri, output),
    );
	

context.subscriptions.push(checkSatisfiability, openFeatJarGui, uvlEditorProvider, modelTest, countConfigurationsCommand, coreDeadFeatures);
}

export function deactivate(): void {
	shutdownExtensionShell();
}
