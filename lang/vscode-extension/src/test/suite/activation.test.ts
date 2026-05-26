// Activation sanity test. Verifies that after the extension activates,
// the surfaces the extension promises to register are actually present.
// A regression here usually means activate() threw silently or a
// contributed command ID drifted between commands.ts and package.json.

import * as assert from 'node:assert';
import * as vscode from 'vscode';

const PUBLISHER_AND_NAME = 'xtclang.xtc-language';

const EXPECTED_COMMANDS = [
    'xtc.createProject',
    'xtc.runModule',
    'xtc.restartServer',
    'xtc.showServerOutput',
];

suite('Extension activation surfaces', () => {
    suiteSetup(async () => {
        const ext = vscode.extensions.getExtension(PUBLISHER_AND_NAME);
        assert.ok(ext, `extension ${PUBLISHER_AND_NAME} not found`);
        await ext.activate();
        assert.strictEqual(ext.isActive, true, 'extension did not enter active state after activate()');
    });

    test('all contributed commands are registered', async () => {
        const registered = await vscode.commands.getCommands(/*filterInternal*/ true);
        const missing = EXPECTED_COMMANDS.filter(id => !registered.includes(id));
        assert.deepStrictEqual(
            missing,
            [],
            `commands declared in package.json's contributes.commands are not registered at runtime: ${missing.join(', ')}. ` +
                'Check that registerCommands() in extension.ts wires every command ID listed in package.json.',
        );
    });

    test('xtc.showServerOutput executes without throwing', async () => {
        // Executing this command instantiates the output channel if not already
        // created, which gives us a non-invasive smoke test that the channel
        // pipeline is intact even before any LSP traffic has happened.
        await assert.doesNotReject(
            () => Promise.resolve(vscode.commands.executeCommand('xtc.showServerOutput')),
            'xtc.showServerOutput threw — output channel wiring is broken',
        );
    });
});
