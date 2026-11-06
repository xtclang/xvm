import { execFile } from 'node:child_process';
import { appendFileSync } from 'node:fs';
import * as fs from 'node:fs/promises';
import * as path from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';
import * as vscode from 'vscode';
import { State } from 'vscode-languageclient/node';
import { onDidUpdateExecution, type ExecutionUpdate } from '../../embedded-execution';
import { getClient } from '../../lsp-client';
import { onDidReportTest, onDidRefreshTests, type TestReport } from '../../test-explorer';
import { Activity, QUIET_MS } from './activity';
import { actingBy } from './editor';
import { DRIVER_CHECK_MS, RECORDING, type Box, type Point, type TourUi } from './ui';

/**
 * A title card of the tour, which the composer draws: a small [eyebrow] line over a [heading], a [lead] under it, and
 * then [boxes] side by side, a block of [code], or both.
 */
export interface TourCard {
    eyebrow: string;
    heading: string;
    lead?: string;
    boxes?: { title: string; text: string }[];
    code?: string;
}

/**
 * One step of the tour as `tour.json` records it for the composer: its [section], [title] and [caption], which the
 * recording shows under it, the [feature] it exercises (the LSP or DAP request, or the VS Code command), and either
 * the [image] of the screen it captured, with the [focus] it highlights and the [cursor] it points at, or the [card]
 * the composer draws. A step that failed has its [error], and no image when nothing was captured; one whose check
 * failed has its [check].
 */
export interface TourStep {
    number: number;
    section: string;
    title: string;
    caption: string;
    feature?: string;
    image?: string;
    focus?: Box;
    cursor?: Point;
    card?: TourCard;
    error?: string;
    check?: string;
    /**
     * When the step started to act, when its screen was captured, and when its check ended, in milliseconds since the
     * epoch, the clock of the screen's recording, which the composer cuts the step's clip from; and what it did by
     * input meanwhile, which the clip shows.
     */
    start?: number;
    shot?: number;
    end?: number;
}

/** A check that ran, a step's or one between the steps, with the feature it checks and why it failed, if it did. */
export interface TourCheck {
    section: string;
    what: string;
    feature: string;
    framed: boolean;
    error?: string;
    /** When it started and ended, in milliseconds since the epoch, the clock of the screen's recording. */
    start: number;
    end: number;
}

/** What a step shows and does; see [Tour.shot]. */
export interface Shot {
    title: string;
    caption: string;
    feature: string;
    /** The subject the frame highlights, as [TourUi.locate] names it. */
    focus?: string;
    /** How long the screen gets to draw before the capture. */
    settleMs?: number;
    until?: () => Promise<boolean> | boolean;
    act?: () => Promise<void>;
    check?: () => Promise<void>;
    after?: () => Promise<void>;
    /** What the step needs that an earlier one makes; without it, it is skipped. */
    needs?: readonly Need[];
}

/**
 * What a step or check needs that an earlier one makes, and that is missing once that one failed: a state VS Code is
 * in, or not, which [holds] reads as it is, never from a time, so that one that can still come, as a debug session
 * that is launching, holds.
 */
export interface Need {
    missing: string;
    holds: (tour: Tour) => boolean | Promise<boolean>;
}

/** The needs of the tour's steps and checks. */
export const NEEDS = {
    /** A debug session, which may still be launching. */
    session: { missing: 'no debug session', holds: () => vscode.debug.activeDebugSession !== undefined },
    /** A debug session whose program is stopped, at a breakpoint or after a step. */
    paused: { missing: 'no stopped debug session', holds: tour => vscode.debug.activeDebugSession !== undefined && tour.pausedAt() },
    /** The tests that the Testing view lists, which the test controller found. */
    tests: { missing: 'no tests listed', holds: tour => tour.testItems.length > 0 },
} satisfies Record<string, Need>;

/** What every step and check needs: the language server, which may be starting, as a restart does. */
const SERVER: Need = { missing: 'no language server', holds: () => getClient() !== undefined && getClient()!.state !== State.Stopped };

/** The failure of a wait or an action whose driver call got no answer while the window still answered. */
export class DriverLost extends Error {
    constructor(what: string) {
        super(`${what}: the driver's connection to the window stopped answering, while the window still answers`);
        this.name = 'DriverLost';
    }
}

