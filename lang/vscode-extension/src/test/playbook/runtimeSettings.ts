import * as assert from 'node:assert';
import * as fs from 'node:fs/promises';
import * as path from 'node:path';
import * as vscode from 'vscode';
import { catalog } from './shared';
import { client, noErrors, playbook } from './support';
import { stopLanguageClient } from '../../lsp-client';
import { readArchive } from '../archive';

interface Status { pid: number; jvmOptions: string[]; logs: { directory: string; retention: Record<string, number> } }
export function runtimeSettingsCases(): void {
    for (const id of ['X269', 'X270'] as const) playbook(id, async workspace => {
        const data = catalog.common.runtimeSettings;
        const settings = vscode.workspace.getConfiguration('xtc');
        const previousVm = settings.inspect('java.vmOptions')?.globalValue;
        const previousLogs = settings.inspect('server.logs')?.globalValue;
        const document = await workspace.open(data.file, data.source);
        await noErrors(document.uri);
        const status = () => client().sendRequest<Status>('xtc/languageServiceStatus');
        const before = await status();
        try {
            await settings.update('java.vmOptions', data.vmOptions, vscode.ConfigurationTarget.Global);
            if (id === 'X270') await settings.update('server.logs', data.logs, vscode.ConfigurationTarget.Global);
            assert.strictEqual((await status()).pid, before.pid, 'Saving runtime settings must not restart a working server');
            await vscode.commands.executeCommand('xtc.restartServer');
            await noErrors(document.uri);
            const applied = await status();
            assert.notStrictEqual(applied.pid, before.pid);
            assert.ok(applied.jvmOptions.includes(data.vmOptions[0]));
            if (id === 'X269') {
                await settings.update('java.vmOptions', data.invalidOptions, vscode.ConfigurationTarget.Global);
                await assert.rejects(async () => vscode.commands.executeCommand('xtc.restartServer'), /heap/);
                assert.strictEqual((await status()).pid, applied.pid);
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
                assert.strictEqual((await status()).pid, applied.pid);
            }
            assert.strictEqual(document.getText(), data.source);
        } finally {
            await settings.update('java.vmOptions', previousVm, vscode.ConfigurationTarget.Global);
            await settings.update('server.logs', previousLogs, vscode.ConfigurationTarget.Global);
            await vscode.commands.executeCommand('xtc.restartServer');
        }
    });
    for (const id of ['X271', 'X272'] as const) playbook(id, async workspace => {
        const data = catalog.common.runtimeSettings;
        const settings = vscode.workspace.getConfiguration('xtc');
        const original = settings.inspect('java.vmOptions')?.globalValue;
        const document = await workspace.open(data.file, data.source);
        await noErrors(document.uri);
        const previous = await client().sendRequest<Status>('xtc/languageServiceStatus');
        try {
            if (id === 'X271') await stopLanguageClient();
            else {
                await settings.update('java.vmOptions', data.startupFailureOptions, vscode.ConfigurationTarget.Global);
                await assert.rejects(async () => vscode.commands.executeCommand('xtc.restartServer'));
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
        } finally {
            await settings.update('java.vmOptions', original, vscode.ConfigurationTarget.Global);
            await vscode.commands.executeCommand('xtc.restartServer');
            await noErrors(document.uri);
            assert.strictEqual(document.getText(), data.source);
        }
    });

}
