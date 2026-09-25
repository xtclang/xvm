import * as assert from 'node:assert';
import * as fs from 'node:fs/promises';
import * as vscode from 'vscode';
import { catalog, editScenario, scenarioOffset, scenarioRegex, shared } from './shared';
import { client, diagnosticCode, diagnostics, eventually, fixture, noErrors, playbook, position, symbols, targets, Workspace } from './support';

const { stringLibrary, brokenLibrary } = catalog.common.dependency;

async function blocked(workspace: Workspace): Promise<void> {
    await diagnostics(workspace.uri(catalog.common.dependency.consumer), values => values.some(item => diagnosticCode(item) === catalog.common.dependency.blockedCode), 'Blocked consumer');
}

async function linked(document: vscode.TextDocument, workspace: Workspace): Promise<void> {
    await symbols(document);
    await noErrors(document.uri);
    const location = shared.dependencyNavigation.location;
    const result = await targets(document, 'Definition', document.positionAt(scenarioOffset(document.getText(), location.cursor)));
    assert.strictEqual(result.length, 1);
    assert.strictEqual(result[0].uri.toString(), workspace.uri(location.targetFile).toString());
}

export function dependencyCases(): void {
    playbook('X45', async (workspace, data) => {
        const consumer = await workspace.dependencies();
        const location = shared.dependencyNavigation.location;
        assert.ok(!vscode.window.visibleTextEditors.some(editor => editor.document.uri.toString() === workspace.uri(location.targetFile).toString()));
        await linked(consumer, workspace);
        const result = await targets(consumer, 'Definition', consumer.positionAt(scenarioOffset(consumer.getText(), location.cursor)));
        const target = await vscode.workspace.openTextDocument(result[0].uri);
        assert.strictEqual(target.offsetAt(result[0].range.start), scenarioOffset(target.getText(), location.target));
    });

    playbook('X46', async (workspace, data) => {
        const scenario = shared.dependencyEdit;
        const consumer = await workspace.dependencies();
        assert.strictEqual(consumer.uri.toString(), workspace.uri(scenario.consumer).toString());
        const version = consumer.version;
        const library = await workspace.open(scenario.file);
        await workspace.replace(library, editScenario(fixture(scenario.file), scenario.edit));
        const result = await diagnostics(consumer.uri, values => values.length > 0, 'Consumer type mismatch');
        assert.ok(result.every(item => diagnosticCode(item) !== data.diagnosticCode));
        assert.strictEqual(consumer.version, version);
        assert.strictEqual(await fs.readFile(library.uri.fsPath, 'utf8'), fixture(scenario.file));
        await workspace.replace(library, fixture(scenario.file));
        await noErrors(consumer.uri);
    });

    playbook('X47', async (workspace, data) => {
        const consumer = await workspace.dependencies();
        const library = await workspace.open(data.file);
        await workspace.replace(library, brokenLibrary);
        await diagnostics(library.uri, values => values.some(item => diagnosticCode(item).startsWith(data.compilerCodePrefix)), 'Library compiler diagnostic');
        await blocked(workspace);
        assert.deepStrictEqual(await targets(consumer, 'Definition', position(consumer, data.anchor, data.offset)), []);
        await workspace.replace(library, fixture(data.file));
        await noErrors(library.uri);
        await linked(consumer, workspace);
    });

    playbook('X48', async (workspace, data) => {
        const consumer = await workspace.dependencies();
        const library = await workspace.open(data.file);
        await workspace.replace(library, stringLibrary);
        await diagnostics(consumer.uri, values => values.length > 0, 'Consumer type mismatch');
        await workspace.discard(library);
        await linked(consumer, workspace);
        assert.strictEqual((await workspace.open(data.file)).getText(), fixture(data.file));
    });

    playbook('X49', async (workspace, data) => {
        const consumer = await workspace.dependencies();
        await fs.unlink(workspace.uri(data.file).fsPath);
        await diagnostics(workspace.uri(data.file), values => values.some(item => diagnosticCode(item) === data.diagnosticCode), 'Missing dependency source');
        await blocked(workspace);
        await workspace.write(data.file);
        await linked(consumer, workspace);
        await noErrors(workspace.uri(data.file));
    });

    playbook('X50', async (workspace, data) => {
        const consumer = await workspace.dependencies();
        const uri = workspace.uri(data.file);
        const text = data.text;
        try {
            await client().sendNotification('textDocument/didOpen', { textDocument: { uri: uri.toString(), languageId: 'xtc', version: data.version, text } });
            await diagnostics(uri, values => values.length > 0, 'Virtual member diagnostic');
            await blocked(workspace);
        } finally {
            await client().sendNotification('textDocument/didClose', { textDocument: { uri: uri.toString() } });
        }
        await linked(consumer, workspace);
        await noErrors(uri);
        await workspace.write(data.file, text);
        await diagnostics(uri, values => values.length > 0, 'Saved member diagnostic');
        await blocked(workspace);
        await fs.unlink(uri.fsPath);
        await linked(consumer, workspace);
        await noErrors(uri);
    });

    playbook('X51', async (workspace, data) => {
        const consumer = await workspace.dependencies();
        const unrelated = await workspace.open(data.unrelatedFile);
        const library = await workspace.open(data.libraryFile);
        const queries = [];
        for (let index = 0; index < 10; index++) {
            await workspace.replace(library, index % 2 ? fixture(data.libraryFile) : brokenLibrary, false);
            queries.push(targets(consumer, 'Definition', position(consumer, data.anchor, data.offset)));
        }
        await Promise.all(queries);
        await linked(consumer, workspace);
        await noErrors(unrelated.uri);
        assert.ok((await symbols(unrelated)).length);
    });

    playbook('X52', async (workspace, data) => {
        const bridgeText = data.bridgeText;
        await workspace.write(data.libraryFile);
        await workspace.write(data.bridgeFile, bridgeText);
        await workspace.write(data.consumerFile, fixture(data.consumerFile).replace(data.replaceFrom, data.bridgeImport));
        await workspace.configure(data.sourceModules.map(module => ({ ...module, uri: workspace.uri(module.uri).toString() })));
        const consumer = await workspace.open(data.consumerFile);
        await noErrors(consumer.uri);
        const library = await workspace.open(data.libraryFile);
        await workspace.replace(library, stringLibrary);
        await diagnostics(workspace.uri(data.bridgeFile), values => values.length > 0, 'Transitive type mismatch');
        await blocked(workspace);
        await workspace.replace(library, brokenLibrary);
        await blocked(workspace);
        await workspace.replace(library, fixture(data.libraryFile));
        await noErrors(consumer.uri);
        const bridge = await workspace.open(data.bridgeFile);
        await workspace.replace(bridge, data.brokenBridge);
        await blocked(workspace);
        await workspace.replace(bridge, bridgeText);
        await noErrors(consumer.uri);
        assert.strictEqual((await targets(consumer, 'Definition', position(consumer, data.anchor, data.offset)))[0].uri.toString(), bridge.uri.toString());
    });
}

