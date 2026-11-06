import * as fs from 'node:fs/promises';
import * as path from 'node:path';
import * as vscode from 'vscode';
import { diagnosticReport, pullsDiagnostics } from '../../lsp-client';
import { commandKeys, paletteTitle } from './inputs';
import { HANG_MS, type Tour } from './tour';
import type { TourUi } from './ui';

/**
 * The input that a step acts by while it records the screen, from its act's start to its end, and otherwise none:
 * then [command], [open], [at], [replace] and [type] do what they do by keys, as a viewer sees, and not by the API.
 */
let input: TourUi | undefined;

/** Whether a step acts, recorded or not: then [open] gives the file's editor the keyboard, as a click in it does. */
let acting = false;

/**
 * Lets a step act by [ui], which is the input of the helpers below when the step is [recorded], or ends its act, with
 * none.
 */
export function actingBy(ui: TourUi | undefined, recorded: boolean): void {
    acting = ui !== undefined;
    input = recorded ? ui : undefined;
}

/**
 * Runs the command [id] with [args], as the Command Palette, a key or a menu does. While a recorded step acts, a
 * command without arguments runs by its keys or the palette, when it has them, and ends once they are pressed.
 */
export async function command<T = unknown>(id: string, ...args: unknown[]): Promise<T> {
    if (input && args.length === 0) {
        const keys = commandKeys(id);
        const title = keys ? undefined : paletteTitle(id);
        if (keys) await input.press(...keys);
        else if (title) await input.palette(title);
        if (keys || title) return undefined as T;
        input.note('action', paletteTitle(id) ?? id);
    }
    return vscode.commands.executeCommand<T>(id, ...args);
}

/**
 * Starts the command [id] with [args] and does not wait for it to end, as a command that opens a picker, a box or a
 * dialog ends only once they are answered. While a recorded step acts, it ends once the command's keys or the
 * palette's input are done, as the input that answers the command must come after them.
 */
export async function launch(id: string, ...args: unknown[]): Promise<void> {
    if (input && args.length === 0 && (commandKeys(id) || paletteTitle(id))) await command(id);
    else void vscode.commands.executeCommand(id, ...args);
}

/**
 * Opens [file] in an editor, as Quick Open does, and puts the caret at [text] when given; answers the editor. While a
 * recorded step acts, it opens by Quick Open, unless the file is already the active one, or Quick Open does not list
 * it; should Quick Open open another of the same name, the file opens by the API.
 */
export async function open(file: string, text?: string, delta = 0, occurrence = 0): Promise<vscode.TextEditor> {
    if (input && vscode.window.activeTextEditor?.document.uri.fsPath !== file && !await ignored(file)) {
        // the editor Quick Open makes active, whichever file it is, waited for as the tour's waits are
        const before = vscode.window.activeTextEditor;
        await input.quickOpen(path.basename(file));
        await input.waiter(`Quick Open opens ${path.basename(file)}`,
            () => vscode.window.activeTextEditor !== undefined && vscode.window.activeTextEditor !== before);
    }
    const active = vscode.window.activeTextEditor;
    // while a step acts, the editor takes the keyboard, as a click in it does: an earlier step can have left it in a
    // view or the panel, as the Variables view, the Test Results' or the Debug Console's, where a paste or F9 would go,
    // and where an editor's command that needs the keyboard in the editor, as Run to Cursor does, does nothing
    const editor = active?.document.uri.fsPath === file
        ? acting ? await vscode.window.showTextDocument(active.document, { viewColumn: active.viewColumn, preserveFocus: false }) : active
        : await vscode.window.showTextDocument(await vscode.workspace.openTextDocument(vscode.Uri.file(file)), { preview: false });
    if (text !== undefined) await at(text, delta, occurrence);
    return editor;
}

/**
 * Whether [file] is in a directory that its workspace folder's .gitignore names, as `xtc init`'s does `.vscode/` and
 * `build/`: Quick Open, which follows the ignore files, does not list it.
 */
async function ignored(file: string): Promise<boolean> {
    const folder = vscode.workspace.getWorkspaceFolder(vscode.Uri.file(file));
    if (!folder) return false;
    const ignore = await fs.readFile(path.join(folder.uri.fsPath, '.gitignore'), 'utf8').catch(() => '');
    const directories = new Set(ignore.split('\n').map(line => line.trim()).filter(line => line.endsWith('/') && !line.startsWith('#'))
        .map(line => line.replace(/^\/|\/$/g, '')));
    return path.relative(folder.uri.fsPath, path.dirname(file)).split(path.sep).some(segment => directories.has(segment));
}

