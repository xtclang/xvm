import * as assert from 'node:assert';
import * as vscode from 'vscode';
import { CallHierarchyItem, CallHierarchyOutgoingCall } from 'vscode-languageclient/node';
import { client, diagnostics, fixture, label, noErrors, playbook, position, targets } from './support';

export function advancedCases(): void {
    playbook('X68', 'concrete chained delegation reaches written method and getter bodies', async workspace => {
        const document = await workspace.open('Advanced.x');
        await noErrors(document.uri);
        for (const [use, offset, declaration, declarationOffset, length] of [
            ['forward.map', 8, 'String map(String value) = value', 7, 3],
            ['forward.name', 8, 'String name.get()', 12, 3]
        ] as const) {
            const found = await targets(document, 'Implementation', position(document, use, offset));
            const at = position(document, declaration, declarationOffset);
            assert.strictEqual(found.length, 1);
            assert.strictEqual(found[0].uri.toString(), document.uri.toString());
            assert.ok(found[0].range.isEqual(new vscode.Range(at, at.translate(0, length))));
        }
    });

    playbook('X69', 'super navigation reaches its inherited body', async workspace => {
        const document = await workspace.open('Advanced.x');
        await noErrors(document.uri);
        const found = await targets(document, 'Definition', position(document, 'super(value)'));
        const at = position(document, 'T pick(T value)', 2);
        assert.strictEqual(found.length, 1);
        assert.ok(found[0].range.isEqual(new vscode.Range(at, at.translate(0, 4))));
        const items = await client().sendRequest<CallHierarchyItem[]>('textDocument/prepareCallHierarchy', {
            textDocument: { uri: document.uri.toString() }, position: position(document, 'String pick(String value)', 7)
        });
        assert.strictEqual(items.length, 1);
        const outgoing = await client().sendRequest<CallHierarchyOutgoingCall[]>('callHierarchy/outgoingCalls', { item: items[0] });
        assert.strictEqual(outgoing.length, 1);
        assert.strictEqual(outgoing[0].to.selectionRange.start.line, at.line);
        assert.strictEqual(outgoing[0].to.selectionRange.start.character, at.character);
        await assert.rejects(client().sendRequest('textDocument/prepareRename', {
            textDocument: { uri: document.uri.toString() }, position: position(document, 'super(value)')
        }), { message: 'Rename not allowed at this position' });
    });

    playbook('X70', 'function calls show signature types without guessed parameter names', async workspace => {
        const document = await workspace.open('Advanced.x');
        await noErrors(document.uri);
        const help = await workspace.signature(document, position(document, 'fn(42)', 4));
        assert.strictEqual(help?.signatures[0].label, 'Int fn(Int)');
        const found = await targets(document, 'Definition', position(document, 'fn(42)'));
        assert.strictEqual(found.length, 1);
        assert.strictEqual(document.getText(found[0].range), 'fn');
    });

    playbook('X71', 'compound conditional and trailing argument edits preserve completion scope', async workspace => {
        const document = await workspace.open('Advanced.x');
        await noErrors(document.uri);
        for (const expression of ['1 + word.', 'flag ? word. : 0', 'flag ? 0 : word.', 'pair(word., 2)']) {
            await workspace.replace(document, fixture('Advanced.x').replace('return 1 + word.size;', `return ${expression};`));
            const found = await workspace.completion(document, position(document, 'word.', 5));
            assert.ok(found.some(item => item.label === 'size'), expression);
        }
        await workspace.replace(document, fixture('Advanced.x'));
        await noErrors(document.uri);
    });

    playbook('X72', 'annotation accessors follow compiler composition and explicit overrides', async workspace => {
        const document = await workspace.open('Advanced.x');
        await noErrors(document.uri);
        for (const [use, getter, getterOffset, setter, setterOffset] of [
            ['meter.count', 'Referent get() = super()', 9, 'void set(Referent value)', 5],
            ['meter.computed', 'Int get() = 3', 4, 'void set(Int value)', 5]
        ] as const) {
            const found = await targets(document, 'Implementation', position(document, use, 6));
            assert.strictEqual(found.length, 2);
            for (const at of [position(document, getter, getterOffset), position(document, setter, setterOffset)]) {
                const range = new vscode.Range(at, at.translate(0, 3));
                assert.ok(found.some(target => target.uri.toString() === document.uri.toString() && target.range.isEqual(range)), use);
            }
        }
    });

    playbook('X73', 'incomplete function values retain signatures and reject incompatible arguments', async workspace => {
        const document = await workspace.open('Advanced.x');
        await noErrors(document.uri);
        for (const [call, active] of [['fn(', 0], ['fn(1, ', 1]] as const) {
            await workspace.replace(document, fixture('Advanced.x').replace('fn(1, "x")', `${call})`));
            const anchor = `Int applyPair(function Int(Int, String) fn) = ${call}`;
            const help = await workspace.signature(document, position(document, anchor, anchor.length));
            assert.strictEqual(help?.signatures[0].label, 'Int fn(Int, String)');
            assert.strictEqual(help?.signatures[0].activeParameter, active);
        }
        await workspace.replace(document, fixture('Advanced.x').replace('fn(1, "x")', 'fn(True, )'));
        const rejected = await workspace.signature(document, position(document, 'fn(True, ', 9));
        assert.ok(!rejected || rejected.signatures.length === 0);
        await workspace.replace(document, fixture('Advanced.x'));
        await noErrors(document.uri);
    });

    playbook('X74', 'incomplete generic constructors map positional and named arguments', async workspace => {
        const document = await workspace.open('Advanced.x');
        await noErrors(document.uri);
        for (const [call, active] of [
            ['new Packet<String>(', 0],
            ['new Packet<String>("a", ', 1],
            ['new Packet<String>(second="b", first=', 0]
        ] as const) {
            await workspace.replace(document, fixture('Advanced.x').replace('new Packet<String>("a", "b")', `${call})`));
            const help = await workspace.signature(document, position(document, call, call.length));
            assert.strictEqual(help?.signatures[0].label, 'new Packet(String first, String second)');
            assert.strictEqual(help?.signatures[0].activeParameter, active);
        }
        await workspace.replace(document, fixture('Advanced.x'));
        await noErrors(document.uri);
    });

    playbook('X75', 'missing grouping and index delimiters retain member completion', async workspace => {
        const document = await workspace.open('Advanced.x');
        await noErrors(document.uri);
        for (const expression of ['(word.si', 'pair((word.si', 'word[word.si']) {
            await workspace.replace(document, fixture('Advanced.x').replace('return 1 + word.size;', `return ${expression};`));
            await diagnostics(document.uri, values => values.length > 0, 'Missing delimiter diagnostics');
            const found = await workspace.completion(document, position(document, 'word.si', 7));
            assert.ok(found.some(item => item.label === 'size'), expression);
            assert.ok(vscode.languages.getDiagnostics(document.uri).length > 0, 'Cursor queries preserve diagnostics');
        }
        await workspace.replace(document, fixture('Advanced.x'));
        await noErrors(document.uri);
    });

    playbook('X76', 'missing nested call delimiters retain the innermost signature', async workspace => {
        const document = await workspace.open('Advanced.x');
        await noErrors(document.uri);
        const expression = 'pair((pair(1, ';
        await workspace.replace(document, fixture('Advanced.x').replace('return 1 + word.size;', `return ${expression};`));
        await diagnostics(document.uri, values => values.length > 0, 'Missing nested call diagnostics');
        const help = await workspace.signature(document, position(document, expression, expression.length));
        assert.strictEqual(help?.signatures[0].label, 'Int pair(Int first, Int second)');
        assert.strictEqual(help?.signatures[0].activeParameter, 1);
        await workspace.replace(document, fixture('Advanced.x'));
        await noErrors(document.uri);
    });

    playbook('X77', 'argument value completions fit methods functions and generic constructors', async workspace => {
        const document = await workspace.open('Advanced.x');
        await noErrors(document.uri);
        for (const [original, prefix] of [
            ['take(1, text)', 'take(1, '],
            ['take(1, text)', 'take(first=1, second='],
            ['fn(1, text)', 'fn(1, '],
            ['new Packet<String>("a", text)', 'new Packet<String>("a", '],
            ['new Packet<String>("a", text)', 'new Packet<String>(second="b", first=']
        ] as const) {
            await workspace.replace(document, fixture('Advanced.x').replace(original, `${prefix})`));
            await diagnostics(document.uri, values => values.length > 0, 'Missing argument diagnostics');
            const at = position(document, prefix, prefix.length, true);
            const items = (await workspace.completion(document, at)).filter(item => item.kind !== vscode.CompletionItemKind.Snippet);
            assert.deepStrictEqual(items.map(item => item.label).sort(), ['qualifiedName', 'simpleName', 'text'], prefix);
            const selected = items.find(item => item.label === 'text')!;
            assert.ok(selected.range instanceof vscode.Range && selected.range.isEqual(new vscode.Range(at, at)));
            await workspace.accept(document, selected);
            await noErrors(document.uri);
        }
        await workspace.replace(document, fixture('Advanced.x'));
        await noErrors(document.uri);
    });

    playbook('X78', 'argument completion preserves overload alternatives until insertion', async workspace => {
        const document = await workspace.open('Advanced.x');
        await noErrors(document.uri);
        for (const selected of ['number', 'text']) {
            await workspace.replace(document, fixture('Advanced.x').replace('choose(text);', 'choose();'));
            await diagnostics(document.uri, values => values.length > 0, 'Missing overloaded argument diagnostics');
            const at = position(document, 'choose();', 7);
            const items = (await workspace.completion(document, at)).filter(item => item.kind !== vscode.CompletionItemKind.Snippet);
            assert.deepStrictEqual(items.map(item => item.label).sort(), ['number', 'qualifiedName', 'simpleName', 'text']);
            assert.strictEqual((await workspace.signature(document, at))?.signatures.length, 2);
            await workspace.accept(document, items.find(item => item.label === selected)!);
            await noErrors(document.uri);
        }
        await workspace.replace(document, fixture('Advanced.x'));
        await noErrors(document.uri);
    });

    playbook('X79', 'typed argument prefixes fit values and replace exactly the identifier', async workspace => {
        const document = await workspace.open('Advanced.x');
        await noErrors(document.uri);
        for (const [original, prefix] of [
            ['take(1, text)', 'take(1, te'],
            ['take(1, text)', 'take(first=1, second=te'],
            ['fn(1, text)', 'fn(1, te'],
            ['new Packet<String>("a", text)', 'new Packet<String>("a", te'],
            ['new Packet<String>("a", text)', 'new Packet<String>(second="b", first=te']
        ] as const) {
            await workspace.replace(document, fixture('Advanced.x').replace(original, `${prefix})`));
            await diagnostics(document.uri, values => values.length > 0, 'Unresolved argument prefix diagnostics');
            const at = position(document, prefix, prefix.length, true);
            const items = (await workspace.completion(document, at)).filter(item => item.kind !== vscode.CompletionItemKind.Snippet);
            assert.deepStrictEqual(items.map(item => item.label), ['text'], prefix);
            assert.ok(items[0].range instanceof vscode.Range && items[0].range.isEqual(new vscode.Range(at.translate(0, -2), at)));
            const before = document.getText();
            await workspace.accept(document, items[0]);
            assert.strictEqual(document.getText(), before.replace(`${prefix})`, `${prefix.slice(0, -2)}text)`));
            await noErrors(document.uri);
        }
        await workspace.replace(document, fixture('Advanced.x'));
        await noErrors(document.uri);
    });

    playbook('X80', 'typed argument prefixes preserve compatible overload alternatives', async workspace => {
        const document = await workspace.open('Advanced.x');
        await noErrors(document.uri);
        for (const selected of ['valueNumber', 'valueText']) {
            await workspace.replace(document, fixture('Advanced.x').replace('choose(valueText);', 'choose(va);'));
            await diagnostics(document.uri, values => values.length > 0, 'Unresolved overloaded prefix diagnostics');
            const at = position(document, 'choose(va);', 9);
            const items = (await workspace.completion(document, at)).filter(item => item.kind !== vscode.CompletionItemKind.Snippet);
            assert.deepStrictEqual(items.map(item => item.label).sort(), ['valueNumber', 'valueText']);
            assert.strictEqual((await workspace.signature(document, at))?.signatures.length, 2);
            const chosen = items.find(item => item.label === selected)!;
            assert.ok(chosen.range instanceof vscode.Range && chosen.range.isEqual(new vscode.Range(at.translate(0, -2), at)));
            await workspace.accept(document, chosen);
            assert.ok(document.getText().includes(`choose(${selected});`));
            await noErrors(document.uri);
        }
        await workspace.replace(document, fixture('Advanced.x'));
        await noErrors(document.uri);
    });

    playbook('X81', 'implicit property and constant values fit positional and named arguments', async workspace => {
        const document = await workspace.open('Advanced.x');
        for (const prefix of ['takeValue(va', 'takeValue(value=va']) {
            await workspace.replace(document, fixture('Advanced.x').replace('takeValue(valueText)', `${prefix})`));
            await diagnostics(document.uri, values => values.length > 0, 'Unresolved property argument prefix');
            const at = position(document, prefix, prefix.length);
            const items = (await workspace.completion(document, at)).filter(item => item.kind !== vscode.CompletionItemKind.Snippet);
            assert.deepStrictEqual(items.map(label).sort(), ['valueConstant', 'valueText']);
            const selected = items.find(item => label(item) === 'valueText')!;
            assert.strictEqual(selected.kind, vscode.CompletionItemKind.Property);
            assert.match(selected.detail ?? '', /String/);
            assert.ok(selected.range instanceof vscode.Range && selected.range.isEqual(new vscode.Range(at.translate(0, -2), at)));
            await workspace.accept(document, selected);
            await noErrors(document.uri);
        }
        await workspace.replace(document, fixture('Advanced.x'));
        await noErrors(document.uri);
    });

    playbook('X82', 'static argument completion offers compatible constants without an instance receiver', async workspace => {
        const document = await workspace.open('Advanced.x');
        await workspace.replace(document, fixture('Advanced.x').replace('takeValue(valueConstant)', 'takeValue(va)'));
        await diagnostics(document.uri, values => values.length > 0, 'Unresolved constant argument prefix');
        const at = position(document, 'takeValue(va)', 12);
        const items = (await workspace.completion(document, at)).filter(item => item.kind !== vscode.CompletionItemKind.Snippet);
        assert.deepStrictEqual(items.map(label), ['valueConstant']);
        assert.strictEqual(items[0].kind, vscode.CompletionItemKind.Property);
        await workspace.accept(document, items[0]);
        await noErrors(document.uri);
        assert.strictEqual(document.getText(), fixture('Advanced.x'));
    });

    playbook('X83', 'specialized constructors preserve receiver type and exact argument edits', async workspace => {
        const document = await workspace.open('Advanced.x');
        for (const original of ['outer.new Part("a", text)', 'packet.new("a", text)', 'new @Marked Packet<String>("a", text)']) {
            const prefix = original.replace('text)', 'te');
            await workspace.replace(document, fixture('Advanced.x').replace(original, `${prefix})`));
            await diagnostics(document.uri, values => values.length > 0, 'Unresolved constructor prefix');
            const at = position(document, prefix, prefix.length);
            const help = await workspace.signature(document, at);
            assert.match(help?.signatures[0].label ?? '', /String first, String second/);
            assert.strictEqual(help?.signatures[0].activeParameter ?? help?.activeParameter, 1);
            const items = (await workspace.completion(document, at)).filter(item => item.kind !== vscode.CompletionItemKind.Snippet);
            assert.deepStrictEqual(items.map(label), ['text']);
            await workspace.accept(document, items[0]);
            assert.strictEqual(document.getText(), fixture('Advanced.x'));
            await noErrors(document.uri);
        }
    });

    playbook('X84', 'omitted constructor types distinguish required from provisional inference', async workspace => {
        const document = await workspace.open('Advanced.x');
        for (const original of ['Packet<String> inferred = new Packet("a", text)', '        new Packet("a", text)']) {
            const prefix = original.replace('text)', 'te');
            await workspace.replace(document, fixture('Advanced.x').replace(original, `${prefix})`));
            await diagnostics(document.uri, values => values.length > 0, 'Unresolved inferred constructor prefix');
            const at = position(document, prefix, prefix.length);
            assert.match((await workspace.signature(document, at))?.signatures[0].label ?? '', /String first, String second/);
            const items = (await workspace.completion(document, at)).filter(item => item.kind !== vscode.CompletionItemKind.Snippet);
            assert.deepStrictEqual(items.map(label).sort(), original.includes('inferred') ? ['text'] : ['text', 'textNumber']);
            await workspace.accept(document, items.find(item => label(item) === 'text')!);
            await noErrors(document.uri);
        }
        await workspace.replace(document, fixture('Advanced.x'));
    });

    playbook('X85', 'array dimensions precede the active initializer argument', async workspace => {
        const document = await workspace.open('Advanced.x');
        for (const prefix of ['new String[2](te', 'new String[2](supply=te']) {
            await workspace.replace(document, fixture('Advanced.x').replace('new String[2](text)', `${prefix})`));
            await diagnostics(document.uri, values => values.length > 0, 'Unresolved array initializer');
            const at = position(document, prefix, prefix.length);
            const help = await workspace.signature(document, at);
            assert.strictEqual(help?.signatures[0].activeParameter ?? help?.activeParameter, 1);
            assert.match(help?.signatures[0].label ?? '', /supply/);
            const items = (await workspace.completion(document, at)).filter(item => item.kind !== vscode.CompletionItemKind.Snippet);
            assert.deepStrictEqual(items.map(label), ['text']);
            await workspace.accept(document, items[0]);
            await noErrors(document.uri);
        }
        await workspace.replace(document, fixture('Advanced.x'));
    });

    playbook('X86', 'tuple and collection literals retain cursor completion with missing closers', async workspace => {
        const document = await workspace.open('Advanced.x');
        for (const expression of ['(1, word.si', 'Tuple<Int, Int>:(1, word.si', '[word.si', '["key"=word.si']) {
            await workspace.replace(document, fixture('Advanced.x').replace('[word.size]', expression));
            await diagnostics(document.uri, values => values.length > 0, 'Missing literal delimiter');
            const at = position(document, expression, expression.length);
            const item = (await workspace.completion(document, at)).find(item => label(item) === 'size');
            assert.ok(item);
            assert.ok(item.range instanceof vscode.Range && item.range.isEqual(new vscode.Range(at.translate(0, -2), at)));
            await workspace.accept(document, item);
            await diagnostics(document.uri, values => values.length > 0, 'Accepting a member must not repair delimiters');
            await workspace.replace(document, fixture('Advanced.x'));
            await noErrors(document.uri);
        }
    });

    playbook('X87', 'declaration recovery preserves initializer and default-value contexts', async workspace => {
        const document = await workspace.open('Advanced.x');
        for (const [prefix, suffix, selected, terminator] of [
            ['Int declaredSize(String word) = word.si', '', 'size', ';'],
            ['Int declaredSize = "x".si', '', 'size', ';'],
            ['void defaults(Int size = Int64.Ma', ' {}', 'MaxValue', ')']
        ]) {
            const text = fixture('Advanced.x').replace(/}\s*$/, `${prefix}${suffix}\n}\n`);
            await workspace.replace(document, text);
            await diagnostics(document.uri, values => values.length > 0, 'Incomplete declaration');
            const at = position(document, prefix, prefix.length);
            const item = (await workspace.completion(document, at)).find(item => label(item) === selected);
            assert.ok(item, prefix);
            await workspace.accept(document, item);
            await workspace.replace(document, text.replace(prefix, prefix.slice(0, -2) + selected + terminator));
            await noErrors(document.uri);
        }
        await workspace.replace(document, fixture('Advanced.x'));
        await noErrors(document.uri);
    });

    playbook('X88', 'anonymous construction uses inherited signatures and preserves captured locals', async workspace => {
        const document = await workspace.open('Advanced.x');
        const original = 'new Packet<String>("anonymous", text) {';
        const prefix = 'new Packet<String>("anonymous", te';
        await workspace.replace(document, fixture('Advanced.x').replace(original, `${prefix}) {`));
        await diagnostics(document.uri, values => values.length > 0, 'Unresolved anonymous constructor argument');
        const at = position(document, prefix, prefix.length);
        const help = await workspace.signature(document, at);
        assert.strictEqual(help?.signatures[0].label, 'new Packet(String first, String second)');
        assert.strictEqual(help?.signatures[0].activeParameter ?? help?.activeParameter, 1);
        const items = (await workspace.completion(document, at)).filter(item => item.kind !== vscode.CompletionItemKind.Snippet);
        assert.deepStrictEqual(items.map(label), ['text']);
        await workspace.accept(document, items[0]);
        assert.strictEqual(document.getText(), fixture('Advanced.x'));
        await noErrors(document.uri);
    });

    playbook('X89', 'anonymous interface constructors survive missing call closers', async workspace => {
        const document = await workspace.open('Advanced.x');
        const original = 'new CursorReader("a", text)';
        const prefix = 'new CursorReader("a", te';
        for (const closer of [')', '']) {
            await workspace.replace(document, fixture('Advanced.x').replace(original, prefix + closer));
            await diagnostics(document.uri, values => values.length > 0, 'Incomplete anonymous interface construction');
            const at = position(document, prefix, prefix.length);
            const help = await workspace.signature(document, at);
            assert.strictEqual(help?.signatures[0].label, 'new CursorReader(String first, String second)');
            assert.strictEqual(help?.signatures[0].activeParameter ?? help?.activeParameter, 1);
            const items = (await workspace.completion(document, at)).filter(item => item.kind !== vscode.CompletionItemKind.Snippet);
            assert.deepStrictEqual(items.map(label), ['text']);
            assert.ok(items[0].range instanceof vscode.Range && items[0].range.isEqual(new vscode.Range(at.translate(0, -2), at)));
            await workspace.accept(document, items[0]);
            if (closer) await noErrors(document.uri);
            else await diagnostics(document.uri, values => values.length > 0, 'Missing constructor closer remains a diagnostic');
            await workspace.replace(document, fixture('Advanced.x'));
            await noErrors(document.uri);
        }
    });

}
