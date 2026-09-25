import * as assert from 'node:assert';
import * as fs from 'node:fs/promises';
import * as vscode from 'vscode';
import { TypeHierarchyItem } from 'vscode-languageclient/node';
import { getClient } from '../../lsp-client';
import { scenarioRegex, scenarioText } from './shared';
import { client, diagnostics, diagnosticCode, eventually, fixture, label, noErrors, playbook, position, symbols, targets } from './support';

export async function hierarchy(document: vscode.TextDocument, at: vscode.Position): Promise<TypeHierarchyItem[]> {
    return await client().sendRequest<TypeHierarchyItem[] | null>('textDocument/prepareTypeHierarchy', {
        textDocument: { uri: document.uri.toString() }, position: at
    }) ?? [];
}

export async function edges(item: TypeHierarchyItem, direction: 'supertypes' | 'subtypes'): Promise<TypeHierarchyItem[]> {
    assert.ok(item, 'Prepared type hierarchy item');
    return await client().sendRequest<TypeHierarchyItem[] | null>(`typeHierarchy/${direction}`, { item }) ?? [];
}

export function moduleCases(): void {
    playbook('X21', async (workspace, data) => {
        const root = await workspace.project();
        const references = await vscode.commands.executeCommand<vscode.Location[]>('vscode.executeReferenceProvider', root.uri, position(root, data.anchor));
        assert.ok(references?.some(item => item.uri.toString() === workspace.uri(data.file).toString()));
        const found = await vscode.commands.executeCommand<vscode.SymbolInformation[]>('vscode.executeWorkspaceSymbolProvider', data.symbolName);
        assert.ok(found?.some(item => item.location.uri.toString() === workspace.uri(data.file).toString()));
        const child = await workspace.open(data.file);
        for (const word of data.uses) {
            const definitions = await targets(child, 'Definition', position(child, word));
            assert.strictEqual(definitions.length, data.targetCount);
            assert.strictEqual(definitions[0].uri.toString(), root.uri.toString());
        }
    });

    playbook('X22', async (workspace, data) => {
        const root = await workspace.project();
        const [base] = await hierarchy(root, position(root, data.baseDeclaration));
        assert.ok(base);
        const children = await edges(base, 'subtypes');
        assert.strictEqual(children.length, data.childCount);
        assert.strictEqual(children[0].name, data.childName);
        assert.strictEqual(vscode.Uri.parse(children[0].uri).fsPath, workspace.uri(data.file).fsPath);
        const parents = await edges(children[0], 'supertypes');
        assert.ok(parents.some(item => scenarioText(data.display, item.name, item.detail).includes(data.typeArgument)), JSON.stringify(parents));
        assert.ok((await edges(base, 'supertypes')).some(item => item.name === data.interfaceName));
        const [named] = await hierarchy(root, position(root, data.interfaceDeclaration, data.offset));
        assert.ok((await edges(named, 'subtypes')).some(item => item.name.startsWith(data.baseName)));
    });

    playbook('X23', async (workspace, data) => {
        const root = await workspace.project();
        const child = await workspace.open(data.memberFile);
        const version = child.version;
        await workspace.replace(root, fixture(data.rootFile).replace(data.replaceFrom, data.replaceWith));
        const errors = await diagnostics(child.uri, values => values.length > 0, 'Sibling diagnostic');
        assert.ok(errors.every(item => diagnosticCode(item) !== data.diagnosticCode));
        assert.strictEqual(child.version, version);
        assert.ok(!vscode.languages.getDiagnostics(root.uri).some(item => diagnosticCode(item) === data.diagnosticCode));
        await workspace.replace(root, fixture(data.rootFile));
        await noErrors(child.uri);
    });

    playbook('X24', async (workspace, data) => {
        const root = await workspace.project();
        const text = fixture(data.memberFile).replace(data.method, data.incompleteMethod);
        const { document, at } = await workspace.marked(data.memberFile, text);
        for (const type of data.types) {
            await workspace.replace(root, fixture(data.rootFile).replace(data.replaceFrom, scenarioText(data.replaceWith, type, type === data.integerType ? data.integerValue : data.stringValue)));
            const item = (await workspace.completion(document, at)).find(value => label(value) === data.label);
            assert.ok(item);
            assert.match(item.detail ?? '', new RegExp(type));
        }
        assert.strictEqual(await fs.readFile(root.uri.fsPath, 'utf8'), fixture(data.rootFile));
    });

    playbook('X25', async (workspace, data) => {
        const root = await workspace.project();
        const uri = workspace.uri(data.file);
        try {
            await client().sendNotification('textDocument/didOpen', { textDocument: {
                uri: uri.toString(), languageId: 'xtc', version: data.version, text: data.text
            } });
            await symbols(root);
            // Fixture filesystem notifications can rebuild the module between these two
            // requests. Reprepare each time: hierarchy items deliberately expire on rebuild.
            await eventually(async () => {
                const [base] = await hierarchy(root, position(root, data.anchor));
                return base ? edges(base, 'subtypes') : [];
            }, children => children.some(item => item.name === data.name), 'Unsaved member in current hierarchy');
            assert.strictEqual(await fs.stat(uri.fsPath).catch(() => undefined), undefined);
            await noErrors(uri);
        } finally {
            await client().sendNotification('textDocument/didClose', { textDocument: { uri: uri.toString() } });
        }
    });

    playbook('X26', async (workspace, data) => {
        const root = await workspace.project();
        const child = await workspace.open(data.file);
        await workspace.replace(child, fixture(data.file).replace(data.anchor, data.replaceWith));
        await diagnostics(child.uri, values => values.length > 0, 'Broken member');
        await workspace.discard(child);
        await symbols(root);
        await noErrors(child.uri);
        const reopened = await workspace.open(data.file);
        assert.strictEqual(reopened.getText(), fixture(data.file));
        assert.strictEqual((await targets(reopened, 'Definition', position(reopened, data.anchor))).length, data.expected);
    });

    playbook('X27', async (workspace, data) => {
        await workspace.project();
        await workspace.write(data.file, data.text);
        const uri = workspace.uri(data.file);
        await diagnostics(uri, values => values.length > 0, 'Closed created member diagnostic');
        await fs.unlink(uri.fsPath);
        await noErrors(uri);
    });

    playbook('X28', async (workspace, data) => {
        await workspace.project();
        const child = await workspace.open(data.file);
        const [old] = await hierarchy(child, position(child, data.anchor));
        assert.ok(old);
        await workspace.replace(child, data.replaceWith);
        await noErrors(child.uri);
        assert.deepStrictEqual(await edges(old, 'supertypes'), []);
        const [fresh] = await hierarchy(child, position(child, data.anchor));
        assert.ok(!(await edges(fresh, 'supertypes')).some(item => item.name.startsWith(data.contains)));
        await workspace.replace(child, fixture(data.file));
        const [restored] = await hierarchy(child, position(child, data.anchor));
        assert.ok((await edges(restored, 'supertypes')).some(item => item.name.startsWith(data.contains)));
    });

    playbook('X29', async (workspace, data) => {
        const { document, at } = await workspace.editing(data.incompleteBody);
        const initial = document.getText();
        const pending = [];
        for (let index = 0; index < 10; index++) {
            await workspace.replace(document, initial.replace(data.integerCall, index % 2 ? data.integerCall : data.stringCall), false);
            pending.push(workspace.signature(document, at));
        }
        await Promise.all(pending);
        const final = await workspace.editing(data.completedBody);
        await noErrors(final.document.uri);
        const signature = await workspace.signature(final.document, position(final.document, data.completedCall, data.offset));
        assert.match(signature?.signatures[0]?.label ?? '', scenarioRegex(data.pattern));
        const root = await workspace.project();
        const child = await workspace.open(data.memberFile);
        for (let index = 0; index < 6; index++) {
            await workspace.replace(root, fixture(data.rootFile).replace(data.replaceFrom, scenarioText(data.replaceWith, index)), false);
        }
        await symbols(child);
        await noErrors(child.uri);
        assert.strictEqual((await targets(child, 'Definition', position(child, data.anchor))).length, data.expected);
    });

    playbook('X30', async (workspace, data) => {
        const { document, at } = await workspace.editing(data.body);
        const token = new vscode.CancellationTokenSource();
        const pending = client().sendRequest('textDocument/completion', { textDocument: { uri: document.uri.toString() }, position: at }, token.token)
            .then(() => undefined, error => { assert.ok((data.cancellationCodes as (number)[]).includes(error.code), String(error)); });
        token.cancel();
        await pending;
        token.dispose();
        await workspace.discard(document);
        const reopened = await workspace.open(data.file);
        await noErrors(reopened.uri);
        const previous = getClient();
        await vscode.commands.executeCommand('xtc.restartServer');
        await eventually(async () => getClient(), value => !!value?.initializeResult && value !== previous, 'Restarted language client');
        await symbols(reopened);
        await noErrors(reopened.uri);
        const current = await workspace.editing(data.body);
        assert.ok((await workspace.completion(current.document, current.at)).some(item => label(item) === data.label));
    });

    playbook('X31', async (workspace, data) => {
        const document = await workspace.open(data.file);
        const capabilities = client().initializeResult!.capabilities;
        for (const capability of data.unsupportedCapabilities) {
            assert.ok(!capabilities[capability as keyof typeof capabilities], capability);
        }
        assert.strictEqual((await vscode.commands.executeCommand<vscode.TextEdit[]>('vscode.executeFormatDocumentProvider', document.uri,
            { tabSize: data.tabSize, insertSpaces: true }))?.length ?? data.editCount, data.editCount);
    });

    playbook('X32', async (workspace, data) => {
        for (const body of data.bodies) {
            const { document, at } = await workspace.editing(body);
            assert.strictEqual((await workspace.completion(document, at)).length, data.completionCount, body);
        }
    });
}
