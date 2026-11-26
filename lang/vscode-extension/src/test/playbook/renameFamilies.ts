import * as assert from 'node:assert';
import * as fs from './disk';
import * as vscode from 'vscode';
import { compilerSettingsLocation, compilerSourceModules, renameWithConfiguration } from '../../rename-proposal';
import { focusTestWindow } from '../native-focus';
import { discovered } from './liveWorkspace';
import { client, eventually, noErrors, outsideUsesMessage, playbook, position, symbols, Workspace } from './support';

/** Inspect edits to closed consumers without opening their editor tabs before Undo. */
async function contents(workspace: Workspace, file: string): Promise<string> {
    const uri = workspace.uri(file);
    return vscode.workspace.textDocuments.find(document => !document.isClosed && document.uri.toString() === uri.toString())?.getText()
        ?? fs.readFile(uri.fsPath, 'utf8');
}

/** Exercise the installed client's real proposal, including the asynchronous conversion boundary. */
async function configurationGuards(document: vscode.TextDocument, at: vscode.Position, replacement: string,
    token: vscode.CancellationToken): Promise<void> {
    const languageClient = client();
    const otherFolder = vscode.workspace.workspaceFolders?.[1];
    if (otherFolder) {
        const configuration = vscode.workspace.getConfiguration('xtc.compiler', otherFolder.uri);
        // The extension declares window scope. VS Code itself prevents an effective folder override.
        await assert.rejects(() => Promise.resolve(configuration.update('sourceModules', [], vscode.ConfigurationTarget.WorkspaceFolder)),
            /does not support the folder resource scope/);
        assert.strictEqual(configuration.inspect('sourceModules')?.workspaceFolderValue, undefined);
    }
    const settings = await vscode.workspace.openTextDocument(compilerSettingsLocation()!.uri);
    const original = settings.getText();
    const converter = languageClient.protocol2CodeConverter;
    // No fake compiler reply: delay the actual conversion only long enough for a real settings edit.
    const delayedConverter = new Proxy(converter, {
        get(target, property, receiver) {
            if (property === 'asWorkspaceEdit') return async (...args: Parameters<typeof converter.asWorkspaceEdit>) => {
                const converted = await converter.asWorkspaceEdit(...args);
                const edit = new vscode.WorkspaceEdit();
                edit.insert(settings.uri, settings.positionAt(settings.getText().length), '\n// Settings edited during Rename\n');
                assert.ok(await vscode.workspace.applyEdit(edit));
                return converted;
            };
            return Reflect.get(target, property, receiver);
        }
    });
    const delayedClient = new Proxy(languageClient, {
        get(target, property) {
            if (property === 'protocol2CodeConverter') return delayedConverter;
            const value = Reflect.get(target, property, target);
            return typeof value === 'function' ? value.bind(target) : value;
        }
    });
    try {
        const outcome = await eventually(async () => {
            try {
                const edit = await renameWithConfiguration(delayedClient, document, at, replacement, token);
                return edit ? { edit } : undefined;
            } catch (error) {
                return { error };
            }
        }, value => value !== undefined, 'Rename proposal survives fixture watcher delivery');
        assert.ok(outcome && 'error' in outcome, 'Conversion must refuse the intervening settings edit');
        assert.match(String(outcome.error), /changed while converting the refactoring/);
    } finally {
        const restore = new vscode.WorkspaceEdit();
        restore.replace(settings.uri, new vscode.Range(settings.positionAt(0), settings.positionAt(settings.getText().length)), original);
        assert.ok(await vscode.workspace.applyEdit(restore));
        assert.ok(await settings.save());
    }
}

/** Checks that the compiler's source modules name [module], not [other], and keep their resource roots. */
function sourceModulesName(module: string, other: string): void {
    const modules = JSON.stringify(compilerSourceModules());
    assert.ok(modules.includes(module) && modules.includes('assets') && modules.includes('fallback') && !modules.includes(other), modules);
}

const renameCases = ['X109', 'X110', 'X111', 'X112', 'X113', 'X114', 'X115', 'X116', 'X117', 'X118', 'X119', 'X120', 'X121'] as const;

