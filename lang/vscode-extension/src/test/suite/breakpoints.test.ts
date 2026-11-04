import * as assert from 'node:assert';
import * as vscode from 'vscode';
import { waitFor } from '../wait';

suite('Breakpoints', () => {
    test('F9 sets a breakpoint in an Ecstasy file', async () => {
        const extension = vscode.extensions.getExtension('xtclang.xtc-language');
        assert.ok(extension);
        await extension.activate();
        const uri = vscode.Uri.joinPath(vscode.workspace.workspaceFolders![0].uri, 'hello.x');
        const editor = await vscode.window.showTextDocument(await vscode.workspace.openTextDocument(uri));
        await waitFor(async () => editor.document.languageId, id => id === 'xtc', 'hello.x is an Ecstasy file');
        const line = editor.document.getText().split('\n').findIndex(text => text.includes('console.print'));
        editor.selection = new vscode.Selection(line, 8, line, 8);
        vscode.debug.removeBreakpoints(vscode.debug.breakpoints);
        try {
            // VS Code sets a breakpoint only in a language that a debugger's contributes.breakpoints names
            await vscode.commands.executeCommand('editor.debug.action.toggleBreakpoint');
            const set = await waitFor(async () => vscode.debug.breakpoints, breakpoints => breakpoints.length > 0,
                'Toggle Breakpoint sets one');
            assert.ok(set[0] instanceof vscode.SourceBreakpoint);
            assert.strictEqual(set[0].location.uri.toString(), uri.toString());
            assert.strictEqual(set[0].location.range.start.line, line);
        } finally {
            vscode.debug.removeBreakpoints(vscode.debug.breakpoints);
        }
    });
});
