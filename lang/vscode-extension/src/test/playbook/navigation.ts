import * as assert from 'node:assert';
import * as vscode from 'vscode';
import { editScenario, scenarioOffset, scenarioRegex, shared } from './shared';
import { diagnostics, diagnosticCode, fixture, hover, nextProblem, noErrors, playbook, position, symbolNames, symbols, targets } from './support';

export function navigationCases(): void {
    playbook('X1', async (workspace, data) => {
        const document = await workspace.open(data.file);
        await noErrors(document.uri);
        const names = symbolNames(await symbols(document));
        for (const name of data.symbolNames) {
            assert.ok(names.some(value => value.includes(name)), `${name} in outline: ${names}`);
        }
        const folds = await vscode.commands.executeCommand<vscode.FoldingRange[]>('vscode.executeFoldingRangeProvider', document.uri);
        assert.ok(folds && folds.length >= data.minimumFolds);
        const at = position(document, data.anchor, data.offset);
        const selections = await vscode.commands.executeCommand<vscode.SelectionRange[]>('vscode.executeSelectionRangeProvider', document.uri, [at]);
        assert.ok(selections?.[0]?.parent);
        assert.ok(selections[0].range.contains(at));
        assert.ok(selections[0].parent.range.contains(selections[0].range));
    });

    playbook('X2', async (workspace, data) => {
        const scenario = shared.diagnostics;
        const document = await workspace.open(scenario.file);
        for (const expected of scenario.errors) {
            const text = editScenario(fixture(scenario.file), expected.edit);
            await workspace.replace(document, text);
            const errors = await diagnostics(document.uri, values => values.some(item => vscode.DiagnosticSeverity[item.severity].toUpperCase() === expected.severity), 'Expected compiler errors');
            assert.ok(errors.every(item => diagnosticCode(item).startsWith(expected.codePrefix)));
            assert.ok(errors.some(item => item.message.toLowerCase().includes(expected.messageContains.toLowerCase())));
            const start = scenarioOffset(text, expected.rangeStart);
            const end = scenarioOffset(text, expected.rangeEnd);
            assert.ok(errors.some(item => document.offsetAt(item.range.start) >= start && document.offsetAt(item.range.start) <= end));
            assert.ok(errors.every(item => item.range.start.line > 0));
            assert.ok(errors.every(item => !item.range.isEmpty && item.source === 'xtc'));
            await nextProblem(document, errors);
            await workspace.replace(document, fixture(scenario.file));
            await noErrors(document.uri);
        }
        await workspace.replace(document, fixture(scenario.file) + scenario.cleanAppend);
        await noErrors(document.uri);
    });

    playbook('X3', async (workspace, data) => {
        const document = await workspace.open(data.file);
        const narrowed = position(document, data.narrowUse, data.offset);
        const original = position(document, data.wideUse, data.offset);
        assert.match(await hover(document, narrowed), scenarioRegex(data.narrowedType));
        assert.match(await hover(document, original), scenarioRegex(data.declaredType));
        const a = await targets(document, 'Definition', narrowed);
        const b = await targets(document, 'Definition', original);
        assert.strictEqual(a.length, data.targetCount);
        assert.deepStrictEqual(a, b);
        assert.strictEqual(a[0].range.start.line, position(document, data.declaration, data.offset).line);
    });

    playbook('X4', async (workspace, data) => {
        const scenario = shared.definitions;
        const document = await workspace.open(scenario.file);
        const [local, property] = scenario.locations.map(location => document.positionAt(scenarioOffset(document.getText(), location.cursor)));
        const localDefinition = await targets(document, 'Definition', local);
        const propertyDefinition = await targets(document, 'Definition', property);
        assert.strictEqual(localDefinition.length, data.targetCount);
        assert.strictEqual(propertyDefinition.length, data.targetCount);
        assert.ok(!localDefinition[0].range.isEqual(propertyDefinition[0].range));
        for (const [index, definition] of [localDefinition[0], propertyDefinition[0]].entries()) {
            const expected = scenario.locations[index];
            assert.strictEqual(definition.uri.toString(), workspace.uri(expected.targetFile).toString());
            assert.strictEqual(document.offsetAt(definition.range.start), scenarioOffset(fixture(expected.targetFile), expected.target));
        }
        for (const [at, excluded] of [[local, property], [property, local]]) {
            const references = await vscode.commands.executeCommand<vscode.Location[]>('vscode.executeReferenceProvider', document.uri, at);
            assert.ok(references?.some(item => item.range.contains(at)));
            assert.ok(references.every(item => !item.range.contains(excluded)));
            const highlights = await vscode.commands.executeCommand<vscode.DocumentHighlight[]>('vscode.executeDocumentHighlights', document.uri, at);
            assert.ok(highlights?.some(item => item.range.contains(at)));
            assert.ok(highlights.every(item => !item.range.contains(excluded)));
        }
    });

    playbook('X5', async (workspace, data) => {
        const document = await workspace.open(data.file);
        assert.deepStrictEqual(await targets(document, 'Definition', position(document, data.libraryType)), []);
        await workspace.replace(document, fixture(data.file).slice(0, fixture(data.file).lastIndexOf(data.missingCloser)));
        await diagnostics(document.uri, values => values.some(item => diagnosticCode(item).startsWith(data.parserCodePrefix)), 'Parser recovery diagnostic');
        assert.ok(symbolNames(await symbols(document)).some(name => name.includes(data.retainedSymbol)));
        assert.ok((await vscode.commands.executeCommand<vscode.FoldingRange[]>('vscode.executeFoldingRangeProvider', document.uri))?.length);
        const at = position(document, data.anchor, data.offset);
        assert.ok((await vscode.commands.executeCommand<vscode.SelectionRange[]>('vscode.executeSelectionRangeProvider', document.uri, [at]))?.length);
        assert.deepStrictEqual(await targets(document, 'Definition', at), []);
        await workspace.replace(document, fixture(data.file));
        await noErrors(document.uri);
    });
}
