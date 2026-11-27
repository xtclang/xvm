import * as assert from 'node:assert';
import * as fs from './disk';
import * as path from 'node:path';
import * as vscode from 'vscode';
import { stopLanguageClient } from '../../lsp-client';
import { readArchive } from '../archive';
import { withPlaybookCleanup } from '../wait';
import { WorkbenchUi } from '../workbenchUi';
import { catalog } from './shared';
import { client, noErrors, playbook } from './support';

interface Status { pid: number; jvmOptions: string[]; logs: { directory: string; retention: Record<string, number> } }
export function runtimeSettingsCases(): void {
    for (const id of ['X269', 'X270'] as const) playbook(id, async workspace => {
        const data = catalog.common.runtimeSettings;
        const settings = vscode.workspace.getConfiguration('xtc');
        const previousVm = settings.inspect('java.vmOptions')?.globalValue;
        const previousLogs = settings.inspect('server.logs')?.globalValue;
        const previousPolicy = settings.inspect('server.memoryPolicy')?.globalValue;
        const previousHeap = settings.inspect('server.maxHeap')?.globalValue;
        const document = await workspace.open(data.file, data.source);
        await noErrors(document.uri);
        const status = () => client().sendRequest<Status>('xtc/languageServiceStatus');
        const before = await status();
        const ui = await WorkbenchUi.connect();
        await withPlaybookCleanup(async () => {
            await settings.update('java.vmOptions', data.vmOptions, vscode.ConfigurationTarget.Global);
            if (id === 'X270') await settings.update('server.logs', data.logs, vscode.ConfigurationTarget.Global);
            assert.strictEqual((await status()).pid, before.pid, 'Saving runtime settings must not restart a working server');
            await vscode.commands.executeCommand('xtc.restartServer');
            await noErrors(document.uri);
            const applied = await status();
            assert.notStrictEqual(applied.pid, before.pid);
            assert.ok(applied.jvmOptions.includes(data.vmOptions[0]));
            if (id === 'X269') {
                // the maximum heap and the memory policy reach the restarted server, unless the JVM options choose
                const restarted = async () => {
                    await vscode.commands.executeCommand('xtc.restartServer');
                    await noErrors(document.uri);
                    return (await status()).jvmOptions;
                };
                await settings.update('java.vmOptions', [], vscode.ConfigurationTarget.Global);
                await settings.update('server.maxHeap', data.maxHeap, vscode.ConfigurationTarget.Global);
                const bounded = await restarted();
                assert.ok(bounded.includes(data.maxHeapOption) && !bounded.includes(data.vmOptions[0]), JSON.stringify(bounded));
                await settings.update('server.memoryPolicy', 'adaptive', vscode.ConfigurationTarget.Global);
                const adaptive = await restarted();
                assert.ok(data.adaptiveOptions.every(option => adaptive.includes(option)) && !adaptive.includes(data.maxHeapOption),
                    JSON.stringify(adaptive));
                await settings.update('java.vmOptions', data.vmOptions, vscode.ConfigurationTarget.Global);
                const chosen = await restarted();
                assert.ok(chosen.includes(data.vmOptions[0]) && !chosen.includes(data.adaptiveOptions[0]), JSON.stringify(chosen));

                const working = await status();
                await settings.update('java.vmOptions', data.invalidOptions, vscode.ConfigurationTarget.Global);
                await assert.rejects(async () => vscode.commands.executeCommand('xtc.restartServer'), /heap/);
                await ui.shows(ui.page.locator('.notification-list-item').filter({ hasText: 'Invalid Ecstasy runtime/log settings' })
                    .filter({ hasText: 'heap' }), 'The notification of the invalid heap');
                assert.strictEqual((await status()).pid, working.pid);
                await noErrors(document.uri);
            } else {
                assert.deepStrictEqual(applied.logs.retention, data.logs);
                assert.notStrictEqual(applied.logs.directory, before.logs.directory);
                const destination = vscode.Uri.file(path.join(workspace.directory, 'server-logs.zip'));
                await vscode.commands.executeCommand('xtc.exportServerLogs', destination);
                const archive = await fs.readFile(destination.fsPath);
                assert.strictEqual(archive.readUInt32LE(0), 0x04034b50);
                assert.ok(archive.includes(Buffer.from('manifest.json')) && archive.includes(Buffer.from('server.log')));
                assert.ok(archive.length < 6 * 1024 * 1024);
                await settings.update('server.logs', { ...data.logs, totalSizeMb: 0 }, vscode.ConfigurationTarget.Global);
                await assert.rejects(async () => vscode.commands.executeCommand('xtc.restartServer'), /retention/i);
                await ui.shows(ui.page.locator('.notification-list-item').filter({ hasText: 'Invalid Ecstasy runtime/log settings' })
                    .filter({ hasText: 'retention' }), 'The notification of the invalid retention');
                assert.strictEqual((await status()).pid, applied.pid);
            }
            assert.strictEqual(document.getText(), data.source);
        }, async () => {
            try {
                await settings.update('java.vmOptions', previousVm, vscode.ConfigurationTarget.Global);
                await settings.update('server.logs', previousLogs, vscode.ConfigurationTarget.Global);
                await settings.update('server.memoryPolicy', previousPolicy, vscode.ConfigurationTarget.Global);
                await settings.update('server.maxHeap', previousHeap, vscode.ConfigurationTarget.Global);
                await vscode.commands.executeCommand('xtc.restartServer');
                await vscode.commands.executeCommand('notifications.clearAll');
            } finally { await ui.close(); }
        });
    });
    for (const id of ['X271', 'X272'] as const) playbook(id, async workspace => {
        const data = catalog.common.runtimeSettings;
        const settings = vscode.workspace.getConfiguration('xtc');
        const original = settings.inspect('java.vmOptions')?.globalValue;
        const document = await workspace.open(data.file, data.source);
        await noErrors(document.uri);
        const previous = await client().sendRequest<Status>('xtc/languageServiceStatus');
        const ui = await WorkbenchUi.connect();
        const unhandled: unknown[] = [];
        const rejected = (error: unknown) => { unhandled.push(error); };
        process.on('unhandledRejection', rejected);
        await withPlaybookCleanup(async () => {
            if (id === 'X271') await stopLanguageClient();
            else {
                await settings.update('java.vmOptions', data.startupFailureOptions, vscode.ConfigurationTarget.Global);
                await assert.rejects(async () => vscode.commands.executeCommand('xtc.restartServer'),
                    /initialization|connection|start/i);
                await ui.shows(ui.page.locator('.notification-list-item').filter({ hasText: 'Ecstasy Language Server could not start' }),
                    'The notification that the server could not start');
                const failures = (await ui.page.locator('.notification-list-item').allTextContents())
                    .filter(text => /could not start|Server initialization failed|Pending response rejected|Connection to server got closed/.test(text));
                assert.strictEqual(failures.length, 1, 'One actionable notification per failed launch');
            }
            const destination = vscode.Uri.file(path.join(workspace.directory, 'offline.zip'));
            await vscode.commands.executeCommand('xtc.exportServerLogs', destination);
            const entries = await readArchive(await fs.readFile(destination.fsPath));
            const manifest = JSON.parse(entries.get('manifest.json')!.toString());
            assert.strictEqual(manifest.offline, true);
            assert.ok(entries.has('launcher.log'));
            if (id === 'X271') {
                assert.strictEqual(manifest.logs.directory, previous.logs.directory);
                assert.ok([...entries.keys()].some(name => name.endsWith('server.log')));
            } else {
                assert.ok(entries.get('launcher.log')!.includes(data.startupFailureOptions[0]));
                assert.strictEqual(manifest.logs, undefined, 'Failed launch must not reuse an earlier successful session');
            }
        }, async () => {
            try {
                await settings.update('java.vmOptions', original, vscode.ConfigurationTarget.Global);
                await vscode.commands.executeCommand('xtc.restartServer');
                await noErrors(document.uri);
                assert.strictEqual(document.getText(), data.source);
                await vscode.commands.executeCommand('notifications.clearAll');
            } finally {
                process.off('unhandledRejection', rejected);
                await ui.close();
            }
        });
        assert.deepStrictEqual(unhandled, [], 'Startup failures must reject the command without orphaned promise rejections');
    });

}
