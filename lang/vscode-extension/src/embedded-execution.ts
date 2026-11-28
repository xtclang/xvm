import * as fs from 'node:fs';
import * as path from 'node:path';
import { randomUUID } from 'node:crypto';
import * as vscode from 'vscode';
import type { LanguageClient } from 'vscode-languageclient/node';

import { ConsoleLineEditor } from './console-input';
import { isTestEvent, type TestEvent } from './test-events';

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
 * What the language server says of the workspace folders that build and run with Gradle whatever their mode, as the
 * server builds and runs only with the bundled XDK: the bundled libraries that the workspace's own modules replace, as
 * the XVM repository's replace them all; the folders, as file URIs, none meaning every folder where libraries are
 * replaced; and, by folder URI, why, as the server says it after the folder's name, such as `builds with XDK 0.5.0, not
 * the bundled XDK 0.4.4`: the folder builds with another XDK than the bundled one, or replaces bundled libraries with
 * its own modules. Neither reaches the other folders of the window. Each server says it again.
 */
export interface ReplacedLibraries {
    readonly libraries: readonly string[];
    readonly folders: readonly string[];
    readonly reasons: Readonly<Record<string, string>>;
}

let replaced: ReplacedLibraries = { libraries: [], folders: [], reasons: {} };
/**
 * What the notice told the user builds and runs with Gradle ([gradleNotice]): each folder once a session while it is in
 * the window, so that one that comes into the window again is told of again ([watchNoticedFolders]), as is one whose
 * reason comes after another folder's, and the workspace once a session.
 */
const told = new Set<string>();
const replacedChanges = new vscode.EventEmitter<ReplacedLibraries>();

/** Fires when the folders that build and run with Gradle whatever their mode, or why, change. */
export const onDidChangeReplacedLibraries = replacedChanges.event;

/** Why [folder] builds and runs with Gradle whatever its mode, as the server says it after its name; undefined when it does not. */
export function gradleReason(folder: vscode.WorkspaceFolder | undefined): string | undefined {
    return gradleReasonIn(folder?.uri.fsPath, replaced);
}

/**
 * Why, by [scope], the folder at [folder], a file system path, builds and runs with Gradle: for every folder when
 * [scope] names none, that the workspace's own modules replace its libraries, if any; else, for one of its folders, the
 * reason it gives that folder, or else that, and nothing for another folder. Without a folder, as for a launch
 * configuration of a multi-root workspace's own, the reason of the first folder it names: with one folder, that
 * folder's, and with several, Gradle as soon as one of them builds with it. A folder it names always has a reason.
 */
export function gradleReasonIn(folder: string | undefined, scope: ReplacedLibraries): string | undefined {
    const libraries = scope.libraries.length === 0 ? undefined
        : `replaces the bundled libraries ${scope.libraries.join(', ')} with its own modules`;
    if (scope.folders.length === 0) return libraries;
    const own = folder === undefined ? undefined : realPath(folder);
    const named = scope.folders.find(uri => own === undefined || realPath(vscode.Uri.parse(uri).fsPath) === own);
    return named === undefined ? undefined : scope.reasons[named] ?? libraries ?? 'is not built or run by the language server';
}

/** The real path of [file], without a trailing separator: the server's folders are canonical, as /private/tmp for macOS's /tmp. */
export function realPath(file: string): string {
    const trimmed = file.replace(/[\\/]+$/, '');
    try {
        return fs.realpathSync.native(trimmed);
    } catch {
        return trimmed;
    }
}

/** A stopped language server builds no folder with Gradle any more; the next server says again which it does. */
export function forgetReplacedLibraries(): void {
    replace({ libraries: [], folders: [], reasons: {} });
}

function replace(scope: ReplacedLibraries): void {
    replaced = scope;
    replacedChanges.fire(scope);
    const notice = gradleNotice(scope, told, new Set((vscode.workspace.workspaceFolders ?? []).map(folder => realPath(folder.uri.fsPath))));
    if (notice !== undefined) {
        void vscode.window.showInformationMessage(notice);
    }
}

/**
 * The notice of what [scope] says builds and runs with Gradle, as far as [told] has not had it: [told] takes the folders
 * it tells of, by their real paths, and `''` for the workspace, whose reason the server gives for it as a whole, and
 * forgets each folder that is not of [open], the real paths of the window's folders, any more, as one whose directory
 * went before it left the window ([forgetFolders]). None when it has had all.
 */
export function gradleNotice(scope: ReplacedLibraries, told: Set<string>, open: ReadonlySet<string>): string | undefined {
    for (const whom of told) if (whom !== '' && !open.has(whom)) told.delete(whom);
    const reasons: [whom: string, text: string][] = scope.folders.length === 0
        ? [gradleReasonIn(undefined, scope)].filter(reason => reason !== undefined).map(reason => ['', `This workspace ${reason}`])
        : scope.folders.map(uri => {
            const folder = vscode.Uri.parse(uri).fsPath;
            return [realPath(folder), `The folder ${path.basename(folder)} ${gradleReasonIn(folder, scope) ?? ''}`];
        });
    const untold = reasons.filter(([whom]) => !told.has(whom));
    untold.forEach(([whom]) => told.add(whom));
    return untold.length === 0 ? undefined : `${untold.map(([, text]) => text).join('; ')}, and the language server builds and runs `
        + 'only with the bundled XDK, so Ecstasy builds, runs and debug sessions there use Gradle.';
}

/** Forgets, in [told], the [folders] that leave the window, so that the notice tells of each again when it comes back. */
export function forgetFolders(told: Set<string>, folders: readonly vscode.WorkspaceFolder[]): void {
    folders.forEach(folder => told.delete(realPath(folder.uri.fsPath)));
}

/**
 * Forgets the notice's folders as they leave the window ([forgetFolders]), whether a server says anything then or not,
 * as while it is stopped, or before its notice for the window's new folders comes.
 */
export function watchNoticedFolders(): vscode.Disposable {
    return vscode.workspace.onDidChangeWorkspaceFolders(event => forgetFolders(told, event.removed));
}

/**
 * The effective mode for a folder: Gradle while the server says it builds and runs with Gradle ([gradleReason]), as it
 * builds with another XDK than the bundled one or its modules are built against libraries that the workspace's own
 * modules replace; else the setting, embedded by default even with a Gradle build, or with `auto`, Gradle exactly when
 * the folder has a Gradle build.
 */
export function buildMode(folder: vscode.WorkspaceFolder | undefined): BuildMode {
    if (gradleReason(folder) !== undefined) {
        return 'gradle';
    }
    const setting = vscode.workspace.getConfiguration('xtc', folder).get<string>('build.mode', 'embedded');
    if (setting === 'embedded' || setting === 'gradle') {
        return setting;
    }
    return hasGradleBuild(folder) ? 'gradle' : 'embedded';
}

/**
 * Shown by embedded builds and runs in a folder with a Gradle build, of whose own settings only the run settings that
 * the imported Gradle model carries apply; the run's output says which it took.
 */
export const GRADLE_NOT_USED =
    'This folder has a Gradle build, but embedded mode builds and runs with the language server\'s compiler and XDK, ' +
    'not the build\'s. Of the build\'s settings, only xtcRun\'s method, arguments and JVM options and xtcTest\'s JVM ' +
    'options apply, once its Gradle model is imported. ' +
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

export interface RunResponse {
    runId?: string;
    build: BuildResponse;
}

/** Where a run's events go; the server reports by the run ID the client chose. */
export interface RunSession {
    output(text: string): void;
    /** An event of the tests a run with `tests` runs, which the server sends apart from the output. */
    tested?(event: TestEvent): void;
    exited(exitCode: number | undefined, error: string | undefined): void;
}

const sessions = new Map<string, RunSession>();

/**
 * Sends the output, the test events and the exit that the server reports for [runId] to [session] until disposed, as a
 * run that is not a task's has them, such as a test run's.
 */
export function trackRun(runId: string, session: RunSession): vscode.Disposable {
    sessions.set(runId, session);
    return new vscode.Disposable(() => sessions.delete(runId));
}

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
    const tested = client.onNotification('xtc/testEvent', (params: { runId: string; event: unknown }) => {
        if (isTestEvent(params.event)) {
            sessions.get(params.runId)?.tested?.(params.event);
        }
    });
    const exited = client.onNotification(
        'xtc/runExited',
        (params: { runId: string; exitCode?: number | null; error?: string | null }) =>
            sessions.get(params.runId)?.exited(params.exitCode ?? undefined, params.error ?? undefined));
    const libraries = client.onNotification('xtc/replacedLibraries',
        (params: { libraries?: string[]; folders?: string[]; reasons?: Record<string, string> }) =>
            replace({ libraries: params.libraries ?? [], folders: params.folders ?? [], reasons: params.reasons ?? {} }));
    return vscode.Disposable.from(output, tested, exited, libraries);
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
export function describeBuild(build: BuildResponse): string {
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

    /**
     * The server builds the editor's text, saved or not, so the extension saves nothing; VS Code saves before a
     * task as `task.saveBeforeRun` says. The language client sends every edit before a later request.
     */
    protected async connection(): Promise<LanguageClient | undefined> {
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
            // without a module, the folder's modules: a window's other folders may build only with Gradle
            const folder = this.module === undefined ? this.folder?.uri.toString() : undefined;
            const build = await client.sendRequest<BuildResponse>(
                'xtc/build', { module: this.module, rebuild: this.rebuild, folder }, this.cancellation.token);
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
    /** The module's name, which the server uses when [module] is a root URI that no longer names a module. */
    moduleName?: string;
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
    /** Whether the task was stopped, whose program's end then says so, not how the program exited. */
    private stopping = false;

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
        this.stopping = true;
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
        // a stopped program did not finish: the worker reports its exit code as 143
        this.line(this.stopping ? '\nProcess stopped' : `\nProcess finished with exit code ${exitCode ?? 1}`);
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
                moduleName: this.target.moduleName,
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
