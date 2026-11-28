import { createHash } from 'node:crypto';
import * as vscode from 'vscode';
import {
    LanguageClient, LSPErrorCodes, RenameFile, RenameFilesParams, RenameParams, RequestType, ResponseError, TextDocumentEdit, WorkspaceEdit
} from 'vscode-languageclient/node';
import { SourceModule, sourceGraphEdits, sourceGraphKey, sourceModulesIn } from './source-graph-configuration';

interface RenameProposal {
    edit: WorkspaceEdit;
    graph?: { before: SourceModule[]; after: SourceModule[] };
    /**
     * The hash ([textHash]) of the text that each document the edit changes had as the server computed its changes, an
     * editor's buffer or the file on the disk, by the URI the edit names it with; none when the server proves no text.
     */
    textHashes?: Record<string, string>;
}
const renameRequest = new RequestType<RenameParams, RenameProposal | null, void>('xtc/rename');
const moveRequest = new RequestType<RenameFilesParams, RenameProposal | null, void>('xtc/renameFiles');

/**
 * How many times a move asks the server again for its edits when a change of the project overtook its answer, as the
 * server's ContentModified says: that refuses the proof it was working on, not the move.
 */
const MOVE_RETRIES = 3;

/**
 * A proof that a change overtook while it ran: documents changed ([sourcesChanged]), or the compiler settings did. A
 * rename shows its message; a move, which VS Code makes whatever its participants answer, asks the server again.
 */
class Overtaken extends Error {}

/** An open Ecstasy document as a refactoring's proof started: its version, and whether it had unsaved edits. */
export interface SourceState {
    document: vscode.TextDocument;
    version: number;
    dirty: boolean;
}

/**
 * Whether the text of Ecstasy's documents that the server proved a refactoring from changed since [before], now that
 * [open] are open: one of them was edited, or closed with unsaved edits, or one opened with unsaved edits. VS Code
 * releases a document that no editor shows some minutes after an extension or a navigation opened it, and opens one
 * for a peek or a search; either leaves its text as its file has it, which the server reads of a closed file, so
 * neither overtakes the proof.
 */
export function sourcesChanged(before: readonly SourceState[], open: readonly vscode.TextDocument[]): boolean {
    return before.some(({ document, version, dirty }) => document.version !== version || (document.isClosed && dirty))
        || open.some(document => document.isDirty && !before.some(state => state.document === document));
}

/**
 * The hash of [text] that a proposal's textHashes names, as the server and IntelliJ's plugin compute it
 * (RenameProposal.textHash): the SHA-256 of its UTF-8, as lowercase hex, with each of its line breaks written as `\n`, as
 * an LSP position counts lines alike whatever their line breaks.
 */
export function textHash(text: string): string {
    return createHash('sha256').update(text.replace(/\r\n?/g, '\n'), 'utf8').digest('hex');
}

/**
 * Whether a document that the server's [edit] changes no longer has the text that the server computed its changes
 * against, which [hashes] names: the text that VS Code's application of the edit changes, an open document's, or else
 * its file's as VS Code reads it from the disk, as the server read it, in its encoding and without a byte order mark,
 * with no version to say that it changed since. A document whose hash is not named must be one of [read], open as the
 * proof started, whose version VS Code checks: a server that proves no text, or its adapter that edits only the
 * document of a rename, read no other.
 */
async function textsChanged(edit: WorkspaceEdit, hashes: Record<string, string> | undefined, read: readonly SourceState[]): Promise<boolean> {
    const uris = [...Object.keys(edit.changes ?? {}), ...(edit.documentChanges ?? []).filter(TextDocumentEdit.is).map(change => change.textDocument.uri)];
    for (const uri of new Set(uris)) {
        const file = vscode.Uri.parse(uri);
        const document = vscode.workspace.textDocuments.find(value => !value.isClosed && value.uri.toString() === file.toString());
        const hash = hashes?.[uri];
        if (hash === undefined) {
            if (!document || !read.some(state => state.document === document)) return true;
            continue;
        }
        const text = await vscode.workspace.openTextDocument(file).then(value => value.getText(), () => undefined);
        if (text === undefined || textHash(text) !== hash) return true;
    }
    return false;
}

