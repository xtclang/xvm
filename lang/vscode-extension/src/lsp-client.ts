import * as vscode from 'vscode';
import {
    CloseAction,
    ConfigurationParams,
    ErrorAction,
    Executable,
    LanguageClient,
    LanguageClientOptions,
    MessageTransports,
    ServerOptions,
    State,
    Trace,
    TransportKind
} from 'vscode-languageclient/node';

import { formattingSettings, nativeFormatOnSave, readServiceSettings } from './editor-settings';
import { synchronizationOptions } from './service-settings';
import { compilerBuildModels } from './compiler-paths';
import { BuildModel } from './build-model';
import { LibraryOptions } from './library-configuration';
import { libraryOptions } from './library-settings';
import { buildJvmArgs, findJavaExecutable } from './java';
import { compilerSourceModules, moveWithConfiguration, renameWithConfiguration } from './rename-proposal';
import { updateStatusBar } from './status-bar';
import { runtimeJvmOptions, runtimeLogArguments } from './runtime-settings';
import { supportLogs } from './support-logs';
import { serviceFailure } from './service-notifications';

let client: LanguageClient | undefined;
let activeConnectionKey: string | undefined;

function connectionKey(): string {
    const settings = readServiceSettings();
    const config = vscode.workspace.getConfiguration('xtc');
    return JSON.stringify([settings.adapter, settings.textSynchronization, settings.saveFormatting, config.get('java.home', ''), config.get('sourceRoots', []), runtimeJvmOptions(), runtimeLogArguments()]);
}

export function connectionSettingsChanged(): boolean { return connectionKey() !== activeConnectionKey; }

const MAX_CRASH_RESTARTS = 3;

export function getClient(): LanguageClient | undefined {
    return client;
}

