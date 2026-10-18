import * as fs from 'node:fs';
import * as path from 'node:path';
import { randomUUID } from 'node:crypto';
import * as vscode from 'vscode';
import type { LanguageClient } from 'vscode-languageclient/node';

import { ConsoleLineEditor } from './console-input';

/**
 * Embedded build and run: the language server's compiler builds into the workspace's embedded output
 * directory, and its execution worker runs the result. This is the counterpart of building and running
 * with Gradle, chosen per folder by `xtc.build.mode`.
 */

export type BuildMode = 'embedded' | 'gradle';

const GRADLE_BUILD_FILES = ['build.gradle.kts', 'build.gradle', 'settings.gradle.kts', 'settings.gradle'];

/** Whether the folder has its own Gradle build. */
export function hasGradleBuild(folder: vscode.WorkspaceFolder | undefined): boolean {
    return !!folder && GRADLE_BUILD_FILES.some(name => fs.existsSync(path.join(folder.uri.fsPath, name)));
}

/**
 * The effective mode for a folder: the setting, embedded by default even with a Gradle build, or with `auto`,
 * Gradle exactly when the folder has a Gradle build.
 */
export function buildMode(folder: vscode.WorkspaceFolder | undefined): BuildMode {
    const setting = vscode.workspace.getConfiguration('xtc', folder).get<string>('build.mode', 'embedded');
    if (setting === 'embedded' || setting === 'gradle') {
        return setting;
    }
    return hasGradleBuild(folder) ? 'gradle' : 'embedded';
}

/** Shown by embedded builds and runs in a folder with a Gradle build, whose own settings do not apply. */
export const GRADLE_NOT_USED =
    'This folder has a Gradle build, but embedded mode builds and runs with the language server\'s compiler and XDK; ' +
    'Gradle settings such as runXtc arguments, JVM options and the build\'s XDK version do not apply. ' +
    'Choose Gradle with "Ecstasy: Select Build Mode" or the build mode in the status bar to use Gradle.';

interface LspDiagnostic {
    range: { start: { line: number; character: number } };
    severity?: number;
    message: string;
    code?: string | number;
}

interface BuildResponse {
    succeeded: boolean;
    cancelled: boolean;
    modules: { name: string; uri: string; succeeded: boolean; skipped: boolean; artifact?: string }[];
    diagnostics: { uri: string; diagnostics: LspDiagnostic[] }[];
    outputDirectory?: string;
}

interface RunResponse {
    runId?: string;
    build: BuildResponse;
}

/** Where a run's events go; the server reports by the run ID the client chose. */
interface RunSession {
    output(text: string): void;
    exited(exitCode: number | undefined, error: string | undefined): void;
}

const sessions = new Map<string, RunSession>();

/**
 * A build or run as its terminal shows it: the text written so far (its last `KEPT_TEXT` characters), and the
 * exit code once finished.
 */
export interface ExecutionUpdate {
    kind: 'build' | 'run';
    label: string;
    text: string;
    exitCode?: number;
}

const updates = new vscode.EventEmitter<ExecutionUpdate>();

/** Fires when an embedded build or run writes output and when it finishes. */
export const onDidUpdateExecution = updates.event;

/**
 * Routes run notifications to their sessions. JSON-RPC keeps one handler per method, so this is registered
 * once per client rather than per run.
 */
export function attachEmbeddedExecution(client: LanguageClient): vscode.Disposable {
    const output = client.onNotification('xtc/runOutput', (params: { runId: string; text: string }) =>
        sessions.get(params.runId)?.output(params.text));
    const exited = client.onNotification(
        'xtc/runExited',
        (params: { runId: string; exitCode?: number | null; error?: string | null }) =>
            sessions.get(params.runId)?.exited(params.exitCode ?? undefined, params.error ?? undefined));
    return vscode.Disposable.from(output, exited);
}

/** A stopped or restarted language server takes its execution worker with it. */
export function endAllRuns(reason: string): void {
    for (const session of [...sessions.values()]) {
        session.exited(undefined, reason);
    }
}

/** The most text a build or run keeps for its updates, so a program that prints without end holds no more. */
export const KEPT_TEXT = 1_000_000;

/** `text` appended to `kept`, keeping the last `KEPT_TEXT` characters and never half a surrogate pair. */
export function keptText(kept: string, text: string): string {
    const all = kept + text;
    if (all.length <= KEPT_TEXT) {
        return all;
    }
    const start = all.length - KEPT_TEXT;
    const code = all.charCodeAt(start);
    return all.slice(code >= 0xdc00 && code <= 0xdfff ? start + 1 : start);
}

/** Terminal text needs carriage returns before line feeds. */
function terminalText(text: string): string {
    return text.replace(/\r?\n/g, '\r\n');
}

