import * as assert from 'node:assert';
import * as vscode from 'vscode';
import { editScenario, scenarioOffset, scenarioRegex, shared } from './shared';
import { diagnostics, diagnosticCode, eventually, fixture, hover, nextProblem, noErrors, playbook, position, symbolNames, symbols, targetNames, targets } from './support';

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

    playbook('X2', async workspace => {
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
            assert.ok(errors.every(item => !item.range.isEmpty && item.source === 'Ecstasy'));
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
        assert.deepStrictEqual(await targets(document, 'Declaration', local), localDefinition);
        assert.deepStrictEqual(await targets(document, 'Declaration', property), propertyDefinition);
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
        assert.deepStrictEqual(await targetNames(await targets(document, 'Definition', position(document, data.libraryType))), [data.libraryTarget]);
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

/**
 * X350, registered after X349 in catalog order: in a bundled library's source, which Go to Definition opened, Go to
 * Definition on a call goes to the method another source of the library declares, which only the library's whole
 * compile resolves.
 */
export function libraryNavigationCases(): void {
    playbook('X350', async (workspace, data) => {
        const document = await workspace.open(data.file, data.source);
        await noErrors(document.uri);
        // as a user opens it: Go to Definition on a library type opens the library's source, read-only, at its declaration
        const library = await revealDefinition(document, position(document, data.libraryType), data.librarySource);
        assert.strictEqual(library.document.uri.scheme, 'ecstasy-library', 'Bundled source uses the read-only content provider');
        const declared = library.document.lineAt(library.selection.start.line).text;
        assert.ok(declared.startsWith(data.libraryDeclaration), `${data.libraryType} opens at ${JSON.stringify(declared)}`);

        // a call in a method body to a method of another source of the library, which the copy compiled on its own,
        // with only its imports and type names, cannot resolve; the query waits for the library's compile
        const call = position(library.document, data.call, data.callOffset);
        const found = await targets(library.document, 'Definition', call);
        assert.strictEqual(found.length, 1, `Definition of ${data.target} in ${library.document.uri}: ${JSON.stringify(found)}`);
        assert.ok(found[0].uri.path.endsWith(`/${data.targetSource}`), found[0].uri.toString());
        assert.strictEqual(found[0].uri.scheme, 'ecstasy-library', 'The method is in the library source, read-only too');
        assert.deepStrictEqual(await targetNames(found), [data.target]);
        const target = await vscode.workspace.openTextDocument(found[0].uri);
        assert.ok(target.lineAt(found[0].range.start.line).text.includes(data.targetDeclaration), target.lineAt(found[0].range.start.line).text);
        const opened = await revealDefinition(library.document, call, data.targetSource);
        assert.deepStrictEqual([opened.selection.start.line, opened.selection.start.character],
            [found[0].range.start.line, found[0].range.start.character], 'Go to Definition opens the declaration');

        // hover, which also needs the whole compile: the copy on its own describes only its own declarations
        const shown = await hover(library.document, call);
        assert.ok(shown.includes(data.hover), `Hover of ${data.target}: ${shown}`);
    });
}

/** Go to Definition, as F12 runs it, at [at] in [document]; the editor it opens on [source], a path's end. */
async function revealDefinition(document: vscode.TextDocument, at: vscode.Position, source: string): Promise<vscode.TextEditor> {
    await vscode.window.showTextDocument(document, { selection: new vscode.Range(at, at), preview: false });
    await vscode.commands.executeCommand('editor.action.revealDefinition');
    const editor = await eventually(async () => vscode.window.activeTextEditor,
        active => active?.document.uri.path.endsWith(`/${source}`) === true, `Go to Definition opens ${source}`);
    assert.ok(editor);
    return editor;
}