export function configurationCases(): void {
    playbook('CFG1', async (workspace, data) => {
        const consumer = await workspace.dependencies();
        assert.strictEqual(consumer.uri.toString(), workspace.uri(shared.configuration.consumer).toString());
        await workspace.configure([]);
        await diagnostics(consumer.uri, values => values.length > 0, 'Removed dependency graph');
        await workspace.configureSharedGraph();
        await linked(consumer, workspace);
    });

    playbook('CFG2', async (workspace, data) => {
        const consumer = await workspace.dependencies();
        // Observe the error notification as well as unchanged navigation: otherwise a query
        // could race ahead of processing the rejected configuration.
        let rejected = false;
        const registration = client().onNotification('window/showMessage', (message: { message: string }) => {
            if (scenarioRegex(data.pattern).test(message.message)) { rejected = true; }
        });
        try {
            await workspace.configure(data.sourceModules.map(module => ({ ...module, uri: workspace.uri(module.uri).toString() })));
            await eventually(async () => rejected, Boolean, 'Invalid configuration rejected');
            await linked(consumer, workspace);
        } finally { registration.dispose(); }
    });

    playbook('CFG3', async (workspace, data) => {
        const consumer = await workspace.dependencies();
        const version = consumer.version;
        await client().sendNotification('workspace/didChangeConfiguration', { settings: null });
        await linked(consumer, workspace);
        assert.strictEqual(consumer.version, version);
    });
}
