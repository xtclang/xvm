import { realpathSync } from 'node:fs';
import * as fs from 'node:fs/promises';
import * as path from 'node:path';
import { fileURLToPath } from 'node:url';

/*
 * The file system of node:fs/promises, which remembers the changes a case makes on disk until the language server has
 * heard of them: a wait for diagnostics takes nothing as final before then. The server hears of a change from the
 * editor's file watcher, which reports it a while after it is made, and from the language client, which holds what it
 * reports for a quarter of a second more.
 */
export * from 'node:fs/promises';

/** When a case changed each file or directory, by its real path, in milliseconds since the epoch. */
const unheard = new Map<string, number>();

export async function writeFile(...args: Parameters<typeof fs.writeFile>): Promise<void> {
    await fs.writeFile(...args);
    changed(args[0]);
}

export async function appendFile(...args: Parameters<typeof fs.appendFile>): Promise<void> {
    await fs.appendFile(...args);
    changed(args[0]);
}

export async function copyFile(...args: Parameters<typeof fs.copyFile>): Promise<void> {
    await fs.copyFile(...args);
    changed(args[1]);
}

export async function cp(...args: Parameters<typeof fs.cp>): Promise<void> {
    await fs.cp(...args);
    changed(args[1]);
}

export async function rename(...args: Parameters<typeof fs.rename>): Promise<void> {
    await fs.rename(...args);
    changed(args[0], args[1]);
}

export async function unlink(...args: Parameters<typeof fs.unlink>): Promise<void> {
    await fs.unlink(...args);
    changed(args[0]);
}

export async function rm(...args: Parameters<typeof fs.rm>): Promise<void> {
    // a forced removal of nothing changes nothing
    const existed = await fs.lstat(args[0]).then(() => true, () => false);
    await fs.rm(...args);
    if (existed) changed(args[0]);
}

export async function rmdir(...args: Parameters<typeof fs.rmdir>): Promise<void> {
    await fs.rmdir(...args);
    changed(args[0]);
}

/** Forgets the changes of the cases before, which changed only their own files. */
export function forgetChanges(): void {
    unheard.clear();
}

/**
 * Whether the server has still to hear of a change a case made on disk. It has heard of one once it received a
 * watched-file event later than the change, of the file, or of a directory it is in or a file in it, as the editor
 * reports a directory's removal: [received] tells when it received the latest of each file, by URI, in milliseconds since
 * the epoch. The server never hears of a change no watcher reports, which leaves a wait to its timeout.
 */
export function unheardChanges(received: Record<string, number>): boolean {
    if (unheard.size === 0) return false;
    const heard = Object.entries(received)
        .filter(([uri]) => uri.startsWith('file:'))
        .map(([uri, time]) => ({ file: real(fileURLToPath(uri)), time }));
    for (const [file, time] of unheard) {
        if (heard.some(each => each.time > time && (each.file === file || within(file, each.file) || within(each.file, file)))) {
            unheard.delete(file);
        }
    }
    return unheard.size > 0;
}

function changed(...targets: unknown[]): void {
    const time = Date.now();
    for (const target of targets) {
        const file = typeof target === 'string' ? target
            : target instanceof URL ? fileURLToPath(target)
                : Buffer.isBuffer(target) ? target.toString() : undefined;
        if (file !== undefined) unheard.set(real(file), time);
    }
}

function within(file: string, directory: string): boolean {
    return file.startsWith(directory + path.sep);
}

/** The real path of [file], or of its directory and its name, once it is removed. */
function real(file: string): string {
    try {
        return realpathSync(file);
    } catch {
        try {
            return path.join(realpathSync(path.dirname(file)), path.basename(file));
        } catch {
            return path.resolve(file);
        }
    }
}
