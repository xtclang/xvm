import * as fs from 'node:fs/promises';
import * as path from 'node:path';
import * as vscode from 'vscode';
import type { DapMessage } from './tour';

/**
 * How long nothing may have happened, no request, compile, edit, diagnostic, debug message, task or run, before a wait
 * whose condition does not hold counts as idle: what the last of them changed has had the time to show.
 */
export const QUIET_MS = 60_000;

/**
 * The server's requests that a client repeats in the background, and the work that they make, as a workspace pull's
 * re-reading of the modules' names every 2 s does: such work counts as under way while its request is open, as a pull
 * that waits for an analysis is, but not as something that happened, which would keep every wait from going quiet.
 */
const BACKGROUND = new Set(['workspace/diagnostic', '$/cancelRequest', '$/setTrace', '$/progress']);

/** How many of the server's spans are kept, to find the request each piece of work is for: the newest. */
const SPANS = 200_000;

/** One piece of work under way, since [since], in milliseconds since the epoch, of the server process [pid], if any. */
interface Work {
    what: string;
    since: number;
    pid?: number;
}

/** A trace entry of the server, as far as the tour reads it. */
interface TraceEntry {
    id?: number;
    parent?: number;
    kind?: string;
    operation?: string;
    event?: string;
    direction?: string;
}

/**
 * What could still make a wait's condition true, read as it is, not from a time: the requests the server is working on
 * and its compiles, from the traces it writes as it works; the debug adapter's requests that have no response yet and
 * a program that runs; the tasks and the embedded runs that have not ended; and when anything last happened, the
 * server's work, an edit, the diagnostics or a debug message, whose effect may still be drawing.
 */
export class Activity implements vscode.Disposable {
    /** The server's requests that started and have not ended, by their servers' process IDs and their trace IDs. */
    private readonly requests = new Map<string, Work>();
    /** How many of the server's requests have ended, by their methods. */
    private readonly ends = new Map<string, number>();
    /** The compiler's jobs and compiles under way, by their servers' process IDs and their trace IDs. */
    private readonly compiles = new Map<string, Work>();
    /**
     * How far each of the server's trace files has been read, by the file's device and inode: the server rotates its
     * trace at 10 MB, renaming the file it wrote, which keeps its inode, and writing a new one by the old name.
     */
    private readonly offsets = new Map<string, number>();
    /** The server's spans, requests, queries, jobs and compiles, by their keys: what each is, and what it is for. */
    private readonly spans = new Map<string, { operation: string; parent?: string }>();
    /** What last happened, and when. */
    private last: Work = { what: 'the tour started', since: Date.now() };
    private readonly subscriptions: vscode.Disposable[];

    constructor(private readonly traces: string | undefined, private readonly dap: readonly { from: 'vscode' | 'adapter'; message: DapMessage }[],
                private readonly runs: () => string[]) {
        this.subscriptions = [
            vscode.workspace.onDidChangeTextDocument(event => {
                if (event.contentChanges.length > 0) this.happened(`an edit of ${path.basename(event.document.uri.path)}`);
            }),
            vscode.languages.onDidChangeDiagnostics(event => this.happened(`the diagnostics of ${event.uris.map(uri => path.basename(uri.path)).join(', ')}`)),
            vscode.window.onDidChangeActiveTextEditor(() => this.happened('the active editor changed')),
            vscode.tasks.onDidStartTask(event => this.happened(`the task ${event.execution.task.name} started`)),
            vscode.tasks.onDidEndTask(event => this.happened(`the task ${event.execution.task.name} ended`)),
            vscode.debug.onDidStartDebugSession(session => this.happened(`the debug session ${session.name} started`)),
            vscode.debug.onDidTerminateDebugSession(session => this.happened(`the debug session ${session.name} ended`)),
            vscode.window.onDidOpenTerminal(terminal => this.happened(`the terminal ${terminal.name} opened`)),
            vscode.window.onDidCloseTerminal(terminal => this.happened(`the terminal ${terminal.name} closed`)),
            vscode.window.onDidChangeVisibleTextEditors(() => this.happened('the visible editors changed')),
        ];
        // a file written, made or deleted, by the tour, a refactoring or a build, which the Explorer and the server follow
        const files = vscode.workspace.createFileSystemWatcher('**/*');
        this.subscriptions.push(files, files.onDidCreate(uri => this.happened(`${path.basename(uri.path)} was made`)),
            files.onDidChange(uri => this.happened(`${path.basename(uri.path)} was written`)),
            files.onDidDelete(uri => this.happened(`${path.basename(uri.path)} was deleted`)));
    }

    dispose(): void {
        this.subscriptions.forEach(subscription => subscription.dispose());
    }

    /** Notes that [what] happened now. */
    happened(what: string): void {
        this.last = { what, since: Date.now() };
    }

    /**
     * What is under way now, each with how long it has been: empty when nothing is, nothing is pending, running or
     * drawing, so that a condition that does not hold now will not hold later either.
     */
    async inProgress(): Promise<string[]> {
        await this.readTraces();
        if (this.dap.length !== this.dapSeen) {
            this.dapSeen = this.dap.length;
            this.happened('a debug message');
        }
        const now = Date.now();
        const ago = (since: number) => `${Math.round((now - since) / 1_000)} s`;
        const work = [
            ...[...this.requests.values()].map(each => `the server's ${each.what}, for ${ago(each.since)}`),
            ...[...this.compiles.values()].map(each => `the compiler's ${each.what}, for ${ago(each.since)}`),
            ...this.dapPending().map(command => `the debug adapter's ${command}`),
            ...vscode.tasks.taskExecutions.map(execution => `the task ${execution.task.name}`),
            ...this.runs(),
        ];
        const session = vscode.debug.activeDebugSession;
        if (session && !this.paused()) work.push(`the debug session ${session.name}, running`);
        if (now - this.last.since < QUIET_MS) work.push(`${this.last.what}, ${ago(this.last.since)} ago`);
        return work;
    }

