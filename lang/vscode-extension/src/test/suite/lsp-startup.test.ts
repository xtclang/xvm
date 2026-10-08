// LSP startup smoke test. Opens a .x fixture, then issues a hover request
// against a known symbol and waits for the LSP server to respond. Catches
// a class of regressions where the extension activates cleanly but the
// LSP server fails to start (wrong JAR path, wrong Java, classpath issue,
// adapter selection broken, etc.) — none of which the per-surface tests
// above would notice, because they don't exercise actual LSP traffic.
//
// Starting the server JVM and selected adapter takes a few seconds; reads have a 30s deadline.

import * as assert from 'node:assert';
import * as vscode from 'vscode';
import { connectionSettingsChanged, getClient } from '../../lsp-client';
import { waitFor } from '../wait';

const PUBLISHER_AND_NAME = 'xtclang.xtc-language';
const STARTUP_TIMEOUT_MS = 30_000;

async function waitForHover(uri: vscode.Uri, position: vscode.Position): Promise<vscode.Hover[]> {
    // The built-in `vscode.executeHoverProvider` command returns hovers from
    // every registered provider for the given position. Before the LSP client
    // has finished initializing, the array is empty; once the server is up
    // and has indexed the document, our provider contributes at least one
    // hover entry. Polling on this signal is more reliable than scraping the
    // output channel for "Backend: TreeSitter" because it actually verifies
    // end-to-end LSP RPC, not just startup logging.
    return waitFor(async () => (await vscode.commands.executeCommand<vscode.Hover[]>(
        'vscode.executeHoverProvider', uri, position)) ?? [],
    hovers => hovers.length > 0, 'Language server responds to hover', STARTUP_TIMEOUT_MS);
}

