// The module 'vscode' contains the VS Code extensibility API
// Import the module and reference it with the alias vscode in your code below
import * as vscode from 'vscode';
import {spawn} from 'child_process'; 
import * as path from 'path';
// This method is called when your extension is activated
// Your extension is activated the very first time the command is executed
export function activate(context: vscode.ExtensionContext) {

	// Use the console to output diagnostic information (console.log) and errors (console.error)
	// This line of code will only be executed once when your extension is activated
	console.log('HELLO WORLD ');

	// The command has been defined in the package.json file
	// Now provide the implementation of the command with registerCommand
	// The commandId parameter must match the command field in package.json
	const disposable = vscode.commands.registerCommand('featjarextension.helloWorld', () => {
		vscode.window.showInformationMessage('Hello World from FeatJarExtension! HJrust');
	});
	const disposable2 = vscode.commands.registerCommand('featjarextension.by', () => {
		vscode.window.showInformationMessage('Bye  from FeatJarExtension! HJrust');
	});
	const disposable3 = vscode.commands.registerCommand('featjarextension.Gui' ,(uri: vscode.Uri) => {
	// the line 25 and 26 : AI-assisted: Resolve the FeatJAR directory dynamically instead of using a hard-coded local path.
	const allPath = path.join(context.extensionPath,'..','all');
	const process = spawn('java', ['-jar','build/libs/feat.jar','gui','--input',uri.fsPath],{cwd :allPath});
	process.stdout.on('data', (data) => {
		const output = data.toString();
		if (output.includes('URL:')) {
			const parts = output.split("URL:");
			const url = parts[1].trim();
			vscode.commands.executeCommand('simpleBrowser.show',url); }
	});
	}); 

	context.subscriptions.push(disposable);
	context.subscriptions.push(disposable2);
	context.subscriptions.push(disposable3);
}

// This method is called when your extension is deactivated
export function deactivate() {}
