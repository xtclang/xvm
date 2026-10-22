import * as assert from 'node:assert';
import { execFile } from 'node:child_process';
import * as fs from 'node:fs/promises';
import * as os from 'node:os';
import * as path from 'node:path';
import { promisify } from 'node:util';
import * as vscode from 'vscode';
import { buildModeStatus } from '../../build-mode-selection';
import { buildMode, onDidUpdateExecution, type ExecutionUpdate } from '../../embedded-execution';
import { getClient } from '../../lsp-client';
import { createXtcRunTask } from '../../task-provider';
import { discovered } from './liveWorkspace';
import { client, diagnosticCode, eventually, noErrors, playbook, type Workspace } from './support';

/** Records what each embedded build or run terminal shows, keyed by kind and label. */
class Executions implements vscode.Disposable {
    private readonly latest = new Map<string, ExecutionUpdate>();
    private readonly subscription = onDidUpdateExecution(update => this.latest.set(`${update.kind}:${update.label}`, update));

    /** Starts an execution and waits until its terminal shows what [accept] expects. */
    async start(kind: ExecutionUpdate['kind'], label: string, action: () => Thenable<unknown>,
                accept: (update: ExecutionUpdate) => boolean, message: string): Promise<ExecutionUpdate> {
        this.latest.delete(`${kind}:${label}`);
        await action();
        return this.await(kind, label, accept, message);
    }

    async await(kind: ExecutionUpdate['kind'], label: string, accept: (update: ExecutionUpdate) => boolean,
                message: string): Promise<ExecutionUpdate> {
        const update = await eventually(async () => this.latest.get(`${kind}:${label}`), value => !!value && accept(value), message);
        return update!;
    }

    dispose(): void {
        this.subscription.dispose();
    }
}

const finished = (update: ExecutionUpdate) => update.exitCode !== undefined;

/** Runs [body] with the workspace in embedded build mode, whatever the folder's Gradle files say. */
async function embedded(body: (executions: Executions) => Promise<void>): Promise<void> {
    const settings = vscode.workspace.getConfiguration('xtc');
    const original = settings.inspect<string>('build.mode')?.workspaceValue;
    const executions = new Executions();
    await settings.update('build.mode', 'embedded', vscode.ConfigurationTarget.Workspace);
    try {
        await body(executions);
    } finally {
        executions.dispose();
        await settings.update('build.mode', original, vscode.ConfigurationTarget.Workspace);
    }
}

/** VS Code saves before a task unless told not to; the extension itself saves nothing. */
async function withoutSavingBeforeTasks(body: () => Promise<void>): Promise<void> {
    const settings = vscode.workspace.getConfiguration('task');
    const original = settings.inspect<string>('saveBeforeRun')?.workspaceValue;
    await settings.update('saveBeforeRun', 'never', vscode.ConfigurationTarget.Workspace);
    try {
        await body();
    } finally {
        await settings.update('saveBeforeRun', original, vscode.ConfigurationTarget.Workspace);
    }
}

async function runLensTitles(document: vscode.TextDocument): Promise<string[]> {
    const provider = client().getFeature('textDocument/codeLens').getProvider(document)!.provider!;
    const token = new vscode.CancellationTokenSource();
    try {
        const lenses = await Promise.all((await provider.provideCodeLenses(document, token.token) ?? [])
            .map(lens => lens.isResolved ? lens : provider.resolveCodeLens!(lens, token.token)));
        return lenses.filter(lens => lens?.command?.command === 'xtc.runModule').map(lens => lens!.command!.title);
    } finally {
        token.dispose();
    }
}

/** Server paths use the platform separator; the expected fragments use '/'. */
function portable(text: string): string {
    return text.replace(/\\/g, '/');
}

async function configureModules(workspace: Workspace, modules: { name: string; file: string; dependencies?: string[] }[]): Promise<void> {
    await workspace.configure(modules.map(module => ({ name: module.name, uri: workspace.uri(module.file).toString(), dependencies: module.dependencies })));
}

