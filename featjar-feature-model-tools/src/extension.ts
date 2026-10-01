import * as vscode from 'vscode';
import {
	checkSatisfiable,
	featJarDownload,
	featJarPath,
	openGui,
	shutdownExtensionShell,
	startExtensionShell,
	exportUVL,
	exportXML,
	exportDIMACS,
	exportTeX,
	registerGuiTrustConfirmation,

} from './extension-functions';
import { registerSidebar } from './sidebar';

export async function activate(context: vscode.ExtensionContext): Promise<void> {
	await featJarDownload();
	await startExtensionShell(featJarPath());
	registerSidebar(context);
	registerGuiTrustConfirmation(context);
	const output = vscode.window.createOutputChannel('FeatJAR');
	context.subscriptions.push(output);
	
	const checkSatisfiability = vscode.commands.registerCommand(
		'featjar-extension.checkSatisfiability',
		async (uri: vscode.Uri) => {
			const satisfiable = await checkSatisfiable(uri);
			if (satisfiable === undefined) {
				return;
			}

			output.clear();
			output.appendLine(satisfiable ? 'The model is satisfiable.' : 'The model is not satisfiable.');
			output.show();
		}
	);

	const openFeatJarGui = vscode.commands.registerCommand(
		'featjar-extension.openGui',
		(uri: vscode.Uri) => openGui(uri),
	);

	const uvlEditorProvider = vscode.window.registerCustomEditorProvider(
		'featjar-extension.uvlEditor',
		{
			resolveCustomTextEditor(document: vscode.TextDocument) {
				openGui(document.uri);
			},
		},
	);
	const exportUVLCommand = vscode.commands.registerCommand('featjar.exportUVL', (uri: vscode.Uri) => exportUVL(uri));
	const exportXMLCommand = vscode.commands.registerCommand('featjar.exportXML', (uri: vscode.Uri) => exportXML(uri));
	const exportDIMACSCommand = vscode.commands.registerCommand('featjar.exportDIMACS', (uri: vscode.Uri) => exportDIMACS(uri));
	const exportTeXCommand = vscode.commands.registerCommand('featjar.exportTeX', (uri: vscode.Uri) => exportTeX(uri));

	context.subscriptions.push(
		openFeatJarGui,
		checkSatisfiability,
		uvlEditorProvider,
		exportUVLCommand,
		exportXMLCommand,
		exportDIMACSCommand,
		exportTeXCommand,
	);
}

export function deactivate(): void {
	shutdownExtensionShell();
}