/** A Debug Adapter Protocol message, as a tracker sees it. */
export interface DapMessage {
    type: string;
    seq?: number;
    request_seq?: number;
    command?: string;
    event?: string;
    success?: boolean;
    message?: string;
    arguments?: Record<string, unknown>;
    body?: Record<string, unknown> & {
        reason?: string;
        threadId?: number;
        output?: string;
        category?: string;
        stackFrames?: { id: number; name: string; line: number; canRestart?: boolean; source?: { path?: string; name?: string } }[];
        variables?: { name: string; value: string; variablesReference: number }[];
        threads?: { id: number; name: string }[];
        result?: string;
    };
}

/** The DAP requests after which a stopped program runs again, until it stops again. */
const RESUMING = ['continue', 'next', 'stepIn', 'stepOut', 'stepBack', 'reverseContinue', 'restartFrame', 'goto', 'disconnect', 'terminate'];

/**
 * The longest any wait of the tour lasts, an hour, which no step reaches however slow or loaded the machine is, so
 * that no result depends on its speed: a bound against a hang only, as the IntelliJ playbook's HANG_LIMIT is.
 * `-PvscodeTourHang=<seconds>` (XTC_TOUR_HANG) shortens it, for a quicker look at a section whose checks fail.
 */
export const HANG_MS = (Number(process.env.XTC_TOUR_HANG) || 3_600) * 1_000;

/**
 * Records the tour of [project] in VS Code: each step does what a user would, through the workbench's commands and
 * [ui], and captures the screen into [run]'s frames, or adds a title card. A step that fails is recorded with its
 * error, and the tour goes on with the next. [fixture] holds the project's sources as the tour starts, which
 * [restore] puts back, and [sections], when not empty, are the only sections recorded, by their IDs or names.
 */
export class Tour implements vscode.Disposable {
    readonly steps: TourStep[] = [];
    readonly checks: TourCheck[] = [];
    /** The checks that failed, each as `section: what: why`; the tour fails when there is any. */
    readonly failures: string[] = [];
    /**
     * Every DAP message of the tour's debug sessions, in the order the tracker saw them: from VS Code, its requests to
     * the adapter, and from the adapter, its responses and events.
     */
    readonly dap: { from: 'vscode' | 'adapter'; message: DapMessage }[] = [];
    /** What each embedded build and run terminal shows, by its kind and label. */
    private readonly executions = new Map<string, ExecutionUpdate>();
    /** The tests' results, by their items' IDs, since [watchTests]. */
    readonly tests = new Map<string, TestReport>();
    /** The test items the latest refresh listed. */
    testItems: readonly string[] = [];
    /** What the tour noticed of VS Code's own behavior, such as the arguments it sends, for results.json. */
    readonly notes: Record<string, unknown> = {};
    /** The exit codes of the shell tasks that ended, by their names, latest last. */
    readonly taskExits: { name: string; exitCode?: number }[] = [];
    /**
     * The steps and checks not run, as `section: what: skipped: why`, because what they need is missing after an
     * earlier one failed: a cascade reads as its cause, in [failures], and these.
     */
    readonly skipped: string[] = [];
    /** What could still make a wait's condition hold, which tells a hang from work that is slow. */
    readonly activity: Activity;
    /** The last step or check that failed, which a skipped one names as its cause. */
    private lastFailure: string | undefined;
    /** The failure after which the driver could not connect to the window again; every later step is skipped. */
    private driverDead: string | undefined;
    /** How many failures' states have been written, which numbers their files. */
    private diagnosed = 0;
    private readonly subscriptions: vscode.Disposable[];
    private sectionId = '';
    private sectionName = '';

    constructor(readonly ui: TourUi, readonly project: string, private readonly fixture: string, private readonly run: string,
                private readonly sections: ReadonlySet<string>) {
        this.subscriptions = [
            vscode.debug.registerDebugAdapterTrackerFactory('xtc', {
                createDebugAdapterTracker: () => ({
                    onWillReceiveMessage: (message: DapMessage) => { this.dap.push({ from: 'vscode', message }); },
                    onDidSendMessage: (message: DapMessage) => { this.dap.push({ from: 'adapter', message }); },
                }),
            }),
            onDidUpdateExecution(update => this.executions.set(`${update.kind}:${update.label}`, update)),
            onDidReportTest(report => this.tests.set(report.id, report)),
            onDidRefreshTests(ids => { this.testItems = ids; }),
            vscode.tasks.onDidEndTaskProcess(event => this.taskExits.push({ name: event.execution.task.name, exitCode: event.exitCode })),
        ];
        this.activity = new Activity(process.env.XTC_LSP_LOG_DIR, this.dap, () => [...this.executions.values()]
            .filter(each => each.exitCode === undefined).map(each => `the embedded ${each.kind} ${each.label}`));
        this.subscriptions.push(this.activity, onDidUpdateExecution(update => this.activity.happened(`the embedded ${update.kind}'s output`)),
            onDidReportTest(report => this.activity.happened(`the test ${report.id}'s result`)),
            onDidRefreshTests(() => this.activity.happened('the tests were listed')));
        ui.waiter = (message, condition) => this.until(message, condition);
    }

