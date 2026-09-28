import { modify, parse, ParseError } from 'jsonc-parser';
import * as vscode from 'vscode';
import { LanguageClient, TextDocumentEdit, WorkspaceEdit } from 'vscode-languageclient/node';

interface SourceModule { name: string; uri: string; dependencies?: string[] }
interface RenameProposal {
    edit: WorkspaceEdit;
    graph?: { before: SourceModule[]; after: SourceModule[] };
}

/** Workspace settings only: a refactoring must not rewrite settings shared by other projects. */
export function compilerSettingsLocation(): { uri: vscode.Uri; path: string[] } | undefined {
    if (vscode.workspace.workspaceFile?.scheme === 'file') {
        return { uri: vscode.workspace.workspaceFile, path: ['settings', 'xtc.compiler.sourceModules'] };
    }
    const folders = vscode.workspace.workspaceFolders;
    if (folders?.length !== 1) return undefined;
    return { uri: vscode.Uri.joinPath(folders[0].uri, '.vscode', 'settings.json'), path: ['xtc.compiler.sourceModules'] };
}

function modulesIn(text: string, path: string[]): unknown {
    const errors: ParseError[] = [];
    const value = parse(text, errors, { allowTrailingComma: true });
    if (errors.length) throw new Error('Fix the workspace settings JSON before renaming a configured module.');
    return path.reduce((current, key) => current?.[key], value);
}

/** Unsaved settings participate in the same transaction and Undo as unsaved source documents. */
export function compilerSourceModules(): unknown[] | null {
    const location = compilerSettingsLocation();
    const document = location && vscode.workspace.textDocuments.find(document => !document.isClosed && document.uri.toString() === location.uri.toString());
    if (document?.isDirty) {
        const value = modulesIn(document.getText(), location!.path);
        if (value === null || Array.isArray(value)) return value;
    }
    return vscode.workspace.getConfiguration('xtc.compiler').get<unknown[] | null>('sourceModules', null);
}

function canonical(modules: SourceModule[]): string {
    const folders = vscode.workspace.workspaceFolders;
    return JSON.stringify(modules.map(module => ({
        name: module.name,
        uri: new URL(module.uri, folders?.length === 1 ? folders[0].uri.toString().replace(/\/?$/, '/') : undefined).href,
        dependencies: [...(module.dependencies ?? [])].sort()
    })).sort((left, right) => left.name.localeCompare(right.name)));
}

export async function renameWithConfiguration(
    client: LanguageClient,
    document: vscode.TextDocument,
    position: vscode.Position,
    newName: string,
    token: vscode.CancellationToken,
): Promise<vscode.WorkspaceEdit | null> {
    const location = compilerSettingsLocation();
    const config = compilerSourceModules();
    const settings = config && location ? await vscode.workspace.openTextDocument(location.uri).then(value => value, () => undefined) : undefined;
    const snapshot = settings && { version: settings.version, text: settings.getText() };
    const proposal = await client.sendRequest<RenameProposal | null>('xtc/rename', {
        textDocument: client.code2ProtocolConverter.asTextDocumentIdentifier(document),
        position: client.code2ProtocolConverter.asPosition(position), newName
    }, token);
    if (!proposal || token.isCancellationRequested) return null;
    if (proposal.graph) {
        if (!settings || !snapshot || !location || !Array.isArray(config)
            || !Array.isArray(modulesIn(snapshot.text, location.path))
            || canonical(modulesIn(snapshot.text, location.path) as SourceModule[]) !== canonical(proposal.graph.before)
            || canonical(config as SourceModule[]) !== canonical(proposal.graph.before)) {
            throw new Error('Module rename needs an explicit graph in this workspace’s settings. Global or folder overrides cannot be rewritten safely.');
        }
        if (settings.isClosed || settings.version !== snapshot.version || JSON.stringify(compilerSourceModules()) !== JSON.stringify(config)) {
            throw new Error('Compiler settings changed while calculating Rename. Try again.');
        }
        const edits = modify(snapshot.text, location.path, proposal.graph.after, { formattingOptions: { insertSpaces: true, tabSize: 4 } });
        const configurationEdit: TextDocumentEdit = {
            textDocument: { uri: settings.uri.toString(), version: snapshot.version },
            edits: edits.map(edit => ({
                range: client.code2ProtocolConverter.asRange(new vscode.Range(settings.positionAt(edit.offset), settings.positionAt(edit.offset + edit.length))),
                newText: edit.content
            }))
        };
        proposal.edit = { ...proposal.edit, documentChanges: [...(proposal.edit.documentChanges ?? []), configurationEdit] };
    }
    const converted = await client.protocol2CodeConverter.asWorkspaceEdit(proposal.edit, token);
    if (token.isCancellationRequested) return null;
    if (!client.validateWorkspaceEdit(proposal.edit)) throw new Error('Documents changed while calculating Rename. Try again.');
    return converted ?? null;
}
