import * as assert from 'node:assert';
import * as vscode from 'vscode';
import { client, diagnostics, noErrors, playbook, position, symbolNames, symbols } from './support';

export function editingClosureCases(ids: readonly ('X158' | 'X243' | 'X244')[] = ['X158']): void {
    ids.forEach(id => playbook(id, async (workspace, data) => {
        await workspace.write(data.libraryFile, data.library);
        await workspace.write(data.file, data.source);
        await workspace.configure([
            { name: data.libraryModule, uri: workspace.uri(data.libraryFile).toString() },
            { name: data.module, uri: workspace.uri(data.file).toString(), dependencies: [data.libraryModule] }
        ]);
        const document = await workspace.open(data.file);
        if (id === 'X244') {
            await diagnostics(document.uri, values => values.some(item => item.severity === vscode.DiagnosticSeverity.Error), 'Unsupported conditional import syntax');
        } else {
            await noErrors(document.uri);
        }
        const links = await vscode.commands.executeCommand<vscode.DocumentLink[]>('vscode.executeLinkProvider', document.uri, 100);
        assert.deepStrictEqual(links?.map(link => document.getText(link.range)), data.linkNames);
        assert.ok(links?.every(link => link.target?.fsPath === workspace.uri(data.libraryFile).fsPath));
        if (data.alias) {
            const result = await client().sendRequest<import('vscode-languageclient/node').LinkedEditingRanges>('textDocument/linkedEditingRange', {
                textDocument: { uri: document.uri.toString() }, position: position(document, data.alias)
            });
            assert.deepStrictEqual(result.ranges.map(range => document.offsetAt(new vscode.Position(range.start.line, range.start.character))),
                data.aliasUses.map(anchor => data.source.indexOf(anchor)));
        }
        if (links?.length) {
            const library = await vscode.workspace.openTextDocument(links[0].target!);
            await vscode.window.showTextDocument(library);
            assert.strictEqual(library.getText(), data.library);
        }
    }));
}

export function linkedScopeCases(): void {
    (['X245', 'X246', 'X247'] as const).forEach(id => playbook(id, async (workspace, data) => {
        const document = await workspace.open(data.file, data.source);
        await noErrors(document.uri);
        for (const query of data.queries) {
            const result = await client().sendRequest<import('vscode-languageclient/node').LinkedEditingRanges | null>('textDocument/linkedEditingRange', {
                textDocument: { uri: document.uri.toString() }, position: position(document, query.anchor)
            });
            const starts = result?.ranges.map(range => document.offsetAt(new vscode.Position(range.start.line, range.start.character))) ?? [];
            assert.deepStrictEqual(starts, query.occurrences.map(anchor => data.source.indexOf(anchor)));
            assert.strictEqual(document.getText(), data.source);
        }
    }));
}

export function structuralRecoveryCases(): void {
    playbook('X248', async (workspace, data) => {
        const document = await workspace.open(data.file, data.variants[0].source);
        for (const variant of data.variants) {
            await workspace.replace(document, variant.source);
            await diagnostics(document.uri, values => values.some(item => item.severity === vscode.DiagnosticSeverity.Error), 'Current damaged source diagnostics');
            const names = symbolNames(await symbols(document));
            assert.ok(variant.symbols.every(name => names.includes(name)));
            const folds = await vscode.commands.executeCommand<vscode.FoldingRange[]>('vscode.executeFoldingRangeProvider', document.uri);
            assert.ok(folds?.some(fold => fold.start === variant.foldLine && fold.end >= variant.foldLine + 1));
            const at = position(document, variant.anchor);
            const ranges = await vscode.commands.executeCommand<vscode.SelectionRange[]>('vscode.executeSelectionRangeProvider', document.uri, [at]);
            let selection: vscode.SelectionRange | undefined = ranges?.[0];
            assert.ok(selection?.parent);
            while (selection) {
                assert.ok(selection.range.contains(at));
                if (selection.parent) {
                    assert.ok(selection.parent.range.contains(selection.range));
                    assert.ok(!selection.parent.range.isEqual(selection.range));
                }
                selection = selection.parent;
            }
        }
        await workspace.replace(document, data.repaired);
        await noErrors(document.uri);
        assert.deepStrictEqual(symbolNames(await symbols(document)), ['Structure', 'repaired']);
    });
}

export function formattingBreadthCases(): void {
    (['X249', 'X250'] as const).forEach(id => playbook(id, async (workspace, data) => {
        const document = await workspace.open(data.file, data.source);
        await noErrors(document.uri);
        await vscode.commands.executeCommand('editor.action.formatDocument');
        assert.strictEqual(document.getText(), data.expected);
        await noErrors(document.uri);
        const edits = await client().sendRequest('textDocument/formatting', {
            textDocument: { uri: document.uri.toString() },
            options: { tabSize: 4, insertSpaces: true, insertFinalNewline: true }
        });
        assert.deepStrictEqual(edits, []);
        await vscode.commands.executeCommand('editor.action.formatDocument');
        assert.strictEqual(document.getText(), data.expected);
        await vscode.commands.executeCommand('undo');
        assert.strictEqual(document.getText(), data.source);
        await vscode.commands.executeCommand('redo');
        assert.strictEqual(document.getText(), data.expected);
        await vscode.commands.executeCommand('undo');
        assert.strictEqual(document.getText(), data.source);
    }));
}
