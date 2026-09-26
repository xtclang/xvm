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

function namedRename(id: 'X102' | 'X104'): void {
    playbook(id, async (workspace, data) => {
        await workspace.write(data.file, data.source);
        await discovered(workspace, async () => {
            const document = await workspace.open(data.file, data.source);
            await noErrors(document.uri);
            const edit = await vscode.commands.executeCommand<vscode.WorkspaceEdit>(
                'vscode.executeDocumentRenameProvider', document.uri, position(document, data.anchor), data.replacement);
            assert.ok(edit);
            assert.strictEqual(edit.get(document.uri).length, data.edits);
            assert.ok(await vscode.workspace.applyEdit(edit));
            await noErrors(document.uri);
            assert.strictEqual(document.getText().split(data.replacement).length - 1, data.edits);
            if ('preserved' in data) assert.ok(document.getText().includes(data.preserved));
        });
    });
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
    namedRename('X102');
    playbook('X103', async (workspace, data) => {
        await workspace.write(data.file, data.source);
        await workspace.write(data.member, data.memberSource);
        await discovered(workspace, async () => {
            const document = await workspace.open(data.file, data.source);
            await noErrors(document.uri);
            const provider = client().getFeature('textDocument/rename').getProvider(document);
            assert.ok(provider);
            // The generic execute-command round trip regroups resource operations before text edits.
            // Call the registered provider used by Rename, preserving its ordered WorkspaceEdit.
            const cancellation = new vscode.CancellationTokenSource();
            try {
                const edit = await provider.provideRenameEdits(document, position(document, data.anchor), data.replacement, cancellation.token);
                assert.ok(edit);
                assert.ok(await vscode.workspace.applyEdit(edit));
            } finally { cancellation.dispose(); }
            await noErrors(document.uri);
            const moved = await vscode.workspace.openTextDocument(workspace.uri(data.destination));
            assert.strictEqual(moved.getText(), data.memberSource.replace(data.anchor, data.replacement));
            assert.strictEqual(await fs.stat(workspace.uri(data.member).fsPath).catch(() => null), null);
            assert.ok(document.getText().includes(data.replacement));
        });
    });

    namedRename('X104');
    playbook('X105', async (workspace, data) => {
        await workspace.write(data.library, data.libraryText);
        await workspace.write(data.file, data.variants[0].source);
        await discovered(workspace, async () => {
            const document = await workspace.open(data.file, data.variants[0].source);
            for (const variant of data.variants) {
                await workspace.replace(document, variant.source);
                await diagnostics(document.uri, values => values.length > 0, 'Unresolved imported type');
                const at = position(document, variant.anchor);
                const action = await eventually(async () => {
                    try {
                        const actions = await vscode.commands.executeCommand<vscode.CodeAction[]>(
                            'vscode.executeCodeActionProvider', document.uri, new vscode.Range(at, at), vscode.CodeActionKind.QuickFix.value);
                        return actions?.find(item => item.title === variant.title);
                    } catch (error) {
                        if (error instanceof Error && error.name === 'Canceled') return undefined;
                        throw error;
                    }
                }, item => !!item?.edit, variant.title);
                assert.ok(action?.edit, variant.title);
                assert.ok(await vscode.workspace.applyEdit(action.edit));
                await noErrors(document.uri);
                assert.ok(document.getText().includes(variant.importText));
            }
        });
    });

}
