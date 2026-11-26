import * as assert from 'node:assert';
import { execFile, spawn } from 'node:child_process';
import * as fs from './disk';
import * as os from 'node:os';
import * as path from 'node:path';
import { promisify } from 'node:util';
import * as vscode from 'vscode';
import { buildModeStatus } from '../../build-mode-selection';
import { buildMode, onDidUpdateExecution, type ExecutionUpdate } from '../../embedded-execution';
import { gradleProjects } from '../../gradle-projects';
import { getClient, updateCompilerConfiguration } from '../../lsp-client';
import { createXtcRunTask } from '../../task-provider';
import { onDidRefreshTests, onDidReportTest, onDidStartTest, type TestReport } from '../../test-explorer';
import { discovered } from './liveWorkspace';
import { gradleModel, withGradleModel } from './platform';
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

    /** Builds all Ecstasy modules, as Ecstasy: Build does, and waits until the build finishes. */
    build(message: string): Promise<ExecutionUpdate> {
        return this.start('build', 'all Ecstasy modules', () => vscode.commands.executeCommand('xtc.build'), finished, message);
    }

    /** Runs [module] from a task, as one saved in tasks.json runs it, and waits until its terminal shows what [until] expects. */
    task(module: string, uri: vscode.Uri, message: string,
         { args, until = finished }: { args?: string[]; until?: (update: ExecutionUpdate) => boolean } = {}): Promise<ExecutionUpdate> {
        const task = createXtcRunTask({ type: 'xtc', moduleName: module, moduleUri: uri.toString(), args, mode: 'embedded' }, getClient);
        return this.start('run', module, () => vscode.tasks.executeTask(task), until, message);
    }

    /** Runs [module] from a task, checks that it exits with [exitCode], and returns what its terminal shows. */
    async exits(module: string, uri: vscode.Uri, exitCode: number, message: string, args?: string[]): Promise<string> {
        const run = await this.task(module, uri, message, { args });
        assert.strictEqual(run.exitCode, exitCode, run.text);
        return run.text;
    }

    /** Runs [module] as its Run lens does, and waits until the run finishes. */
    lens(module: string, uri: vscode.Uri, message: string): Promise<ExecutionUpdate> {
        return this.start('run', module, () => vscode.commands.executeCommand('xtc.runModule', uri.toString(), module), finished, message);
    }

    /** Stops the run task of [module], as its terminal's Stop does, and waits until the terminal shows its exit. */
    async stop(module: string, message: string): Promise<ExecutionUpdate> {
        const execution = vscode.tasks.taskExecutions.find(item => item.task.name === `Run ${module}`);
        assert.ok(execution, `The run of ${module} is an active task`);
        execution.terminate();
        return this.await('run', module, finished, message);
    }

    dispose(): void {
        this.subscription.dispose();
    }
}

const finished = (update: ExecutionUpdate) => update.exitCode !== undefined;

/** Runs [body] with [Executions], which stops recording after it. */
async function executing(body: (executions: Executions) => Promise<void>): Promise<void> {
    const executions = new Executions();
    try {
        await body(executions);
    } finally {
        executions.dispose();
    }
}

/** Runs [body] with the workspace in embedded build mode, whatever the folder's Gradle files say. */
async function embedded(body: (executions: Executions) => Promise<void>): Promise<void> {
    const settings = vscode.workspace.getConfiguration('xtc');
    const original = settings.inspect<string>('build.mode')?.workspaceValue;
    await settings.update('build.mode', 'embedded', vscode.ConfigurationTarget.Workspace);
    try {
        await executing(body);
    } finally {
        await settings.update('build.mode', original, vscode.ConfigurationTarget.Workspace);
    }
}

/** The Gradle build that X288 and X354 write at the folder root, with a wrapper that records what it is asked to do. */
interface GradleWrapperValues {
    gradleFile: string;
    gradleSource: string;
    wrapper: string;
    wrapperSource: string;
    windowsWrapper: string;
    windowsWrapperSource: string;
    invocations: string;
}

/**
 * Writes [data]'s Gradle build at the folder root, runs [body] with the folder and the wrapper's calls so far, and removes
 * the build again, with the folder's build mode as it was.
 */
async function withGradleWrapper(data: GradleWrapperValues,
    body: (folder: vscode.WorkspaceFolder, invocations: () => Promise<string[]>) => Promise<void>): Promise<void> {
    const folder = vscode.workspace.workspaceFolders?.[0];
    assert.ok(folder, 'The playbook opens a folder');
    const root = (file: string) => path.join(folder.uri.fsPath, file);
    const invocations = async () => (await fs.readFile(root(data.invocations), 'utf8').catch(() => '')).split('\n').filter(line => line);
    const settings = vscode.workspace.getConfiguration('xtc');
    const original = settings.inspect<string>('build.mode')?.workspaceValue;
    try {
        await fs.writeFile(root(data.gradleFile), data.gradleSource);
        await fs.writeFile(root(data.wrapper), data.wrapperSource, { mode: 0o755 });
        await fs.writeFile(root(data.windowsWrapper), data.windowsWrapperSource);
        await body(folder, invocations);
    } finally {
        await settings.update('build.mode', original, vscode.ConfigurationTarget.Workspace);
        await Promise.all([data.gradleFile, data.wrapper, data.windowsWrapper, data.invocations].map(file => fs.rm(root(file), { force: true })));
    }
}

/** Chooses the folder's build mode, as the command and the status bar choose it, and waits until the status bar shows [status]. */
async function selectBuildMode(folder: vscode.WorkspaceFolder, mode: 'auto' | 'embedded' | 'gradle', status: string, message: string): Promise<void> {
    await vscode.commands.executeCommand('xtc.selectBuildMode', mode, folder.uri);
    await eventually(async () => buildModeStatus(), text => text === status, message);
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

export async function configureModules(workspace: Workspace, modules: { name: string; file: string; dependencies?: string[] }[]): Promise<void> {
    await workspace.configure(modules.map(module => ({ name: module.name, uri: workspace.uri(module.file).toString(), dependencies: module.dependencies })));
}

/** A case's one module: its file, its source and its name. */
interface ModuleValues {
    file: string;
    source: string;
    module: string;
}

/** Moves the cursor of [editor] to the column [column] of the one-based line [line]. */
function select(editor: vscode.TextEditor, line: number, column = 0): void {
    const position = new vscode.Position(line - 1, column);
    editor.selection = new vscode.Selection(position, position);
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
            const built = await executions.build('Embedded build finishes');
            assert.strictEqual(built.exitCode, 0, built.text);
            for (const line of data.built) assert.ok(built.text.includes(line), `${line} in ${built.text}`);
            assert.ok(portable(built.text).includes(data.artifact), built.text);

            await workspace.replace(library, data.brokenLibrary);
            assert.ok(library.isDirty, 'The broken library is unsaved before the build');
            // the build compiles the editor's text, and only VS Code's task setting would save it
            await withoutSavingBeforeTasks(async () => {
                const failed = await executions.build('Failing build finishes');
                assert.ok(library.isDirty, 'Build leaves the broken library unsaved');
                assert.strictEqual(failed.exitCode, 1, failed.text);
                assert.ok(failed.text.includes(data.failed), failed.text);
                assert.ok(failed.text.includes(data.skipped), failed.text);
                assert.ok(failed.text.split('\n').some(line => line.includes(data.diagnostic) && line.includes(': error: ')), failed.text);
            });
        });
    });

    playbook('X282', async (workspace, data) => {
        const document = await workspace.module(data.file, data.source, data.module);
        await embedded(async executions => {
            const text = await executions.exits(data.module, document.uri, data.exitCode, 'Embedded run finishes', data.args);
            assert.ok(text.includes(data.output), text);
            assert.ok(text.includes(`Process finished with exit code ${data.exitCode}`), text);
        });
    });

    playbook('X283', async (workspace, data) => {
        const forever = await workspace.open(data.file, data.source);
        const next = await workspace.open(data.nextFile, data.nextSource);
        await configureModules(workspace, [{ name: data.module, file: data.file }, { name: data.nextModule, file: data.nextFile }]);
        await noErrors(forever.uri);
        await noErrors(next.uri);
        await embedded(async executions => {
            const started = (update: ExecutionUpdate) => update.text.includes(data.started) && !finished(update);
            await executions.task(data.module, forever.uri, 'Long-running program starts', { until: started });
            // Tasks: Restart Running Task stops the program, waits for its exit, then runs it again
            await executions.start('run', data.module,
                () => vscode.commands.executeCommand('workbench.action.tasks.restartTask', `Run ${data.module}`), started, 'Rerun starts a new run');
            await executions.stop(data.module, 'Stopped program reports its exit');
            // the worker is free again: the Run lens command runs the next program
            const result = await executions.lens(data.nextModule, next.uri, 'Next program finishes');
            assert.strictEqual(result.exitCode, data.nextExitCode, result.text);
        });
    });

    playbook('X284', async (workspace, data) => {
        const document = await workspace.open(data.file, data.source);
        await configureModules(workspace, [{ name: data.module, file: data.file }]);
        await embedded(async executions => {
            const run = await executions.lens(data.module, document.uri, 'Failed build ends the run');
            assert.strictEqual(run.exitCode, 1, run.text);
            assert.ok(run.text.includes(data.diagnostic), run.text);
            assert.ok(run.text.includes(data.message), run.text);
            assert.ok(!run.text.includes('Process finished'), 'Nothing ran');
        });
    });

    playbook('X285', async (workspace, data) => {
        const document = await workspace.module(data.file, data.source, data.module);
        await embedded(async () => {
            await debugging(async debug => {
                await debug.launch(document.uri, data.module);
                await debug.end('The debug session ends when the program exits');
                debug.exited(data.exitCode);
                assert.strictEqual(debug.events('terminated').length, 1, debug.transcript());
                debug.printed(data.finished);
            });

            // a line typed in the Debug Console while the program runs is its console input
            const ask = await workspace.open(data.inputFile, data.inputSource);
            await configureModules(workspace, [{ name: data.module, file: data.file }, { name: data.inputModule, file: data.inputFile }]);
            await noErrors(ask.uri);
            await debugging(async debug => {
                await debug.launch(ask.uri, data.inputModule);
                await eventually(async () => debug.output(), text => text.includes(data.prompt), 'The program prompts in the Debug Console');
                await debug.request('evaluate', { expression: data.input, context: 'repl' });
                await debug.end('The program reads the line and ends');
                debug.printed(data.greeting);
            });
        });
    });
}

