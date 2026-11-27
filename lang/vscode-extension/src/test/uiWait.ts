import * as path from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';
import * as vscode from 'vscode';
import { State } from 'vscode-languageclient/node';
import { getClient, lensActivity, pullsInFlight } from '../lsp-client';
import { withTestWait } from './progress';
import { PlaybookFinal, waitReport, waitWhile } from './wait';

/** How long one try of a UI action waits for its target, which [untilTakes] repeats. */
export const TRY_MS = 2_000;

/**
 * VS Code's longest wait before it fetches or resolves code lenses: the average of the last fetches' times, at least
 * 250 ms and at most 62.5 s. Lens work that came or went within it may still make VS Code draw a lens.
 */
const LENS_DELAY_MS = 62_500;

/**
 * What [untilTakes] counts as under way besides the workbench's events: with [server], the language server's compiles
 * and requests and the client's diagnostic pulls, for a widget that the extension or the server makes; with [lenses],
 * the client's code lens work, for a lens that VS Code draws.
 */
export interface UnderWay { server?: boolean; lenses?: boolean }

/**
 * What VS Code's workbench does as it comes, which [take] answers once, since it last did: its editors, tabs and window,
 * its documents' opens, closes and edits, but a log's, the diagnostics and the settings.
 */
class WorkbenchEvents implements vscode.Disposable {
    private seen: string[] = [];
    private readonly subscriptions: vscode.Disposable[];

    constructor() {
        const name = (uri: vscode.Uri) => path.basename(uri.path);
        const noted = (what: string) => this.seen.push(what);
        // a log, as the language server's, grows with each request the server logs, which is no work of the window's
        const log = (document: vscode.TextDocument) => document.uri.scheme === 'output' || document.uri.path.endsWith('.log');
        this.subscriptions = [
            vscode.window.onDidChangeActiveTextEditor(() => noted('the active editor changed')),
            vscode.window.onDidChangeVisibleTextEditors(() => noted('the visible editors changed')),
            vscode.window.tabGroups.onDidChangeTabs(() => noted('the tabs changed')),
            vscode.window.tabGroups.onDidChangeTabGroups(() => noted('the editor groups changed')),
            vscode.window.onDidChangeWindowState(state => noted(`the window is ${state.focused ? 'focused' : 'not focused'}`)),
            vscode.workspace.onDidOpenTextDocument(document => { if (!log(document)) noted(`${name(document.uri)} opened`); }),
            vscode.workspace.onDidCloseTextDocument(document => { if (!log(document)) noted(`${name(document.uri)} closed`); }),
            vscode.workspace.onDidChangeTextDocument(event => {
                if (event.contentChanges.length > 0 && !log(event.document)) noted(`an edit of ${name(event.document.uri)}`);
            }),
            vscode.languages.onDidChangeDiagnostics(event => noted(`the diagnostics of ${event.uris.map(name).join(', ')}`)),
            vscode.workspace.onDidChangeConfiguration(() => noted('the settings changed')),
        ];
    }

    take(): string[] {
        const seen = this.seen;
        this.seen = [];
        return seen;
    }

    dispose(): void {
        this.subscriptions.forEach(subscription => subscription.dispose());
    }
}

/** The calls of VS Code or the server that a try made and that have not answered within [TRY_MS], until they do. */
const unanswered = new Map<symbol, string>();

/**
 * [work], or a failure that says [what] did not answer within [TRY_MS]: a call of VS Code or the server that a try
 * makes, which ends the try, so that the wait looks again and its minute's report names what it waits for. With
 * [tracked], a call that has not answered is under way for [untilTakes] until it does.
 */
export async function answered<T>(work: Thenable<T>, what: string, tracked = true): Promise<T> {
    const timer = new AbortController();
    const call = Promise.resolve(work);
    try {
        return await Promise.race([call, delay(TRY_MS, undefined, { signal: timer.signal }).then(() => {
            if (tracked) {
                const token = Symbol(what);
                unanswered.set(token, what);
                void call.then(() => unanswered.delete(token), () => unanswered.delete(token));
            }
            throw new Error(`${what} did not answer within ${TRY_MS / 1000} s`);
        })]);
    } finally { timer.abort(); }
}

/**
 * The language server's compiles and requests under way and the client's diagnostic pulls, as the server's status says
 * within [TRY_MS]; a status that does not answer is the server's work too, whose name the minute's report gives.
 */
async function serverWork(): Promise<string[]> {
    const client = getClient();
    if (!client) return [];
    if (client.state === State.Starting) return ['the language server starts'];
    if (!client.isRunning()) return [];
    const token = new vscode.CancellationTokenSource();
    try {
        const status = await answered(client.sendRequest<{ compilerQueue?: Record<string, number>; openRequests?: string[] }>(
            'xtc/languageServiceStatus', undefined, token.token), "The server's status", false);
        const queue = status?.compilerQueue;
        return [
            ...(queue && (queue.queueSize > 0 || queue.debouncingSize > 0 || queue.runningSize > 0) ? ["the server's compiles"] : []),
            ...(status?.openRequests ?? []).map(request => `the server's ${request}`),
            ...(pullsInFlight() > 0 ? ["the client's diagnostic pulls"] : []),
        ];
    } catch {
        return ["the server's status, which does not answer"];
    } finally {
        token.cancel();
        token.dispose();
    }
}

/** The client's code lens work under way, or that came or went within [LENS_DELAY_MS], after which VS Code draws. */
function lensWork(): string[] {
    const lenses = lensActivity();
    if (lenses.inFlight > 0) return ["the client's code lens requests"];
    const ago = lenses.last === undefined ? undefined : Date.now() - lenses.last;
    return ago !== undefined && ago < LENS_DELAY_MS
        ? [`the client's code lens work, ${Math.round(ago / 1000)} s ago, within VS Code's longest wait before it fetches or resolves them`]
        : [];
}

/**
 * Does [attempt], one try of a UI action that waits for its target at most [TRY_MS] and fails when it does not take,
 * again until it takes, while anything that could make it take is under way: VS Code's workbench events since the last
 * try, a try's own call that has not answered ([answered]), and what [underWay] adds. One that has not taken at three tries in a row with nothing under way fails at once,
 * with its last try's failure; [waitWhile] says every minute what it waits for, naming the case, and gives up at the
 * hang's bound only as a last resort. The wait shows in the run's status bar, and among a case's waits.
 */
export async function untilTakes(what: string, attempt: () => Promise<unknown>, underWay: UnderWay = { server: true }): Promise<void> {
    await withTestWait(what, async () => {
        const events = new WorkbenchEvents();
        let failure: unknown;
        try {
            await waitWhile(() => attempt().then(() => true, (error: unknown) => {
                failure = error;
                return false;
            }), took => took, what, async () => [...events.take(), ...(underWay.server ? await serverWork() : []),
                ...(underWay.lenses ? lensWork() : []), ...[...unanswered.values()].map(call => `${call}, which has not answered`)],
            waitReport);
        } catch (error) {
            if (!(error instanceof PlaybookFinal)) throw error;
            const why = failure instanceof Error ? failure.message.split('\n')[0] : String(failure);
            throw new PlaybookFinal(`${what}: not at three tries in a row with nothing under way that could make it take; the last: ${why}`);
        } finally {
            events.dispose();
        }
    }, 'while under way');
}
