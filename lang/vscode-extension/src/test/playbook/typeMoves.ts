import * as assert from 'node:assert';
import * as fs from 'node:fs/promises';
import * as vscode from 'vscode';
import { focusTestWindow } from '../native-focus';
import { eventually, noErrors, playbook, symbols } from './support';

export function typeMoveCases(): void {
    playbook('X161', async (workspace, data) => {
        for (const file of data.files) await workspace.write(file.file, file.source);
        await workspace.configure(data.modules.map(module => ({ ...module, uri: workspace.uri(module.uri).toString() })));
        const document = await workspace.open(data.root);
        await symbols(document);
        await noErrors(document.uri);
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
            await symbols(document);
            await noErrors(document.uri);
        };
        await verify(true);
        for (const [action, moved] of [['undo', false], ['redo', true]] as const) {
            await focusTestWindow();
            await vscode.commands.executeCommand('workbench.action.focusActiveEditorGroup');
            await vscode.commands.executeCommand(action);
            await verify(moved);
        }
    });
}
