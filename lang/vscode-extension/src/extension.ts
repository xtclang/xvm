import { runtimeJvmOptions, runtimeLogArguments } from './runtime-settings';
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
import { startLanguageClient, restartLanguageClient, stopLanguageClient, applyTraceConfig, updateCompilerConfiguration, updateEditorConfiguration, getClient, connectionSettingsChanged } from './lsp-client';
import { XtcTaskProvider } from './task-provider';
import { XtcDebugAdapterDescriptorFactory, XtcDebugConfigurationProvider } from './debug-adapter';
import { registerCommands } from './commands';
import { registerCompilerPaths } from './compiler-paths';
import { readServiceSettings } from './editor-settings';
import { compilerSettingsLocation } from './rename-proposal';

function ensureXtcLanguageAssociation(document: vscode.TextDocument): void {
    if (document.fileName.endsWith('.x') && document.languageId !== 'xtc') {
        vscode.languages.setTextDocumentLanguage(document, 'xtc').then(
            () => console.log(`Set language to Ecstasy for ${document.fileName}`),
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
    console.log('Ecstasy Language Support is now active');

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
    const outputChannel = vscode.window.createOutputChannel('Ecstasy Language Server', { log: true });

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
    registerCompilerPaths(context);

    // Setup LSP server
    const serverJar = context.asAbsolutePath(path.join('server', 'lsp-server.jar'));
    const serverExists = fs.existsSync(serverJar);

    // Register LSP-related commands
    const effectiveOutput = vscode.window.createOutputChannel('Ecstasy Effective Configuration');
    // Owned by this UI command on the extension event loop, never by compiler callbacks.
    let effectiveRevision = 0;
    context.subscriptions.push(effectiveOutput,
        { dispose: () => { effectiveRevision++; } },
        vscode.workspace.onDidChangeConfiguration(() => { effectiveRevision++; }),
        vscode.commands.registerCommand('xtc.showLanguageServiceStatus', async () => {
            const revision = ++effectiveRevision;
            const resource = vscode.window.activeTextEditor?.document.uri;
            const settings = vscode.workspace.getConfiguration('xtc', resource);
            const keys = Object.keys(context.extension.packageJSON.contributes.configuration.properties).map(key => key.slice('xtc.'.length));
            const configured = Object.fromEntries(keys.map(key => {
                const value = settings.inspect(key);
                return [key, { value: settings.get(key), origin: value?.workspaceFolderValue !== undefined ? 'folder' : value?.workspaceValue !== undefined ? 'workspace' : value?.globalValue !== undefined ? 'user' : 'default' }];
            }));
            const running = getClient();
            const effective = running?.isRunning()
                ? await running.sendRequest('xtc/languageServiceStatus').catch(error => ({ status: 'unavailable', reason: String(error) }))
                : { status: 'not running; open an Ecstasy file or check the server log' };
            if (revision !== effectiveRevision || running !== getClient()) return undefined;
            const report = { configured, effective, activeDocument: resource?.toString(), nativeFormatOnSave: vscode.workspace.getConfiguration('editor', { uri: resource, languageId: 'xtc' }).get('formatOnSave', false), sourceRoots: settings.get('sourceRoots', []), compilerPaths: 'Ecstasy: Show Effective Compiler Paths', log: 'Ecstasy: Show Language Server Output' };
            effectiveOutput.clear();
            effectiveOutput.appendLine(JSON.stringify(report, null, 2));
            effectiveOutput.show(true);
            return report;
        }),
        vscode.commands.registerCommand('xtc.exportServerLogs', async () => {
            const connection = getClient();
            if (!connection?.isRunning()) { void vscode.window.showInformationMessage('Open an Ecstasy file to connect to its server, then export logs. Previous logs remain under ~/.xtc/logs/lsp.'); return; }
            const destination = await vscode.window.showSaveDialog({ title: 'Export Ecstasy Server Logs (includes local paths and logged diagnostics)', filters: { 'ZIP archives': ['zip'] }, defaultUri: vscode.Uri.file('ecstasy-server-logs.zip') });
            if (!destination) return;
            const bundle = await connection.sendRequest<{ base64: string }>('xtc/exportLogs');
            await vscode.workspace.fs.writeFile(destination, Buffer.from(bundle.base64, 'base64'));
            outputChannel.info(`Exported Ecstasy server logs to ${destination.fsPath}`);
        }),
        vscode.commands.registerCommand('xtc.restartServer', async () => {
            if (serverExists) {
                await restartLanguageClient(context, serverJar, outputChannel);
            } else {
                vscode.window.showWarningMessage('Ecstasy Language Server JAR not found. Build the extension first.');
            }
        }),

        vscode.workspace.onDidChangeTextDocument(event => {
            if (event.document.uri.toString() === compilerSettingsLocation()?.uri.toString()) {
                void updateCompilerConfiguration().catch(error => outputChannel.error(`Compiler settings edit rejected: ${error}`));
            }
        }),
        vscode.workspace.onDidChangeConfiguration(event => {
            if (event.affectsConfiguration('xtc.java.vmOptions') || event.affectsConfiguration('xtc.server.logs')) {
                try {
                    runtimeJvmOptions();
                    runtimeLogArguments();
                    void vscode.window.showInformationMessage('Ecstasy runtime/log settings saved. Restart the language server to apply them.', 'Restart now', 'Open Settings')
                        .then(choice => choice === 'Restart now' ? vscode.commands.executeCommand('xtc.restartServer')
                            : choice === 'Open Settings' ? vscode.commands.executeCommand('workbench.action.openSettings', 'xtc.java.vmOptions') : undefined);
                } catch (error) { void vscode.window.showErrorMessage(`Invalid Ecstasy runtime/log settings; running server retained: ${error}`); }
            }
            if (event.affectsConfiguration('xtc.trace.server')) {
                void applyTraceConfig();
            }
            if (event.affectsConfiguration('xtc.compiler.sourceModules') || event.affectsConfiguration('xtc.compiler.libraries')) {
                void updateCompilerConfiguration().catch(error => outputChannel.error(`Compiler configuration update failed: ${error}`));
            }
            if (event.affectsConfiguration('xtc.formatting') || event.affectsConfiguration('xtc.inlayHints.enabled')) {
                void updateEditorConfiguration().catch(error => outputChannel.error(`Editor configuration update failed: ${error}`));
            }
            const needsRestart = serverExists && (
                event.affectsConfiguration('xtc.java.home') || event.affectsConfiguration('xtc.sourceRoots') ||
                event.affectsConfiguration('xtc.languageService')
            );
            if (needsRestart) {
                try {
                    readServiceSettings();
                    if (!connectionSettingsChanged()) return;
                    outputChannel.info('Connection settings changed; restarting Ecstasy and resynchronizing open buffers.');
                    void restartLanguageClient(context, serverJar, outputChannel).catch(error => outputChannel.error(`Ecstasy restart failed: ${error}`));
                } catch (error) {
                    outputChannel.error(`Invalid Ecstasy settings; previous connection retained: ${error}`);
                    void vscode.window.showErrorMessage(`Invalid Ecstasy settings; previous connection retained: ${error}`);
                }
            }
        })
    );

    // Start language server or show build instructions
    if (serverExists) {
        updateStatusBar('starting');
        void startLanguageClient(context, serverJar, outputChannel).catch(error => outputChannel.error(`Ecstasy startup failed: ${error}`));
    } else {
        const buildCmd = './gradlew :lang:vscode-extension:assemble -PincludeBuildLang=true -PincludeBuildAttachLang=true';
        console.log('Ecstasy Language Server JAR not found at:', serverJar);
        console.log(`Build with: ${buildCmd}`);
        void vscode.window.showErrorMessage(
            'Ecstasy Language Server JAR not found. Build lang:vscode-extension to enable LSP features.',
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
