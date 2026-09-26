import * as assert from 'node:assert';
import * as fs from 'node:fs/promises';
import * as vscode from 'vscode';
import { edges, hierarchy } from './modules';
import { client, diagnostics, eventually, noErrors, playbook, position, targetNames, targets, Workspace } from './support';

async function discovered<T>(workspace: Workspace, body: () => Promise<T>): Promise<T> {
    const original = vscode.workspace.workspaceFolders!.map(folder => ({ uri: folder.uri.toString(), name: folder.name }));
    const folder = { uri: vscode.Uri.file(workspace.directory).toString(), name: 'Live discovery' };
    await client().sendNotification('workspace/didChangeWorkspaceFolders', { event: { added: [folder], removed: original } });
    await vscode.workspace.getConfiguration('xtc.compiler').update('sourceModules', null, vscode.ConfigurationTarget.Workspace);
    try { return await body(); }
    finally {
        await workspace.configure([]);
        await client().sendNotification('workspace/didChangeWorkspaceFolders', { event: { added: original, removed: [folder] } });
    }
}

export function liveWorkspaceCases(): void {
    playbook('X99', async (workspace, data) => {
        await workspace.write(data.file, data.original);
        await workspace.write(data.library, data.libraryText);
        await discovered(workspace, async () => {
            const document = await workspace.open(data.file, data.original);
            await workspace.replace(document, data.changed);
            const found = await eventually(() => targets(document, 'Definition', position(document, data.anchor)),
                values => values.length === 1, 'Unsaved import reaches discovered dependency');
            assert.deepStrictEqual(await targetNames(found), [data.target]);
            const library = await workspace.open(data.library, data.libraryText);
            await workspace.replace(library, data.brokenLibrary);
            await diagnostics(document.uri, values => values.some(item => item.severity === vscode.DiagnosticSeverity.Error), 'Dependency edit reaches unchanged consumer');
            await workspace.discard(library);
            await noErrors(document.uri);
            assert.strictEqual(await fs.readFile(document.uri.fsPath, 'utf8'), data.original);
        });
    });

    playbook('X100', async (workspace, data) => {
        await workspace.write(data.file, data.source);
        await workspace.write(data.neighbor, data.neighborText);
        await discovered(workspace, async () => {
            const document = await workspace.open(data.file, data.source);
            const neighbor = await workspace.open(data.neighbor, data.neighborText);
            await workspace.replace(neighbor, data.brokenNeighbor);
            await diagnostics(neighbor.uri, values => values.length > 0, 'Neighbor is broken');
            const items = await hierarchy(document, position(document, data.anchor));
            assert.strictEqual(items?.length, 1);
            const children = await edges(items[0], 'subtypes');
            assert.deepStrictEqual(children?.map(item => item.name), [data.child]);
            const references = await vscode.commands.executeCommand<vscode.Location[]>('vscode.executeReferenceProvider', document.uri, position(document, data.anchor));
            assert.deepStrictEqual(references, [], 'Incomplete graphs do not claim complete reference results');
            await workspace.replace(neighbor, data.neighborText);
            await noErrors(neighbor.uri);
        });
    });

    playbook('X101', async (workspace, data) => {
        const document = await workspace.open(data.file, data.source);
        await noErrors(document.uri);
        for (const type of data.types) {
            const found = await targets(document, 'Definition', position(document, type));
            assert.deepStrictEqual(await targetNames(found), [type]);
            assert.strictEqual((await fs.stat(found[0].uri.fsPath)).mode & 0o222, 0, 'Bundled source is read-only');
            const edits = await vscode.commands.executeCommand<vscode.TextEdit[]>(
                'vscode.executeFormatDocumentProvider', found[0].uri, { tabSize: 4, insertSpaces: true });
            assert.deepStrictEqual(edits ?? [], []);
            await assert.rejects(client().sendRequest('textDocument/prepareRename', {
                textDocument: { uri: document.uri.toString() }, position: position(document, type)
            }), { message: data.renameRejection });
        }
        const types = await targets(document, 'TypeDefinition', position(document, data.variable));
        assert.deepStrictEqual(await targetNames(types), [data.variableType]);
    });
}
