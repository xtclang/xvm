import * as assert from 'node:assert';
import * as vscode from 'vscode';
import { getClient, traceChannel } from '../../lsp-client';
import { waitFor } from '../wait';

/** The longest a read waits, against a hang only: the server's first start and analysis take what they take. */
const HANG_MS = 120_000;

/** A server log at Info, VS Code's default level, which keeps the lines written to it. */
function infoLog(): { log: vscode.LogOutputChannel; lines: string[] } {
    const lines: string[] = [];
    const levels = new vscode.EventEmitter<vscode.LogLevel>();
    const write = (message: string | Error) => { lines.push(String(message)); };
    const log = {
        name: 'Ecstasy Language Server', logLevel: vscode.LogLevel.Info, onDidChangeLogLevel: levels.event,
        trace: () => undefined, debug: () => undefined, info: write, warn: write, error: write,
        append: write, appendLine: write, replace: write, clear: () => undefined, show: () => undefined, hide: () => undefined,
        dispose: () => levels.dispose(),
    } as vscode.LogOutputChannel;
    return { log, lines };
}

suite('Server trace', function () {
    this.timeout(HANG_MS + 30_000);

    test('the client traces at the Trace level while xtc.trace.server traces, into the log at its own level', async () => {
        const settings = vscode.workspace.getConfiguration('xtc');
        const { log, lines } = infoLog();
        const channel = traceChannel(log);
        const levels: vscode.LogLevel[] = [];
        const listening = channel.onDidChangeLogLevel(level => levels.push(level));
        try {
            assert.strictEqual(channel.logLevel, vscode.LogLevel.Info);
            await settings.update('trace.server', 'messages', vscode.ConfigurationTarget.Workspace);
            assert.strictEqual(channel.logLevel, vscode.LogLevel.Trace);
            assert.deepStrictEqual(levels, [vscode.LogLevel.Trace]);
            channel.trace("Sending request 'textDocument/hover - (1)'.");
            assert.deepStrictEqual(lines, ["Sending request 'textDocument/hover - (1)'."]);
            await settings.update('trace.server', undefined, vscode.ConfigurationTarget.Workspace);
            assert.strictEqual(channel.logLevel, vscode.LogLevel.Info);
            assert.deepStrictEqual(levels, [vscode.LogLevel.Trace, vscode.LogLevel.Info]);
        } finally {
            listening.dispose();
            await settings.update('trace.server', undefined, vscode.ConfigurationTarget.Workspace);
        }
    });

    test("xtc.trace.server traces the client's messages into the server's log, at the log's own level", async () => {
        const extension = vscode.extensions.getExtension('xtclang.xtc-language');
        assert.ok(extension);
        await extension.activate();
        const uri = vscode.Uri.joinPath(vscode.workspace.workspaceFolders![0].uri, 'hello.x');
        const document = await vscode.workspace.openTextDocument(uri);
        await vscode.window.showTextDocument(document);
        await waitFor(async () => getClient()?.isRunning(), running => running === true, 'The language server runs', HANG_MS);
        const settings = vscode.workspace.getConfiguration('xtc');
        await settings.update('trace.server', 'messages', vscode.ConfigurationTarget.Workspace);
        try {
            await vscode.commands.executeCommand('xtc.showServerOutput');
            const shown = () => vscode.window.visibleTextEditors.map(editor => editor.document)
                .find(each => /Ecstasy Language Server(?:\.\d+)?\.log$/.test(each.uri.path))?.getText() ?? '';
            const at = document.positionAt(document.getText().indexOf('console.print'));
            // the log is at VS Code's default level, Info, which the setting does not change
            await waitFor(async () => {
                await vscode.commands.executeCommand('vscode.executeHoverProvider', uri, at);
                return shown();
            }, log => /Sending request 'textDocument\/hover/.test(log), 'The log traces the hover request', HANG_MS);
        } finally {
            await settings.update('trace.server', undefined, vscode.ConfigurationTarget.Workspace);
            await vscode.commands.executeCommand('xtc.hideServerOutput');
        }
    });
});
