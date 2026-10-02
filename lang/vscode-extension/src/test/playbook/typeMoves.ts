import * as assert from 'node:assert';
import * as fs from 'node:fs/promises';
import * as vscode from 'vscode';
import { compilerSettingsLocation, compilerSourceModules } from '../../rename-proposal';
import { SourceModule, sourceGraphKey } from '../../source-graph-configuration';
import { focusTestWindow } from '../native-focus';
import { eventually, noErrors, playbook, symbols } from './support';

export function typeMoveCases(): void {
    (['X161', 'X162', 'X163'] as const).forEach(id => playbook(id, async (workspace, data) => {
        for (const file of data.files) await workspace.write(file.file, file.source);
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
        const move = new vscode.WorkspaceEdit();
        move.renameFile(workspace.uri(data.from), workspace.uri(data.to), { overwrite: false });
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
            // These moves edit settings but leave source text unchanged. Use the settings editor
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
