import * as vscode from 'vscode';
import { ChildProcessWithoutNullStreams, spawn } from 'node:child_process';
import * as os from 'node:os';
import * as path from 'node:path';

type ShellResult = {
	output: string;
};

let extensionShell: ChildProcessWithoutNullStreams | undefined;
let shellOutputBuffer = '';
let resolveShellReady: (() => void) | undefined;
const pendingCommands: Array<(result: ShellResult) => void> = [];

function featJarPath(): string {
	return path.join(os.homedir(), '.featjar-bin', 'feat.jar');
}

function startExtensionShell(jarPath: string): Promise<void> {
	extensionShell = spawn(
		'java',
		['-jar', jarPath],
		{
			windowsHide: true,
			stdio: 'pipe',
		},
	);

	extensionShell.stdout.setEncoding('utf8');
	extensionShell.stdout.on('data', (data: string) => readShellOutput(data));

	return new Promise(resolve => {
		resolveShellReady = resolve;
	});
}

function readShellOutput(data: string): void {
	shellOutputBuffer += data;

	const promptIndex = shellOutputBuffer.indexOf('$ ');

	if (promptIndex === -1) {
		return;
	}

	const output = shellOutputBuffer.slice(0, promptIndex);

	shellOutputBuffer =
		shellOutputBuffer.slice(promptIndex + 2);

	// First "$ " = shell startup
	if (resolveShellReady) {
		resolveShellReady();
		resolveShellReady = undefined;
		return;
	}

	// Later "$ " = previous command finished
	const resolveCommand = pendingCommands.shift();

	resolveCommand?.({
		output: output.trim(),
	});
}

function executeInExtensionShell(args: string[]): Promise<ShellResult> {
	return new Promise(resolve => {
		pendingCommands.push(resolve);

		const command =
			`execute ${args.join(' ')}\n`;

		extensionShell?.stdin.write(command);
	});
}

function openGui(uri: vscode.Uri): void {
	const process = spawn('java', ['-jar', featJarPath(), 'gui', '--input', uri.fsPath]);
	process.stdout.on('data', data => {
		const output = data.toString();

		if (output.includes('URL:')) {
			const url = output.split('URL:')[1].trim();
			void vscode.commands.executeCommand('simpleBrowser.show', url);
		}
	});
}

export async function activate(context: vscode.ExtensionContext): Promise<void> {
	await startExtensionShell(featJarPath());
	
	vscode.window.showInformationMessage('Ready');
	const checkSatisfiability = vscode.commands.registerCommand(
		'featjar-extension.checkSatisfiability',
		async (uri: vscode.Uri) => {
			const result = await executeInExtensionShell([
	'solutions-sat4j',
	'--input',
	uri.fsPath,
	'--limit',
	'1',
	'--format',
	'SimpleCSV',
]);

			const satisfiable = result.output
				.split('\n')
				.some(line => line.startsWith('0;'));
			void vscode.window.showInformationMessage(
				satisfiable ? 'The model is satisfiable.' : 'The model is not satisfiable.',
				{ modal: true },
			);
		},
	);

	const openFeatJarGui = vscode.commands.registerCommand(
		'featjar-extension.openGui',
		(uri: vscode.Uri) => openGui(uri),
	);
	const testCommand = vscode.commands.registerCommand(
		'featjar-extension.TestCommand',() => {
			vscode.window.showInformationMessage('Test command executed successfully!');
		});

	const uvlEditorProvider = vscode.window.registerCustomEditorProvider(
		'featjar-extension.uvlEditor',
		{
			resolveCustomTextEditor(document: vscode.TextDocument) {
				openGui(document.uri);
			},
		},
	);
	context.subscriptions.push(testCommand);
	context.subscriptions.push(checkSatisfiability, openFeatJarGui, uvlEditorProvider);
}

export function deactivate(): void {
}
