import * as vscode from 'vscode';
import {
    CloseAction,
    ConfigurationParams,
    ErrorAction,
    Executable,
    LanguageClient,
    LanguageClientOptions,
    ServerOptions,
    State,
    Trace,
    TransportKind
} from 'vscode-languageclient/node';

import { formattingSettings, nativeFormatOnSave, readServiceSettings } from './editor-settings';
import { synchronizationOptions } from './service-settings';
import { compilerBuildModels } from './compiler-paths';
import { BuildModel } from './build-model';
import { buildJvmArgs, findJavaExecutable } from './java';
import { compilerSourceModules, renameWithConfiguration } from './rename-proposal';
import { updateStatusBar } from './status-bar';

let client: LanguageClient | undefined;
let crashCount = 0;
let hasEverReachedRunning = false;
const MAX_CRASH_RESTARTS = 3;

export function getClient(): LanguageClient | undefined {
    return client;
}

function compilerConfiguration(): { sourceModules: unknown[] | null; buildModels: BuildModel[] } {
    return { sourceModules: compilerSourceModules(), buildModels: compilerBuildModels() };
}

export async function updateEditorConfiguration(): Promise<void> {
    if (client?.state === State.Running) {
        await client.sendNotification('workspace/didChangeConfiguration', { settings: { xtc: { presentation: {} } } });
    }
}

export async function updateCompilerConfiguration(): Promise<void> {
    if (client?.state === State.Running) {
        await client.sendNotification('workspace/didChangeConfiguration', { settings: { xtc: { compiler: compilerConfiguration() } } });
    }
}

/** Stop the client safely, swallowing any state-related throws. */
async function safeStop(): Promise<void> {
    const c = client;
    client = undefined;
    if (!c) {
        return;
    }
    try {
        await c.stop();
    } catch {
        // Client may be in starting/startFailed state — ignore.
    }
}

let starting: Promise<void> | undefined;
export function startLanguageClient(context: vscode.ExtensionContext, serverJar: string, outputChannel: vscode.LogOutputChannel): Promise<void> {
    starting ??= startConnection(context, serverJar, outputChannel).finally(() => { starting = undefined; });
    return starting;
}

