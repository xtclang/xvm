import * as assert from 'node:assert';
import * as path from 'node:path';
import * as vscode from 'vscode';
import { normalizeLibraries } from '../../library-configuration';
import { libraryOptions } from '../../library-settings';
import { getClient, updateCompilerConfiguration } from '../../lsp-client';
import { WorkbenchUi } from '../workbenchUi';
import { catalog, sharedScenarioPath } from './shared';
import { client, diagnostics, eventually, noErrors, playbook, targets } from './support';

export function librarySettingsCases(): void {
    for (const id of ['X266', 'X267', 'X268'] as const) {
        playbook(id, async workspace => {
            const data = catalog.common.librarySettings;
            const binaries = path.resolve(path.dirname(sharedScenarioPath), '../../lsp-server/build/generated/compiler-playbook/libraries');
            const one = vscode.Uri.file(path.join(binaries, 'one')).toString();
            const two = vscode.Uri.file(path.join(binaries, 'two')).toString();
            const sources = vscode.Uri.file(path.join(binaries, 'sources')).toString();
            const settings = vscode.workspace.getConfiguration('xtc.compiler');
            const previous = settings.inspect('libraries')?.workspaceValue;
            const previousGraph = settings.inspect('sourceModules')?.workspaceValue;
            const options = (modulePath: string[] | null, attached = false) => ({ modulePath,
                sourceAttachments: attached ? [{ module: data.module, roots: [sources] }] : [] });
            const apply = async (paths: string[], attached = false) => {
                await settings.update('libraries', options(paths, attached), vscode.ConfigurationTarget.Workspace);
                await updateCompilerConfiguration();
            };
            const document = await workspace.open(data.consumerFile, data.consumer);
            try {
                if (id === 'X266') {
                    const ui = await WorkbenchUi.connect();
                    const pick = async (label: string) => {
                        await ui.page.locator('.quick-input-widget:visible .monaco-list-row').filter({ hasText: label }).first().click({ timeout: 10_000 });
                    };
                    const enter = async (value: string) => {
                        const input = ui.page.locator('.quick-input-widget:visible input').first();
                        await input.fill(value);
                        await input.press('Enter');
                    };
                    try {
                        const cancelled = vscode.commands.executeCommand('xtc.configureCompilerLibraries');
                        await pick('Edit binary paths');
                        await pick('Enter path…');
                        await enter(one);
                        await ui.page.locator('.quick-input-widget:visible input').first().press('Escape');
                        await cancelled;
                        assert.deepStrictEqual(settings.inspect('libraries')?.workspaceValue, previous);
                        const command = vscode.commands.executeCommand('xtc.configureCompilerLibraries');
                        await pick('Edit binary paths');
                        for (const value of [one, two]) { await pick('Enter path…'); await enter(value); }
                        await pick(`2. ${two}`);
                        await pick('Move up');
                        await pick('Use these paths');
                        await pick('Apply library settings');
                        await command;
                        assert.deepStrictEqual(libraryOptions().modulePath, [two, one]);
                        await noErrors(document.uri);
                        const effective = await client().sendRequest<{ bundledXdk: { readOnly: boolean } }>('xtc/languageServiceStatus');
                        assert.ok(effective.bundledXdk.readOnly);
                    } finally { await vscode.commands.executeCommand('workbench.action.closeQuickOpen'); await ui.close(); }
                } else {
                    await apply([one], true);
                    await noErrors(document.uri);
                    const definition = async () => {
                        const locations = await targets(document, 'Definition', document.positionAt(data.consumer.indexOf(data.anchor)));
                        assert.strictEqual(locations.length, 1);
                        assert.strictEqual(locations[0].range.start.line, data.definitionLine);
                        const library = await vscode.workspace.openTextDocument(locations[0].uri);
                        assert.strictEqual(library.getText(), data.source);
                        assert.strictEqual(library.uri.scheme, 'ecstasy-library');
                        await vscode.window.showTextDocument(library, { preview: true, preserveFocus: true });
                    };
                    await definition();
                    if (id === 'X267') {
                        const previousClient = getClient();
                        await vscode.commands.executeCommand('xtc.restartServer');
                        await eventually(async () => getClient() !== previousClient && !!getClient()?.initializeResult, ready => ready, 'Library configuration reconnect');
                        await noErrors(document.uri);
                        await definition();
                        assert.deepStrictEqual(libraryOptions(), options([one], true));
                    } else {
                        assert.throws(() => normalizeLibraries(options([one, one]), [], true), /Duplicate/);
                        assert.throws(() => normalizeLibraries(options([vscode.Uri.file(path.join(binaries, 'missing')).toString()]), [], true), /does not exist/);
                        await settings.update('libraries', options([vscode.Uri.file(path.join(binaries, 'missing')).toString()]), vscode.ConfigurationTarget.Workspace);
                        await updateCompilerConfiguration();
                        await noErrors(document.uri);
                        await definition();
                        await apply([one], true);
                        const resources = [two, one];
                        await settings.update('sourceModules', [{ name: 'LibraryConsumer', uri: document.uri.toString(), resourceRoots: resources }], vscode.ConfigurationTarget.Workspace);
                        await updateCompilerConfiguration();
                        const modules = await client().sendRequest<{ resourceRoots: string[] }[]>('xtc/compilerSourceModules');
                        assert.deepStrictEqual(modules[0].resourceRoots.map(uri => vscode.Uri.parse(uri).fsPath), resources.map(uri => vscode.Uri.parse(uri).fsPath));
                        await noErrors(document.uri);
                    }
                    await apply([]);
                    await diagnostics(document.uri, values => values.length > 0, 'Removed binary library');
                    await apply([one], true);
                    await noErrors(document.uri);
                }
            } finally {
                await settings.update('sourceModules', previousGraph, vscode.ConfigurationTarget.Workspace);
                await settings.update('libraries', previous, vscode.ConfigurationTarget.Workspace);
                await updateCompilerConfiguration();
            }
        });
    }
}
