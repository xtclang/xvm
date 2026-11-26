import * as vscode from 'vscode';
import { getClient } from '../../lsp-client';
import { createXtcRunTask } from '../../task-provider';
import { terminateTask } from '../../task-termination';
import { waitFor } from '../wait';

/** A program that prints a line and then waits for one, which only a stop ends. */
const WAITING = 'module Waiting {\n    void run() {\n        @Inject Console console;\n        console.print("waiting");\n        console.readLine();\n    }\n}\n';

suite('Run task stop', function () {
    // the server starts, and builds the module, before the run
    this.timeout(120_000);

    const file = () => vscode.Uri.joinPath(vscode.workspace.workspaceFolders![0].uri, 'Waiting.x');

    suiteSetup(async () => {
        await vscode.extensions.getExtension('xtclang.xtc-language')!.activate();
        await vscode.workspace.fs.writeFile(file(), Buffer.from(WAITING));
        await vscode.window.showTextDocument(await vscode.workspace.openTextDocument(file()));
        await waitFor(async () => getClient()?.isRunning() ?? false, Boolean, 'The language server runs', 60_000);
    });

    suiteTeardown(async () => {
        vscode.tasks.taskExecutions.forEach(terminateTask);
        await vscode.commands.executeCommand('workbench.action.closeAllEditors');
        await vscode.workspace.fs.delete(file()).then(undefined, () => undefined);
    });

    /** Starts Waiting's embedded run task, and answers its execution, and the end of the task, once it ends. */
    async function start(): Promise<{ execution: vscode.TaskExecution; ended: Promise<void> }> {
        const task = createXtcRunTask({ type: 'xtc', moduleName: 'Waiting', moduleUri: file().toString(), mode: 'embedded', quietMode: true },
            getClient);
        let ended!: () => void;
        const end = new Promise<void>(resolve => { ended = resolve; });
        const listening = vscode.tasks.onDidEndTask(event => {
            if (event.execution.task === task) {
                listening.dispose();
                ended();
            }
        });
        return { execution: await vscode.tasks.executeTask(task), ended: end };
    }

    /** The runs that the server tracks, which a stopped run leaves. */
    const tracked = () => getClient()!.sendRequest<{ embeddedRuns: number }>('xtc/languageServiceStatus').then(status => status.embeddedRuns);

    test('a run task terminated as soon as executeTask answers ends, and the server stops its program', async () => {
        const { execution, ended } = await start();
        // before the task has started, where VS Code drops TaskExecution.terminate()
        terminateTask(execution);
        await ended;
        await waitFor(tracked, runs => runs === 0, 'The server holds no run');
    });

    test('a run task terminated while its program waits for input ends, and the server stops its program', async () => {
        const { execution, ended } = await start();
        await waitFor(tracked, runs => runs > 0, 'The program runs');
        terminateTask(execution);
        await ended;
        await waitFor(tracked, runs => runs === 0, 'The server holds no run');
    });
});
