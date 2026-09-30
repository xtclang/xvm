import * as assert from 'node:assert';
import * as vscode from 'vscode';
import { ProgressType, WorkDoneProgressBegin, WorkDoneProgressEnd, WorkDoneProgressReport } from 'vscode-languageclient/node';
import { client, eventually, hover, noErrors, playbook } from './support';

export function progressCases(): void {
    playbook('X145', async (workspace, data) => {
        await workspace.write(data.file, data.source);
        await workspace.configure([{ name: data.module, uri: workspace.uri(data.file).toString() }]);
        const document = await workspace.open(data.file);
        const large = `module ${data.module} {\n    static Int value = 1;\n${Array.from({ length: data.methods }, (_, i) =>
            `    Int read${i}() { return value; }`).join('\n')}\n}\n`;
        const connection = client();
        const titles = new Map<string | number, string>();
        const original = connection.onProgress;
        const subscribe = original.bind(connection);
        // Observe registration without replacing the SDK handler or inventing progress events.
        connection.onProgress = <P>(type: ProgressType<P>, token: string | number, handler: (value: P) => void) => subscribe(type, token, (value: P) => {
            // The extension is bundled, so its ProgressType singleton has a different identity
            // from the test runner's module. Observe the wire value, not that module singleton.
            if (typeof value === 'object' && value !== null && 'kind' in value) {
                const event = value as WorkDoneProgressBegin | WorkDoneProgressReport | WorkDoneProgressEnd;
                if (event.kind === 'begin') { titles.set(token, event.title); }
                if (event.kind === 'end') { titles.delete(token); }
            }
            handler(value);
        });
        const features = Reflect.get(connection, '_features') as object[];
        const feature = features.find(item => Reflect.get(item, 'activeParts') instanceof Set);
        assert.ok(feature, 'Installed SDK progress feature');
        // Test-only SDK observation: _progress and _cancellationToken are populated by the real
        // workbench withProgress callback. Fail on SDK shape changes rather than mocking the UI.
        const parts = Reflect.get(feature, 'activeParts') as Set<object>;
        assert.ok(parts instanceof Set);
        function active(): vscode.CancellationToken[] {
            return [...parts].filter(part => titles.get(Reflect.get(part, '_token')) === 'Ecstasy: finding references'
                && Reflect.get(part, '_progress')).map(part => Reflect.get(part, '_cancellationToken'));
        }
        async function start(text: string) {
            await workspace.replace(document, text, false);
            let completed = false;
            const deadline = Date.now() + 20_000;
            async function references(): Promise<unknown> {
                assert.strictEqual(document.getText(), text, 'Progress workload was not externally edited');
                try {
                    return await client().sendRequest('textDocument/references', {
                        textDocument: { uri: document.uri.toString() }, position: document.positionAt(text.indexOf('value')),
                        context: { includeDeclaration: true }
                    });
                } catch (error) {
                    // A delayed fixture-create watch can retire this read. Re-request only;
                    // never replay the edit, cancel, restart or an already completed mutation.
                    if ((error as { code?: number }).code === -32801 && Date.now() < deadline) { return references(); }
                    throw error;
                }
            }
            const outcome = references().then(() => ({ error: undefined }), (error: unknown) => ({ error }))
                .finally(() => { completed = true; });
            const tokens = await eventually(async () => {
                assert.ok(!completed, 'Workload must remain pending until native progress is exercised');
                return active();
            }, values => values.length === 1, 'Real compiler reference progress');
            return { outcome, token: tokens[0] };
        }
        try {
            await vscode.commands.executeCommand('notifications.clearAll');
            const canceled = await start(large);
            await vscode.commands.executeCommand('notifications.showList');
            // Cancel the actual workbench token (the same callback used by its Cancel button).
            // Do not activate an arbitrary notification when unrelated progress is also visible.
            const cancel = Reflect.get(canceled.token, 'cancel') as () => void;
            assert.strictEqual(typeof cancel, 'function');
            cancel.call(canceled.token);
            await eventually(async () => canceled.token.isCancellationRequested, value => value, 'Native progress Cancel action');
            const result = await canceled.outcome;
            assert.strictEqual((result.error as { code?: number })?.code, -32800, 'Pending reference request is canceled');
            await eventually(async () => active().length, size => size === 0, 'Canceled progress disappears');
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
            await eventually(async () => active().length, size => size === 0, 'Disconnected progress disappears');
        } finally {
            connection.onProgress = original;
            await vscode.commands.executeCommand('notifications.hideList');
        }
    });
}
