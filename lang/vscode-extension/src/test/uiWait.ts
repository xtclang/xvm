import type { ChildProcess } from 'node:child_process';
import * as fs from 'node:fs/promises';
import * as path from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';
import { chromium, type Browser, type Page } from 'playwright-core';
import * as vscode from 'vscode';
import { State, type LanguageClient } from 'vscode-languageclient/node';
import { onDidUpdateExecution } from '../embedded-execution';
import { getClient, lensActivity, pullsInFlight } from '../lsp-client';
import { onDidReportTest, onDidStartTest } from '../test-explorer';
import { withTestWait } from './progress';
import { withoutClock } from './terminal';
import { currentRun, PlaybookFinal, QUIET_MS, waitOn, waitReport, waitWhile } from './wait';

/** How long one try of a UI action waits for its target, which [untilTakes] repeats. */
export const TRY_MS = 2_000;

/**
 * VS Code's longest wait before it fetches or resolves code lenses: the average of the last fetches' times, at least
 * 250 ms and at most 62.5 s. Lens work that came or went within it may still make VS Code draw a lens.
 */
const LENS_DELAY_MS = 62_500;

/**
 * What [untilTakes] counts as under way besides a try's own calls that have not answered: with [recent], the default,
 * what happened within [QUIET_MS] ([Happenings]), as the playbook's other waits count it; with [server], the language
 * server's compiles and requests and the client's diagnostic pulls, for a widget that the extension or the server
 * makes; with [lenses], the client's code lens work, for a lens that VS Code draws.
 */
export interface UnderWay { server?: boolean; lenses?: boolean; recent?: boolean }

/** The DAP requests that wait for a program, which a Gradle task may start, and not for the adapter's own work. */
const PROGRAM_REQUESTS = new Set(['launch', 'attach']);

/** A process's task, as Gradle's, which prints into a terminal of its own. */
function processTask(execution: vscode.TaskExecution): boolean {
    return execution.task.execution instanceof vscode.ShellExecution || execution.task.execution instanceof vscode.ProcessExecution;
}

/** What a terminal shows, and the tasks that print into it. */
export interface TerminalShows { tasks: string[]; text: string }

/** VS Code's namespaces whose events [watchWorkbench] hears, which a test gives it instead. */
export interface Workbench {
    window: typeof vscode.window;
    workspace: typeof vscode.workspace;
    languages: typeof vscode.languages;
}

/**
 * Notes, by [noted], what VS Code's [workbench] does as it comes: its editors and tabs, its documents' opens, closes
 * and edits, but a log's, the diagnostics and the settings. Not the window's focus: it changes with what the window
 * manager and other windows do, not with the work that a wait waits for, so a window whose focus kept changing would
 * keep any wait going; and no wait needs it, as one for the focus asks for it again at each try ([focusTestWindow]).
 */
function watchWorkbench(noted: (what: string) => void, { window, workspace, languages }: Workbench = vscode): vscode.Disposable[] {
    const name = (uri: vscode.Uri) => path.basename(uri.path);
    // a log, as the language server's, grows with each request the server logs, which is no work of the window's
    const log = (document: vscode.TextDocument) => document.uri.scheme === 'output' || document.uri.path.endsWith('.log');
    return [
        window.onDidChangeActiveTextEditor(() => noted('the active editor changed')),
        window.onDidChangeVisibleTextEditors(() => noted('the visible editors changed')),
        window.tabGroups.onDidChangeTabs(() => noted('the tabs changed')),
        window.tabGroups.onDidChangeTabGroups(() => noted('the editor groups changed')),
        workspace.onDidOpenTextDocument(document => { if (!log(document)) noted(`${name(document.uri)} opened`); }),
        workspace.onDidCloseTextDocument(document => { if (!log(document)) noted(`${name(document.uri)} closed`); }),
        workspace.onDidChangeTextDocument(event => {
            if (event.contentChanges.length > 0 && !log(event.document)) noted(`an edit of ${name(event.document.uri)}`);
        }),
        languages.onDidChangeDiagnostics(event => noted(`the diagnostics of ${event.uris.map(name).join(', ')}`)),
        workspace.onDidChangeConfiguration(() => noted('the settings changed')),
    ];
}