    dispose(): void {
        this.subscriptions.forEach(subscription => subscription.dispose());
    }

    /**
     * Writes [text] to the console and, as it happens, to the run's tour.log, which a run that is stopped before it
     * writes its results still leaves: what the tour did, and what each long wait found under way.
     */
    log(text: string): void {
        console.log(`[tour] ${text}`);
        appendFileSync(path.join(this.run, 'tour.log'), `${new Date().toISOString()} ${text}\n`);
    }

    /** Whether the tour records every section, as a full run does. */
    get everything(): boolean {
        return this.sections.size === 0;
    }

    /** Whether the current section is recorded. */
    get recording(): boolean {
        return this.sections.size === 0 || this.sections.has(this.sectionId) || this.sections.has(this.sectionName);
    }

    section(id: string, name: string): void {
        this.sectionId = id;
        this.sectionName = name;
        if (this.recording) this.log(`Section ${name}`);
    }

    /** The absolute path of [relative], a file of the project. */
    file(relative: string): string {
        return path.join(this.project, relative);
    }

    /** The absolute path of [name], a source of the game's module, such as `Player.x`. */
    source(name: string): string {
        return this.file(path.join('src', 'main', 'x', 'cardGame', name));
    }

    /** The absolute path of [name], a test module, such as `DeckTest.x`. */
    test(name: string): string {
        return this.file(path.join('src', 'test', 'x', name));
    }

    card(title: string, caption: string, card: TourCard): void {
        if (this.recording) this.steps.push({ number: this.steps.length + 1, section: this.sectionName, title, caption, card });
    }

    /**
     * A step on the screen: [Shot.act] does what the step shows; then, once [Shot.until] holds and the subject
     * [Shot.focus] names shows, and after [Shot.settleMs] for the screen to draw, the screen is captured with that
     * subject as the step's highlight; [Shot.check] checks what the caption says, and [Shot.after] undoes what the step
     * must not leave behind.
     */
    async shot(step: Shot): Promise<void> {
        if (!this.recording || await this.skips(step.title, step.needs ?? [])) return;
        const number = this.steps.length + 1;
        const image = `frames/${String(number).padStart(3, '0')}.png`;
        this.log(`${number} ${step.title}: starts`);
        const focus = step.focus ?? 'none';
        this.ui.forget();
        const start = Date.now();
        let shot: number | undefined;
        let record: TourStep;
        try {
            actingBy(RECORDING ? this.ui : undefined);
            try {
                await this.bounded(step.act?.(), `${step.title}: its action`);
            } finally {
                actingBy(undefined);
            }
            let waited: string | undefined;
            await this.until(`What the step waits for, and its subject (${focus}), shows`,
                async () => (await (step.until?.() ?? true)) && this.ui.showing(focus)).catch((failure: unknown) => {
                if (failure instanceof DriverLost) throw failure;
                waited = describe(failure);
            });
            await delay(step.settleMs ?? 1_500);
            const box = focus === 'none' ? undefined : await this.ui.locate(focus);
            const cursor = await this.ui.pointer();
            await fs.mkdir(path.join(this.run, 'frames'), { recursive: true });
            await this.ui.capture(path.join(this.run, image));
            shot = Date.now();
            record = {
                number, section: this.sectionName, title: step.title, caption: step.caption, feature: step.feature, image, focus: box,
                cursor, error: waited,
            };
        } catch (failure) {
            record = { number, section: this.sectionName, title: step.title, caption: step.caption, feature: step.feature, error: describe(failure) };
            // what the screen showed, for the report; the tour's frames do not have it
            await fs.mkdir(path.join(this.run, 'failures'), { recursive: true });
            await this.ui.capture(path.join(this.run, 'failures', `${String(number).padStart(3, '0')}.png`)).catch(() => undefined);
            await this.lost(failure, `${number} ${step.title}`);
        }
        if (record.image) {
            const checked = Date.now();
            try {
                await this.bounded(step.check?.(), `${step.title}: its check`);
            } catch (failure) {
                record.check = describe(failure);
                await this.lost(failure, `${number} ${step.title}`);
            }
            this.checks.push({
                section: this.sectionName, what: step.title, feature: step.feature, framed: true, error: record.check, start: checked, end: Date.now(),
            });
        }
        const strays = await this.closeStrays();
        if (strays) record.check = [record.check, strays].filter(Boolean).join('; ');
        Object.assign(record, { start, shot, end: Date.now() });
        if (record.error) this.failures.push(`${this.sectionName}: ${number} ${step.title}: ${record.error}`);
        if (record.check) this.failures.push(`${this.sectionName}: ${number} ${step.title}: check: ${record.check}`);
        if (record.error || record.check) this.lastFailure = `${number} ${step.title}`;
        this.log(`${number} ${step.title}: ${record.error ?? record.check ?? 'ok'}`);
        this.steps.push(record);
        if (this.driverDead) return;
        try {
            await this.bounded(step.after?.(), `${step.title}: undoing it`);
        } catch (failure) {
            this.log(`${number} ${step.title}: undoing it failed: ${describe(failure)}`);
            await this.lost(failure, `${number} ${step.title}: undoing it`);
        }
    }

