import * as assert from 'node:assert';
import * as fs from 'node:fs/promises';
import * as vscode from 'vscode';
import { compilerSettingsLocation, compilerSourceModules } from '../../rename-proposal';
import { SourceModule, sourceGraphKey } from '../../source-graph-configuration';
import { focusTestWindow } from '../native-focus';
import { client, eventually, noErrors, playbook, symbols } from './support';

export function typeMoveCases(ids: readonly ('X161' | 'X162' | 'X163' | 'X169' | 'X170' | 'X171' | 'X172' | 'X173' | 'X174' | 'X175' | 'X176' | 'X216' | 'X217' | 'X218' | 'X219')[] = ['X161', 'X162', 'X163']): void {
    ids.forEach(id => playbook(id, async (workspace, data) => {
        for (const file of data.files) await workspace.write(file.file, file.source);
        if ('directories' in data) {
            for (const directory of data.directories) await vscode.workspace.fs.createDirectory(workspace.uri(directory));
        }
        const graph = (modules: SourceModule[]) => modules.map(module => ({
            ...module, uri: workspace.uri(module.uri).toString(),
            resourceRoots: module.resourceRoots?.map(root => workspace.uri(root).toString())
        }));
        await workspace.configure(graph(data.modules));
        const document = await workspace.open(data.root);
        await symbols(document);
        await noErrors(document.uri);
        if ('consumer' in data) {
            assert.ok(!vscode.window.tabGroups.all.flatMap(group => group.tabs).some(tab =>
                tab.input instanceof vscode.TabInputText && tab.input.uri.toString() === workspace.uri(data.consumer).toString()), 'Consumer stays closed before Move');
        }
        const moves = 'moves' in data ? data.moves : [{ from: data.from, to: data.to }];
        if ('refused' in data && data.refused) {
            const beforeGraph = sourceGraphKey(compilerSourceModules());
            const proposal = await client().sendRequest('xtc/renameFiles', { files: moves.map(move => ({
                oldUri: workspace.uri(move.from).toString(), newUri: workspace.uri(move.to).toString()
            })) });
            assert.strictEqual(proposal, null, 'A colliding member rejects the complete proposal');
            for (const file of data.files) assert.strictEqual(await fs.readFile(workspace.uri(file.file).fsPath, 'utf8'), file.source);
            for (const move of moves) assert.strictEqual(await fs.stat(workspace.uri(move.to).fsPath).catch(() => null), null);
            assert.strictEqual(sourceGraphKey(compilerSourceModules()), beforeGraph);
            await noErrors(document.uri);
            return;
        }
        const move = new vscode.WorkspaceEdit();
        for (const item of moves) move.renameFile(workspace.uri(item.from), workspace.uri(item.to), { overwrite: false });
        assert.ok(await vscode.workspace.applyEdit(move, { isRefactoring: true }));

        const verify = async (moved: boolean) => {
            await eventually(async () => Promise.all(data.files.map(async file => {
                const uri = workspace.uri(moved ? file.destination : file.file);
                const buffer = vscode.workspace.textDocuments.find(value => !value.isClosed && value.uri.toString() === uri.toString());
                return buffer?.getText() ?? fs.readFile(uri.fsPath, 'utf8').catch(() => '');
            })), texts => texts.every((text, index) => text === (moved ? data.files[index].expected : data.files[index].source)),
            `Move ${moved ? 'applied' : 'undone'}: all sources and resources agree`);
            for (const file of data.files.filter(value => value.destination !== value.file)) {
                assert.strictEqual(await fs.stat(workspace.uri(moved ? file.file : file.destination).fsPath).catch(() => null), null);
            }
            if ('afterModules' in data) {
                const expected = sourceGraphKey(graph(moved ? data.afterModules : data.modules));
                await eventually(async () => sourceGraphKey(compilerSourceModules()), value => value === expected, 'Persisted graph follows Move/Undo/Redo');
                await noErrors((await workspace.open(moved ? data.afterRoot : data.root)).uri);
                await noErrors((await workspace.open(data.consumer)).uri);
            } else {
                await symbols(document);
                await noErrors(document.uri);
            }
        };
        await verify(true);
        for (const [action, moved] of [['undo', false], ['redo', true]] as const) {
            // Graph moves also edit settings. Use the settings editor
            // as the undo context so the native transaction includes its file operations as well.
            if ('afterModules' in data) {
                await vscode.window.showTextDocument(await vscode.workspace.openTextDocument(compilerSettingsLocation()!.uri), { preview: false });
            }
            await focusTestWindow();
            await vscode.commands.executeCommand('workbench.action.focusActiveEditorGroup');
            await vscode.commands.executeCommand(action);
            await verify(moved);
        }
    }));
}
