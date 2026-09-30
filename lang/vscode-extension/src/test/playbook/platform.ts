import * as assert from 'node:assert';
import * as fs from 'node:fs/promises';
import * as os from 'node:os';
import * as path from 'node:path';
import * as vscode from 'vscode';
import { CodeAction, ProgressType } from 'vscode-languageclient/node';
import { discovered } from './liveWorkspace';
import { modelPath } from '../../build-model';
import { getClient, updateCompilerConfiguration } from '../../lsp-client';
import { client, diagnostics, eventually, hover, label, noErrors, playbook, symbols, targets } from './support';

export function platformCases(): void {
    playbook('X124', async (workspace, data) => {
        const external = await fs.mkdtemp(path.join(os.tmpdir(), 'xtc-playbook-resources-'));
        try {
            await workspace.write(data.file, data.text);
            const resourceRoot = path.join(external, data.resourceDirectory);
            const modules = [{ name: data.module, uri: workspace.uri(data.file).toString(), resourceRoots: [vscode.Uri.file(resourceRoot).toString()] }];
            await workspace.configure(modules);
            const document = await workspace.open(data.file);
            await diagnostics(document.uri, values => values.length > 0, 'Missing external resource');
            await fs.mkdir(resourceRoot, { recursive: true });
            await fs.writeFile(path.join(resourceRoot, data.resource), data.contents);
            await noErrors(document.uri);
            await workspace.configure([{ ...modules[0], resourceRoots: [] }]);
            await diagnostics(document.uri, values => values.length > 0, 'Explicitly disabled resources');
            await workspace.configure(modules);
            await noErrors(document.uri);
            assert.deepStrictEqual(vscode.workspace.getConfiguration('xtc.compiler').get('sourceModules'), modules);
            await fs.unlink(path.join(resourceRoot, data.resource));
            await diagnostics(document.uri, values => values.length > 0, 'Deleted external resource');
            await fs.writeFile(path.join(resourceRoot, data.resource), data.contents);
            await noErrors(document.uri);
            const replacement = path.join(external, 'replacement');
            await workspace.configure([{ ...modules[0], resourceRoots: [vscode.Uri.file(replacement).toString()] }]);
            await diagnostics(document.uri, values => values.length > 0, 'Replacement root is missing');
            await fs.mkdir(replacement);
            await fs.writeFile(path.join(replacement, data.resource), data.contents);
            await noErrors(document.uri);
        } finally { await fs.rm(external, { recursive: true, force: true }); }
    });

    playbook('X125', async (workspace, data) => {
        const document = await workspace.open(data.file, data.hoverSource);
        await noErrors(document.uri);
        const contents = await hover(document, document.positionAt(data.hoverSource.lastIndexOf(data.hoverAnchor)));
        assert.ok(contents.includes(data.hoverExpected), contents);
        for (const call of data.signatures) {
            await workspace.replace(document, call.text.replace('§', ''));
            const help = await workspace.signature(document, document.positionAt(call.text.indexOf('§')));
            assert.strictEqual(help?.signatures[0].parameters.length, 2);
            assert.strictEqual(help?.activeParameter, call.active);
        }
        for (const text of data.completions) {
            await workspace.replace(document, text.replace('§', ''));
            const items = await vscode.commands.executeCommand<vscode.CompletionList>('vscode.executeCompletionItemProvider', document.uri, document.positionAt(text.indexOf('§')));
            assert.ok(items?.items.some(item => label(item) === data.completion));
        }
        await workspace.replace(document, data.narrowedSource);
        await noErrors(document.uri);
        const locations = await targets(document, 'TypeDefinition', document.positionAt(data.narrowedSource.lastIndexOf(data.narrowedAnchor)));
        assert.ok(locations.some(location => location.uri.path.endsWith(data.targetSuffix)));
    });
    playbook('X126', async (workspace, data) => {
        const document = await workspace.open(data.file, data.source);
        const id = { uri: document.uri.toString() };
        const full = await client().sendRequest<{ data: number[]; resultId: string }>('textDocument/semanticTokens/full', { textDocument: id });
        assert.ok(full.data.length && full.resultId);
        await workspace.replace(document, '\n' + data.source);
        const caps = client().initializeResult!.capabilities.semanticTokensProvider!;
        if (typeof caps.full === 'object' && caps.full.delta) {
            const delta = await client().sendRequest<{ edits: { start: number; deleteCount: number; data?: number[] }[] }>('textDocument/semanticTokens/full/delta', { textDocument: id, previousResultId: full.resultId });
            const patched = [...full.data];
            for (const edit of delta.edits.slice().sort((a, b) => b.start - a.start)) patched.splice(edit.start, edit.deleteCount, ...edit.data ?? []);
            const current = await client().sendRequest<{ data: number[] }>('textDocument/semanticTokens/full', { textDocument: id });
            assert.deepStrictEqual(patched, current.data);
        } else {
            await assert.rejects(client().sendRequest('textDocument/semanticTokens/full/delta', { textDocument: id, previousResultId: full.resultId }), /not negotiated/);
        }
        if (caps.range) {
            const range = await client().sendRequest<{ data: number[] }>('textDocument/semanticTokens/range', { textDocument: id, range: { start: { line: 0, character: 0 }, end: { line: 1, character: 0 } } });
            assert.deepStrictEqual(range.data, []);
        }
        await workspace.discard(document);
        await workspace.open(data.file);
        if (typeof caps.full === 'object' && caps.full.delta) {
            const reopened = await client().sendRequest<{ data: number[] }>('textDocument/semanticTokens/full/delta', { textDocument: id, previousResultId: full.resultId });
            assert.ok(reopened.data.length);
        }
    });

    playbook('X127', async (workspace, data) => {
        await workspace.write(data.file, data.source);
        await workspace.configure([{ name: 'Resolve', uri: workspace.uri(data.file).toString() }]);
        const document = await workspace.open(data.file, data.source);
        const actions = await client().sendRequest<CodeAction[]>('textDocument/codeAction', {
            textDocument: { uri: document.uri.toString() }, range: { start: { line: 0, character: 0 }, end: { line: 0, character: data.source.length } }, context: { diagnostics: [] }
        });
        assert.strictEqual(actions.length, 1);
        const action = actions[0];
        const capabilities = client().initializeResult!.capabilities.codeActionProvider;
        if (typeof capabilities === 'object' && capabilities.resolveProvider) {
            assert.ok(action.data);
            assert.strictEqual(action.edit, undefined);
            const resolved = await client().sendRequest<CodeAction>('codeAction/resolve', action);
            assert.ok(resolved.edit?.documentChanges?.length);
            assert.strictEqual(resolved.title, action.title);
            await workspace.replace(document, '\n' + data.source);
            await assert.rejects(client().sendRequest('codeAction/resolve', action), /expired or changed/);
        } else assert.ok(action.edit);
    });

    playbook('X128', async (workspace, data) => {
        for (const variant of data.variants) {
            await workspace.write(variant.root, variant.source);
            await workspace.write(variant.member, variant.memberSource);
            await workspace.configure([{ name: 'App', uri: workspace.uri(variant.root).toString() }]);
            const document = await workspace.open(variant.root);
            await noErrors(document.uri);
            const edit = new vscode.WorkspaceEdit();
            edit.renameFile(workspace.uri(variant.from), workspace.uri(variant.to), { overwrite: false });
            assert.ok(await vscode.workspace.applyEdit(edit));
            await eventually(async () => document.getText(), text => text === variant.expected, 'Native file rename updates references');
            const target = await workspace.open(variant.target);
            assert.strictEqual(target.getText(), variant.targetSource);
            await noErrors(document.uri);
        }
    });

    playbook('X129', async (workspace, data) => {
        const report = vscode.Uri.joinPath(vscode.workspace.workspaceFolders![0].uri, modelPath);
        const model = JSON.parse(JSON.stringify(data.model).split('${workspace}').join(vscode.Uri.file(workspace.directory).toString()));
        const settings = vscode.workspace.getConfiguration('xtc.compiler');
        const previous = settings.get('sourceModules');
        const writeModel = async () => {
            await fs.mkdir(path.dirname(report.fsPath), { recursive: true });
            await fs.writeFile(report.fsPath, JSON.stringify(model));
            await updateCompilerConfiguration();
        };
        try {
            await workspace.write(data.file, data.source);
            await workspace.write(data.resource, data.contents);
            await writeModel();
            await settings.update('sourceModules', null, vscode.ConfigurationTarget.Workspace);
            const document = await workspace.open(data.file);
            await noErrors(document.uri);
            model.sourceSets[0].resourceRoots = [];
            await writeModel();
            await diagnostics(document.uri, values => values.length > 0, 'Reimport disabled resources');
            model.sourceSets[0].resourceRoots = [workspace.uri('processed').toString()];
            await writeModel();
            await noErrors(document.uri);
            await fs.writeFile(report.fsPath, '{');
            await updateCompilerConfiguration();
            await noErrors(document.uri);
            const override = [{ name: 'GradleAssets', uri: document.uri.toString(), resourceRoots: [] }];
            await workspace.configure(override);
            await diagnostics(document.uri, values => values.length > 0, 'Explicit override disables resources');
            await writeModel();
            assert.deepStrictEqual(vscode.workspace.getConfiguration('xtc.compiler').get('sourceModules'), override);
            await diagnostics(document.uri, values => values.length > 0, 'Refresh preserves explicit override');
            await settings.update('sourceModules', null, vscode.ConfigurationTarget.Workspace);
            await noErrors(document.uri);
            await vscode.commands.executeCommand('xtc.showCompilerPaths');
        } finally {
            await fs.rm(report.fsPath, { force: true });
            await settings.update('sourceModules', previous, vscode.ConfigurationTarget.Workspace);
            await updateCompilerConfiguration();
        }
    });

    playbook('X130', async (workspace, data) => {
        for (const file of data.files) await workspace.write(file.file, file.text);
        await fs.mkdir(path.join(workspace.directory, data.destination));
        await discovered(workspace, async () => {
            const document = await workspace.open(data.consumer);
            await noErrors(document.uri);
            // Use Explorer's actual batch operation and its Undo source. A pure file-only
            // workspace.applyEdit is not on the unrelated Consumer editor's Undo stack.
            assert.deepStrictEqual(data.sources, ['old', 'second']);
            const sourcePaths = data.sources.map(source => workspace.uri(source).fsPath).sort();
            await vscode.commands.executeCommand('workbench.files.action.refreshFilesExplorer');
            await eventually(async () => {
                // A watcher refresh can retire Explorer nodes between selection and Cut. Only
                // repeat this pre-mutation selection step; Paste, Undo and Redo each run once.
                try {
                    await vscode.commands.executeCommand('workbench.files.action.collapseExplorerFolders');
                    await vscode.commands.executeCommand('revealInExplorer', workspace.uri(data.sources[0]));
                    await vscode.commands.executeCommand('list.expandSelectionDown');
                    await vscode.commands.executeCommand('copyFilePath');
                    const selected = (await vscode.env.clipboard.readText()).split(/\r?\n/).sort();
                    if (JSON.stringify(selected) !== JSON.stringify(sourcePaths)) return selected;
                    await vscode.commands.executeCommand('filesExplorer.cut');
                    return selected;
                } catch (error) {
                    if (!String(error).includes('Data tree node not found')) throw error;
                    return [];
                }
            }, selected => JSON.stringify(selected) === JSON.stringify(sourcePaths), 'Explorer selects both source folders');
            await vscode.commands.executeCommand('revealInExplorer', workspace.uri(data.destination));
            await vscode.commands.executeCommand('filesExplorer.paste');
            const moved = async () => Promise.all(data.sources.map(async source => ({
                old: await fs.stat(path.join(workspace.directory, source)).then(() => true, () => false),
                new: await fs.stat(path.join(workspace.directory, data.destination, source)).then(() => true, () => false)
            })));
            await eventually(moved, values => values.every(value => !value.old && value.new), 'All selected directories moved');
            await noErrors(document.uri);
            for (const [action, expected] of [['undo', false], ['redo', true]] as const) {
                await vscode.commands.executeCommand('workbench.files.action.focusFilesExplorer');
                await vscode.commands.executeCommand(action);
                await eventually(moved, values => values.every(value => value.old !== expected && value.new === expected), `${action} restores all paths`);
                await noErrors(document.uri);
            }
            for (const file of data.files) {
                const target = data.sources.some(source => file.file.startsWith(`${source}/`)) ? `${data.destination}/${file.file}` : file.file;
                assert.strictEqual(await fs.readFile(path.join(workspace.directory, target), 'utf8'), file.text);
            }
        });
    });

    playbook('X131', async (workspace, data) => {
        await workspace.write(data.file, data.source);
        await workspace.configure([{ name: data.module, uri: workspace.uri(data.file).toString() }]);
        const document = await workspace.open(data.file);
        await noErrors(document.uri);
        const id = { textDocument: { uri: document.uri.toString() } };
        const lenses = await client().sendRequest<import('vscode-languageclient/node').CodeLens[]>('textDocument/codeLens', id);
        const links = await client().sendRequest<import('vscode-languageclient/node').DocumentLink[]>('textDocument/documentLink', id);
        const hints = await client().sendRequest<import('vscode-languageclient/node').InlayHint[]>('textDocument/inlayHint', { ...id, range: { start: { line: 0, character: 0 }, end: { line: 6, character: 0 } } });
        const symbols = await client().sendRequest<import('vscode-languageclient/node').WorkspaceSymbol[]>('workspace/symbol', { query: data.module });
        const lens = lenses[0], link = links[0], hint = hints.find(item => item.data || item.tooltip)!, symbol = symbols[0];
        const resolve = async <T extends { data?: unknown }>(method: string, item: T): Promise<T> => item.data ? client().sendRequest<T>(method, item) : item;
        const resolvedLens = await resolve('codeLens/resolve', lens);
        assert.deepStrictEqual(resolvedLens.range, lens.range);
        assert.strictEqual(resolvedLens.command?.arguments?.length, 2);
        assert.strictEqual((await resolve('documentLink/resolve', link)).target, data.link);
        const resolvedHint = await resolve('inlayHint/resolve', hint);
        assert.deepStrictEqual(resolvedHint.label, hint.label);
        assert.ok(resolvedHint.tooltip);
        assert.ok('range' in (await resolve('workspaceSymbol/resolve', symbol)).location);
        await workspace.replace(document, '\n' + data.source);
        for (const [method, item] of [['codeLens/resolve', lens], ['documentLink/resolve', link], ['inlayHint/resolve', hint], ['workspaceSymbol/resolve', symbol]] as const) {
            if (item.data) await assert.rejects(client().sendRequest(method, item), /expired or changed/);
        }
    });

    playbook('X132', async (workspace, data) => {
        await workspace.write(data.file, data.source);
        await workspace.configure([{ name: data.module, uri: workspace.uri(data.file).toString() }]);
        const document = await workspace.open(data.file);
        await noErrors(document.uri);
        const params = { textDocument: { uri: document.uri.toString() }, options: { tabSize: 4, insertSpaces: true }, ranges: data.ranges };
        const edits = await client().sendRequest<import('vscode-languageclient/node').TextEdit[]>('textDocument/rangesFormatting', params);
        assert.deepStrictEqual(edits.map(edit => edit.range.start.line), data.lines);
        assert.ok(edits.every(edit => edit.newText === data.indent));
        const sync = client().initializeResult?.capabilities.textDocumentSync;
        if (typeof sync === 'object') {
            const save = { textDocument: params.textDocument, reason: 1 };
            if (sync.willSave) await client().sendNotification('textDocument/willSave', save);
            if (sync.willSaveWaitUntil) assert.deepStrictEqual(await client().sendRequest('textDocument/willSaveWaitUntil', save), []);
        }
        assert.strictEqual(document.getText(), data.source);
    });

    playbook('X133', async (workspace, data) => {
        await workspace.write(data.file, data.source);
        await workspace.configure([{ name: data.module, uri: workspace.uri(data.file).toString() }]);
        const document = await workspace.open(data.file);
        await noErrors(document.uri);
        const linked = async (anchor: string) => client().sendRequest<import('vscode-languageclient/node').LinkedEditingRanges>('textDocument/linkedEditingRange', {
            textDocument: { uri: document.uri.toString() }, position: document.positionAt(document.getText().indexOf(anchor))
        });
        const result = await linked(data.anchor);
        assert.deepStrictEqual(result.ranges.map(range => document.offsetAt(new vscode.Position(range.start.line, range.start.character))), data.uses.map((anchor: string) => data.source.indexOf(anchor)));
        for (const anchor of data.refused) assert.strictEqual((await linked(anchor)).ranges?.length ?? 0, 0);
        await workspace.replace(document, data.broken);
        await diagnostics(document.uri, values => values.length > 0, 'Broken source');
        assert.strictEqual((await linked(data.anchor)).ranges?.length ?? 0, 0);
        await workspace.replace(document, data.source);
        await noErrors(document.uri);
        assert.strictEqual((await linked(data.anchor)).ranges.length, 2);
    });

    playbook('X134', async (workspace, data) => {
        const external = await fs.mkdtemp(path.join(os.tmpdir(), 'xtc-playbook-source-'));
        try {
            const library = path.join(external, data.libraryFile);
            await fs.writeFile(library, data.library);
            await workspace.write(data.file, data.consumer);
            await workspace.configure([
                { name: data.libraryModule, uri: vscode.Uri.file(library).toString() },
                { name: data.module, uri: workspace.uri(data.file).toString(), dependencies: [data.libraryModule] }
            ]);
            const document = await workspace.open(data.file);
            await noErrors(document.uri);
            await fs.writeFile(library, data.brokenLibrary);
            await diagnostics(document.uri, values => values.length > 0, 'External source changed');
            await fs.writeFile(library, data.library);
            await noErrors(document.uri);
            await fs.rm(library);
            await diagnostics(document.uri, values => values.length > 0, 'External source deleted');
            await fs.writeFile(library, data.library);
            await noErrors(document.uri);
        } finally { await workspace.configure([]); await fs.rm(external, { recursive: true, force: true }); }
    });

    playbook('X135', async (workspace, data) => {
        const document = await workspace.open(data.file, data.source);
        await noErrors(document.uri);
        const visible = () => vscode.window.visibleTextEditors.some(editor =>
            /Ecstasy Language Server(?:\.\d+)?\.log$/.test(editor.document.uri.path));
        for (const command of ['xtc.showServerOutput', 'xtc.hideServerOutput', 'xtc.showServerOutput', 'xtc.hideServerOutput']) {
            await vscode.commands.executeCommand(command);
            await eventually(async () => visible(), value => value === (command === 'xtc.showServerOutput'), command);
        }
        assert.strictEqual(document.getText(), data.source);
    });

    playbook('X136', async (workspace, data) => {
        const document = await workspace.open(data.file, data.source);
        await noErrors(document.uri);
        const before = await client().sendRequest<{ pid: number }>('xtc/languageServiceStatus');
        const config = vscode.workspace.getConfiguration('xtc');
        const original = config.inspect('inlayHints.enabled')?.workspaceValue;
        const graph = config.get('compiler.sourceModules');
        try {
            await config.update('inlayHints.enabled', false, vscode.ConfigurationTarget.Workspace);
            const hints = await vscode.commands.executeCommand<vscode.InlayHint[]>('vscode.executeInlayHintProvider', document.uri, new vscode.Range(0, 0, document.lineCount, 0));
            assert.deepStrictEqual(hints, []);
            const report = await vscode.commands.executeCommand<{ configured: object; effective: { pid: number; compilerQueue: { queueSize: number; queuedJobs: string[] } } }>('xtc.showLanguageServiceStatus');
            assert.strictEqual(report?.effective.pid, before.pid);
            assert.ok(Array.isArray(report.effective.compilerQueue.queuedJobs));
            assert.strictEqual(report.effective.compilerQueue.queueSize, report.effective.compilerQueue.queuedJobs.length);
            assert.deepStrictEqual(config.get('compiler.sourceModules'), graph);
            assert.strictEqual(document.getText(), data.source);
        } finally { await config.update('inlayHints.enabled', original, vscode.ConfigurationTarget.Workspace); }
    });

    playbook('X137', async (workspace, data) => {
        const document = await workspace.open(data.file, data.source);
        await workspace.replace(document, data.edited);
        const config = vscode.workspace.getConfiguration('xtc.languageService');
        const original = config.inspect('textSynchronization')?.workspaceValue;
        const status = async () => {
            const current = getClient();
            if (!current?.isRunning()) return undefined;
            try { return await current.sendRequest<{ pid: number; textSynchronization: string }>('xtc/languageServiceStatus'); }
            catch { return undefined; }
        };
        let previous = (await status())!.pid;
        try {
            for (const transport of ['incremental', 'full']) {
                const started = Date.now();
                await config.update('textSynchronization', transport, vscode.ConfigurationTarget.Workspace);
                const after = await eventually(status, value => value?.textSynchronization === transport && value.pid !== previous, `Restart into ${transport}`);
                console.log(`[X137] ${transport} connection ready in ${Date.now() - started}ms; PID ${previous} -> ${after!.pid}`);
                previous = after!.pid;
                assert.strictEqual(document.getText(), data.edited);
                assert.ok(document.isDirty, 'Restart must not save the buffer');
                await noErrors(document.uri);
                await eventually(
                    () => symbols(document),
                    values => values.length > 0, 'Document symbols registered after restart');
                console.log(`[X137] ${transport} unsaved document ready in ${Date.now() - started}ms`);
            }
        } finally {
            await config.update('textSynchronization', original, vscode.ConfigurationTarget.Workspace);
            await eventually(status, value => value?.textSynchronization === config.get('textSynchronization'), 'Original transport restored');
        }
    });

    playbook('X138', async (workspace, data) => {
        const document = await workspace.open(data.file, data.source);
        const config = vscode.workspace.getConfiguration('xtc.formatting');
        const original = config.inspect('indentSize')?.workspaceValue;
        const before = await client().sendRequest<{ pid: number }>('xtc/languageServiceStatus');
        try {
            await config.update('indentSize', data.indent, vscode.ConfigurationTarget.Workspace);
            await eventually(async () => client().sendRequest<{ formatting: { indentSize: number } }>('xtc/languageServiceStatus'), value => value.formatting?.indentSize === data.indent, 'Live formatter configuration');
            const formatted = async () => {
                const edits = await vscode.commands.executeCommand<vscode.TextEdit[]>('vscode.executeFormatDocumentProvider', document.uri, { tabSize: 4, insertSpaces: true });
                assert.ok(edits, 'Formatting provider returns edits');
                return edits.sort((left, right) => document.offsetAt(right.range.start) - document.offsetAt(left.range.start))
                    .reduce((text, edit) => text.slice(0, document.offsetAt(edit.range.start)) + edit.newText + text.slice(document.offsetAt(edit.range.end)), document.getText());
            };
            const expected = data.source.replace('\nInt', `\n${data.expectedIndent}Int`);
            assert.strictEqual(await formatted(), expected);
            await config.update('indentSize', 0, vscode.ConfigurationTarget.Workspace);
            await client().sendNotification('workspace/didChangeConfiguration', { settings: {} });
            assert.strictEqual(await formatted(), expected);
            const after = await client().sendRequest<{ pid: number; serverSaveFormatting: boolean }>('xtc/languageServiceStatus');
            assert.strictEqual(after.pid, before.pid);
            assert.strictEqual(after.serverSaveFormatting, false);
            assert.strictEqual(document.getText(), data.source);
        } finally { await config.update('indentSize', original, vscode.ConfigurationTarget.Workspace); }
    });

    playbook('X139', async (workspace, data) => {
        const document = await workspace.open(data.file, data.source);
        const config = vscode.workspace.getConfiguration('xtc.languageService');
        const editor = vscode.workspace.getConfiguration('editor', { uri: document.uri, languageId: 'xtc' });
        const previousOwner = config.inspect('saveFormatting')?.workspaceValue;
        const previousNative = editor.inspect('formatOnSave')?.workspaceValue;
        const running = async () => {
            const current = getClient();
            if (!current?.isRunning()) return undefined;
            try { return await current.sendRequest<{ serverSaveFormatting: boolean }>('xtc/languageServiceStatus'); }
            catch { return undefined; }
        };
        try {
            await editor.update('formatOnSave', false, vscode.ConfigurationTarget.Workspace);
            await config.update('saveFormatting', 'server', vscode.ConfigurationTarget.Workspace);
            await eventually(running, value => value?.serverSaveFormatting === true, 'Server save edits enabled');
            await workspace.replace(document, data.source + '\n');
            await document.save();
            assert.ok(document.getText().includes('    Int value = 1;'));
            await editor.update('formatOnSave', true, vscode.ConfigurationTarget.Workspace);
            const middleware = client().clientOptions.middleware!.willSaveWaitUntil!;
            assert.deepStrictEqual(await middleware({ document, reason: vscode.TextDocumentSaveReason.Manual, waitUntil: () => {} }, () => { throw new Error('Native format-on-save must suppress the server hook'); }), []);
            await workspace.replace(document, data.source);
            await document.save();
            assert.strictEqual(document.getText(), data.formatted);
        } finally {
            await editor.update('formatOnSave', previousNative, vscode.ConfigurationTarget.Workspace);
            await config.update('saveFormatting', previousOwner, vscode.ConfigurationTarget.Workspace);
            await eventually(running, value => value?.serverSaveFormatting === false, 'Editor save ownership restored');
        }
    });

    playbook('X140', async (workspace, data) => {
        const document = await workspace.open(data.file, data.source);
        await noErrors(document.uri);
        assert.strictEqual(client().initializeResult!.capabilities.positionEncoding, 'utf-16');
        const offset = data.source.lastIndexOf(data.anchor);
        const at = document.positionAt(offset);
        assert.ok((await hover(document, at)).includes(data.expected));
        const prepared = await client().sendRequest<{ range: { start: { character: number }; end: { character: number } } }>('textDocument/prepareRename', {
            textDocument: { uri: document.uri.toString() }, position: at,
        });
        assert.strictEqual(prepared.range.start.character, offset);
        assert.strictEqual(prepared.range.end.character, offset + data.anchor.length);
    });

    playbook('X141', async (workspace, data) => {
        const document = await workspace.open(data.file, data.source);
        await noErrors(document.uri);
        const messages: { message: string; verbose?: string }[] = [];
        const registration = client().onNotification('$/logTrace', value => { messages.push(value); });
        try {
            for (const level of data.levels) {
                await client().sendNotification('$/setTrace', { value: level });
                const before = messages.length;
                await client().sendRequest('textDocument/hover', {
                    textDocument: { uri: document.uri.toString() },
                    position: document.positionAt(data.source.indexOf(data.anchor)),
                });
                const trace = await eventually(async () => messages.slice(before).find(value => value.message.startsWith('textDocument/hover:')),
                    value => !!value, 'Server runtime trace');
                assert.strictEqual(typeof trace!.verbose === 'string', level === 'verbose');
                assert.ok(!JSON.stringify(trace).includes(data.source.trim()));
            }
        } finally {
            registration.dispose();
            // Reconnect to restore the language client's own logTrace handler after this observer.
            await vscode.commands.executeCommand('xtc.restartServer');
        }
    });

    playbook('X142', async (workspace, data) => {
        await workspace.write(data.file, data.source);
        await workspace.configure([{ name: data.module, uri: workspace.uri(data.file).toString() }]);
        const document = await workspace.open(data.file);
        await noErrors(document.uri);
        const at = document.positionAt(data.source.indexOf(data.anchor));
        assert.ok((await hover(document, at)).includes(data.expected));
        const definitions = await vscode.commands.executeCommand<vscode.Location[]>('vscode.executeDefinitionProvider', document.uri, at);
        assert.ok(definitions?.some(value => value.range.start.character === data.source.indexOf(data.name)));
        const references = await vscode.commands.executeCommand<vscode.Location[]>('vscode.executeReferenceProvider', document.uri, at);
        assert.strictEqual(references?.length, data.references);
        const edit = await vscode.commands.executeCommand<vscode.WorkspaceEdit>('vscode.executeDocumentRenameProvider', document.uri, at, data.newName);
        assert.ok(edit && await vscode.workspace.applyEdit(edit));
        assert.strictEqual(document.getText(), data.source.split(data.name).join(data.newName));
        await noErrors(document.uri);
        await vscode.commands.executeCommand('undo');
        assert.strictEqual(document.getText(), data.source);
    });

    playbook('X143', async (workspace, data) => {
        const source = `module ${data.module} {\n${Array.from({ length: data.declarations }, (_, i) => `    class ${data.prefix}${i} {}`).join('\n')}\n}\n`;
        await workspace.write(data.file, source);
        await workspace.configure([{ name: data.module, uri: workspace.uri(data.file).toString() }]);
        const document = await workspace.open(data.file);
        await noErrors(document.uri);
        await symbols(document);
        const batches: { name: string }[][] = [];
        const observer = client().onProgress(new ProgressType<{ name: string }[]>(), data.token, values => { batches.push(values); });
        try {
            const normal = await client().sendRequest<{ name: string }[]>('workspace/symbol', { query: data.prefix });
            const final = await client().sendRequest<{ name: string }[]>('workspace/symbol', { query: data.prefix, partialResultToken: data.token });
            assert.deepStrictEqual(final, []);
            assert.ok(batches.length > 1 && batches.every(values => values.length <= data.batchSize));
            assert.strictEqual(normal.length, data.declarations);
            assert.deepStrictEqual(batches.flat().map(value => value.name), normal.map(value => value.name));
        } finally { observer.dispose(); }
    });

}
