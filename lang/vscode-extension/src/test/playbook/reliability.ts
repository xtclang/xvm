import * as assert from 'node:assert';
import * as vscode from 'vscode';
import { CloseAction } from 'vscode-languageclient/node';
import { client, eventually, noErrors, playbook } from './support';

export function reliabilityCases(): void {
    playbook('X146', async (workspace, data) => {
        await workspace.write(data.library, data.original);
        await workspace.write(data.consumer, data.source);
        await workspace.configure([
            { name: data.libraryModule, uri: workspace.uri(data.library).toString() },
            { name: data.consumerModule, uri: workspace.uri(data.consumer).toString(), dependencies: [data.libraryModule] }
        ]);
        const library = await workspace.open(data.library);
        const consumer = await workspace.open(data.consumer);
        const version = consumer.version;
        const connection = client();
        const hints = connection.getFeature('textDocument/inlayHint').getProvider(consumer)!;
        const tokens = connection.getFeature('textDocument/semanticTokens').getProvider(consumer)!;
        const events: string[] = [];
        const subscriptions = [hints.onDidChangeInlayHints.event(() => events.push('hints')),
            tokens.onDidChangeSemanticTokensEmitter.event(() => events.push('tokens'))];
        const cancellation = new vscode.CancellationTokenSource();
        async function verify(expected: string) {
            await eventually(async () => {
                const values = await hints.provider.provideInlayHints(consumer, new vscode.Range(0, 0, consumer.lineCount, 0), cancellation.token);
                return values?.map(hint => typeof hint.label === 'string' ? hint.label : hint.label.map(part => part.value).join('')) ?? [];
            }, labels => labels.some(label => label.includes(expected)), `Untouched consumer receives ${expected} inlay`);
            assert.strictEqual(consumer.version, version);
            assert.strictEqual(consumer.getText(), data.source);
        }
        try {
            await verify(data.before);
            for (const [source, expected] of [[data.changed, data.after], [data.original, data.before]]) {
                events.length = 0;
                await workspace.replace(library, source);
                await eventually(async () => [...events], values => values.includes('hints') && values.includes('tokens'), 'Native providers receive dependency refresh');
                await verify(expected);
                await noErrors(consumer.uri);
            }
        } finally { subscriptions.forEach(subscription => subscription.dispose()); cancellation.dispose(); }
    });

    playbook('X147', async (workspace, data) => {
        await workspace.write(data.file, data.source);
        await workspace.configure([{ name: data.module, uri: workspace.uri(data.file).toString() }]);
        const document = await workspace.open(data.file);
        await noErrors(document.uri);
        const config = vscode.workspace.getConfiguration('xtc');
        const originalSettings = config.inspect('inlayHints.enabled')?.workspaceValue;
        const connection = client();
        const originalRequest = connection.sendRequest;
        const releases = new Set<() => void>();
        let hold = true;
        // Hold a real reply at the UI boundary, so the older command actually finishes last.
        connection.sendRequest = ((...args: unknown[]) => {
            const response = Reflect.apply(originalRequest, connection, args) as Promise<unknown>;
            return hold && args[0] === 'xtc/languageServiceStatus'
                ? response.then(value => new Promise(resolve => {
                    const release = () => { releases.delete(release); resolve(value); };
                    releases.add(release);
                })) : response;
        }) as typeof connection.sendRequest;
        async function delayed() {
            hold = true;
            const pending = vscode.commands.executeCommand('xtc.showLanguageServiceStatus');
            await eventually(async () => releases.size, count => count === 1, 'Older status reply is held');
            hold = false;
            return { pending, release: [...releases][0] };
        }
        try {
            const older = await delayed();
            assert.ok(await vscode.commands.executeCommand('xtc.showLanguageServiceStatus'));
            older.release();
            assert.strictEqual(await older.pending, undefined, 'Older command cannot replace newer report');
            const settings = await delayed();
            await config.update('inlayHints.enabled', !config.get('inlayHints.enabled', true), vscode.ConfigurationTarget.Workspace);
            settings.release();
            assert.strictEqual(await settings.pending, undefined, 'Old report cannot publish after settings change');
            const retired = await delayed();
            const before = await Reflect.apply(originalRequest, connection, ['xtc/languageServiceStatus']) as { pid: number };
            await vscode.commands.executeCommand('xtc.restartServer');
            retired.release();
            assert.strictEqual(await retired.pending, undefined, 'Retired connection cannot publish its old report');
            assert.strictEqual((await connection.clientOptions.errorHandler!.closed()).action, CloseAction.DoNotRestart);
            const current = await vscode.commands.executeCommand<{ effective: { pid: number } }>('xtc.showLanguageServiceStatus');
            assert.notStrictEqual(current!.effective.pid, before.pid);
            await eventually(async () => { try { process.kill(before.pid, 0); return false; } catch { return true; } }, value => value, 'Retired server exits');
            assert.strictEqual(document.getText(), data.source);
        } finally {
            connection.sendRequest = originalRequest;
            releases.forEach(release => release());
            await config.update('inlayHints.enabled', originalSettings, vscode.ConfigurationTarget.Workspace);
        }
    });
}