/** What [Happenings] reads of the window, the client and the server, which a test gives it instead. */
export interface HappeningsSources {
    /** The clock. */
    now?: () => number;
    /** While a process's task runs, what its terminal shows ([taskTerminal]). */
    terminal?: () => Promise<TerminalShows | undefined>;
    /** The language client. */
    language?: () => LanguageClient | undefined;
    /** What grows while the starting server [client] works: its process's time, and its session's log ([serverSigns]). */
    starting?: (client: LanguageClient) => Promise<number>;
    /**
     * The workbench whose events it hears ([watchWorkbench]): a test gives its own, whose events only the test fires,
     * and the record then hears nothing else of the window's, so that only what the test feeds it happens.
     */
    workbench?: Workbench;
}

/**
 * What last happened of what could make a playbook wait's condition come, as it comes: the workbench's events
 * ([watchWorkbench]); the workspace's files made, written, deleted, saved or moved; debug sessions that start or end,
 * and the debug adapters' events, as their output; tasks and their processes that start or end, terminals that open or
 * close, and what a process's task prints into its terminal ([look]); the embedded builds' and runs' output; the tests'
 * starts and results; the language client's states, as a restart goes through them, and while it starts, the server's
 * process's time and its session's log as they grow; and a process that a case runs itself ([watch]), as it prints,
 * computes and ends. And VS Code's requests to a debug adapter that have no response yet, of the case that runs
 * ([currentRun]): but a launch or an attach, which waits for its program, as one that a Gradle task starts, counts only
 * by what its task and its program show. A debug session, a task, a process or a starting server that shows none of
 * these is no work, however long it runs: a program that waits for input, a hung Gradle task or a server that never
 * answers. The playbook's one record watches from its first wait on; the tour has none.
 */
export class Happenings implements vscode.Disposable {
    private last: { what: string; at: number };
    /** How many things happened, which a case asks to tell whether anything did between two answers ([happened]). */
    private count = 0;
    /** VS Code's requests to the debug adapters with no response yet, by their sessions' IDs and sequence numbers. */
    private readonly requests = new Map<string, { command: string; run?: object; left?: boolean }>();
    /** The debug sessions that have started and not ended, by their IDs. */
    private readonly sessions = new Map<string, vscode.DebugSession>();
    /** The processes that cases run themselves, by their IDs, with their time at the last look. */
    private readonly processes = new Map<number, { what: string; time?: number }>();
    /** The language client as the last look saw it, by its number and state: a restart makes another. */
    private client = '';
    private readonly clients = new WeakMap<LanguageClient, number>();
    private numbered = 0;
    /** What the starting server had grown to at the last look, and what a task's terminal showed. */
    private grown = -1;
    private shown = '';
    private readonly now: () => number;
    private readonly terminal: () => Promise<TerminalShows | undefined>;
    private readonly language: () => LanguageClient | undefined;
    private readonly starting: (client: LanguageClient) => Promise<number>;
    private readonly subscriptions: vscode.Disposable[];

