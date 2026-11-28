import { AsyncLocalStorage } from 'node:async_hooks';
import { setTimeout as delay } from 'node:timers/promises';
import type Mocha from 'mocha';

/** The extension suite's waits' bound ([waitFor]); the playbook's and the tour's waits go on while what could end them is under way. */
export const PLAYBOOK_WAIT_MS = 30_000;

/** The hang's bound, an hour: the last resort of a wait that what is under way keeps going. */
export const PLAYBOOK_HANG_MS = 3_600_000;

/**
 * How long something that happened counts as under way: what it set off may still be coming, which a wait cannot always
 * see, as an extension's or VS Code's own work in between; a wait whose condition does not hold, with nothing else under
 * way and nothing happened for this long, has its final value. The tours' waits, VS Code's and IntelliJ's, take a minute too.
 */
export const QUIET_MS = 60_000;

/** How long a look of [waitOn] waits for its read to answer before it looks at what is under way, and again at the next. */
const ANSWER_MS = 2_000;

/** How often a wait that goes on says what it waits for, and what is under way. */
const REPORT_MS = 60_000;

/** A line of the run's progress in the build's output, in the format the IntelliJ playbook prints. */
export function progress(line: string): void {
    console.log(`[playbook] ${new Date().toTimeString().slice(0, 8)} ${line}`);
}

/** The case that runs, which a wait's report names. */
let running: string | undefined;

/** Notes [id] as the case that runs, or with undefined that none does. */
export function caseRuns(id: string | undefined): void {
    running = id;
}

/** A wait's line of the run's progress, which names the case that runs. */
export function waitReport(line: string): void {
    progress(running === undefined ? line : `${running} ${line}`);
}

/**
 * A wait that did not end within its bound, or a case given up on: the editor's operation may still be running, so the
 * extension suite starts no other test; the playbook lets the case end and restores the editor before its next case.
 */
export class PlaybookTimeout extends Error {}
/**
 * A case given up on that did not end after it either, which [runCase] leaves to itself: what it still does would be
 * the next case's to meet, so the playbook runs no other case.
 */
export class PlaybookLeftRunning extends PlaybookTimeout {}
/** A progress-based wait's [value] that does not do, with nothing under way that could change it. */
export class PlaybookFinal extends Error {
    constructor(message: string, readonly value?: unknown) { super(message); }
}
export class PlaybookCleanupFailure extends Error {
    constructor(readonly failures: readonly unknown[]) { super(`Playbook cleanup failed: ${failures.map(String).join('; ')}`); }
}

/** Always restore test settings; retain the original failure if cleanup also fails. */
export async function withPlaybookCleanup(body: () => Promise<void>, cleanup: () => Promise<void>): Promise<void> {
    const failures: unknown[] = [];
    try { await body(); }
    catch (error) { failures.push(error); }
    try { await cleanup(); }
    catch (error) { throw new PlaybookCleanupFailure([...failures, error]); }
    if (failures.length) throw failures[0];
}

/** The [PlaybookLeftRunning] that [error] is, or that a cleanup failure has, if any: after it, no case may run. */
export function leftRunning(error: unknown): PlaybookLeftRunning | undefined {
    if (error instanceof PlaybookLeftRunning) return error;
    return error instanceof PlaybookCleanupFailure ? error.failures.map(leftRunning).find(Boolean) : undefined;
}

export function mustStopPlaybook(error: unknown): boolean {
    return error instanceof PlaybookTimeout || error instanceof PlaybookCleanupFailure ||
        (error instanceof Error && error.name === 'TimeoutError') ||
        (error as { code?: string } | undefined)?.code === 'ERR_MOCHA_TIMEOUT';
}

/** The extension suite's: a test that timed out or could not restore its settings ends the run. */
export function stopOnUnsafeFailure(runner: Mocha.Runner): void {
    runner.on('fail', (_test, error) => { if (mustStopPlaybook(error)) runner.abort(); });
}

/** A wait of a playbook case, as it runs: once the playbook gives up on the case, [abandoned] is what it fails with. */
interface Waiting { abandoned?: Error }