suite('LSP startup', function () {
    // Mocha-level timeout: we already cap our internal poll at 30s, so 45s
    // here leaves comfortable headroom for the suiteSetup overhead.
    this.timeout(45_000);

    suiteSetup(async () => {
        const ext = vscode.extensions.getExtension(PUBLISHER_AND_NAME);
        assert.ok(ext, `extension ${PUBLISHER_AND_NAME} not found`);
        await ext.activate();
    });

    test('LSP server responds to a hover request on hello.x', async () => {
        const fixture = vscode.Uri.joinPath(vscode.workspace.workspaceFolders![0].uri, 'hello.x');
        const doc = await vscode.workspace.openTextDocument(fixture);
        await vscode.window.showTextDocument(doc);

        // Aim the hover at the `console` identifier in `console.print(...)`.
        // Choosing a non-keyword token gives the LSP a meaningful symbol to
        // resolve; an empty hover here means the server has not loaded the
        // file (or is not responding) within STARTUP_TIMEOUT_MS.
        const text = doc.getText();
        const idx = text.indexOf('console.print');
        assert.ok(idx > 0, 'hello.x fixture is missing the `console.print` call we hover over');
        const position = doc.positionAt(idx);

        const hovers = await waitForHover(doc.uri, position);
        assert.ok(
            hovers.length > 0,
            `LSP server did not respond to hover within ${STARTUP_TIMEOUT_MS} ms. ` +
                'This usually means the LSP server JVM failed to start ' +
                '(missing JAR, wrong Java version, tree-sitter native lib not loaded). ' +
                'Check the "Ecstasy Language Server" output channel from a manual run.',
        );
    });

    test('an unexpected server exit restarts the existing connection and preserves unsaved text', async () => {
        const fixture = vscode.Uri.joinPath(vscode.workspace.workspaceFolders![0].uri, 'hello.x');
        const document = await vscode.workspace.openTextDocument(fixture);
        await vscode.window.showTextDocument(document);
        const original = document.getText();
        const offset = original.indexOf('console.print');
        assert.ok(offset > 0, 'hello.x contains console.print');
        const at = document.positionAt(offset);
        await waitForHover(fixture, at);
        const connection = getClient();
        assert.ok(connection);
        assert.ok(connection.isRunning());
        const initialized = connection.initializeResult;
        const before = await connection.sendRequest<{ pid: number }>('xtc/languageServiceStatus');
        assert.ok(Number.isSafeInteger(before.pid) && before.pid > 0, 'Only terminate this isolated server');
        try {
            const edit = new vscode.WorkspaceEdit();
            edit.insert(fixture, document.positionAt(original.length), '\n// unsaved restart control\n');
            assert.ok(await vscode.workspace.applyEdit(edit));
            const unsaved = document.getText();
            process.kill(before.pid, 'SIGTERM');
            await waitFor(async () => connection.isRunning() && connection.initializeResult !== initialized,
                Boolean, 'Automatic restart completes initialization');
            assert.strictEqual(getClient(), connection, 'The installed client owns its automatic restart');
            const after = await connection.sendRequest<{ pid: number }>('xtc/languageServiceStatus');
            assert.notStrictEqual(after.pid, before.pid);
            assert.strictEqual(document.getText(), unsaved);
            assert.ok((await waitForHover(fixture, at)).length > 0, 'The restarted server analyzes the reopened buffer');
        } finally {
            const restore = new vscode.WorkspaceEdit();
            restore.replace(fixture, new vscode.Range(document.positionAt(0), document.positionAt(document.getText().length)), original);
            assert.ok(await vscode.workspace.applyEdit(restore));
        }
    });

    test('switching adapters restarts the server and reopens unsaved buffers in both backends', async function () {
        this.timeout(150_000);
        const fixture = vscode.Uri.joinPath(vscode.workspace.workspaceFolders![0].uri, 'hello.x');
        const document = await vscode.workspace.openTextDocument(fixture);
        await vscode.window.showTextDocument(document);
        const original = document.getText();
        const settings = vscode.workspace.getConfiguration('xtc');
        const saved = settings.inspect('languageService.adapter')?.workspaceValue;
        await waitFor(async () => getClient()?.isRunning() ?? false, Boolean, 'Initial server running');
        const insertion = original.indexOf('@Inject');
        assert.ok(insertion >= 0, 'hello.x contains an injected property');
        const edit = new vscode.WorkspaceEdit();
        edit.insert(fixture, document.positionAt(insertion), 'Int adapterSwitchProof = 42;\n    ');
        assert.ok(await vscode.workspace.applyEdit(edit));
        const unsaved = document.getText();
        const names = (symbols: vscode.DocumentSymbol[]): string[] => symbols.flatMap(symbol => [symbol.name, ...names(symbol.children ?? [])]);
        try {
            for (const [choice, expected] of [['treesitter', 'TreeSitter'], ['compiler', 'XDK']]) {
                const previous = getClient();
                const before = await previous!.sendRequest<{ pid: number }>('xtc/languageServiceStatus');
                await vscode.commands.executeCommand('xtc.selectLanguageAdapter', choice);
                const current = await waitFor(async () => getClient(), value => !!value && value !== previous && value.isRunning(), `Restart into ${choice}`);
                const status = await current!.sendRequest<{ adapter: string; pid: number }>('xtc/languageServiceStatus');
                assert.strictEqual(status.adapter, expected);
                assert.notStrictEqual(status.pid, before.pid);
                assert.strictEqual(document.getText(), unsaved);
                const symbols = await waitFor(async () => (await vscode.commands.executeCommand<vscode.DocumentSymbol[]>('vscode.executeDocumentSymbolProvider', fixture)) ?? [],
                    value => names(value).includes('adapterSwitchProof'), `Unsaved symbol available in ${choice}`);
                assert.ok(names(symbols).includes('adapterSwitchProof'));
                await vscode.commands.executeCommand('xtc.selectLanguageAdapter', choice);
                assert.strictEqual(getClient(), current, 'Selecting the saved adapter again leaves the connection intact');
            }
            const current = getClient();
            await assert.rejects(async () => vscode.commands.executeCommand('xtc.selectLanguageAdapter', 'invalid'), /adapter/);
            assert.strictEqual(getClient(), current, 'An invalid selection leaves the working connection intact');
        } finally {
            const restore = new vscode.WorkspaceEdit();
            restore.replace(fixture, new vscode.Range(document.positionAt(0), document.positionAt(document.getText().length)), original);
            assert.ok(await vscode.workspace.applyEdit(restore));
            await settings.update('languageService.adapter', saved, vscode.ConfigurationTarget.Workspace);
            await waitFor(async () => !connectionSettingsChanged() && (getClient()?.isRunning() ?? false), Boolean, 'Restore original adapter');
        }
    });
});
