// The module 'vscode' contains the VS Code extensibility API
// Import the module and reference it with the alias vscode in your code below
import * as vscode from 'vscode';
import { spawn } from 'node:child_process';
import { homedir } from 'node:os';
import { join } from 'node:path';

function runFeatJar(jarPath: string, args: string[]): Promise<string> {
    return new Promise((resolve, reject) => {
        const child = spawn('java', ['-jar', jarPath, ...args], {
            windowsHide: true,
            stdio: ['ignore', 'pipe', 'ignore']
        });
        let output = '';
        child.stdout.setEncoding('utf8');
        child.stdout.on('data', data => {
            output += data;
        });
        child.on('error', reject);
        child.on('close', () => resolve(output));
    });
}

// This method is called when your extension is activated
// Your extension is activated the very first time the command is executed
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
	const checkSatisfiability = vscode.commands.registerCommand('featjar-extension.checkSatisfiability', async (uri: vscode.Uri) => {
        const jarPath = join(homedir(), '.featjar-bin', 'feat.jar');

        const result = await runFeatJar(jarPath, ['solutions-sat4j', '--input', uri.fsPath, '--limit', '1', '--format', 'SimpleCSV']);
        // The first configuration in SimpleCSV starts with "0;".
        const satisfiable = result.split('\n').some(line => line.startsWith('0;'));
        vscode.window.showInformationMessage(satisfiable ? 'The model is satisfiable.' : 'The model is not satisfiable.', { modal: true });
    });		
	
	context.subscriptions.push(disposable);
	context.subscriptions.push(disposable2);
	context.subscriptions.push(checkSatisfiability);
}

// This method is called when your extension is deactivated
export function deactivate() {}
