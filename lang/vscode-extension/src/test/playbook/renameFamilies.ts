import * as assert from 'node:assert';
import * as fs from 'node:fs/promises';
import * as vscode from 'vscode';
import { compilerSourceModules } from '../../rename-proposal';
import { discovered } from './liveWorkspace';
import { client, eventually, noErrors, playbook, position, symbols, Workspace } from './support';

/** Inspect edits to closed consumers without opening their editor tabs before Undo. */
async function contents(workspace: Workspace, file: string): Promise<string> {
    const uri = workspace.uri(file);
    return vscode.workspace.textDocuments.find(document => !document.isClosed && document.uri.toString() === uri.toString())?.getText()
        ?? fs.readFile(uri.fsPath, 'utf8');
}

export function renameFamilyCases(): void {
    for (const id of ['X109', 'X110', 'X111', 'X112', 'X113', 'X114', 'X115', 'X116', 'X117', 'X118', 'X119', 'X120', 'X121'] as const) {
        playbook(id, async (workspace, data) => {
            for (const file of data.files) await workspace.write(file.file, file.source);
            await discovered(workspace, async () => {
                if ('sourceModules' in data) await workspace.configure(data.sourceModules.map(module => ({ ...module, uri: workspace.uri(module.uri).toString() })));
                const document = await workspace.open(data.file);
                await noErrors(document.uri);
                for (const file of data.files.filter(file => file.file !== data.file)) {
                    assert.ok(!vscode.workspace.textDocuments.some(document => !document.isClosed && document.uri.fsPath === workspace.uri(file.file).fsPath),
                        `Consumer must be closed before rename: ${file.file}`);
                }
                const provider = client().getFeature('textDocument/rename').getProvider(document);
                assert.ok(provider);
                const cancellation = new vscode.CancellationTokenSource();
                try {
                    if (data.files.some(file => file.file !== file.destination && file.source !== file.expected)) {
                        const configuration = vscode.workspace.getConfiguration('files.refactoring');
                        const previous = configuration.inspect<boolean>('autoSave')?.workspaceValue;
                        try {
                            await configuration.update('autoSave', false, vscode.ConfigurationTarget.Workspace);
                            const refusal = await eventually(async () => {
                                try {
                                    const edit = await provider.provideRenameEdits(document, position(document, data.anchor), data.replacement, cancellation.token);
                                    assert.ok(!edit, 'An unsafe resource history must not be offered');
                                    return undefined;
                                } catch (error) { return error; }
                            }, Boolean, 'Resource rename refuses disabled refactoring auto-save');
                            assert.match(String(refusal), /requires files\.refactoring\.autoSave/);
                            for (const file of data.files) assert.strictEqual(await contents(workspace, file.file), file.source);
                        } finally { await configuration.update('autoSave', previous, vscode.ConfigurationTarget.Workspace); }
                    }
                    const edit = await eventually(
                        async () => provider.provideRenameEdits(document, position(document, data.anchor), data.replacement, cancellation.token),
                        Boolean, `${id} rename proof after fixture discovery`);
                    assert.ok(edit);
                    assert.ok(await vscode.workspace.applyEdit(edit, { isRefactoring: true }));
                } finally { cancellation.dispose(); }
                for (const file of data.files) {
                    assert.strictEqual(await contents(workspace, file.destination), file.expected);
                    if (file.destination !== file.file) assert.strictEqual(await fs.stat(workspace.uri(file.file).fsPath).catch(() => null), null);
                }
                const destination = data.files.find(file => file.file === data.file)!.destination;
                const renamed = await workspace.open(destination);
                await symbols(renamed);
                await noErrors(renamed.uri);
                if ('sourceModules' in data) {
                    assert.ok(JSON.stringify(compilerSourceModules()).includes('Renamed.example.org'));
                    assert.ok(!JSON.stringify(compilerSourceModules()).includes('Library.example.org'));
                    assert.ok(await vscode.workspace.saveAll());
                }
                await vscode.commands.executeCommand('workbench.action.focusActiveEditorGroup');
                await vscode.commands.executeCommand('undo');
                await eventually(() => contents(workspace, data.file).catch(() => ''),
                    text => text === data.files.find(file => file.file === data.file)!.source, `${id} undo restores the request document`);
                for (const file of data.files) {
                    assert.strictEqual(await contents(workspace, file.file), file.source);
                    if (file.destination !== file.file) assert.strictEqual(await fs.stat(workspace.uri(file.destination).fsPath).catch(() => null), null);
                }
                const restored = await workspace.open(data.file);
                await symbols(restored);
                await noErrors(restored.uri);
                if ('sourceModules' in data) {
                    assert.ok(JSON.stringify(compilerSourceModules()).includes('Library.example.org'));
                    assert.ok(!JSON.stringify(compilerSourceModules()).includes('Renamed.example.org'));
                    await vscode.commands.executeCommand('workbench.action.focusActiveEditorGroup');
                    await vscode.commands.executeCommand('redo');
                    await eventually(async () => compilerSourceModules(),
                        graph => JSON.stringify(graph).includes('Renamed.example.org'), 'Redo restores the replacement graph');
                    await eventually(async () => Promise.all(data.files.map(file => contents(workspace, file.destination).catch(() => ''))),
                        texts => texts.every((text, index) => text === data.files[index].expected), 'Redo restores every source and resource');
                    await workspace.open(destination);
                    await vscode.commands.executeCommand('workbench.action.focusActiveEditorGroup');
                    await vscode.commands.executeCommand('undo');
                    await eventually(async () => compilerSourceModules(),
                        graph => JSON.stringify(graph).includes('Library.example.org'), 'Second undo restores the original graph');
                    for (const file of data.files) assert.strictEqual(await contents(workspace, file.file), file.source);
                    assert.ok(await vscode.workspace.saveAll());
                }
            });
        });
    }
}