export function executionCases(): void {
    playbook('X280', async (workspace, data) => {
        await workspace.write(data.libraryFile, data.librarySource);
        const runnable = await workspace.open(data.file, data.source);
        const library = await workspace.open(data.libraryFile);
        await noErrors(runnable.uri);
        await noErrors(library.uri);
        await eventually(() => runLensTitles(runnable), titles => titles.includes(data.runTitle), 'Run lens on a module with run()');
        assert.deepStrictEqual(await runLensTitles(library), [], 'No Run lens on a module without run()');
        await workspace.replace(library, data.uncallable);
        await noErrors(library.uri);
        assert.deepStrictEqual(await runLensTitles(library), [], 'No Run lens when run() needs two arguments');
        await workspace.replace(library, data.runnableLibrary);
        await noErrors(library.uri);
        await eventually(() => runLensTitles(library), titles => titles.includes(data.libraryRunTitle), 'Run lens after adding run()');
    });

    playbook('X281', async (workspace, data) => {
        await workspace.write(data.libraryFile, data.librarySource);
        await workspace.write(data.appFile, data.appSource);
        await configureModules(workspace, [
            { name: data.libraryModule, file: data.libraryFile },
            { name: data.appModule, file: data.appFile, dependencies: [data.libraryModule] }
        ]);
        const library = await workspace.open(data.libraryFile);
        const app = await workspace.open(data.appFile);
        await noErrors(app.uri);
        await embedded(async executions => {
            const label = 'all Ecstasy modules';
            const built = await executions.start('build', label, () => vscode.commands.executeCommand('xtc.build'), finished, 'Embedded build finishes');
            assert.strictEqual(built.exitCode, 0, built.text);
            for (const line of data.built) assert.ok(built.text.includes(line), `${line} in ${built.text}`);
            assert.ok(portable(built.text).includes(data.artifact), built.text);

            await workspace.replace(library, data.brokenLibrary);
            assert.ok(library.isDirty, 'The broken library is unsaved before the build');
            // the build compiles the editor's text, and only VS Code's task setting would save it
            await withoutSavingBeforeTasks(async () => {
                const failed = await executions.start('build', label, () => vscode.commands.executeCommand('xtc.build'), finished, 'Failing build finishes');
                assert.ok(library.isDirty, 'Build leaves the broken library unsaved');
                assert.strictEqual(failed.exitCode, 1, failed.text);
                assert.ok(failed.text.includes(data.failed), failed.text);
                assert.ok(failed.text.includes(data.skipped), failed.text);
                assert.ok(failed.text.split('\n').some(line => line.includes(data.diagnostic) && line.includes(': error: ')), failed.text);
            });
        });
    });

    playbook('X282', async (workspace, data) => {
        const document = await workspace.open(data.file, data.source);
        await configureModules(workspace, [{ name: data.module, file: data.file }]);
        await noErrors(document.uri);
        await embedded(async executions => {
            const task = createXtcRunTask({ type: 'xtc', moduleName: data.module, moduleUri: document.uri.toString(), args: data.args, mode: 'embedded' }, getClient);
            const run = await executions.start('run', data.module, () => vscode.tasks.executeTask(task), finished, 'Embedded run finishes');
            assert.strictEqual(run.exitCode, data.exitCode, run.text);
            assert.ok(run.text.includes(data.output), run.text);
            assert.ok(run.text.includes(`Process finished with exit code ${data.exitCode}`), run.text);
        });
    });

    playbook('X283', async (workspace, data) => {
        const forever = await workspace.open(data.file, data.source);
        const next = await workspace.open(data.nextFile, data.nextSource);
        await configureModules(workspace, [{ name: data.module, file: data.file }, { name: data.nextModule, file: data.nextFile }]);
        await noErrors(forever.uri);
        await noErrors(next.uri);
        await embedded(async executions => {
            const task = createXtcRunTask({ type: 'xtc', moduleName: data.module, moduleUri: forever.uri.toString(), mode: 'embedded' }, getClient);
            const started = (update: ExecutionUpdate) => update.text.includes(data.started) && !finished(update);
            await executions.start('run', data.module, () => vscode.tasks.executeTask(task), started, 'Long-running program starts');
            // Tasks: Restart Running Task stops the program, waits for its exit, then runs it again
            await executions.start('run', data.module,
                () => vscode.commands.executeCommand('workbench.action.tasks.restartTask', `Run ${data.module}`), started, 'Rerun starts a new run');
            const execution = vscode.tasks.taskExecutions.find(item => item.task.name === `Run ${data.module}`);
            assert.ok(execution, 'The rerun task is active');
            execution.terminate();
            await executions.await('run', data.module, finished, 'Stopped program reports its exit');
            // the worker is free again: the Run lens command runs the next program
            const result = await executions.start('run', data.nextModule,
                () => vscode.commands.executeCommand('xtc.runModule', next.uri.toString(), data.nextModule), finished, 'Next program finishes');
            assert.strictEqual(result.exitCode, data.nextExitCode, result.text);
        });
    });

    playbook('X284', async (workspace, data) => {
        const document = await workspace.open(data.file, data.source);
        await configureModules(workspace, [{ name: data.module, file: data.file }]);
        await embedded(async executions => {
            const run = await executions.start('run', data.module,
                () => vscode.commands.executeCommand('xtc.runModule', document.uri.toString(), data.module), finished, 'Failed build ends the run');
            assert.strictEqual(run.exitCode, 1, run.text);
            assert.ok(run.text.includes(data.diagnostic), run.text);
            assert.ok(run.text.includes(data.message), run.text);
            assert.ok(!run.text.includes('Process finished'), 'Nothing ran');
        });
    });

    playbook('X285', async (workspace, data) => {
        const document = await workspace.open(data.file, data.source);
        await configureModules(workspace, [{ name: data.module, file: data.file }]);
        await noErrors(document.uri);
        await embedded(async () => {
            const sent: DapMessage[] = [];
            const tracker = trackDebugSessions(sent);
            let ended = false;
            const termination = vscode.debug.onDidTerminateDebugSession(session => {
                if (session.type === 'xtc') ended = true;
            });
            try {
                const started = await vscode.debug.startDebugging(vscode.workspace.getWorkspaceFolder(document.uri),
                    { type: 'xtc', request: 'launch', name: `Debug ${data.module}`, module: data.module });
                assert.strictEqual(started, true, 'The launch starts a debug session in the language server');
                await eventually(async () => ended, value => value, 'The debug session ends when the program exits');
                assert.strictEqual(dapEvents(sent, 'exited')[0]?.body?.exitCode, data.exitCode, JSON.stringify(sent));
                assert.strictEqual(dapEvents(sent, 'terminated').length, 1, JSON.stringify(sent));
                assert.ok(dapOutput(sent).includes(data.finished), dapOutput(sent));

                // a line typed in the Debug Console while the program runs is its console input
                sent.length = 0;
                ended = false;
                const ask = await workspace.open(data.inputFile, data.inputSource);
                await configureModules(workspace, [{ name: data.module, file: data.file }, { name: data.inputModule, file: data.inputFile }]);
                await noErrors(ask.uri);
                let session: vscode.DebugSession | undefined;
                const startedSession = vscode.debug.onDidStartDebugSession(item => { session = item; });
                try {
                    assert.ok(await vscode.debug.startDebugging(vscode.workspace.getWorkspaceFolder(ask.uri),
                        { type: 'xtc', request: 'launch', name: `Debug ${data.inputModule}`, module: data.inputModule }));
                    await eventually(async () => dapOutput(sent), text => text.includes(data.prompt), 'The program prompts in the Debug Console');
                    await session!.customRequest('evaluate', { expression: data.input, context: 'repl' });
                    await eventually(async () => ended, value => value, 'The program reads the line and ends');
                    assert.ok(dapOutput(sent).includes(data.greeting), dapOutput(sent));
                } finally {
                    startedSession.dispose();
                }
            } finally {
                termination.dispose();
                tracker.dispose();
            }
        });
    });
}