function severityLabel(severity: number | undefined): string {
    switch (severity) {
        case 2: return 'warning';
        case 3: return 'info';
        case 4: return 'hint';
        default: return 'error';
    }
}

/** Prints module outcomes and diagnostics as `file:line:column` lines, which the terminal links to sources. */
function describeBuild(build: BuildResponse): string {
    const lines: string[] = [];
    for (const module of build.modules) {
        if (module.succeeded) {
            lines.push(`  ✓ ${module.name}${module.artifact ? ` -> ${module.artifact}` : ''}`);
        } else if (module.skipped) {
            lines.push(`  - ${module.name} (not built: a dependency failed)`);
        } else {
            lines.push(`  ✗ ${module.name}`);
        }
    }
    for (const { uri, diagnostics } of build.diagnostics) {
        const file = uri.startsWith('file:') ? vscode.Uri.parse(uri).fsPath : uri;
        for (const diagnostic of diagnostics) {
            const { line, character } = diagnostic.range.start;
            const code = diagnostic.code === undefined ? '' : ` [${diagnostic.code}]`;
            lines.push(`${file}:${line + 1}:${character + 1}: ${severityLabel(diagnostic.severity)}: ${diagnostic.message}${code}`);
        }
    }
    if (build.cancelled) {
        lines.push('Build cancelled.');
    }
    return lines.join('\n');
}

abstract class ExecutionTerminal implements vscode.Pseudoterminal {
    private readonly writer = new vscode.EventEmitter<string>();
    private readonly closer = new vscode.EventEmitter<number>();
    readonly onDidWrite = this.writer.event;
    readonly onDidClose = this.closer.event;
    private finished = false;
    private text = '';

    constructor(protected readonly client: () => LanguageClient | undefined,
                private readonly kind: ExecutionUpdate['kind'], private readonly label: string) {}

    abstract open(): void;

    close(): void {
        this.finish(1);
    }

    protected write(text: string): void {
        this.text = keptText(this.text, text);
        this.writer.fire(terminalText(text));
        updates.fire({ kind: this.kind, label: this.label, text: this.text });
    }

    protected line(text: string): void {
        this.write(`${text}\n`);
    }

    /** Shows what the user types; it is terminal display, not program output. */
    protected echo(text: string): void {
        this.writer.fire(text);
    }

    protected finish(code: number): void {
        if (!this.finished) {
            this.finished = true;
            this.closer.fire(code);
            updates.fire({ kind: this.kind, label: this.label, text: this.text, exitCode: code });
        }
    }

    /** Builds always start from saved files, so the artifacts on disk match the sources. */
    protected async connection(): Promise<LanguageClient | undefined> {
        await vscode.workspace.saveAll(false);
        const client = this.client();
        if (!client?.isRunning()) {
            this.line('The Ecstasy language server is not running.');
            this.finish(1);
            return undefined;
        }
        return client;
    }
}

class BuildTerminal extends ExecutionTerminal {
    private readonly cancellation = new vscode.CancellationTokenSource();

    constructor(client: () => LanguageClient | undefined, private readonly module: string | undefined,
                private readonly rebuild: boolean, private readonly folder: vscode.WorkspaceFolder | undefined) {
        super(client, 'build', module ?? 'all Ecstasy modules');
    }

    open(): void {
        void this.build();
    }

    override close(): void {
        this.cancellation.cancel();
        super.close();
    }

    private async build(): Promise<void> {
        const client = await this.connection();
        if (!client) {
            return;
        }
        this.line(`${this.rebuild ? 'Rebuilding' : 'Building'} ${this.module ?? 'all Ecstasy modules'} with the embedded compiler...`);
        if (hasGradleBuild(this.folder)) {
            this.line(GRADLE_NOT_USED);
        }
        try {
            const build = await client.sendRequest<BuildResponse>(
                'xtc/build', { module: this.module, rebuild: this.rebuild }, this.cancellation.token);
            this.line(describeBuild(build));
            this.line(build.succeeded ? 'Build succeeded.' : 'Build failed.');
            this.finish(build.succeeded ? 0 : 1);
        } catch (error) {
            this.line(`Build failed: ${error instanceof Error ? error.message : String(error)}`);
            this.finish(1);
        }
    }
}

/** What an embedded run executes: a module name or any of its source URIs, and its entry point. */
export interface RunTarget {
    module: string;
    label: string;
    method?: string;
    args?: string[];
    workingDirectory?: string;
    /** The folder that owns the module, if known. */
    folder?: vscode.WorkspaceFolder;
}

/** How long a stopped run may take to report its exit before the task ends anyway. */
const STOP_TIMEOUT_MS = 10_000;

