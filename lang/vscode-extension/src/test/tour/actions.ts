import * as path from 'node:path';
import type { Locator } from 'playwright-core';
import * as vscode from 'vscode';
import { at, command, open } from './editor';
import { settingScope } from './inputs';
import { HANG_MS, type Tour } from './tour';

/** Fails with [message], which says what was found, unless [condition] holds: a step's or a check's. */
export function expect(condition: unknown, message: () => string): void {
    if (!condition) throw new Error(message());
}

/** Fails with what [message] finds, unless [condition] holds: for a message that has to read the screen. */
export async function ensure(condition: unknown, message: () => Promise<string>): Promise<void> {
    if (!condition) throw new Error(await message());
}

/** The rows of the suggest widget, as it lists them. */
export async function suggestions(tour: Tour): Promise<string[]> {
    return tour.ui.page.locator('.suggest-widget.visible .monaco-list-row').allInnerTexts().catch(() => []);
}

/**
 * Moves the suggest widget's focus to its row whose label starts with [label] and accepts it with Enter, as the user
 * does with the arrows.
 */
export async function acceptSuggestion(tour: Tour, label: string): Promise<void> {
    const widget = tour.ui.page.locator('.suggest-widget.visible');
    const named = (row: string) => row.trim().startsWith(label) && !/^\w/.test(row.trim().slice(label.length));
    await tour.until(`Completion lists ${label}`, async () => (await suggestions(tour)).some(named), () => suggestions(tour));
    const focus = () => widget.locator('.monaco-list-row.focused').innerText({ timeout: 2_000 }).catch(() => '');
    let focused = await focus();
    for (let moves = 0; moves < 40; moves++) {
        if (named(focused)) {
            await tour.ui.press('Enter');
            return;
        }
        // the row is listed, so another row has the focus: ArrowDown moves it on, once the widget draws the move
        const before = focused;
        await tour.ui.press('ArrowDown');
        await tour.until('The focus moves to the next row', async () => (focused = await focus()) !== before, () => focused);
    }
    throw new Error(`Completion's focus never reached ${label}: ${(await suggestions(tour)).join(' | ')}`);
}

/**
 * Types [code] into the active editor as the user does, with the editor's auto-closing, completion and on-type
 * formatting, closing the suggest widget before each character that would accept its item, as the user would.
 */
export async function typeCode(tour: Tour, code: string): Promise<void> {
    for (const char of code) {
        if (!/\w/.test(char)) await command('hideSuggestWidget');
        if (char === '\n') await tour.ui.press('Enter');
        else await tour.ui.type(char);
    }
}

/** Types [text] into [target] once it has the keyboard, which a box or widget takes a moment after it shows. */
export async function typeIn(tour: Tour, target: Locator, text: string, what: string): Promise<void> {
    await tour.until(`${what} takes the keyboard`, () => tour.ui.focusedIn(target));
    await tour.ui.type(text);
}

/** Waits for a quick input that asks for text, such as an input box, and types [text] and Enter into it. */
export async function answer(tour: Tour, text: string): Promise<void> {
    await tour.until('The input box', () => tour.ui.showing('quickInput'));
    await typeIn(tour, tour.ui.page.locator('.quick-input-widget input').first(), text, 'The input box');
    await tour.ui.press('Enter');
}

/**
 * Chooses [file] in the simple file dialog, the quick input that VS Code's files.simpleDialog.enable shows for an
 * open or save dialog: its path, typed over the one it shows, and OK.
 */
export async function fileDialog(tour: Tour, file: string): Promise<void> {
    // the simple dialog's box holds a folder's path, which the picker that opened it before did not
    const box = tour.ui.page.locator('.quick-input-widget input').first();
    const value = () => box.inputValue({ timeout: 1_000 }).catch(() => '');
    await tour.until('The file dialog', async () => (await value()).startsWith('/'));
    await tour.until('The file dialog takes the keyboard', () => tour.ui.focusedIn(box));
    await tour.ui.press('Control+a');
    await tour.ui.type(file);
    // the dialog completes what is typed, so the box may hold more than the path
    await tour.until('The path is in the box', async () => (await value()).startsWith(file), value);
    await tour.ui.press('Enter');
    // a file's path chooses it; a folder's opens the folder in the dialog, which shows it with a slash, and whose OK
    // then chooses it
    const open = async () => tour.ui.showing('quickInput');
    await tour.until('The dialog chooses, or opens the folder', async () => !await open() || (await value()).endsWith('/'), value);
    const ok = tour.ui.page.locator('.quick-input-widget .monaco-button').filter({ visible: true }).first();
    if (await open() && await ok.count() > 0) await tour.ui.click(ok);
}

/** The rows of the open action widget, such as Quick Fix's or Refactor's. */
export async function actionRows(tour: Tour): Promise<string[]> {
    return tour.ui.page.locator('.action-widget .monaco-list-row').filter({ visible: true }).allInnerTexts().catch(() => []);
}

