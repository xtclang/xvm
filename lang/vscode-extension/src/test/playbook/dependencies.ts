import * as assert from 'node:assert';
import * as fs from 'node:fs/promises';
import * as vscode from 'vscode';
import { client, diagnosticCode, diagnostics, eventually, fixture, noErrors, playbook, position, symbols, targets, Workspace } from './support';

const stringLibrary = 'module Library { static String value()="text"; }';
const brokenLibrary = 'module Library { MissingType broken; }';

async function blocked(workspace: Workspace): Promise<void> {
    await diagnostics(workspace.uri('Consumer.x'), values => values.some(item => diagnosticCode(item) === 'DEPENDENCY-FAILED'), 'Blocked consumer');
}

async function linked(document: vscode.TextDocument, workspace: Workspace): Promise<void> {
    await symbols(document);
    await noErrors(document.uri);
    const result = await targets(document, 'Definition', position(document, 'lib.value', 4));
    assert.strictEqual(result.length, 1);
    assert.strictEqual(result[0].uri.toString(), workspace.uri('Library.x').toString());
}

export function dependencyCases(): void {
    playbook('X45', 'source dependencies compile and navigate without opening the library', async workspace => {
        const consumer = await workspace.dependencies();
        assert.ok(!vscode.window.visibleTextEditors.some(editor => editor.document.uri.toString() === workspace.uri('Library.x').toString()));
        await linked(consumer, workspace);
    });

    playbook('X46', 'unsaved library type changes recompile unchanged consumers', async workspace => {
        const consumer = await workspace.dependencies();
        const version = consumer.version;
        const library = await workspace.open('Library.x');
        await workspace.replace(library, stringLibrary);
        const result = await diagnostics(consumer.uri, values => values.length > 0, 'Consumer type mismatch');
        assert.ok(result.every(item => diagnosticCode(item) !== 'DEPENDENCY-FAILED'));
        assert.strictEqual(consumer.version, version);
        assert.strictEqual(await fs.readFile(library.uri.fsPath, 'utf8'), fixture('Library.x'));
    });

    playbook('X47', 'dependency failure owns diagnostics and removes stale navigation', async workspace => {
        const consumer = await workspace.dependencies();
        const library = await workspace.open('Library.x');
        await workspace.replace(library, brokenLibrary);
        await diagnostics(library.uri, values => values.some(item => diagnosticCode(item).startsWith('COMPILER-')), 'Library compiler diagnostic');
        await blocked(workspace);
        assert.deepStrictEqual(await targets(consumer, 'Definition', position(consumer, 'lib.value', 4)), []);
        await workspace.replace(library, fixture('Library.x'));
        await noErrors(library.uri);
        await linked(consumer, workspace);
    });

    playbook('X48', 'discarding a dependency overlay restores the disk artifact', async workspace => {
        const consumer = await workspace.dependencies();
        const library = await workspace.open('Library.x');
        await workspace.replace(library, stringLibrary);
        await diagnostics(consumer.uri, values => values.length > 0, 'Consumer type mismatch');
        await workspace.discard(library);
        await linked(consumer, workspace);
        assert.strictEqual((await workspace.open('Library.x')).getText(), fixture('Library.x'));
    });

    playbook('X49', 'watched dependency deletion and restoration propagate', async workspace => {
        const consumer = await workspace.dependencies();
        await fs.unlink(workspace.uri('Library.x').fsPath);
        await diagnostics(workspace.uri('Library.x'), values => values.some(item => diagnosticCode(item) === 'SOURCE-UNAVAILABLE'), 'Missing dependency source');
        await blocked(workspace);
        await workspace.write('Library.x');
        await linked(consumer, workspace);
        await noErrors(workspace.uri('Library.x'));
    });

    playbook('X50', 'unsaved and saved dependency members invalidate and recover consumers', async workspace => {
        const consumer = await workspace.dependencies();
        const uri = workspace.uri('Library/Extra.x');
        const text = 'class Extra { MissingType broken; }';
        try {
            await client().sendNotification('textDocument/didOpen', { textDocument: { uri: uri.toString(), languageId: 'xtc', version: 1, text } });
            await diagnostics(uri, values => values.length > 0, 'Virtual member diagnostic');
            await blocked(workspace);
        } finally {
            await client().sendNotification('textDocument/didClose', { textDocument: { uri: uri.toString() } });
        }
        await linked(consumer, workspace);
        await noErrors(uri);
        await workspace.write('Library/Extra.x', text);
        await diagnostics(uri, values => values.length > 0, 'Saved member diagnostic');
        await blocked(workspace);
        await fs.unlink(uri.fsPath);
        await linked(consumer, workspace);
        await noErrors(uri);
    }, ['Named unsaved member uses LSP didOpen/didClose; filesystem half uses the real VS Code watcher']);

    playbook('X51', 'rapid dependency changes converge without damaging unrelated sessions', async workspace => {
        const consumer = await workspace.dependencies();
        const unrelated = await workspace.open('Navigation.x');
        const library = await workspace.open('Library.x');
        const queries = [];
        for (let index = 0; index < 10; index++) {
            await workspace.replace(library, index % 2 ? fixture('Library.x') : brokenLibrary, false);
            queries.push(targets(consumer, 'Definition', position(consumer, 'lib.value', 4)));
        }
        await Promise.all(queries);
        await linked(consumer, workspace);
        await noErrors(unrelated.uri);
        assert.ok((await symbols(unrelated)).length);
    });

    playbook('X52', 'transitive source changes propagate through the dependency graph', async workspace => {
        const bridgeText = 'module Bridge { package lib import Library; static Int value()=lib.value(); }';
        await workspace.write('Library.x');
        await workspace.write('Bridge.x', bridgeText);
        await workspace.write('Consumer.x', fixture('Consumer.x').replace('import Library', 'import Bridge'));
        await workspace.configure([
            { name: 'Library', uri: workspace.uri('Library.x').toString() },
            { name: 'Bridge', uri: workspace.uri('Bridge.x').toString(), dependencies: ['Library'] },
            { name: 'Consumer', uri: workspace.uri('Consumer.x').toString(), dependencies: ['Bridge'] }
        ]);
        const consumer = await workspace.open('Consumer.x');
        await noErrors(consumer.uri);
        const library = await workspace.open('Library.x');
        await workspace.replace(library, stringLibrary);
        await diagnostics(workspace.uri('Bridge.x'), values => values.length > 0, 'Transitive type mismatch');
        await blocked(workspace);
        await workspace.replace(library, brokenLibrary);
        await blocked(workspace);
        await workspace.replace(library, fixture('Library.x'));
        await noErrors(consumer.uri);
        const bridge = await workspace.open('Bridge.x');
        await workspace.replace(bridge, 'module Bridge { MissingType broken; }');
        await blocked(workspace);
        await workspace.replace(bridge, bridgeText);
        await noErrors(consumer.uri);
        assert.strictEqual((await targets(consumer, 'Definition', position(consumer, 'lib.value', 4)))[0].uri.toString(), bridge.uri.toString());
    });
}

