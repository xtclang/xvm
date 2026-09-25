import * as assert from 'node:assert';
import * as vscode from 'vscode';
import { TextDocumentEdit, WorkspaceEdit } from 'vscode-languageclient/node';
import { scenarioRegex } from './shared';
import { client, diagnostics, fixture, noErrors, playbook, position, symbols } from './support';

async function rename(document: vscode.TextDocument, at: vscode.Position, newName: string): Promise<WorkspaceEdit | null> {
    return client().sendRequest<WorkspaceEdit | null>('textDocument/rename', {
        textDocument: { uri: document.uri.toString() }, position: at, newName
    });
}

async function applyRename(document: vscode.TextDocument, at: vscode.Position, newName: string): Promise<void> {
    const edit = await vscode.commands.executeCommand<vscode.WorkspaceEdit>('vscode.executeDocumentRenameProvider', document.uri, at, newName);
    assert.ok(edit?.size, 'Rename must produce a workspace edit');
    assert.ok(await vscode.workspace.applyEdit(edit));
    await symbols(document);
    await noErrors(document.uri);
}

export function renameCases(): void {
    playbook('X53', async (workspace, data) => {
        const document = await workspace.open(data.file);
        await noErrors(document.uri);
        await applyRename(document, position(document, data.anchor), data.replaceWith);
        assert.strictEqual(document.getText(), fixture(data.file).replace(scenarioRegex(data.replaceFrom), data.replaceWith));
    });

    playbook('X54', async (workspace, data) => {
        const document = await workspace.open(data.file);
        for (const anchor of data.anchors) {
            await workspace.replace(document, fixture(data.file));
            await applyRename(document, position(document, anchor, anchor === data.declaration ? data.callOffset : data.declarationOffset), data.replaceWith);
            assert.strictEqual(document.getText(), fixture(data.file).replace(scenarioRegex(data.replaceFrom), data.replaceWith));
            assert.ok(document.getText().includes(data.namedArgument));
        }
    });

    playbook('X55', async (workspace, data) => {
        const document = await workspace.open(data.file);
        assert.strictEqual(await rename(document, position(document, data.anchor), data.newName), null);
        assert.strictEqual(document.getText(), fixture(data.file));
        await noErrors(document.uri);
    });

    playbook('X56', async (workspace, data) => {
        const document = await workspace.open(data.file);
        for (const anchor of data.variants) {
            assert.strictEqual(await rename(document, position(document, anchor), data.newName), null, anchor);
        }
        await workspace.replace(document, fixture(data.file).replace(data.replaceFrom, data.replaceWith));
        assert.strictEqual(await rename(document, position(document, data.anchor, data.offset), data.newName), null);
    });

    playbook('X57', async (workspace, data) => {
        const document = await workspace.open(data.file);
        const edit = await rename(document, position(document, data.anchor), data.newName);
        assert.ok(edit?.documentChanges?.length);
        assert.ok(!edit.changes, 'Only versioned documentChanges are supported');
        const change = edit.documentChanges[0];
        assert.ok(TextDocumentEdit.is(change));
        assert.strictEqual(change.textDocument.version, document.version);
        await workspace.replace(document, '\n' + fixture(data.file));
        assert.notStrictEqual(change.textDocument.version, document.version);
        // The language client's rename provider checks this before returning an edit.
        // Converting directly to vscode.WorkspaceEdit would discard the protocol version.
        assert.strictEqual(client().validateWorkspaceEdit(edit), false, 'Client must reject an edit for the old document version');
        assert.strictEqual(document.getText(), '\n' + fixture(data.file));

        const pending = rename(document, position(document, data.anchor), data.newName).catch(error => {
            assert.ok((data.cancellationCodes as (number)[]).includes(error.code), String(error));
            return null;
        });
        await workspace.discard(document);
        await pending;
        const reopened = await workspace.open(data.file);
        assert.strictEqual(reopened.getText(), fixture(data.file));
        await applyRename(reopened, position(reopened, data.anchor), data.newName);
    });

    playbook('X58', async (workspace, data) => {
        const document = await workspace.open(data.file);
        await workspace.replace(document, fixture(data.file).replace(data.replaceFrom, data.replaceWith));
        await diagnostics(document.uri, values => values.length > 0, 'Broken rename source');
        assert.strictEqual(await rename(document, position(document, data.anchor), data.newName), null);
        await workspace.replace(document, fixture(data.file));
        await noErrors(document.uri);
        for (const name of data.invalidNames) {
            assert.strictEqual(await rename(document, position(document, data.anchor), name), null, name);
        }
        await applyRename(document, position(document, data.anchor), data.newName);
    });
}
