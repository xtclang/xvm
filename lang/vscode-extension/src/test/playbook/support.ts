import * as assert from 'node:assert';
import * as fs from 'node:fs/promises';
import * as path from 'node:path';
import * as vscode from 'vscode';
import { getClient } from '../../lsp-client';

export const manualPath = path.resolve(__dirname, '../../../../doc/manual-test-plan.md');
export const cases = new Map<string, { title: string; manual: string[] }>();
let fixtures: Map<string, string>;

export function client() {
    const result = getClient();
    assert.ok(result?.initializeResult, 'Compiler language client must be initialized');
    return result;
}

export async function eventually<T>(read: () => Promise<T>, accept: (value: T) => boolean, message: string): Promise<T> {
    const deadline = Date.now() + 30_000;
    let last: T;
    do {
        last = await read();
        if (accept(last)) { return last; }
        await new Promise(resolve => setTimeout(resolve, 75));
    } while (Date.now() < deadline);
    assert.fail(`${message}: ${JSON.stringify(last)}`);
}

export async function loadFixtures(): Promise<void> {
    const manual = await fs.readFile(manualPath, 'utf8');
    const blocks = [...manual.matchAll(/```xtc\n([\s\S]*?)\n```/g)].map(match => match[1]);
    fixtures = new Map();
    for (const name of ['Navigation', 'Editing', 'Project', 'Lookups', 'Consumers', 'Library', 'Consumer', 'Rename', 'Contracts', 'Uses', 'Dormant', 'Properties', 'Advanced', 'DupAnno']) {
        const matches = blocks.filter(text => new RegExp(`^module ${name}\\s*\\{`, 'm').test(text));
        assert.strictEqual(matches.length, 1, `One canonical ${name} fixture in playbook`);
        fixtures.set(`${name}.x`, matches[0] + '\n');
    }
    const children = blocks.filter(text => text.startsWith('class Child extends Base<String> {'));
    assert.strictEqual(children.length, 1);
    fixtures.set('Project/Child.x', children[0] + '\n');
    const ids = [...manual.matchAll(/^\| (X\d+) \|/gm)].map(match => match[1]);
    assert.deepStrictEqual([...cases.keys()].filter(id => /^X\d+$/.test(id)), ids,
        'Every XdkAdapter playbook row must have exactly one registered case, in playbook order');
}

export function fixture(file: string): string {
    const text = fixtures.get(file);
    assert.ok(text, `Missing fixture ${file}`);
    return text;
}

export function position(document: vscode.TextDocument, text: string, offset = 0, last = false): vscode.Position {
    const index = last ? document.getText().lastIndexOf(text) : document.getText().indexOf(text);
    assert.ok(index >= 0, `Missing anchor ${JSON.stringify(text)} in ${document.uri.fsPath}`);
    return document.positionAt(index + offset);
}

export function label(item: vscode.CompletionItem): string {
    return typeof item.label === 'string' ? item.label : item.label.label;
}

export function symbolNames(symbols: vscode.DocumentSymbol[]): string[] {
    return symbols.flatMap(symbol => [symbol.name, ...symbolNames(symbol.children ?? [])]);
}

export async function symbols(document: vscode.TextDocument): Promise<vscode.DocumentSymbol[]> {
    // This also acts as an analysis barrier after sibling edits or explicit close/reopen.
    // The execute command caches results at an unchanged editor version, so ask the
    // registered provider directly when the module snapshot may have changed.
    const provider = client().getFeature('textDocument/documentSymbol').getProvider(document);
    assert.ok(provider, 'Registered XTC document-symbol provider');
    const token = new vscode.CancellationTokenSource();
    try { return await provider.provideDocumentSymbols(document, token.token) as vscode.DocumentSymbol[] ?? []; }
    finally { token.dispose(); }
}

export async function hover(document: vscode.TextDocument, at: vscode.Position): Promise<string> {
    const result = await vscode.commands.executeCommand<vscode.Hover[]>('vscode.executeHoverProvider', document.uri, at);
    return (result ?? []).flatMap(item => item.contents.map(content => typeof content === 'string' ? content : content.value)).join('\n');
}

