import * as assert from 'node:assert';
import * as vscode from 'vscode';
import { noErrors, playbook, position, targets } from './support';

function locations(document: vscode.TextDocument, anchors: [string, number, number][]): vscode.Location[] {
    return anchors.map(([text, offset, length]) => {
        const at = position(document, text, offset);
        return new vscode.Location(document.uri, new vscode.Range(at, at.translate(0, length)));
    });
}

function assertLocations(actual: vscode.Location[], expected: vscode.Location[]): void {
    const keys = (values: vscode.Location[]) => values.map(value => `${value.uri}:${JSON.stringify(value.range)}`).sort();
    assert.deepStrictEqual(keys(actual), keys(expected));
}

export function propertyCases(): void {
    playbook('X64', 'property implementations preserve generic identity and written accessor targets', async workspace => {
        const document = await workspace.open('Properties.x');
        await noErrors(document.uri);
        const expected = locations(document, [['String name = "stored"', 7, 4], ['String name.get()', 12, 3]]);
        for (const at of [position(document, 'T name', 2), position(document, 'value.name', 6)]) {
            assertLocations(await targets(document, 'Implementation', at), expected);
        }
    }, ['Implementation chooser/peek rendering and clicking the selected getter']);

    playbook('X65', 'property getters setters and default field overrides have distinct implementation sets', async workspace => {
        const document = await workspace.open('Properties.x');
        await noErrors(document.uri);
        const getter: [string, number, number] = ['Int get()', 4, 3];
        const setter: [string, number, number] = ['void set(', 5, 3];
        const override: [string, number, number] = ['Int value.get()', 10, 3];
        for (const [anchor, offset, expected] of [
            ['Int value {', 4, [getter, setter, override]],
            ['Int get()', 4, [getter, override]],
            ['void set(', 5, [setter]],
            ['String label {', 7, [['String get()', 7, 3], ['String label =', 7, 5]]]
        ] as [string, number, [string, number, number][]][]) {
            assertLocations(await targets(document, 'Implementation', position(document, anchor, offset)), locations(document, expected));
        }
    });

    playbook('X66', 'abstract delegated annotated and binary properties have no invented source targets', async workspace => {
        const document = await workspace.open('Properties.x');
        await noErrors(document.uri);
        for (const [anchor, offset] of [['String absent', 7], ['Int later', 4], ['text.size', 5]] as const) {
            assert.deepStrictEqual(await targets(document, 'Implementation', position(document, anchor, offset)), []);
        }
    });

    playbook('X67', 'closed property accessors track unsaved positions and recover after parse failure', async workspace => {
        const original = 'class Member implements Named<String> { @Override String name.get() = "member"; }';
        await workspace.write('Properties/Member.x', original);
        const document = await workspace.open('Properties.x');
        await noErrors(document.uri);
        const lookup = () => targets(document, 'Implementation', position(document, 'T name', 2));
        const initial = await lookup();
        assert.strictEqual(initial.length, 3);
        const memberUri = workspace.uri('Properties/Member.x');
        const closed = initial.find(item => item.uri.toString() === memberUri.toString());
        assert.ok(closed);
        assert.ok(closed.range.isEqual(new vscode.Range(0, original.indexOf('get'), 0, original.indexOf('get') + 3)));
        const member = await workspace.open('Properties/Member.x');
        await workspace.replace(member, '\n\n' + original);
        const moved = (await lookup()).find(item => item.uri.toString() === memberUri.toString());
        assert.ok(moved?.range.isEqual(new vscode.Range(closed.range.start.translate(2), closed.range.end.translate(2))));
        await workspace.replace(member, 'class Member {');
        assert.deepStrictEqual(await lookup(), []);
        await workspace.replace(member, original);
        await noErrors(member.uri);
        assertLocations(await lookup(), initial);
    });
}
