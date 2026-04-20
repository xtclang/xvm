// XTC Language Support for VS Code
//
// Semantic tokens are enabled by default in the LSP server. VS Code automatically
// layers them on top of the TextMate grammar — types, methods, properties, annotations,
// and modifiers (static, deprecated, readonly) get richer colors than TextMate alone.
//
// TextMate remains as the fast-paint fallback during server startup.

import * as fs from 'node:fs';
import * as path from 'node:path';
import * as vscode from 'vscode';

import { createStatusBar, updateStatusBar } from './status-bar';
import { startLanguageClient, restartLanguageClient, stopLanguageClient } from './lsp-client';
import { XtcTaskProvider } from './task-provider';
import { XtcDebugAdapterDescriptorFactory, XtcDebugConfigurationProvider } from './debug-adapter';
import { registerCommands } from './commands';

export function activate(context: vscode.ExtensionContext): void {
    console.log('XTC Language Support is now active');

    // Create output channel for LSP server
    const outputChannel = vscode.window.createOutputChannel('XTC Language Server', { log: true });
    context.subscriptions.push(outputChannel);

    // Create status bar item
    const statusBar = createStatusBar();
    context.subscriptions.push(statusBar, 
        vscode.tasks.registerTaskProvider(XtcTaskProvider.type, new XtcTaskProvider())
    , 
        vscode.debug.registerDebugAdapterDescriptorFactory('xtc', new XtcDebugAdapterDescriptorFactory(context)),
        vscode.debug.registerDebugConfigurationProvider('xtc', new XtcDebugConfigurationProvider())
    );

    // Register commands
    registerCommands(context, outputChannel);

    // Find the LSP server JAR
    const serverJar = context.asAbsolutePath(path.join('server', 'lsp-server.jar'));

    // Register LSP-related commands
    context.subscriptions.push(
        vscode.commands.registerCommand('xtc.restartServer', async () => {
            if (fs.existsSync(serverJar)) {
                await restartLanguageClient(context, serverJar, outputChannel);
            } else {
                vscode.window.showWarningMessage('XTC Language Server JAR not found. Build the extension first.');
            }
        }),

        // Watch for Java path changes
        vscode.workspace.onDidChangeConfiguration(event => {
            if (event.affectsConfiguration('xtc.java.home') && fs.existsSync(serverJar)) {
                vscode.window.showInformationMessage(
                    'Java path changed. Restart the language server?',
                    'Restart', 'Later'
                ).then(choice => {
                    if (choice === 'Restart') {
                        restartLanguageClient(context, serverJar, outputChannel);
                    }
                });
            }
        })
    );

    // Start the language client if the server JAR exists
    if (fs.existsSync(serverJar)) {
        startLanguageClient(context, serverJar, outputChannel);
    } else {
        const buildCmd = './gradlew :lang:vscode-extension:assemble -PincludeBuildLang=true -PincludeBuildAttachLang=true';
        console.log('XTC Language Server JAR not found at:', serverJar);
        console.log(`Running without LSP features. Build with: ${buildCmd}`);
        void vscode.window.showErrorMessage(
            'XTC Language Server JAR not found. Build lang:vscode-extension to enable LSP features.',
            'Show Build Command'
        ).then(choice => {
            if (choice === 'Show Build Command') {
                outputChannel.appendLine(`Build command: ${buildCmd}`);
                outputChannel.show();
            }
        });
        updateStatusBar('stopped');
    }
}

export function deactivate(): Thenable<void> | undefined {
    return stopLanguageClient();
}
