// Snippet contribution test (#4 in the parity audit).
//
// Verifies that the snippets declared in package.json's contributes.snippets
// (resolved to snippets/xtc.json) are picked up by VS Code's snippet
// completion provider. A regression here would surface as snippet expansion
// silently disappearing for users — easy to miss in interactive testing
// because the editor still works, the snippets just don't tab-expand.
//
// Implementation note: we query the registered completion providers via
// `vscode.executeCompletionItemProvider` rather than simulating a real
// keystroke + completion-accept. The keystroke path involves a settle
// race in @vscode/test-electron that's flaky on CI; the provider-level
// query is deterministic.

import * as assert from 'node:assert';
import * as vscode from 'vscode';

const PUBLISHER_AND_NAME = 'xtclang.xtc-language';

suite('Snippet contributions', () => {
    suiteSetup(async () => {
        const ext = vscode.extensions.getExtension(PUBLISHER_AND_NAME);
        assert.ok(ext, `extension ${PUBLISHER_AND_NAME} not found`);
        await ext.activate();
    });

    test('snippet "mod" expands to a module declaration', async () => {
        // Untitled in-memory document — avoids touching any fixture file
        // and ensures we exercise pure-extension snippet wiring, not
        // anything affected by surrounding file content.
        const doc = await vscode.workspace.openTextDocument({ language: 'xtc', content: 'mod' });
        await vscode.window.showTextDocument(doc);

        const position = new vscode.Position(0, 3); // end of "mod"
        const list = await vscode.commands.executeCommand<vscode.CompletionList>(
            'vscode.executeCompletionItemProvider',
            doc.uri,
            position,
        );
        assert.ok(list, 'executeCompletionItemProvider returned undefined');

        const snippets = list.items.filter(i => i.kind === vscode.CompletionItemKind.Snippet);
        assert.ok(
            snippets.length > 0,
            `no snippet-kind completions returned at "mod|" — package.json's contributes.snippets ` +
                'mapping to snippets/xtc.json appears broken.',
        );

        // The Module snippet's prefix is "mod"; the label VS Code shows is
        // either the JSON key ("Module") or the prefix depending on version.
        // Either way the body should contain the literal "module" keyword
        // and the ${1:mymodule} placeholder we wrote in xtc.json.
        const moduleSnippet = snippets.find(i => {
            const body = typeof i.insertText === 'string' ? i.insertText : i.insertText?.value;
            return body?.includes('module ') ?? false;
        });
        assert.ok(
            moduleSnippet,
            `none of the ${snippets.length} snippet completions contain the literal "module " — ` +
                'the Module entry in snippets/xtc.json is not reaching VS Code.',
        );
    });
});
