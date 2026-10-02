import * as assert from 'node:assert';
import * as vscode from 'vscode';
import { client, noErrors, playbook, position } from './support';

export function editingClosureCases(): void {
    playbook('X158', async (workspace, data) => {
        await workspace.write(data.libraryFile, data.library);
        await workspace.write(data.file, data.source);
        await workspace.configure([
            { name: data.libraryModule, uri: workspace.uri(data.libraryFile).toString() },
            { name: data.module, uri: workspace.uri(data.file).toString(), dependencies: [data.libraryModule] }
        ]);
        const document = await workspace.open(data.file);
        await noErrors(document.uri);
        const links = await vscode.commands.executeCommand<vscode.DocumentLink[]>('vscode.executeLinkProvider', document.uri, 100);
        assert.deepStrictEqual(links?.map(link => document.getText(link.range)), data.linkNames);
        assert.ok(links?.every(link => link.target?.fsPath === workspace.uri(data.libraryFile).fsPath));
        const result = await client().sendRequest<import('vscode-languageclient/node').LinkedEditingRanges>('textDocument/linkedEditingRange', {
            textDocument: { uri: document.uri.toString() }, position: position(document, data.alias)
        });
        assert.deepStrictEqual(result.ranges.map(range => document.offsetAt(new vscode.Position(range.start.line, range.start.character))),
            data.aliasUses.map(anchor => data.source.indexOf(anchor)));
        const library = await vscode.workspace.openTextDocument(links![0].target!);
        await vscode.window.showTextDocument(library);
        assert.strictEqual(library.getText(), data.library);
    });
}