class RunTerminal extends ExecutionTerminal implements RunSession {
    private readonly runId = randomUUID();
    private readonly input = new ConsoleLineEditor();
    private running = false;
    private inputEnded = false;

    constructor(client: () => LanguageClient | undefined, private readonly target: RunTarget) {
        super(client, 'run', target.label);
    }

    open(): void {
        void this.run();
    }

    /**
     * Closing or stopping the task stops the program. The task ends when the server reports the exit, so a
     * run started right afterwards does not find the worker still busy with this one.
     */
    override close(): void {
        if (!this.running) {
            sessions.delete(this.runId);
            super.close();
            return;
        }
        const client = this.client();
        void client?.sendRequest<boolean>('xtc/stopRun', { runId: this.runId })
            .then(active => {
                if (!active) this.exited(undefined, 'Stopped.');
            }, () => this.exited(undefined, 'Stopped.'));
        if (!client) this.exited(undefined, 'Stopped.');
        setTimeout(() => this.exited(undefined, 'The program did not report its exit after Stop.'), STOP_TIMEOUT_MS);
    }

    /**
     * Typed lines go to the program's console, including while the module still builds (the server holds
     * them until it starts). Ctrl+D on an empty line ends the input; Ctrl+C stops the program.
     */
    handleInput(data: string): void {
        if (!this.running || this.inputEnded) {
            return;
        }
        const step = this.input.feed(data);
        if (step.interrupt) {
            this.echo('^C\r\n');
            this.close();
            return;
        }
        this.echo(step.echo);
        const text = step.lines.join('');
        this.inputEnded = step.end;
        if (text || step.end) {
            void this.client()?.sendRequest<boolean>('xtc/runInput', { runId: this.runId, text, end: step.end })
                .then(undefined, () => undefined);
        }
    }

    output(text: string): void {
        this.write(text);
    }

    exited(exitCode: number | undefined, error: string | undefined): void {
        if (!sessions.has(this.runId) && !this.running) return;
        this.running = false;
        sessions.delete(this.runId);
        if (error) {
            this.line(`\n${error}`);
        }
        this.line(`\nProcess finished with exit code ${exitCode ?? 1}`);
        this.finish(exitCode ?? 1);
    }

    private async run(): Promise<void> {
        const client = await this.connection();
        if (!client) {
            return;
        }
        this.line(`Building and running ${this.target.label} with the embedded compiler...`);
        if (hasGradleBuild(this.target.folder)) {
            this.line(GRADLE_NOT_USED);
        }
        // register before sending, because output can arrive before the response
        sessions.set(this.runId, this);
        this.running = true;
        try {
            const response = await client.sendRequest<RunResponse>('xtc/run', {
                module: this.target.module,
                method: this.target.method,
                args: this.target.args ?? [],
                workingDirectory: this.target.workingDirectory,
                runId: this.runId,
            });
            if (!response.runId) {
                this.running = false;
                sessions.delete(this.runId);
                this.line(describeBuild(response.build));
                this.line('Build failed; nothing was run.');
                this.finish(1);
            }
        } catch (error) {
            this.running = false;
            sessions.delete(this.runId);
            this.line(`Run failed: ${error instanceof Error ? error.message : String(error)}`);
            this.finish(1);
        }
    }
}

/** An embedded build task for a folder (all modules) or for one module. */
export function createEmbeddedBuildTask(client: () => LanguageClient | undefined, scope: vscode.WorkspaceFolder | vscode.TaskScope,
                                        rebuild: boolean, module?: string): vscode.Task {
    const name = rebuild ? 'Rebuild' : 'Build';
    const task = new vscode.Task(
        { type: 'xtc', moduleName: `_embedded_${name.toLowerCase()}`, mode: 'embedded' },
        scope,
        name,
        'xtc',
        new vscode.CustomExecution(async () => new BuildTerminal(client, module, rebuild, typeof scope === 'object' ? scope : undefined)),
        []
    );
    task.group = vscode.TaskGroup.Build;
    task.presentationOptions = { reveal: vscode.TaskRevealKind.Always, panel: vscode.TaskPanelKind.Shared, clear: true };
    return task;
}

/** An embedded run task; stopping the task stops the program. */
export function createEmbeddedRunTask(client: () => LanguageClient | undefined, definition: vscode.TaskDefinition,
                                      target: RunTarget): vscode.Task {
    const task = new vscode.Task(
        definition,
        vscode.TaskScope.Workspace,
        `Run ${target.label}`,
        'xtc',
        new vscode.CustomExecution(async () => new RunTerminal(client, target)),
        []
    );
    task.presentationOptions = { reveal: vscode.TaskRevealKind.Always, panel: vscode.TaskPanelKind.Dedicated, clear: true };
    return task;
}
