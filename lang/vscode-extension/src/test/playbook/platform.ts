import * as assert from 'node:assert';
import * as fs from 'node:fs/promises';
import * as os from 'node:os';
import * as path from 'node:path';
import * as vscode from 'vscode';
import { CodeAction } from 'vscode-languageclient/node';
import { modelPath } from '../../build-model';
import { updateCompilerConfiguration } from '../../lsp-client';
import { client, diagnostics, eventually, label, noErrors, playbook, targets } from './support';

export function platformCases(): void {
    playbook('X129', async (workspace, data) => {
        const report = vscode.Uri.joinPath(vscode.workspace.workspaceFolders![0].uri, modelPath);
        const model = JSON.parse(JSON.stringify(data.model).replaceAll('${workspace}', vscode.Uri.file(workspace.directory).toString()));
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
            assert.deepStrictEqual(settings.get('sourceModules'), override);
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

    playbook('X124', async (workspace, data) => {
        const external = await fs.mkdtemp(path.join(os.tmpdir(), 'xtc-playbook-resources-'));
        try {
            await workspace.write(data.file, data.text);
            const modules = [{ name: data.module, uri: workspace.uri(data.file).toString(), resourceRoots: [vscode.Uri.file(external).toString()] }];
            await workspace.configure(modules);
            const document = await workspace.open(data.file);
            await diagnostics(document.uri, values => values.length > 0, 'Missing external resource');
            await fs.writeFile(path.join(external, data.resource), data.contents);
            await noErrors(document.uri);
            await workspace.configure([{ ...modules[0], resourceRoots: [] }]);
            await diagnostics(document.uri, values => values.length > 0, 'Explicitly disabled resources');
            await workspace.configure(modules);
            await noErrors(document.uri);
            assert.deepStrictEqual(vscode.workspace.getConfiguration('xtc.compiler').get('sourceModules'), modules);
            await fs.unlink(path.join(external, data.resource));
            await diagnostics(document.uri, values => values.length > 0, 'Deleted external resource');
            await fs.writeFile(path.join(external, data.resource), data.contents);
            await noErrors(document.uri);
        } finally { await fs.rm(external, { recursive: true, force: true }); }
    });

    playbook('X125', async (workspace, data) => {
        const document = await workspace.open(data.file, data.hoverSource);
        await noErrors(document.uri);
        const hover = await vscode.commands.executeCommand<vscode.Hover[]>('vscode.executeHoverProvider', document.uri, document.positionAt(data.hoverSource.lastIndexOf(data.hoverAnchor)));
        assert.ok(JSON.stringify(hover).includes(data.hoverExpected));
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

}