/** A playbook case, as its body runs: the waits it is in, and what aborts once the playbook gives up on it. */
interface CaseRun { readonly waits: Set<Waiting>; readonly given: AbortController }

/** The case each wait belongs to, by the async context its body runs in, which outlives the playbook's wait for it. */
const cases = new AsyncLocalStorage<CaseRun>();

/**
 * The signal that the playbook gave up on the case whose body runs, if any, for a call that can be aborted, as a
 * process's: a case that is given up on ends its call with it.
 */
export function caseSignal(): AbortSignal | undefined {
    return cases.getStore()?.given.signal;
}

/** The case, or the step of its cleanup, that [runCase] runs now, if any. */
let current: CaseRun | undefined;

/**
 * The case, or the step of its cleanup, that runs now, as a token, or undefined when none does: what is under way
 * for it, as a call it made that has not answered, is no work of a later one's.
 */
export function currentRun(): object | undefined {
    return current;
}

/** A wait that starts, in the case whose body runs, if any: [leave] ends it, and [given] fails once the case was given up on. */
function entered(): { given: () => void; leave: () => void } {
    const run = cases.getStore();
    const wait: Waiting = {};
    run?.waits.add(wait);
    return {
        given: () => { if (wait.abandoned) throw wait.abandoned; },
        leave: () => { run?.waits.delete(wait); },
    };
}

/**
 * Runs [body], a playbook case or a step of its cleanup, and answers its failure, or undefined once it passed. It goes
 * on while the case progresses: while it is in a wait of the playbook's, which goes on while what could end it is under
 * way and fails once nothing is, and while it is in a call of VS Code's, the server's or a process's that [underWay]
 * names something under way for, which it says every minute with [report] once the call takes a minute. A case in a
 * call with nothing under way at three looks in a row, or still running at the hang's bound, is given up on: the waits
 * it is in fail at their next look with a [PlaybookTimeout], so that it ends through its own cleanup, whose waits wait
 * as they do; [caseSignal] aborts; and a call that it is in, out of any wait, [unstick] ends, once, as a restart of the
 * server does. This then waits, as before, while the case and [unstick] progress, saying every minute what the case
 * waits in ([waiting]); a case that does not end, or an [unstick] that does not, is left to itself, as a
 * [PlaybookLeftRunning].
 */
export async function runCase(body: () => Promise<void>, report: (line: string) => void, waiting: () => string[],
    underWay: () => Promise<string[]> | string[], unstick: () => Promise<unknown>): Promise<unknown> {
    const run: CaseRun = { waits: new Set(), given: new AbortController() };
    const before = current;
    current = run;
    try {
        return await given(run, body, report, waiting, underWay, unstick);
    } finally {
        current = before;
    }
}

