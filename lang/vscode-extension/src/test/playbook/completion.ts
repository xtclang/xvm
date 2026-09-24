import * as assert from 'node:assert';
import { SignatureHelp } from 'vscode-languageclient/node';
import { client, fixture, label, noErrors, playbook, position, targets } from './support';

export function completionCases(): void {
    playbook('X6', 'receiver members and exact prefix replacement', async workspace => {
        for (const body of ['box.§;', 'box.it§;']) {
            const { document, at } = await workspace.editing(body);
            const items = await workspace.completion(document, at);
            const item = items.find(value => label(value) === 'item');
            assert.ok(item, JSON.stringify(items));
            assert.match(item.detail ?? '', /String/);
            assert.ok(!items.some(value => label(value) === 'itemMethod'));
            await workspace.accept(document, item);
            assert.ok(document.getText().includes('box.item;'));
            await workspace.replace(document, document.getText().replace('box.item;', 'String selected = box.item;'));
            await noErrors(document.uri);
        }
    }, ['Completion popup appearance and keyboard acceptance']);

    for (const [id, body] of [
        ['X7', 'Int size = getValue().si§;'],
        ['X8', 'if (value.is(String)) { Int size = value.si§; }']
    ]) {
        playbook(id, id === 'X7' ? 'expression receiver completion' : 'flow-narrowed receiver completion', async workspace => {
            const { document, at } = await workspace.editing(body);
            const item = (await workspace.completion(document, at)).find(value => label(value) === 'size');
            assert.ok(item);
            await workspace.accept(document, item);
            assert.ok(document.getText().includes('.size;'));
            await noErrors(document.uri);
        });
    }

    playbook('X9', 'scope excludes unreadable and later locals', async workspace => {
        const { document, at } = await workspace.editing('Int itemLocal = 1; Int itemUnassigned; item§; Int itemLater = 2;');
        const names = (await workspace.completion(document, at)).map(label);
        for (const name of ['itemLocal', 'itemParameter']) { assert.ok(names.includes(name), names.join(', ')); }
        for (const name of ['itemUnassigned', 'itemLater']) { assert.ok(!names.includes(name), names.join(', ')); }
    });

    playbook('X10', 'empty statement scope inserts without deleting syntax', async workspace => {
        const { document, at } = await workspace.editing('§');
        const before = document.getText();
        const offset = document.offsetAt(at);
        const item = (await workspace.completion(document, at)).find(value => label(value) === 'itemParameter');
        assert.ok(item);
        await workspace.accept(document, item);
        assert.strictEqual(document.getText(), before.slice(0, offset) + 'itemParameter' + before.slice(offset));
    });

    playbook('X11', 'implicit members respect shadowing and closed scopes', async workspace => {
        const first = await workspace.editing('ite§;', true);
        const initial = await workspace.completion(first.document, first.at);
        for (const name of ['item', 'itemMethod', 'itemLocal']) { assert.ok(initial.some(item => label(item) === name)); }
        assert.match(initial.find(item => label(item) === 'item')?.detail ?? '', /\bT\b/);
        const second = await workspace.editing('{ Int itemClosed = 1; } Int item = 2; ite§;', true);
        const shadowed = await workspace.completion(second.document, second.at);
        assert.match(shadowed.find(item => label(item) === 'item')?.detail ?? '', /Int/);
        assert.ok(!shadowed.some(item => label(item) === 'itemClosed'));
    });

    playbook('X12', 'static completion enforces access and receiver kind', async workspace => {
        const { document, at } = await workspace.editing('Tools.item§;');
        const names = (await workspace.completion(document, at)).map(label);
        for (const name of ['itemFunction', 'itemConstant', 'itemType']) { assert.ok(names.includes(name), names.join(', ')); }
        for (const name of ['itemProperty', 'itemMethod', 'itemHidden', 'itemPrivate']) { assert.ok(!names.includes(name), names.join(', ')); }
    });

    playbook('X13', 'implicit, aliased and wildcard imported names', async workspace => {
        for (const [prefix, name, addition] of [
            ['Strin', 'String', ''], ['Buffe', 'Buffer', 'import ecstasy.text.StringBuffer as Buffer;'],
            ['StringBuffe', 'StringBuffer', 'import ecstasy.text.*;']
        ]) {
            const { document, at } = await workspace.editing(`${prefix}§;`, false,
                text => text.replace('module Editing {', `module Editing { ${addition}`));
            assert.ok((await workspace.completion(document, at)).some(item => label(item) === name), name);
        }
    });

    playbook('X14', 'completion edits preserve emoji and CRLF positions', async workspace => {
        const text = fixture('Editing.x').replace('void run(Box<String> box, String itemParameter, Object value) {}',
            'void run(Box<String> box, String itemParameter, Object value) { /* 😀 */ Int size = getValue().si§; }').replace(/\n/g, '\r\n');
        // Load the CRLF file from disk: inserting CRLF text into an LF document normalizes it.
        const document = await workspace.open('Editing.x', text.replace('§', ''));
        const at = document.positionAt(text.indexOf('§'));
        const before = document.getText();
        const item = (await workspace.completion(document, at)).find(value => label(value) === 'size');
        assert.ok(item);
        await workspace.accept(document, item);
        assert.strictEqual(document.getText(), before.replace('getValue().si;', 'getValue().size;'));
        assert.ok(document.getText().includes('\r\n'));
        await noErrors(document.uri);
    });

    playbook('X15', 'written arguments filter incomplete overloads', async workspace => {
        for (const [argument, type] of [['"x"', 'String'], ['1', 'Int']]) {
            const { document, at } = await workspace.editing(`box.choose(${argument}, §);`);
            const result = await workspace.signature(document, at);
            assert.ok(result);
            assert.strictEqual(result?.signatures.length, 1, JSON.stringify(result));
            assert.match(result.signatures[0].label, new RegExp(type));
            assert.strictEqual(result.signatures[0].activeParameter ?? result.activeParameter, 1);
        }
    }, ['Parameter hint popup switches without retaining the previous overload']);

    playbook('X16', 'named arguments map to the declared parameter', async workspace => {
        const { document, at } = await workspace.editing('box.pair(second="x", first=§);');
        const result = await workspace.signature(document, at);
        assert.ok(result);
        assert.strictEqual(result?.signatures.length, 1);
        assert.match(result.signatures[0].label, /String/);
        assert.strictEqual(result.signatures[0].activeParameter ?? result.activeParameter, 0);
        await workspace.replace(document, document.getText().replace('first=)', 'first="y")'));
        await noErrors(document.uri);
        assert.strictEqual((await targets(document, 'Definition', position(document, 'box.pair', 4))).length, 1);
        assert.ok((await workspace.signature(document, position(document, 'first="y"', 7)))?.signatures.length);
    });

    playbook('X17', 'generic inference retains unbound formals', async workspace => {
        for (const [body, type] of [['box.generic("x", §);', 'String'], ['box.generic(§);', 'U']]) {
            const { document, at } = await workspace.editing(body);
            const result = await workspace.signature(document, at);
            assert.ok(result?.signatures.length);
            assert.match(result.signatures[0].label, new RegExp(`\\b${type}\\b`));
        }
    });

    playbook('X18', 'incompatible and invalid named arguments have no candidate', async workspace => {
        for (const body of ['box.choose(True, §);', 'box.pair(unknown=§);', 'box.pair(first="x", first=§);']) {
            const { document, at } = await workspace.editing(body);
            assert.strictEqual((await workspace.signature(document, at))?.signatures.length ?? 0, 0, body);
        }
    });

    playbook('X19', 'implicit instance and static signatures', async workspace => {
        for (const instance of [true, false]) {
            const { document, at } = await workspace.editing(instance ? 'pair(itemLocal, §);' : 'Tools.join("x", §);', instance,
                text => text.replace('class Tools {', 'class Tools { static String join(String first, String second) = first;'));
            const result = await workspace.signature(document, at);
            assert.ok(result?.signatures.length);
            assert.strictEqual(result.signatures[0].activeParameter ?? result.activeParameter, 1);
            const offset = document.offsetAt(at);
            await workspace.replace(document, document.getText().slice(0, offset) + (instance ? 'itemLocal' : '"y"') + document.getText().slice(offset));
            await noErrors(document.uri);
        }
    });

    playbook('X20', 'named-slot ambiguity and selected overload navigation', async workspace => {
        const incomplete = await workspace.editing('box.pair(second="x", §);');
        // LSP defaults the active index to zero. The server suppresses parameter metadata
        // in this ambiguous slot, retaining the signature label without a guessed highlight.
        const result = await client().sendRequest<SignatureHelp | null>('textDocument/signatureHelp', {
            textDocument: { uri: incomplete.document.uri.toString() }, position: incomplete.at
        });
        assert.ok(result?.signatures.length);
        assert.deepStrictEqual(result.signatures[0].parameters, []);
        const locations = [];
        for (const body of ['box.choose("x", "y"); §', 'box.choose(1, 2); §']) {
            const { document } = await workspace.editing(body);
            await noErrors(document.uri);
            const selected = await targets(document, 'Definition', position(document, 'box.choose', 4));
            assert.strictEqual(selected.length, 1);
            locations.push(selected[0]);
        }
        assert.ok(!locations[0].range.isEqual(locations[1].range));
    });
}
