import * as assert from 'node:assert';
import * as vscode from 'vscode';
import { focusTestWindow } from '../native-focus';
import { discovered } from './liveWorkspace';
import { diagnostics, eventually, noErrors, playbook, position } from './support';

export function memberActionCases(): void {
    playbook('X122', async (workspace, data) => {
        await workspace.write(data.file, data.variants[0].source);
        await discovered(workspace, async () => {
            const document = await workspace.open(data.file);
            for (const variant of data.variants) {
                await workspace.replace(document, variant.source);
                const originalDiagnostics = async () => {
                    if ('initiallyValid' in variant && !variant.initiallyValid) {
                        await diagnostics(document.uri, values => values.some(item => item.severity === vscode.DiagnosticSeverity.Error),
                            'Missing implementation is diagnosed before applying the action');
                    } else await noErrors(document.uri);
                };
                await originalDiagnostics();
                const at = position(document, data.anchor);
                const actions = await eventually(async () => vscode.commands.executeCommand<vscode.CodeAction[]>(
                    'vscode.executeCodeActionProvider', document.uri, new vscode.Range(at, at), undefined, 100),
                actions => Array.isArray(actions) && (!variant.title || actions.some(action => action.title === variant.title)), 'Member action reply');
                if (!variant.title) {
                    assert.ok(!actions?.some(action => /^(Implement|Override) /.test(action.title) && action.title.includes(` ${data.refusalMember}(`)));
                    assert.strictEqual(document.getText(), variant.source);
                    continue;
                }
                const action = actions?.find(action => action.title === variant.title);
                assert.ok(action?.edit);
                assert.ok(await vscode.workspace.applyEdit(action.edit));
                assert.strictEqual(document.getText(), variant.expected);
                await noErrors(document.uri);
                for (const [command, expected] of [['undo', variant.source], ['redo', variant.expected], ['undo', variant.source]]) {
                    await focusTestWindow();
                    await vscode.commands.executeCommand('workbench.action.focusActiveEditorGroup');
                    await vscode.commands.executeCommand(command);
                    await eventually(async () => document.getText(), text => text === expected, `${command} member action`);
                    if (command === 'redo') await noErrors(document.uri);
                    else await originalDiagnostics();
                }
            }
        });
    });
}

export function localRefactoringCases(ids: readonly ('X148' | 'X156' | 'X157' | 'X177' | 'X178' | 'X179' | 'X180' | 'X181' | 'X182' | 'X183' | 'X184')[] = ['X148']): void {
    for (const id of ids) playbook(id, async (workspace, data) => {
        await workspace.write(data.file, data.source);
        await discovered(workspace, async () => {
            const document = await workspace.open(data.file);
            await noErrors(document.uri);
            const start = position(document, data.selected);
            const range = new vscode.Range(start, start.translate(0, data.selected.length));
            if ('refused' in data && data.refused) {
                const actions = await vscode.commands.executeCommand<vscode.CodeAction[]>(
                    'vscode.executeCodeActionProvider', document.uri, range, vscode.CodeActionKind.Refactor.value, 100);
                assert.ok(!actions?.some(item => item.title === data.title));
                assert.strictEqual(document.getText(), data.source);
                return;
            }
            const action = await eventually(async () => {
                const actions = await vscode.commands.executeCommand<vscode.CodeAction[]>(
                    'vscode.executeCodeActionProvider', document.uri, range, vscode.CodeActionKind.Refactor.value, 100);
                return actions?.find(item => item.title === data.title);
            }, item => !!item?.edit, data.title);
            assert.ok(action?.edit);
            assert.ok(await vscode.workspace.applyEdit(action.edit));
            assert.strictEqual(document.getText(), data.expected);
            await noErrors(document.uri);
            for (const [command, expected] of [['undo', data.source], ['redo', data.expected], ['undo', data.source]]) {
                await focusTestWindow();
                await vscode.commands.executeCommand('workbench.action.focusActiveEditorGroup');
                await vscode.commands.executeCommand(command);
                await eventually(async () => document.getText(), text => text === expected, `${command} local refactoring`);
                await noErrors(document.uri);
            }
        });
    });

}