/**
 * X304-X315, registered after X303 in catalog order: a breakpoint, variables, steps, an exception stop, pause, stop on entry, watches,
 * conditions and logpoints, exception classes with exceptionInfo, expressions with operators, code run from the Debug Console, and
 * code that runs with the whole program.
 */
export function debugSessionCases(): void {
    playbook('X304', (workspace, data) => debugModule(workspace, data, async (debug, document) => {
        await debug.launchToBreakpoint(document.uri, data.module, data.breakpointLine, 'The program stops at the breakpoint');
        // VS Code asks for the stopped fiber's stack itself, to show where the program stopped
        const frames = await debug.atBreakpoint(document.uri, data.breakpointLine, data.frames);
        // the program's frames are the whole stack, without the runner that hosts it
        assert.strictEqual(debug.responses('stackTrace')[0]?.body?.totalFrames, data.frames.length, JSON.stringify(frames));
        debug.printed(data.before);
        assert.ok(!debug.output().includes(data.after), 'Nothing after the breakpoint has run');

        await debug.continueToExit(data.exitCode, 'Continue runs the program to its exit');
        debug.printed(data.after);
    }));

    playbook('X305', (workspace, data) => debugModule(workspace, data, async (debug, document) => {
        const stop = await debug.launchToBreakpoint(document.uri, data.module, data.breakpointLine, 'The program stops at the breakpoint');

        // the requests the Variables view makes for the top frame, and for each variable it expands
        const top = await debug.top(stop.threadId);
        assert.strictEqual(top.line, data.breakpointLine, JSON.stringify(top));
        const scopes = await debug.scopes(top.id);
        const locals = scopes.find(scope => scope.name === 'Locals');
        assert.ok(locals, JSON.stringify(scopes));
        const shown = await debug.variables(locals.variablesReference);
        for (const [name, value, type] of data.locals as string[][]) {
            const variable = shown.find(item => item.name === name);
            assert.deepStrictEqual([variable?.name, variable?.value, variable?.type], [name, value, type], JSON.stringify(shown));
        }
        const children = async (name: string) => {
            const variable = shown.find(item => item.name === name);
            assert.ok(variable && variable.variablesReference > 0, `${name} expands: ${JSON.stringify(variable)}`);
            return (await debug.variables(variable.variablesReference)).map(child => [child.name, child.value]);
        };
        assert.deepStrictEqual(await children('point'), data.fields);
        assert.deepStrictEqual(await children('ints'), data.elements);
        assert.strictEqual(shown.find(item => item.name === 'sum')?.variablesReference, 0);

        await debug.continueToExit(data.exitCode, 'Continue runs the program to its exit');
        debug.printed(data.output);
    }));

    playbook('X306', (workspace, data) => debugModule(workspace, data, async (debug, document) => {
        // the frames VS Code selects: a stopped thread's top frame, once the thread's stack has come
        const selected: vscode.DebugStackFrame[] = [];
        const selection = vscode.debug.onDidChangeActiveStackItem(item => {
            if (item instanceof vscode.DebugStackFrame && item.session.type === 'xtc') selected.push(item);
        });
        // VS Code shows a stop once it has selected a frame of the stopped thread, after the [since] it had selected
        //  before, and the stack it asked for then is where the stop is. A step before that steps the thread it had
        //  selected, or the session's first, which the adapter refuses as not stopped, and the stack VS Code asks
        //  for the stop before may come after the step's: both failed X306 on 2026-11-22
        const shown = (stop: DapMessage, since: number, depth: number, message: string) => eventually(
            async () => selected.slice(since).some(frame => frame.threadId === stop.body?.threadId) ? debug.stackAfter(stop) : [],
            frames => frames.length >= depth, message);
        try {
            await debug.launchToBreakpoint(document.uri, data.module, data.breakpointLine, 'The program stops at the breakpoint');
            await shown(debug.events('stopped')[0], 0, 1, 'VS Code shows the stop at the breakpoint');

            for (const [kind, name, line] of data.steps as [string, string, number][]) {
                const stops = debug.events('stopped').length;
                const since = selected.length;
                await vscode.commands.executeCommand(stepCommands[kind]);
                assert.strictEqual((await debug.stopped(`Step ${kind} stops the program`, stops)).reason, 'step');
                // VS Code asks for the stack again after each stop, to show where the step ended
                const frames = await shown(debug.events('stopped')[stops], since, kind === 'into' ? 2 : 1, `VS Code shows where step ${kind} ended`);
                assert.deepStrictEqual([frames[0].name, frames[0].line], [name, line], `Step ${kind}: ${JSON.stringify(frames)}`);
                if (kind === 'into') assert.deepStrictEqual([frames[1].name, frames[1].line], data.caller);
            }
            assert.ok(!debug.output().includes(data.output), 'Nothing after line 6 has run');

            await debug.continueToExit(data.exitCode, 'Continue runs the program to its exit');
            debug.printed(data.output);
        } finally {
            selection.dispose();
        }
    }));

    playbook('X307', (workspace, data) => debugModule(workspace, data, async (debug, document) => {
        // no breakpoint: VS Code turns the adapter's Uncaught Exceptions filter on, as it is by default
        await debug.launch(document.uri, data.module);
        const stop = await debug.stopped('The program stops at the exception');
        assert.strictEqual(stop.reason, 'exception');
        assert.ok(stop.text?.startsWith(data.exception) && stop.text.includes(data.message), JSON.stringify(stop));

        const frames = await debug.showsStack(data.frames, 'VS Code shows where the exception was thrown');
        assert.strictEqual(debug.responses('stackTrace')[0]?.body?.totalFrames, data.frames.length, JSON.stringify(frames));

        const variables = await debug.locals((await debug.top(stop.threadId)).id);
        assert.strictEqual(variables[0].name, data.variable, JSON.stringify(variables));
        assert.ok(variables[0].type?.includes(data.exception), JSON.stringify(variables[0]));
        const [name, value] = data.argument as string[];
        assert.strictEqual(variables.find(item => item.name === name)?.value, value, JSON.stringify(variables));

        await debug.continueToExit(data.exitCode, 'Continue lets the program fail');
        assert.strictEqual(debug.events('stopped').length, 1, 'The exception stops the program once');
        debug.printed(data.message);
    }));

    playbook('X308', (workspace, data) => debugModule(workspace, data, async (debug, document) => {
        await debug.launch(document.uri, data.module);
        await eventually(async () => debug.output(), output => output.includes(data.started), 'The program runs');
        await vscode.commands.executeCommand('workbench.action.debug.pause');
        assert.strictEqual((await debug.stopped('Pause stops the program')).reason, 'pause');
        const frames = await debug.shownFrames(1, 'VS Code shows where the program paused', debug.events('stopped')[0]);
        assert.strictEqual(frames[0].name, data.frame, JSON.stringify(frames));
        assert.ok((data.lines as number[]).includes(frames[0].line), JSON.stringify(frames[0]));

        // the program never ends on its own: stopping the session ends it
        await vscode.debug.stopDebugging(debug.session);
        await debug.end('Stopping the session ends the program');
    }));

    playbook('X309', (workspace, data) => debugModule(workspace, data, async (debug, document) => {
        await debug.launch(document.uri, data.module, { stopOnEntry: true });
        assert.strictEqual((await debug.stopped('The program stops on entry')).reason, 'entry');
        const frames = await debug.shownFrames(1, 'VS Code shows where the program stopped');
        assert.strictEqual(frames[0].name, data.frame, JSON.stringify(frames));
        assert.ok(frames[0].line <= data.lastLine, JSON.stringify(frames[0]));
        assert.ok(!debug.output().includes(data.output), 'Nothing has run yet');

        await debug.continueToExit(data.exitCode, 'Continue runs the program to its exit');
        debug.printed(data.output);
    }));

    playbook('X310', (workspace, data) => debugModule(workspace, data, async (debug, document) => {
        const stop = await debug.launchToBreakpoint(document.uri, data.module, data.breakpointLine, 'The program stops at the breakpoint');
        const frameId = (await debug.top(stop.threadId)).id;

        // the requests VS Code's Watch view and hover send for the focused frame
        await checkWatches(debug, frameId, data.watches as string[][], data.refused as string[]);
        assert.ok((await debug.evaluate(data.expands, frameId, 'hover')).variablesReference > 0, `${data.expands} expands`);

        await debug.continueToExit(data.exitCode, 'Continue runs the program to its exit');
    }));

    playbook('X311', (workspace, data) => debugModule(workspace, data, async (debug, document) => {
        debug.add(lineBreakpoint(document.uri, data.breakpointLine, data.condition, data.hitCondition),
            lineBreakpoint(document.uri, data.logLine, undefined, undefined, data.logMessage));
        await debug.launch(document.uri, data.module);
        const stop = await debug.stopped('The program stops at the hit its conditions name');
        assert.strictEqual(stop.reason, 'breakpoint');
        const i = await debug.evaluate('i', (await debug.top(stop.threadId)).id, 'watch');
        assert.strictEqual(i.result, data.stopsAt);

        await debug.continueToExit(data.exitCode, 'The logpoint lets the program run to its exit');
        assert.strictEqual(debug.events('stopped').length, 1, 'Only the hit the conditions name stops the program');
        debug.printed(data.logged);
    }));

    playbook('X312', (workspace, data) => debugModule(workspace, data, async (debug, document) => {
        await debug.launch(document.uri, data.module, { stopOnEntry: true });
        await debug.stopped('The program stops on entry');

        // the filter's condition, as the Breakpoints view's Edit Condition sends it
        const answer = await debug.request<{ breakpoints?: { verified: boolean }[] }>('setExceptionBreakpoints',
            { filters: [], filterOptions: [{ filterId: data.filter, condition: data.condition }] });
        assert.deepStrictEqual(answer.breakpoints?.map(breakpoint => breakpoint.verified), [true]);
        await vscode.commands.executeCommand('workbench.action.debug.continue');
        const stop = await debug.stopped('The program stops at the exception class the condition names', 1);
        assert.strictEqual(stop.reason, 'exception');
        assert.ok(debug.output().includes(data.caught), `The caught exception did not stop the program: ${debug.output()}`);
        const top = await debug.top(stop.threadId);
        assert.deepStrictEqual([top.name, top.line], data.frame);

        const info = await debug.request<{ exceptionId: string; description?: string; breakMode: string; details?: { stackTrace?: string } }>(
            'exceptionInfo', { threadId: stop.threadId });
        assert.deepStrictEqual([info.exceptionId, info.description, info.breakMode], [data.exception, data.description, data.breakMode]);
        assert.ok(info.details?.stackTrace?.includes(data.stackTrace), JSON.stringify(info));

        await debug.continueToExit(data.exitCode, 'Continue lets the program fail');
    }));

    playbook('X313', (workspace, data) => debugModule(workspace, data, async (debug, document) => {
        debug.add(lineBreakpoint(document.uri, data.breakpointLine, data.condition),
            lineBreakpoint(document.uri, data.logLine, undefined, undefined, data.logMessage));
        await debug.launch(document.uri, data.module);
        const stops = data.conditionStops as string[];
        for (let stop = 0; stop < stops.length; stop++) {
            const stopped = await debug.stopped(`The program stops where i is ${stops[stop]}`, stop);
            assert.strictEqual(stopped.reason, 'breakpoint');
            const frameId = (await debug.top(stopped.threadId)).id;
            assert.strictEqual((await debug.evaluate('i', frameId, 'watch')).result, stops[stop]);
            // the watches and the refusal, at the first stop
            if (stop === 0) await checkWatches(debug, frameId, data.watches as string[][], data.refused as string[]);
            await vscode.commands.executeCommand('workbench.action.debug.continue');
        }
        await debug.end('The logpoint lets the program run to its exit');
        assert.strictEqual(debug.events('stopped').length, stops.length, 'Only the hits whose condition holds stop the program');
        debug.printed(data.logged);
        debug.exited(data.exitCode);
    }));

    playbook('X314', (workspace, data) => debugModule(workspace, data, async (debug, document) => {
        const stop = await debug.launchToBreakpoint(document.uri, data.module, data.breakpointLine, 'The program stops at the breakpoint');
        const top = await debug.top(stop.threadId);
        assert.strictEqual(top.name, data.frame);

        // a call typed in the Debug Console runs on the stopped fiber, and VS Code is told to read the variables again
        const [call, value] = data.call as string[];
        assert.strictEqual((await debug.evaluate(call, top.id, 'repl')).result, value);
        await debug.invalidates(['variables'], 'The run invalidates the variables');
        const [watch, watched] = data.watch as string[];
        assert.strictEqual((await debug.evaluate(watch, top.id, 'watch')).result, watched, 'What the call changed stays changed');

        // a run that loops ends when it is cancelled, by its request's seq, and at the console's time limit when it is not
        // the expected rejection is attached first, since the answer can come before the cancel's own
        const looping = assert.rejects(debug.evaluate(data.loop, top.id, 'repl'), (error: Error) => error.message.includes(data.cancelled));
        const request = await eventually(async () => debug.requests('evaluate').find(message => message.arguments?.expression === data.loop),
            message => message !== undefined, 'VS Code sends the loop to the debug adapter');
        await debug.request('cancel', { requestId: request!.seq });
        await looping;
        await assert.rejects(debug.evaluate(data.loop, top.id, 'repl'), (error: Error) => error.message.includes(data.limited));

        // the program goes on from its stop, with the console's change
        await debug.continueToExit(data.exitCode);
        debug.printed(data.output);
    }));

    playbook('X315', (workspace, data) => debugModule(workspace, data, async (debug, document) => {
        const stop = await debug.launchToBreakpoint(document.uri, data.module, data.breakpointLine, 'The program stops at the breakpoint', { evaluationRunsProgram: true });
        const top = await debug.top(stop.threadId);
        assert.strictEqual(top.name, data.frame);

        // the worker's service answers once its fiber, which the stop holds otherwise, has done its work
        const [call, value] = data.call as string[];
        assert.strictEqual((await debug.evaluate(call, top.id, 'repl')).result, value);
        await debug.invalidates(['all'], 'The run invalidates the threads, stacks and variables');

        await debug.continueToExit(data.exitCode);
        debug.printed(data.output);
    }));

    playbook('X316', (workspace, data) => debugModule(workspace, data, async (debug, document) => {
        const stop = await debug.launchToBreakpoint(document.uri, data.module, data.breakpointLine, 'The program stops at the breakpoint');

        // the requests the Variables view makes as it expands the object, and as each lazy variable is clicked
        const locals = await debug.locals((await debug.top(stop.threadId)).id);
        const object = locals.find(item => item.name === data.expands);
        assert.ok(object && object.variablesReference > 0, JSON.stringify(locals));
        const fields = await debug.variables(object.variablesReference);
        assert.deepStrictEqual(fields.map(field => [field.name, field.value]), data.fields);
        for (const [name, value] of data.lazy as string[][]) {
            const lazy = fields.find(field => field.name === name);
            assert.strictEqual(lazy?.presentationHint?.lazy, true, JSON.stringify(lazy));
            const shown = await debug.variables(lazy.variablesReference);
            assert.deepStrictEqual(shown.map(item => [item.name, item.value]), [[name, value]]);
        }

        await debug.continueToExit(data.exitCode);
        debug.printed(data.output);
    }));

    playbook('X317', (workspace, data) => debugModule(workspace, data, async (debug, document) => {
        const stop = await debug.launchToBreakpoint(document.uri, data.module, data.breakpointLine, 'The program stops at the breakpoint');
        const reference = await debug.localsReference((await debug.top(stop.threadId)).id);
        // the requests the Variables view makes, which name no format, as VS Code never asks for one
        const locals = async () => new Map<string, string>((await debug.variables(reference)).map(variable => [variable.name, variable.value]));

        const decimal = await locals();
        for (const [name, value] of data.decimal as string[][]) {
            assert.strictEqual(decimal.get(name), value, JSON.stringify([...decimal]));
        }
        // two objects with the same fields have different identity codes
        const identities = (data.identities as string[]).map(name => decimal.get(name) ?? '');
        for (const text of identities) {
            assert.match(text, /^.+ #\d+$/);
        }
        assert.notStrictEqual(identities[0], identities[1]);

        // the Variables view's Show Values as Hexadecimal sets the session's format, and VS Code reads the variables again
        await vscode.commands.executeCommand('xtc.debug.toggleHexValues');
        await eventually(async () => debug.requests('xtc/valueFormat').length, count => count > 0, 'The item sets the session\'s value format');
        await eventually(async () => debug.events('invalidated').length, count => count > 0, 'The session invalidates the variables');
        const hex = await locals();
        for (const [name, value] of data.hex as string[][]) {
            assert.strictEqual(hex.get(name), value, JSON.stringify([...hex]));
        }
        await vscode.commands.executeCommand('xtc.debug.toggleHexValues');
        const [name, value] = (data.decimal as string[][])[0];
        await eventually(async () => (await locals()).get(name), shown => shown === value, 'The item turned off shows decimals again');

        await debug.continueToExit(data.exitCode);
        debug.printed(data.output);
    }));

    playbook('X318', (workspace, data) => debugModule(workspace, data, async (debug, document) => {
        const stop = await debug.launchToBreakpoint(document.uri, data.module, data.breakpointLine, 'The program stops at the breakpoint');
        const locals = await debug.locals((await debug.top(stop.threadId)).id);

        // the array says how many elements it has, so VS Code groups them in ranges, and reads a range when it is expanded
        const array = locals.find(variable => variable.name === data.expands);
        assert.ok(array, JSON.stringify(locals));
        assert.strictEqual(array.indexedVariables, data.elements, JSON.stringify(array));
        const [first, count] = data.page as number[];
        const page = await debug.variables(array.variablesReference, { filter: 'indexed', start: first, count });
        assert.strictEqual(page.length, count);
        assert.deepStrictEqual([page[0].name, page[0].value], data.first);
        assert.deepStrictEqual([page[count - 1].name, page[count - 1].value], data.last);

        await debug.continueToExit(data.exitCode);
        debug.printed(data.output);
    }));

    playbook('X319', (workspace, data) => debugModule(workspace, data, async (debug, document) => {
        const stop = await debug.launchToBreakpoint(document.uri, data.module, data.breakpointLine, 'The program stops at the breakpoint');
        const variablesReference = await debug.localsReference((await debug.top(stop.threadId)).id);

        // the request the Variables view's Set Value makes: a value of another type is refused with the reason
        const [name, expression, value] = data.set as string[];
        const [other, reason] = data.refused as string[];
        await assert.rejects(debug.request('setVariable', { variablesReference, name, value: other }), (error: Error) => error.message.includes(reason));
        const changed = await debug.request<{ value: string }>('setVariable', { variablesReference, name, value: expression });
        assert.strictEqual(changed.value, value);

        await debug.continueToExit(data.exitCode);
        debug.printed(data.output);
    }));

    playbook('X320', (workspace, data) => debugModule(workspace, data, async (debug, document) => {
        const stop = await debug.launchToBreakpoint(document.uri, data.module, data.breakpointLine, 'The program stops at the breakpoint');
        const frameId = (await debug.top(stop.threadId)).id;
        type Target = { label: string; type?: string; start?: number; length?: number };
        const complete = async (text: string): Promise<Target[]> =>
            (await debug.request<{ targets: Target[] }>('completions', { frameId, text, column: text.length + 1 })).targets;

        // the request the console makes as a name is typed: after a dot, the members of the value before it
        const [members, ...names] = data.members as string[];
        const offered = (await complete(members)).map(target => target.label);
        for (const name of names) assert.ok(offered.includes(name), `${members} offers ${JSON.stringify(offered)}`);
        // the typed part of the name is replaced, the length before the cursor, as the console replaces it
        const [typed, name, completed] = data.typed as string[];
        const target = (await complete(typed)).find(candidate => candidate.label === name);
        assert.ok(target, `${typed} offers no ${name}`);
        assert.strictEqual(target.start, undefined, JSON.stringify(target));
        assert.strictEqual(typed.slice(0, typed.length - (target.length ?? 0)) + target.label, completed);

        await debug.continueToExit(data.exitCode);
        debug.printed(data.output);
    }));

    playbook('X321', (workspace, data) => debugModule(workspace, data, async (debug, document) => {
        const stop = await debug.launchToBreakpoint(document.uri, data.module, data.breakpointLine, 'The program stops at the breakpoint');
        const frames = async () => (await debug.frames(stop.threadId)).map(frame => [frame.name, frame.line]);

        // lines typed above the stop, which the program was not built with; once the server has them, the stop
        // shows on its line now
        const edit = new vscode.WorkspaceEdit();
        edit.insert(document.uri, new vscode.Position(data.insertLine - 1, 0), (data.inserted as string[]).map(line => `${line}\n`).join(''));
        assert.ok(await vscode.workspace.applyEdit(edit));
        await eventually(async () => (await frames())[0]?.[1], line => line === data.stoppedNow, 'The stop shows on its line now');

        // a breakpoint on a line that moved is placed on the line the program has for it; one on a typed line
        // is refused with the reason
        debug.add(lineBreakpoint(document.uri, data.moved), lineBreakpoint(document.uri, data.typed));
        const answer = await eventually(async () => debug.responses('setBreakpoints')
            .map(message => message.body?.breakpoints ?? []).find(items => items.some(item => item.line === data.moved)),
        found => found !== undefined, 'The new breakpoints are answered');
        assert.ok(answer!.some(item => item.line === data.moved && item.verified), JSON.stringify(answer));
        assert.ok(answer!.some(item => item.line === data.typed && !item.verified && item.message === data.reason), JSON.stringify(answer));

        await vscode.commands.executeCommand('workbench.action.debug.continue');
        await debug.stopped('The program stops at the moved line', 1);
        assert.deepStrictEqual(await frames(), data.frames);

        await debug.continueToExit(data.exitCode);
        debug.printed(data.output);
    }));

    playbook('X322', async (workspace, data) => {
        const document = await workspace.module(data.file, data.source, data.module);
        await embedded(async executions => {
            const started = (update: ExecutionUpdate) => update.text.includes(data.started) && !finished(update);
            for (let round = 1; round <= data.rounds; round++) {
                // a run to its exit, from the Run lens command
                const done = await executions.lens(data.module, document.uri, `Run ${round} finishes`);
                assert.strictEqual(done.exitCode, 0, done.text);
                assert.ok(done.text.includes(data.done), done.text);

                // a run that loops until Stop ends it
                await executions.task(data.module, document.uri, `Looping run ${round} starts`, { args: [data.spin], until: started });
                const stopped = await executions.stop(data.module, `Stopped run ${round} reports its exit`);
                assert.ok(!stopped.text.includes(data.done), stopped.text);

                // a debug session that stops at a breakpoint and continues to its exit
                await debugging(async debug => {
                    await debug.launchToBreakpoint(document.uri, data.module, data.breakpointLine, `Debug session ${round} stops at the breakpoint`);
                    // VS Code focuses the stopped fiber once it has its stack, which Continue then resumes
                    await debug.shownFrames(1, `VS Code shows debug session ${round}'s stack`);
                    assert.ok(vscode.debug.activeDebugSession, `Debug session ${round} is open while it is stopped`);
                    await debug.continueToExit(0, `Debug session ${round} runs to its exit`);
                    debug.printed(data.done);
                });

                // the round leaves no run task and no debug session open
                await eventually(async () => vscode.tasks.taskExecutions.filter(item => item.task.definition.type === 'xtc').length,
                    count => count === 0, `Round ${round} leaves no run task`);
                await eventually(async () => vscode.debug.activeDebugSession, session => session === undefined, `Round ${round} leaves no debug session`);
            }
        });
    });

    playbook('X323', (workspace, data) => debugModule(workspace, data, async (debug, document) => {
        await debug.launchToBreakpoint(document.uri, data.module, data.breakpointLine, 'The program stops at the breakpoint');
        // VS Code focuses the stopped fiber once it has its stack, which Run to Cursor then continues
        await debug.shownFrames(1, 'VS Code shows the stack');

        // Run to Cursor, from an editor whose cursor is on the line
        select(await vscode.window.showTextDocument(document, { preserveFocus: false }), data.cursorLine);
        await vscode.commands.executeCommand('editor.debug.action.runToCursor');
        const stop = await debug.stopped('Run to Cursor stops at the cursor\'s line', 1);
        assert.strictEqual((await debug.top(stop.threadId)).line, data.cursorLine);
        // the loop's first round, before it prints
        assert.ok(!debug.output().includes(data.printed), debug.output());

        // once stopped, VS Code removes the breakpoint it added for the cursor, and the server answers for the
        // breakpoints left; Continue right after the stop could otherwise reach the server first
        await eventually(async () => debug.responses('setBreakpoints', debug.events('stopped')[1])
            .some(message => (message.body?.breakpoints ?? []).every(item => item.line !== data.cursorLine)), removed => removed,
        'VS Code removes the breakpoint it added for the cursor');

        // no breakpoint stays on the cursor's line: Continue runs the loop's other rounds to the exit
        await debug.continueToExit(data.exitCode);
        assert.strictEqual(debug.events('stopped').length, 2, JSON.stringify(debug.events('stopped')));
        debug.printed(data.output);
        assert.deepStrictEqual(vscode.debug.breakpoints.filter(item => item instanceof vscode.SourceBreakpoint
            && item.location.uri.toString() === document.uri.toString()).map(item => (item as vscode.SourceBreakpoint).location.range.start.line + 1),
        [data.breakpointLine]);
    }));

    playbook('X324', (workspace, data) => debugModule(workspace, data, async (debug, document) => {
        const stop = await debug.launchToBreakpoint(document.uri, data.module, data.breakpointLine, 'The program stops in the lambda');
        const frames = await debug.frames(stop.threadId);
        assert.deepStrictEqual(frames.map(frame => frame.name), data.frames);

        // the library's frame is in the source bundled with it, not a file of the workspace, and opens at its line
        const library = frames[1];
        const file = library.source?.path ?? '';
        assert.ok(file.replace(/\\/g, '/').endsWith(data.librarySource), JSON.stringify(library));
        assert.ok(!file.startsWith(vscode.workspace.getWorkspaceFolder(document.uri)!.uri.fsPath), file);
        const opened = await vscode.workspace.openTextDocument(vscode.Uri.file(file));
        const text = opened.lineAt(library.line - 1).text;
        assert.ok(text.includes(data.libraryText), text);

        // the lambda runs once, so Continue runs the program to its exit
        await debug.continueToExit(data.exitCode);
        debug.printed(data.output);
    }));

    playbook('X325', (workspace, data) => debugModule(workspace, data, async (debug, document) => {
        // by then, run() has asked the busy worker for its count, and waits for the answer
        debug.add(lineBreakpoint(document.uri, data.breakpointLine, data.condition));
        await debug.launch(document.uri, data.module);
        const stop = await debug.stopped('The worker stops at its count');

        // the stopped fiber first, then run()'s, which waits for the worker, with its own stack; the worker takes
        // in the call that run() waits for, as a fiber not started yet, when it next schedules, before the stop
        // or after it
        const threads = (await debug.request<{ threads: { id: number; name: string }[] }>('threads')).threads;
        const names = threads.map(thread => thread.name);
        assert.ok([JSON.stringify(data.threads), JSON.stringify([...data.threads, data.queuedThread])].includes(JSON.stringify(names)),
            JSON.stringify(names));
        assert.strictEqual(threads[0].id, stop.threadId);
        const frames = await debug.frames(threads[1].id);
        assert.deepStrictEqual(frames.map(frame => [frame.name, frame.line]), data.waitingFrames);

        await debug.continueToExit(data.exitCode);
        debug.printed(data.output);
    }));

    playbook('X326', (workspace, data) => debugModule(workspace, data, async (debug, document) => {
        await debug.launchToBreakpoint(document.uri, data.module, data.breakpointLine, 'The program stops in the lambda');

        // VS Code asks for the loaded sources once its Loaded Scripts view shows
        await vscode.commands.executeCommand('workbench.debug.loadedScriptsView.focus');
        const asked = await eventually(async () => debug.requests('loadedSources')[0], request => request !== undefined, 'VS Code asks for the loaded sources');
        const answer = await eventually(async () => debug.responses('loadedSources').find(message => message.request_seq === asked!.seq),
            response => response !== undefined, 'The loaded sources are answered');
        const paths = (answer!.body?.sources ?? []).map(source => (source.path ?? '').replace(/\\/g, '/'));
        assert.ok(paths.some(path => path.endsWith(`/${data.file}`)), JSON.stringify(paths));
        const library = paths.find(path => path.endsWith(`/${data.librarySource}`));
        assert.ok(library, JSON.stringify(paths));
        assert.ok(!library.startsWith(vscode.workspace.getWorkspaceFolder(document.uri)!.uri.fsPath.replace(/\\/g, '/')), library);

        // VS Code has no Modules view; the answer says which modules are the program's own
        const modules = (await debug.request<{ modules: { name: string; isUserCode?: boolean }[] }>('modules', {})).modules;
        for (const [name, own] of Object.entries(data.modules as Record<string, boolean>)) {
            assert.strictEqual(modules.find(module => module.name === name)?.isUserCode, own, JSON.stringify(modules));
        }

        await debug.continueToExit(data.exitCode);
        debug.printed(data.output);
    }));

    playbook('X327', (workspace, data) => debugModule(workspace, data, async (debug, document) => {
        const stop = await debug.launchToBreakpoint(document.uri, data.module, data.breakpointLine, 'The program stops at the line with two calls');

        // the calls of the line, in the order it makes them, as VS Code's Step Into Target offers them
        const targets = (await debug.request<{ targets: { id: number; label: string }[] }>('stepInTargets',
            { frameId: (await debug.top(stop.threadId)).id })).targets;
        assert.deepStrictEqual(targets.map(target => target.label), data.targets);

        // into the second call: the first runs without stopping, and the step ends in the chosen one
        await debug.request('stepIn', { threadId: stop.threadId, targetId: targets.find(target => target.label === data.into)!.id });
        assert.strictEqual((await debug.stopped('The step stops in the chosen call', 1)).reason, 'step');
        const frame = await debug.top(stop.threadId);
        assert.deepStrictEqual([frame.name, frame.line], data.frame);

        debug.removeBreakpoints();
        await debug.continueToExit(data.exitCode);
        debug.printed(data.output);
    }));

    playbook('X328', (workspace, data) => debugModule(workspace, data, async (debug, document) => {
        // as the Breakpoints view's + adds it, with a condition on the method's parameter
        debug.add(new vscode.FunctionBreakpoint(data.function, true, data.condition));
        await debug.launch(document.uri, data.module);
        const stop = await debug.stopped('The program stops where it enters the method');

        // installed as the program started, at the method's first line
        debug.installed(data.frame[1] as number);

        // the second call, the first whose n is more than 5
        assert.strictEqual(stop.reason, 'function breakpoint');
        const frames = await debug.frames(stop.threadId);
        assert.deepStrictEqual([frames[0].name, frames[0].line], data.frame);
        assert.deepStrictEqual([frames[1].name, frames[1].line], data.caller);

        await debug.continueToExit(data.exitCode);
        assert.strictEqual(debug.events('stopped').length, 1, debug.transcript());
        debug.printed(data.output);
    }));

    playbook('X329', (workspace, data) => debugModule(workspace, data, async (debug, document) => {
        const stop = await debug.launchToBreakpoint(document.uri, data.module, data.breakpointLine, 'The program stops in inner()');

        // the frames that the Call Stack view offers Restart Frame on: not run(), which the runtime called
        const frames = await debug.frames(stop.threadId);
        assert.deepStrictEqual(frames.map(frame => frame.name), data.frames);
        // false is said, since a frame that does not say it cannot restart can
        assert.deepStrictEqual(frames.map(frame => frame.canRestart), data.canRestart);

        // as Restart Frame does: its caller calls it again, and it stops at its first line
        debug.removeBreakpoints();
        await debug.request('restartFrame', { frameId: frames.find(frame => frame.name === data.restart)!.id });
        assert.strictEqual((await debug.stopped('The restarted frame stops at its first line', 1)).reason, 'restart');
        const top = await debug.top(stop.threadId);
        assert.deepStrictEqual([top.name, top.line], data.frame);

        await debug.continueToExit(data.exitCode);
        const output = debug.output();
        assert.ok(output.includes(data.output), output);
        assert.strictEqual(output.split(data.printed).length - 1, 2, output);
    }));

    playbook('X330', (workspace, data) => debugModule(workspace, data, async (debug, document) => {
        const stop = await debug.launchToBreakpoint(document.uri, data.module, data.breakpointLine, 'The program stops before the first add()');

        // the field of the variable's object, as the Variables view's Break on Value Change asks for it
        const locals = await debug.locals((await debug.top(stop.threadId)).id);
        const object = locals.find(variable => variable.name === data.variable)!;
        const info = await debug.request<{ dataId: string | null; description: string; accessTypes?: string[] }>('dataBreakpointInfo',
            { variablesReference: object.variablesReference, name: data.field });
        assert.strictEqual(info.description, data.description);
        assert.deepStrictEqual(info.accessTypes, ['write']);
        const set = await debug.request<{ breakpoints: { verified: boolean }[] }>('setDataBreakpoints',
            { breakpoints: [{ dataId: info.dataId, accessType: 'write' }] });
        assert.ok(set.breakpoints[0].verified, JSON.stringify(set));

        // each add() stops at the line after the one that changed the field, and says how it changed
        debug.removeBreakpoints();
        for (const [index, change] of (data.changes as string[]).entries()) {
            await vscode.commands.executeCommand('workbench.action.debug.continue');
            const stopped = await debug.stopped(`The program stops once ${change}`, index + 1);
            assert.strictEqual(stopped.reason, 'data breakpoint');
            assert.strictEqual(stopped.description, change);
            const frame = await debug.top(stop.threadId);
            assert.deepStrictEqual([frame.name, frame.line], data.frame);
        }

        await debug.request('setDataBreakpoints', { breakpoints: [] });
        await debug.continueToExit(data.exitCode);
        debug.printed(data.output);
    }));

    playbook('X331', (workspace, data) => debugModule(workspace, data, async (debug, document) => {
        const stop = await debug.launchToBreakpoint(document.uri, data.module, data.breakpointLine, 'The program stops in add()');
        const frame = await debug.top(stop.threadId);

        // what VS Code asks the language server for as it shows the values beside the code
        const stopped = new vscode.Range(frame.line - 1, 0, frame.line - 1, 0);
        const all = new vscode.Range(0, 0, document.lineCount, 0);
        const values = await vscode.commands.executeCommand<vscode.InlineValue[]>('vscode.executeInlineValueProvider',
            document.uri, all, { frameId: frame.id, stoppedLocation: stopped });
        const lookups = values.map(value => value as vscode.InlineValueVariableLookup);
        assert.deepStrictEqual(lookups.map(lookup => [lookup.variableName, lookup.range.start.line + 1]), data.inlineValues);
        assert.ok(lookups.every(lookup => lookup.caseSensitiveLookup), JSON.stringify(lookups));

        // each name has its value in the stopped frame, where VS Code looks it up
        const locals = await debug.locals(frame.id);
        for (const [name, value] of Object.entries(data.frameValues as Record<string, string>)) {
            assert.strictEqual(locals.find(variable => variable.name === name)?.value, value, JSON.stringify(locals));
        }

        debug.removeBreakpoints();
        await debug.continueToExit(data.exitCode);
    }));
}

/** Checks the [watches], each an expression with its value and type, in the frame [frameId], and that the one [refused] names is refused with its reason. */
async function checkWatches(debug: Debugger, frameId: number, watches: string[][], [refused, reason]: string[]): Promise<void> {
    for (const [expression, value, type] of watches) {
        const answer = await debug.evaluate(expression, frameId, 'watch');
        assert.deepStrictEqual([answer.result, answer.type], [value, type], expression);
    }
    await assert.rejects(debug.evaluate(refused, frameId, 'watch'), (error: Error) => error.message.includes(reason));
}

/** X306's steps, by the commands that VS Code's Step Over, Step Into and Step Out buttons run. */
const stepCommands: Record<string, string> = {
    over: 'workbench.action.debug.stepOver',
    into: 'workbench.action.debug.stepInto',
    out: 'workbench.action.debug.stepOut',
};

/** The launch configuration of [module] that its Debug lens starts, with [settings] besides. */
function launchOf(module: string, settings: object = {}): vscode.DebugConfiguration {
    return { type: 'xtc', request: 'launch', name: `Debug ${module}`, module, ...settings };
}

/** A breakpoint on the one-based [line] of [uri], as a click in the editor's gutter sets it, and Edit Breakpoint changes it. */
function lineBreakpoint(uri: vscode.Uri, line: number, condition?: string, hitCondition?: string, logMessage?: string): vscode.SourceBreakpoint {
    return new vscode.SourceBreakpoint(new vscode.Location(uri, new vscode.Position(line - 1, 0)), true, condition, hitCondition, logMessage);
}

/**
 * What a case sees of the Ecstasy debug sessions it starts: the Debug Adapter Protocol messages both ways, the session and
 * whether it ended, and the breakpoints the case set, which [close] removes again.
 */
class Debugger {
    /** The messages the debug adapters send VS Code. */
    private readonly sent: DapMessage[] = [];
    /** The messages VS Code sends the debug adapters. */
    private readonly received: DapMessage[] = [];
    /** The messages both ways, in the order they went, so that the stack requests of a stop follow it. */
    private readonly messages: DapMessage[] = [];
    private readonly breakpoints: vscode.Breakpoint[] = [];
    private started?: vscode.DebugSession;
    private done = false;
    private readonly subscriptions = [
        vscode.debug.registerDebugAdapterTrackerFactory('xtc', {
            createDebugAdapterTracker: () => ({
                onDidSendMessage: (message: DapMessage) => {
                    this.sent.push(message);
                    this.messages.push(message);
                },
                onWillReceiveMessage: (message: DapMessage) => {
                    this.received.push(message);
                    this.messages.push(message);
                },
            }),
        }),
        vscode.debug.onDidStartDebugSession(session => {
            if (session.type === 'xtc') this.started = session;
        }),
        vscode.debug.onDidTerminateDebugSession(session => {
            if (session.type === 'xtc') this.done = true;
        }),
    ];

    /** The session that started, which the case asks what VS Code's views ask. */
    get session(): vscode.DebugSession {
        assert.ok(this.started, 'The Ecstasy debug session started');
        return this.started;
    }

    /** Sets [breakpoints], all at once, as VS Code sends them to the adapter. */
    add(...breakpoints: vscode.Breakpoint[]): void {
        vscode.debug.addBreakpoints(breakpoints);
        this.breakpoints.push(...breakpoints);
    }

    removeBreakpoints(): void {
        vscode.debug.removeBreakpoints(this.breakpoints);
    }

    /** Starts a debug session of [module], as its Debug lens does, in the folder of [uri]. */
    async launch(uri: vscode.Uri, module: string, settings: object = {}): Promise<void> {
        assert.ok(await vscode.debug.startDebugging(vscode.workspace.getWorkspaceFolder(uri), launchOf(module, settings)),
            `The launch of ${module} starts a debug session in the language server`);
    }

    /** Sets a breakpoint on the one-based [line] of [uri], launches [module] with [settings], and waits until the program first stops. */
    async launchToBreakpoint(uri: vscode.Uri, module: string, line: number, message: string, settings: object = {}): Promise<DapBody> {
        this.add(lineBreakpoint(uri, line));
        await this.launch(uri, module, settings);
        return this.stopped(message);
    }

    events(name: string): DapMessage[] {
        return this.sent.filter(message => message.type === 'event' && message.event === name);
    }

    /** What the program printed, as the Debug Console shows it. */
    output(): string {
        return this.events('output').map(message => message.body?.output ?? '').join('');
    }

    printed(text: string): void {
        assert.ok(this.output().includes(text), this.output());
    }

    /** Every message the debug adapters sent, for a failure to show. */
    transcript(): string {
        return JSON.stringify(this.sent);
    }

    /** The adapters' responses to [command], after the message [after], if given. */
    responses(command: string, after?: DapMessage): DapMessage[] {
        return this.sent.slice(after ? this.sent.indexOf(after) : 0).filter(message => message.type === 'response' && message.command === command);
    }

    /** VS Code's requests of [command]. */
    requests(command: string): DapMessage[] {
        return this.received.filter(message => message.type === 'request' && message.command === command);
    }

    /**
     * Waits until the program has stopped after [index] stops before, and returns what that stop says; a session that ends
     * first fails at once, with what the adapter sent, instead of the wait running out.
     */
    async stopped(message: string, index = 0, timeoutMs?: number): Promise<DapBody> {
        await eventually(async () => this.events('stopped').length > index || this.done, value => value, message, timeoutMs);
        const stops = this.events('stopped');
        assert.ok(stops.length > index, `The session ended without stopping: ${this.transcript()}`);
        return stops[index].body ?? {};
    }

    /** Checks that the adapter installed a breakpoint on [line], as its `breakpoint` events say. */
    installed(line: number): void {
        const installed = this.events('breakpoint').map(message => message.body?.breakpoint);
        assert.ok(installed.some(item => item?.verified && item.line === line), JSON.stringify(installed));
    }

    /** The frames of the stacks VS Code asked for itself, after the message [after], if given, once there are [count]. */
    shownFrames(count: number, message: string, after?: DapMessage): Promise<DapFrame[]> {
        return eventually(async () => this.responses('stackTrace', after).flatMap(response => response.body?.stackFrames ?? []),
            frames => frames.length >= count, message);
    }

    /** Waits until VS Code has asked for the stack of a stop, as it does to show it, and checks its top frames are [expected]. */
    async showsStack(expected: readonly unknown[], message: string): Promise<DapFrame[]> {
        const frames = await this.shownFrames(expected.length, message);
        assert.deepStrictEqual(frames.slice(0, expected.length).map(frame => [frame.name, frame.line]), expected);
        return frames;
    }

    /** Checks the first stop: at the installed breakpoint on [line] of [uri], with the stack VS Code shows [expected] at its top. */
    async atBreakpoint(uri: vscode.Uri, line: number, expected: readonly unknown[]): Promise<DapFrame[]> {
        this.installed(line);
        assert.strictEqual(this.events('stopped')[0].body?.reason, 'breakpoint');
        const frames = await this.showsStack(expected, 'VS Code shows the call stack');
        assert.strictEqual(await fs.realpath(frames[0].source!.path!), await fs.realpath(uri.fsPath));
        return frames;
    }

    /**
     * The stack of [stop]'s thread that VS Code asked for after the stop, from its top frame: each request's frames go where
     * its `startFrame` puts them.
     */
    stackAfter(stop: DapMessage): DapFrame[] {
        const after = this.messages.slice(this.messages.indexOf(stop));
        const frames: DapFrame[] = [];
        after.filter(message => message.type === 'request' && message.command === 'stackTrace' && message.arguments?.threadId === stop.body?.threadId)
            .forEach(request => after.find(message => message.type === 'response' && message.request_seq === request.seq)?.body?.stackFrames
                ?.forEach((frame, index) => { frames[(request.arguments?.startFrame ?? 0) + index] = frame; }));
        const gap = frames.findIndex(frame => frame === undefined);
        return gap < 0 ? frames : frames.slice(0, gap);
    }

    /** Sends the session the request [command], as one of VS Code's views does. */
    request<T>(command: string, args?: object): Promise<T> {
        return Promise.resolve(this.session.customRequest(command, args));
    }

    async frames(threadId: number | undefined): Promise<DapFrame[]> {
        return (await this.request<{ stackFrames: DapFrame[] }>('stackTrace', { threadId })).stackFrames;
    }

    async top(threadId: number | undefined): Promise<DapFrame> {
        return (await this.frames(threadId))[0];
    }

    async scopes(frameId: number): Promise<DapScope[]> {
        return (await this.request<{ scopes: DapScope[] }>('scopes', { frameId })).scopes;
    }

    /** The reference of the frame's locals, its first scope. */
    async localsReference(frameId: number): Promise<number> {
        return (await this.scopes(frameId))[0].variablesReference;
    }

    async variables(variablesReference: number, paging: object = {}): Promise<DapVariable[]> {
        return (await this.request<{ variables: DapVariable[] }>('variables', { variablesReference, ...paging })).variables;
    }

    async locals(frameId: number): Promise<DapVariable[]> {
        return this.variables(await this.localsReference(frameId));
    }

    evaluate(expression: string, frameId: number, context: string): Promise<{ result: string; type?: string; variablesReference: number }> {
        return this.request('evaluate', { expression, frameId, context });
    }

    /** Waits until the adapter tells VS Code to read [areas] again, as its first `invalidated` event says. */
    async invalidates(areas: string[], message: string): Promise<void> {
        await eventually(async () => this.events('invalidated'), events => events.length > 0, message);
        assert.deepStrictEqual(this.events('invalidated')[0].body?.areas, areas);
    }

    /** Waits until the session ends. */
    async end(message: string, timeoutMs?: number): Promise<void> {
        await eventually(async () => this.done, done => done, message, timeoutMs);
    }

    /** Checks that the program exited with [exitCode], as the adapter's `exited` event says. */
    exited(exitCode: number): void {
        assert.strictEqual(this.events('exited')[0]?.body?.exitCode, exitCode, this.transcript());
    }

    /** Continues the stopped program, as the Continue button does, and checks that it runs to its exit with [exitCode]. */
    async continueToExit(exitCode: number, message = 'The program runs to its exit'): Promise<void> {
        await vscode.commands.executeCommand('workbench.action.debug.continue');
        await this.end(message);
        this.exited(exitCode);
    }

    /** Removes the breakpoints set, stops a session that has not ended, as one a case failed in while it was stopped, and stops listening. */
    async close(): Promise<void> {
        vscode.debug.removeBreakpoints(this.breakpoints);
        if (this.started && !this.done) await vscode.debug.stopDebugging(this.started);
        this.subscriptions.forEach(each => each.dispose());
    }
}

/** Runs [body] with a [Debugger], which leaves no session, breakpoint or listener behind, also when [body] fails while the program is stopped. */
async function debugging(body: (debug: Debugger) => Promise<void>): Promise<void> {
    const debug = new Debugger();
    try {
        await body(debug);
    } finally {
        await debug.close();
    }
}

/** Opens [data]'s module as the graph's only one, and runs [body] with a [Debugger] in embedded build mode. */
async function debugModule(workspace: Workspace, data: ModuleValues,
    body: (debug: Debugger, document: vscode.TextDocument) => Promise<void>): Promise<void> {
    const document = await workspace.module(data.file, data.source, data.module);
    await embedded(() => debugging(debug => body(debug, document)));
}

/** The parts of a Debug Adapter Protocol message that the X285, X304-X331 and X336-X340 cases check. */
interface DapMessage {
    seq?: number;
    request_seq?: number;
    type: string;
    event?: string;
    command?: string;
    success?: boolean;
    message?: string;
    arguments?: { expression?: string; threadId?: number; startFrame?: number };
    body?: {
        areas?: string[];
        exitCode?: number;
        output?: string;
        reason?: string;
        description?: string;
        text?: string;
        threadId?: number;
        breakpoint?: { verified: boolean; line?: number };
        breakpoints?: { verified: boolean; line?: number; message?: string }[];
        stackFrames?: DapFrame[];
        sources?: { path?: string }[];
        totalFrames?: number;
    };
}

type DapBody = NonNullable<DapMessage['body']>;

/** A frame as a `stackTrace` response gives it. */
interface DapFrame {
    id: number;
    name: string;
    line: number;
    source?: { path?: string };
    canRestart?: boolean;
}

/** A scope as a `scopes` response gives it. */
interface DapScope {
    name: string;
    variablesReference: number;
}

/** A variable as a `variables` response gives it. */
interface DapVariable {
    name: string;
    value: string;
    type?: string;
    variablesReference: number;
    presentationHint?: { lazy?: boolean };
    indexedVariables?: number;
}

/** Console input for embedded runs; registered after the adapter chooser to keep catalog order. */
export function executionInputCases(): void {
    playbook('X287', async (workspace, data) => {
        const document = await workspace.module(data.file, data.source, data.module);
        await embedded(async executions => {
            const prompted = (update: ExecutionUpdate) => update.text.includes(data.prompt) && !finished(update);
            await executions.task(data.module, document.uri, 'Program prompts for input', { until: prompted });
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

/** Builds and runs [data]'s module in Gradle mode, and checks that the wrapper was asked to build, then to run it. */
async function gradleBuildAndRun(document: vscode.TextDocument, data: { module: string; gradleBuild: string; gradleRun: string },
    invocations: () => Promise<string[]>): Promise<void> {
    assert.strictEqual(await shellTaskExit('Build', () => vscode.commands.executeCommand('xtc.build')), 0, 'Gradle build exit code');
    assert.deepStrictEqual(await invocations(), [data.gradleBuild], 'Build calls the Gradle wrapper');
    assert.strictEqual(await shellTaskExit(`Run ${data.module}`,
        () => vscode.commands.executeCommand('xtc.runModule', document.uri.toString(), data.module)), 0, 'Gradle run exit code');
    const calls = await invocations();
    assert.strictEqual(calls.length, 2, calls.join('\n'));
    assert.ok(calls[1].includes(data.gradleRun), calls.join('\n'));
}

/** Build modes beside a Gradle build and repeated runs; registered after console input to keep catalog order. */
export function buildModeCases(): void {
    playbook('X288', (workspace, data) => withGradleWrapper(data, (folder, invocations) => executing(async executions => {
        await vscode.workspace.getConfiguration('xtc').update('build.mode', undefined, vscode.ConfigurationTarget.Workspace);
        const document = await workspace.module(data.file, data.source, data.module);

        // the default is embedded, also beside a Gradle build, and says which of the build's settings apply
        assert.strictEqual(buildMode(folder), 'embedded');
        await eventually(async () => buildModeStatus(), text => text === data.statusEmbedded, 'Status bar shows the embedded build');
        const built = await executions.build('Embedded build finishes');
        assert.strictEqual(built.exitCode, 0, built.text);
        assert.ok(built.text.includes(data.notice), built.text);
        const run = await executions.lens(data.module, document.uri, 'Embedded run finishes');
        assert.strictEqual(run.exitCode, 0, run.text);
        assert.ok(run.text.includes(data.output), run.text);
        assert.deepStrictEqual(await invocations(), [], 'Embedded mode does not call Gradle');

        // Gradle, chosen the way the command and the status bar choose it
        await selectBuildMode(folder, 'gradle', data.statusGradle, 'Status bar shows the Gradle build');
        await gradleBuildAndRun(document, data, invocations);

        // and back: Run is embedded again
        await selectBuildMode(folder, 'embedded', data.statusEmbedded, 'Status bar shows the embedded build again');
        const again = await executions.lens(data.module, document.uri, 'Embedded run after switching back finishes');
        assert.strictEqual(again.exitCode, 0, again.text);
        assert.ok(again.text.includes(data.output), again.text);
        assert.strictEqual((await invocations()).length, 2, 'No Gradle call after switching back');
    })));

    playbook('X289', async (workspace, data) => {
        const document = await workspace.module(data.file, data.source, data.module);
        await embedded(async executions => {
            const runOnce = (message: string) => executions.exits(data.module, document.uri, data.exitCode, message);
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

/** X354, registered after X353 in catalog order: Build and Run in the automatic build mode, beside a Gradle build and without one. */
export function automaticBuildModeCases(): void {
    playbook('X354', (workspace, data) => withGradleWrapper(data, (folder, invocations) => executing(async executions => {
        // automatic: Gradle while the folder has a Gradle build
        await selectBuildMode(folder, 'auto', data.statusGradle, 'Status bar shows the Gradle build');
        const document = await workspace.module(data.file, data.source, data.module);
        assert.strictEqual(buildMode(folder), 'gradle');
        await gradleBuildAndRun(document, data, invocations);

        // and the embedded compiler once the folder has none, with no choice made again
        await fs.rm(path.join(folder.uri.fsPath, data.gradleFile));
        assert.strictEqual(buildMode(folder), 'embedded');
        const run = await executions.lens(data.module, document.uri, 'Embedded run without a Gradle build finishes');
        assert.strictEqual(run.exitCode, 0, run.text);
        assert.ok(run.text.includes(data.output), run.text);
        assert.strictEqual((await invocations()).length, 2, 'No Gradle call without a Gradle build');
    })));
}

/** A real Gradle build starts its own daemon, so its tasks get longer than the usual wait. */
const GRADLE_WAIT_MS = 120_000;

const exists = (file: string) => fs.access(file).then(() => true, () => false);

/** The values of a case whose project xtc init makes at the folder root: X303 and X337. */
interface GradleProjectValues {
    project: string;
    rootEntries: readonly string[];
    sharedDirectories: readonly string[];
    catalog: string;
    versionPattern: string;
    mavenLocal: readonly string[];
    statusGradle: string;
}

/**
 * Makes a project with xtc init, which Ecstasy: Create Project runs, at the folder root, where Gradle mode runs
 * ./gradlew, runs [body] with it, and removes it again, with the folder's build mode as it was. Its build takes the
 * XTC Gradle plugin and XDK last published to mavenLocal.
 */
async function withGradleProject(id: string, data: GradleProjectValues,
    body: (folder: vscode.WorkspaceFolder, root: (file: string) => string) => Promise<void>): Promise<void> {
    const folder = vscode.workspace.workspaceFolders?.[0];
    assert.ok(folder, 'The playbook opens a folder');
    // Gradle mode runs ./gradlew at the folder root, so the project goes there; nothing of it may be there yet,
    // apart from the build directory, which embedded builds share
    const root = (file: string) => path.join(folder.uri.fsPath, file);
    for (const entry of data.rootEntries) assert.ok(!await exists(root(entry)), `${id} needs a folder root without ${entry}`);
    const xtc = path.resolve(__dirname, '../../../../../xdk/build/install/xdk/bin', process.platform === 'win32' ? 'xtc.bat' : 'xtc');
    assert.ok(await exists(xtc), `${id} makes its project with ${xtc} init; run ./gradlew xdk:installDist first`);
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
            assert.ok(await exists(published), `${id} builds with ${artifact} ${version} from mavenLocal; run ./gradlew publishLocal first`);
        }
        await body(folder, root);
    } finally {
        await settings.update('build.mode', original, vscode.ConfigurationTarget.Workspace);
        await Promise.all(data.rootEntries.map(entry => fs.rm(root(entry), { recursive: true, force: true })));
        // only when nothing else, such as embedded build output, is left in them
        for (const directory of data.sharedDirectories) await fs.rmdir(root(directory)).catch(() => undefined);
        await fs.rm(generated, { recursive: true, force: true });
    }
}

/** X303, registered after X302 in catalog order: a project made by xtc init, which Ecstasy: Create Project runs. */
export function gradleProjectCases(): void {
    playbook('X303', async (_workspace, data) => {
        await withGradleProject('X303', data, async (folder, root) => {
            await selectBuildMode(folder, 'gradle', data.statusGradle, 'Status bar shows the Gradle build');
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
        });
    }, 360_000);
}

/**
 * X337, registered after X336 in catalog order: Debug in Gradle mode, in a project made as X303 makes it, runs the
 * module's Gradle task under the debugger, whose program connects to the debug session.
 */
export function gradleDebugCases(): void {
    playbook('X337', async (_workspace, data) => {
        await withGradleProject('X337', data, async (folder, root) => {
            const module = vscode.Uri.file(root(data.moduleFile));
            await fs.writeFile(module.fsPath, data.source);
            await selectBuildMode(folder, 'gradle', data.statusGradle, 'Status bar shows the Gradle build');
            // the Gradle task ends with its program
            const exit = await shellTaskExit(`Debug ${data.module}`, () => debugging(async debug => {
                debug.add(lineBreakpoint(module, data.breakpointLine));
                await debug.launch(module, data.module);
                await debug.stopped('Gradle runs the program, which stops at the breakpoint', 0, GRADLE_WAIT_MS);
                await debug.atBreakpoint(module, data.breakpointLine, data.frames);
                await debug.continueToExit(data.exitCode, 'Continue runs the program to its exit, which ends the session');
            }), GRADLE_WAIT_MS);
            assert.strictEqual(exit, 0, 'Gradle task exit code');
        });
    }, 360_000);
}

/**
 * X338, registered after X337 in catalog order: VS Code has no command-line build mode, which runs a debugged program
 * with xtc run, so a launch that asks for one fails, with VS Code's reason, instead of running a Gradle task.
 */
export function commandLineDebugCases(): void {
    playbook('X338', async (workspace, data) => {
        const document = await workspace.module(data.file, data.source, data.module);
        const tasks: string[] = [];
        const listener = vscode.tasks.onDidStartTask(event => { tasks.push(event.execution.task.name); });
        try {
            await debugging(async debug => {
                // VS Code shows a failed launch in a dialog, which its test host refuses, so the start fails either way
                const start = await Promise.resolve(vscode.debug.startDebugging(vscode.workspace.getWorkspaceFolder(document.uri),
                    launchOf(data.module, { buildMode: 'commandLine' }))).then(value => String(value), (error: Error) => error.message);
                assert.ok(start === 'false' || start.includes(data.refused), start);
                const launch = await eventually(async () => debug.responses('launch')[0], value => value !== undefined, 'The command-line launch answers');
                assert.strictEqual(launch?.success, false, JSON.stringify(launch));
                assert.ok(launch?.message?.includes(data.refused), JSON.stringify(launch));
                assert.strictEqual(debug.events('stopped').length, 0, debug.transcript());
                assert.deepStrictEqual(tasks, [], 'A command-line launch runs no task');
            });
        } finally {
            listener.dispose();
        }
    });
}

/** What the Test Explorer listed, started and reported while a case watched it, until [dispose]. */
export class TestWatch implements vscode.Disposable {
    readonly reports: TestReport[] = [];
    readonly started: string[] = [];
    listed: readonly string[] = [];
    private readonly subscriptions = [
        onDidReportTest(report => this.reports.push(report)),
        onDidStartTest(id => this.started.push(id)),
        onDidRefreshTests(ids => { this.listed = ids; }),
    ];

    /** The latest report of the test [id]. */
    result(id: string): TestReport | undefined {
        return this.reports.filter(report => report.id === id).pop();
    }

    /** Waits until the Test Explorer lists each of [ids], asking it to list the tests again. */
    async listing(ids: readonly string[], message = 'The Test Explorer lists the module\'s tests', timeoutMs?: number): Promise<void> {
        await eventually(async () => {
            await vscode.commands.executeCommand('testing.refreshTests');
            return this.listed;
        }, listed => ids.every(id => listed.includes(id)), message, timeoutMs);
    }

    /** Waits until each of [ids] has a report, and answers the latest of each. */
    async results(ids: readonly string[], what: string, timeoutMs?: number): Promise<TestReport[]> {
        return eventually(async () => ids.map(id => this.result(id)!), reports => reports.every(Boolean), what, timeoutMs);
    }

    /** Waits until [data]'s tests are reported, and checks that the passed ones passed, and the failed one failed where and as it says. */
    async checkRun(data: { passed: readonly string[]; failed: string; failure: string; failureLine: number }, what: string, timeoutMs?: number): Promise<void> {
        const [failure, ...passed] = await this.results([data.failed, ...data.passed], what, timeoutMs);
        for (const report of passed) assert.strictEqual(report.state, 'passed', JSON.stringify(this.reports));
        assert.strictEqual(failure.state, 'failed', JSON.stringify(this.reports));
        assert.strictEqual(failure.message, data.failure);
        assert.strictEqual(failure.line, data.failureLine);
    }

    /**
     * Debugs the test whose name is at [data]'s line and column of [editor], as Debug Test at Cursor does, to the breakpoint on
     * [data]'s line, and on to its end; checks that it alone ran, and passed.
     */
    async debugAtCursor(editor: vscode.TextEditor, data: { debugged: string; nameLine: number; nameColumn: number; breakpointLine: number; printed: string },
        stopMessage: string, timeoutMs?: number): Promise<void> {
        this.reports.length = 0;
        await debugging(async debug => {
            debug.add(lineBreakpoint(editor.document.uri, data.breakpointLine));
            select(editor, data.nameLine, data.nameColumn);
            // the command answers once its test run has ended, which waits for the session that the breakpoint stops
            const running = vscode.commands.executeCommand('testing.debugAtCursor');
            assert.strictEqual((await debug.stopped(stopMessage, 0, timeoutMs)).reason, 'breakpoint');
            const frames = await debug.shownFrames(1, 'VS Code shows the call stack');
            assert.ok(frames[0].name.endsWith('works'), JSON.stringify(frames[0]));
            assert.strictEqual(frames[0].line, data.breakpointLine);

            await vscode.commands.executeCommand('workbench.action.debug.continue');
            await debug.end('Continue runs the test to its end', timeoutMs);
            await running;
            await eventually(async () => this.result(data.debugged)?.state, state => state === 'passed', 'The debugged test passes');
            debug.printed(data.printed);
            assert.deepStrictEqual(this.reports.map(report => report.id), [data.debugged], 'Debug Test at Cursor runs that test alone');
        });
    }

    dispose(): void {
        this.subscriptions.forEach(each => each.dispose());
    }
}

/** Runs [body] with a [TestWatch], which stops watching after it. */
export async function watchTests(body: (tests: TestWatch) => Promise<void>): Promise<void> {
    const tests = new TestWatch();
    try {
        await body(tests);
    } finally {
        tests.dispose();
    }
}

/**
 * X339, registered after X338 in catalog order: the Test Explorer lists the module's tests, Run All marks each passed
 * or failed, and Debug Test at Cursor stops at a breakpoint in the test and runs it to its end.
 */
export function testExplorerCases(): void {
    playbook('X339', async (workspace, data) => {
        const document = await workspace.module(data.file, data.source, data.module);
        await watchTests(async tests => {
            await tests.listing(data.items);
            await vscode.commands.executeCommand('testing.runAll');
            await tests.checkRun(data, 'Run All reports every test');
            await tests.debugAtCursor(await vscode.window.showTextDocument(document), data, 'The debugged test stops at the breakpoint');
        });
    });
}

/** The values of X340, whose project is made as X337's: its test module's tests and where they are. */
interface GradleTestValues extends GradleProjectValues {
    module: string;
    mainModule: string;
    mainFile: string;
    testFile: string;
    testSource: string;
    items: string[];
    passed: string[];
    failed: string;
    failure: string;
    failureLine: number;
    classLine: number;
    classColumn: number;
    debugged: string;
    nameLine: number;
    nameColumn: number;
    breakpointLine: number;
    printed: string;
}

/**
 * X340, registered after X339 in catalog order: in Gradle mode, the Test Explorer runs the tests of a project's test
 * module with its testXtc, and Debug Test at Cursor has testXtc run one under the debugger.
 */
export function gradleTestCases(): void {
    playbook('X340', async (workspace, raw) => {
        const data = raw as unknown as GradleTestValues;
        await withGradleProject('X340', data, async (folder, root) => {
            const file = vscode.Uri.file(root(data.testFile));
            await fs.writeFile(file.fsPath, data.testSource);
            // the playbook's graph is explicit, so the project's modules join it as the test source set has them
            await workspace.configure([
                { name: data.mainModule, uri: vscode.Uri.file(root(data.mainFile)).toString() },
                { name: data.module, uri: file.toString(), dependencies: [data.mainModule] },
            ]);
            await selectBuildMode(folder, 'gradle', data.statusGradle, 'Status bar shows the Gradle build');
            const editor = await vscode.window.showTextDocument(await vscode.workspace.openTextDocument(file));
            const tests = new TestWatch();
            try {
                await tests.listing(data.items, 'The Test Explorer lists the test module\'s tests', GRADLE_WAIT_MS);
                select(editor, data.classLine, data.classColumn);
                await vscode.commands.executeCommand('testing.runAtCursor');
                await tests.checkRun(data, 'testXtc runs the class\'s tests', GRADLE_WAIT_MS);
                await tests.debugAtCursor(editor, data, 'testXtc runs the test under the debugger, which stops at the breakpoint', GRADLE_WAIT_MS);
            } finally {
                tests.dispose();
                await vscode.commands.executeCommand('workbench.action.closeActiveEditor');
            }
        });
    });
}

/**
 * X336, registered after X335 in catalog order: a debug session attaches to a program that the XDK's xec runs with
 * --debug outside the editor, through the agent that the XDK's javatools has.
 */
export function attachCases(): void {
    playbook('X336', async (workspace, data) => {
        const document = await workspace.module(data.file, data.source, data.module);
        const xec = path.resolve(__dirname, '../../../../../xdk/build/install/xdk/bin', process.platform === 'win32' ? 'xec.bat' : 'xec');
        assert.ok(await exists(xec), `X336 runs its program with ${xec}; run ./gradlew xdk:installDist first`);
        const output = await fs.mkdtemp(path.join(os.tmpdir(), 'xtc-attach-'));
        const program = spawn(xec, ['-o', output, '--debug', 'listen:127.0.0.1:0,suspend', document.uri.fsPath],
            { shell: process.platform === 'win32' });
        let printed = '';
        program.stdout.on('data', (chunk: Buffer) => { printed += chunk.toString('utf8'); });
        program.stderr.on('data', (chunk: Buffer) => { printed += chunk.toString('utf8'); });
        let exitCode: number | null | undefined;
        program.on('exit', code => { exitCode = code; });
        try {
            await debugging(async debug => {
                debug.add(lineBreakpoint(document.uri, data.breakpointLine));
                const listening = new RegExp(data.listening);
                const port = Number(await eventually(async () => listening.exec(printed)?.[1], value => value !== undefined,
                    'The program listens for the debugger'));
                assert.ok(!printed.includes(data.before), `The program waits for the debugger: ${printed}`);
                assert.ok(await vscode.debug.startDebugging(vscode.workspace.getWorkspaceFolder(document.uri),
                    { type: 'xtc', request: 'attach', name: `Attach to ${data.module}`, port }));
                await debug.stopped('The program stops at the breakpoint');
                await debug.atBreakpoint(document.uri, data.breakpointLine, data.frames);
                assert.ok(printed.includes(data.before) && !printed.includes(data.after), printed);

                await debug.continueToExit(data.exitCode, 'Continue runs the program to its exit, which ends the session');
                await eventually(async () => exitCode, code => code !== undefined, 'The program ends');
                assert.strictEqual(exitCode, data.exitCode, printed);
                assert.ok(printed.includes(data.after), printed);
            });
        } finally {
            program.kill();
            await fs.rm(output, { recursive: true, force: true });
        }
    });
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
                    const text = await executions.exits(data.module, workspace.uri(data.file), data.exitCode, message);
                    assert.ok(text.includes(data.output), text);
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
        const document = await workspace.module(data.file, data.source, data.module);
        await embedded(async executions => {
            for (const message of ['First run finishes', 'Second run finishes']) {
                const text = await executions.exits(data.module, document.uri, data.exitCode, message);
                // the counter keeps its count within a run, and a run that went on from the run before it would
                // print the counts after that run's
                assert.ok(text.includes(data.output), text);
                assert.ok(!text.includes(data.carriedOver), text);
            }
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
            const refused = async (module: string, uri: vscode.Uri, message: string, expected: string) => {
                const text = await executions.exits(module, uri, data.exitCode, message);
                assert.ok(text.includes(expected), text);
                // the reason alone, as xtc run reports it, not a failure to create the program's container
                assert.ok(!text.includes(data.containerFailure), text);
                return text;
            };
            await refused(data.module, library.uri, 'Run of a module without run is refused', data.message);
            const text = await refused(data.twoArgumentsModule, twoArguments.uri, 'Run of a run method requiring two arguments is refused',
                data.twoArgumentsMessage);
            assert.ok(!text.includes(data.twoArgumentsOutput), `Nothing ran: ${text}`);
        });
    });

    playbook('X294', async (workspace, data) => {
        const document = await workspace.module(data.file, data.source, data.module);
        await embedded(async executions => {
            const text = await executions.exits(data.module, document.uri, data.exitCode, 'Run with dropped arguments finishes', data.args);
            assert.ok(text.includes(data.warning), text);
            assert.ok(text.includes(data.output), text);
            assert.ok(text.indexOf(data.warning) < text.indexOf(data.output), `The warning comes before the program's output: ${text}`);
        });
    });
}

/**
 * X351, registered after X350 in catalog order: an embedded run in a folder whose Gradle model is imported takes the
 * method, arguments and JVM options of the module's xtcRun from the model, says so in its output and leaves out a Java
 * debugger's agent; the run's own arguments win.
 */
export function gradleRunCases(): void {
    playbook('X351', (workspace, data) => withGradleModel(async report => {
        const document = await workspace.open(data.file, data.source);
        // the model that an import writes, which the extension gives the language server without explicit modules
        await fs.mkdir(path.dirname(report.fsPath), { recursive: true });
        await fs.writeFile(report.fsPath, gradleModel(workspace, data.model));
        await vscode.workspace.getConfiguration('xtc.compiler').update('sourceModules', null, vscode.ConfigurationTarget.Workspace);
        await updateCompilerConfiguration();
        await eventually(async () => gradleProjects()[data.module], project => project === data.project, 'The model\'s Gradle projects');
        await noErrors(document.uri);
        await embedded(async executions => {
            // no method or arguments of its own: Gradle's, its JVM options but the debugger's agent, as it says
            const taken = await executions.exits(data.module, document.uri, 0, 'The embedded run with Gradle\'s settings finishes');
            for (const expected of [data.fromGradle, data.leftOut, data.output]) assert.ok(taken.includes(expected), taken);
            // its own arguments win, with Gradle's method
            const own = await executions.exits(data.module, document.uri, 0, 'The embedded run with its own arguments finishes', data.args);
            for (const expected of [data.ownFromGradle, data.ownOutput]) assert.ok(own.includes(expected), own);
            assert.ok(!own.includes(data.output), own);
        });
    }));
}