    constructor({ now = Date.now, terminal = taskTerminal, language = getClient, starting = serverSigns, workbench }: HappeningsSources = {}) {
        this.now = now;
        this.terminal = terminal;
        this.language = language;
        this.starting = starting;
        this.last = { what: 'the playbook started', at: now() };
        const noted = (what: string) => this.happened(what);
        if (workbench) {
            this.subscriptions = watchWorkbench(noted, workbench);
            return;
        }
        const name = (uri: vscode.Uri) => path.basename(uri.path);
        const files = vscode.workspace.createFileSystemWatcher('**/*');
        this.subscriptions = [...watchWorkbench(noted), files,
            files.onDidCreate(uri => noted(`${name(uri)} was made`)),
            files.onDidChange(uri => noted(`${name(uri)} was written`)),
            files.onDidDelete(uri => noted(`${name(uri)} was deleted`)),
            vscode.workspace.onDidSaveTextDocument(document => noted(`${name(document.uri)} was saved`)),
            vscode.workspace.onDidRenameFiles(event => noted(`${event.files.map(file => name(file.newUri)).join(', ')} moved`)),
            vscode.debug.onDidStartDebugSession(session => {
                this.sessions.set(session.id, session);
                noted(`the debug session ${session.name} started`);
            }),
            vscode.debug.onDidTerminateDebugSession(session => {
                this.sessions.delete(session.id);
                for (const key of [...this.requests.keys()].filter(each => each.startsWith(`${session.id}:`))) this.requests.delete(key);
                noted(`the debug session ${session.name} ended`);
            }),
            vscode.debug.registerDebugAdapterTrackerFactory('*', {
                createDebugAdapterTracker: session => ({
                    onWillReceiveMessage: (message: DapMessage) => this.toAdapter(session.id, message),
                    onDidSendMessage: (message: DapMessage) => this.fromAdapter(session.id, message),
                }),
            }),
            vscode.tasks.onDidStartTask(event => noted(`the task ${event.execution.task.name} started`)),
            vscode.tasks.onDidEndTask(event => noted(`the task ${event.execution.task.name} ended`)),
            vscode.tasks.onDidStartTaskProcess(event => noted(`the task ${event.execution.task.name}'s process started`)),
            vscode.tasks.onDidEndTaskProcess(event => noted(`the task ${event.execution.task.name}'s process ended`)),
            vscode.window.onDidOpenTerminal(terminal => noted(`the terminal ${terminal.name} opened`)),
            vscode.window.onDidCloseTerminal(terminal => noted(`the terminal ${terminal.name} closed`)),
            onDidUpdateExecution(update => noted(`the embedded ${update.kind} ${update.label}'s output`)),
            // not the tests' listing, which a wait for it asks for at each look
            onDidStartTest(id => noted(`the test ${id} started`)),
            onDidReportTest(report => noted(`the test ${report.id}'s result`)),
        ];
    }

    /** Notes that [what] happened now. */
    happened(what: string): void {
        this.last = { what, at: this.now() };
        this.count++;
    }

    /** How many things have happened. */
    happenings(): number {
        return this.count;
    }

    /** The debug sessions that have started and not ended, which a case's cleanup ends. */
    liveSessions(): vscode.DebugSession[] {
        return [...this.sessions.values()];
    }

    /** Takes a message that VS Code sends the adapter of the session [session]: a request is under way until its response. */
    toAdapter(session: string, message: DapMessage): void {
        if (message.type === 'request') this.requests.set(`${session}:${message.seq}`, { command: message.command ?? 'request', run: currentRun() });
    }

    /**
     * Takes a message that the adapter of the session [session] sends VS Code: a response ends a request, which was
     * under way, and is nothing that happened, as a wait's own are not; an event, its program's output among them, is.
     */
    fromAdapter(session: string, message: DapMessage): void {
        if (message.type === 'response') this.requests.delete(`${session}:${message.request_seq}`);
        if (message.type === 'event') this.happened(`the debug adapter's ${message.event} event`);
    }

    /** Watches [child], a process that a case runs itself, as [what]: its output, its time as it computes ([look]) and its end. */
    watch(child: ChildProcess, what: string): void {
        if (child.pid !== undefined) this.processes.set(child.pid, { what });
        child.stdout?.on('data', () => this.happened(`${what} printed`));
        child.stderr?.on('data', () => this.happened(`${what} printed`));
        child.on('exit', () => {
            if (child.pid !== undefined) this.processes.delete(child.pid);
            this.happened(`${what} ended`);
        });
    }

    /**
     * Looks at what shows no event of its own: the language client's state, which a restart takes through Stopped and
     * Starting to Running, and while it starts, what grows as its server works ([HappeningsSources.starting]); the time
     * of the processes that cases run themselves, while they compute; and while a process's task runs, what its terminal
     * shows, without the clock of Gradle's progress bar.
     */
    async look(): Promise<void> {
        const client = this.language();
        if (client && !this.clients.has(client)) this.clients.set(client, ++this.numbered);
        const seen = client ? `${this.clients.get(client)}:${client.state}` : 'none';
        if (seen !== this.client) {
            this.client = seen;
            this.happened(`the language client is ${client ? State[client.state].toLowerCase() : 'gone'}`);
        }
        if (client?.state === State.Starting) {
            const grown = await this.starting(client).catch(() => this.grown);
            if (grown !== this.grown) {
                if (this.grown >= 0) this.happened('the starting server works');
                this.grown = grown;
            }
        } else {
            this.grown = -1;
        }
        for (const [pid, watched] of this.processes) {
            const time = await processTime(pid);
            if (time === undefined || time === watched.time) continue;
            if (watched.time !== undefined) this.happened(`${watched.what} computes`);
            watched.time = time;
        }
        const shows = await this.terminal().catch(() => undefined);
        if (shows === undefined) return;
        const shown = withoutClock(shows.text);
        if (shown === this.shown) return;
        this.shown = shown;
        this.happened(`the terminal of ${shows.tasks.map(task => `the task ${task}`).join(', ')} printed`);
    }