    /**
     * Closes the windows that a step or check left beside the workbench's, as the developer tools' or a dialog's, which
     * would stay in every later step's recording; answers what it closed, as a failure of the step or check, or
     * nothing when there was none.
     */
    private async closeStrays(): Promise<string | undefined> {
        const strays = await this.ui.strays();
        for (const stray of strays) await this.ui.closeWindow(stray.id);
        return strays.length ? `a window was left open beside the workbench, and closed: ${strays.map(each => each.name).join(', ')}` : undefined;
    }

    /**
     * Whether the step or check [what] is skipped, and recorded so in [skipped]: the driver's connection to the window
     * is gone, or one of its [needs], or the language server, is missing after an earlier failure. It decides from the
     * state as it is, not from a time.
     */
    private async skips(what: string, needs: readonly Need[]): Promise<boolean> {
        let reason = this.driverDead && `driver connection lost (after ${this.driverDead} failed)`;
        for (const need of [SERVER, ...needs]) {
            if (reason) break;
            if (!await Promise.resolve().then(() => need.holds(this)).catch(() => false)) {
                reason = `${need.missing} (${this.lastFailure ? `after ${this.lastFailure} failed` : 'no step had failed'})`;
            }
        }
        if (!reason) return false;
        this.skipped.push(`${this.sectionName}: ${what}: skipped: ${reason}`);
        this.log(`${what}: skipped: ${reason}`);
        return true;
    }

    /**
     * After [failure] of [what]: when the driver's connection stopped answering, connects it again, and when it cannot,
     * every later step and check is skipped.
     */
    private async lost(failure: unknown, what: string): Promise<void> {
        let cause: unknown = failure;
        while (cause !== undefined && !(cause instanceof DriverLost)) cause = (cause as { cause?: unknown }).cause;
        if (!cause) return;
        this.log(`${what}: ${describe(cause)}; connecting again`);
        if (!await this.ui.reconnect()) this.driverDead = what;
    }

    /**
     * A check between the steps, which the recording does not show: [block] fails with what it found when what the
     * tour has done did not do what it should, such as a refactoring that left the project with an error.
     */
    async verify(what: string, feature: string, block: () => Promise<void>, needs: readonly Need[] = []): Promise<void> {
        if (!this.recording || await this.skips(what, needs)) return;
        this.log(`check ${what}: starts`);
        const start = Date.now();
        let error: string | undefined;
        try {
            await this.bounded(block(), what);
        } catch (failure) {
            error = describe(failure);
            this.failures.push(`${this.sectionName}: ${what}: ${error}`);
            this.lastFailure = what;
            await fs.mkdir(path.join(this.run, 'failures'), { recursive: true });
            await this.ui.capture(path.join(this.run, 'failures', `check-${this.checks.length + 1}.png`)).catch(() => undefined);
            await this.lost(failure, what);
            // a check that failed may have left its edits, which the next steps must not find; its session stays
            if (!this.driverDead) await this.restoreSources().catch(cause => this.log(`restoring after ${what} failed: ${describe(cause)}`));
        }
        const strays = await this.closeStrays();
        if (strays) {
            if (!error) this.failures.push(`${this.sectionName}: ${what}: ${strays}`);
            error = [error, strays].filter(Boolean).join('; ');
            this.lastFailure = what;
        }
        this.log(`check ${what}: ${error ?? 'ok'}`);
        this.checks.push({ section: this.sectionName, what, feature, framed: false, error, start, end: Date.now() });
    }

    /** Runs [block] as a step's preparation that captures nothing, in a recorded section only; a failure is not fatal. */
    async prepare(block: () => Promise<void>): Promise<void> {
        if (!this.recording) return;
        await block().catch(failure => this.log(`preparation failed in ${this.sectionName}: ${describe(failure)}`));
    }

