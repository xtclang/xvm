import * as assert from 'node:assert';
import * as fs from 'node:fs/promises';
import * as vscode from 'vscode';
import { TextDocumentEdit, WorkspaceEdit } from 'vscode-languageclient/node';
import { catalog, scenarioRegex, scenarioText } from './shared';
import { client, diagnostics, eventually, fixture, hover, noErrors, playbook, position, symbols, Workspace } from './support';

async function graph(workspace: Workspace): Promise<vscode.TextDocument> {
    const setup = catalog.common.graph;
    for (const module of setup.sourceModules) { await workspace.write(module.uri); }
    await workspace.configure(setup.sourceModules.map(module => ({ ...module, uri: workspace.uri(module.uri).toString() })));
    const document = await workspace.open(setup.root);
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
    return await vscode.commands.executeCommand<vscode.Location[]>('vscode.executeReferenceProvider', document.uri, position(document, catalog.common.graph.referenceAnchor)) ?? [];
}

export function graphCases(): void {
    playbook('X59', async (workspace, data) => {
        const document = await graph(workspace);
        const result = await references(document);
        assert.strictEqual(result.length, data.referenceCount, 'Interface declaration and two interface-dispatch calls; concrete override is a different identity');
        assert.deepStrictEqual(result.map(item => item.uri.toString()).sort(),
            data.modules.map(name => workspace.uri(scenarioText(data.moduleFile, name)).toString()).sort());
        assert.ok(!vscode.workspace.textDocuments.some(item => item.uri.toString() === workspace.uri(data.closedConsumer).toString()), 'Consumer remains unopened');
    });

    playbook('X60', async (workspace, data) => {
        const document = await graph(workspace);
        const proposed = await rename(document, data.anchor, data.replaceWith);
        assert.ok(proposed?.documentChanges?.length === data.moduleCount && !proposed.changes);
        for (const change of proposed.documentChanges) {
            assert.ok(TextDocumentEdit.is(change));
            assert.strictEqual(change.textDocument.version, change.textDocument.uri === document.uri.toString() ? document.version : null);
        }
        const edit = await vscode.commands.executeCommand<vscode.WorkspaceEdit>('vscode.executeDocumentRenameProvider', document.uri, position(document, data.anchor), data.replaceWith);
        assert.ok(edit?.size === data.moduleCount && await vscode.workspace.applyEdit(edit));
        for (const name of data.modules) {
            const changed = await workspace.open(scenarioText(data.file, name));
            await symbols(changed);
            await noErrors(changed.uri);
            assert.strictEqual(changed.getText(), fixture(scenarioText(data.file, name)).replace(scenarioRegex(data.replaceFrom), data.replaceWith));
            assert.strictEqual(await fs.readFile(changed.uri.fsPath, 'utf8'), fixture(scenarioText(data.file, name)), 'Proof and editor edits do not save source files');
        }
    });

    playbook('X61', async (workspace, data) => {
        const document = await graph(workspace);
        assert.strictEqual(await rename(document, data.anchor, data.newName), null);
        assert.strictEqual(document.getText(), fixture(data.file));
        await noErrors(document.uri);
    });

    playbook('X62', async (workspace, data) => {
        await graph(workspace);
        const document = await workspace.open(data.file);
        await noErrors(document.uri);
        const signature = await workspace.signature(document, position(document, data.searchCall, data.searchCall.length));
        assert.ok(signature?.signatures.some(item => scenarioRegex(data.searchName).test(item.label) && scenarioRegex(data.searchArgumentType).test(item.label)));
        assert.strictEqual(await rename(document, data.searchCall, data.searchRename), null);
        assert.match(await hover(document, position(document, data.descriptionCall)), scenarioRegex(data.descriptionType));
        assert.strictEqual(await rename(document, data.descriptionCall, data.descriptionRename), null);
    });

    playbook('X63', async (workspace, data) => {
        const document = await graph(workspace);
        const consumer = await workspace.open(data.file);
        await workspace.replace(consumer, fixture(data.file).replace(data.replaceFrom, data.addedReference));
        assert.strictEqual((await references(document)).filter(item => item.uri.toString() === consumer.uri.toString()).length, data.consumerReferenceCount);
        const edit = await rename(document, data.anchor, data.newName);
        assert.ok(edit?.documentChanges);
        const change = edit.documentChanges.find(item => TextDocumentEdit.is(item) && item.textDocument.uri === consumer.uri.toString());
        assert.ok(change && TextDocumentEdit.is(change));
        assert.strictEqual(change.textDocument.version, consumer.version);
        await workspace.replace(consumer, data.replaceWith);
        await diagnostics(consumer.uri, values => values.length > 0, 'Incomplete graph diagnostic');
        assert.deepStrictEqual(await references(document), []);
        assert.strictEqual(await rename(document, data.anchor, data.newName), null);
        await noErrors(document.uri);
        await workspace.replace(consumer, fixture(data.file));
        await noErrors(consumer.uri);
        assert.strictEqual((await references(document)).length, data.referenceCount);
    });
}
