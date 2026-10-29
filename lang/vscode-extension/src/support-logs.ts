import { constants } from 'node:fs';
import * as fs from 'node:fs/promises';
import * as path from 'node:path';
import { randomUUID } from 'node:crypto';
import { ZipFile } from 'yazl';
import type { ExtensionContext, Memento } from 'vscode';

const FILE_LIMIT = 512 * 1024;
const FILE_COUNT = 8;
const LAUNCH_LIMIT = 64 * 1024;
const STATE_KEY = 'xtc.supportSession';
const sessions = new WeakMap<ExtensionContext, SupportLogs>();

interface Session {
    readonly id: string;
    readonly started: string;
    readonly launcher: string;
    readonly logs?: { directory: string; traceDirectory: string };
}

export function supportLogs(context: ExtensionContext): SupportLogs {
    const existing = sessions.get(context);
    if (existing) return existing;
    const created = new SupportLogs(context.workspaceState);
    sessions.set(context, created);
    return created;
}

/** One immutable snapshot at a time, owned by the extension event loop and workspace storage. */
export class SupportLogs {
    private current: Session;
    constructor(private readonly storage: Memento) {
        this.current = storage.get<Session>(STATE_KEY) ?? { id: '', started: '', launcher: 'No Ecstasy server launch recorded.' };
    }

    begin(description: string): string {
        const id = randomUUID();
        this.current = { id, started: new Date().toISOString(), launcher: description.slice(-LAUNCH_LIMIT) };
        this.save();
        return id;
    }

    append(id: string, text: string): void {
        if (this.current.id === id) this.current = { ...this.current, launcher: (this.current.launcher + text).slice(-LAUNCH_LIMIT) };
    }

    remember(id: string, status: { logs: { directory: string; traceDirectory: string } }): void {
        if (this.current.id !== id) return;
        this.current = { ...this.current, logs: status.logs };
        this.save();
    }

    finish(id: string, text: string): void {
        if (this.current.id !== id) return;
        this.append(id, text);
        this.save();
    }

    private save(): void {
        // VS Code owns persistence ordering. Stale connection callbacks cannot replace this snapshot.
        void this.storage.update(STATE_KEY, this.current);
    }

    async export(): Promise<Buffer> { return offlineArchive(this.current); }
}

/** A recorded session only; never look for the newest directory in the shared log root. */
export async function offlineArchive(session: Session): Promise<Buffer> {
    const entries: { name: string; bytes: Buffer; originalBytes: number }[] = [];
    const logs = session.logs;
    const process = logs && /^server-(\d+-\d+)$/.exec(path.basename(logs.directory))?.[1];
    if (logs && process) {
        for (const directory of [...new Set([logs.directory, logs.traceDirectory])]) {
            if (!(await fs.lstat(directory).catch(() => undefined))?.isDirectory()) continue;
            const files = (await fs.readdir(directory, { withFileTypes: true })).filter(file => file.isFile() &&
                (directory === logs.directory ? /^(server(?:\.[0-9.-]+)?\.log|lsp-trace-[0-9.-]+\.jsonl)$/.test(file.name)
                    : file.name.startsWith(`lsp-trace-${process}`) && file.name.endsWith('.jsonl')));
            for (const file of files.sort((a, b) => b.name.localeCompare(a.name))) {
                if (entries.length >= FILE_COUNT) break;
                const handle = await fs.open(path.join(directory, file.name), constants.O_RDONLY | constants.O_NOFOLLOW).catch(() => undefined);
                if (!handle) continue; // A retained log can disappear during pruning.
                try {
                    const stat = await handle.stat();
                    if (!stat.isFile()) continue;
                    const bytes = Buffer.alloc(Math.min(stat.size, FILE_LIMIT));
                    const { bytesRead } = await handle.read(bytes, 0, bytes.length, Math.max(0, stat.size - FILE_LIMIT));
                    entries.push({ name: `${entries.length}-${file.name}`, bytes: bytes.subarray(0, bytesRead), originalBytes: stat.size });
                } finally { await handle.close(); }
            }
        }
    }
    const zip = new ZipFile();
    const chunks: Buffer[] = [];
    const result = new Promise<Buffer>((resolve, reject) => {
        zip.on('error', reject);
        zip.outputStream.on('error', reject).on('data', chunk => chunks.push(chunk)).on('end', () => resolve(Buffer.concat(chunks)));
    });
    for (const entry of entries) zip.addBuffer(entry.bytes, entry.name);
    zip.addBuffer(Buffer.from(session.launcher).subarray(-LAUNCH_LIMIT), 'launcher.log');
    zip.addBuffer(Buffer.from(JSON.stringify({ schemaVersion: 1, offline: true, started: session.started,
        scope: 'Last launch recorded by this workspace; bounded launcher stderr and server log tails. No source files or IDE protocol consoles.',
        logs: session.logs, files: entries.map(({ name, bytes, originalBytes }) => ({ entry: name, originalBytes, includedBytes: bytes.length, truncated: originalBytes > bytes.length })) }, null, 2)), 'manifest.json');
    zip.end();
    return result;
}