/** X304, registered after X303 in catalog order: a breakpoint in a debug session. */
export function debugSessionCases(): void {
    playbook('X304', async (workspace, data) => {
        const document = await workspace.open(data.file, data.source);
        await configureModules(workspace, [{ name: data.module, file: data.file }]);
        await noErrors(document.uri);
        await embedded(async () => {
            const sent: DapMessage[] = [];
            const tracker = trackDebugSessions(sent);
            let ended = false;
            const termination = vscode.debug.onDidTerminateDebugSession(session => {
                if (session.type === 'xtc') ended = true;
            });
            const breakpoint = new vscode.SourceBreakpoint(new vscode.Location(document.uri, new vscode.Position(data.breakpointLine - 1, 0)));
            vscode.debug.addBreakpoints([breakpoint]);
            try {
                assert.ok(await vscode.debug.startDebugging(vscode.workspace.getWorkspaceFolder(document.uri),
                    { type: 'xtc', request: 'launch', name: `Debug ${data.module}`, module: data.module }));
                await eventually(async () => dapEvents(sent, 'stopped').length, count => count > 0, 'The program stops at the breakpoint');
                const installed = dapEvents(sent, 'breakpoint').map(message => message.body?.breakpoint);
                assert.ok(installed.some(item => item?.verified && item.line === data.breakpointLine), JSON.stringify(installed));
                assert.strictEqual(dapEvents(sent, 'stopped')[0].body?.reason, 'breakpoint');

                // VS Code asks for the stopped fiber's stack itself, to show where the program stopped
                const frames = await eventually(
                    async () => sent.filter(message => message.type === 'response' && message.command === 'stackTrace')
                        .flatMap(message => message.body?.stackFrames ?? []),
                    values => values.length >= data.frames.length, 'VS Code shows the call stack');
                assert.deepStrictEqual(frames.slice(0, data.frames.length).map(frame => [frame.name, frame.line]), data.frames);
                assert.strictEqual(await fs.realpath(frames[0].source!.path!), await fs.realpath(document.uri.fsPath));
                assert.ok(dapOutput(sent).includes(data.before), dapOutput(sent));
                assert.ok(!dapOutput(sent).includes(data.after), 'Nothing after the breakpoint has run');

                await vscode.commands.executeCommand('workbench.action.debug.continue');
                await eventually(async () => ended, value => value, 'Continue runs the program to its exit');
                assert.strictEqual(dapEvents(sent, 'exited')[0]?.body?.exitCode, data.exitCode, JSON.stringify(sent));
                assert.ok(dapOutput(sent).includes(data.after), dapOutput(sent));
            } finally {
                vscode.debug.removeBreakpoints([breakpoint]);
                termination.dispose();
                tracker.dispose();
            }
        });
    });
}