export function renameFamilyCases(ids: readonly (typeof renameCases[number] | 'X155' | 'X159' | 'X160' | 'X164' | 'X165' | 'X166' | 'X167' | 'X168' | 'X220' | 'X300')[] = renameCases): void {
    for (const id of ids) {
        playbook(id, async (workspace, data) => {
            for (const file of data.files) await workspace.write(file.file, file.source);
            await discovered(workspace, async () => {
                if ('sourceModules' in data) await workspace.configure(data.sourceModules.map(module => ({
                    ...module,
                    uri: workspace.uri(module.uri).toString(),
                    resourceRoots: 'resourceRoots' in module ? module.resourceRoots?.map(root => workspace.uri(root).toString()) : undefined
                })));
                if ('projectSettingsRoundTrip' in data && data.projectSettingsRoundTrip) {
                    assert.ok(vscode.workspace.getConfiguration('xtc.compiler').inspect('sourceModules')?.workspaceValue,
                        'The explicit graph must be persisted in workspace settings');
                }
                const document = await workspace.open(data.file);
                await noErrors(document.uri);
                if ('navigationAnchor' in data) {
                    const references = await vscode.commands.executeCommand<vscode.Location[]>(
                        'vscode.executeReferenceProvider', document.uri, position(document, data.navigationAnchor));
                    assert.deepStrictEqual(references?.map(reference => reference.uri.fsPath).sort(),
                        data.navigationFiles.map(file => workspace.uri(file).fsPath).sort());
                }
                for (const file of data.files.filter(file => file.file !== data.file)) {
                    assert.ok(!vscode.workspace.textDocuments.some(document => !document.isClosed && document.uri.fsPath === workspace.uri(file.file).fsPath),
                        `Consumer must be closed before rename: ${file.file}`);
                }
                const provider = client().getFeature('textDocument/rename').getProvider(document);
                assert.ok(provider);
                const cancellation = new vscode.CancellationTokenSource();
                try {
                    if ('sourceModules' in data) {
                        await configurationGuards(document, position(document, data.anchor), data.replacement, cancellation.token);
                        for (const file of data.files) assert.strictEqual(await contents(workspace, file.file), file.source);
                    }
                    if (data.files.some(file => file.file !== file.destination && file.source !== file.expected)) {
                        const configuration = vscode.workspace.getConfiguration('files.refactoring');
                        const previous = configuration.inspect<boolean>('autoSave')?.workspaceValue;
                        try {
                            await configuration.update('autoSave', false, vscode.ConfigurationTarget.Workspace);
                            const refusal = await eventually(async () => {
                                try {
                                    const edit = await provider.provideRenameEdits(document, position(document, data.anchor), data.replacement, cancellation.token);
                                    assert.ok(!edit, 'An unsafe resource history must not be offered');
                                    return undefined;
                                } catch (error) { return error; }
                            }, Boolean, 'Resource rename refuses disabled refactoring auto-save');
                            assert.match(String(refusal), /requires files\.refactoring\.autoSave/);
                            for (const file of data.files) assert.strictEqual(await contents(workspace, file.file), file.source);
                        } finally { await configuration.update('autoSave', previous, vscode.ConfigurationTarget.Workspace); }
                    }
                    // the server's message on modules outside the workspace, which it shows as it answers the rename
                    const shown: string[] = [];
                    const observer = 'outsideUses' in data || 'unchangedSpellings' in data
                        ? client().onNotification('window/showMessage', (params: { message: string }) => { shown.push(params.message); })
                        : undefined;
                    const edit = await eventually(
                        async () => provider.provideRenameEdits(document, position(document, data.anchor), data.replacement, cancellation.token),
                        Boolean, `${id} rename proof after fixture discovery`).finally(() => observer?.dispose());
                    assert.ok(edit);
                    if ('outsideUses' in data) {
                        const notes = shown.filter(message => message.includes('modules outside the workspace'));
                        if (data.outsideUses === null) assert.deepStrictEqual(notes, [], 'A local\'s rename has no message');
                        else {
                            const message = outsideUsesMessage(String(data.outsideUses));
                            // a proof that the project overtook and that ran again says it again
                            assert.ok(notes.length > 0 && notes.every(note => note === message), JSON.stringify(notes));
                        }
                    }
                    // and its message on the comments and string literals that spell the old name, which the rename leaves
                    if ('unchangedSpellings' in data) assert.ok(shown.includes(`Ecstasy: ${data.unchangedSpellings}.`), JSON.stringify(shown));
                    assert.ok(await vscode.workspace.applyEdit(edit, { isRefactoring: true }));
                } finally { cancellation.dispose(); }
                for (const file of data.files) {
                    assert.strictEqual(await contents(workspace, file.destination), file.expected);
                    if (file.destination !== file.file) assert.strictEqual(await fs.stat(workspace.uri(file.file).fsPath).catch(() => null), null);
                }
                const destination = data.files.find(file => file.file === data.file)!.destination;
                const renamed = await workspace.open(destination);
                await symbols(renamed);
                await noErrors(renamed.uri);
                if ('sourceModules' in data) {
                    sourceModulesName('Renamed.example.org', 'Library.example.org');
                    assert.ok(await vscode.workspace.saveAll());
                }
                await focusTestWindow();
                await vscode.commands.executeCommand('workbench.action.focusActiveEditorGroup');
                await vscode.commands.executeCommand('undo');
                await eventually(() => contents(workspace, data.file).catch(() => ''),
                    text => text === data.files.find(file => file.file === data.file)!.source, `${id} undo restores the request document`);
                for (const file of data.files) {
                    assert.strictEqual(await contents(workspace, file.file), file.source);
                    if (file.destination !== file.file) assert.strictEqual(await fs.stat(workspace.uri(file.destination).fsPath).catch(() => null), null);
                }
                const restored = await workspace.open(data.file);
                await symbols(restored);
                await noErrors(restored.uri);
                if ('sourceModules' in data) {
                    sourceModulesName('Library.example.org', 'Renamed.example.org');
                    await focusTestWindow();
                    await vscode.commands.executeCommand('workbench.action.focusActiveEditorGroup');
                    await vscode.commands.executeCommand('redo');
                    await eventually(async () => compilerSourceModules(),
                        graph => JSON.stringify(graph).includes('Renamed.example.org'), 'Redo restores the replacement graph');
                    await eventually(async () => Promise.all(data.files.map(file => contents(workspace, file.destination).catch(() => ''))),
                        texts => texts.every((text, index) => text === data.files[index].expected), 'Redo restores every source and resource');
                    await workspace.open(destination);
                    await focusTestWindow();
                    await vscode.commands.executeCommand('workbench.action.focusActiveEditorGroup');
                    await vscode.commands.executeCommand('undo');
                    await eventually(async () => compilerSourceModules(),
                        graph => JSON.stringify(graph).includes('Library.example.org'), 'Second undo restores the original graph');
                    for (const file of data.files) assert.strictEqual(await contents(workspace, file.file), file.source);
                    assert.ok(await vscode.workspace.saveAll());
                }
            }).finally(async () => {
                // gives back the language client's own handler of the messages, which observing them took
                if ('outsideUses' in data) await vscode.commands.executeCommand('xtc.restartServer');
            });
        });
    }
}