    /** Whether [condition] holds within [timeoutMs], polled: for a retry's pace, which [until] waits on. */
    async within(timeoutMs: number, condition: () => Promise<boolean> | boolean): Promise<boolean> {
        const deadline = Date.now() + timeoutMs;
        for (;;) {
            if (await this.holds(condition)) return true;
            if (Date.now() > deadline) return false;
            await delay(250);
        }
    }

    /**
     * Waits until [condition] holds, polled. It fails with [message] and what [found] says as soon as nothing that
     * could make it hold is under way, as [Activity.inProgress] reads it: no request at the server or the debug adapter,
     * no compile, task, run or running program, and nothing has happened for [QUIET_MS], nor since the wait began,
     * which follows what the tour did; it waits as long as anything is, however slow, but at most [timeoutMs], the
     * last resort. The failure writes the state it found, and the server's threads, into the run's failures.
     */
    async until(message: string, condition: () => Promise<boolean> | boolean, found: () => unknown = () => '', timeoutMs = HANG_MS): Promise<void> {
        const begun = Date.now();
        const deadline = begun + timeoutMs;
        let report = begun + QUIET_MS;
        for (;;) {
            if (await this.holds(condition)) return;
            const work = await this.activity.inProgress();
            const expired = Date.now() > deadline;
            if (Date.now() > report) {
                this.log(`${this.sectionName}: waiting ${Math.round((Date.now() - begun) / 1_000)} s for: ${message}; under way: ${work.join('; ')}`);
                report += QUIET_MS;
            }
            if ((work.length === 0 && Date.now() - begun >= QUIET_MS) || expired) {
                const why = expired
                    ? `not within ${timeoutMs / 1000} s, with ${work.join('; ') || 'nothing'} under way`
                    : `and nothing that could make it so has been under way for ${QUIET_MS / 1_000} s; the last that happened was ${this.activity.lastEvent()}`;
                const value = await Promise.resolve().then(found).catch(error => String(error));
                const state = await this.diagnose(message, why);
                throw new Error(`${message}, ${why} (${state}): ${typeof value === 'string' ? value : JSON.stringify(value)}`);
            }
            await delay(250);
        }
    }

    /** Whether [condition] holds now; it fails with [DriverLost] when a call of the driver that it makes stops answering. */
    private async holds(condition: () => Promise<boolean> | boolean): Promise<boolean> {
        return this.guard(Promise.resolve().then(condition), 'A wait').catch((failure: unknown) => {
            if (failure instanceof DriverLost) throw failure;
            return false;
        });
    }

    /**
     * [work], or [DriverLost] once it has been pending for [DRIVER_CHECK_MS] and the driver's connection does not
     * answer while the window does: a call of the driver that would otherwise wait out the hang's bound.
     */
    private async guard<T>(work: Promise<T>, what: string): Promise<T> {
        const settled = new AbortController();
        const watched = work.finally(() => settled.abort());
        const watchdog = (async (): Promise<never> => {
            for (;;) {
                await delay(DRIVER_CHECK_MS, undefined, { signal: settled.signal }).catch(() => undefined);
                if (settled.signal.aborted) return new Promise<never>(() => undefined);
                if (await this.ui.driverLost()) throw new DriverLost(what);
            }
        })();
        return Promise.race([watched, watchdog]);
    }

    /**
     * [work], unless it has not ended within [BOUND_MS], as a hung command would not, or its driver call stopped
     * answering: then a failure that says [what] hung, so that the tour goes on with its next step.
     */
    private async bounded(work: Promise<unknown> | undefined, what: string): Promise<void> {
        if (!work) return;
        const timer = new AbortController();
        try {
            await this.guard(Promise.race([work, delay(BOUND_MS, undefined, { signal: timer.signal }).then(() => {
                throw new Error(`${what} did not end within ${BOUND_MS / 1_000} s`);
            }, () => undefined)]), what);
        } finally {
            timer.abort();
        }
    }