async function given(run: CaseRun, body: () => Promise<void>, report: (line: string) => void, waiting: () => string[],
    underWay: () => Promise<string[]> | string[], unstick: () => Promise<unknown>): Promise<unknown> {
    let ended = false;
    const done = cases.run(run, body).then(() => undefined, (error: unknown) => error ?? new Error('The case failed with nothing'))
        .finally(() => { ended = true; });
    // the restart that ends the call a case given up on is in, while it has not ended
    let unsticking: Promise<unknown> | undefined;
    const where = () => [waiting().join('; ') || (ended ? '' : 'a call of VS Code, the server or a process'),
        ...(unsticking ? ["the restart of the language server that is to end the case's call"] : [])].filter(Boolean).join('; ');
    // whether the case and the restart, from [since], ended, or are in a call with nothing under way, or reached the
    // hang's bound; each look, a wait of 250 ms at most, ends as soon as either ends
    const progresses = async (since: number, what: string): Promise<'ended' | 'stuck' | 'hang'> => {
        const settles = () => Promise.race([...(ended ? [] : [done]), ...(unsticking ? [unsticking] : [])]);
        const finished = () => ended && unsticking === undefined;
        for (let idle = 0, calling = Date.now(), reported = calling; ;) {
            if (finished()) return 'ended';
            if (Date.now() - since >= PLAYBOOK_HANG_MS) return 'hang';
            // a wait of the playbook's says itself what it waits for, and fails once nothing is under way
            const work = run.waits.size > 0 ? undefined : await Promise.race([Promise.resolve().then(underWay), settles().then(() => [])]);
            if (finished()) return 'ended';
            if (work === undefined) calling = reported = Date.now();
            idle = work === undefined || work.length > 0 ? 0 : idle + 1;
            if (idle >= 3) return 'stuck';
            if (work !== undefined && Date.now() - reported >= REPORT_MS) {
                reported = Date.now();
                report(`${what}, ${Math.round((reported - calling) / 1000)} s in ${where()}; under way: ${work.join('; ')}`);
            }
            await Promise.race([settles(), delay(250)]);
        }
    };
    const begun = Date.now();
    const outcome = await progresses(begun, 'running');
    if (outcome === 'ended') return done;
    const abandoned = new PlaybookTimeout(outcome === 'hang'
        ? `The case did not end within the hang's bound of ${PLAYBOOK_HANG_MS / 1000} s, in ${where()}`
        : `The case was in ${where()} with nothing under way that could end it, at three looks in a row, after `
            + `${Math.round((Date.now() - begun) / 1000)} s`);
    run.waits.forEach(wait => { wait.abandoned = abandoned; });
    run.given.abort(abandoned);
    if (run.waits.size === 0) {
        report(`given up on: ${abandoned.message}; ending its call with a restart of the language server`);
        // as the case is waited for: a restart that does not end, as one whose server never answers, is no end
        unsticking = Promise.resolve().then(unstick)
            .catch((error: unknown) => report(`the restart failed: ${error instanceof Error ? error.message : String(error)}`))
            .finally(() => { unsticking = undefined; });
    } else {
        report(`given up on: ${abandoned.message}`);
    }
    const after = await progresses(Date.now(), 'given up on, waiting for the case to end');
    if (after !== 'ended') {
        const what = ended ? "the restart of the language server that was to end the case's call did not end" : 'it did not end';
        return new PlaybookLeftRunning(`${abandoned.message}; given up on, ${what}, ${after === 'hang'
            ? `within the hang's bound of ${PLAYBOOK_HANG_MS / 1000} s` : 'with nothing under way that could end it'}, in ${where()}: `
            + 'it is left to itself, and no other case runs');
    }
    const failure = await done;
    // the failure the case's wait threw, with what else the case failed with after it was given up on
    return failure === abandoned || failure === undefined ? abandoned
        : new PlaybookTimeout(`${abandoned.message}; given up on, the case failed: ${failure instanceof Error ? failure.stack ?? failure.message : String(failure)}`);
}

/**
 * The extension suite's wait: bounds both polling and a single read that never settles. The caller must stop on
 * timeout. The playbook's waits are [waitWhile]'s and [waitOn]'s.
 */
export async function waitFor<T>(read: () => Promise<T>, accept: (value: T) => boolean, message: string, timeoutMs = PLAYBOOK_WAIT_MS): Promise<T> {
    let last: T | undefined;
    const timer = new AbortController();
    const deadline = delay(timeoutMs, undefined, { signal: timer.signal }).then(() => {
        throw new PlaybookTimeout(`${message} timed out after ${timeoutMs} ms; last value: ${JSON.stringify(last)}`);
    });
    // the timer's abort, as the wait ends, rejects it: also when the wait ends before it raced it
    deadline.catch(() => undefined);
    const wait = entered();
    try {
        for (;;) {
            wait.given();
            last = await Promise.race([Promise.resolve().then(read), deadline]);
            if (accept(last)) return last;
            await Promise.race([delay(75), deadline]);
        }
    } finally {
        timer.abort();
        wait.leave();
    }
}

