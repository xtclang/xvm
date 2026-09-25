import * as assert from 'node:assert';
import * as vscode from 'vscode';
import { CallHierarchyIncomingCall, CallHierarchyItem, CallHierarchyOutgoingCall, SemanticTokens, SemanticTokensOptions } from 'vscode-languageclient/node';
import { scenarioRegex } from './shared';
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
    playbook('X33', async (workspace, data) => {
        const document = await workspace.open(data.file);
        await noErrors(document.uri);
        for (const { anchor, expected } of data.variants) {
            assert.deepStrictEqual(await targetNames(await targets(document, 'TypeDefinition', position(document, anchor))), [expected]);
        }
    });

    playbook('X34', async (workspace, data) => {
        const document = await workspace.open(data.file);
        assert.deepStrictEqual((await targetNames(await targets(document, 'TypeDefinition', position(document, data.narrowedUse, data.narrowedOffset, true)))), data.narrowedTargets);
        assert.deepStrictEqual((await targetNames(await targets(document, 'TypeDefinition', position(document, data.unionUse, data.unionOffset)))).sort(), data.unionTargets);
    });

    playbook('X35', async (workspace, data) => {
        const document = await workspace.open(data.file);
        assert.deepStrictEqual(await targetNames(await targets(document, 'TypeDefinition', position(document, data.formalUse, data.offset))), data.formalTargets);
        assert.deepStrictEqual(await targets(document, 'TypeDefinition', position(document, data.binaryUse)), []);
    });

    playbook('X36', async (workspace, data) => {
        const document = await workspace.open(data.file);
        assert.deepStrictEqual((await targetNames(await targets(document, 'Implementation', position(document, data.anchor)))).sort(),
            data.implementations);
    });

    playbook('X37', async (workspace, data) => {
        const document = await workspace.open(data.file);
        const overrideLines = [...document.getText().matchAll(scenarioRegex(data.overrides))].map(match => document.positionAt(match.index! + data.declarationPrefix.length).line);
        assert.strictEqual(overrideLines.length, data.declarationOffset);
        for (const at of [position(document, data.declaration, data.declarationOffset), position(document, data.use, data.useOffset)]) {
            const result = await targets(document, 'Implementation', at);
            assert.deepStrictEqual(result.map(item => item.range.start.line).sort((a, b) => a - b), overrideLines);
        }
        const child = await targets(document, 'Implementation', new vscode.Position(overrideLines[1], document.lineAt(overrideLines[1]).text.indexOf(data.methodName)));
        assert.strictEqual(child.length, data.targetCount);
        assert.strictEqual(child[0].range.start.line, overrideLines[1]);
    });

    playbook('X38', async (workspace, data) => {
        const root = await workspace.project();
        await workspace.replace(root, fixture(data.rootFile).replace(data.replaceFrom, data.rootWithFactory));
        const lookup = () => targets(root, 'TypeDefinition', position(root, data.factory));
        const initial = await lookup();
        assert.strictEqual(initial.length, data.targetCount);
        assert.strictEqual(initial[0].uri.toString(), workspace.uri(data.memberFile).toString());
        const implementations = await targets(root, 'Implementation', position(root, data.method));
        assert.strictEqual(implementations.length, data.targetCount);
        assert.strictEqual(implementations[0].uri.toString(), root.uri.toString());
        const child = await workspace.open(data.memberFile);
        await workspace.replace(child, '\n\n' + fixture(data.memberFile));
        assert.strictEqual((await lookup())[0].range.start.line, initial[0].range.start.line + data.lineShift);
        await workspace.replace(child, data.replaceWith);
        assert.deepStrictEqual(await lookup(), []);
        await workspace.replace(child, fixture(data.memberFile));
        assert.strictEqual((await lookup())[0].range.start.line, initial[0].range.start.line);
    });

    playbook('X39', async (workspace, data) => {
        const document = await workspace.open(data.file);
        const [leaf] = await calls(document, position(document, data.callee, data.offset));
        const result = await incoming(leaf);
        assert.strictEqual(result.length, data.edgeCount);
        assert.strictEqual(result.find(item => item.from.name.includes(data.callerName))?.fromRanges.length, data.edgeCount);
        assert.strictEqual(result.find(item => item.from.name.includes(data.lambdaName))?.fromRanges.length, data.lambdaSites);
        const [run] = await calls(document, position(document, data.caller, data.offset));
        const edges = await outgoing(run);
        assert.strictEqual(edges.length, data.edgeCount);
        assert.strictEqual(new Set(edges.map(item => item.to.selectionRange.start.line)).size, data.edgeCount);
        assert.deepStrictEqual(edges.map(item => item.fromRanges.length).sort(), (data.sitesPerCallee as (number)[]));
    });

    playbook('X40', async (workspace, data) => {
        const document = await workspace.open(data.file);
        const [leaf] = await calls(document, position(document, data.callee, data.offset));
        const lambda = (await incoming(leaf)).find(item => item.from.name.includes(data.lambdaName));
        assert.ok(lambda);
        const edges = await outgoing(lambda.from);
        assert.strictEqual(edges.length, data.edgeCount);
        assert.deepStrictEqual(edges[0].to.selectionRange, leaf.selectionRange);
        assert.strictEqual(edges[0].fromRanges[0].start.line, position(document, data.lambdaCall).line);
        assert.deepStrictEqual(await calls(document, position(document, data.dynamicCall)), []);
    });

    playbook('X41', async (workspace, data) => {
        const document = await workspace.open(data.file);
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
        for (const { name, type } of data.tokenKinds) {
            assert.ok(tokens.some(token => token.text === name && token.type === type), JSON.stringify(tokens));
        }
        assert.ok(tokens.some(token => token.text === data.methodName && token.modifiers.includes(data.staticModifier) && token.modifiers.includes(data.declarationModifier)));
        const write = position(document, data.anchor2);
        assert.ok(tokens.some(token => token.line === write.line && token.text === data.variableName && token.modifiers.includes(data.writeModifier)));
        const highlights = await vscode.commands.executeCommand<vscode.DocumentHighlight[]>('vscode.executeDocumentHighlights', document.uri, write);
        assert.ok(highlights?.some(item => item.range.contains(write) && item.kind === vscode.DocumentHighlightKind.Write));
        const read = position(document, data.anchor, data.offset);
        assert.ok(highlights?.some(item => item.range.contains(read) && item.kind === vscode.DocumentHighlightKind.Read));
    });

    playbook('X42', async (workspace, data) => {
        const document = await workspace.open(data.file);
        const range = new vscode.Range(document.positionAt(0), document.positionAt(document.getText().length));
        const hints = await vscode.commands.executeCommand<vscode.InlayHint[]>('vscode.executeInlayHintProvider', document.uri, range);
        assert.ok(hints);
        const labels = hints.map(hint => typeof hint.label === 'string' ? hint.label : hint.label.map(part => part.value).join(''));
        for (const expected of data.hints) { assert.ok(labels.some(label => label.includes(expected)), JSON.stringify(hints)); }
        assert.ok(!hints.some(hint => hint.position.line === position(document, data.anchor).line));
        assert.strictEqual(hints.filter(hint => hint.kind === vscode.InlayHintKind.Type).length, data.typeHintCount);
        assert.ok(!labels.some(label => label.includes(data.excludedHint)));
    });

    playbook('X43', async (workspace, data) => {
        const document = await workspace.open(data.file);
        const [old] = await calls(document, position(document, data.anchor, data.offset));
        await workspace.replace(document, fixture(data.file).replace(data.replaceFrom, data.shiftedDeclaration));
        assert.deepStrictEqual(await outgoing(old), []);
        const [fresh] = await calls(document, position(document, data.anchor, data.offset));
        assert.strictEqual(fresh.selectionRange.start.line, old.selectionRange.start.line + data.lineShift);
        assert.strictEqual((await outgoing(fresh)).length, data.edgeCount);
        await workspace.replace(document, document.getText().replace(data.anchor, data.replaceWith));
        assert.deepStrictEqual(await outgoing(fresh), []);
        await workspace.replace(document, fixture(data.file));
        assert.strictEqual((await outgoing((await calls(document, position(document, data.anchor, data.offset)))[0])).length, data.edgeCount);
        await workspace.discard(document);
        const reopened = await workspace.open(data.file);
        assert.strictEqual((await outgoing((await calls(reopened, position(reopened, data.anchor, data.offset)))[0])).length, data.edgeCount);
    });

    playbook('X44', async (workspace, data) => {
        const member = fixture(data.memberFile).replace(data.memberDeclaration, data.memberWithCall);
        await workspace.write(data.memberFile, member);
        const root = await workspace.open(data.rootFile, fixture(data.rootFile).replace(data.replaceFrom, data.replaceWith));
        await noErrors(root.uri);
        const [target] = await calls(root, position(root, data.anchor, data.offset));
        const [edge] = await incoming(target);
        assert.ok(edge);
        assert.strictEqual(vscode.Uri.parse(edge.from.uri).fsPath, workspace.uri(data.memberFile).fsPath);
        const child = await workspace.open(data.memberFile);
        await workspace.replace(child, '\n\n' + member);
        await symbols(root);
        assert.deepStrictEqual(await incoming(target), []);
        const [current] = await calls(root, position(root, data.anchor, data.offset));
        const [moved] = await incoming(current);
        assert.strictEqual(moved.fromRanges[0].start.line, edge.fromRanges[0].start.line + data.lineShift);
    });
}
