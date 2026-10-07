import * as assert from 'node:assert';
import * as vscode from 'vscode';
import { focusTestEditor } from '../native-focus';
import { discovered } from './liveWorkspace';
import { eventually, noErrors, playbook, position } from './support';

export function documentationCases(): void {
    playbook('X278', async (workspace, data) => {
        await workspace.write(data.file, data.source);
        await discovered(workspace, async () => {
            const document = await workspace.open(data.file);
            await noErrors(document.uri);
            const actions = () => vscode.commands.executeCommand<vscode.CodeAction[]>(
                'vscode.executeCodeActionProvider', document.uri,
                new vscode.Range(position(document, data.anchor), position(document, data.anchor)),
                vscode.CodeActionKind.Source.value, 100);
            const found = await eventually(actions, values => !!values?.some(action => action.title === data.title), 'Compiler documentation action');
            const action = found!.find(action => action.title === data.title)!;
            assert.ok(action.edit);
            assert.ok(await vscode.workspace.applyEdit(action.edit));
            assert.strictEqual(document.getText(), data.expected);
            await noErrors(document.uri);
            assert.ok(!(await actions())?.some(action => action.title === data.title), 'Existing documentation is retained');
            for (const [command, expected] of [['undo', data.source], ['redo', data.expected], ['undo', data.source]]) {
                await focusTestEditor(document);
                await vscode.commands.executeCommand(command);
                await eventually(async () => document.getText(), text => text === expected, `${command} documentation`);
                await noErrors(document.uri);
            }
        });
    });
}
