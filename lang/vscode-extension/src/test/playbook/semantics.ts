import * as assert from 'node:assert';
import * as vscode from 'vscode';
import { CallHierarchyIncomingCall, CallHierarchyItem, CallHierarchyOutgoingCall, SemanticTokens, SemanticTokensOptions } from 'vscode-languageclient/node';
import { client, fixture, noErrors, playbook, position, symbols, targetNames, targets } from './support';

async function calls(document: vscode.TextDocument, at: vscode.Position): Promise<CallHierarchyItem[]> {
    return await client().sendRequest<CallHierarchyItem[] | null>('textDocument/prepareCallHierarchy', {
        textDocument: { uri: document.uri.toString() }, position: at
    }) ?? [];
}

async function incoming(item: CallHierarchyItem): Promise<CallHierarchyIncomingCall[]> {
    assert.ok(item, 'Prepared call hierarchy item');
    return await client().sendRequest<CallHierarchyIncomingCall[] | null>('callHierarchy/incomingCalls', { item }) ?? [];
}

async function outgoing(item: CallHierarchyItem): Promise<CallHierarchyOutgoingCall[]> {
    assert.ok(item, 'Prepared call hierarchy item');
    return await client().sendRequest<CallHierarchyOutgoingCall[] | null>('callHierarchy/outgoingCalls', { item }) ?? [];
}