export function configurationCases(): void {
    playbook('CFG1', 'removing and restoring source settings replaces the graph', async workspace => {
        const consumer = await workspace.dependencies();
        await workspace.configure([]);
        await diagnostics(consumer.uri, values => values.length > 0, 'Removed dependency graph');
        await workspace.configure([
            { name: 'Library', uri: workspace.uri('Library.x').toString() },
            { name: 'Consumer', uri: workspace.uri('Consumer.x').toString(), dependencies: ['Library'] }
        ]);
        await linked(consumer, workspace);
    });

    playbook('CFG2', 'invalid cyclic settings preserve the last valid graph', async workspace => {
        const consumer = await workspace.dependencies();
        // Observe the error notification as well as unchanged navigation: otherwise a query
        // could race ahead of processing the rejected configuration.
        let rejected = false;
        const registration = client().onNotification('window/showMessage', (message: { message: string }) => {
            if (/cycl|invalid|reject/i.test(message.message)) { rejected = true; }
        });
        try {
            await workspace.configure([
                { name: 'Library', uri: workspace.uri('Library.x').toString(), dependencies: ['Consumer'] },
                { name: 'Consumer', uri: workspace.uri('Consumer.x').toString(), dependencies: ['Library'] }
            ]);
            await eventually(async () => rejected, Boolean, 'Invalid configuration rejected');
            await linked(consumer, workspace);
        } finally { registration.dispose(); }
    });

    playbook('CFG3', 'unchanged source settings preserve current semantics', async workspace => {
        const consumer = await workspace.dependencies();
        const version = consumer.version;
        await client().sendNotification('workspace/didChangeConfiguration', { settings: null });
        await linked(consumer, workspace);
        assert.strictEqual(consumer.version, version);
    });
}