    /**
     * What is under way: VS Code's requests to the debug adapters of the case that runs that have no response, but a
     * launch's or an attach's, and what happened within [QUIET_MS].
     */
    underWay(): string[] {
        const ago = this.now() - this.last.at;
        const run = currentRun();
        return [
            ...[...this.requests.values()].filter(each => each.run === run && !PROGRAM_REQUESTS.has(each.command))
                .map(each => `the debug adapter's ${each.command}, which has not answered`),
            ...(ago < QUIET_MS ? [`${this.last.what}, ${Math.round(ago / 1000)} s ago`] : []),
        ];
    }

    /** The requests that a case or a step that has ended left without a response, once each: they count for no later one. */
    leftOver(): string[] {
        const run = currentRun();
        return [...this.requests.values()].filter(each => each.run !== run && !each.left).map(each => {
            each.left = true;
            return `the debug adapter's ${each.command}`;
        });
    }

    dispose(): void {
        this.subscriptions.forEach(subscription => subscription.dispose());
    }
}

/** A Debug Adapter Protocol message, as far as [Happenings] reads it. */
interface DapMessage { type?: string; seq?: number; request_seq?: number; command?: string; event?: string }

/**
 * The time, in clock ticks, that the process [pid] and the processes under it have computed, from Linux's `/proc`, or
 * undefined where there is none, or once the process has ended: a JVM that starts and compiles prints nothing a while.
 */
async function processTime(pid: number, depth = 0): Promise<number | undefined> {
    const stat = await fs.readFile(`/proc/${pid}/stat`, 'utf8').catch(() => undefined);
    if (stat === undefined) return undefined;
    // the fields after the command, whose name may hold spaces: utime and stime are the 14th and 15th
    const fields = stat.slice(stat.lastIndexOf(')') + 2).split(' ');
    const own = Number(fields[11]) + Number(fields[12]);
    if (depth >= 8) return own;
    const children = (await fs.readFile(`/proc/${pid}/task/${pid}/children`, 'utf8').catch(() => '')).split(' ').filter(Boolean).map(Number);
    const theirs = await Promise.all(children.map(child => processTime(child, depth + 1)));
    return theirs.reduce<number>((sum, time) => sum + (time ?? 0), own);
}

/**
 * What grows while the starting server of [client] works: its process's time ([processTime]), and the bytes of its
 * session's log and traces, `server-<pid>-*` under `XTC_LSP_LOG_DIR`, and no other session's.
 */
async function serverSigns(client: LanguageClient): Promise<number> {
    const pid = client.serverProcess?.pid;
    if (pid === undefined) return 0;
    const directory = process.env.XTC_LSP_LOG_DIR;
    const sessions = directory ? (await fs.readdir(directory).catch(() => [] as string[])).filter(name => name.startsWith(`server-${pid}-`)) : [];
    const files = (await Promise.all(sessions.map(async session => (await fs.readdir(path.join(directory!, session)).catch(() => [] as string[]))
        .map(file => path.join(directory!, session, file))))).flat();
    const sizes = await Promise.all(files.map(file => fs.stat(file).then(stat => stat.isFile() ? stat.size : 0, () => 0)));
    return sizes.reduce((sum, size) => sum + size, 0) + (await processTime(pid) ?? 0);
}

/** The window's DevTools connection that reads a task's terminal, while one runs, which every look shares. */
let reader: Promise<{ browser: Browser; page: Page } | undefined> | undefined;

