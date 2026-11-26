import * as assert from 'node:assert';
import * as vscode from 'vscode';
import { applyRename, rename } from './rename';
import { scenarioRegex } from './shared';
import { hover, playbook, position, targets } from './support';

/** A use of a name, as a case's values give it: an anchor, and the name's offset in it. */
interface Use {
    anchor: string;
    offset: number;
}

/** The ranges of [name] at [uses]. */
function useRanges(document: vscode.TextDocument, uses: readonly Use[], name: string): vscode.Range[] {
    return uses.map(use => {
        const start = position(document, use.anchor, use.offset);
        return new vscode.Range(start, start.translate(0, name.length));
    });
}

/** The ranges as sorted text, to compare them whatever order they come in. */
function spans(ranges: readonly vscode.Range[]): string[] {
    return ranges.map(range => `${range.start.line}:${range.start.character}-${range.end.line}:${range.end.character}`).sort();
}

/** Checks that Find References at [at], and its highlights unless [highlights] is false, are [expected], in the document. */
async function checkUses(document: vscode.TextDocument, at: vscode.Position, expected: readonly vscode.Range[], highlights = true): Promise<void> {
    const references = await vscode.commands.executeCommand<vscode.Location[]>('vscode.executeReferenceProvider', document.uri, at) ?? [];
    assert.ok(references.every(item => item.uri.toString() === document.uri.toString()), JSON.stringify(references));
    assert.deepStrictEqual(spans(references.map(item => item.range)), spans(expected), 'Find References');
    if (highlights) {
        const shown = await vscode.commands.executeCommand<vscode.DocumentHighlight[]>('vscode.executeDocumentHighlights', document.uri, at) ?? [];
        assert.deepStrictEqual(spans(shown.map(item => item.range)), spans(expected), 'Highlights');
    }
}

/** X352, registered after X351 in catalog order: a loop's label, its uses, and its rename. */
export function labelCases(): void {
    playbook('X352', async (workspace, data) => {
        const document = await workspace.module(data.file, data.source, data.module);
        const uses = useRanges(document, data.uses, data.name);
        const [declaration, first, , broken] = uses;
        assert.match(await hover(document, first.start), scenarioRegex(data.hover));
        await checkUses(document, declaration.start, uses);
        const found = await targets(document, 'Definition', broken.start);
        assert.deepStrictEqual(found.map(item => [item.uri.toString(), ...spans([item.range])]), [[document.uri.toString(), ...spans([declaration])]]);
        // renamed span, the label would hide the property span from the loop, whose span.first would read the label's
        assert.strictEqual(await rename(document, declaration.start, data.refusedName), null);
        await applyRename(document, declaration.start, data.newName);
        assert.strictEqual(document.getText(), data.source.split(data.name).join(data.newName));
    });
}
