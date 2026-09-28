import * as vscode from 'vscode';
import { LanguageClient, RenameFile, RenameParams, RequestType, TextDocumentEdit, WorkspaceEdit } from 'vscode-languageclient/node';
import { SourceModule, sourceGraphEdits, sourceGraphKey, sourceModulesIn } from './source-graph-configuration';

interface RenameProposal {
    edit: WorkspaceEdit;
    graph?: { before: SourceModule[]; after: SourceModule[] };
}
const renameRequest = new RequestType<RenameParams, RenameProposal | null, void>('xtc/rename');

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
function configurationState(document: vscode.TextDocument): string {
    return JSON.stringify({
        workspace: vscode.workspace.workspaceFile?.toString(),
        folders: vscode.workspace.workspaceFolders?.map(folder => folder.uri.toString()),
        graph: compilerSourceModules(),
        override: vscode.workspace.getConfiguration('xtc.compiler', document.uri).inspect('sourceModules')?.workspaceFolderValue
    });
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
    const state = configurationState(document);
    const folders = vscode.workspace.workspaceFolders;
    const base = folders?.length === 1 ? folders[0].uri.toString().replace(/\/?$/, '/') : undefined;
    const settings = config && location ? await vscode.workspace.openTextDocument(location.uri).then(value => value, () => undefined) : undefined;
    const snapshot = settings && { version: settings.version, text: settings.getText() };
    const proposal = await client.sendRequest(renameRequest, {
        textDocument: client.code2ProtocolConverter.asTextDocumentIdentifier(document),
        position: client.code2ProtocolConverter.asPosition(position), newName
    }, token).catch(error => client.handleFailedRequest(renameRequest, token, error, null, false));
    if (!proposal || token.isCancellationRequested) return null;
    if (proposal.graph) {
        if (!settings || !snapshot || !location || !Array.isArray(config)
            || vscode.workspace.getConfiguration('xtc.compiler', document.uri).inspect('sourceModules')?.workspaceFolderValue !== undefined
            || sourceGraphKey(config, base) !== sourceGraphKey(proposal.graph.before, base)) {
            throw new Error('Module rename needs an explicit graph in this workspace’s settings. Global or folder overrides cannot be rewritten safely.');
        }
        if (settings.isClosed || settings.version !== snapshot.version || configurationState(document) !== state) {
            throw new Error('Compiler settings changed while calculating Rename. Try again.');
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
        throw new Error('Renaming an edited source file requires files.refactoring.autoSave in VS Code so Undo and Redo preserve its contents.');
    }
    const converted = await client.protocol2CodeConverter.asWorkspaceEdit(proposal.edit, token);
    if (token.isCancellationRequested) return null;
    if (proposal.graph && (settings?.isClosed || settings?.version !== snapshot?.version || configurationState(document) !== state)) {
        throw new Error('Compiler settings changed while converting Rename. Try again.');
    }
    if (!client.validateWorkspaceEdit(proposal.edit)) throw new Error('Documents changed while calculating Rename. Try again.');
    return converted ?? null;
}