/** Closes the DevTools connection that reads a task's terminal, if one is open. */
async function closeReader(): Promise<void> {
    const open = reader;
    reader = undefined;
    await (await open?.catch(() => undefined))?.browser.close().catch(() => undefined);
}

/**
 * While a process's task runs, as Gradle's, the tasks' names and what the active terminal shows, which such a task
 * prints into, as the window draws it with `terminal.integrated.gpuAcceleration` off, through its DevTools
 * (`XTC_PLAYBOOK_UI_PORT`); a connection or a read that does not answer within [TRY_MS] reads nothing, and the next
 * look tries again. Without such a task, the connection closes.
 */
export async function taskTerminal(): Promise<TerminalShows | undefined> {
    const tasks = vscode.tasks.taskExecutions.filter(processTask).map(each => each.task.name);
    const port = Number(process.env.XTC_PLAYBOOK_UI_PORT);
    if (tasks.length === 0 || !Number.isInteger(port) || port <= 0) {
        await closeReader();
        return undefined;
    }
    const timer = new AbortController();
    const within = <T>(work: Promise<T>) => Promise.race([work.catch(() => undefined),
        delay(TRY_MS, undefined, { signal: timer.signal }).catch(() => undefined)]);
    try {
        // one connection for every look, also two at once
        reader ??= within(chromium.connectOverCDP(`http://127.0.0.1:${port}`, { timeout: TRY_MS })).then(async browser => {
            const page = browser?.contexts().flatMap(context => context.pages()).find(each => each.url().includes('/workbench/'));
            if (browser && page) return { browser, page };
            await browser?.close().catch(() => undefined);
            return undefined;
        });
        const open = await reader;
        if (!open) {
            reader = undefined;
            return undefined;
        }
        const texts = await within(open.page.locator('.terminal-wrapper.active .xterm-rows, .terminal-wrapper .xterm-rows')
            .filter({ visible: true }).allInnerTexts());
        return texts === undefined ? undefined : { tasks, text: texts.join('\n') };
    } finally {
        timer.abort();
    }
}

/** The playbook's [Happenings], which watch from the first wait that asks for them on, for the extension host's life. */
let happenings: Happenings | undefined;

function watching(): Happenings {
    happenings ??= new Happenings();
    return happenings;
}

/** How many things have happened ([Happenings]), which tells a case whether anything did between two of its looks. */
export function happenedCount(): number {
    return watching().happenings();
}

/**
 * Watches [child], a process that a case runs itself, as [what] ([Happenings.watch]): while it prints or computes it is
 * under way, and one that does neither is no work, however long it runs. The case passes it [caseSignal], which ends it
 * once the case is given up on.
 */
export function watchProcess(child: ChildProcess, what: string): void {
    watching().watch(child, what);
}

/** The debug sessions that have started and not ended, as the playbook's record has them, which a case's cleanup ends. */
export function liveSessions(): vscode.DebugSession[] {
    return happenings?.liveSessions() ?? [];
}

/** The calls of VS Code or the server that a try made and that have not answered within [TRY_MS], until they do, by the case that made them. */
const unanswered = new Map<symbol, { what: string; run?: object; left?: boolean }>();

/** The calls of the case that runs ([currentRun]) that have not answered: those of a case or a step that ended count for no later one. */
function unansweredNow(): string[] {
    const run = currentRun();
    return [...unanswered.values()].filter(call => call.run === run).map(call => `${call.what}, which has not answered`);
}

/**
 * What a case or a step of its cleanup that has ended left under way, once each: the calls of its tries and the
 * debug adapters' requests that have not answered, which no later one counts, for the run's progress to say.
 */
export function leftOver(): string[] {
    const run = currentRun();
    const calls = [...unanswered.values()].filter(call => call.run !== run && !call.left).map(call => {
        call.left = true;
        return call.what;
    });
    return [...calls, ...happenings?.leftOver() ?? []];
}

/**
 * [work], or a failure that says [what] did not answer within [TRY_MS]: a call of VS Code or the server that a try
 * makes, which ends the try, so that the wait looks again and its minute's report names what it waits for. With
 * [tracked], a call that has not answered is under way for the case that made it until it does.
 */
