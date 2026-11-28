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

/** Checks that Go to Definition at [at] goes to [declaration] alone, in the document. */
async function checkDefinition(document: vscode.TextDocument, at: vscode.Position, declaration: vscode.Range): Promise<void> {
    const found = await targets(document, 'Definition', at);
    assert.deepStrictEqual(found.map(item => [item.uri.toString(), ...spans([item.range])]), [[document.uri.toString(), ...spans([declaration])]]);
}

/** X352, registered after X351 in catalog order: a loop's label, its uses, and its rename. */
export function labelCases(): void {
    playbook('X352', async (workspace, data) => {
        const document = await workspace.module(data.file, data.source, data.module);
        const uses = useRanges(document, data.uses, data.name);
        const [declaration, first, , broken] = uses;
        assert.match(await hover(document, first.start), scenarioRegex(data.hover));
        await checkUses(document, declaration.start, uses);
        await checkDefinition(document, broken.start, declaration);
        // renamed span, the label would hide the property span from the loop, whose span.first would read the label's
        assert.strictEqual(await rename(document, declaration.start, data.refusedName), null);
        await applyRename(document, declaration.start, data.newName);
        assert.strictEqual(document.getText(), data.source.split(data.name).join(data.newName));
    });
}

/**
 * X353, registered after X352 in catalog order: a parameter of a constructor with a finally block, which the compiler
 * compiles as a method of its own over the same parameters, from its use in the body and from a named argument.
 */
export function constructorFinallyCases(): void {
    playbook('X353', async (workspace, data) => {
        const document = await workspace.module(data.file, data.source, data.module);
        for (const from of data.from) {
            await workspace.replace(document, data.source);
            const uses = useRanges(document, data.uses, data.name);
            await checkUses(document, uses[from].start, uses, false);
            await applyRename(document, uses[from].start, data.newName);
            assert.strictEqual(document.getText(), data.source.split(data.name).join(data.newName), `Rename from ${data.uses[from].anchor}`);
        }
    });
}

/**
 * X360, registered after X359 in catalog order: a method's type parameter named in a lambda, which the compiler gives a
 * type parameter of its own, is the method's.
 */
export function lambdaTypeParameterCases(): void {
    playbook('X360', async (workspace, data) => {
        const document = await workspace.module(data.file, data.source, data.module);
        const [declaration, inside, outside] = useRanges(document, [data.declaration, data.lambda, data.outside], data.name);
        await checkDefinition(document, inside.start, declaration);
        const uses = [...data.source.matchAll(new RegExp(`\\b${data.name}\\b`, 'g'))].map(match => {
            const start = document.positionAt(match.index!);
            return new vscode.Range(start, start.translate(0, data.name.length));
        });
        await checkUses(document, inside.start, uses);
        const shown = await hover(document, inside.start);
        assert.ok(shown.includes(data.name), shown);
        assert.strictEqual(shown, await hover(document, outside.start), 'Hover shows the type parameter as outside the lambda');
    });
}
