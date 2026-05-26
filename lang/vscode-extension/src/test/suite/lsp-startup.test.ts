// LSP startup smoke test. Opens a .x fixture, then issues a hover request
// against a known symbol and waits for the LSP server to respond. Catches
// a class of regressions where the extension activates cleanly but the
// LSP server fails to start (wrong JAR path, wrong Java, classpath issue,
// adapter selection broken, etc.) — none of which the per-surface tests
// above would notice, because they don't exercise actual LSP traffic.
//
// Slow by design: starting the LSP server JVM + tree-sitter native lib
// load takes a few seconds even on a warm machine; we poll up to 30s.

import * as assert from 'node:assert';
import * as path from 'node:path';
import * as vscode from 'vscode';

const PUBLISHER_AND_NAME = 'xtclang.xtc-language';
const STARTUP_TIMEOUT_MS = 30_000;
const POLL_INTERVAL_MS = 250;

async function waitForHover(uri: vscode.Uri, position: vscode.Position): Promise<vscode.Hover[]> {
    // The built-in `vscode.executeHoverProvider` command returns hovers from
    // every registered provider for the given position. Before the LSP client
    // has finished initializing, the array is empty; once the server is up
    // and has indexed the document, our provider contributes at least one
    // hover entry. Polling on this signal is more reliable than scraping the
    // output channel for "Backend: TreeSitter" because it actually verifies
    // end-to-end LSP RPC, not just startup logging.
    const deadline = Date.now() + STARTUP_TIMEOUT_MS;
    let lastResult: vscode.Hover[] = [];
    while (Date.now() < deadline) {
        lastResult = (await vscode.commands.executeCommand<vscode.Hover[]>(
            'vscode.executeHoverProvider',
            uri,
            position,
        )) ?? [];
        if (lastResult.length > 0) {
            return lastResult;
        }
        await new Promise(resolve => setTimeout(resolve, POLL_INTERVAL_MS));
    }
    return lastResult;
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
        const fixture = path.resolve(__dirname, '..', '..', '..', 'src', 'test', 'fixtures', 'hello.x');
        const doc = await vscode.workspace.openTextDocument(vscode.Uri.file(fixture));
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
                'Check the "XTC Language Server" output channel from a manual run.',
        );
    });
});
