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

import { buildJvmArgs, findJavaExecutable } from './java';
import { updateStatusBar } from './status-bar';

let client: LanguageClient | undefined;
let crashCount = 0;
let hasEverReachedRunning = false;
const MAX_CRASH_RESTARTS = 3;

export function getClient(): LanguageClient | undefined {
    return client;
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

export async function startLanguageClient(context: vscode.ExtensionContext, serverJar: string, outputChannel: vscode.OutputChannel): Promise<void> {
    const javaExecutable = await findJavaExecutable(context);
    const logLevel = process.env.XTC_LOG_LEVEL?.toUpperCase() ?? 'INFO';
    const jvmArgs = buildJvmArgs(serverJar, logLevel);

    outputChannel.appendLine('Starting XTC Language Server...');
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
            inlayHintsEnabled: vscode.workspace.getConfiguration('xtc').get<boolean>('inlayHints.enabled', true),
            xtcSourceRoots: vscode.workspace.getConfiguration('xtc').get<string[]>('sourceRoots', [])
        },
        middleware: {
            workspace: {
                configuration: (params: ConfigurationParams) => {
                    return params.items.map(item => {
                        if (item.section === 'xtc.formatting') {
                            const config = vscode.workspace.getConfiguration('xtc.formatting');
                            return {
                                indentSize: config.get<number>('indentSize', 4),
                                continuationIndentSize: config.get<number>('continuationIndentSize', 8),
                                tabSize: config.get<number>('tabSize', 4),
                                insertSpaces: config.get<boolean>('insertSpaces', true),
                                maxLineWidth: config.get<number>('maxLineWidth', 120)
                            };
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
                    `XTC Language Server crashed ${crashCount} times and will not be restarted. Use "XTC: Restart Language Server" to restart manually.`
                );
                return { action: CloseAction.DoNotRestart };
            }
        }
    };

    client = new LanguageClient(
        'xtcLanguageServer',
        'XTC Language Server',
        serverOptions,
        clientOptions
    );

    // Patch stop() to suppress internal rejections from vscode-languageclient.
    // The library calls `void this.stop()` during initialization failures, creating
    // unhandled rejections. This patch ensures stop() always resolves.
    const originalStop = client.stop.bind(client);
    (client as unknown as { stop: (timeout?: number) => Promise<void> }).stop =
        (timeout?: number) => originalStop(timeout).catch(() => {});

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
        
        const status = stateMap[newState];
        if (status) {
            updateStatusBar(status);
        }
    });

    updateStatusBar('starting');

    void client.start().catch(err => {
        const message = err?.message ?? String(err);
        console.warn('XTC Language Server failed to start:', message);

        if (message.includes('UnsupportedClassVersionError') || message.includes('class file version')) {
            void vscode.window.showErrorMessage(
                'XTC Language Server requires Java 25+. Set the "xtc.java.home" setting to your Java 25 installation path.',
                'Open Settings'
            ).then(choice => {
                if (choice === 'Open Settings') {
                    void vscode.commands.executeCommand('workbench.action.openSettings', 'xtc.java.home');
                }
            });
        }

        updateStatusBar('error');
        client = undefined;
    });
}

export async function restartLanguageClient(context: vscode.ExtensionContext, serverJar: string, outputChannel: vscode.OutputChannel): Promise<void> {
    crashCount = 0;
    hasEverReachedRunning = false;
    await safeStop();
    await startLanguageClient(context, serverJar, outputChannel);
}

export async function stopLanguageClient(): Promise<void> {
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