/**
 * What a refactoring says when a document that its edit changes no longer has the text that the server proved the edit
 * against, as a file that changed on the disk since the server read it.
 */
export const TEXTS_CHANGED = 'Source files changed since the language server read them for this refactoring. Try again.';

/**
 * Whether the server's [error] says that a change of the project overtook its proof: ContentModified, or a cancellation
 * the client did not ask for, as when a document's close retires the server's graph queries, the move's among them.
 */
function overtakenAnswer(error: unknown, token: vscode.CancellationToken): boolean {
    if (!(error instanceof ResponseError)) return false;
    return error.code === LSPErrorCodes.ContentModified || (!token.isCancellationRequested
        && (error.code === LSPErrorCodes.RequestCancelled || error.code === LSPErrorCodes.ServerCancelled));
}

/**
 * Whether the server's [error] refuses the proof for a reason that it gives, as RequestFailed's message: as for a move
 * that it can't prove while a module does not compile, or while discovery of the workspace's modules has a problem.
 */
function refusedAnswer(error: unknown): boolean {
    return error instanceof ResponseError && error.code === LSPErrorCodes.RequestFailed;
}

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

/**
 * What VS Code says when the server answers a rename with no edit, in the words of IntelliJ's plugin: the server answers
 * so for a rename that it refuses after prepareRename accepted the name, as one that would change what a name means.
 */
export const RENAME_REFUSED = 'This rename is not supported or would change another binding.';

/**
 * What VS Code says when the server answers a rename with ContentModified: a change of the project overtook its proof, as
 * an edit or a file written on disk does while it compiles, and the same rename asked again proves the project as it then
 * is. It is not asked again here, where nothing says when the change has settled: the user renames again.
 */
export const RENAME_OVERTAKEN = 'The project changed while the language server proved this rename. Rename again.';

/**
 * The edit of a rename at [position] to [newName], as the server proposes it with xtc/rename. VS Code shows the message
 * of an error that a rename provider throws, and says only "No result." when it answers nothing: a refusal that the
 * server gives its reason for, an error, keeps that reason, as one that it can't prove while a module does not compile,
 * its answer of no edit fails with [RENAME_REFUSED], and its ContentModified with [RENAME_OVERTAKEN]. A request that the
 * user cancels answers nothing.
 */
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
        }, token).then(proposal => {
            if (!proposal && !token.isCancellationRequested) throw new Error(RENAME_REFUSED);
            return proposal;
        }, error => {
            // the language client takes ContentModified as no answer, which VS Code shows as "No result."
            if (error instanceof ResponseError && error.code === LSPErrorCodes.ContentModified && !token.isCancellationRequested) {
                throw new Error(RENAME_OVERTAKEN);
            }
            return client.handleFailedRequest(renameRequest, token, error, null, false);
        }));
}

/**
 * The moved sources among [moved], files or folders, whose types the project uses elsewhere, as the server's references
 * of each file's declaration say.
 */
export async function usedElsewhere(moved: readonly vscode.Uri[], token: vscode.CancellationToken): Promise<vscode.Uri[]> {
    const sources = (await Promise.all(moved.map(uri => uri.path.endsWith('.x') ? [uri]
        : vscode.workspace.findFiles(new vscode.RelativePattern(uri, '**/*.x'), undefined, 100, token)))).flat();
    const used: vscode.Uri[] = [];
    for (const source of sources) {
        if (token.isCancellationRequested) break;
        const symbols = await vscode.commands.executeCommand<(vscode.DocumentSymbol | vscode.SymbolInformation)[] | undefined>(
            'vscode.executeDocumentSymbolProvider', source).then(value => value ?? [], () => []);
        const declaration = symbols[0];
        if (!declaration) continue;
        const at = 'selectionRange' in declaration ? declaration.selectionRange.start : declaration.location.range.start;
        const references = await vscode.commands.executeCommand<vscode.Location[] | undefined>('vscode.executeReferenceProvider', source, at)
            .then(value => value ?? [], () => []);
        if (references.some(reference => reference.uri.toString() !== source.toString())) used.push(source);
    }
    return used;
}

