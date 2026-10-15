import * as fs from 'node:fs';
import * as path from 'node:path';
import { randomUUID } from 'node:crypto';
import * as vscode from 'vscode';
import type { LanguageClient } from 'vscode-languageclient/node';

/**
 * Embedded build and run: the language server's compiler builds into the workspace's embedded output
 * directory, and its execution worker runs the result. This is the counterpart of building and running
 * with Gradle, chosen per folder by `xtc.build.mode`.
 */

export type BuildMode = 'embedded' | 'gradle';

const GRADLE_BUILD_FILES = ['build.gradle.kts', 'build.gradle', 'settings.gradle.kts', 'settings.gradle'];

/** The effective mode for a folder: the setting, or with `auto`, Gradle exactly when the folder has a Gradle build. */
export function buildMode(folder: vscode.WorkspaceFolder | undefined): BuildMode {
    const setting = vscode.workspace.getConfiguration('xtc', folder).get<string>('build.mode', 'auto');
    if (setting === 'embedded' || setting === 'gradle') {
        return setting;
    }
    return folder && GRADLE_BUILD_FILES.some(name => fs.existsSync(path.join(folder.uri.fsPath, name)))
        ? 'gradle'
        : 'embedded';
}

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

    constructor(protected readonly client: () => LanguageClient | undefined) {}

    abstract open(): void;

    close(): void {
        this.finish(1);
    }

    protected write(text: string): void {
        this.writer.fire(terminalText(text));
    }

    protected line(text: string): void {
        this.write(`${text}\n`);
    }

    protected finish(code: number): void {
        if (!this.finished) {
            this.finished = true;
            this.closer.fire(code);
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
                private readonly rebuild: boolean) {
        super(client);
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
}

class RunTerminal extends ExecutionTerminal implements RunSession {
    private readonly runId = randomUUID();
    private running = false;

    constructor(client: () => LanguageClient | undefined, private readonly target: RunTarget) {
        super(client);
    }

    open(): void {
        void this.run();
    }

    /** Closing or stopping the task stops the program; the server still reports its exit. */
    override close(): void {
        if (this.running) {
            void this.client()?.sendRequest('xtc/stopRun', { runId: this.runId }).catch(() => undefined);
        }
        sessions.delete(this.runId);
        super.close();
    }

    output(text: string): void {
        this.write(text);
    }

    exited(exitCode: number | undefined, error: string | undefined): void {
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
        new vscode.CustomExecution(async () => new BuildTerminal(client, module, rebuild)),
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