/**
 * Opens Quick Fix (Ctrl+.) on the caret or selection, as [id] says, again until its list has a row with [row], as a
 * user would press it again while the server's analysis catches up; fails within a hang's bound with the rows the
 * list last showed and the titles of the actions that the providers give for the selection.
 */
export async function codeActions(tour: Tour, row: string | RegExp, id = 'editor.action.quickFix'): Promise<void> {
    const matches = (each: string) => typeof row === 'string' ? each.includes(row) : row.test(each);
    // Escape, which closes a list without the row, also clears a selection when no list is open
    const editor = vscode.window.activeTextEditor;
    const selection = editor?.selection;
    let shown: string[] = [];
    const deadline = Date.now() + HANG_MS;
    while (Date.now() < deadline) {
        if (editor && selection) editor.selection = selection;
        await tour.ui.focusEditor();
        // the list shows once every provider has answered, however long the server's proofs take, or VS Code says
        // that there is no action. An automatic request, which a change of the file's markers or of the caret makes
        // with the light bulb off too, replaces this one, which then shows nothing: once a request has ended, none is
        // under way and nothing shows, at two looks in a row, as VS Code shows a list as its request ends, ask again,
        // as a user would, twice at most; a third such round fails at once
        const answered = async () => (await actionRows(tour)).length > 0 || await tour.ui.showing('css:.monaco-editor-overlaymessage');
        const what = 'The list of actions or its message';
        let ended = tour.activity.ended('textDocument/codeAction');
        let unanswered = 0;
        let asks = 1;
        await command(id);
        await tour.until(what, async () => {
            if (await answered()) return true;
            const now = tour.activity.ended('textDocument/codeAction');
            unanswered = now !== ended && !tour.activity.serverWork().includes('textDocument/codeAction') ? unanswered + 1 : 0;
            if (unanswered < 2) return false;
            if (asks === 3) return true;
            ended = now;
            unanswered = 0;
            asks++;
            await command(id);
            return false;
        });
        if (!await answered()) {
            const why = `nothing shows after ${asks} asks, each of whose requests ended, with none under way`;
            throw new Error(`${what}, ${why} (${await tour.diagnose(what, why)})`);
        }
        shown = await actionRows(tour);
        if (shown.some(matches)) return;
        // a list without the row, or none: ask again, as a user would while the server's analysis catches up
        await tour.ui.press('Escape');
        await tour.until('The list closes', async () => (await actionRows(tour)).length === 0);
    }
    const offered = editor && selection
        ? await vscode.commands.executeCommand<(vscode.CodeAction | vscode.Command)[]>('vscode.executeCodeActionProvider', editor.document.uri, selection)
        : [];
    // a refactoring needs the module to compile: the files with errors say why there is none
    const broken = vscode.languages.getDiagnostics()
        .filter(([, items]) => items.some(item => item.severity === vscode.DiagnosticSeverity.Error))
        .map(([uri, items]) => `${path.basename(uri.fsPath)}: ${items.find(item => item.severity === vscode.DiagnosticSeverity.Error)!.message}`);
    throw new Error(`No ${row}: the list showed ${shown.join(' | ') || 'nothing'}; the providers give ${(offered ?? []).map(each => each.title).join(' | ') || 'nothing'}; errors: ${broken.join('; ') || 'none'}`);
}

/** Chooses the row with [row] of the open action widget, as a click on it does. */
export async function chooseAction(tour: Tour, row: string | RegExp): Promise<void> {
    await tour.ui.click(tour.ui.page.locator('.action-widget .monaco-list-row').filter({ hasText: row }).filter({ visible: true }).first());
}

/** The texts of the open quick input's rows. */
export async function quickRows(tour: Tour): Promise<string[]> {
    return tour.ui.quickItems();
}

/**
 * Sets [setting] of the `xtc` or another section, as the Settings editor does, for the workspace, or for the user, as
 * VS Code writes a setting of the machine or the application only there; undefined resets it.
 */
export async function setting(name: string, value: unknown): Promise<void> {
    const dot = name.lastIndexOf('.');
    const user = ['machine', 'application'].includes(settingScope(name) ?? '');
    await vscode.workspace.getConfiguration(name.slice(0, dot))
        .update(name.slice(dot + 1), value, user ? vscode.ConfigurationTarget.Global : vscode.ConfigurationTarget.Workspace);
}

/** Switches the project's build mode, as Ecstasy: Select Build Mode's picker does. */
export async function buildMode(tour: Tour, mode: 'embedded' | 'gradle'): Promise<void> {
    await command('xtc.selectBuildMode', mode, vscode.Uri.file(tour.project));
}

