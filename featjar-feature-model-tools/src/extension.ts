import * as vscode from 'vscode';
import { ChildProcessWithoutNullStreams, spawn } from 'node:child_process';
import * as os from 'node:os';
import * as path from 'node:path';

let extensionShell: ChildProcessWithoutNullStreams | undefined;
let shellOutputBuffer = '';
let resolveShellReady: (() => void) | undefined;
const pendingCommands: Array<(output: string) => void> = [];

function featJarPath(): string {
	return path.join(os.homedir(), '.featjar-bin', 'feat.jar');
}

function startExtensionShell(jarPath: string): Promise<void> {
	extensionShell = spawn(
		'java',
		['-cp', jarPath, 'de.featjar.base.shell.ExtensionShell'],
		{ windowsHide: true, stdio: 'pipe' },
	);

	extensionShell.stdout.setEncoding('utf8');
	extensionShell.stdout.on('data', (data: string) => readShellOutput(data));

	return new Promise(resolve => {
		resolveShellReady = resolve;
	});
}

function readShellOutput(data: string): void {
	shellOutputBuffer += data;

	let lineBreakIndex: number;
	while ((lineBreakIndex = shellOutputBuffer.indexOf('\n')) >= 0) {
		const line = shellOutputBuffer.slice(0, lineBreakIndex).replace(/\r$/, '');
		shellOutputBuffer = shellOutputBuffer.slice(lineBreakIndex + 1);

		if (line === 'READY') {
			if (resolveShellReady !== undefined) {
				resolveShellReady();
			}
			continue;
		}

		if (line.startsWith('RESULT\t')) {
			const fields = line.split('\t', 2);
			const resolveCommand = pendingCommands.shift();
			resolveCommand?.(Buffer.from(fields[1], 'base64url').toString('utf8'));
		}
	}
}

function executeInExtensionShell(args: string[]): Promise<string> {
	return new Promise(resolve => {
		pendingCommands.push(resolve);
		extensionShell?.stdin.write(`RUN\t${args.join('\t')}\n`);
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

	const checkSatisfiability = vscode.commands.registerCommand(
		'featjar-extension.checkSatisfiability',
		async (uri: vscode.Uri) => {
			const output = await executeInExtensionShell([
				'solutions-sat4j',
				'--input',
				uri.fsPath,
				'--limit',
				'1',
				'--format',
				'SimpleCSV',
			]);

			const satisfiable = output
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

	context.subscriptions.push(checkSatisfiability, openFeatJarGui, uvlEditorProvider);
}

export function deactivate(): void {
	extensionShell?.stdin.write('SHUTDOWN\n');
}