export async function targets(document: vscode.TextDocument, kind: 'Definition' | 'TypeDefinition' | 'Implementation', at: vscode.Position) {
    const result = await vscode.commands.executeCommand<(vscode.Location | vscode.LocationLink)[]>(`vscode.execute${kind}Provider`, document.uri, at);
    return (result ?? []).map(item => 'targetUri' in item
        ? new vscode.Location(item.targetUri, item.targetSelectionRange ?? item.targetRange)
        : item);
}

export async function targetNames(locations: vscode.Location[]): Promise<string[]> {
    return Promise.all(locations.map(async location => (await vscode.workspace.openTextDocument(location.uri)).getText(location.range)));
}

export async function noErrors(uri: vscode.Uri): Promise<void> {
    await diagnostics(uri, values => values.length === 0, 'Expected no diagnostics');
}

export async function diagnostics(uri: vscode.Uri, accept: (values: vscode.Diagnostic[]) => boolean, message: string) {
    return eventually(async () => vscode.languages.getDiagnostics(uri), accept, `${message} in ${uri.fsPath}`);
}

export function diagnosticCode(item: vscode.Diagnostic): string {
    return String(typeof item.code === 'object' ? item.code.value : item.code);
}

export async function nextProblem(document: vscode.TextDocument, values: vscode.Diagnostic[]): Promise<void> {
    await vscode.commands.executeCommand('workbench.actions.view.problems');
    const editor = await vscode.window.showTextDocument(document, { preview: false });
    editor.selection = new vscode.Selection(0, 0, 0, 0);
    await vscode.commands.executeCommand('editor.action.marker.nextInFiles');
    await eventually(async () => vscode.window.activeTextEditor,
        current => current?.document.uri.toString() === document.uri.toString() && values.some(item => item.range.contains(current.selection.start)),
        'Next problem selects the diagnostic source location');
    await vscode.commands.executeCommand('closeMarkersNavigation');
}

export class Workspace {
    private readonly detached = new Set<string>();

    constructor(readonly directory: string) {}

    uri(file: string): vscode.Uri { return vscode.Uri.file(path.join(this.directory, file)); }

    async write(file: string, text = fixture(file)): Promise<void> {
        const uri = this.uri(file);
        await fs.mkdir(path.dirname(uri.fsPath), { recursive: true });
        await fs.writeFile(uri.fsPath, text);
    }

    async open(file: string, text?: string): Promise<vscode.TextDocument> {
        if (text !== undefined) { await this.write(file, text); }
        else if (!await fs.stat(this.uri(file).fsPath).catch(() => undefined)) { await this.write(file); }
        let document = await vscode.workspace.openTextDocument(this.uri(file));
        if (document.languageId !== 'xtc') { document = await vscode.languages.setTextDocumentLanguage(document, 'xtc'); }
        await vscode.window.showTextDocument(document, { preview: false });
        if (this.detached.delete(document.uri.toString())) {
            const provider = client().getFeature('textDocument/didOpen').getProvider(document);
            assert.ok(provider, 'Registered document-open synchronization');
            await provider.send(document);
        }
        await eventually(() => symbols(document), values => values.length > 0, 'Initial document structure');
        return document;
    }

    async replace(document: vscode.TextDocument, text: string, settle = true): Promise<void> {
        const edit = new vscode.WorkspaceEdit();
        edit.replace(document.uri, new vscode.Range(document.positionAt(0), document.positionAt(document.getText().length)), text);
        assert.ok(await vscode.workspace.applyEdit(edit));
        if (settle) { await symbols(document); }
    }

    async marked(file: string, text: string): Promise<{ document: vscode.TextDocument; at: vscode.Position }> {
        const index = text.indexOf('§');
        assert.ok(index >= 0, 'Missing cursor marker');
        const document = await this.open(file);
        await this.replace(document, text.replace('§', ''));
        return { document, at: document.positionAt(index) };
    }

    async editing(body: string, inspect = false, transform: (text: string) => string = text => text) {
        const text = transform(fixture('Editing.x')).replace(inspect ? 'void inspect(T itemLocal) {}'
            : 'void run(Box<String> box, String itemParameter, Object value) {}',
        inspect ? `void inspect(T itemLocal) { ${body} }` : `void run(Box<String> box, String itemParameter, Object value) { ${body} }`);
        return this.marked('Editing.x', text);
    }

