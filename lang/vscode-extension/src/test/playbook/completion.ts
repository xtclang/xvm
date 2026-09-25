import * as assert from 'node:assert';
import { SignatureHelp } from 'vscode-languageclient/node';
import { editScenario, scenarioOffset, scenarioRegex, scenarioText, shared } from './shared';
import { client, fixture, label, noErrors, playbook, position, targets } from './support';

export function completionCases(): void {
    playbook('X6', async (workspace, data) => {
        for (const body of data.bodies) {
            const { document, at } = await workspace.editing(body);
            const items = await workspace.completion(document, at);
            const item = items.find(value => label(value) === data.label);
            assert.ok(item, JSON.stringify(items));
            assert.match(item.detail ?? '', scenarioRegex(data.type));
            assert.ok(!items.some(value => label(value) === data.excludedLabel));
            await workspace.accept(document, item);
            assert.ok(document.getText().includes(data.acceptedExpression));
            await workspace.replace(document, document.getText().replace(data.acceptedExpression, data.validStatement));
            await noErrors(document.uri);
        }
    });

    playbook('X7', async (workspace, data) => {
        const scenario = shared.completion;
        const document = await workspace.open(scenario.file);
        const incomplete = editScenario(fixture(scenario.file), scenario.edit);
        await workspace.replace(document, incomplete);
        const at = document.positionAt(scenarioOffset(incomplete, scenario.cursor));
        const item = (await workspace.completion(document, at)).find(value => label(value) === scenario.label);
        assert.ok(item);
        await workspace.accept(document, item);
        assert.strictEqual(document.getText(), editScenario(incomplete, scenario.accepted));
        await noErrors(document.uri);
    });

    playbook('X8', async (workspace, data) => {
        const { document, at } = await workspace.editing(data.body);
        const item = (await workspace.completion(document, at)).find(value => label(value) === data.label);
        assert.ok(item);
        await workspace.accept(document, item);
        assert.ok(document.getText().includes(data.acceptedSuffix));
        await noErrors(document.uri);
    });

    playbook('X9', async (workspace, data) => {
        const { document, at } = await workspace.editing(data.body);
        const names = (await workspace.completion(document, at)).map(label);
        for (const name of data.include) { assert.ok(names.includes(name), names.join(', ')); }
        for (const name of data.exclude) { assert.ok(!names.includes(name), names.join(', ')); }
    });

    playbook('X10', async (workspace, data) => {
        const { document, at } = await workspace.editing(data.body);
        const before = document.getText();
        const offset = document.offsetAt(at);
        const item = (await workspace.completion(document, at)).find(value => label(value) === data.label);
        assert.ok(item);
        await workspace.accept(document, item);
        assert.strictEqual(document.getText(), before.slice(0, offset) + data.label + before.slice(offset));
    });

    playbook('X11', async (workspace, data) => {
        const first = await workspace.editing(data.body, true);
        const initial = await workspace.completion(first.document, first.at);
        for (const name of data.include) { assert.ok(initial.some(item => label(item) === name)); }
        assert.match(initial.find(item => label(item) === data.label)?.detail ?? '', scenarioRegex(data.genericType));
        const second = await workspace.editing(data.shadowedBody, true);
        const shadowed = await workspace.completion(second.document, second.at);
        assert.match(shadowed.find(item => label(item) === data.label)?.detail ?? '', scenarioRegex(data.shadowedType));
        assert.ok(!shadowed.some(item => label(item) === data.excludedLabel));
    });

    playbook('X12', async (workspace, data) => {
        const { document, at } = await workspace.editing(data.body);
        const names = (await workspace.completion(document, at)).map(label);
        for (const name of data.include) { assert.ok(names.includes(name), names.join(', ')); }
        for (const name of data.exclude) { assert.ok(!names.includes(name), names.join(', ')); }
    });

    playbook('X13', async (workspace, data) => {
        for (const { prefix, name, addition } of data.variants) {
            const { document, at } = await workspace.editing(scenarioText(data.body, prefix), false,
                text => text.replace(data.moduleStart, scenarioText(data.moduleWithImport, addition)));
            assert.ok((await workspace.completion(document, at)).some(item => label(item) === name), name);
        }
    });

    playbook('X14', async (workspace, data) => {
        const text = fixture(data.file).replace(data.method,
            data.incompleteMethod).replace(scenarioRegex(data.lineBreak), '\r\n');
        // Load the CRLF file from disk: inserting CRLF text into an LF document normalizes it.
        const document = await workspace.open(data.file, text.replace('§', ''));
        const at = document.positionAt(text.indexOf('§'));
        const before = document.getText();
        const item = (await workspace.completion(document, at)).find(value => label(value) === data.label);
        assert.ok(item);
        await workspace.accept(document, item);
        assert.strictEqual(document.getText(), before.replace(data.prefix, data.accepted));
        assert.ok(document.getText().includes('\r\n'));
        await noErrors(document.uri);
    });

    playbook('X15', async (workspace, data) => {
        for (const { argument, type } of data.variants) {
            const { document, at } = await workspace.editing(scenarioText(data.body, argument));
            const result = await workspace.signature(document, at);
            assert.ok(result);
            assert.strictEqual(result?.signatures.length, data.expected, JSON.stringify(result));
            assert.match(result.signatures[0].label, new RegExp(type));
            assert.strictEqual(result.signatures[0].activeParameter ?? result.activeParameter, data.expected);
        }
    });

    playbook('X16', async (workspace, data) => {
        const { document, at } = await workspace.editing(data.body);
        const result = await workspace.signature(document, at);
        assert.ok(result);
        assert.strictEqual(result?.signatures.length, data.expected);
        assert.match(result.signatures[0].label, scenarioRegex(data.type));
        assert.strictEqual(result.signatures[0].activeParameter ?? result.activeParameter, data.activeParameter);
        await workspace.replace(document, document.getText().replace(data.replaceFrom, data.replaceWith));
        await noErrors(document.uri);
        assert.strictEqual((await targets(document, 'Definition', position(document, data.call, data.callOffset))).length, data.expected);
        assert.ok((await workspace.signature(document, position(document, data.completedArgument, data.argumentOffset)))?.signatures.length);
    });

    playbook('X17', async (workspace, data) => {
        for (const { body, type } of data.variants) {
            const { document, at } = await workspace.editing(body);
            const result = await workspace.signature(document, at);
            assert.ok(result?.signatures.length);
            assert.match(result.signatures[0].label, new RegExp(scenarioText(data.typePattern, type)));
        }
    });

    playbook('X18', async (workspace, data) => {
        for (const body of data.bodies) {
            const { document, at } = await workspace.editing(body);
            assert.strictEqual((await workspace.signature(document, at))?.signatures.length ?? data.signatureCount, data.signatureCount, body);
        }
    });

    playbook('X19', async (workspace, data) => {
        for (const instance of (data.receivers as (boolean)[])) {
            const { document, at } = await workspace.editing(instance ? data.instanceBody : data.staticBody, instance,
                text => text.replace(data.replaceFrom, data.replaceWith));
            const result = await workspace.signature(document, at);
            assert.ok(result?.signatures.length);
            assert.strictEqual(result.signatures[0].activeParameter ?? result.activeParameter, data.activeParameter);
            const offset = document.offsetAt(at);
            await workspace.replace(document, document.getText().slice(0, offset) + (instance ? data.instanceArgument : data.staticArgument) + document.getText().slice(offset));
            await noErrors(document.uri);
        }
    });

    playbook('X20', async (workspace, data) => {
        const incomplete = await workspace.editing(data.body);
        // LSP defaults the active index to zero. The server suppresses parameter metadata
        // in this ambiguous slot, retaining the signature label without a guessed highlight.
        const result = await client().sendRequest<SignatureHelp | null>('textDocument/signatureHelp', {
            textDocument: { uri: incomplete.document.uri.toString() }, position: incomplete.at
        });
        assert.ok(result?.signatures.length);
        assert.deepStrictEqual(result.signatures[0].parameters, []);
        const locations = [];
        for (const body of data.bodies) {
            const { document } = await workspace.editing(body);
            await noErrors(document.uri);
            const selected = await targets(document, 'Definition', position(document, data.anchor, data.offset));
            assert.strictEqual(selected.length, data.targetCount);
            locations.push(selected[0]);
        }
        assert.ok(!locations[0].range.isEqual(locations[1].range));
    });
}
