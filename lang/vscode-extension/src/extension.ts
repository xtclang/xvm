// Ecstasy (XTC) Language Support for VS Code
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
import { startLanguageClient, restartLanguageClient, stopLanguageClient, applyTraceConfig } from './lsp-client';
import { XtcTaskProvider } from './task-provider';
import { XtcDebugAdapterDescriptorFactory, XtcDebugConfigurationProvider } from './debug-adapter';
import { registerCommands } from './commands';

function ensureXtcLanguageAssociation(document: vscode.TextDocument): void {
    if (document.fileName.endsWith('.x') && document.languageId !== 'xtc') {
        vscode.languages.setTextDocumentLanguage(document, 'xtc').then(
            () => console.log(`Set language to XTC for ${document.fileName}`),
            err => console.error(`Failed to set language for ${document.fileName}:`, err)
        );
    }
}

/**
 * On first activation, check whether the user has an explicit `files.associations` entry
 * for `*.x` that points to a language other than `xtc`. If so, offer a one-time prompt to
 * update it permanently. This covers users who previously had the Logos (or another) extension
 * claim `.x` files and dismissed the VS Code conflict picker in a way that wrote a conflicting
 * entry into their global settings.json.
 *
 * The prompt is shown at most once (tracked via globalState). If the user declines, the
 * runtime `ensureXtcLanguageAssociation` listener continues to fix the language per-session.
 */
async function fixFilesAssociation(context: vscode.ExtensionContext): Promise<void> {
    const STATE_KEY = 'xtc.filesAssociationChecked';
    if (context.globalState.get<boolean>(STATE_KEY)) {
        return;
    }
    await context.globalState.update(STATE_KEY, true);

    const config = vscode.workspace.getConfiguration('files');
    const inspected = config.inspect<Record<string, string>>('associations');
    const globalAssociations = inspected?.globalValue ?? {};
    const conflictingLanguage = globalAssociations['*.x'];

    if (conflictingLanguage && conflictingLanguage !== 'xtc') {
        const updated = { ...globalAssociations, '*.x': 'xtc' };
        await config.update('associations', updated, vscode.ConfigurationTarget.Global);
    }
}

export function activate(context: vscode.ExtensionContext): void {
    console.log('XTC Language Support is now active');

    // Handle unhandled promise rejections from VS Code's git integration trying to stat .x.git files
    const rejectionHandler = (reason: unknown) => {
        const msg = reason instanceof Error ? reason.message : String(reason);
        // Silently ignore git-related ENOENT errors for .x.git files
        if (msg.includes('ENOENT') && msg.includes('.x.git')) {
            return;
        }
        // Log other unhandled rejections
        console.error('Unhandled promise rejection:', reason);
    };
    process.on('unhandledRejection', rejectionHandler);
    
    // Register rejection handler cleanup and language association.
    // We listen on three signals because each catches a different way a .x file
    // can end up mis-labeled: onDidOpenTextDocument fires when a new document
    // is loaded (covers Explorer click and Quick Open); the textDocuments
    // forEach fixes anything already open at activation time; and
    // onDidChangeActiveTextEditor catches the "tab restored from a previous
    // session but not in textDocuments yet" case where VS Code defers loading
    // the doc until the tab is focused — without this listener, restored tabs
    // can stick at the default languageId (plaintext / Logos / etc.) until
    // the user re-opens the file manually.
    void fixFilesAssociation(context);

    context.subscriptions.push(
        { dispose: () => process.off('unhandledRejection', rejectionHandler) },
        vscode.workspace.onDidOpenTextDocument(ensureXtcLanguageAssociation),
        vscode.window.onDidChangeActiveTextEditor(editor => {
            if (editor) {
                ensureXtcLanguageAssociation(editor.document);
            }
        })
    );
    vscode.workspace.textDocuments.forEach(ensureXtcLanguageAssociation);

    // Create output channel for LSP server
    const outputChannel = vscode.window.createOutputChannel('XTC Language Server', { log: true });

    // Register all providers and UI components
    const statusBar = createStatusBar();
    context.subscriptions.push(
        outputChannel,
        statusBar,
        vscode.tasks.registerTaskProvider(XtcTaskProvider.type, new XtcTaskProvider()),
        vscode.debug.registerDebugAdapterDescriptorFactory('xtc', new XtcDebugAdapterDescriptorFactory(context)),
        vscode.debug.registerDebugConfigurationProvider('xtc', new XtcDebugConfigurationProvider())
    );

    registerCommands(context, outputChannel);

    // Setup LSP server
    const serverJar = context.asAbsolutePath(path.join('server', 'lsp-server.jar'));
    const serverExists = fs.existsSync(serverJar);

    // Register LSP-related commands
    context.subscriptions.push(
        vscode.commands.registerCommand('xtc.restartServer', async () => {
            if (serverExists) {
                await restartLanguageClient(context, serverJar, outputChannel);
            } else {
                vscode.window.showWarningMessage('XTC Language Server JAR not found. Build the extension first.');
            }
        }),

        vscode.workspace.onDidChangeConfiguration(event => {
            if (event.affectsConfiguration('xtc.trace.server')) {
                void applyTraceConfig();
            }
            const needsRestart = serverExists && (
                event.affectsConfiguration('xtc.java.home') ||
                event.affectsConfiguration('xtc.sourceRoots')
            );
            if (needsRestart) {
                const changed = event.affectsConfiguration('xtc.java.home') ? 'Java path' : 'Source roots';
                vscode.window.showInformationMessage(
                    `${changed} changed. Restart the language server?`,
                    'Restart', 'Later'
                ).then(choice => {
                    if (choice === 'Restart') {
                        void restartLanguageClient(context, serverJar, outputChannel);
                    }
                });
            }
        })
    );

    // Start language server or show build instructions
    if (serverExists) {
        updateStatusBar('starting');
        startLanguageClient(context, serverJar, outputChannel);
    } else {
        const buildCmd = './gradlew :lang:vscode-extension:assemble -PincludeBuildLang=true -PincludeBuildAttachLang=true';
        console.log('XTC Language Server JAR not found at:', serverJar);
        console.log(`Build with: ${buildCmd}`);
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
