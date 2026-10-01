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
