import * as fs from 'node:fs/promises';
import * as path from 'node:path';
import * as vscode from 'vscode';
import type { LanguageClient } from 'vscode-languageclient/node';
import { focusTestWindow } from './native-focus';

/** Trace native commands and events. Observers never waitUntil, apply edits or replay actions. */
export class ExplorerMoveTrace {
    private readonly started = performance.now();
    private readonly events: { milliseconds: number; event: string; data?: unknown }[] = [];
    private readonly subscriptions: vscode.Disposable[];

    constructor(private readonly report: string, root: vscode.Uri, connection?: LanguageClient) {
        this.record('window-state', vscode.window.state);
        const files = (event: vscode.FileRenameEvent) => event.files.map(file => ({
            oldUri: file.oldUri.toString(), newUri: file.newUri.toString()
        }));
        const watcher = vscode.workspace.createFileSystemWatcher(new vscode.RelativePattern(root, '**/*'));
        this.subscriptions = [watcher,
            vscode.window.onDidChangeWindowState(state => this.record('window-state', state)),
            vscode.workspace.onWillRenameFiles(event => this.record('willRenameFiles', files(event))),
            vscode.workspace.onDidRenameFiles(event => this.record('didRenameFiles', files(event))),
            watcher.onDidCreate(uri => this.record('created', uri.toString())),
            watcher.onDidDelete(uri => this.record('deleted', uri.toString())),
            watcher.onDidChange(uri => this.record('changed', uri.toString()))];
        if (connection) {
            const original = connection.sendRequest;
            connection.sendRequest = ((...args: unknown[]) => {
                const method = typeof args[0] === 'string' ? args[0] : (args[0] as { method?: string })?.method;
                const response = Reflect.apply(original, connection, args) as Promise<unknown>;
                if (method !== 'workspace/willRenameFiles') return response;
                this.record('lsp-request', args[1]);
                return response.then(value => {
                    this.record('lsp-reply', value);
                    return value;
                }, error => { this.record('lsp-error', String(error)); throw error; });
            }) as typeof connection.sendRequest;
            this.subscriptions.push({ dispose: () => { connection.sendRequest = original; } });
        }
    }

    record(event: string, data?: unknown): void {
        this.events.push({ milliseconds: performance.now() - this.started, event, data });
    }

    async command<T = unknown>(id: string, ...args: unknown[]): Promise<T | undefined> {
        await focusTestWindow();
        this.record('command-start', { id, args });
        try {
            const value = await vscode.commands.executeCommand<T>(id, ...args);
            this.record('command-end', id);
            return value;
        } catch (error) {
            this.record('command-error', { id, error: error instanceof Error ? error.stack : String(error) });
            throw error;
        }
    }

    async save(): Promise<void> {
        this.subscriptions.forEach(subscription => subscription.dispose());
        await fs.mkdir(path.dirname(this.report), { recursive: true });
        await fs.writeFile(this.report, JSON.stringify({ vscode: vscode.version, events: this.events }, null, 2) + '\n');
    }
}
