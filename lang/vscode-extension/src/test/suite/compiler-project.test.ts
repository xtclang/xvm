import * as assert from 'node:assert';
import * as fs from 'node:fs/promises';
import * as path from 'node:path';
import * as vscode from 'vscode';
import { getClient } from '../../lsp-client';

const LIBRARY = 'module Library { static Int value()=1; class Item {} }';
const CONSUMER = 'module Consumer { package lib import Library; Int value=10; ' +
    'private Int pick(Int input)=input; Int run() { Int local=pick(input=lib.value()); return local+value; } }';

async function eventually(check: () => Promise<boolean> | boolean, description: string): Promise<void> {
    const end = Date.now() + 30_000;
    while (Date.now() < end) {
        if (await check()) { return; }
        await new Promise(resolve => setTimeout(resolve, 100));
    }
    assert.fail(`Timed out: ${description}`);
}

async function replace(document: vscode.TextDocument, text: string): Promise<void> {
    const edit = new vscode.WorkspaceEdit();
    edit.replace(document.uri, new vscode.Range(document.positionAt(0), document.positionAt(document.getText().length)), text);
    assert.ok(await vscode.workspace.applyEdit(edit));
}

suite('Compiler editor acceptance', function () {
    this.timeout(180_000);

    suiteSetup(async function () {
        await vscode.extensions.getExtension('xtclang.xtc-language')!.activate();
        await eventually(() => !!getClient()?.initializeResult, 'language server initialization');
        // These cases require the opt-in compiler build; the shipped Tree-sitter suite still runs.
        if (!getClient()!.initializeResult!.capabilities.typeHierarchyProvider) { this.skip(); }
    });

    test('settings, unsaved dependency rebuilding, semantic rename and reopen work through VS Code', async () => {
        const workspace = vscode.workspace.workspaceFolders![0].uri.fsPath;
        const directory = await fs.mkdtemp(path.join(workspace, 'compiler-acceptance-'));
        const library = vscode.Uri.file(path.join(directory, 'Library.x'));
        const consumer = vscode.Uri.file(path.join(directory, 'Consumer.x'));
        const config = vscode.workspace.getConfiguration('xtc.compiler');
        const previous = config.inspect('sourceModules')?.workspaceValue;
        const settingsFile = path.join(workspace, '.vscode', 'settings.json');
        const previousSettings = await fs.readFile(settingsFile).catch((error: NodeJS.ErrnoException) => {
            if (error.code === 'ENOENT') { return undefined; }
            throw error;
        });
        try {
            await fs.writeFile(library.fsPath, LIBRARY);
            await fs.writeFile(consumer.fsPath, CONSUMER);
            await config.update('sourceModules', [], vscode.ConfigurationTarget.Workspace);
            const document = await vscode.workspace.openTextDocument(consumer);
            await vscode.window.showTextDocument(document);
            await eventually(() => vscode.languages.getDiagnostics(consumer).length > 0, 'missing dependency diagnostics');
            await config.update('sourceModules', [
                { name: 'Library', uri: library.toString() },
                { name: 'Consumer', uri: consumer.toString(), dependencies: ['Library'] }
            ], vscode.ConfigurationTarget.Workspace);
            await eventually(() => vscode.languages.getDiagnostics(consumer).length === 0, 'configuration enables source compilation');
            const originalVersion = document.version;
            const dependency = await vscode.workspace.openTextDocument(library);
            await vscode.window.showTextDocument(dependency);
            await replace(dependency, LIBRARY.replace('Int value()=1', 'String value()="changed"'));
            await eventually(() => vscode.languages.getDiagnostics(consumer).length > 0, 'unchanged consumer reports new dependency type');
            assert.strictEqual(document.version, originalVersion);
            await replace(dependency, LIBRARY);
            await eventually(() => vscode.languages.getDiagnostics(consumer).length === 0, 'dependency correction clears consumer errors');
            await vscode.window.showTextDocument(document);

            const definition = await vscode.commands.executeCommand<vscode.Location[]>('vscode.executeDefinitionProvider', consumer,
                document.positionAt(document.getText().indexOf('lib.value') + 4));
            assert.ok(definition?.some(location => location.uri.toString() === library.toString()), 'definition reaches dependency source');
            const signature = await vscode.commands.executeCommand<vscode.SignatureHelp>('vscode.executeSignatureHelpProvider', consumer,
                document.positionAt(document.getText().lastIndexOf('pick(') + 5));
            assert.ok(signature?.signatures.some(item => item.label.includes('pick')), 'selected signature reaches the editor');
            const incomplete = 'module Consumer { void run(String text) { text.si } }';
            await replace(document, incomplete);
            const completion = await vscode.commands.executeCommand<vscode.CompletionList>('vscode.executeCompletionItemProvider', consumer,
                document.positionAt(incomplete.indexOf('text.si') + 'text.si'.length));
            assert.ok(completion?.items.some(item => (typeof item.label === 'string' ? item.label : item.label.label) === 'size'),
                'compiler completion works at an incomplete member prefix');
            await replace(document, CONSUMER);
            const rename = await vscode.commands.executeCommand<vscode.WorkspaceEdit>('vscode.executeDocumentRenameProvider', consumer,
                document.positionAt(document.getText().lastIndexOf('input')), 'number');
            assert.ok(rename && await vscode.workspace.applyEdit(rename), 'versioned rename applies through the editor');
            assert.ok(document.getText().includes('Int number)=number'));
            assert.ok(document.getText().includes('pick(number=lib.value())'));
            const rejected = await getClient()!.sendRequest('textDocument/rename', {
                textDocument: { uri: consumer.toString() }, position: document.positionAt(document.getText().indexOf('local')), newName: 'value'
            });
            assert.strictEqual(rejected, null, 'silent property capture must be rejected');

            const prefix = document.getText() + '\n';
            const timings: number[] = [];
            // Repeated editor notifications and queries, including real invalid/valid transitions.
            for (let cycle = 0; cycle < 40; cycle++) {
                const start = Date.now();
                await replace(document, prefix.replace('Int local=', 'MissingType local=') + `// ${cycle}`);
                await eventually(() => vscode.languages.getDiagnostics(consumer).length > 0, `error cycle ${cycle}`);
                await replace(document, prefix + `// ${cycle}`);
                await eventually(() => vscode.languages.getDiagnostics(consumer).length === 0, `recovery cycle ${cycle}`);
                const hovers = await vscode.commands.executeCommand<vscode.Hover[]>('vscode.executeHoverProvider', consumer,
                    document.positionAt(document.getText().indexOf('local')));
                assert.ok(hovers?.length, 'hover survives repeated recompilation');
                timings.push(Date.now() - start);
            }
            timings.sort((a, b) => a - b);
            console.log(`Compiler editor workload: 40 error/recovery cycles, p50=${timings[20]}ms p95=${timings[38]}ms`);
            await replace(document, CONSUMER);
            await document.save();
            await dependency.save();
            await vscode.commands.executeCommand('workbench.action.closeAllEditors');
            const reopened = await vscode.workspace.openTextDocument(consumer);
            await vscode.window.showTextDocument(reopened);
            await eventually(async () => !!(await vscode.commands.executeCommand<vscode.Hover[]>('vscode.executeHoverProvider', consumer,
                reopened.positionAt(CONSUMER.indexOf('local'))))?.length, 'hover after reopen');
            assert.strictEqual(vscode.languages.getDiagnostics(consumer).length, 0);
        } finally {
            await config.update('sourceModules', previous, vscode.ConfigurationTarget.Workspace);
            await vscode.commands.executeCommand('workbench.action.closeAllEditors');
            await fs.rm(directory, { recursive: true, force: true });
            if (previousSettings) {
                await fs.writeFile(settingsFile, previousSettings);
            } else {
                await fs.rm(settingsFile, { force: true });
            }
        }
    });
});
