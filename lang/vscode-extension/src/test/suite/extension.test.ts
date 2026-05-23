// Integration tests for the Ecstasy (XTC) VS Code extension.
// Runs inside a real VS Code instance via @vscode/test-electron.

import * as assert from 'node:assert';
import * as path from 'node:path';
import * as vscode from 'vscode';

const PUBLISHER_AND_NAME = 'xtclang.xtc-language';

async function waitForLanguageId(doc: vscode.TextDocument, expected: string): Promise<string> {
    // The extension's onDidOpenTextDocument hook calls setTextDocumentLanguage
    // asynchronously; allow up to ~1s for it to settle. We bail early as soon
    // as the languageId flips so the test stays fast in the success case.
    for (let i = 0; i < 20; i++) {
        if (doc.languageId === expected) {
            return doc.languageId;
        }
        await new Promise(resolve => setTimeout(resolve, 50));
    }
    return doc.languageId;
}

suite('Ecstasy file association', () => {
    suiteSetup(async () => {
        const ext = vscode.extensions.getExtension(PUBLISHER_AND_NAME);
        assert.ok(ext, `extension ${PUBLISHER_AND_NAME} not found — check publisher/name in package.json`);
        await ext.activate();
    });

    test('opens .x files with languageId "xtc"', async () => {
        // The runTest launcher opens VS Code with src/test/fixtures as the
        // workspace folder, so this absolute path resolves cleanly.
        const fixture = path.resolve(__dirname, '..', '..', '..', 'src', 'test', 'fixtures', 'hello.x');
        const doc = await vscode.workspace.openTextDocument(vscode.Uri.file(fixture));
        const langId = await waitForLanguageId(doc, 'xtc');
        assert.strictEqual(
            langId,
            'xtc',
            `expected .x file to be associated with languageId "xtc", got "${langId}". ` +
                'If this fails, the extension\'s contributes.languages mapping or its ' +
                'onDidOpenTextDocument fallback may be broken.',
        );
    });
});
