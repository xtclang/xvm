import * as assert from 'node:assert';
import * as vscode from 'vscode';
import { CallHierarchyItem, CallHierarchyOutgoingCall } from 'vscode-languageclient/node';
import { scenarioRegex, scenarioText } from './shared';
import { client, diagnostics, fixture, label, noErrors, playbook, position, symbolNames, symbols, targets } from './support';

export function advancedCases(): void {
    playbook('X68', async (workspace, data) => {
        const document = await workspace.open(data.file);
        await noErrors(document.uri);
        for (const { use, offset, declaration, declarationOffset, length } of data.variants) {
            const found = await targets(document, 'Implementation', position(document, use, offset));
            const at = position(document, declaration, declarationOffset);
            assert.strictEqual(found.length, data.targetCount);
            assert.strictEqual(found[0].uri.toString(), document.uri.toString());
            assert.ok(found[0].range.isEqual(new vscode.Range(at, at.translate(0, length))));
        }
    });

    playbook('X69', async (workspace, data) => {
        const document = await workspace.open(data.file);
        await noErrors(document.uri);
        const found = await targets(document, 'Definition', position(document, data.anchor));
        const at = position(document, data.inheritedDeclaration, data.inheritedOffset);
        assert.strictEqual(found.length, data.targetCount);
        assert.ok(found[0].range.isEqual(new vscode.Range(at, at.translate(0, data.targetLength))));
        const items = await client().sendRequest<CallHierarchyItem[]>('textDocument/prepareCallHierarchy', {
            textDocument: { uri: document.uri.toString() }, position: position(document, data.override, data.overrideOffset)
        });
        assert.strictEqual(items.length, data.targetCount);
        const outgoing = await client().sendRequest<CallHierarchyOutgoingCall[]>('callHierarchy/outgoingCalls', { item: items[0] });
        assert.strictEqual(outgoing.length, data.targetCount);
        assert.strictEqual(outgoing[0].to.selectionRange.start.line, at.line);
        assert.strictEqual(outgoing[0].to.selectionRange.start.character, at.character);
        await assert.rejects(client().sendRequest('textDocument/prepareRename', {
            textDocument: { uri: document.uri.toString() }, position: position(document, data.anchor)
        }), { message: data.renameRejection });
    });

    playbook('X70', async (workspace, data) => {
        const document = await workspace.open(data.file);
        await noErrors(document.uri);
        const help = await workspace.signature(document, position(document, data.anchor, data.offset));
        assert.strictEqual(help?.signatures[0].label, data.signature);
        const found = await targets(document, 'Definition', position(document, data.anchor));
        assert.strictEqual(found.length, data.targetCount);
        assert.strictEqual(document.getText(found[0].range), data.targetName);
    });

    playbook('X71', async (workspace, data) => {
        const document = await workspace.open(data.file);
        await noErrors(document.uri);
        for (const expression of data.variants) {
            await workspace.replace(document, fixture(data.file).replace(data.replaceFrom, scenarioText(data.replaceWith, expression)));
            const found = await workspace.completion(document, position(document, data.anchor, data.offset));
            assert.ok(found.some(item => item.label === data.label), expression);
        }
        await workspace.replace(document, fixture(data.file));
        await noErrors(document.uri);
    });

    playbook('X72', async (workspace, data) => {
        const document = await workspace.open(data.file);
        await noErrors(document.uri);
        for (const { use, getter, getterOffset, setter, setterOffset } of data.variants) {
            const found = await targets(document, 'Implementation', position(document, use, data.offset));
            assert.strictEqual(found.length, data.targetCount);
            for (const at of [position(document, getter, getterOffset), position(document, setter, setterOffset)]) {
                const range = new vscode.Range(at, at.translate(0, data.targetLength));
                assert.ok(found.some(target => target.uri.toString() === document.uri.toString() && target.range.isEqual(range)), use);
            }
        }
    });

    playbook('X73', async (workspace, data) => {
        const document = await workspace.open(data.file);
        await noErrors(document.uri);
        for (const { call, active } of data.variants) {
            await workspace.replace(document, fixture(data.file).replace(data.replaceFrom, scenarioText(data.incompleteExpression, call)));
            const anchor = scenarioText(data.callAnchor, call);
            const help = await workspace.signature(document, position(document, anchor, anchor.length));
            assert.strictEqual(help?.signatures[0].label, data.signature);
            assert.strictEqual(help?.signatures[0].activeParameter, active);
        }
        await workspace.replace(document, fixture(data.file).replace(data.replaceFrom, data.invalidExpression));
        const rejected = await workspace.signature(document, position(document, data.invalidCall, data.offset));
        assert.ok(!rejected || rejected.signatures.length === data.rejectedSignatureCount);
        await workspace.replace(document, fixture(data.file));
        await noErrors(document.uri);
    });

    playbook('X74', async (workspace, data) => {
        const document = await workspace.open(data.file);
        await noErrors(document.uri);
        for (const { call, active } of data.variants) {
            await workspace.replace(document, fixture(data.file).replace(data.replaceFrom, scenarioText(data.replaceWith, call)));
            const help = await workspace.signature(document, position(document, call, call.length));
            assert.strictEqual(help?.signatures[0].label, data.signature);
            assert.strictEqual(help?.signatures[0].activeParameter, active);
        }
        await workspace.replace(document, fixture(data.file));
        await noErrors(document.uri);
    });

    playbook('X75', async (workspace, data) => {
        const document = await workspace.open(data.file);
        await noErrors(document.uri);
        for (const expression of data.variants) {
            await workspace.replace(document, fixture(data.file).replace(data.replaceFrom, scenarioText(data.replaceWith, expression)));
            await diagnostics(document.uri, values => values.length > 0, 'Missing delimiter diagnostics');
            const found = await workspace.completion(document, position(document, data.anchor, data.offset));
            assert.ok(found.some(item => item.label === data.label), expression);
            assert.ok(vscode.languages.getDiagnostics(document.uri).length > 0, 'Cursor queries preserve diagnostics');
        }
        await workspace.replace(document, fixture(data.file));
        await noErrors(document.uri);
    });

    playbook('X76', async (workspace, data) => {
        const document = await workspace.open(data.file);
        await noErrors(document.uri);
        const expression = data.expression;
        await workspace.replace(document, fixture(data.file).replace(data.replaceFrom, scenarioText(data.replaceWith, expression)));
        await diagnostics(document.uri, values => values.length > 0, 'Missing nested call diagnostics');
        const help = await workspace.signature(document, position(document, expression, expression.length));
        assert.strictEqual(help?.signatures[0].label, data.signature);
        assert.strictEqual(help?.signatures[0].activeParameter, data.activeParameter);
        await workspace.replace(document, fixture(data.file));
        await noErrors(document.uri);
    });

    playbook('X77', async (workspace, data) => {
        const document = await workspace.open(data.file);
        await noErrors(document.uri);
        for (const { original, prefix } of data.variants) {
            await workspace.replace(document, fixture(data.file).replace(original, scenarioText(data.replaceWith, prefix)));
            await diagnostics(document.uri, values => values.length > 0, 'Missing argument diagnostics');
            const at = position(document, prefix, prefix.length, true);
            const items = (await workspace.completion(document, at)).filter(item => item.kind !== vscode.CompletionItemKind.Snippet);
            assert.deepStrictEqual(items.map(item => item.label).sort(), data.labels, prefix);
            const selected = items.find(item => item.label === data.label)!;
            assert.ok(selected.range instanceof vscode.Range && selected.range.isEqual(new vscode.Range(at, at)));
            await workspace.accept(document, selected);
            await noErrors(document.uri);
        }
        await workspace.replace(document, fixture(data.file));
        await noErrors(document.uri);
    });

    playbook('X78', async (workspace, data) => {
        const document = await workspace.open(data.file);
        await noErrors(document.uri);
        for (const selected of data.selections) {
            await workspace.replace(document, fixture(data.file).replace(data.replaceFrom, data.anchor));
            await diagnostics(document.uri, values => values.length > 0, 'Missing overloaded argument diagnostics');
            const at = position(document, data.anchor, data.offset);
            const items = (await workspace.completion(document, at)).filter(item => item.kind !== vscode.CompletionItemKind.Snippet);
            assert.deepStrictEqual(items.map(item => item.label).sort(), data.labels);
            assert.strictEqual((await workspace.signature(document, at))?.signatures.length, data.signatureCount);
            await workspace.accept(document, items.find(item => item.label === selected)!);
            await noErrors(document.uri);
        }
        await workspace.replace(document, fixture(data.file));
        await noErrors(document.uri);
    });

    playbook('X79', async (workspace, data) => {
        const document = await workspace.open(data.file);
        await noErrors(document.uri);
        for (const { original, prefix } of data.variants) {
            await workspace.replace(document, fixture(data.file).replace(original, scenarioText(data.replaceFrom, prefix)));
            await diagnostics(document.uri, values => values.length > 0, 'Unresolved argument prefix diagnostics');
            const at = position(document, prefix, prefix.length, true);
            const items = (await workspace.completion(document, at)).filter(item => item.kind !== vscode.CompletionItemKind.Snippet);
            assert.deepStrictEqual(items.map(item => item.label), data.labels, prefix);
            assert.ok(items[0].range instanceof vscode.Range && items[0].range.isEqual(new vscode.Range(at.translate(0, data.replacementStartDelta), at)));
            const before = document.getText();
            await workspace.accept(document, items[0]);
            assert.strictEqual(document.getText(), before.replace(scenarioText(data.replaceFrom, prefix), scenarioText(data.replaceWith, prefix.slice(0, -data.prefixLength))));
            await noErrors(document.uri);
        }
        await workspace.replace(document, fixture(data.file));
        await noErrors(document.uri);
    });

    playbook('X80', async (workspace, data) => {
        const document = await workspace.open(data.file);
        await noErrors(document.uri);
        for (const selected of data.labels) {
            await workspace.replace(document, fixture(data.file).replace(data.replaceFrom, data.anchor));
            await diagnostics(document.uri, values => values.length > 0, 'Unresolved overloaded prefix diagnostics');
            const at = position(document, data.anchor, data.offset);
            const items = (await workspace.completion(document, at)).filter(item => item.kind !== vscode.CompletionItemKind.Snippet);
            assert.deepStrictEqual(items.map(item => item.label).sort(), data.labels);
            assert.strictEqual((await workspace.signature(document, at))?.signatures.length, data.signatureCount);
            const chosen = items.find(item => item.label === selected)!;
            assert.ok(chosen.range instanceof vscode.Range && chosen.range.isEqual(new vscode.Range(at.translate(0, data.replacementStartDelta), at)));
            await workspace.accept(document, chosen);
            assert.ok(document.getText().includes(scenarioText(data.acceptedCall, selected)));
            await noErrors(document.uri);
        }
        await workspace.replace(document, fixture(data.file));
        await noErrors(document.uri);
    });

    playbook('X81', async (workspace, data) => {
        const document = await workspace.open(data.file);
        for (const prefix of data.variants) {
            await workspace.replace(document, fixture(data.file).replace(data.replaceFrom, scenarioText(data.replaceWith, prefix)));
            await diagnostics(document.uri, values => values.length > 0, 'Unresolved property argument prefix');
            const at = position(document, prefix, prefix.length);
            const items = (await workspace.completion(document, at)).filter(item => item.kind !== vscode.CompletionItemKind.Snippet);
            assert.deepStrictEqual(items.map(label).sort(), data.labels);
            const selected = items.find(item => label(item) === data.label)!;
            assert.strictEqual(selected.kind, vscode.CompletionItemKind.Property);
            assert.match(selected.detail ?? '', scenarioRegex(data.type));
            assert.ok(selected.range instanceof vscode.Range && selected.range.isEqual(new vscode.Range(at.translate(0, data.replacementStartDelta), at)));
            await workspace.accept(document, selected);
            await noErrors(document.uri);
        }
        await workspace.replace(document, fixture(data.file));
        await noErrors(document.uri);
    });

    playbook('X82', async (workspace, data) => {
        const document = await workspace.open(data.file);
        await workspace.replace(document, fixture(data.file).replace(data.replaceFrom, data.anchor));
        await diagnostics(document.uri, values => values.length > 0, 'Unresolved constant argument prefix');
        const at = position(document, data.anchor, data.offset);
        const items = (await workspace.completion(document, at)).filter(item => item.kind !== vscode.CompletionItemKind.Snippet);
        assert.deepStrictEqual(items.map(label), data.labels);
        assert.strictEqual(items[0].kind, vscode.CompletionItemKind.Property);
        await workspace.accept(document, items[0]);
        await noErrors(document.uri);
        assert.strictEqual(document.getText(), fixture(data.file));
    });

    playbook('X83', async (workspace, data) => {
        const document = await workspace.open(data.file);
        for (const original of data.variants) {
            const prefix = original.replace(data.replaceFrom, data.prefix);
            await workspace.replace(document, fixture(data.file).replace(original, scenarioText(data.replaceWith, prefix)));
            await diagnostics(document.uri, values => values.length > 0, 'Unresolved constructor prefix');
            const at = position(document, prefix, prefix.length);
            const help = await workspace.signature(document, at);
            assert.match(help?.signatures[0].label ?? '', scenarioRegex(data.signature));
            assert.strictEqual(help?.signatures[0].activeParameter ?? help?.activeParameter, data.activeParameter);
            const items = (await workspace.completion(document, at)).filter(item => item.kind !== vscode.CompletionItemKind.Snippet);
            assert.deepStrictEqual(items.map(label), data.labels);
            await workspace.accept(document, items[0]);
            assert.strictEqual(document.getText(), fixture(data.file));
            await noErrors(document.uri);
        }
    });

    playbook('X84', async (workspace, data) => {
        const document = await workspace.open(data.file);
        for (const original of data.constructors) {
            const prefix = original.replace(data.replaceFrom, data.prefix);
            await workspace.replace(document, fixture(data.file).replace(original, scenarioText(data.replaceWith, prefix)));
            await diagnostics(document.uri, values => values.length > 0, 'Unresolved inferred constructor prefix');
            const at = position(document, prefix, prefix.length);
            assert.match((await workspace.signature(document, at))?.signatures[0].label ?? '', scenarioRegex(data.signature));
            const items = (await workspace.completion(document, at)).filter(item => item.kind !== vscode.CompletionItemKind.Snippet);
            assert.deepStrictEqual(items.map(label).sort(), original.includes(data.requiredContext) ? data.requiredLabels : data.provisionalLabels);
            await workspace.accept(document, items.find(item => label(item) === data.label)!);
            await noErrors(document.uri);
        }
        await workspace.replace(document, fixture(data.file));
    });

    playbook('X85', async (workspace, data) => {
        const document = await workspace.open(data.file);
        for (const prefix of data.variants) {
            await workspace.replace(document, fixture(data.file).replace(data.replaceFrom, scenarioText(data.replaceWith, prefix)));
            await diagnostics(document.uri, values => values.length > 0, 'Unresolved array initializer');
            const at = position(document, prefix, prefix.length);
            const help = await workspace.signature(document, at);
            assert.strictEqual(help?.signatures[0].activeParameter ?? help?.activeParameter, data.activeParameter);
            assert.match(help?.signatures[0].label ?? '', scenarioRegex(data.parameter));
            const items = (await workspace.completion(document, at)).filter(item => item.kind !== vscode.CompletionItemKind.Snippet);
            assert.deepStrictEqual(items.map(label), data.labels);
            await workspace.accept(document, items[0]);
            await noErrors(document.uri);
        }
        await workspace.replace(document, fixture(data.file));
    });

    playbook('X86', async (workspace, data) => {
        const document = await workspace.open(data.file);
        for (const expression of data.variants) {
            await workspace.replace(document, fixture(data.file).replace(data.replaceFrom, expression));
            await diagnostics(document.uri, values => values.length > 0, 'Missing literal delimiter');
            const at = position(document, expression, expression.length);
            const item = (await workspace.completion(document, at)).find(item => label(item) === data.label);
            assert.ok(item);
            assert.ok(item.range instanceof vscode.Range && item.range.isEqual(new vscode.Range(at.translate(0, data.replacementStartDelta), at)));
            await workspace.accept(document, item);
            await diagnostics(document.uri, values => values.length > 0, 'Accepting a member must not repair delimiters');
            await workspace.replace(document, fixture(data.file));
            await noErrors(document.uri);
        }
    });

    playbook('X87', async (workspace, data) => {
        const document = await workspace.open(data.file);
        for (const { prefix, suffix, selected, terminator } of data.variants) {
            const text = fixture(data.file).replace(scenarioRegex(data.replaceFrom), scenarioText(data.replaceWith, prefix, suffix));
            await workspace.replace(document, text);
            await diagnostics(document.uri, values => values.length > 0, 'Incomplete declaration');
            const at = position(document, prefix, prefix.length);
            const item = (await workspace.completion(document, at)).find(item => label(item) === selected);
            assert.ok(item, prefix);
            await workspace.accept(document, item);
            await workspace.replace(document, text.replace(prefix, prefix.slice(0, -data.prefixLength) + selected + terminator));
            await noErrors(document.uri);
        }
        await workspace.replace(document, fixture(data.file));
        await noErrors(document.uri);
    });

    playbook('X88', async (workspace, data) => {
        const document = await workspace.open(data.file);
        const original = data.original;
        const prefix = data.prefix;
        await workspace.replace(document, fixture(data.file).replace(original, scenarioText(data.replaceWith, prefix)));
        await diagnostics(document.uri, values => values.length > 0, 'Unresolved anonymous constructor argument');
        const at = position(document, prefix, prefix.length);
        const help = await workspace.signature(document, at);
        assert.strictEqual(help?.signatures[0].label, data.signature);
        assert.strictEqual(help?.signatures[0].activeParameter ?? help?.activeParameter, data.activeParameter);
        const items = (await workspace.completion(document, at)).filter(item => item.kind !== vscode.CompletionItemKind.Snippet);
        assert.deepStrictEqual(items.map(label), data.labels);
        await workspace.accept(document, items[0]);
        assert.strictEqual(document.getText(), fixture(data.file));
        await noErrors(document.uri);
    });

    playbook('X89', async (workspace, data) => {
        const document = await workspace.open(data.file);
        const original = data.original;
        const prefix = data.prefix;
        for (const closer of data.closers) {
            await workspace.replace(document, fixture(data.file).replace(original, prefix + closer));
            await diagnostics(document.uri, values => values.length > 0, 'Incomplete anonymous interface construction');
            const at = position(document, prefix, prefix.length);
            const help = await workspace.signature(document, at);
            assert.strictEqual(help?.signatures[0].label, data.signature);
            assert.strictEqual(help?.signatures[0].activeParameter ?? help?.activeParameter, data.activeParameter);
            const items = (await workspace.completion(document, at)).filter(item => item.kind !== vscode.CompletionItemKind.Snippet);
            assert.deepStrictEqual(items.map(label), data.labels);
            assert.ok(items[0].range instanceof vscode.Range && items[0].range.isEqual(new vscode.Range(at.translate(0, data.replacementStartDelta), at)));
            await workspace.accept(document, items[0]);
            if (closer) await noErrors(document.uri);
            else await diagnostics(document.uri, values => values.length > 0, 'Missing constructor closer remains a diagnostic');
            await workspace.replace(document, fixture(data.file));
            await noErrors(document.uri);
        }
    });

    playbook('X90', async (workspace, data) => {
        const document = await workspace.open(data.file);
        for (const variant of data.variants) {
            const text = fixture(data.file).replace(data.original, data.prefix + variant.suffix);
            await workspace.replace(document, text);
            await diagnostics(document.uri, values => values.length > 0, 'Incomplete array size');
            const at = position(document, data.prefix, data.prefix.length);
            const help = await workspace.signature(document, at);
            assert.strictEqual(help?.signatures[0].label, data.signature);
            assert.strictEqual(help?.signatures[0].activeParameter ?? help?.activeParameter, data.activeParameter);
            const items = (await workspace.completion(document, at)).filter(item => item.kind !== vscode.CompletionItemKind.Snippet);
            assert.deepStrictEqual(items.map(label), data.labels);
            assert.ok(items[0].range instanceof vscode.Range && items[0].range.isEqual(new vscode.Range(at.translate(0, data.replacementStartDelta), at)));
            await workspace.accept(document, items[0]);
            const accepted = data.prefix.slice(0, data.replacementStartDelta) + data.labels[0];
            assert.strictEqual(document.getText(), fixture(data.file).replace(data.original, accepted + variant.suffix));
            if (variant.validAfterAcceptance) await noErrors(document.uri);
            else await diagnostics(document.uri, values => values.length > 0, 'Missing bracket remains a diagnostic');
            await workspace.replace(document, fixture(data.file).replace(data.original, accepted + variant.repairedSuffix));
            await noErrors(document.uri);
            await workspace.replace(document, fixture(data.file));
            await noErrors(document.uri);
        }
    });

    playbook('X91', async (workspace, data) => {
        const document = await workspace.open(data.file);
        for (const variant of data.variants) {
            const marked = scenarioText(data.source, variant);
            const cursor = marked.indexOf('|');
            await workspace.replace(document, marked.replace('|', ''));
            await diagnostics(document.uri, values => values.length > 0, 'Unresolved declaration type');
            const at = document.positionAt(cursor);
            const items = (await workspace.completion(document, at)).filter(item => item.kind !== vscode.CompletionItemKind.Snippet);
            assert.ok(data.include.every(name => items.some(item => label(item) === name)));
            assert.ok(data.exclude.every(name => items.every(item => label(item) !== name)));
            assert.ok(!(await workspace.signature(document, at))?.signatures.length);
            const selected = items.find(item => label(item) === data.selected)!;
            assert.ok(selected.range instanceof vscode.Range && selected.range.isEqual(new vscode.Range(at.translate(0, -data.prefixLength), at)));
            await workspace.accept(document, selected);
            assert.strictEqual(document.getText(), marked.slice(0, cursor - data.prefixLength) + data.selected + marked.slice(cursor + 1));
            await noErrors(document.uri);
        }
    });

    playbook('X92', async (workspace, data) => {
        const document = await workspace.open(data.file);
        for (const parameters of data.parameters) {
            await workspace.replace(document, scenarioText(data.source, parameters));
            await diagnostics(document.uri, values => values.length > 0, 'Unfinished method header');
            const names = symbolNames(await symbols(document));
            assert.ok(data.symbols.every(name => names.includes(name)));
            assert.ok(!names.includes(data.excludedSymbol));
            const folds = await vscode.commands.executeCommand<vscode.FoldingRange[]>('vscode.executeFoldingRangeProvider', document.uri);
            assert.ok(folds?.some(fold => fold.start === data.foldStart && fold.end === data.foldEnd));
            await workspace.replace(document, scenarioText(data.source, data.repairedParameters));
            await noErrors(document.uri);
        }
    });

    playbook('X93', async (workspace, data) => {
        const document = await workspace.open(data.file);
        for (const variant of data.variants) {
            const marked = scenarioText(data.source, variant.declaration);
            const cursor = marked.indexOf('|');
            await workspace.replace(document, marked.replace('|', ''));
            await diagnostics(document.uri, values => values.length > 0, 'Unresolved declaration type');
            const at = document.positionAt(cursor);
            const items = (await workspace.completion(document, at)).filter(item => item.kind !== vscode.CompletionItemKind.Snippet);
            assert.ok(variant.include.every(name => items.some(item => label(item) === name)));
            assert.ok(data.exclude.every(name => items.every(item => label(item) !== name)));
            assert.ok(!(await workspace.signature(document, at))?.signatures.length);
            const selected = items.find(item => label(item) === variant.selected)!;
            assert.ok(selected.range instanceof vscode.Range && selected.range.isEqual(new vscode.Range(at.translate(0, -data.prefixLength), at)));
            await workspace.accept(document, selected);
            assert.strictEqual(document.getText(), marked.slice(0, cursor - data.prefixLength) + variant.selected + marked.slice(cursor + 1));
            await noErrors(document.uri);
        }
    });

    typeHeaderCases(['X94', 'X95', 'X96', 'X97', 'X98']);
}