/** The active editor, which a step works in. */
export function active(): vscode.TextEditor {
    const editor = vscode.window.activeTextEditor;
    if (!editor) throw new Error('No editor is active');
    return editor;
}

/** The offset of the [occurrence]th [text] in [document], which must have it. */
function offsetOf(document: vscode.TextDocument, text: string, occurrence: number): number {
    let index = -1;
    for (let found = 0; found <= occurrence; found++) {
        index = document.getText().indexOf(text, index + 1);
        if (index < 0) throw new Error(`No ${JSON.stringify(text)} (${occurrence}) in ${path.basename(document.uri.fsPath)}`);
    }
    return index;
}

/**
 * Puts the caret on the [occurrence]th [text] of the active editor, [delta] characters in, in the middle of the
 * editor; answers its 1-based line, as the editor's line numbers show it.
 */
export async function at(text: string, delta = 0, occurrence = 0): Promise<number> {
    const editor = active();
    const position = editor.document.positionAt(offsetOf(editor.document, text, occurrence) + delta);
    if (input) {
        await input.goTo(position.line + 1, position.character + 1);
        if (editor.selection.isEmpty && editor.selection.active.isEqual(position)) return position.line + 1;
    }
    editor.selection = new vscode.Selection(position, position);
    editor.revealRange(new vscode.Range(position, position), vscode.TextEditorRevealType.InCenterIfOutsideViewport);
    return position.line + 1;
}

/** The position of the [occurrence]th [text] of the active editor, [delta] characters in. */
export function positionOf(text: string, delta = 0, occurrence = 0): vscode.Position {
    const document = active().document;
    return document.positionAt(offsetOf(document, text, occurrence) + delta);
}

/**
 * Selects the [occurrence]th [text] of the active editor, from its end to its start, as a drag up does: the caret is
 * at its start. VS Code drops what an invoked code action, such as Organize Imports, Refactor or Quick Fix, answers
 * when the selection starts on another line than the caret, so a selection of several lines made downwards, with the
 * caret at its end, would never show its refactorings (CodeActionController.update, VS Code 1.140).
 */
export async function select(text: string, occurrence = 0): Promise<void> {
    const editor = active();
    const start = offsetOf(editor.document, text, occurrence);
    editor.selection = new vscode.Selection(editor.document.positionAt(start + text.length), editor.document.positionAt(start));
    editor.revealRange(editor.selection, vscode.TextEditorRevealType.InCenterIfOutsideViewport);
    // the window takes the selection from the API a moment later, and the keys that a recorded step presses next reach
    // it by another way, which can come first and act on the selection before: the status bar shows the selection once
    // the window has it
    const ui = input;
    if (ui) {
        const caret = editor.selection.active;
        const shown = `Ln ${caret.line + 1}, Col ${caret.character + 1} (${text.length} selected)`;
        await ui.waiter('The editor has the selection', async () => (await ui.text('status:Ln ')).includes(shown));
    }
}

/**
 * Replaces the [occurrence]th [from] of the active editor with [to], as pasting over its selection does: the caret is
 * then after [to], with nothing selected.
 */
export async function replace(from: string, to: string, occurrence = 0): Promise<void> {
    await select(from, occurrence);
    const editor = active();
    const start = editor.document.offsetAt(editor.selection.start);
    if (input) {
        // pasted, by Ctrl+V, as a viewer sees; the edit is the paste's once the document has it
        const before = editor.document.getText();
        const expected = before.slice(0, start) + to + before.slice(start + from.length);
        // an empty text deletes the selection, as Delete does; a paste of nothing would leave it
        if (to) {
            await vscode.env.clipboard.writeText(to);
            await input.press('Control+KeyV');
        } else {
            await input.press('Delete');
        }
        // the document as the keys left it, waited for as the tour's waits are, which fail once nothing is under way
        await input.waiter('The keys edit the document', () => editor.document.getText() !== before);
        if (editor.document.getText() === expected) return;
        throw new Error(`Pasting ${JSON.stringify(to)} over ${JSON.stringify(from)} made other text`);
    }
    if (!await editor.edit(edit => edit.replace(editor.selection, to))) throw new Error(`Could not replace ${JSON.stringify(from)}`);
    const after = editor.document.positionAt(start + to.length);
    editor.selection = new vscode.Selection(after, after);
}