/** Records every DAP message that Ecstasy debug adapters send VS Code. */
function trackDebugSessions(sent: DapMessage[]): vscode.Disposable {
    return vscode.debug.registerDebugAdapterTrackerFactory('xtc', {
        createDebugAdapterTracker: () => ({ onDidSendMessage: (message: DapMessage) => { sent.push(message); } }),
    });
}

function dapEvents(sent: DapMessage[], name: string): DapMessage[] {
    return sent.filter(message => message.type === 'event' && message.event === name);
}

function dapOutput(sent: DapMessage[]): string {
    return dapEvents(sent, 'output').map(message => message.body?.output ?? '').join('');
}

/** The parts of a Debug Adapter Protocol message that X285 and X304 check. */
interface DapMessage {
    type: string;
    event?: string;
    command?: string;
    body?: {
        exitCode?: number;
        output?: string;
        reason?: string;
        breakpoint?: { verified: boolean; line?: number };
        stackFrames?: { name: string; line: number; source?: { path?: string } }[];
    };
}

/** Console input for embedded runs; registered after the adapter chooser to keep catalog order. */
export function executionInputCases(): void {
    playbook('X287', async (workspace, data) => {
        const document = await workspace.open(data.file, data.source);
        await configureModules(workspace, [{ name: data.module, file: data.file }]);
        await noErrors(document.uri);
        await embedded(async executions => {
            const task = createXtcRunTask({ type: 'xtc', moduleName: data.module, moduleUri: document.uri.toString(), mode: 'embedded' }, getClient);
            const prompted = (update: ExecutionUpdate) => update.text.includes(data.prompt) && !finished(update);
            await executions.start('run', data.module, () => vscode.tasks.executeTask(task), prompted, 'Program prompts for input');
            // the newest terminal for this module is the run's task terminal
            const terminal = vscode.window.terminals.filter(item => item.name.includes(data.module)).pop();
            assert.ok(terminal, `A run terminal for ${data.module}`);
            // typed into the run's own terminal: the line and Enter, then Ctrl+D on the empty line
            terminal.sendText(data.input, true);
            await executions.await('run', data.module, update => update.text.includes(data.greeting), 'Program reads the typed line');
            terminal.sendText('\x04', false);
            const run = await executions.await('run', data.module, finished, 'Program ends after the end of input');
            assert.strictEqual(run.exitCode, data.exitCode, run.text);
            assert.ok(run.text.includes(data.ended), run.text);
        });
    });
}

