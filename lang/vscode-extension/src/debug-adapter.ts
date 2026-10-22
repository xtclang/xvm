import * as vscode from 'vscode';
import type { LanguageClient } from 'vscode-languageclient/node';

import { buildMode, GRADLE_NOT_USED, hasGradleBuild } from './embedded-execution';
import { createXtcRunTask, type XtcTaskDefinition } from './task-provider';

type ClientAccess = () => LanguageClient | undefined;

/** The live adapters, by the language server's ID for their session, which routes the server's messages. */
const adapters = new Map<string, LanguageServerDebugAdapter>();

/**
 * Routes the language server's debug session messages to their adapters. JSON-RPC keeps one handler per method,
 * so this is registered once per client rather than per session.
 */
export function attachDebugSessions(client: LanguageClient): vscode.Disposable {
    return client.onNotification('xtc/debugMessage', (params: { sessionId: string; message: vscode.DebugProtocolMessage }) =>
        adapters.get(params.sessionId)?.receive(params.message));
}

/** A stopped or restarted language server takes its debug sessions, and the programs they launched, with it. */
export function endAllDebugSessions(reason: string): void {
    for (const adapter of [...adapters.values()]) {
        adapter.serverStopped(reason);
    }
}

/**
 * An Ecstasy debug session lives in the language server, which builds and runs its program the way Run does. This
 * adapter carries the session's Debug Adapter Protocol messages over the language client's connection:
 * `xtc/debugStart` creates the session, `xtc/debugMessage` notifications carry its messages both ways, and
 * `xtc/debugEnd` ends it, which stops its program.
 */
class LanguageServerDebugAdapter implements vscode.DebugAdapter {
    private readonly messages = new vscode.EventEmitter<vscode.DebugProtocolMessage>();
    readonly onDidSendMessage = this.messages.event;

    constructor(private readonly client: LanguageClient, private readonly sessionId: string,
                private readonly notice: string | undefined) {
        adapters.set(sessionId, this);
    }

    handleMessage(message: vscode.DebugProtocolMessage): void {
        void this.client.sendNotification('xtc/debugMessage', { sessionId: this.sessionId, message })
            .catch(error => this.serverStopped(`The Ecstasy language server did not take the debug message: ${error}`));
        if (this.notice && (message as { command?: string }).command === 'launch') {
            this.event('output', { category: 'console', output: `${this.notice}\n` });
        }
    }

    receive(message: vscode.DebugProtocolMessage): void {
        this.messages.fire(message);
    }

    serverStopped(reason: string): void {
        if (adapters.delete(this.sessionId)) {
            this.event('output', { category: 'stderr', output: `\n${reason}\n` });
            this.event('terminated', {});
        }
    }

    dispose(): void {
        if (adapters.delete(this.sessionId) && this.client.isRunning()) {
            void this.client.sendNotification('xtc/debugEnd', { sessionId: this.sessionId }).catch(() => undefined);
        }
        this.messages.dispose();
    }

    /** An event of the adapter's own, which VS Code shows like the server's. */
    private event(event: string, body: object): void {
        this.messages.fire({ type: 'event', seq: 0, event, body } as vscode.DebugProtocolMessage);
    }
}

export class XtcDebugAdapterDescriptorFactory implements vscode.DebugAdapterDescriptorFactory {
    constructor(private readonly client: ClientAccess) {}

    async createDebugAdapterDescriptor(session: vscode.DebugSession): Promise<vscode.DebugAdapterDescriptor> {
        const client = this.client();
        if (!client?.isRunning()) {
            throw new Error('The Ecstasy language server is not running.');
        }
        const { sessionId } = await client.sendRequest<{ sessionId: string }>('xtc/debugStart');
        const notice = hasGradleBuild(session.workspaceFolder) ? GRADLE_NOT_USED : undefined;
        return new vscode.DebugAdapterInlineImplementation(new LanguageServerDebugAdapter(client, sessionId, notice));
    }
}

/**
 * Launches in embedded mode start a debug session in the language server, which builds and runs the module as the
 * Run button does, under the runtime debugger. In Gradle mode, Run and Debug launches run the module's Gradle task
 * instead, without a debug session.
 */
export class XtcDebugConfigurationProvider implements vscode.DebugConfigurationProvider {
    constructor(private readonly client: ClientAccess) {}

    async resolveDebugConfiguration(
        folder: vscode.WorkspaceFolder | undefined,
        config: vscode.DebugConfiguration,
        _token?: vscode.CancellationToken
    ): Promise<vscode.DebugConfiguration | undefined> {
        const resolved = this.fill(config);
        if (!resolved) {
            return undefined;
        }
        if (buildMode(folder) === 'embedded') {
            return resolved;
        }
        if (!resolved.noDebug) {
            void vscode.window.showInformationMessage('Ecstasy debugging is not available yet; running with Gradle without the debugger.');
        }
        const editor = vscode.window.activeTextEditor;
        const definition: XtcTaskDefinition = {
            type: 'xtc',
            moduleName: resolved.module,
            methodName: resolved.method,
            moduleArguments: typeof resolved.args === 'string' ? resolved.args : undefined,
            args: Array.isArray(resolved.args) ? resolved.args : undefined,
            moduleUri: editor?.document.languageId === 'xtc' ? editor.document.uri.toString() : undefined,
            mode: 'gradle',
        };
        await vscode.tasks.executeTask(createXtcRunTask(definition, this.client));
        // returning undefined ends the launch without starting a debug session
        return undefined;
    }

    /** Fills a launch without a configuration from the active editor's module, which its file then identifies. */
    private fill(config: vscode.DebugConfiguration): vscode.DebugConfiguration | undefined {
        if (!config.type && !config.request && !config.name) {
            const editor = vscode.window.activeTextEditor;
            if (editor?.document.languageId === 'xtc') {
                config.type = 'xtc';
                config.name = 'Run Ecstasy Module';
                config.request = 'launch';
                config.moduleUri = editor.document.uri.toString();
                const text = editor.document.getText();
                const match = /^\s*module\s+(\w+)/m.exec(text);
                if (match) {
                    config.module = match[1];
                }
            }
        }
        if (!config.module) {
            void vscode.window.showInformationMessage('Cannot find an Ecstasy module to run');
            return undefined;
        }
        return config;
    }
}