export function typeHeaderCases(ids: readonly ('X94' | 'X95' | 'X96' | 'X97' | 'X98' | 'X106')[]): void {
    for (const id of ids) {
        playbook(id, async (workspace, data) => {
            const document = await workspace.open(data.file);
            for (const variant of data.variants) {
                const marked = scenarioText(data.source, variant.declaration);
                const cursor = marked.indexOf(data.marker);
                const before = 'prefixLength' in variant ? variant.prefixLength : data.prefixLength;
                const after = 'suffixLength' in variant ? variant.suffixLength : 0;
                await workspace.replace(document, marked.replace(data.marker, ''));
                if ('initiallyValid' in variant && variant.initiallyValid) await noErrors(document.uri);
                else await diagnostics(document.uri, values => values.length > 0, 'Unresolved nested declaration type');
                const at = document.positionAt(cursor);
                const items = (await workspace.completion(document, at)).filter(item => item.kind !== vscode.CompletionItemKind.Snippet);
                assert.ok(variant.include.every(name => items.some(item => label(item) === name)));
                assert.ok(data.exclude.every(name => items.every(item => label(item) !== name)));
                const help = await workspace.signature(document, at);
                if ('callContext' in data && data.callContext) assert.ok(help?.signatures.length);
                else assert.ok(!help?.signatures.length);
                const selected = items.find(item => label(item) === variant.selected)!;
                assert.ok(selected.range instanceof vscode.Range && selected.range.isEqual(new vscode.Range(at.translate(0, -before), at.translate(0, after))));
                await workspace.accept(document, selected);
                assert.strictEqual(document.getText(), marked.slice(0, cursor - before) + variant.selected + marked.slice(cursor + data.marker.length + after));
                if (variant.validAfterAcceptance) await noErrors(document.uri);
                else await diagnostics(document.uri, values => values.length > 0, 'Missing type delimiters remain diagnostics');
                await workspace.replace(document, scenarioText(data.source, variant.repairedDeclaration));
                await noErrors(document.uri);
            }
        });
    }
}
