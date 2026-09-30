import * as assert from 'node:assert';
import * as vscode from 'vscode';
import { client, eventually, hover, noErrors, playbook } from './support';

export function progressCases(): void {
    playbook('X145', async (workspace, data) => {
        await workspace.write(data.file, data.source);
        await workspace.configure([{ name: data.module, uri: workspace.uri(data.file).toString() }]);
        const document = await workspace.open(data.file);
        const large = `module ${data.module} {\n    static Int value = 1;\n${Array.from({ length: data.methods }, (_, i) =>
            `    Int read${i}() { return value; }`).join('\n')}\n}\n`;
        const active = new Set<vscode.CancellationToken>();
        const original = vscode.window.withProgress;
        // Observe the real SDK-to-workbench call. Forward its reporter and cancellation token
        // unchanged: no synthetic progress events or replacement cancellation source.
        const observed: typeof original = (options, task) => original(options, (reporter, token) => {
            const tracked = options.title === 'Ecstasy: textDocument/references';
            if (tracked) { active.add(token); }
            return Promise.resolve(task(reporter, token)).finally(() => { if (tracked) { active.delete(token); } });
        });
        assert.ok(Reflect.set(vscode.window, 'withProgress', observed));
        async function start(text: string) {
            await workspace.replace(document, text, false);
            let completed = false;
            const outcome = client().sendRequest('textDocument/references', {
                textDocument: { uri: document.uri.toString() }, position: document.positionAt(text.indexOf('value')),
                context: { includeDeclaration: true }
            }).then(() => ({ error: undefined }), (error: unknown) => ({ error })).finally(() => { completed = true; });
            const tokens = await eventually(async () => {
                assert.ok(!completed, 'Workload must remain pending until native progress is exercised');
                return [...active];
            }, values => values.length === 1, 'Real compiler reference progress');
            return { outcome, token: tokens[0] };
        }
        try {
            await vscode.commands.executeCommand('notifications.clearAll');
            const canceled = await start(large);
            await vscode.commands.executeCommand('notifications.showList');
            await vscode.commands.executeCommand('notification.acceptPrimaryAction');
            await eventually(async () => canceled.token.isCancellationRequested, value => value, 'Native progress Cancel action');
            const result = await canceled.outcome;
            assert.strictEqual((result.error as { code?: number })?.code, -32800, 'Pending reference request is canceled');
            await eventually(async () => active.size, size => size === 0, 'Canceled progress disappears');
            assert.ok((await hover(document, document.positionAt(large.indexOf('value')))).includes('Int'));
            await noErrors(document.uri);
            const before = await client().sendRequest<{ pid: number }>('xtc/languageServiceStatus', {});
            const pending = await start(large + '// unsaved restart\n');
            await vscode.commands.executeCommand('xtc.restartServer');
            assert.ok((await pending.outcome).error, 'Restart retires the pending reader');
            await eventually(async () => { try { process.kill(before.pid, 0); return false; } catch { return true; } },
                value => value, 'Old server process exits');
            const after = await client().sendRequest<{ pid: number }>('xtc/languageServiceStatus', {});
            assert.notStrictEqual(after.pid, before.pid);
            assert.strictEqual(document.getText(), large + '// unsaved restart\n');
            await workspace.replace(document, data.source);
            await noErrors(document.uri);
            await eventually(async () => active.size, size => size === 0, 'Disconnected progress disappears');
        } finally {
            Reflect.set(vscode.window, 'withProgress', original);
            await vscode.commands.executeCommand('notifications.hideList');
        }
    });
}
