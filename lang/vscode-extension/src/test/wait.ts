import { setTimeout as delay } from 'node:timers/promises';
import type Mocha from 'mocha';

export const PLAYBOOK_WAIT_MS = 30_000;

/** The hang's bound, an hour: the last resort of a wait that what is under way keeps going. */
export const PLAYBOOK_HANG_MS = 3_600_000;

/** How often a wait that goes on says what it waits for, and what is under way. */
const REPORT_MS = 60_000;

/** A timeout means the editor operation may still be running; never start another case. */
export class PlaybookTimeout extends Error {}
/** A progress-based wait's value that does not do, with nothing under way that could change it. */
export class PlaybookFinal extends Error {}
export class PlaybookCleanupFailure extends Error {
    constructor(readonly failures: readonly unknown[]) { super(`Playbook cleanup failed: ${failures.map(String).join('; ')}`); }
}

/** Always restore test settings; retain the original failure if cleanup also fails and abort the run. */
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

export function stopOnUnsafeFailure(runner: Mocha.Runner): void {
    runner.on('fail', (_test, error) => { if (mustStopPlaybook(error)) runner.abort(); });
}

/** Bounds both polling and a single read that never settles. The caller must stop on timeout. */
export async function waitFor<T>(read: () => Promise<T>, accept: (value: T) => boolean, message: string, timeoutMs = PLAYBOOK_WAIT_MS): Promise<T> {
    let last: T | undefined;
    const timer = new AbortController();
    const deadline = delay(timeoutMs, undefined, { signal: timer.signal }).then(() => {
        throw new PlaybookTimeout(`${message} timed out after ${timeoutMs} ms; last value: ${JSON.stringify(last)}`);
    });
    try {
        for (;;) {
            last = await Promise.race([Promise.resolve().then(read), deadline]);
            if (accept(last)) return last;
            await Promise.race([delay(75), deadline]);
        }
    } finally { timer.abort(); }
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
    try {
        for (let idle = 0, reported = begun; ;) {
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
    } finally { timer.abort(); }
}
