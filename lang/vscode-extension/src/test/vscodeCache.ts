// Where the headless tests keep their VS Code builds.
//
// @vscode/test-electron downloads each VS Code build into the checkout's .vscode-test/ by default,
// so every checkout and worktree kept its own copy of every version it ever tested, and each new
// stable release added another ~500 MB that nothing removed. The builds now live in one per-user
// cache, one copy per version, and builds no test run has used for PRUNE_AFTER_DAYS are removed.
// The throwaway test profile (user data, extensions) stays in the checkout's .vscode-test/.

import { mkdirSync, readdirSync, rmSync, statSync, utimesSync } from 'node:fs';
import * as os from 'node:os';
import * as path from 'node:path';

const PRUNE_AFTER_DAYS = 30;
const DAY_MS = 24 * 60 * 60 * 1000;
// Longer than any download takes: a lock this old was left by a run that died.
const STALE_LOCK_MS = 15 * 60 * 1000;
const LOCK_POLL_MS = 1000;

/** The per-user VS Code build cache, or XTC_VSCODE_TEST_CACHE when set. */
export function sharedCachePath(): string {
    const override = process.env.XTC_VSCODE_TEST_CACHE;
    if (override) {
        return override;
    }
    const userCache =
        process.env.XDG_CACHE_HOME ??
        (process.platform === 'darwin'
            ? path.join(os.homedir(), 'Library', 'Caches')
            : process.platform === 'win32'
              ? (process.env.LOCALAPPDATA ?? path.join(os.homedir(), 'AppData', 'Local'))
              : path.join(os.homedir(), '.cache'));
    return path.join(userCache, 'xtclang', 'vscode-test');
}

/**
 * Runs action while holding the cache's download lock. test-electron deletes and re-downloads a
 * build that lacks its completion marker, so two runs downloading the same version at once could
 * delete each other's build; across checkouts, one download at a time.
 */
export async function withDownloadLock<T>(cachePath: string, action: () => Promise<T>): Promise<T> {
    const lock = path.join(cachePath, '.download.lock');
    mkdirSync(cachePath, { recursive: true });
    while (!tryAcquire(lock)) {
        await new Promise((resolve) => setTimeout(resolve, LOCK_POLL_MS));
    }
    try {
        return await action();
    } finally {
        rmSync(lock, { recursive: true, force: true });
    }
}

function tryAcquire(lock: string): boolean {
    try {
        mkdirSync(lock);
        return true;
    } catch (err) {
        if ((err as NodeJS.ErrnoException).code !== 'EEXIST') {
            throw err;
        }
    }
    try {
        if (Date.now() - statSync(lock).mtimeMs > STALE_LOCK_MS) {
            rmSync(lock, { recursive: true, force: true });
        }
    } catch (err) {
        // The holder released the lock between the two calls; try again.
        if ((err as NodeJS.ErrnoException).code !== 'ENOENT') {
            throw err;
        }
    }
    return false;
}

/**
 * Removes the VS Code builds a checkout downloaded into its own .vscode-test/ before builds moved to
 * the shared cache. Only builds: the test profile directories there are still in use.
 */
export function removeCheckoutBuilds(checkoutCache: string): string[] {
    let entries;
    try {
        entries = readdirSync(checkoutCache, { withFileTypes: true });
    } catch (err) {
        if ((err as NodeJS.ErrnoException).code === 'ENOENT') {
            return [];
        }
        throw err;
    }
    const builds = entries
        .filter((entry) => entry.isDirectory() && entry.name.startsWith('vscode-'))
        .map((entry) => path.join(checkoutCache, entry.name));
    for (const build of builds) {
        rmSync(build, { recursive: true, force: true });
    }
    return builds;
}

/** The cache directory of the build that executable belongs to. */
export function buildDirectory(cachePath: string, executable: string): string {
    return path.join(cachePath, path.relative(cachePath, executable).split(path.sep)[0]);
}

/**
 * Marks the builds this run uses, then removes builds no run has used for PRUNE_AFTER_DAYS. Call
 * it while holding the download lock, so it never removes a build another run is downloading.
 */
export function markUsedAndPrune(cachePath: string, usedBuilds: readonly string[], now = new Date()): string[] {
    for (const build of usedBuilds) {
        utimesSync(build, now, now);
    }
    const pruned = readdirSync(cachePath, { withFileTypes: true })
        .filter((entry) => entry.isDirectory() && entry.name.startsWith('vscode-'))
        .map((entry) => path.join(cachePath, entry.name))
        .filter((build) => now.getTime() - statSync(build).mtimeMs > PRUNE_AFTER_DAYS * DAY_MS);
    for (const build of pruned) {
        rmSync(build, { recursive: true, force: true });
    }
    return pruned;
}