async function startConnection(context: vscode.ExtensionContext, serverJar: string, outputChannel: vscode.LogOutputChannel): Promise<void> {
    const preferences = readServiceSettings();
    let lastFormatting = formattingSettings();
    const javaExecutable = await findJavaExecutable(context);
    const logLevel = process.env.XTC_LOG_LEVEL?.toUpperCase() ?? 'INFO';
    const jvmArgs = buildJvmArgs(serverJar, logLevel);

    outputChannel.appendLine('Starting Ecstasy Language Server...');
    outputChannel.appendLine(`Java: ${javaExecutable}`);
    outputChannel.appendLine(`Args: ${jvmArgs.join(' ')}`);
    outputChannel.appendLine(`JAR: ${serverJar}`);

    const createExecutable = (debugPort?: number): Executable => ({
        command: javaExecutable,
        args: debugPort
            ? [`-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,quiet=y,address=${debugPort}`, ...jvmArgs]
            : jvmArgs,
        transport: TransportKind.stdio,
        options: { cwd: context.extensionPath }
    });

    const serverOptions: ServerOptions = {
        run: createExecutable(),
        debug: createExecutable(5005)
    };

    const clientOptions: LanguageClientOptions = {
        documentSelector: [{ scheme: 'file', language: 'xtc' }],
        outputChannel,
        traceOutputChannel: outputChannel,
        synchronize: {
            fileEvents: vscode.workspace.createFileSystemWatcher('**/*.x')
        },
        initializationOptions: {
            xtcDocumentSync: synchronizationOptions(preferences, false),
            xtcSourceRoots: vscode.workspace.getConfiguration('xtc').get<string[]>('sourceRoots', []),
            xtcCompiler: compilerConfiguration()
        },
        middleware: {
            provideInlayHints: (document, range, token, next) => {
                const value = vscode.workspace.getConfiguration('xtc', document.uri).get<unknown>('inlayHints.enabled', true);
                const enabled = typeof value === 'boolean' ? value : preferences.inlayHints;
                return enabled ? next(document, range, token) : [];
            },
            // Save ownership is checked per document at the instant of saving, including
            // language/folder overrides changed after initialization.
            willSaveWaitUntil: (event, next) => nativeFormatOnSave(event.document) ? Promise.resolve([]) : next(event),
            provideRenameEdits: (document, position, name, token, next) => {
                const current = client;
                return current?.initializeResult?.capabilities.experimental?.xtcRenameProposal === 1
                    ? renameWithConfiguration(current, document, position, name, token)
                    : next(document, position, name, token);
            },
            workspace: {
                configuration: (params: ConfigurationParams) => {
                    return params.items.map(item => {
                        if (item.section === 'xtc.compiler') {
                            return compilerConfiguration();
                        }
                        if (item.section === 'xtc.formatting') {
                            try { lastFormatting = formattingSettings(); }
                            catch (error) { outputChannel.warn(`Retaining previous Ecstasy formatting settings: ${error}`); }
                            return lastFormatting;
                        }
                        return {};
                    });
                }
            }
        },
        errorHandler: {
            error: (_error, _message, count) => {
                if (count && count <= 3) {
                    return { action: ErrorAction.Continue };
                }
                return { action: ErrorAction.Shutdown };
            },
            closed: () => {
                if (!hasEverReachedRunning) {
                    // Server died before reaching Running state (startup crash).
                    // Do not restart to avoid unhandled rejection issues in vscode-languageclient.
                    updateStatusBar('error');
                    return { action: CloseAction.DoNotRestart };
                }
                crashCount++;
                if (crashCount <= MAX_CRASH_RESTARTS) {
                    updateStatusBar('starting');
                    return { action: CloseAction.Restart };
                }
                updateStatusBar('stopped');
                void vscode.window.showErrorMessage(
                    `Ecstasy Language Server crashed ${crashCount} times and will not be restarted. Use "Ecstasy: Restart Language Server" to restart manually.`
                );
                return { action: CloseAction.DoNotRestart };
            }
        }
    };

    client = new LanguageClient(
        'xtcLanguageServer',
        'Ecstasy Language Server',
        serverOptions,
        clientOptions
    );

    // Patch stop() to suppress internal rejections from vscode-languageclient.
    // The library calls `void this.stop()` during initialization failures, creating
    // unhandled rejections. This patch ensures stop() always resolves.
    const originalStop = client.stop.bind(client);
    (client as unknown as { stop: (timeout?: number) => Promise<void> }).stop =
        (timeout?: number) => originalStop(timeout).catch(() => {});

    const startingClient = client;
    client.onDidChangeState(({ newState }) => {
        const stateMap = {
            [State.Starting]: 'starting' as const,
            [State.Running]: 'ready' as const,
            [State.Stopped]: 'stopped' as const
        };
        
        if (newState === State.Running) {
            crashCount = 0;
            hasEverReachedRunning = true;
        }
        
        const status = stateMap[newState as keyof typeof stateMap];
        if (status) {
            updateStatusBar(status);
        }
    });

    updateStatusBar('starting');

    await startingClient.start().catch(err => {
        const message = err?.message ?? String(err);
        console.warn('Ecstasy Language Server failed to start:', message);

        if (message.includes('UnsupportedClassVersionError') || message.includes('class file version')) {
            void vscode.window.showErrorMessage(
                'Ecstasy Language Server requires Java 25+. Set the "xtc.java.home" setting to your Java 25 installation path.',
                'Open Settings'
            ).then(choice => {
                if (choice === 'Open Settings') {
                    void vscode.commands.executeCommand('workbench.action.openSettings', 'xtc.java.home');
                }
            });
        }

        updateStatusBar('error');
        if (client === startingClient) client = undefined;
        throw err;
    });
    await applyTraceConfig();
}

// Restart requests share one operation; settings changes during startup are applied in order.
let restarting: Promise<void> | undefined;
let restartRevision = 0;
export function restartLanguageClient(context: vscode.ExtensionContext, serverJar: string, outputChannel: vscode.LogOutputChannel): Promise<void> {
    readServiceSettings();
    formattingSettings(); // Reject malformed settings before stopping a valid connection.
    restartRevision++;
    restarting ??= (async () => {
        let applied: number;
        do {
            applied = restartRevision;
            crashCount = 0;
            hasEverReachedRunning = false;
            await starting?.catch(() => {});
            await safeStop();
            await startLanguageClient(context, serverJar, outputChannel);
        } while (applied !== restartRevision);
    })().finally(() => { restarting = undefined; });
    return restarting;
}

export async function stopLanguageClient(): Promise<void> {
    await restarting?.catch(() => {});
    await starting?.catch(() => {});
    await safeStop();
}

function traceFromConfig(): Trace {
    const level = vscode.workspace.getConfiguration('xtc').get<string>('trace.server', 'off');
    if (level === 'verbose') { return Trace.Verbose; }
    if (level === 'messages') { return Trace.Messages; }
    return Trace.Off;
}

export async function applyTraceConfig(): Promise<void> {
    if (client) {
        await client.setTrace(traceFromConfig());
    }
}