/** Runs [action], which starts the shell task called [name], and returns that task process's exit code. */
async function shellTaskExit(name: string, action: () => Thenable<unknown>, timeoutMs?: number): Promise<number | undefined> {
    let ended = false;
    let exitCode: number | undefined;
    const listener = vscode.tasks.onDidEndTaskProcess(event => {
        if (event.execution.task.name === name) {
            ended = true;
            exitCode = event.exitCode;
        }
    });
    try {
        await action();
        await eventually(async () => ended, value => value, `${name} task ends`, timeoutMs);
        return exitCode;
    } finally {
        listener.dispose();
    }
}

/** Build modes beside a Gradle build and repeated runs; registered after console input to keep catalog order. */
export function buildModeCases(): void {
    playbook('X288', async (workspace, data) => {
        const folder = vscode.workspace.workspaceFolders?.[0];
        assert.ok(folder, 'The playbook opens a folder');
        // a Gradle build at the folder root, with a wrapper that records what it is asked to do
        const root = (file: string) => path.join(folder.uri.fsPath, file);
        const rootFiles = [data.gradleFile, data.wrapper, data.windowsWrapper, data.invocations].map(root);
        const invocations = async () => (await fs.readFile(root(data.invocations), 'utf8').catch(() => '')).split('\n').filter(line => line);
        const settings = vscode.workspace.getConfiguration('xtc');
        const original = settings.inspect<string>('build.mode')?.workspaceValue;
        const executions = new Executions();
        try {
            await fs.writeFile(root(data.gradleFile), data.gradleSource);
            await fs.writeFile(root(data.wrapper), data.wrapperSource, { mode: 0o755 });
            await fs.writeFile(root(data.windowsWrapper), data.windowsWrapperSource);
            await settings.update('build.mode', undefined, vscode.ConfigurationTarget.Workspace);
            const document = await workspace.open(data.file, data.source);
            await configureModules(workspace, [{ name: data.module, file: data.file }]);
            await noErrors(document.uri);

            // the default is embedded, also beside a Gradle build, and says the build's settings do not apply
            assert.strictEqual(buildMode(folder), 'embedded');
            await eventually(async () => buildModeStatus(), text => text === data.statusEmbedded, 'Status bar shows the embedded build');
            const built = await executions.start('build', 'all Ecstasy modules', () => vscode.commands.executeCommand('xtc.build'), finished, 'Embedded build finishes');
            assert.strictEqual(built.exitCode, 0, built.text);
            assert.ok(built.text.includes(data.notice), built.text);
            const run = await executions.start('run', data.module,
                () => vscode.commands.executeCommand('xtc.runModule', document.uri.toString(), data.module), finished, 'Embedded run finishes');
            assert.strictEqual(run.exitCode, 0, run.text);
            assert.ok(run.text.includes(data.output), run.text);
            assert.deepStrictEqual(await invocations(), [], 'Embedded mode does not call Gradle');

            // Gradle, chosen the way the command and the status bar choose it
            await vscode.commands.executeCommand('xtc.selectBuildMode', 'gradle', folder.uri);
            await eventually(async () => buildModeStatus(), text => text === data.statusGradle, 'Status bar shows the Gradle build');
            assert.strictEqual(await shellTaskExit('Build', () => vscode.commands.executeCommand('xtc.build')), 0, 'Gradle build exit code');
            assert.deepStrictEqual(await invocations(), [data.gradleBuild], 'Build calls the Gradle wrapper');
            assert.strictEqual(await shellTaskExit(`Run ${data.module}`,
                () => vscode.commands.executeCommand('xtc.runModule', document.uri.toString(), data.module)), 0, 'Gradle run exit code');
            const calls = await invocations();
            assert.strictEqual(calls.length, 2, calls.join('\n'));
            assert.ok(calls[1].includes(data.gradleRun), calls.join('\n'));

            // and back: Run is embedded again
            await vscode.commands.executeCommand('xtc.selectBuildMode', 'embedded', folder.uri);
            await eventually(async () => buildModeStatus(), text => text === data.statusEmbedded, 'Status bar shows the embedded build again');
            const again = await executions.start('run', data.module,
                () => vscode.commands.executeCommand('xtc.runModule', document.uri.toString(), data.module), finished, 'Embedded run after switching back finishes');
            assert.strictEqual(again.exitCode, 0, again.text);
            assert.ok(again.text.includes(data.output), again.text);
            assert.strictEqual((await invocations()).length, 2, 'No Gradle call after switching back');
        } finally {
            executions.dispose();
            await settings.update('build.mode', original, vscode.ConfigurationTarget.Workspace);
            await Promise.all(rootFiles.map(file => fs.rm(file, { force: true })));
        }
    });

    playbook('X289', async (workspace, data) => {
        const document = await workspace.open(data.file, data.source);
        await configureModules(workspace, [{ name: data.module, file: data.file }]);
        await noErrors(document.uri);
        await embedded(async executions => {
            const runOnce = async (message: string) => {
                const task = createXtcRunTask({ type: 'xtc', moduleName: data.module, moduleUri: document.uri.toString(), mode: 'embedded' }, getClient);
                const run = await executions.start('run', data.module, () => vscode.tasks.executeTask(task), finished, message);
                assert.strictEqual(run.exitCode, data.exitCode, run.text);
                return run.text;
            };
            assert.ok((await runOnce('First run finishes')).includes(data.first));
            assert.ok((await runOnce('Second run of the unchanged module finishes')).includes(data.first));
            // edited and not saved: the run builds and runs the editor's text, not the earlier build, and
            // leaves the file unsaved when VS Code's task setting does not save it
            await workspace.replace(document, data.edited);
            assert.ok(document.isDirty, 'The edit is unsaved before the run');
            await withoutSavingBeforeTasks(async () => {
                const edited = await runOnce('Run of the edited module finishes');
                assert.ok(edited.includes(data.second), edited);
                assert.ok(!edited.includes(data.first), edited);
                assert.ok(document.isDirty, 'The run leaves the edit unsaved');
            });
        });
    });
}

