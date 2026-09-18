// The module 'vscode' contains the VS Code extensibility API
// Import the module and reference it with the alias vscode in your code below
import * as vscode from 'vscode';
import { spawn } from 'child_process';
import * as path from 'path';
import * as os from 'os';
// This method is called when your extension is activated
// Your extension is activated the very first time the command is executed
function openGui(uri: vscode.Uri) {
	const featjarPath = path.join(os.homedir(),'.featjar-bin','feat.jar');
	const process = spawn('java',['-jar', featjarPath, 'gui', '--input', uri.fsPath]);
	process.stdout.on('data', (data) => {
	const output = data.toString();

	if (output.includes('URL:')) {
		const parts = output.split('URL:');
		const url = parts[1].trim();

		vscode.commands.executeCommand(
			'simpleBrowser.show',
			url
		);
	}
	});
}
export function activate(context: vscode.ExtensionContext) {

	// Use the console to output diagnostic information (console.log) and errors (console.error)
	// This line of code will only be executed once when your extension is activated
	console.log('Congratulations, your extension "FeatJar Extension" is now active!');

	// The command has been defined in the package.json file
	// Now provide the implementation of the command with registerCommand
	// The commandId parameter must match the command field in package.json
	const disposable = vscode.commands.registerCommand('featjar-extension.helloWorld', () => {
		// The code you place here will be executed every time your command is executed
		// Display a message box to the user
		vscode.window.showInformationMessage('Hello World from World!');
	});
	const disposable2 = vscode.commands.registerCommand('featjar-extension.hellovscode', () => {
		// The code you place here will be executed every time your command is executed
		// Display a message box to the user
		vscode.window.showWarningMessage('This is a warning message from VSCode!');
	});
	const disposable3 = vscode.commands.registerCommand('featjar-extension.openGui',(uri: vscode.Uri) => {openGui(uri);});
	// AI-assisted: Register a custom editor for UVL files
	// and open the FeatJAR GUI when a UVL file is opened.
	const uvlEditorProvider = vscode.window.registerCustomEditorProvider('featjar-extension.uvlEditor',
	{
		resolveCustomTextEditor(
			document: vscode.TextDocument,
			webviewPanel: vscode.WebviewPanel
		) {
			openGui(document.uri);
		}
	}
	);

	context.subscriptions.push(disposable);
	context.subscriptions.push(disposable2);
	context.subscriptions.push(disposable3);	
	context.subscriptions.push(uvlEditorProvider);
}

// This method is called when your extension is deactivated
export function deactivate() {}
