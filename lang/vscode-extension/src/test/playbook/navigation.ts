import * as assert from 'node:assert';
import * as vscode from 'vscode';
import { diagnostics, diagnosticCode, fixture, hover, nextProblem, noErrors, playbook, position, symbolNames, symbols, targets } from './support';

export function navigationCases(): void {
    playbook('X1', 'diagnostics, outline, folding and selection', async workspace => {
        const document = await workspace.open('Navigation.x');
        await noErrors(document.uri);
        const names = symbolNames(await symbols(document));
        for (const name of ['Navigation', 'Holder', 'value', 'read', 'text']) {
            assert.ok(names.some(value => value.includes(name)), `${name} in outline: ${names}`);
        }
        const folds = await vscode.commands.executeCommand<vscode.FoldingRange[]>('vscode.executeFoldingRangeProvider', document.uri);
        assert.ok(folds && folds.length >= 3);
        const at = position(document, 'return value;', 7);
        const selections = await vscode.commands.executeCommand<vscode.SelectionRange[]>('vscode.executeSelectionRangeProvider', document.uri, [at]);
        assert.ok(selections?.[0]?.parent);
        assert.ok(selections[0].range.contains(at));
        assert.ok(selections[0].parent.range.contains(selections[0].range));
    }, ['Outline/folding rendering and Expand Selection keyboard behavior']);

    playbook('X2', 'source errors clear after unsaved corrections', async workspace => {
        const document = await workspace.open('Navigation.x');
        for (const text of [fixture('Navigation.x').replace('Int value = 2;', 'String value = 2;'),
            fixture('Navigation.x').replace('Object value = input;', 'Object value = missing;')]) {
            await workspace.replace(document, text);
            const errors = await diagnostics(document.uri, values => values.some(item => item.severity === vscode.DiagnosticSeverity.Error), 'Expected compiler errors');
            assert.ok(errors.every(item => /^COMPILER-/.test(diagnosticCode(item))));
            assert.ok(errors.every(item => item.range.start.line > 0));
            assert.ok(errors.every(item => !item.range.isEmpty && item.source === 'xtc'));
            await nextProblem(document, errors);
            await workspace.replace(document, fixture('Navigation.x'));
            await noErrors(document.uri);
        }
        await workspace.replace(document, fixture('Navigation.x') + '// ERROR: test\n');
        await noErrors(document.uri);
    }, ['Problems rows/icons/filter/count rendering and clicking a row; automated next-problem navigation is checked']);

    playbook('X3', 'narrowed hover preserves declaration identity', async workspace => {
        const document = await workspace.open('Navigation.x');
        const narrowed = position(document, 'return value;', 7);
        const original = position(document, 'return value.toString()', 7);
        assert.match(await hover(document, narrowed), /String/);
        assert.match(await hover(document, original), /Object/);
        const a = await targets(document, 'Definition', narrowed);
        const b = await targets(document, 'Definition', original);
        assert.strictEqual(a.length, 1);
        assert.deepStrictEqual(a, b);
        assert.strictEqual(a[0].range.start.line, position(document, 'Object value = input', 7).line);
    }, ['Hover popup presentation']);

    playbook('X4', 'local and property references remain separate', async workspace => {
        const document = await workspace.open('Navigation.x');
        const local = position(document, 'return value + this.value', 7);
        const property = position(document, 'this.value', 5);
        const localDefinition = await targets(document, 'Definition', local);
        const propertyDefinition = await targets(document, 'Definition', property);
        assert.strictEqual(localDefinition.length, 1);
        assert.strictEqual(propertyDefinition.length, 1);
        assert.ok(!localDefinition[0].range.isEqual(propertyDefinition[0].range));
        for (const [at, excluded] of [[local, property], [property, local]]) {
            const references = await vscode.commands.executeCommand<vscode.Location[]>('vscode.executeReferenceProvider', document.uri, at);
            assert.ok(references?.some(item => item.range.contains(at)));
            assert.ok(references.every(item => !item.range.contains(excluded)));
            const highlights = await vscode.commands.executeCommand<vscode.DocumentHighlight[]>('vscode.executeDocumentHighlights', document.uri, at);
            assert.ok(highlights?.some(item => item.range.contains(at)));
            assert.ok(highlights.every(item => !item.range.contains(excluded)));
        }
    });

    playbook('X5', 'library target absence and parser recovery', async workspace => {
        const document = await workspace.open('Navigation.x');
        assert.deepStrictEqual(await targets(document, 'Definition', position(document, 'String text')), []);
        await workspace.replace(document, fixture('Navigation.x').slice(0, fixture('Navigation.x').lastIndexOf('}')));
        await diagnostics(document.uri, values => values.some(item => diagnosticCode(item).startsWith('PARSER-')), 'Parser recovery diagnostic');
        assert.ok(symbolNames(await symbols(document)).some(name => name.includes('Holder')));
        assert.ok((await vscode.commands.executeCommand<vscode.FoldingRange[]>('vscode.executeFoldingRangeProvider', document.uri))?.length);
        const at = position(document, 'return value;', 7);
        assert.ok((await vscode.commands.executeCommand<vscode.SelectionRange[]>('vscode.executeSelectionRangeProvider', document.uri, [at]))?.length);
        assert.deepStrictEqual(await targets(document, 'Definition', at), []);
        await workspace.replace(document, fixture('Navigation.x'));
        await noErrors(document.uri);
    });
}
