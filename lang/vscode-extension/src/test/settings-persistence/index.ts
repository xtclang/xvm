import * as assert from 'node:assert';
import * as fs from 'node:fs/promises';
import * as path from 'node:path';
import * as vscode from 'vscode';
import { getClient } from '../../lsp-client';
import { catalog } from '../playbook/shared';
import { eventually } from '../playbook/support';

/** Separate VS Code processes reuse this disposable profile and workspace, with no replay on read. */
export async function run(): Promise<void> {
    const phase = process.env.XTC_SETTINGS_PHASE;
    assert.ok(phase === 'write' || phase === 'read');
    const root = vscode.workspace.workspaceFolders![0].uri;
    const data = catalog.common.runtimeSettings;
    const file = vscode.Uri.joinPath(root, data.file);
    const settings = vscode.workspace.getConfiguration('xtc');
    if (phase === 'write') {
        await vscode.workspace.fs.writeFile(file, Buffer.from(data.source));
        await settings.update('java.vmOptions', data.vmOptions, vscode.ConfigurationTarget.Global);
        await settings.update('server.logs', data.logs, vscode.ConfigurationTarget.Global);
        await settings.update('languageService.textSynchronization', 'incremental', vscode.ConfigurationTarget.Global);
        await settings.update('inlayHints.enabled', false, vscode.ConfigurationTarget.Global);
        await settings.update('inlayHints.enabled', true, vscode.ConfigurationTarget.Workspace);
        await settings.update('compiler.sourceModules', [{ name: 'RuntimeSettings', uri: file.toString(), resourceRoots: [] }], vscode.ConfigurationTarget.Workspace);
    }
    const extension = vscode.extensions.getExtension('xtclang.xtc-language')!;
    await extension.activate();
    await vscode.window.showTextDocument(await vscode.workspace.openTextDocument(file));
    if (phase === 'write') await vscode.commands.executeCommand('xtc.restartServer');
    await eventually(async () => getClient()?.isRunning(), running => running === true, 'Persisted connection starts');
    const status = await getClient()!.sendRequest<{ pid: number; jvmOptions: string[]; textSynchronization: string; logs: { retention: object } }>('xtc/languageServiceStatus');
    assert.strictEqual(status.textSynchronization, 'incremental');
    assert.ok(status.jvmOptions.includes(data.vmOptions[0]));
    assert.deepStrictEqual(status.logs.retention, data.logs);
    const saved = vscode.workspace.getConfiguration('xtc');
    assert.strictEqual(saved.inspect('inlayHints.enabled')!.globalValue, false);
    assert.strictEqual(saved.inspect('inlayHints.enabled')!.workspaceValue, true);
    const report = await vscode.commands.executeCommand<{ configured: Record<string, { value: unknown; origin: string }> }>('xtc.showLanguageServiceStatus');
    assert.strictEqual(report!.configured['inlayHints.enabled'].value, true);
    assert.strictEqual(report!.configured['inlayHints.enabled'].origin, 'workspace');
    assert.deepStrictEqual(saved.get('compiler.sourceModules'), [{ name: 'RuntimeSettings', uri: file.toString(), resourceRoots: [] }]);
    const previous = path.join(root.fsPath, '..', 'settings-write.json');
    if (phase === 'read') assert.notStrictEqual(JSON.parse(await fs.readFile(previous, 'utf8')).pid, status.pid);
    await fs.writeFile(path.join(root.fsPath, '..', `settings-${phase}.json`), JSON.stringify({ status: 'passed', pid: status.pid, configured: report!.configured }, null, 2) + '\n');
}
