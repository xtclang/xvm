import * as assert from 'node:assert';
import * as vscode from 'vscode';
import { noErrors, playbook, position, targets } from './support';

function locations(document: vscode.TextDocument, anchors: { anchor: string; offset: number; length: number }[]): vscode.Location[] {
    return anchors.map(({ anchor, offset, length }) => {
        const at = position(document, anchor, offset);
        return new vscode.Location(document.uri, new vscode.Range(at, at.translate(0, length)));
    });
}

function assertLocations(actual: vscode.Location[], expected: vscode.Location[]): void {
    const keys = (values: vscode.Location[]) => values.map(value => `${value.uri}:${JSON.stringify(value.range)}`).sort();
    assert.deepStrictEqual(keys(actual), keys(expected));
}

export function propertyCases(): void {
    playbook('X64', async (workspace, data) => {
        const document = await workspace.open(data.file);
        await noErrors(document.uri);
        const expected = locations(document, data.locations);
        for (const at of [position(document, data.anchor2, data.offset2), position(document, data.anchor, data.offset)]) {
            assertLocations(await targets(document, 'Implementation', at), expected);
        }
    });

    playbook('X65', async (workspace, data) => {
        const document = await workspace.open(data.file);
        await noErrors(document.uri);
        for (const { anchor, offset, expected } of data.variants) {
            assertLocations(await targets(document, 'Implementation', position(document, anchor, offset)), locations(document, expected));
        }
    });

    playbook('X66', async (workspace, data) => {
        const document = await workspace.open(data.file);
        await noErrors(document.uri);
        for (const { anchor, offset } of data.variants) {
            assert.deepStrictEqual(await targets(document, 'Implementation', position(document, anchor, offset)), []);
        }
    });

    playbook('X67', async (workspace, data) => {
        const original = data.original;
        await workspace.write(data.memberFile, original);
        const document = await workspace.open(data.rootFile);
        await noErrors(document.uri);
        const lookup = () => targets(document, 'Implementation', position(document, data.anchor, data.lineShift));
        const initial = await lookup();
        assert.strictEqual(initial.length, data.targetCount);
        const memberUri = workspace.uri(data.memberFile);
        const closed = initial.find(item => item.uri.toString() === memberUri.toString());
        assert.ok(closed);
        assert.ok(closed.range.isEqual(new vscode.Range(data.declarationLine, original.indexOf(data.getter), data.declarationLine, original.indexOf(data.getter) + data.targetCount)));
        const member = await workspace.open(data.memberFile);
        await workspace.replace(member, '\n\n' + original);
        const moved = (await lookup()).find(item => item.uri.toString() === memberUri.toString());
        assert.ok(moved?.range.isEqual(new vscode.Range(closed.range.start.translate(data.lineShift), closed.range.end.translate(data.lineShift))));
        await workspace.replace(member, data.replaceWith);
        assert.deepStrictEqual(await lookup(), []);
        await workspace.replace(member, original);
        await noErrors(member.uri);
        assertLocations(await lookup(), initial);
    });
}