    /**
     * Writes what the tour found as [message] failed [why] into the run's failures: what was under way, the editors,
     * the debug session, the tasks, the workbench as its page shows it, and the language server's threads; answers
     * where it is.
     */
    async diagnose(message: string, why: string): Promise<string> {
        const directory = path.join(this.run, 'failures');
        const name = `state-${String(++this.diagnosed).padStart(3, '0')}`;
        await fs.mkdir(directory, { recursive: true });
        const editor = vscode.window.activeTextEditor;
        const workbench = await this.ui.evaluateDirectly(WORKBENCH_STATE);
        const state = {
            message, why, at: new Date().toISOString(),
            underWay: await this.activity.inProgress(), lastEvent: this.activity.lastEvent(), server: this.activity.serverWork(),
            editor: editor && {
                file: editor.document.uri.toString(), version: editor.document.version, dirty: editor.document.isDirty,
                selection: `${editor.selection.start.line + 1}:${editor.selection.start.character + 1}-${editor.selection.end.line + 1}:${editor.selection.end.character + 1}`,
            },
            editors: vscode.window.visibleTextEditors.map(each => path.basename(each.document.uri.path)),
            debug: vscode.debug.activeDebugSession?.name, stopped: this.stopped(),
            dap: this.dap.slice(-12).map(({ from, message }) => `${from}: ${message.type} ${message.command ?? message.event ?? ''}${message.success === false ? ' failed' : ''}`),
            tasks: vscode.tasks.taskExecutions.map(each => each.task.name),
            client: getClient()?.state,
            workbench: typeof workbench === 'string' ? JSON.parse(workbench) as unknown : 'the window does not answer',
        };
        await fs.writeFile(path.join(directory, `${name}.json`), JSON.stringify(state, null, 2) + '\n');
        // the debug session's messages, each a line, its arguments and body cut short: what the adapter answered
        if (this.dap.length > 0) {
            const lines = this.dap.map(({ from, message }) => JSON.stringify({ from, ...message }).slice(0, 2_000));
            await fs.writeFile(path.join(directory, `${name}-dap.jsonl`), lines.join('\n') + '\n');
        }
        const threads = await serverThreads();
        if (threads) await fs.writeFile(path.join(directory, `${name}-server-threads.txt`), threads);
        return `failures/${name}.json${threads ? ", with the server's threads beside it" : ''}`;
    }

    /** What the embedded build or run terminal of [kind] and [label] has shown, if there is one. */
    execution(kind: ExecutionUpdate['kind'], label: string): ExecutionUpdate | undefined {
        return this.executions.get(`${kind}:${label}`);
    }

    /** Forgets what the embedded executions showed, as a new one starts. */
    forgetExecutions(): void {
        this.executions.clear();
    }

    /** Forgets the tests' results, as a new test run starts. */
    watchTests(): void {
        this.tests.clear();
    }

    /** The results of the tests run since [watchTests], counted by state, such as passed=7, failed=1. */
    testCounts(): Record<string, number> {
        return [...this.tests.values()].reduce((counts, report) => ({ ...counts, [report.state]: (counts[report.state] ?? 0) + 1 }),
            {} as Record<string, number>);
    }

    /** The DAP requests VS Code has sent, by their commands. */
    dapRequests(): Set<string> {
        return new Set(this.dap.filter(each => each.from === 'vscode' && each.message.type === 'request').map(each => each.message.command!));
    }

    /** The messages the adapter sent, from the [from]th message of [dap] on. */
    private fromAdapter(from: number): DapMessage[] {
        return this.dap.slice(from).filter(each => each.from === 'adapter').map(each => each.message);
    }

    /** The DAP events of [name] that the adapter has sent, from the [from]th message of [dap] on. */
    dapEvents(name: string, from = 0): DapMessage[] {
        return this.fromAdapter(from).filter(message => message.type === 'event' && message.event === name);
    }

    /** The DAP responses to [command] that the adapter has sent, from the [from]th message of [dap] on. */
    dapResponses(command: string, from = 0): DapMessage[] {
        return this.fromAdapter(from).filter(message => message.type === 'response' && message.command === command);
    }

    /** The output events' text, from the [from]th message of [dap] on. */
    dapOutput(from = 0): string {
        return this.dapEvents('output', from).map(message => message.body?.output ?? '').join('');
    }

    /** Where the debug session is stopped: the latest stop and the stack VS Code read after it, or undefined while it runs. */
    stopped(): { reason?: string; file?: string; line?: number; frames: string[]; threadId?: number } | undefined {
        const at = this.dap.map(each => each.from === 'adapter' && each.message.type === 'event' && each.message.event === 'stopped').lastIndexOf(true);
        if (at < 0) return undefined;
        const after = this.dap.slice(at + 1);
        const resumed = after.some(({ from, message }) => from === 'adapter'
            ? message.type === 'event' && ['continued', 'terminated', 'exited'].includes(message.event!)
            : message.type === 'request' && RESUMING.includes(message.command!));
        if (resumed) return undefined;
        const stop = this.dap[at].message;
        const stack = after.find(({ from, message }) => from === 'adapter' && message.type === 'response' && message.command === 'stackTrace'
            && (message.body?.stackFrames?.length ?? 0) > 0)?.message;
        const top = stack?.body?.stackFrames?.[0];
        return {
            reason: stop.body?.reason, file: top?.source?.path, line: top?.line, threadId: stop.body?.threadId,
            frames: stack?.body?.stackFrames?.map(frame => frame.name) ?? [],
        };
    }