/** A real Gradle build starts its own daemon, so its tasks get longer than the usual wait. */
const GRADLE_WAIT_MS = 120_000;

const exists = (file: string) => fs.access(file).then(() => true, () => false);

/** X303, registered after X302 in catalog order: a project made by xtc init, which Ecstasy: Create Project runs. */
export function gradleProjectCases(): void {
    playbook('X303', async (_workspace, data) => {
        const folder = vscode.workspace.workspaceFolders?.[0];
        assert.ok(folder, 'The playbook opens a folder');
        // Gradle mode runs ./gradlew at the folder root, so the project goes there; nothing of it may be there yet,
        // apart from the build directory, which embedded builds share
        const root = (file: string) => path.join(folder.uri.fsPath, file);
        for (const entry of data.rootEntries) assert.ok(!await exists(root(entry)), `X303 needs a folder root without ${entry}`);
        const xtc = path.resolve(__dirname, '../../../../../xdk/build/install/xdk/bin', process.platform === 'win32' ? 'xtc.bat' : 'xtc');
        assert.ok(await exists(xtc), `X303 makes its project with ${xtc} init; run ./gradlew xdk:installDist first`);
        const settings = vscode.workspace.getConfiguration('xtc');
        const original = settings.inspect<string>('build.mode')?.workspaceValue;
        const generated = await fs.mkdtemp(path.join(os.tmpdir(), 'xtc-init-'));
        try {
            await promisify(execFile)(xtc, ['init', data.project, '--type', 'application', '--dir', generated],
                { shell: process.platform === 'win32' });
            await fs.cp(path.join(generated, data.project), folder.uri.fsPath, { recursive: true });
            await fs.chmod(root('gradlew'), 0o755);
            const version = new RegExp(data.versionPattern, 'm').exec(await fs.readFile(root(data.catalog), 'utf8'))?.[1];
            assert.ok(version, `An XTC version in ${data.catalog}`);
            for (const artifact of data.mavenLocal) {
                const published = path.join(os.homedir(), '.m2', 'repository', artifact, version);
                assert.ok(await exists(published), `X303 builds with ${artifact} ${version} from mavenLocal; run ./gradlew publishLocal first`);
            }

            await vscode.commands.executeCommand('xtc.selectBuildMode', 'gradle', folder.uri);
            await eventually(async () => buildModeStatus(), text => text === data.statusGradle, 'Status bar shows the Gradle build');
            const build = () => shellTaskExit('Build', () => vscode.commands.executeCommand('xtc.build'), GRADLE_WAIT_MS);
            assert.strictEqual(await build(), 0, 'Gradle build exit code');
            for (const artifact of data.artifacts) assert.ok(await exists(root(artifact)), `The Gradle build writes ${artifact}`);
            const module = vscode.Uri.file(root(data.moduleFile));
            assert.strictEqual(await shellTaskExit(`Run ${data.module}`,
                () => vscode.commands.executeCommand('xtc.runModule', module.toString(), data.module), GRADLE_WAIT_MS), 0, 'Gradle run exit code');

            // the build compiles the saved module: a type error fails it, and the Build task's problem matcher shows
            // it in Problems (the file is never opened, so the language server reports nothing for it)
            assert.deepStrictEqual(vscode.languages.getDiagnostics(module), [], 'No problems before the broken build');
            await fs.writeFile(module.fsPath, data.broken);
            assert.notStrictEqual(await build(), 0, 'Broken Gradle build exit code');
            await eventually(async () => vscode.languages.getDiagnostics(module),
                values => values.some(item => diagnosticCode(item) === data.brokenCode && item.range.start.line === data.brokenLine),
                'The build error in Problems');
        } finally {
            await settings.update('build.mode', original, vscode.ConfigurationTarget.Workspace);
            await Promise.all(data.rootEntries.map(entry => fs.rm(root(entry), { recursive: true, force: true })));
            // only when nothing else, such as embedded build output, is left in them
            for (const directory of data.sharedDirectories) await fs.rmdir(root(directory)).catch(() => undefined);
            await fs.rm(generated, { recursive: true, force: true });
        }
    }, 360_000);
}

