import * as assert from 'node:assert';
import * as fs from 'node:fs/promises';
import * as vscode from 'vscode';
import { TypeHierarchyItem } from 'vscode-languageclient/node';
import { getClient } from '../../lsp-client';
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
    playbook('X21', 'closed module members participate in navigation', async workspace => {
        const root = await workspace.project();
        const references = await vscode.commands.executeCommand<vscode.Location[]>('vscode.executeReferenceProvider', root.uri, position(root, 'Base<Element>'));
        assert.ok(references?.some(item => item.uri.toString() === workspace.uri('Project/Child.x').toString()));
        const found = await vscode.commands.executeCommand<vscode.SymbolInformation[]>('vscode.executeWorkspaceSymbolProvider', 'Child');
        assert.ok(found?.some(item => item.location.uri.toString() === workspace.uri('Project/Child.x').toString()));
        const child = await workspace.open('Project/Child.x');
        for (const word of ['Base<String>', 'echo("member")']) {
            const definitions = await targets(child, 'Definition', position(child, word));
            assert.strictEqual(definitions.length, 1);
            assert.strictEqual(definitions[0].uri.toString(), root.uri.toString());
        }
    });

    playbook('X22', 'direct generic type hierarchy spans files', async workspace => {
        const root = await workspace.project();
        const [base] = await hierarchy(root, position(root, 'Base<Element>'));
        assert.ok(base);
        const children = await edges(base, 'subtypes');
        assert.strictEqual(children.length, 1);
        assert.strictEqual(children[0].name, 'Child');
        assert.strictEqual(vscode.Uri.parse(children[0].uri).fsPath, workspace.uri('Project/Child.x').fsPath);
        const parents = await edges(children[0], 'supertypes');
        assert.ok(parents.some(item => `${item.name} ${item.detail}`.includes('String')), JSON.stringify(parents));
        assert.ok((await edges(base, 'supertypes')).some(item => item.name === 'Named'));
        const [named] = await hierarchy(root, position(root, 'interface Named', 10));
        assert.ok((await edges(named, 'subtypes')).some(item => item.name.startsWith('Base')));
    }, ['Type-hierarchy tree rendering and editor action availability']);

    playbook('X23', 'unsaved root edits invalidate sibling diagnostics', async workspace => {
        const root = await workspace.project();
        const child = await workspace.open('Project/Child.x');
        const version = child.version;
        await workspace.replace(root, fixture('Project.x').replace('class Base', 'class Renamed'));
        const errors = await diagnostics(child.uri, values => values.length > 0, 'Sibling diagnostic');
        assert.ok(errors.every(item => diagnosticCode(item) !== 'EMB-5'));
        assert.strictEqual(child.version, version);
        assert.ok(!vscode.languages.getDiagnostics(root.uri).some(item => diagnosticCode(item) === 'EMB-5'));
        await workspace.replace(root, fixture('Project.x'));
        await noErrors(child.uri);
    });

    playbook('X24', 'member completion sees the unsaved root type', async workspace => {
        const root = await workspace.project();
        const text = fixture('Project/Child.x').replace('String answer()', 'void inspect() { ite§; }\n    String answer()');
        const { document, at } = await workspace.marked('Project/Child.x', text);
        for (const type of ['Int', 'String', 'Int']) {
            await workspace.replace(root, fixture('Project.x').replace('Int item = 1;', `${type} item = ${type === 'Int' ? '1' : '"overlay"'};`));
            const item = (await workspace.completion(document, at)).find(value => label(value) === 'item');
            assert.ok(item);
            assert.match(item.detail ?? '', new RegExp(type));
        }
        assert.strictEqual(await fs.readFile(root.uri.fsPath, 'utf8'), fixture('Project.x'));
    });

    playbook('X25', 'named unsaved virtual member joins the module', async workspace => {
        const root = await workspace.project();
        const uri = workspace.uri('Project/pkg/Added.x');
        try {
            await client().sendNotification('textDocument/didOpen', { textDocument: {
                uri: uri.toString(), languageId: 'xtc', version: 1, text: 'class Added extends Base<String> {}'
            } });
            await symbols(root);
            // Fixture filesystem notifications can rebuild the module between these two
            // requests. Reprepare each time: hierarchy items deliberately expire on rebuild.
            await eventually(async () => {
                const [base] = await hierarchy(root, position(root, 'Base<Element>'));
                return base ? edges(base, 'subtypes') : [];
            }, children => children.some(item => item.name === 'Added'), 'Unsaved member in current hierarchy');
            assert.strictEqual(await fs.stat(uri.fsPath).catch(() => undefined), undefined);
            await noErrors(uri);
        } finally {
            await client().sendNotification('textDocument/didClose', { textDocument: { uri: uri.toString() } });
        }
    }, ['Named nonexistent file is supplied through the LSP client; an ordinary Untitled editor is not equivalent']);

    playbook('X26', 'discarded member overlay restores disk semantics', async workspace => {
        const root = await workspace.project();
        const child = await workspace.open('Project/Child.x');
        await workspace.replace(child, fixture('Project/Child.x').replace('Base<String>', 'Missing'));
        await diagnostics(child.uri, values => values.length > 0, 'Broken member');
        await workspace.discard(child);
        await symbols(root);
        await noErrors(child.uri);
        const reopened = await workspace.open('Project/Child.x');
        assert.strictEqual(reopened.getText(), fixture('Project/Child.x'));
        assert.strictEqual((await targets(reopened, 'Definition', position(reopened, 'Base<String>'))).length, 1);
    });

    playbook('X27', 'real file-watcher creation and deletion refresh the module', async workspace => {
        await workspace.project();
        await workspace.write('Project/Bad.x', 'class Bad extends Missing {}');
        const uri = workspace.uri('Project/Bad.x');
        await diagnostics(uri, values => values.length > 0, 'Closed created member diagnostic');
        await fs.unlink(uri.fsPath);
        await noErrors(uri);
    });

    playbook('X28', 'obsolete type hierarchy items cannot resolve', async workspace => {
        await workspace.project();
        const child = await workspace.open('Project/Child.x');
        const [old] = await hierarchy(child, position(child, 'Child'));
        assert.ok(old);
        await workspace.replace(child, 'class Child {}');
        await noErrors(child.uri);
        assert.deepStrictEqual(await edges(old, 'supertypes'), []);
        const [fresh] = await hierarchy(child, position(child, 'Child'));
        assert.ok(!(await edges(fresh, 'supertypes')).some(item => item.name.startsWith('Base')));
        await workspace.replace(child, fixture('Project/Child.x'));
        const [restored] = await hierarchy(child, position(child, 'Child'));
        assert.ok((await edges(restored, 'supertypes')).some(item => item.name.startsWith('Base')));
    });

    playbook('X29', 'rapid edits and cursor requests converge on current text', async workspace => {
        const { document, at } = await workspace.editing('box.choose(1, §);');
        const initial = document.getText();
        const pending = [];
        for (let index = 0; index < 10; index++) {
            await workspace.replace(document, initial.replace('choose(1,', index % 2 ? 'choose(1,' : 'choose("x",'), false);
            pending.push(workspace.signature(document, at));
        }
        await Promise.all(pending);
        const final = await workspace.editing('box.choose(1, 2); §');
        await noErrors(final.document.uri);
        const signature = await workspace.signature(final.document, position(final.document, 'choose(1, 2)', 10));
        assert.match(signature?.signatures[0]?.label ?? '', /Int/);
        const root = await workspace.project();
        const child = await workspace.open('Project/Child.x');
        for (let index = 0; index < 6; index++) {
            await workspace.replace(root, fixture('Project.x').replace('Int item = 1;', `Int item = ${index};`), false);
        }
        await symbols(child);
        await noErrors(child.uri);
        assert.strictEqual((await targets(child, 'Definition', position(child, 'echo("member")'))).length, 1);
    });

    playbook('X30', 'cancellation, close, reopen and server restart recover', async workspace => {
        const { document, at } = await workspace.editing('box.it§;');
        const token = new vscode.CancellationTokenSource();
        const pending = client().sendRequest('textDocument/completion', { textDocument: { uri: document.uri.toString() }, position: at }, token.token)
            .then(() => undefined, error => { assert.ok([-32800, -32801].includes(error.code), String(error)); });
        token.cancel();
        await pending;
        token.dispose();
        await workspace.discard(document);
        const reopened = await workspace.open('Editing.x');
        await noErrors(reopened.uri);
        const previous = getClient();
        await vscode.commands.executeCommand('xtc.restartServer');
        await eventually(async () => getClient(), value => !!value?.initializeResult && value !== previous, 'Restarted language client');
        await symbols(reopened);
        await noErrors(reopened.uri);
        const current = await workspace.editing('box.it§;');
        assert.ok((await workspace.completion(current.document, current.at)).some(item => label(item) === 'item'));
    }, ['Dismissing the actual popup and keyboard responsiveness']);

    playbook('X31', 'unsupported compiler capabilities are not advertised', async workspace => {
        const document = await workspace.open('Navigation.x');
        const capabilities = client().initializeResult!.capabilities;
        for (const capability of ['documentFormattingProvider', 'documentRangeFormattingProvider', 'codeActionProvider', 'codeLensProvider']) {
            assert.ok(!capabilities[capability as keyof typeof capabilities], capability);
        }
        assert.strictEqual((await vscode.commands.executeCommand<vscode.TextEdit[]>('vscode.executeFormatDocumentProvider', document.uri,
            { tabSize: 4, insertSpaces: true }))?.length ?? 0, 0);
    }, ['Editor-native formatting/snippets are outside compiler capability assertions']);

    playbook('X32', 'unsupported cursor contexts do not invent completions', async workspace => {
        for (const body of ['box.pa§ir("x", "y");', 'new Box<String>(§);',
            'function String() fn = () -> "x"; fn(§);', 'box.pair(§, "x");']) {
            const { document, at } = await workspace.editing(body);
            assert.strictEqual((await workspace.completion(document, at)).length, 0, body);
        }
    });
}
