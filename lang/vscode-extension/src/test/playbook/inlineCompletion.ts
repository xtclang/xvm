import * as assert from 'node:assert';
import * as vscode from 'vscode';
import { InlineCompletionList, InlineCompletionTriggerKind } from 'vscode-languageclient/node';
import { focusTestEditor } from '../native-focus';
import { WorkbenchUi } from '../workbenchUi';
import { client, eventually, noErrors, playbook, symbols } from './support';

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
            const ui = await WorkbenchUi.connect();
            try {
                await focusTestEditor(document);
                const at = document.positionAt(offset);
                const query = (triggerKind: InlineCompletionTriggerKind, selectedCompletionInfo?: { range: vscode.Range; text: string }) =>
                    client().sendRequest<InlineCompletionList>('textDocument/inlineCompletion', {
                        textDocument: { uri: document.uri.toString() }, position: at,
                        context: {
                            triggerKind,
                            selectedCompletionInfo: selectedCompletionInfo && {
                                ...selectedCompletionInfo,
                                range: client().code2ProtocolConverter.asRange(selectedCompletionInfo.range)
                            }
                        }
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
                    const nextText = items[1].insertText;
                    assert.ok(typeof nextText === 'string');
                    await eventually(() => ui.inlineText(), text => text.trim() === nextText.slice(2), 'Next inline alternative is rendered');
                    await vscode.commands.executeCommand('editor.action.inlineSuggest.commit');
                    await eventually(async () => document.getText(), text => text === source.slice(0, offset - 2) + nextText + source.slice(offset), 'Native next inline alternative is accepted');
                    return;
                }
                assert.deepStrictEqual((await query(InlineCompletionTriggerKind.Automatic)).items.map(item => item.insertText), [data.expected]);
                editor.selection = new vscode.Selection(at, at);
                await vscode.commands.executeCommand('editor.action.inlineSuggest.trigger');
                if (data.mode === 'typing') {
                    await vscode.commands.executeCommand('editor.action.inlineSuggest.hide');
                    assert.strictEqual(document.getText(), source, 'Dismissing ghost text does not edit the document');
                    await vscode.commands.executeCommand('default:type', { text: 'w' });
                    await eventually(async () => document.getText(), text => text === source.slice(0, offset) + 'w' + source.slice(offset), 'Typed prefix reaches the document');
                    await symbols(document);
                    await vscode.commands.executeCommand('editor.action.inlineSuggest.trigger');
                }
                await eventually(() => ui.inlineText(), text => text.trim() === data.expected.slice(data.mode === 'typing' ? 4 : 3), 'Inline suggestion is rendered before acceptance');
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
                await ui.close();
                await vscode.commands.executeCommand('editor.action.inlineSuggest.hide');
                await configuration.update('inlineSuggest.enabled', previous, vscode.ConfigurationTarget.Workspace);
            }
        });
    }
}