/** X291 is registered after the on-type cases and X301 after X300, in catalog order. */
export function renamedRootCases(ids: readonly ('X291' | 'X301')[]): void {
    for (const id of ids) playbook(id, async (workspace, data) => {
        await workspace.write(data.file, data.source);
        await discovered(workspace, async () => {
            // X301 leaves an editor open on the root, which keeps its document open after the file is renamed
            if ('editorOpen' in data && data.editorOpen) await workspace.open(data.file);
            await embedded(async executions => {
                // a task saved with the module's root file, as tasks.json or Rerun keeps it
                const runSaved = async (message: string) => {
                    const task = createXtcRunTask({ type: 'xtc', moduleName: data.module, moduleUri: workspace.uri(data.file).toString(), mode: 'embedded' }, getClient);
                    const run = await executions.start('run', data.module, () => vscode.tasks.executeTask(task), finished, message);
                    assert.strictEqual(run.exitCode, data.exitCode, run.text);
                    assert.ok(run.text.includes(data.output), run.text);
                };
                await runSaved('Run before the rename finishes');
                // renamed on disk, as by a terminal
                await fs.rename(workspace.uri(data.file).fsPath, workspace.uri(data.destinationFile).fsPath);
                const renamed = await vscode.workspace.openTextDocument(workspace.uri(data.destinationFile));
                await vscode.window.showTextDocument(renamed);
                await eventually(() => runLensTitles(renamed), titles => titles.includes(data.runTitle), 'Run lens on the renamed root');
                await runSaved('Run of the saved task after the rename finishes');
            });
        });
    });
}

