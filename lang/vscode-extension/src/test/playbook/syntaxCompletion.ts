import * as assert from 'node:assert';
import * as vscode from 'vscode';
import { focusTestWindow } from '../native-focus';
import { discovered } from './liveWorkspace';
import { eventually, label, noErrors, playbook } from './support';

export function syntaxCompletionCases(): void {
    for (const id of ['X149', 'X150', 'X151', 'X152'] as const) {
        playbook(id, async (workspace, data) => {
            await workspace.write(data.file, data.variants[0].source);
            await discovered(workspace, async () => {
                const document = await workspace.open(data.file);
                for (const variant of data.variants) {
                    await workspace.replace(document, variant.source);
                    const at = document.positionAt(variant.source.indexOf(variant.anchor) +
                        ('prefix' in variant ? variant.prefix.length : variant.anchor.length));
                    const item = await eventually(async () => (await workspace.completion(document, at))
                        .find(item => label(item) === variant.label), item => !!item, variant.label);
                    assert.ok(item);
                    assert.strictEqual(item.insertText instanceof vscode.SnippetString, id === 'X150');
                    await workspace.accept(document, item);
                    assert.strictEqual(document.getText(), variant.expected);
                    if ('selected' in variant) {
                        const editor = vscode.window.activeTextEditor!;
                        assert.strictEqual(document.getText(editor.selection), variant.selected);
                        await vscode.commands.executeCommand('jumpToNextSnippetPlaceholder');
                        assert.ok(editor.selection.isEmpty, 'Final snippet tab stop leaves no selected placeholder');
                        assert.strictEqual(document.getText(), variant.expected);
                        const body = variant.expected.split('\n').find(line => line.length > 0 && !line.trim())!;
                        assert.strictEqual(document.offsetAt(editor.selection.active), variant.expected.indexOf(`\n${body}\n`) + 1 + body.length);
                    }
                    await noErrors(document.uri);
                    await focusTestWindow();
                    await vscode.commands.executeCommand('workbench.action.focusActiveEditorGroup');
                    await vscode.commands.executeCommand('undo');
                    await eventually(async () => document.getText(), text => text === variant.source, 'One undo restores the completion prefix');
                }
            });
        });
    }
}