export function semanticCases(): void {
    playbook('X33', 'type definitions follow variable and return types', async workspace => {
        const document = await workspace.open('Lookups.x');
        await noErrors(document.uri);
        for (const [anchor, expected] of [['mapper.map', 'Mapper'], ['make();', 'TextMapper'], ['make() =', 'TextMapper']]) {
            assert.deepStrictEqual(await targetNames(await targets(document, 'TypeDefinition', position(document, anchor))), [expected]);
        }
    });

    playbook('X34', 'union and narrowed types have distinct source targets', async workspace => {
        const document = await workspace.open('Lookups.x');
        assert.deepStrictEqual((await targetNames(await targets(document, 'TypeDefinition', position(document, 'value.toString();', 0, true)))), ['TextMapper']);
        assert.deepStrictEqual((await targetNames(await targets(document, 'TypeDefinition', position(document, '        value.toString();', 8)))).sort(), ['TextMapper', 'Unrelated']);
    }, ['Multiple-target chooser and peek rendering']);

    playbook('X35', 'formal types resolve without invented library locations', async workspace => {
        const document = await workspace.open('Lookups.x');
        assert.deepStrictEqual(await targetNames(await targets(document, 'TypeDefinition', position(document, 'T value', 2))), ['T']);
        assert.deepStrictEqual(await targets(document, 'TypeDefinition', position(document, 'String map')), []);
    });

    playbook('X36', 'nominal implementation lookup excludes structural lookalikes', async workspace => {
        const document = await workspace.open('Lookups.x');
        assert.deepStrictEqual((await targetNames(await targets(document, 'Implementation', position(document, 'Mapper<T>')))).sort(),
            ['Child', 'Inherited', 'TextMapper']);
    });

    playbook('X37', 'method implementation lookup preserves overload identity', async workspace => {
        const document = await workspace.open('Lookups.x');
        const overrideLines = [...document.getText().matchAll(/@Override String map/g)].map(match => document.positionAt(match.index! + '@Override String '.length).line);
        assert.strictEqual(overrideLines.length, 2);
        for (const at of [position(document, 'T map', 2), position(document, 'mapper.map', 7)]) {
            const result = await targets(document, 'Implementation', at);
            assert.deepStrictEqual(result.map(item => item.range.start.line).sort((a, b) => a - b), overrideLines);
        }
        const child = await targets(document, 'Implementation', new vscode.Position(overrideLines[1], document.lineAt(overrideLines[1]).text.indexOf('map(')));
        assert.strictEqual(child.length, 1);
        assert.strictEqual(child[0].range.start.line, overrideLines[1]);
    });

    playbook('X38', 'cross-file semantic lookups track current member positions', async workspace => {
        const root = await workspace.project();
        await workspace.replace(root, fixture('Project.x').replace('module Project {', 'module Project {\n    Child make() = new Child();'));
        const lookup = () => targets(root, 'TypeDefinition', position(root, 'Child make'));
        const initial = await lookup();
        assert.strictEqual(initial.length, 1);
        assert.strictEqual(initial[0].uri.toString(), workspace.uri('Project/Child.x').toString());
        const implementations = await targets(root, 'Implementation', position(root, 'echo('));
        assert.strictEqual(implementations.length, 1);
        assert.strictEqual(implementations[0].uri.toString(), root.uri.toString());
        const child = await workspace.open('Project/Child.x');
        await workspace.replace(child, '\n\n' + fixture('Project/Child.x'));
        assert.strictEqual((await lookup())[0].range.start.line, initial[0].range.start.line + 2);
        await workspace.replace(child, 'class Child extends');
        assert.deepStrictEqual(await lookup(), []);
        await workspace.replace(child, fixture('Project/Child.x'));
        assert.strictEqual((await lookup())[0].range.start.line, initial[0].range.start.line);
    });

    playbook('X39', 'call hierarchy groups sites by caller and overload', async workspace => {
        const document = await workspace.open('Consumers.x');
        const [leaf] = await calls(document, position(document, 'Int leaf', 4));
        const result = await incoming(leaf);
        assert.strictEqual(result.length, 2);
        assert.strictEqual(result.find(item => item.from.name.includes('run'))?.fromRanges.length, 2);
        assert.strictEqual(result.find(item => item.from.name.includes('lambda'))?.fromRanges.length, 1);
        const [run] = await calls(document, position(document, 'Int run', 4));
        const edges = await outgoing(run);
        assert.strictEqual(edges.length, 2);
        assert.strictEqual(new Set(edges.map(item => item.to.selectionRange.start.line)).size, 2);
        assert.deepStrictEqual(edges.map(item => item.fromRanges.length).sort(), [1, 2]);
    }, ['Call hierarchy view rendering and click navigation']);

    playbook('X40', 'lambda calls retain their owner and dynamic calls have no static edge', async workspace => {
        const document = await workspace.open('Consumers.x');
        const [leaf] = await calls(document, position(document, 'Int leaf', 4));
        const lambda = (await incoming(leaf)).find(item => item.from.name.includes('lambda'));
        assert.ok(lambda);
        const edges = await outgoing(lambda.from);
        assert.strictEqual(edges.length, 1);
        assert.deepStrictEqual(edges[0].to.selectionRange, leaf.selectionRange);
        assert.strictEqual(edges[0].fromRanges[0].start.line, position(document, 'leaf(seed)').line);
        assert.deepStrictEqual(await calls(document, position(document, 'fn();')), []);
    });

    playbook('X41', 'semantic tokens and highlights distinguish identity and access', async workspace => {
        const document = await workspace.open('Consumers.x');
        const legend = (client().initializeResult!.capabilities.semanticTokensProvider as SemanticTokensOptions).legend;
        const result = await client().sendRequest<SemanticTokens>('textDocument/semanticTokens/full', { textDocument: { uri: document.uri.toString() } });
        let line = 0;
        let character = 0;
        const tokens = [];
        for (let offset = 0; offset < result.data.length; offset += 5) {
            const [deltaLine, deltaCharacter, length, type, mask] = result.data.slice(offset, offset + 5);
            line += deltaLine;
            character = deltaLine ? deltaCharacter : character + deltaCharacter;
            tokens.push({ line, character, text: document.getText(new vscode.Range(line, character, line, character + length)),
                type: legend.tokenTypes[type], modifiers: legend.tokenModifiers.filter((_, bit) => mask & (1 << bit)) });
        }
        for (const [name, type] of [['leaf', 'method'], ['seed', 'parameter'], ['number', 'variable']]) {
            assert.ok(tokens.some(token => token.text === name && token.type === type), JSON.stringify(tokens));
        }
        assert.ok(tokens.some(token => token.text === 'leaf' && token.modifiers.includes('static') && token.modifiers.includes('declaration')));
        const write = position(document, 'number +=');
        assert.ok(tokens.some(token => token.line === write.line && token.text === 'number' && token.modifiers.includes('modification')));
        const highlights = await vscode.commands.executeCommand<vscode.DocumentHighlight[]>('vscode.executeDocumentHighlights', document.uri, write);
        assert.ok(highlights?.some(item => item.range.contains(write) && item.kind === vscode.DocumentHighlightKind.Write));
        const read = position(document, 'return number', 7);
        assert.ok(highlights?.some(item => item.range.contains(read) && item.kind === vscode.DocumentHighlightKind.Read));
    }, ['Theme colors and the token inspector UI']);

    playbook('X42', 'inlay hints show inferred types and positional argument names', async workspace => {
        const document = await workspace.open('Consumers.x');
        const range = new vscode.Range(document.positionAt(0), document.positionAt(document.getText().length));
        const hints = await vscode.commands.executeCommand<vscode.InlayHint[]>('vscode.executeInlayHintProvider', document.uri, range);
        assert.ok(hints);
        const labels = hints.map(hint => typeof hint.label === 'string' ? hint.label : hint.label.map(part => part.value).join(''));
        for (const expected of ['Int', 'String', 'input:', 'text:']) { assert.ok(labels.some(label => label.includes(expected)), JSON.stringify(hints)); }
        assert.ok(!hints.some(hint => hint.position.line === position(document, 'number +=').line));
        assert.strictEqual(hints.filter(hint => hint.kind === vscode.InlayHintKind.Type).length, 2);
        assert.ok(!labels.some(label => label.includes('extra:')));
    }, ['Inline hint layout and visual distinction from source text']);

    playbook('X43', 'call hierarchy rejects old snapshots and recovers after parse failure', async workspace => {
        const document = await workspace.open('Consumers.x');
        const [old] = await calls(document, position(document, 'Int run', 4));
        await workspace.replace(document, fixture('Consumers.x').replace('    Int run', '\n    Int run'));
        assert.deepStrictEqual(await outgoing(old), []);
        const [fresh] = await calls(document, position(document, 'Int run', 4));
        assert.strictEqual(fresh.selectionRange.start.line, old.selectionRange.start.line + 1);
        assert.strictEqual((await outgoing(fresh)).length, 2);
        await workspace.replace(document, document.getText().replace('Int run', 'Int broken(\n Int run'));
        assert.deepStrictEqual(await outgoing(fresh), []);
        await workspace.replace(document, fixture('Consumers.x'));
        assert.strictEqual((await outgoing((await calls(document, position(document, 'Int run', 4)))[0])).length, 2);
        await workspace.discard(document);
        const reopened = await workspace.open('Consumers.x');
        assert.strictEqual((await outgoing((await calls(reopened, position(reopened, 'Int run', 4)))[0])).length, 2);
    });

    playbook('X44', 'incoming calls include closed members and current overlays', async workspace => {
        const member = fixture('Project/Child.x').replace('class Child extends Base<String> {', 'class Child extends Base<String> {\n    Int callTarget()=target(1);');
        await workspace.write('Project/Child.x', member);
        const root = await workspace.open('Project.x', fixture('Project.x').replace('module Project {', 'module Project {\n    static Int target(Int n)=n;'));
        await noErrors(root.uri);
        const [target] = await calls(root, position(root, 'Int target', 4));
        const [edge] = await incoming(target);
        assert.ok(edge);
        assert.strictEqual(vscode.Uri.parse(edge.from.uri).fsPath, workspace.uri('Project/Child.x').fsPath);
        const child = await workspace.open('Project/Child.x');
        await workspace.replace(child, '\n\n' + member);
        await symbols(root);
        assert.deepStrictEqual(await incoming(target), []);
        const [current] = await calls(root, position(root, 'Int target', 4));
        const [moved] = await incoming(current);
        assert.strictEqual(moved.fromRanges[0].start.line, edge.fromRanges[0].start.line + 2);
    });
}