    /** Whether the debug session is stopped, at [line] of the file named [file] when given. */
    pausedAt(file?: string, line?: number): boolean {
        const stop = this.stopped();
        return !!stop && stop.frames.length > 0 && (file === undefined || path.basename(stop.file ?? '') === file) && (line === undefined || stop.line === line);
    }

    /**
     * Puts the project's sources back as [fixture] has them, in the editors and on disk, and deletes the sources that
     * steps added, such as a renamed class's file; first it closes what steps left open and stops sessions and runs.
     */
    async restore(): Promise<void> {
        if (!this.recording) return;
        for (const command of ['closeReferenceSearch', 'closeParameterHints', 'hideSuggestWidget', 'closeMarkersNavigation',
            'workbench.action.closeQuickOpen', 'closeFindWidget', 'closeBreakpointWidget', 'notifications.clearAll']) {
            await vscode.commands.executeCommand(command).then(undefined, () => undefined);
        }
        await this.ui.press('Escape', 'Escape').catch(() => undefined);
        await this.stopAll();
        vscode.debug.removeBreakpoints(vscode.debug.breakpoints);
        await this.restoreSources();
    }

    /** Puts the project's sources back as [fixture] has them, in the editors and on disk, and deletes the ones steps added. */
    async restoreSources(): Promise<void> {
        const sources = this.file('src');
        const originals = await files(this.fixture);
        for (const relative of originals) {
            const text = await fs.readFile(path.join(this.fixture, relative), 'utf8');
            const target = path.join(sources, relative);
            const document = vscode.workspace.textDocuments.find(each => !each.isClosed && each.uri.fsPath === target);
            if (document && document.getText() !== text) {
                const edit = new vscode.WorkspaceEdit();
                edit.replace(document.uri, new vscode.Range(document.positionAt(0), document.positionAt(document.getText().length)), text);
                await vscode.workspace.applyEdit(edit);
            }
            if (document?.isDirty) await document.save();
            if (await fs.readFile(target, 'utf8').catch(() => undefined) !== text) {
                await fs.mkdir(path.dirname(target), { recursive: true });
                await fs.writeFile(target, text);
            }
        }
        for (const added of (await files(sources)).filter(relative => !originals.includes(relative))) {
            const uri = vscode.Uri.file(path.join(sources, added));
            const tabs = vscode.window.tabGroups.all.flatMap(group => group.tabs)
                .filter(tab => tab.input instanceof vscode.TabInputText && tab.input.uri.fsPath === uri.fsPath);
            const document = vscode.workspace.textDocuments.find(each => each.uri.fsPath === uri.fsPath);
            if (document?.isDirty) {
                await vscode.window.showTextDocument(document);
                await vscode.commands.executeCommand('workbench.action.revertAndCloseActiveEditor');
            }
            await vscode.window.tabGroups.close(tabs).then(undefined, () => undefined);
            await vscode.workspace.fs.delete(uri, { useTrash: false }).then(undefined, () => fs.rm(uri.fsPath, { force: true }));
        }
        await removeEmptyDirectories(sources, this.fixture);
    }

    /** Ends every debug session and task of the tour, which stops their programs. */
    async stopAll(): Promise<void> {
        // Stop again while a session is left, as the user presses it again: a second stop ends one that the first did not
        for (let tries = 0; vscode.debug.activeDebugSession && tries < 5; tries++) {
            await vscode.debug.stopDebugging(vscode.debug.activeDebugSession).then(undefined, () => undefined);
            await this.within(10_000, () => !vscode.debug.activeDebugSession);
        }
        await this.until('Every debug session ends', () => !vscode.debug.activeDebugSession, () => vscode.debug.activeDebugSession?.name);
        for (const execution of vscode.tasks.taskExecutions) execution.terminate();
        await this.until('Every task ends', () => vscode.tasks.taskExecutions.length === 0, () => vscode.tasks.taskExecutions.map(each => each.task.name));
    }