function compilerConfiguration(): { sourceModules: unknown[] | null; buildModels: BuildModel[]; libraries: LibraryOptions } {
    return { sourceModules: compilerSourceModules(), buildModels: compilerBuildModels(), libraries: libraryOptions() };
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
    // These counters belong to this connection, including its automatic crash restarts.
    let crashCount = 0;
    let hasEverReachedRunning = false;
    const preferences = readServiceSettings();
    const requestedKey = connectionKey();
    let lastFormatting = formattingSettings();
    const logs = supportLogs(context);
    let launch = logs.begin('Resolving the Java runtime.\n');
    const javaExecutable = await findJavaExecutable(context).catch(error => {
        logs.finish(launch, `Startup failed: ${error}\n`);
        serviceFailure(`Cannot start Ecstasy: ${error}`);
        throw error;
    });
    const logLevel = process.env.XTC_LOG_LEVEL?.toUpperCase() ?? 'INFO';
    const adapterArgs = preferences.adapter === 'default' ? [] : [`-Dxtc.lsp.adapter=${preferences.adapter}`];
    const jvmArgs = [...runtimeJvmOptions(), ...runtimeLogArguments(), ...adapterArgs, ...buildJvmArgs(serverJar, logLevel)];

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
        documentSelector: [{ scheme: 'file', language: 'xtc' }, { scheme: 'ecstasy-library', language: 'xtc' }],
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
        // Our startup handler reports one actionable failure; the default handler also opens
        // raw JSON-RPC and initialization popups for the same failed process.
        initializationFailedHandler: () => false,
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
                if (client !== connection) return null;
                return connection.initializeResult?.capabilities.experimental?.xtcRenameProposal === 1
                    ? renameWithConfiguration(connection, document, position, name, token)
                    : next(document, position, name, token);
            },
            workspace: {
                willRenameFiles: (event, next) => {
                    if (client !== connection) return Promise.resolve(null);
                    return connection.initializeResult?.capabilities.experimental?.xtcFileMoveProposal === 1
                        ? moveWithConfiguration(connection, event) : next(event);
                },
                configuration: (params: ConfigurationParams) => {
                    return params.items.map(item => {
                        if (item.section === 'xtc.compiler') {
                            return compilerConfiguration();
                        }
                        if (item.section === 'xtc.codeLens') {
                            const resource = item.scopeUri ? vscode.Uri.parse(item.scopeUri) : undefined;
                            return { references: vscode.workspace.getConfiguration('xtc.codeLens', resource).get('references', true) };
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
                if (client !== connection) return { action: CloseAction.DoNotRestart, handled: true };
                if (!hasEverReachedRunning) {
                    // Server died before reaching Running state (startup crash).
                    // Do not restart to avoid unhandled rejection issues in vscode-languageclient.
                    updateStatusBar('error');
                    return { action: CloseAction.DoNotRestart, handled: true };
                }
                crashCount++;
                if (crashCount <= MAX_CRASH_RESTARTS) {
                    updateStatusBar('starting');
                    return { action: CloseAction.Restart };
                }
                updateStatusBar('stopped');
                serviceFailure(`Ecstasy Language Server crashed ${crashCount} times and will not restart automatically.`);
                return { action: CloseAction.DoNotRestart, handled: true };
            }
        }
    };

    const connection = new class extends LanguageClient {
        override async start(): Promise<void> {
            try {
                // TODO VSCODE: UP28 — start() can lose its internal startup promise when the
                // transport closes during initialize. Observe the idempotent in-flight start
                // immediately too, before upstream clears it, so its rejection is handled.
                // Remove when upstream always returns/observes that captured promise (X272).
                const starting = super.start();
                await Promise.all([starting, super.start()]);
                if (!this.isRunning()) throw new Error('Server stopped before initialization completed');
            } catch (error) {
                if (client === this) {
                    const message = error instanceof Error ? error.message : String(error);
                    logs.finish(launch, `Startup failed: ${message}\n`);
                    serviceFailure('Ecstasy Language Server could not start. Check the Java runtime and JVM options; details are in the server log.');
                    updateStatusBar('error');
                }
                throw error;
            }
        }

        override error(message: string, data?: unknown, showNotification?: boolean | 'force'): void {
            // Keep full transport detail in Output. Startup/disconnect notifications have an owner.
            super.error(message, data, this.isRunning() ? showNotification : false);
        }

        override stop(timeout?: number): Promise<void> {
            // Upstream invokes void stop() during initialization failure, when stopping is invalid.
            return super.stop(timeout).catch(() => {});
        }

        protected override async createMessageTransports(encoding: string): Promise<MessageTransports> {
            const id = logs.begin(`Java: ${javaExecutable}\nArguments: ${jvmArgs.join(' ')}\n`);
            launch = id;
            const transports = await super.createMessageTransports(encoding);
            this.serverProcess?.stderr?.on('data', (chunk: Buffer) => logs.append(id, chunk.toString()));
            this.serverProcess?.once('exit', (code, signal) => logs.finish(id, `\nProcess exited: code=${code}, signal=${signal ?? 'none'}\n`));
            return transports;
        }
    }(
        'xtcLanguageServer',
        'Ecstasy Language Server',
        serverOptions,
        clientOptions
    );

    client = connection;

    activeConnectionKey = requestedKey;
    const startingClient = client;
    client.onDidChangeState(({ newState }) => {
        if (client !== connection) return;
        const stateMap = {
            [State.Starting]: 'starting' as const,
            [State.Running]: 'ready' as const,
            [State.Stopped]: 'stopped' as const
        };
        
        if (newState === State.Running) {
            crashCount = 0;
            hasEverReachedRunning = true;
            const id = launch;
            void connection.sendRequest<{ adapter: string; logs: { directory: string; traceDirectory: string } }>('xtc/languageServiceStatus')
                .then(status => {
                    if (client === connection && connection.isRunning()) {
                        logs.remember(id, status);
                        updateStatusBar('ready', status.adapter);
                    }
                })
                .catch(error => outputChannel.warn(`Could not record Ecstasy log session: ${error}`));
        }
        
        const status = stateMap[newState as keyof typeof stateMap];
        if (status) {
            updateStatusBar(status);
        }
    });

    updateStatusBar('starting');

    await startingClient.start().catch(err => {
        if (client !== connection) throw err;
        const message = err?.message ?? String(err);
        console.warn('Ecstasy Language Server failed to start:', message);

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
    runtimeJvmOptions(); // Validate before stopping a working connection.
    runtimeLogArguments();
    formattingSettings(); // Reject malformed settings before stopping a valid connection.
    restartRevision++;
    restarting ??= (async () => {
        let applied: number;
        do {
            applied = restartRevision;
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
