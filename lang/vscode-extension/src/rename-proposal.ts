import * as vscode from 'vscode';
import { LanguageClient, RenameFile, RenameFilesParams, RenameParams, RequestType, TextDocumentEdit, WorkspaceEdit } from 'vscode-languageclient/node';
import { SourceModule, sourceGraphEdits, sourceGraphKey, sourceModulesIn } from './source-graph-configuration';

interface RenameProposal {
    edit: WorkspaceEdit;
    graph?: { before: SourceModule[]; after: SourceModule[] };
}
const renameRequest = new RequestType<RenameParams, RenameProposal | null, void>('xtc/rename');
const moveRequest = new RequestType<RenameFilesParams, RenameProposal | null, void>('xtc/renameFiles');

/** VS Code's native Rename saves affected files when files.refactoring.autoSave is enabled.
 * Without that save, moving an edited model can lose its text Undo. Reordering the operations
 * fixes Undo but breaks Redo, so refuse this unsupported editor configuration before editing.
 */
function editsMovedSource(edit: WorkspaceEdit): boolean {
    const changes = edit.documentChanges ?? [];
    const moves = changes.filter(RenameFile.is).map(move => new URL(move.oldUri).href.replace(/\/$/, ''));
    return changes.filter(TextDocumentEdit.is).some(change => {
        const uri = new URL(change.textDocument.uri).href;
        return moves.some(from => uri === from || uri.startsWith(from + '/'));
    });
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

/** Unsaved settings participate in the same transaction and Undo as unsaved source documents. */
export function compilerSourceModules(): unknown[] | null {
    const location = compilerSettingsLocation();
    const document = location && vscode.workspace.textDocuments.find(document => !document.isClosed && document.uri.toString() === location.uri.toString());
    // The document is current even immediately after a save, before VS Code's settings
    // watcher has refreshed getConfiguration(). Falling back then briefly reinstalls the old graph.
    if (document) {
        const value = sourceModulesIn(document.getText(), location!.path);
        if (value === null || Array.isArray(value)) return value;
    }
    return vscode.workspace.getConfiguration('xtc.compiler').get<unknown[] | null>('sourceModules', null);
}

/** Settings scopes and workspace topology are part of the proposal's immutable input. */
function configurationState(uri: vscode.Uri): string {
    return JSON.stringify({
        workspace: vscode.workspace.workspaceFile?.toString(),
        folders: vscode.workspace.workspaceFolders?.map(folder => folder.uri.toString()),
        graph: compilerSourceModules(),
        overrides: folderOverrides(uri)
    });
}

function folderOverrides(uri: vscode.Uri): unknown[] {
    // In a single-folder window, .vscode/settings.json is the workspace graph itself.
    // VS Code also reports it as workspaceFolderValue when queried with that folder's URI.
    if (!vscode.workspace.workspaceFile && vscode.workspace.workspaceFolders?.length === 1) return [];
    // In a workspace file, each folder can override the graph independently.
    const roots = vscode.workspace.workspaceFolders?.map(folder => folder.uri) ?? [uri];
    return roots.map(uri => vscode.workspace.getConfiguration('xtc.compiler', uri).inspect('sourceModules')?.workspaceFolderValue);
}

export async function renameWithConfiguration(
    client: LanguageClient,
    document: vscode.TextDocument,
    position: vscode.Position,
    newName: string,
    token: vscode.CancellationToken,
): Promise<vscode.WorkspaceEdit | null> {
    return proposalWithConfiguration(client, document.uri, token,
        () => client.sendRequest(renameRequest, {
            textDocument: client.code2ProtocolConverter.asTextDocumentIdentifier(document),
            position: client.code2ProtocolConverter.asPosition(position), newName
        }, token).catch(error => client.handleFailedRequest(renameRequest, token, error, null, false)));
}

/** File-operation participation contributes only extra edits; VS Code owns the requested moves. */
export async function moveWithConfiguration(client: LanguageClient, event: vscode.FileWillRenameEvent): Promise<vscode.WorkspaceEdit | null> {
    if (!event.files.length) return null;
    const settings = compilerSettingsLocation()?.uri.toString();
    const owned = [...(vscode.workspace.workspaceFolders ?? []).map(folder => folder.uri.toString()), ...(settings ? [settings] : [])];
    if (event.files.some(move => owned.some(uri => uri === move.oldUri.toString() || uri.startsWith(move.oldUri.toString().replace(/\/$/, '') + '/')))) {
        throw new Error('Moving workspace roots or their settings requires reopening the workspace.');
    }
    return proposalWithConfiguration(client, event.files[0].oldUri, event.token,
        () => client.sendRequest(moveRequest, {
            files: event.files.map(move => ({ oldUri: move.oldUri.toString(), newUri: move.newUri.toString() }))
        }, event.token).catch(error => client.handleFailedRequest(moveRequest, event.token, error, null, false)),
        event.files.map(move => move.oldUri.toString()));
}

async function proposalWithConfiguration(
    client: LanguageClient,
    uri: vscode.Uri,
    token: vscode.CancellationToken,
    request: () => Promise<RenameProposal | null>,
    hostMoves: readonly string[] = [],
): Promise<vscode.WorkspaceEdit | null> {
    const documents = vscode.workspace.textDocuments.filter(value => !value.isClosed && value.languageId === 'xtc')
        .map(document => ({ document, version: document.version }));
    const location = compilerSettingsLocation();
    const config = compilerSourceModules();
    const state = configurationState(uri);
    const folders = vscode.workspace.workspaceFolders;
    const base = folders?.length === 1 ? folders[0].uri.toString().replace(/\/?$/, '/') : undefined;
    const settings = config && location ? await vscode.workspace.openTextDocument(location.uri).then(value => value, () => undefined) : undefined;
    const snapshot = settings && { version: settings.version, text: settings.getText() };
    const proposal = await request();
    if (!proposal || token.isCancellationRequested) return null;
    if (proposal.graph) {
        if (!settings || !snapshot || !location || !Array.isArray(config)
            || folderOverrides(uri).some(value => value !== undefined)
            || sourceGraphKey(config, base) !== sourceGraphKey(proposal.graph.before, base)) {
            throw new Error('This refactoring needs an explicit graph in this workspace’s settings. Global or folder overrides cannot be rewritten safely.');
        }
        if (settings.isClosed || settings.version !== snapshot.version || configurationState(uri) !== state) {
            throw new Error('Compiler settings changed while calculating the refactoring. Try again.');
        }
        const edits = sourceGraphEdits(snapshot.text, location.path, proposal.graph.before, proposal.graph.after, base);
        const configurationEdit: TextDocumentEdit = {
            textDocument: { uri: settings.uri.toString(), version: snapshot.version },
            edits: edits.map(edit => ({
                range: client.code2ProtocolConverter.asRange(new vscode.Range(settings.positionAt(edit.offset), settings.positionAt(edit.offset + edit.length))),
                newText: edit.content
            }))
        };
        proposal.edit = { ...proposal.edit, documentChanges: [...(proposal.edit.documentChanges ?? []), configurationEdit] };
    }
    if (editsMovedSource(proposal.edit) && !vscode.workspace.getConfiguration('files.refactoring').get('autoSave', true)) {
        throw new Error('Moving an edited source file requires files.refactoring.autoSave in VS Code so Undo and Redo preserve its contents.');
    }
    const originals = new Set(hostMoves.map(uri => new URL(uri).href.replace(/\/$/, '')));
    const edit = { ...proposal.edit, documentChanges: proposal.edit.documentChanges?.filter(change =>
        !RenameFile.is(change) || !originals.has(new URL(change.oldUri).href.replace(/\/$/, ''))) };
    const converted = await client.protocol2CodeConverter.asWorkspaceEdit(edit, token);
    if (token.isCancellationRequested) return null;
    if (proposal.graph && (settings?.isClosed || settings?.version !== snapshot?.version || configurationState(uri) !== state)) {
        throw new Error('Compiler settings changed while converting the refactoring. Try again.');
    }
    if (documents.some(({ document, version }) => document.isClosed || document.version !== version)
        || vscode.workspace.textDocuments.filter(value => !value.isClosed && value.languageId === 'xtc').length !== documents.length) {
        throw new Error('Source documents changed while calculating the refactoring. Try again.');
    }
    if (!client.validateWorkspaceEdit(proposal.edit)) throw new Error('Documents changed while calculating the refactoring. Try again.');
    return converted ?? null;
}
