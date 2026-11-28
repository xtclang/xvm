// Activation sanity test. Verifies that after the extension activates,
// the surfaces the extension promises to register are actually present.
// A regression here usually means activate() threw silently or a
// contributed command ID drifted between commands.ts and package.json.

import * as assert from 'node:assert';
import * as fs from 'node:fs';
import * as path from 'node:path';
import * as vscode from 'vscode';
import { configurationProperties } from '../../settings-report';

const PUBLISHER_AND_NAME = 'xtclang.xtc-language';

/** The parts of a contributed walkthrough step that name commands, settings and files. */
type WalkthroughStep = { id: string; description: string; completionEvents: string[]; media: { markdown: string } };

const EXPECTED_COMMANDS = [
    'xtc.createProject',
    'xtc.runModule',
    'xtc.build',
    'xtc.rebuild',
    'xtc.selectBuildMode',
    'xtc.restartServer',
    'xtc.selectLanguageAdapter',
    'xtc.showServerOutput',
    'xtc.hideServerOutput',
    'xtc.openLanguageSettings',
    'xtc.showLanguageServiceStatus',
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

    test('connection preferences are window scoped and ineffective formatting fields are explicit', () => {
        const properties = configurationProperties(vscode.extensions.getExtension(PUBLISHER_AND_NAME)!.packageJSON.contributes.configuration);
        for (const key of ['adapter', 'textSynchronization', 'saveFormatting']) {
            assert.strictEqual(properties[`xtc.languageService.${key}`].scope, 'window');
        }
        assert.strictEqual(properties['xtc.languageService.textSynchronization'].default, 'full');
        assert.strictEqual(properties['xtc.languageService.saveFormatting'].default, 'editor');
        assert.ok(properties['xtc.formatting.tabSize'].deprecationMessage);
        assert.strictEqual(properties['xtc.formatting.maxLineWidth'].deprecationMessage, undefined);
        assert.strictEqual(properties['xtc.formatting.maxLineWidth'].default, 120);
    });

    test('the walkthrough names contributed commands and settings, and shows bundled media', () => {
        // VS Code reports none of these: a step's button or completion event that names no command, or a missing
        // media file, leaves the step dead or blank on the Welcome page
        const extension = vscode.extensions.getExtension(PUBLISHER_AND_NAME)!;
        const contributes = extension.packageJSON.contributes;
        const commands = new Set<string>(contributes.commands.map((command: { command: string }) => command.command));
        const settings = configurationProperties(contributes.configuration);
        const steps: WalkthroughStep[] = contributes.walkthroughs.flatMap((walkthrough: { steps: WalkthroughStep[] }) => walkthrough.steps);
        assert.ok(steps.length > 0, 'package.json contributes no walkthrough steps');
        for (const step of steps) {
            const events = step.completionEvents.map(event => event.split(':'));
            const named = [...step.description.matchAll(/\(command:([^)?]+)/g)].map(match => match[1])
                .concat(events.filter(([kind]) => kind === 'onCommand').map(([, id]) => id));
            for (const id of named) {
                assert.ok(commands.has(id), `walkthrough step ${step.id} names ${id}, which package.json does not contribute`);
            }
            for (const [, key] of events.filter(([kind]) => kind === 'onSettingChanged')) {
                assert.ok(settings[key], `walkthrough step ${step.id} waits for ${key}, which package.json does not contribute`);
            }
            assert.ok(fs.existsSync(path.join(extension.extensionPath, step.media.markdown)),
                `walkthrough step ${step.id} shows ${step.media.markdown}, which the extension does not have`);
        }
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