    /** What the tour noticed last, for a report of a wait that found nothing in progress. */
    lastEvent(): string {
        return `${this.last.what}, ${Math.round((Date.now() - this.last.since) / 1_000)} s ago`;
    }

    /** How many of the server's requests of [method] have ended, answered, failed or canceled, as its traces have it. */
    ended(method: string): number {
        return this.ends.get(method) ?? 0;
    }

    /** The server's requests under way, for a report. */
    serverWork(): string[] {
        return [...this.requests.values(), ...this.compiles.values()].map(each => each.what);
    }

    /** How many debug messages the last [inProgress] saw. */
    private dapSeen = 0;

    /** The DAP requests VS Code sent that have no response yet. */
    private dapPending(): string[] {
        const answered = new Set(this.dap.filter(each => each.from === 'adapter' && each.message.type === 'response')
            .map(each => each.message.request_seq));
        return this.dap.filter(each => each.from === 'vscode' && each.message.type === 'request' && !answered.has(each.message.seq))
            .map(each => each.message.command!);
    }

    /** Whether the debug session is stopped: its last stop has no resume after it. */
    private paused(): boolean {
        const stop = this.dap.map(each => each.from === 'adapter' && each.message.type === 'event' && each.message.event === 'stopped').lastIndexOf(true);
        if (stop < 0) return false;
        return !this.dap.slice(stop + 1).some(({ from, message }) => from === 'adapter'
            && message.type === 'event' && ['continued', 'terminated', 'exited'].includes(message.event!));
    }

    /**
     * Reads what the server's traces have gained since the last read: its requests' and compiles' starts and ends. The
     * work of a server that has ended, as one that a restart replaced, is under way no more.
     */
    private async readTraces(): Promise<void> {
        if (!this.traces) return;
        const files = (await fs.readdir(this.traces, { recursive: true }).catch(() => [] as string[]))
            .filter(file => /lsp-trace-.*\.jsonl$/.test(file)).map(file => path.join(this.traces!, file));
        for (const work of [this.requests, this.compiles]) {
            for (const [key, each] of work) if (each.pid !== undefined && !alive(each.pid)) work.delete(key);
        }
        for (const file of files) {
            const pid = Number(/lsp-trace-(\d+)-/.exec(path.basename(file))?.[1]);
            const stat = await fs.stat(file).catch(() => undefined);
            if (!stat) continue;
            // a rotated file is read on from where its old name was, and the new file by that name from its start
            const identity = `${stat.dev}:${stat.ino}`;
            const size = stat.size;
            // and a file that shrank, as one truncated in place, from its start too
            const read = this.offsets.get(identity) ?? 0;
            const from = read <= size ? read : 0;
            if (size <= from) continue;
            const handle = await fs.open(file, 'r').catch(() => undefined);
            if (!handle) continue;
            try {
                const buffer = Buffer.alloc(size - from);
                const { bytesRead } = await handle.read(buffer, 0, buffer.length, from);
                const text = buffer.subarray(0, bytesRead).toString('utf8');
                // a line still being written is read with the next
                const complete = text.lastIndexOf('\n') + 1;
                this.offsets.set(identity, from + Buffer.byteLength(text.slice(0, complete)));
                for (const line of text.slice(0, complete).split('\n')) this.traced(line, Number.isInteger(pid) ? pid : undefined);
            } finally {
                await handle.close();
            }
        }
    }

    /** Takes one line of the trace of the server [pid] into the work under way. */
    private traced(line: string, pid: number | undefined): void {
        if (!line) return;
        let entry: TraceEntry;
        try {
            entry = JSON.parse(line) as TraceEntry;
        } catch {
            return;
        }
        if (entry.id === undefined || !entry.operation) return;
        const key = `${pid}:${entry.id}`;
        if (!this.spans.has(key)) {
            this.spans.set(key, { operation: entry.operation, parent: entry.parent === undefined ? undefined : `${pid}:${entry.parent}` });
            if (this.spans.size > SPANS) this.spans.delete(this.spans.keys().next().value!);
        }
        const background = this.background(key);
        if (entry.kind === 'lsp-request' && entry.direction !== 'to-client') {
            if (entry.event === 'start') this.requests.set(key, { what: entry.operation, since: Date.now(), pid });
            if (entry.event === 'end') {
                this.requests.delete(key);
                this.ends.set(entry.operation, this.ended(entry.operation) + 1);
            }
            if (!background) this.happened(`the server's ${entry.operation}`);
        } else if ((entry.kind === 'compiler-queue' || entry.kind === 'javatools') && !background) {
            if (entry.event === 'submitted' || entry.event === 'start') this.compiles.set(key, { what: entry.operation, since: Date.now(), pid });
            if (entry.event === 'end' || entry.event === 'removed' || entry.event === 'cancel-or-failure-signalled') this.compiles.delete(key);
            this.happened(`the compiler's ${entry.operation}`);
        }
    }

    /** Whether the span [key] is, or is for, a request that a client repeats in the background, by its parents. */
    private background(key: string): boolean {
        let span = this.spans.get(key);
        for (let depth = 0; span && depth < 32; depth++) {
            if (BACKGROUND.has(span.operation)) return true;
            span = span.parent === undefined ? undefined : this.spans.get(span.parent);
        }
        return false;
    }
}

/** Whether the process [pid] is still alive. */
function alive(pid: number): boolean {
    try {
        process.kill(pid, 0);
        return true;
    } catch {
        return false;
    }
}