    async completion(document: vscode.TextDocument, at: vscode.Position): Promise<vscode.CompletionItem[]> {
        const result = await vscode.commands.executeCommand<vscode.CompletionList>('vscode.executeCompletionItemProvider', document.uri, at);
        return result?.items ?? [];
    }

    async accept(document: vscode.TextDocument, item: vscode.CompletionItem): Promise<void> {
        assert.ok(item.range, `Completion ${label(item)} must provide its source replacement range`);
        const range = item.range instanceof vscode.Range ? item.range : item.range.replacing;
        const value = item.insertText ?? label(item);
        assert.strictEqual(typeof value, 'string', 'These fixtures expect plain completion text');
        const edit = new vscode.WorkspaceEdit();
        edit.replace(document.uri, range, value as string);
        assert.ok(await vscode.workspace.applyEdit(edit));
        await symbols(document);
    }

    async signature(document: vscode.TextDocument, at: vscode.Position): Promise<vscode.SignatureHelp | undefined> {
        return vscode.commands.executeCommand<vscode.SignatureHelp>('vscode.executeSignatureHelpProvider', document.uri, at);
    }

    async project(): Promise<vscode.TextDocument> {
        await this.write('Project/Child.x');
        const document = await this.open('Project.x');
        await noErrors(document.uri);
        return document;
    }

    async configure(modules: { name: string; uri: string; dependencies?: string[] }[]): Promise<void> {
        await vscode.workspace.getConfiguration('xtc.compiler').update('sourceModules', modules, vscode.ConfigurationTarget.Workspace);
    }

    async dependencies(): Promise<vscode.TextDocument> {
        await this.write('Library.x');
        await this.write('Consumer.x');
        await this.configure([
            { name: 'Library', uri: this.uri('Library.x').toString() },
            { name: 'Consumer', uri: this.uri('Consumer.x').toString(), dependencies: ['Library'] }
        ]);
        const document = await this.open('Consumer.x');
        await noErrors(document.uri);
        return document;
    }

    async discard(document: vscode.TextDocument): Promise<void> {
        await vscode.window.showTextDocument(document, { preview: false });
        await vscode.commands.executeCommand('workbench.action.revertAndCloseActiveEditor');
        // Closing a tab can retain a hidden model. Use the client's public synchronization
        // provider so the server and client's synced-document map both observe didClose.
        if (!document.isClosed && !this.detached.has(document.uri.toString())) {
            const provider = client().getFeature('textDocument/didClose').getProvider(document);
            assert.ok(provider, 'Registered document-close synchronization');
            await provider.send(document);
            this.detached.add(document.uri.toString());
        }
        await client().sendRequest('xtc/healthCheck');
    }

    async dispose(): Promise<void> {
        // Every buffer and setting belongs to this test's isolated VS Code workspace.
        for (const document of vscode.workspace.textDocuments.filter(doc => doc.uri.fsPath.startsWith(this.directory + path.sep) && doc.languageId === 'xtc')) {
            await this.discard(document);
        }
        await vscode.commands.executeCommand('workbench.action.closeAllEditors');
        await this.configure([]);
    }
}

export function playbook(id: string, title: string, body: (workspace: Workspace) => Promise<void>, manual: string[] = []): void {
    assert.ok(!cases.has(id), `Duplicate playbook case ${id}`);
    cases.set(id, { title, manual });
    test(`${id}: ${title}`, async function () {
        this.timeout(90_000);
        const root = vscode.workspace.workspaceFolders?.[0].uri.fsPath;
        assert.ok(root);
        const workspace = new Workspace(path.join(root, id));
        try { await body(workspace); }
        catch (error) {
            const report = process.env.XTC_PLAYBOOK_REPORT_DIR;
            if (report) {
                for (const document of vscode.workspace.textDocuments.filter(doc => doc.uri.fsPath.startsWith(workspace.directory + path.sep))) {
                    const file = path.join(report, 'failure-sources', id, path.relative(workspace.directory, document.uri.fsPath));
                    await fs.mkdir(path.dirname(file), { recursive: true });
                    await fs.writeFile(file, document.getText());
                }
            }
            throw error;
        }
        finally { await workspace.dispose(); }
    });
}
