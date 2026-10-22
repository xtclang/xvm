// JAR-bundling sanity test. Mirrors lang/intellij-plugin's
// LspServerJarResolutionTest: verifies that the build placed the LSP
// server JAR at the path the extension code resolves it from, so an
// installed extension can actually start the server. Debug sessions run
// in the language server, so there is no separate DAP server JAR.
//
// This is the only integration test that catches a regression where
// the `copyLspServer` Gradle task falls out of sync with extension.ts's
// `context.asAbsolutePath(...)` calls.

import * as assert from 'node:assert';
import * as fs from 'node:fs';
import * as vscode from 'vscode';

const PUBLISHER_AND_NAME = 'xtclang.xtc-language';

suite('Bundled server JAR', () => {
    let extensionRoot: string;

    suiteSetup(async () => {
        const ext = vscode.extensions.getExtension(PUBLISHER_AND_NAME);
        assert.ok(ext, `extension ${PUBLISHER_AND_NAME} not found`);
        await ext.activate();
        extensionRoot = ext.extensionPath;
    });

    test('lsp-server.jar is present at the expected runtime path', () => {
        const expected = `${extensionRoot}/server/lsp-server.jar`;
        assert.ok(
            fs.existsSync(expected),
            `expected ${expected} to exist (produced by :lang:vscode-extension:copyLspServer). ` +
                'If this fails, the build placed the JAR somewhere other than where extension.ts resolves it.',
        );
        const stat = fs.statSync(expected);
        assert.ok(stat.size > 1024 * 1024, `lsp-server.jar is suspiciously small (${stat.size} bytes) — expected a fat jar >1 MB`);
    });
});
