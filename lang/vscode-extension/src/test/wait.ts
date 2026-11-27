import { AsyncLocalStorage } from 'node:async_hooks';
import { setTimeout as delay } from 'node:timers/promises';
import type Mocha from 'mocha';

export const PLAYBOOK_WAIT_MS = 30_000;

/** The hang's bound, an hour: the last resort of a wait that what is under way keeps going. */
export const PLAYBOOK_HANG_MS = 3_600_000;

/** How often a wait that goes on says what it waits for, and what is under way. */
const REPORT_MS = 60_000;

/**
 * A wait that did not end within its bound: the editor's operation may still be running, so the extension suite
 * starts no other test; the playbook lets the case end and restores the editor before its next case.
 */
export class PlaybookTimeout extends Error {}
/** A progress-based wait's value that does not do, with nothing under way that could change it. */
export class PlaybookFinal extends Error {}
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

/** A playbook case, as its body runs: the waits it is in. */
interface CaseRun { readonly waits: Set<Waiting> }

/** The case each wait belongs to, by the async context its body runs in, which outlives the playbook's wait for it. */
const cases = new AsyncLocalStorage<CaseRun>();

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
 * Runs [body], a playbook case, and answers its failure, or undefined once it passed. Past [boundMs] the case is given
 * up on: the waits it is in fail at their next look with a [PlaybookTimeout], so that it ends through its own cleanup,
 * whose waits wait as they do, and this waits for it to end, saying every minute with [report] what the case waits
 * in, [waiting]. A case that is in no wait of the playbook's at such a minute is in a call of VS Code or the language
 * server, which [unstick] ends, once, as a restart of the server does; at the hang's bound this leaves it to itself.
 */
export async function runCase(body: () => Promise<void>, boundMs: number, report: (line: string) => void,
    waiting: () => string[], unstick: () => Promise<unknown>): Promise<unknown> {
    const run: CaseRun = { waits: new Set() };
    let ended = false;
    const done = cases.run(run, body).then(() => undefined, (error: unknown) => error ?? new Error('The case failed with nothing'))
        .finally(() => { ended = true; });
    const timer = new AbortController();
    const bound = delay(boundMs, 'bound', { signal: timer.signal }).catch(() => 'ended');
    const first = await Promise.race([done, bound]);
    timer.abort();
    if (!ended) {
        const abandoned = new PlaybookTimeout(`The case did not end within its bound of ${boundMs / 1000} s`);
        run.waits.forEach(wait => { wait.abandoned = abandoned; });
        const begun = Date.now();
        let unstuck = false;
        for (let reported = begun; !ended;) {
            const elapsed = Date.now() - begun;
            const where = () => waiting().join('; ') || 'a call of VS Code or the server';
            if (elapsed >= PLAYBOOK_HANG_MS) {
                return new PlaybookTimeout(`${abandoned.message}, nor within the hang's bound of ${PLAYBOOK_HANG_MS / 1000} s after it, in ${where()}`);
            }
            if (Date.now() - reported >= REPORT_MS) {
                reported = Date.now();
                report(`past its bound, waiting ${Math.round(elapsed / 1000)} s for the case to end, in: ${where()}`);
                if (run.waits.size === 0 && !unstuck) {
                    unstuck = true;
                    report('in no wait of the playbook\'s past its bound: ending its call with a restart of the language server');
                    await unstick().catch((error: unknown) => report(`the restart failed: ${error instanceof Error ? error.message : String(error)}`));
                }
            }
            await Promise.race([done, delay(250)]);
        }
        const failure = await done;
        // the bound's failure, which the case's wait threw, with what else the case failed with past its bound
        return failure === abandoned || failure === undefined ? abandoned
            : new PlaybookTimeout(`${abandoned.message}; past it, the case failed: ${failure instanceof Error ? failure.stack ?? failure.message : String(failure)}`);
    }
    return first;
}

/** Bounds both polling and a single read that never settles. The caller must stop on timeout. */
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
 * progress.
 */
export async function waitWhile<T>(read: () => Promise<T>, accept: (value: T) => boolean, message: string,
    underWay: (value: T) => Promise<string[]> | string[], report: (line: string) => void): Promise<T> {
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
            idle = work.length > 0 ? 0 : idle + 1;
            if (idle >= 3) {
                throw new PlaybookFinal(`${message}: the value is final, with nothing under way that could change it, after `
                    + `${Math.round((Date.now() - begun) / 1000)} s; last value: ${JSON.stringify(last)}`);
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