/** What VS Code shows when it moves [moved] sources that the project uses without the server's edits for those uses. */
export function unupdatedReferences(moved: readonly vscode.Uri[], reason?: string): string {
    const names = moved.map(uri => uri.path.split('/').pop()).join(', ');
    return `Ecstasy: the references to ${names} were not updated for this move${reason ? `: ${reason}` : ''}. `
        + 'The language server could not prove edits that keep them compiling, and VS Code moves the files anyway.';
}

/**
 * File-operation participation contributes only extra edits; VS Code owns the requested moves. A participant cannot
 * refuse a move, so when the server refuses its edits for sources that the project uses, [warn] says so: the move
 * leaves those uses as they were. A refusal that the server gives its reason for, as for a move that it can't prove
 * while a module does not compile, where its references can't find the uses either, says so with that reason for each
 * moved file or folder, whatever uses they find: the server proves only a move within a module of the graph, of its
 * sources or its folders, which are its packages and hold its resources, or of a folder that holds a module, and the
 * proof that it could not make updates the uses of their types and the file and directory literals that name them.
 */
export async function moveWithConfiguration(
    client: LanguageClient,
    event: vscode.FileWillRenameEvent,
    used: typeof usedElsewhere = usedElsewhere,
    warn: (message: string) => void = message => void vscode.window.showWarningMessage(message),
): Promise<vscode.WorkspaceEdit | null> {
    // TODO VSCODE: UP23 — the participant cannot replace/reorder host moves. Independently
    // relocated children must precede parents for apply, but native Undo also replays inverse
    // moves in forward order. Overlapping moves need an upstream reversal fix as well as
    // a complete replacement transaction; server ordering alone cannot make them safe.
    if (!event.files.length) return null;
    const settings = compilerSettingsLocation()?.uri.toString();
    const owned = [...(vscode.workspace.workspaceFolders ?? []).map(folder => folder.uri.toString()), ...(settings ? [settings] : [])];
    if (event.files.some(move => owned.some(uri => uri === move.oldUri.toString() || uri.startsWith(move.oldUri.toString().replace(/\/$/, '') + '/')))) {
        throw new Error('Moving workspace roots or their settings requires reopening the workspace.');
    }
    let refused = false;
    let reason: string | undefined;
    let overtaken = false;
    let unproven = false;
    const propose = () => proposalWithConfiguration(client, event.files[0].oldUri, event.token,
        async () => {
            overtaken = unproven = false;
            reason = undefined;
            const proposal = await client.sendRequest(moveRequest, {
                files: event.files.map(move => ({ oldUri: move.oldUri.toString(), newUri: move.newUri.toString() }))
            }, event.token).catch(error => {
                reason = error instanceof Error ? error.message : String(error);
                // the client's handling of a cancellation it did not ask for throws, which would leave VS Code to move
                // the files without the server's edits, and with no word of it; so does its handling of a refusal that
                // the server gives its reason for, which the warning names instead
                overtaken = overtakenAnswer(error, event.token);
                unproven = refusedAnswer(error);
                return overtaken || unproven ? null : client.handleFailedRequest(moveRequest, event.token, error, null, false);
            });
            refused = proposal === null;
            return proposal;
        },
        event.files.map(move => move.oldUri.toString()))
        // documents or the compiler settings that changed as the server proved the move, which the client finds once it
        // has the server's answer
        .catch(error => {
            if (!(error instanceof Overtaken) || event.token.isCancellationRequested) throw error;
            reason = error.message;
            overtaken = refused = true;
            return null;
        });
    let edit = await propose();
    // a change of the project while the server proved the move, such as a file written on disk, which it learns of
    // as it comes, or a document that VS Code released, overtakes the proof: the server proves the move again, of the
    // project as it now is
    for (let retries = 0; overtaken && retries < MOVE_RETRIES && !event.token.isCancellationRequested; retries++) edit = await propose();
    if (overtaken) reason = `the project changed each of the ${MOVE_RETRIES + 1} times the language server proved it`;
    if (refused && !event.token.isCancellationRequested) {
        const moved = event.files.map(move => move.oldUri);
        const unupdated = unproven ? moved : await used(moved, event.token);
        if (unupdated.length) warn(unupdatedReferences(unupdated, reason));
    }
    return edit;
}

