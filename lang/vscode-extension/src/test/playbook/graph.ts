import * as assert from 'node:assert';
import * as fs from 'node:fs/promises';
import * as vscode from 'vscode';
import { TextDocumentEdit, WorkspaceEdit } from 'vscode-languageclient/node';
import { client, diagnostics, eventually, fixture, hover, noErrors, playbook, position, symbols, Workspace } from './support';

async function graph(workspace: Workspace): Promise<vscode.TextDocument> {
    for (const name of ['Contracts', 'Uses', 'Dormant']) { await workspace.write(`${name}.x`); }
    await workspace.configure([
        { name: 'Contracts', uri: workspace.uri('Contracts.x').toString() },
        { name: 'Uses', uri: workspace.uri('Uses.x').toString(), dependencies: ['Contracts'] },
        { name: 'Dormant', uri: workspace.uri('Dormant.x').toString(), dependencies: ['Contracts'] }
    ]);
    const document = await workspace.open('Contracts.x');
    await noErrors(document.uri);
    return document;
}

async function rename(document: vscode.TextDocument, anchor: string, name: string): Promise<WorkspaceEdit | null> {
    // Creation/configuration notifications may legitimately retire a captured graph. Retry only
    // explicit cancellation/content-modified responses, never an absent or incorrect semantic answer.
    const result = await eventually(async () => {
        try {
            const value = await client().sendRequest<WorkspaceEdit | null>('textDocument/rename', {
                textDocument: { uri: document.uri.toString() }, position: position(document, anchor), newName: name
            });
            return { value };
        } catch (error) {
            if ([-32800, -32801].includes((error as { code: number }).code)) { return undefined; }
            throw error;
        }
    }, value => value !== undefined, 'Graph rename completes for current inputs');
    return result!.value;
}

async function references(document: vscode.TextDocument): Promise<vscode.Location[]> {
    return await vscode.commands.executeCommand<vscode.Location[]>('vscode.executeReferenceProvider', document.uri, position(document, 'map(')) ?? [];
}

export function graphCases(): void {
    playbook('X59', 'exact references include unopened configured consumers', async workspace => {
        const document = await graph(workspace);
        const result = await references(document);
        assert.strictEqual(result.length, 3, 'Interface declaration and two interface-dispatch calls; concrete override is a different identity');
        assert.deepStrictEqual(result.map(item => item.uri.toString()).sort(),
            ['Contracts', 'Uses', 'Dormant'].map(name => workspace.uri(`${name}.x`).toString()).sort());
        assert.ok(!vscode.workspace.textDocuments.some(item => item.uri.toString() === workspace.uri('Dormant.x').toString()), 'Consumer remains unopened');
    }, ['References panel presentation']);

    playbook('X60', 'generic override rename edits every configured declaration and call', async workspace => {
        const document = await graph(workspace);
        const proposed = await rename(document, 'map(', 'convert');
        assert.ok(proposed?.documentChanges?.length === 3 && !proposed.changes);
        for (const change of proposed.documentChanges) {
            assert.ok(TextDocumentEdit.is(change));
            assert.strictEqual(change.textDocument.version, change.textDocument.uri === document.uri.toString() ? document.version : null);
        }
        const edit = await vscode.commands.executeCommand<vscode.WorkspaceEdit>('vscode.executeDocumentRenameProvider', document.uri, position(document, 'map('), 'convert');
        assert.ok(edit?.size === 3 && await vscode.workspace.applyEdit(edit));
        for (const name of ['Contracts', 'Uses', 'Dormant']) {
            const changed = await workspace.open(`${name}.x`);
            await symbols(changed);
            await noErrors(changed.uri);
            assert.strictEqual(changed.getText(), fixture(`${name}.x`).replace(/\bmap\b/g, 'convert'));
            assert.strictEqual(await fs.readFile(changed.uri.fsPath, 'utf8'), fixture(`${name}.x`), 'Proof and editor edits do not save source files');
        }
    }, ['F2 preview, confirmation and undo across files']);

    playbook('X61', 'graph rename rejects silent capture of an untouched overload call', async workspace => {
        const document = await graph(workspace);
        assert.strictEqual(await rename(document, 'pick(', 'choose'), null);
        assert.strictEqual(document.getText(), fixture('Contracts.x'));
        await noErrors(document.uri);
    }, ['A separate compiler regression verifies the rejected source still compiles and would change overload selection']);

    playbook('X62', 'bundled XDK members resolve but their binary contracts cannot be renamed', async workspace => {
        await graph(workspace);
        const document = await workspace.open('Uses.x');
        await noErrors(document.uri);
        const signature = await workspace.signature(document, position(document, 'indexOf(', 'indexOf('.length));
        assert.ok(signature?.signatures.some(item => /indexOf/.test(item.label) && /Char/.test(item.label)));
        assert.strictEqual(await rename(document, 'indexOf(', 'locate'), null);
        assert.match(await hover(document, position(document, 'toString(')), /String/);
        assert.strictEqual(await rename(document, 'toString(', 'describe'), null);
    });

    playbook('X63', 'graph queries use unsaved consumer text and fail closed on an incomplete graph', async workspace => {
        const document = await graph(workspace);
        const consumer = await workspace.open('Dormant.x');
        await workspace.replace(consumer, fixture('Dormant.x').replace('mapper.map("c")', 'mapper.map("c")+mapper.map("d")'));
        assert.strictEqual((await references(document)).filter(item => item.uri.toString() === consumer.uri.toString()).length, 2);
        const edit = await rename(document, 'map(', 'convert');
        assert.ok(edit?.documentChanges);
        const change = edit.documentChanges.find(item => TextDocumentEdit.is(item) && item.textDocument.uri === consumer.uri.toString());
        assert.ok(change && TextDocumentEdit.is(change));
        assert.strictEqual(change.textDocument.version, consumer.version);
        await workspace.replace(consumer, 'module Dormant { MissingType broken; }');
        await diagnostics(consumer.uri, values => values.length > 0, 'Incomplete graph diagnostic');
        assert.deepStrictEqual(await references(document), []);
        assert.strictEqual(await rename(document, 'map(', 'convert'), null);
        await noErrors(document.uri);
        await workspace.replace(consumer, fixture('Dormant.x'));
        await noErrors(consumer.uri);
        assert.strictEqual((await references(document)).length, 3);
    }, ['Controlled edit/close/configuration/dependency/cancellation races run in XdkProjectQueryLifecycleTest and XdkCursorServerTest']);
}