/**
 * Types [text] at the caret of the active editor, as the keyboard does, with the editor's auto-indent and on-type
 * formatting; while a recorded step acts, by its keys.
 */
export async function type(text: string): Promise<void> {
    if (input) await input.type(text);
    else await command('default:type', { text });
}

/** The text of the source at [file]: its editor's, when it has one, or else its file's. */
export async function text(file: string): Promise<string> {
    const document = vscode.workspace.textDocuments.find(each => !each.isClosed && each.uri.fsPath === file);
    return document?.getText() ?? fs.readFile(file, 'utf8').catch(() => '');
}

/** What the Problems view shows of [file], as `severity code message` lines. */
export function problems(file: string): string[] {
    return vscode.languages.getDiagnostics(vscode.Uri.file(file))
        .map(item => `${vscode.DiagnosticSeverity[item.severity]} ${typeof item.code === 'object' ? item.code.value : item.code ?? ''} ${item.message}`);
}

/** The errors VS Code shows for [file]. */
export function errors(file: string): vscode.Diagnostic[] {
    return vscode.languages.getDiagnostics(vscode.Uri.file(file)).filter(item => item.severity === vscode.DiagnosticSeverity.Error);
}

/**
 * Whether what VS Code shows of [file]'s diagnostics is the server's report of the document's current version, when
 * the document is open and its diagnostics are pulled; an earlier report, from before an edit, may still show.
 */
export function current(file: string): boolean {
    const document = vscode.workspace.textDocuments.find(each => !each.isClosed && each.uri.fsPath === file);
    if (!document || !pullsDiagnostics()) return true;
    const report = diagnosticReport(document.uri);
    return report === undefined ? false : report.version === document.version;
}

/**
 * Fails unless, within a hang's bound, VS Code shows no error for [file], as its current text's report: a
 * refactoring or a fix must leave the code compiling.
 */
export async function compiles(tour: Tour, file: string): Promise<void> {
    await tour.until(`${path.basename(file)} compiles`, () => current(file) && errors(file).length === 0, () => problems(file));
}

/** Waits until VS Code shows an error for [file] whose message has [message]. */
export async function hasError(tour: Tour, file: string, message: string | RegExp): Promise<void> {
    await tour.until(`An error in ${path.basename(file)} with ${message}`,
        () => current(file) && errors(file).some(item => typeof message === 'string' ? item.message.includes(message) : message.test(item.message)),
        () => problems(file));
}

/**
 * The text of the output channel named [name], as its view shows it: a log channel's file, or another channel's
 * document, which VS Code has while the Output view shows it.
 */
export function output(name: string): string {
    const documents = new Set([...vscode.workspace.textDocuments, ...vscode.window.visibleTextEditors.map(editor => editor.document)]);
    return [...documents].filter(document => (document.uri.scheme === 'output' || document.uri.path.endsWith('.log')) && document.uri.path.includes(name))
        .map(document => document.getText()).join('\n');
}

/** The files the active editor group's tabs show, by their names. */
export function tabs(): string[] {
    return vscode.window.tabGroups.activeTabGroup.tabs.map(tab => tab.label);
}

/** Waits for the active editor to show [file], by its name. */
export async function editing(tour: Tour, name: string, timeoutMs = HANG_MS): Promise<void> {
    await tour.until(`The editor shows ${name}`, () => path.basename(vscode.window.activeTextEditor?.document.uri.path ?? '') === name,
        () => vscode.window.activeTextEditor?.document.uri.toString(), timeoutMs);
}

/** The 1-based line of the active editor's caret. */
export function caretLine(): number {
    return active().selection.active.line + 1;
}

/** The 1-based line of the [occurrence]th line of [file] that has [wanted]. */
export async function lineOf(file: string, wanted: string, occurrence = 0): Promise<number> {
    const lines = (await text(file)).split('\n');
    const found = lines.map((line, index) => line.includes(wanted) ? index + 1 : 0).filter(line => line > 0);
    if (found.length <= occurrence) throw new Error(`No line with ${JSON.stringify(wanted)} (${occurrence}) in ${path.basename(file)}`);
    return found[occurrence];
}