/**
 * Waits until [read]'s value satisfies [accept] for as long as anything that could change it is under way, which
 * [underWay] names for each value read: a value that does not, with nothing under way at three looks in a row, is
 * final, and the wait fails at once, with the value. It says every minute with [report] what it waits for and what is
 * under way, and fails at the hang's bound only as a last resort; time that passes with nothing under way is no
 * progress. A look that [pending] describes, as one whose read has not answered, neither counts toward the three nor
 * starts them again, and shows among what is under way.
 */
export async function waitWhile<T>(read: () => Promise<T>, accept: (value: T) => boolean, message: string,
    underWay: (value: T) => Promise<string[]> | string[], report: (line: string) => void,
    pending: (value: T) => string | undefined = () => undefined): Promise<T> {
    const begun = Date.now();
    const timer = new AbortController();
    let last: T | undefined;
    let work: string[] = [];
    const deadline = delay(PLAYBOOK_HANG_MS, undefined, { signal: timer.signal }).then(() => {
        throw new PlaybookTimeout(`${message}, not within the hang's bound of ${PLAYBOOK_HANG_MS / 1000} s, with ${work.join('; ') || 'nothing'} `
            + `under way; last value: ${JSON.stringify(last)}`);
    });
    deadline.catch(() => undefined);
    const wait = entered();
    try {
        for (let idle = 0, reported = begun; ;) {
            wait.given();
            const value = await Promise.race([Promise.resolve().then(read), deadline]);
            last = value;
            if (accept(value)) return value;
            work = await Promise.race([Promise.resolve().then(() => underWay(value)), deadline]);
            // a look whose read has not answered decides nothing: the reads that answer, however late, do
            const waiting = pending(value);
            if (work.length > 0) idle = 0;
            else if (waiting === undefined) idle++;
            if (waiting !== undefined) work = [waiting, ...work];
            if (idle >= 3) {
                throw new PlaybookFinal(`${message}: the value is final, with nothing under way that could change it, after `
                    + `${Math.round((Date.now() - begun) / 1000)} s; last value: ${JSON.stringify(last)}`, last);
            }
            if (Date.now() - reported >= REPORT_MS) {
                reported = Date.now();
                report(`waiting ${Math.round((reported - begun) / 1000)} s for: ${message}; under way: ${work.join('; ')}`);
            }
            await Promise.race([delay(75), deadline]);
        }
    } finally {
        timer.abort();
        wait.leave();
    }
}

/** A look of [waitOn] whose read has not answered. */
const UNANSWERED: unique symbol = Symbol('unanswered');

/**
 * Waits as [waitWhile] does, for a [read] that may take a while to answer, as a request of the server's or VS Code's:
 * a look waits for its answer at most [answerMs], and a read that has not answered is waited for again at the next
 * look, so that the wait says every minute what it waits for while the read takes long. Such a look decides nothing,
 * as [waitWhile]'s [pending] has it: the wait is not final while its read has not answered, and a read that answers,
 * however late, without the value and with nothing that [underWay] names, counts toward the final verdict. A read that
 * fails fails the wait at once.
 */
export async function waitOn<T>(read: () => Promise<T>, accept: (value: T) => boolean, message: string,
    underWay: () => Promise<string[]> | string[], report: (line: string) => void, answerMs = ANSWER_MS): Promise<T> {
    let asked: { answer: Promise<T>; since: number } | undefined;
    const look = async (): Promise<T | typeof UNANSWERED> => {
        asked ??= { answer: Promise.resolve().then(read), since: Date.now() };
        const timer = new AbortController();
        try {
            const value = await Promise.race([asked.answer,
                delay<typeof UNANSWERED>(answerMs, UNANSWERED, { signal: timer.signal }).catch((): typeof UNANSWERED => UNANSWERED)]);
            if (value !== UNANSWERED) asked = undefined;
            return value;
        } finally {
            timer.abort();
        }
    };
    // a final value is never one not answered
    return await waitWhile(look, value => value !== UNANSWERED && accept(value), message, () => underWay(), report,
        value => value === UNANSWERED && asked ? `its read, which has not answered for ${Math.round((Date.now() - asked.since) / 1000)} s` : undefined) as T;
}
