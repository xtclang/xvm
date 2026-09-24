import * as assert from 'node:assert';
import * as vscode from 'vscode';
import { TextDocumentEdit, WorkspaceEdit } from 'vscode-languageclient/node';
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
    playbook('X53', 'local rename updates declarations and captured uses', async workspace => {
        const document = await workspace.open('Rename.x');
        await noErrors(document.uri);
        await applyRename(document, position(document, 'local'), 'renamed');
        assert.strictEqual(document.getText(), fixture('Rename.x').replace(/\blocal\b/g, 'renamed'));
    }, ['F2 input widget, preview and undo interaction']);

    playbook('X54', 'private parameter rename includes named argument labels', async workspace => {
        const document = await workspace.open('Rename.x');
        for (const anchor of ['Int input', 'input=1']) {
            await workspace.replace(document, fixture('Rename.x'));
            await applyRename(document, position(document, anchor, anchor === 'Int input' ? 4 : 0), 'number');
            assert.strictEqual(document.getText(), fixture('Rename.x').replace(/\binput\b/g, 'number'));
            assert.ok(document.getText().includes('number=1'));
        }
    });

    playbook('X55', 'rename rejects silent capture of an untouched property use', async workspace => {
        const document = await workspace.open('Rename.x');
        assert.strictEqual(await rename(document, position(document, 'local'), 'value'), null);
        assert.strictEqual(document.getText(), fixture('Rename.x'));
        await noErrors(document.uri);
    });

    playbook('X56', 'unsupported member and public parameter renames produce no edits', async workspace => {
        const document = await workspace.open('Rename.x');
        for (const anchor of ['pick(', 'Rename', 'value =']) {
            assert.strictEqual(await rename(document, position(document, anchor), 'replacement'), null, anchor);
        }
        await workspace.replace(document, fixture('Rename.x').replace('private Int pick', 'Int pick'));
        assert.strictEqual(await rename(document, position(document, 'Int input', 4), 'replacement'), null);
    });

    playbook('X57', 'rename carries the buffer version and obsolete requests cannot mutate text', async workspace => {
        const document = await workspace.open('Rename.x');
        const edit = await rename(document, position(document, 'local'), 'renamed');
        assert.ok(edit?.documentChanges?.length);
        assert.ok(!edit.changes, 'Only versioned documentChanges are supported');
        const change = edit.documentChanges[0];
        assert.ok(TextDocumentEdit.is(change));
        assert.strictEqual(change.textDocument.version, document.version);
        await workspace.replace(document, '\n' + fixture('Rename.x'));
        assert.notStrictEqual(change.textDocument.version, document.version);
        // The language client's rename provider checks this before returning an edit.
        // Converting directly to vscode.WorkspaceEdit would discard the protocol version.
        assert.strictEqual(client().validateWorkspaceEdit(edit), false, 'Client must reject an edit for the old document version');
        assert.strictEqual(document.getText(), '\n' + fixture('Rename.x'));

        const pending = rename(document, position(document, 'local'), 'renamed').catch(error => {
            assert.ok([-32800, -32801].includes(error.code), String(error));
            return null;
        });
        await workspace.discard(document);
        await pending;
        const reopened = await workspace.open('Rename.x');
        assert.strictEqual(reopened.getText(), fixture('Rename.x'));
        await applyRename(reopened, position(reopened, 'local'), 'renamed');
    }, ['Deterministic in-flight edit/close/sibling races run in XdkCursorServerTest; this editor race may complete before closing']);

    playbook('X58', 'rename recovers after syntax errors and rejects invalid or conflicting names', async workspace => {
        const document = await workspace.open('Rename.x');
        await workspace.replace(document, fixture('Rename.x').replace('Int run()', 'Int broken(\n Int run()'));
        await diagnostics(document.uri, values => values.length > 0, 'Broken rename source');
        assert.strictEqual(await rename(document, position(document, 'local'), 'renamed'), null);
        await workspace.replace(document, fixture('Rename.x'));
        await noErrors(document.uri);
        for (const name of ['not-a-name', 'captured']) {
            assert.strictEqual(await rename(document, position(document, 'local'), name), null, name);
        }
        await applyRename(document, position(document, 'local'), 'renamed');
    });
}