async function proposalWithConfiguration(
    client: LanguageClient,
    uri: vscode.Uri,
    token: vscode.CancellationToken,
    request: () => Promise<RenameProposal | null>,
    hostMoves: readonly string[] = [],
): Promise<vscode.WorkspaceEdit | null> {
    const documents = vscode.workspace.textDocuments.filter(value => !value.isClosed && value.languageId === 'xtc')
        .map(document => ({ document, version: document.version, dirty: document.isDirty }));
    const location = compilerSettingsLocation();
    const config = compilerSourceModules();
    const state = configurationState(uri);
    const folders = vscode.workspace.workspaceFolders;
    const base = folders?.length === 1 ? folders[0].uri.toString().replace(/\/?$/, '/') : undefined;
    const settings = config && location ? await vscode.workspace.openTextDocument(location.uri).then(value => value, () => undefined) : undefined;
    const snapshot = settings && { version: settings.version, text: settings.getText() };
    const proposal = await request();
    if (!proposal || token.isCancellationRequested) return null;
    // the server's own edit, which the compiler settings' edit below joins
    const proved = proposal.edit;
    if (proposal.graph) {
        if (!settings || !snapshot || !location || !Array.isArray(config)
            || folderOverrides(uri).some(value => value !== undefined)
            || sourceGraphKey(config, base) !== sourceGraphKey(proposal.graph.before, base)) {
            throw new Error('This refactoring needs an explicit graph in this workspace’s settings. Global or folder overrides cannot be rewritten safely.');
        }
        if (settings.isClosed || settings.version !== snapshot.version || configurationState(uri) !== state) {
            throw new Overtaken('Compiler settings changed while calculating the refactoring. Try again.');
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
    // Only Rename's own edit needs files.refactoring.autoSave. VS Code applies a file move's participant edits with the
    // move as one undo group, and its Undo restores the moved file's text, saved or not; refusing a move, which VS Code
    // makes anyway, would only drop the Ecstasy edits.
    if (!hostMoves.length && editsMovedSource(proposal.edit) && !vscode.workspace.getConfiguration('files.refactoring').get('autoSave', true)) {
        throw new Error('Moving an edited source file requires files.refactoring.autoSave in VS Code so Undo and Redo preserve its contents.');
    }
    const originals = new Set(hostMoves.map(uri => new URL(uri).href.replace(/\/$/, '')));
    const edit = { ...proposal.edit, documentChanges: proposal.edit.documentChanges?.filter(change =>
        !RenameFile.is(change) || !originals.has(new URL(change.oldUri).href.replace(/\/$/, ''))) };
    const converted = await client.protocol2CodeConverter.asWorkspaceEdit(edit, token);
    if (token.isCancellationRequested) return null;
    if (proposal.graph && (settings?.isClosed || settings?.version !== snapshot?.version || configurationState(uri) !== state)) {
        throw new Overtaken('Compiler settings changed while converting the refactoring. Try again.');
    }
    if (sourcesChanged(documents, vscode.workspace.textDocuments.filter(value => !value.isClosed && value.languageId === 'xtc'))) {
        throw new Overtaken('Source documents changed while calculating the refactoring. Try again.');
    }
    if (!client.validateWorkspaceEdit(proposal.edit)) throw new Overtaken('Documents changed while calculating the refactoring. Try again.');
    // the last check that this extension makes: VS Code applies the edit that a rename provider or a file operation's
    // participant returns itself, once this returns, with no hook before it, so a closed file that changes on the disk
    // between this check and that application, as while VS Code shows the refactoring's preview, still takes the edit
    if (await textsChanged(proved, proposal.textHashes, documents)) throw new Overtaken(TEXTS_CHANGED);
    if (token.isCancellationRequested) return null;
    return converted ?? null;
}
