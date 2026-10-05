import * as assert from 'node:assert';
import * as vscode from 'vscode';
import { InlineCompletionList, InlineCompletionTriggerKind } from 'vscode-languageclient/node';
import { client, eventually, noErrors, playbook } from './support';

export function inlineCompletionCases(): void {
    for (const id of ['X255', 'X256', 'X257', 'X258'] as const) {
        playbook(id, async (workspace, data) => {
            const offset = data.source.indexOf('§');
            const source = data.source.replace('§', '');
            const document = await workspace.open(data.file, source);
            const editor = await vscode.window.showTextDocument(document);
            const configuration = vscode.workspace.getConfiguration('editor', document.uri);
            const previous = configuration.inspect<boolean>('inlineSuggest.enabled')?.workspaceValue;
            await configuration.update('inlineSuggest.enabled', true, vscode.ConfigurationTarget.Workspace);
            try {
                const at = document.positionAt(offset);
                const query = (triggerKind: InlineCompletionTriggerKind, selectedCompletionInfo?: { range: vscode.Range; text: string }) =>
                    client().sendRequest<InlineCompletionList>('textDocument/inlineCompletion', {
                        textDocument: { uri: document.uri.toString() }, position: at,
                        context: { triggerKind, selectedCompletionInfo }
                    });
                assert.ok(client().initializeResult?.capabilities.inlineCompletionProvider);
                if (data.mode === 'selection') {
                    assert.deepStrictEqual((await query(InlineCompletionTriggerKind.Automatic)).items, []);
                    const items = (await query(InlineCompletionTriggerKind.Invoked)).items;
                    assert.deepStrictEqual(items.map(item => item.insertText).sort(), ['another', 'answer']);
                    const range = new vscode.Range(at.translate(0, -2), at);
                    assert.deepStrictEqual((await query(InlineCompletionTriggerKind.Invoked, { range, text: 'ans' })).items.map(item => item.insertText), ['answer']);
                    assert.deepStrictEqual((await query(InlineCompletionTriggerKind.Invoked, { range, text: 'answer' })).items, []);
                    editor.selection = new vscode.Selection(at, at);
                    await vscode.commands.executeCommand('editor.action.inlineSuggest.trigger');
                    await vscode.commands.executeCommand('editor.action.inlineSuggest.showNext');
                    await vscode.commands.executeCommand('editor.action.inlineSuggest.commit');
                    await eventually(async () => document.getText(), text => text === source.slice(0, offset - 2) + items[1].insertText + source.slice(offset), 'Native next inline alternative is accepted');
                    return;
                }
                assert.deepStrictEqual((await query(InlineCompletionTriggerKind.Automatic)).items.map(item => item.insertText), [data.expected]);
                editor.selection = new vscode.Selection(at, at);
                await vscode.commands.executeCommand('editor.action.inlineSuggest.trigger');
                if (data.mode === 'typing') {
                    await vscode.commands.executeCommand('editor.action.inlineSuggest.hide');
                    assert.strictEqual(document.getText(), source, 'Dismissing ghost text does not edit the document');
                    await vscode.commands.executeCommand('default:type', { text: 'w' });
                    await vscode.commands.executeCommand('editor.action.inlineSuggest.trigger');
                }
                await vscode.commands.executeCommand('editor.action.inlineSuggest.commit');
                const expected = source.slice(0, offset - 3) + data.expected + source.slice(offset);
                await eventually(async () => document.getText(), text => text === expected, 'Native inline acceptance preserves the source suffix');
                if (data.mode === 'accept') {
                    await noErrors(document.uri);
                    await vscode.commands.executeCommand('undo');
                    await eventually(async () => document.getText(), text => text === source, 'One Undo restores the typed prefix');
                } else if (data.mode === 'incomplete') {
                    await workspace.replace(document, expected.replace('consume(answer\n', 'consume(answer);\n'));
                    await noErrors(document.uri);
                }
            } finally {
                await vscode.commands.executeCommand('editor.action.inlineSuggest.hide');
                await configuration.update('inlineSuggest.enabled', previous, vscode.ConfigurationTarget.Workspace);
            }
        });
    }
}