    /** Checks that every source of the project is back as [fixture] has it, as each section leaves it. */
    async verifyRestored(): Promise<void> {
        await this.verify('the project is back as it was', 'restore', async () => {
            const sources = this.file('src');
            const originals = await files(this.fixture);
            const changed: string[] = [];
            for (const relative of originals) {
                const text = await fs.readFile(path.join(this.fixture, relative), 'utf8');
                const target = path.join(sources, relative);
                const document = vscode.workspace.textDocuments.find(each => !each.isClosed && each.uri.fsPath === target);
                if (await fs.readFile(target, 'utf8').catch(() => '') !== text || (document && document.getText() !== text)) changed.push(relative);
            }
            const added = (await files(sources)).filter(relative => !originals.includes(relative));
            if (changed.length || added.length) throw new Error(`Changed: ${changed.join(', ')}; added: ${added.join(', ')}`);
            // the project as it was compiles: an error left is one of a file, or of the server, that the tour undid
            const errors = () => vscode.languages.getDiagnostics()
                .flatMap(([uri, items]) => items.filter(item => item.severity === vscode.DiagnosticSeverity.Error)
                    .map(item => `${path.relative(this.project, uri.fsPath)}:${item.range.start.line + 1}: ${item.message}`));
            await this.until('No errors are left', () => errors().length === 0, () => errors());
        });
    }
}

/**
 * The longest a step's action, check or undoing, or a check between the steps, takes, against a hang only: twice
 * [HANG_MS], so that a wait of its own, which says what it waited for, ends first.
 */
const BOUND_MS = 2 * HANG_MS;

/** What the workbench's page shows of its focus, its open widgets and its progress, as JSON, for a failure's state. */
const WORKBENCH_STATE = `JSON.stringify({
    focused: document.activeElement && (document.activeElement.className || document.activeElement.tagName),
    widgets: [...document.querySelectorAll('.quick-input-widget, .monaco-dialog-box, .notifications-toasts .notification-toast, .context-view, '
        + '.action-widget, .monaco-hover:not(.hidden), .suggest-widget.visible, .rename-box, .peekview-widget')]
        .filter(each => each.offsetParent !== null).map(each => each.className.split(' ')[0] + ': ' + each.innerText.slice(0, 300)),
    progress: document.querySelectorAll('.monaco-progress-container.active').length,
})`;

/**
 * The threads of the language server that runs now, as `jcmd Thread.print` prints them, with the JDK the server runs
 * on: the newest server process of the traces' directory that is alive. Undefined without one, or without jcmd.
 */
async function serverThreads(): Promise<string | undefined> {
    const directory = process.env.XTC_LSP_LOG_DIR;
    if (!directory) return undefined;
    const pids = (await fs.readdir(directory).catch(() => [] as string[]))
        .map(name => /^server-(\d+)-(\d+)$/.exec(name)).filter((match): match is RegExpExecArray => match !== null)
        .sort((a, b) => Number(b[2]) - Number(a[2])).map(match => Number(match[1]));
    for (const pid of pids) {
        const java = await fs.readlink(`/proc/${pid}/exe`).catch(() => undefined);
        if (!java) continue;
        const jcmd = path.join(path.dirname(java), 'jcmd');
        return new Promise(resolve => execFile(jcmd, [String(pid), 'Thread.print'], { timeout: DRIVER_CHECK_MS, maxBuffer: 16 * 1024 * 1024 },
            (error, stdout) => resolve(error ? `jcmd ${pid} Thread.print failed: ${error.message}\n${stdout}` : stdout)));
    }
    return undefined;
}

/** The files under [directory], by their paths relative to it. */
async function files(directory: string): Promise<string[]> {
    const entries = await fs.readdir(directory, { recursive: true, withFileTypes: true }).catch(() => []);
    return entries.filter(entry => entry.isFile())
        .map(entry => path.relative(directory, path.join(entry.parentPath, entry.name))).sort();
}

/** Removes the directories under [directory] that are empty and that [original] does not have. */
async function removeEmptyDirectories(directory: string, original: string): Promise<void> {
    const entries = await fs.readdir(directory, { recursive: true, withFileTypes: true }).catch(() => []);
    const directories = entries.filter(entry => entry.isDirectory()).map(entry => path.join(entry.parentPath, entry.name))
        .sort((a, b) => b.length - a.length);
    for (const each of directories) {
        const kept = await fs.stat(path.join(original, path.relative(directory, each))).then(() => true, () => false);
        if (!kept && (await fs.readdir(each)).length === 0) await fs.rmdir(each).catch(() => undefined);
    }
}

/** A failure as the report gives it: its message and its causes'. */
export function describe(failure: unknown): string {
    const parts: string[] = [];
    for (let cause: unknown = failure; cause !== undefined && parts.length < 5; cause = (cause as { cause?: unknown }).cause) {
        parts.push(cause instanceof Error ? `${cause.name}: ${cause.message}` : String(cause));
    }
    return parts.join(' <- ');
}
