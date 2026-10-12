import { setTimeout as delay } from 'node:timers/promises';
import type Mocha from 'mocha';

export const PLAYBOOK_WAIT_MS = 30_000;

/** A timeout means the editor operation may still be running; never start another case. */
export class PlaybookTimeout extends Error {}
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