/** Registered after the renamed-root case, as the catalog orders X292–X294 after X291. */
export function runEntryCases(): void {
    playbook('X292', async (workspace, data) => {
        const document = await workspace.open(data.file, data.source);
        await configureModules(workspace, [{ name: data.module, file: data.file }]);
        await noErrors(document.uri);
        await embedded(async executions => {
            const runOnce = async (message: string) => {
                const task = createXtcRunTask({ type: 'xtc', moduleName: data.module, moduleUri: document.uri.toString(), mode: 'embedded' }, getClient);
                const run = await executions.start('run', data.module, () => vscode.tasks.executeTask(task), finished, message);
                assert.strictEqual(run.exitCode, data.exitCode, run.text);
                // the counter keeps its count within a run, and a run that went on from the run before it would
                // print the counts after that run's
                assert.ok(run.text.includes(data.output), run.text);
                assert.ok(!run.text.includes(data.carriedOver), run.text);
            };
            await runOnce('First run finishes');
            await runOnce('Second run finishes');
        });
    });

    playbook('X293', async (workspace, data) => {
        const library = await workspace.open(data.file, data.source);
        const twoArguments = await workspace.open(data.twoArgumentsFile, data.twoArgumentsSource);
        await configureModules(workspace, [
            { name: data.module, file: data.file },
            { name: data.twoArgumentsModule, file: data.twoArgumentsFile },
        ]);
        await noErrors(library.uri);
        await noErrors(twoArguments.uri);
        await embedded(async executions => {
            // neither module has a Run lens, so each runs from a task, as one saved in tasks.json
            const refused = async (moduleName: string, uri: vscode.Uri, message: string, expected: string) => {
                const task = createXtcRunTask({ type: 'xtc', moduleName, moduleUri: uri.toString(), mode: 'embedded' }, getClient);
                const run = await executions.start('run', moduleName, () => vscode.tasks.executeTask(task), finished, message);
                assert.strictEqual(run.exitCode, data.exitCode, run.text);
                assert.ok(run.text.includes(expected), run.text);
                // the reason alone, as xtc run reports it, not a failure to create the program's container
                assert.ok(!run.text.includes(data.containerFailure), run.text);
                return run.text;
            };
            await refused(data.module, library.uri, 'Run of a module without run is refused', data.message);
            const text = await refused(data.twoArgumentsModule, twoArguments.uri, 'Run of a run method requiring two arguments is refused',
                data.twoArgumentsMessage);
            assert.ok(!text.includes(data.twoArgumentsOutput), `Nothing ran: ${text}`);
        });
    });

    playbook('X294', async (workspace, data) => {
        const document = await workspace.open(data.file, data.source);
        await configureModules(workspace, [{ name: data.module, file: data.file }]);
        await noErrors(document.uri);
        await embedded(async executions => {
            const task = createXtcRunTask({ type: 'xtc', moduleName: data.module, moduleUri: document.uri.toString(), args: data.args, mode: 'embedded' }, getClient);
            const run = await executions.start('run', data.module, () => vscode.tasks.executeTask(task), finished, 'Run with dropped arguments finishes');
            assert.strictEqual(run.exitCode, data.exitCode, run.text);
            assert.ok(run.text.includes(data.warning), run.text);
            assert.ok(run.text.includes(data.output), run.text);
            assert.ok(run.text.indexOf(data.warning) < run.text.indexOf(data.output), `The warning comes before the program's output: ${run.text}`);
        });
    });
}
