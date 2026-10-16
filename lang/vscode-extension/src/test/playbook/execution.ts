import * as assert from 'node:assert';
import * as vscode from 'vscode';
import { onDidUpdateExecution, type ExecutionUpdate } from '../../embedded-execution';
import { getClient } from '../../lsp-client';
import { createXtcRunTask } from '../../task-provider';
import { client, eventually, noErrors, playbook, type Workspace } from './support';

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
            const failed = await executions.start('build', label, () => vscode.commands.executeCommand('xtc.build'), finished, 'Failing build finishes');
            assert.ok(!library.isDirty, 'Build saves all files first');
            assert.strictEqual(failed.exitCode, 1, failed.text);
            assert.ok(failed.text.includes(data.failed), failed.text);
            assert.ok(failed.text.includes(data.skipped), failed.text);
            assert.ok(failed.text.split('\n').some(line => line.includes(data.diagnostic) && line.includes(': error: ')), failed.text);
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
        await embedded(async executions => {
            let sessions = 0;
            const subscription = vscode.debug.onDidStartDebugSession(() => sessions++);
            try {
                const run = await executions.start('run', data.module, async () => {
                    const started = await vscode.debug.startDebugging(vscode.workspace.getWorkspaceFolder(document.uri),
                        { type: 'xtc', request: 'launch', name: `Debug ${data.module}`, module: data.module });
                    assert.strictEqual(started, false, 'A launch without a debugger must not start a debug session');
                }, finished, 'Debug launch runs the module');
                assert.strictEqual(run.exitCode, data.exitCode, run.text);
                assert.strictEqual(sessions, 0, 'No debug session started');
            } finally {
                subscription.dispose();
            }
        });
    });
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
