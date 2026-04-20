import * as vscode from 'vscode';
import {
    CloseAction,
    ConfigurationParams,
    ErrorAction,
    LanguageClient,
    LanguageClientOptions,
    ServerOptions,
    State,
    TransportKind
} from 'vscode-languageclient/node';

import { buildJvmArgs, findJavaExecutable } from './java';
import { updateStatusBar } from './status-bar';

let client: LanguageClient | undefined;
let crashCount = 0;
const MAX_CRASH_RESTARTS = 3;

export function getClient(): LanguageClient | undefined {
    return client;
}

export function startLanguageClient(context: vscode.ExtensionContext, serverJar: string, outputChannel: vscode.OutputChannel): void {
    const javaExecutable = findJavaExecutable();
    const logLevel = process.env.XTC_LOG_LEVEL?.toUpperCase() ?? 'INFO';
    const jvmArgs = buildJvmArgs(serverJar, logLevel);

    const serverOptions: ServerOptions = {
        run: {
            command: javaExecutable,
            args: jvmArgs,
            transport: TransportKind.stdio
        },
        debug: {
            command: javaExecutable,
            args: ['-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005', ...jvmArgs],
            transport: TransportKind.stdio
        }
    };

    const clientOptions: LanguageClientOptions = {
        documentSelector: [{ scheme: 'file', language: 'xtc' }],
        outputChannel,
        traceOutputChannel: outputChannel,
        synchronize: {
            fileEvents: vscode.workspace.createFileSystemWatcher('**/*.x')
        },
        initializationOptions: {
            inlayHintsEnabled: vscode.workspace.getConfiguration('xtc').get<boolean>('inlayHints.enabled', true)
        },
        middleware: {
            workspace: {
                configuration: (params: ConfigurationParams, token, next) => {
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
                        return next(params, token);
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
                crashCount++;
                if (crashCount <= MAX_CRASH_RESTARTS) {
                    updateStatusBar('starting');
                    return { action: CloseAction.Restart };
                }
                updateStatusBar('stopped');
                vscode.window.showErrorMessage(
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

    client.onDidChangeState(event => {
        switch (event.newState) {
            case State.Starting:
                updateStatusBar('starting');
                break;
            case State.Running:
                crashCount = 0;
                updateStatusBar('ready');
                break;
            case State.Stopped:
                updateStatusBar('stopped');
                break;
        }
    });

    updateStatusBar('starting');

    client.start().catch(err => {
        const message = err?.message ?? String(err);
        console.warn('XTC Language Server failed to start:', message);

        if (message.includes('UnsupportedClassVersionError') || message.includes('class file version')) {
            vscode.window.showErrorMessage(
                'XTC Language Server requires Java 25+. Set the "xtc.java.home" setting to your Java 25 installation path.',
                'Open Settings'
            ).then(choice => {
                if (choice === 'Open Settings') {
                    vscode.commands.executeCommand('workbench.action.openSettings', 'xtc.java.home');
                }
            });
        }

        updateStatusBar('error');
        client = undefined;
    });
}

export async function restartLanguageClient(context: vscode.ExtensionContext, serverJar: string, outputChannel: vscode.OutputChannel): Promise<void> {
    crashCount = 0;
    if (client) {
        updateStatusBar('starting');
        try {
            await client.stop();
        } catch {
            // already stopped
        }
        client = undefined;
    }
    startLanguageClient(context, serverJar, outputChannel);
}

export async function stopLanguageClient(): Promise<void> {
    if (client) {
        try {
            await client.stop();
        } catch {
            // already stopped
        }
        client = undefined;
    }
}