/** Sets a line breakpoint at [line] of [file], as F9 with the caret on the line does. */
export async function breakpoint(file: string, line: number): Promise<void> {
    // F9 toggles a breakpoint where the keyboard is, which an earlier step may have left in the Debug Console's input,
    // with the file's editor active still: the editor takes it, as a click in it does
    const editor = await vscode.window.showTextDocument((await open(file)).document, { preserveFocus: false });
    const position = new vscode.Position(line - 1, editor.document.lineAt(line - 1).firstNonWhitespaceCharacterIndex);
    editor.selection = new vscode.Selection(position, position);
    editor.revealRange(new vscode.Range(position, position), vscode.TextEditorRevealType.InCenterIfOutsideViewport);
    await command('editor.debug.action.toggleBreakpoint');
}

/** The line breakpoints VS Code has, as `file:line` with their conditions, hit counts and log messages. */
export function breakpoints(): string[] {
    return vscode.debug.breakpoints.map(each => each instanceof vscode.SourceBreakpoint
        ? `${path.basename(each.location.uri.fsPath)}:${each.location.range.start.line + 1}${each.condition ? ` if ${each.condition}` : ''}`
            + `${each.hitCondition ? ` hit ${each.hitCondition}` : ''}${each.logMessage ? ` log ${each.logMessage}` : ''}`
        : each instanceof vscode.FunctionBreakpoint ? `function ${each.functionName}` : 'other');
}

/** Waits until the debug session stops, at [line] of the file named [file] when given; answers where it stopped. */
export async function stoppedAt(tour: Tour, file?: string, line?: number, timeoutMs = HANG_MS) {
    await tour.until(`The program stops${file ? ` at ${file}:${line}` : ''}`, () => tour.pausedAt(file, line),
        () => tour.stopped() ?? `running; ${tour.dapOutput().slice(-300)}`, timeoutMs);
    return tour.stopped()!;
}

/** Waits until the debug session has left its stop, by its position: a step, a run to a line, or a continue. */
export async function movedFrom(tour: Tour, from: { file?: string; line?: number }, timeoutMs = HANG_MS) {
    await tour.until('The program moves on and stops again', () => {
        const stop = tour.stopped();
        return !!stop && stop.frames.length > 0 && (stop.file !== from.file || stop.line !== from.line);
    }, () => tour.stopped(), timeoutMs);
    return tour.stopped()!;
}

/** Types [text] and Enter into the Debug Console's input, as the user does, which shows the console first. */
export async function debugConsole(tour: Tour, text: string): Promise<void> {
    await command('workbench.debug.action.focusRepl');
    // the page's script, as the extension host has no DOM types
    await tour.until('The Debug Console takes the keyboard', async () => await tour.ui.page.evaluate("!!document.activeElement?.closest('.repl')") === true);
    await tour.ui.type(text);
    await tour.ui.press('Enter');
}

/** What the Debug Console shows. */
export async function consoleText(tour: Tour): Promise<string> {
    return tour.ui.page.locator('.repl .monaco-list-row').allInnerTexts().then(rows => rows.join('\n')).catch(() => '');
}

/** The rows of the Variables view, expanded as they are. */
export async function variables(tour: Tour): Promise<string[]> {
    return tour.ui.viewRows('Variables');
}

/** The row of the debug view [view] that names [name], such as a variable's or a frame's. */
export function row(tour: Tour, view: string, name: string) {
    // the name begins a word of the row's text, as a variable's does, `numCards = 5`; or a frame's row runs its parts
    // together, its name and its file's, CardCollection.dealAllCardCollection.x, and the name, or a dotted name that
    // ends in it, is the whole text of one of its parts
    const escaped = name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    const rows = tour.ui.locator(`view:${view}`)!.locator('.monaco-list-row');
    const part = tour.ui.page.locator('span, div').filter({ hasText: new RegExp(`^(.*[.\\s])?${escaped}$`) });
    return rows.filter({ hasText: new RegExp(`(^|\\.|\\s)${escaped}\\b`) }).or(rows.filter({ has: part })).first();
}

/** Expands the row of the debug view [view] that starts with [name], as a click on its twistie does. */
export async function expand(tour: Tour, view: string, name: string): Promise<void> {
    const item = row(tour, view, name);
    await tour.until(`The ${view} view has ${name}`, () => item.isVisible());
    if (await item.getAttribute('aria-expanded') !== 'true') {
        await tour.ui.click(item);
        await tour.ui.press('ArrowRight');
    }
}

/** Starts debugging as F5 does: the selected launch configuration, or without one the active editor's module. */
export async function debug(): Promise<void> {
    await command('workbench.action.debug.start');
}

/** Starts the launch configuration named [name], as picking it in the Run and Debug view and pressing F5 does. */
export async function debugConfiguration(tour: Tour, name: string): Promise<void> {
    if (!await vscode.debug.startDebugging(vscode.workspace.workspaceFolders![0], name)) throw new Error(`${name} did not start`);
}

/** Opens [file] at [text] and answers its 1-based line. */
export async function line(file: string, text: string, delta = 0): Promise<number> {
    await open(file);
    return at(text, delta);
}

/** The terminal named like [name], the newest first, as the Terminal view's list has it. */
export function terminal(name: string): vscode.Terminal | undefined {
    return [...vscode.window.terminals].reverse().find(each => each.name.includes(name));
}