export async function answered<T>(work: Thenable<T>, what: string, tracked = true): Promise<T> {
    const timer = new AbortController();
    const call = Promise.resolve(work);
    // the case that makes the call, which may have ended when it has not answered
    const run = currentRun();
    try {
        return await Promise.race([call, delay(TRY_MS, undefined, { signal: timer.signal }).then(() => {
            if (tracked) {
                const token = Symbol(what);
                unanswered.set(token, { what, run });
                void call.then(() => unanswered.delete(token), () => unanswered.delete(token));
            }
            throw new Error(`${what} did not answer within ${TRY_MS / 1000} s`);
        })]);
    } finally { timer.abort(); }
}

/**
 * The language server's compiles and requests under way and the client's diagnostic pulls, as the server's status says
 * within [TRY_MS]; a status that does not answer is the server's work too, whose name the minute's report gives. A
 * server that starts counts by what [Happenings] sees of it.
 */
export async function serverWork(): Promise<string[]> {
    const client = getClient();
    if (!client?.isRunning()) return [];
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
 * again until it takes, while anything that could make it take is under way: a try's own call that has not answered
 * ([answered]), and what [underWay] adds, by default what happened within [QUIET_MS], as the playbook's other waits
 * count it, so that a widget that a busy window is slow to draw is waited for. One that has not taken at three tries
 * in a row with nothing under way fails at once, with its last try's failure; [waitWhile] says every minute what it
 * waits for, naming the case, and gives up at the hang's bound only as a last resort. The wait shows in the run's
 * status bar, and among a case's waits.
 */
export async function untilTakes(what: string, attempt: () => Promise<unknown>, underWay: UnderWay = { server: true }): Promise<void> {
    await withTestWait(what, async () => {
        // the playbook's record, which a try without the minute's needs none of
        const seen = underWay.recent ?? true ? watching() : undefined;
        let failure: unknown;
        try {
            await waitWhile(() => attempt().then(() => true, (error: unknown) => {
                failure = error;
                return false;
            }), took => took, what, async () => {
                await seen?.look();
                const work = [...unansweredNow(), ...seen?.underWay() ?? [], ...(underWay.lenses ? lensWork() : [])];
                return work.length > 0 || !underWay.server ? work : serverWork();
            }, waitReport);
        } catch (error) {
            if (!(error instanceof PlaybookFinal)) throw error;
            const why = failure instanceof Error ? failure.message.split('\n')[0] : String(failure);
            throw new PlaybookFinal(`${what}: not at three tries in a row with nothing under way that could make it take; the last: ${why}`);
        }
    }, 'while under way');
}

/**
 * What could still make a playbook wait's condition come, or a case's call end: the calls of VS Code's or the server's
 * that a UI try of the case made and that have not answered ([answered]), VS Code's requests to the debug adapters that
 * have not answered, what happened within [QUIET_MS] ([Happenings]), and else, asked only then, the language server's
 * work ([serverWork]): a wait that looks as often as a wait does would ask it at every look. A test gives the record
 * [seen] and the server's work [server] of its own.
 */
export async function playbookWork(seen: Happenings = watching(), server: () => Promise<string[]> = serverWork): Promise<string[]> {
    await seen.look();
    const work = [...unansweredNow(), ...seen.underWay()];
    return work.length > 0 ? work : server();
}

/**
 * Waits until [read]'s value satisfies [accept], for as long as anything that could make it is under way
 * ([playbookWork]), or [read] has not answered ([waitOn]): a value that does not, with nothing under way at three looks
 * in a row, is final, and the wait fails at once with it. It says every minute what it waits for, naming the case, and
 * fails at the hang's bound only as a last resort; it shows in the run's status bar, and among a case's waits. A test
 * gives [playbookWork] its own [seen] and [server].
 */
export async function whileUnderWay<T>(read: () => Promise<T>, accept: (value: T) => boolean, message: string,
    { seen = watching(), server = serverWork }: { seen?: Happenings; server?: () => Promise<string[]> } = {}): Promise<T> {
    return withTestWait(message, () => waitOn(read, accept, message, () => playbookWork(seen, server), waitReport), 'while under way');
}
