import * as assert from 'node:assert';
import * as vscode from 'vscode';
import { CallHierarchyItem, CallHierarchyOutgoingCall } from 'vscode-languageclient/node';
import { client, diagnostics, fixture, noErrors, playbook, position, targets } from './support';

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
            const help = await workspace.signature(document, position(document, call, call.length, true));
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
}
