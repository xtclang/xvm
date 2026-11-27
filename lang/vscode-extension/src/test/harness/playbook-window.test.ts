import * as assert from 'node:assert';
import { spawn } from 'node:child_process';
import * as path from 'node:path';
import * as vscode from 'vscode';
import { State, type LanguageClient } from 'vscode-languageclient/node';
import { terminateTask } from '../../task-termination';
import { waitsUnderWay } from '../progress';
import { recordSettings, restoreSettings } from '../settings';
import { answered, Happenings, leftOver, playbookWork, taskTerminal, untilTakes, whileUnderWay } from '../uiWait';
import { PlaybookFinal, progress, QUIET_MS, runCase } from '../wait';

/**
 * Whether the harness runs in the tour's window, before the tour's first step: the tests here act on the window, its
 * settings, tasks and language server, which the tour must find as it would without them, so they run in the
 * playbook's window and in the harness's own only.
 */
const tourWindow = process.env.XTC_TOUR_REPORT_DIR !== undefined;

/**
 * A test that acts on the window, and so waits on VS Code: it goes on while it progresses, as a playbook case does
 * ([runCase]), with no bound of Mocha's, whose 10 s a loaded machine outlasts.
 */
function windowTest(title: string, body: () => Promise<void>): void {
    test(title, async function () {
        this.timeout(0);
        const failure = await runCase(body, line => progress(`the harness's "${title}" ${line}`), waitsUnderWay, playbookWork, async () => undefined);
        if (failure !== undefined) throw failure;
    });
}

/** A record of what happened whose clock, terminal, client and starting server the test gives it. */
function record(sources: ConstructorParameters<typeof Happenings>[0] = {}): Happenings {
    return new Happenings({ terminal: async () => undefined, language: () => undefined, ...sources });
}

if (!tourWindow) suite("What the playbook's waits count as under way, in the window", () => {
    windowTest('what happened counts for a minute, a response is nothing that happened, and an event is', async () => {
        let clock = 0;
        const happenings = record({ now: () => clock });
        const recorded = await recordSettings();
        try {
            clock = 1_000;
            const changed = new Promise<void>(resolve => { const once = vscode.workspace.onDidChangeConfiguration(() => { once.dispose(); resolve(); }); });
            const count = happenings.happenings();
            await vscode.workspace.getConfiguration('editor').update('wordWrapColumn', 76, vscode.ConfigurationTarget.Workspace);
            await changed;
            assert.ok(happenings.happenings() > count, 'A change of the settings happened');
            assert.ok(happenings.underWay().length > 0, 'and is under way');
            // a minute later, with nothing since, nothing is
            clock += QUIET_MS;
            assert.deepStrictEqual(happenings.underWay(), []);
            const before = happenings.happenings();
            happenings.fromAdapter('session', { type: 'response', request_seq: 1, command: 'stackTrace' });
            assert.strictEqual(happenings.happenings(), before, 'A response is the end of a request, not something that happened');
            happenings.fromAdapter('session', { type: 'event', event: 'output' });
            assert.strictEqual(happenings.happenings(), before + 1, "An event, as the program's output, is");
        } finally {
            happenings.dispose();
            await restoreSettings(recorded);
        }
    });

    windowTest("an adapter's request is under way until its response, but a launch, which waits for its program, is not", async () => {
        // a minute after the record began, with nothing since
        let clock = 0;
        const happenings = record({ now: () => clock });
        clock = QUIET_MS * 2;
        try {
            happenings.toAdapter('session', { type: 'request', seq: 7, command: 'stackTrace' });
            happenings.toAdapter('session', { type: 'request', seq: 8, command: 'launch' });
            assert.deepStrictEqual(happenings.underWay(), ["the debug adapter's stackTrace, which has not answered"]);
            happenings.fromAdapter('session', { type: 'response', request_seq: 7, command: 'stackTrace' });
            assert.deepStrictEqual(happenings.underWay(), []);
        } finally {
            happenings.dispose();
        }
    });

    windowTest("an adapter's request counts for the case that it came in, and is left over for later ones", async () => {
        let clock = 0;
        const happenings = record({ now: () => clock });
        clock = QUIET_MS * 2;
        try {
            let during: string[] = [];
            await runCase(async () => {
                happenings.toAdapter('session', { type: 'request', seq: 9, command: 'evaluate' });
                during = happenings.underWay();
            }, () => undefined, () => [], () => ['the case'], async () => undefined);
            assert.deepStrictEqual(during, ["the debug adapter's evaluate, which has not answered"]);
            assert.deepStrictEqual(happenings.underWay(), [], 'No later one counts it');
            assert.deepStrictEqual(happenings.leftOver(), ["the debug adapter's evaluate"]);
            assert.deepStrictEqual(happenings.leftOver(), [], 'Reported once');
        } finally {
            happenings.dispose();
        }
    });

    windowTest("a process's task counts by what its terminal prints, without Gradle's clock", async () => {
        const reads: ({ tasks: string[]; text: string } | undefined)[] = [
            { tasks: ['Build'], text: '<====---> 50% EXECUTING [1s]\n> :compileXtc' },
            { tasks: ['Build'], text: '<====---> 50% EXECUTING [2s]\n> :compileXtc' },
            { tasks: ['Build'], text: '<=====--> 60% EXECUTING [3s]\n> :runXtc' },
        ];
        const happenings = record({ terminal: async () => reads.shift() });
        try {
            await happenings.look();
            const printed = happenings.happenings();
            await happenings.look();
            assert.strictEqual(happenings.happenings(), printed, 'Only the clock counted on');
            await happenings.look();
            assert.strictEqual(happenings.happenings(), printed + 1);
        } finally {
            happenings.dispose();
        }
    });

    windowTest('a starting server counts while what it grows grows, and its states as they come', async () => {
        const client = { state: State.Starting } as unknown as LanguageClient;
        const grown = [10, 10, 20];
        const happenings = record({ language: () => client, starting: async () => grown.shift() ?? 20 });
        try {
            await happenings.look();
            const started = happenings.happenings();
            assert.ok(started > 0, 'The client that starts is something that happened');
            await happenings.look();
            assert.strictEqual(happenings.happenings(), started, 'A server that grows nothing is no work');
            await happenings.look();
            assert.strictEqual(happenings.happenings(), started + 1, 'One that grows is');
        } finally {
            happenings.dispose();
        }
    });

    windowTest("a case's own process counts while it prints, computes and as it ends", async () => {
        const happenings = record();
        try {
            const printing = spawn('/bin/sh', ['-c', 'echo listening']);
            const before = happenings.happenings();
            happenings.watch(printing, 'a process of the harness');
            await new Promise(resolve => printing.on('close', resolve));
            assert.ok(happenings.happenings() >= before + 2, 'Its output and its end happened');
            if (process.platform === 'linux') {
                // a silent one that computes, as a JVM that starts and compiles, until its time has grown at a look
                const computing = spawn('/bin/sh', ['-c', 'while :; do :; done']);
                try {
                    happenings.watch(computing, 'a silent process of the harness');
                    const counted = happenings.happenings();
                    await whileUnderWay(async () => {
                        await happenings.look();
                        return happenings.happenings();
                    }, count => count > counted, "The silent process's time counts");
                } finally {
                    computing.kill();
                }
            }
        } finally {
            happenings.dispose();
        }
    });

    windowTest('a playbook wait goes on while something happened within the minute, and is final once nothing has', async () => {
        let clock = 0;
        const happenings = record({ now: () => clock });
        try {
            let reads = 0;
            happenings.happened('an edit of the harness');
            await assert.rejects(whileUnderWay(async () => {
                // the minute passes at the fifth read
                if (++reads === 5) clock += QUIET_MS;
                return 'not yet';
            }, value => value === 'done', 'The harness waits', { seen: happenings, server: async () => [] }),
            error => error instanceof PlaybookFinal);
            assert.ok(reads >= 7, `${reads} reads: it went on while the edit was within the minute, and three after`);
            // and the server's work counts once nothing else does
            let asked = 0;
            const work = await playbookWork(happenings, async () => [`the server's compiles, asked ${++asked} times`]);
            assert.deepStrictEqual(work, ["the server's compiles, asked 1 times"]);
        } finally {
            happenings.dispose();
        }
    });

    windowTest("a process's task's terminal is read through the window's DevTools", async () => {
        const folder = vscode.workspace.workspaceFolders![0];
        const name = 'Harness terminal';
        const execution = await vscode.tasks.executeTask(new vscode.Task({ type: 'harness' }, folder, name, 'harness',
            new vscode.ShellExecution("printf 'the harness reads %s\\n' this; read line")));
        try {
            const shown = await whileUnderWay(() => taskTerminal(), shows => !!shows?.text.includes('the harness reads this'),
                "The task's terminal shows its line");
            assert.deepStrictEqual(shown?.tasks, [name]);
        } finally {
            terminateTask(execution);
            await whileUnderWay(async () => vscode.tasks.taskExecutions.includes(execution), running => !running, 'The task ends');
            vscode.window.terminals.filter(terminal => terminal.name.includes(name)).forEach(terminal => terminal.dispose());
            await vscode.commands.executeCommand('workbench.action.closePanel');
            // and its connection closes, with no task to read
            assert.strictEqual(await taskTerminal(), undefined);
        }
    });

    windowTest("a call that a case's try left unanswered counts for no later case, and is reported once", async () => {
        let answer: () => void = () => undefined;
        const call = new Promise<void>(resolve => { answer = resolve; });
        // the case's own look finds its try under way, as the case's call
        await runCase(async () => {
            await answered(call, 'Showing the document').catch(() => undefined);
        }, () => undefined, () => [], () => ['a try'], async () => undefined);
        // a later case's try that never takes is final, as if the call were not there: it is not its own
        const later = await runCase(() => untilTakes('The box shows', async () => { throw new Error('not yet'); }, { recent: false }),
            () => undefined, () => [], () => [], async () => undefined);
        assert.ok(later instanceof PlaybookFinal, String(later));
        assert.ok(leftOver().includes('Showing the document'), 'Reported once its case has ended');
        assert.ok(!leftOver().includes('Showing the document'), 'and only once');
        answer();
    });

    windowTest("a case's cleanup puts back a language's block and a folder's setting, setting by setting", async () => {
        const recorded = await recordSettings();
        const folder = vscode.workspace.workspaceFolders![0];
        const tabSize = () => vscode.workspace.getConfiguration('editor', { languageId: 'xtc' }).inspect<number>('tabSize');
        const column = () => vscode.workspace.getConfiguration('editor', folder.uri).inspect<number>('wordWrapColumn');
        const before = { tabSize: tabSize()?.workspaceLanguageValue, column: column()?.workspaceFolderValue };
        try {
            await vscode.workspace.getConfiguration('editor', { languageId: 'xtc' }).update('tabSize', 3, vscode.ConfigurationTarget.Workspace, true);
            await vscode.workspace.getConfiguration('editor', folder.uri).update('wordWrapColumn', 73, vscode.ConfigurationTarget.WorkspaceFolder);
            const restored = await restoreSettings(recorded);
            assert.ok(restored.includes('[xtc].editor.tabSize') && restored.includes('editor.wordWrapColumn'), JSON.stringify(restored));
            assert.strictEqual(tabSize()?.workspaceLanguageValue, before.tabSize);
            assert.strictEqual(column()?.workspaceFolderValue, before.column);
            assert.deepStrictEqual(await restoreSettings(recorded), [], 'Nothing is left to put back');
        } finally {
            await restoreSettings(recorded);
        }
    });

    windowTest("a restore that puts back a setting of the server's runtime, or finds one changed by a failed case, restarts the server", async () => {
        const recorded = await recordSettings();
        const heap = vscode.workspace.getConfiguration('xtc').inspect<string>('server.maxHeap')?.globalValue;
        let restarts = 0;
        const restart = async () => { restarts++; };
        try {
            // the extension only offers to restart: the cleanup does, and dismisses the offer
            await vscode.workspace.getConfiguration('xtc').update('server.maxHeap', '2G', vscode.ConfigurationTarget.Global);
            assert.deepStrictEqual(await restoreSettings(recorded, { restart }), ['xtc.server.maxHeap']);
            assert.strictEqual(restarts, 1);
            // a setting that applies at once needs none
            await vscode.workspace.getConfiguration('editor').update('wordWrapColumn', 75, vscode.ConfigurationTarget.Workspace);
            await restoreSettings(recorded, { restart });
            assert.strictEqual(restarts, 1);
            // a case that changed one and put it back, but failed before its own restart
            const again = await recordSettings();
            await vscode.workspace.getConfiguration('xtc').update('server.maxHeap', '2G', vscode.ConfigurationTarget.Global);
            await vscode.workspace.getConfiguration('xtc').update('server.maxHeap', heap, vscode.ConfigurationTarget.Global);
            assert.deepStrictEqual(await restoreSettings(again, { restart }), []);
            assert.strictEqual(restarts, 1, 'A case that passed restarted the server itself');
            assert.deepStrictEqual(await restoreSettings(again, { restart, failed: true }), []);
            assert.strictEqual(restarts, 2);
        } finally {
            await restoreSettings(recorded);
            await vscode.commands.executeCommand('notifications.clearAll');
        }
    });

    windowTest("a case's cleanup puts back every setting the case changed, as it found it", async () => {
        const recorded = await recordSettings();
        const editor = () => vscode.workspace.getConfiguration('editor').inspect<number>('wordWrapColumn');
        const before = editor();
        try {
            await vscode.workspace.getConfiguration('editor').update('wordWrapColumn', 71, vscode.ConfigurationTarget.Workspace);
            if (process.env.XTC_TEST_USER_SETTINGS) {
                await vscode.workspace.getConfiguration('editor').update('wordWrapColumn', 72, vscode.ConfigurationTarget.Global);
            }
            const restored = await restoreSettings(recorded);
            assert.deepStrictEqual(restored, ['editor.wordWrapColumn', ...(process.env.XTC_TEST_USER_SETTINGS ? ['editor.wordWrapColumn'] : [])].sort(),
                JSON.stringify(restored));
            assert.strictEqual(editor()?.workspaceValue, before?.workspaceValue);
            assert.strictEqual(editor()?.globalValue, before?.globalValue);
            assert.deepStrictEqual(await restoreSettings(recorded), [], 'Nothing is left to put back');
        } finally {
            await restoreSettings(recorded);
        }
    });

    windowTest("a case's cleanup ends its tasks, leaves the graph as the disposal empties it, puts back the rest, and restarts for the runtime's", async () => {
        const { cleanUp, serviceStatus, Workspace } = await import('../playbook/support.js');
        await vscode.extensions.all.find(item => item.packageJSON.name === 'xtc-language')?.activate();
        const pid = () => whileUnderWay(async () => (await serviceStatus<{ pid: number }>())?.pid, value => value !== undefined, 'The language server answers');
        const folder = vscode.workspace.workspaceFolders![0];
        const graph = () => vscode.workspace.getConfiguration('xtc.compiler').inspect<unknown[]>('sourceModules');
        const heap = () => vscode.workspace.getConfiguration('xtc').inspect<string>('server.maxHeap')?.globalValue;
        const recorded = await recordSettings();
        const was = heap();
        const before = await pid();
        try {
            const workspace = new Workspace(path.join(folder.uri.fsPath, 'harness-cleanup'));
            await workspace.configure([{ name: 'HarnessCleanup', uri: workspace.uri('HarnessCleanup.x').toString() }]);
            await vscode.workspace.getConfiguration('editor').update('wordWrapColumn', 77, vscode.ConfigurationTarget.Workspace);
            await vscode.workspace.getConfiguration('xtc').update('server.maxHeap', '2G', vscode.ConfigurationTarget.Global);
            // a task that the case left running, which waits for input
            const execution = await vscode.tasks.executeTask(new vscode.Task({ type: 'harness' }, folder, 'Harness leftover', 'harness',
                new vscode.ShellExecution('read line')));
            assert.deepStrictEqual(await cleanUp(workspace, recorded, false), []);
            assert.ok(!vscode.tasks.taskExecutions.includes(execution), 'The task that the case left running has ended');
            assert.deepStrictEqual(graph()?.workspaceValue ?? graph()?.workspaceFolderValue, [], 'The graph is as the disposal leaves it');
            assert.strictEqual(heap(), was, "The runtime's setting is as the case found it");
            assert.notStrictEqual(await pid(), before, "The server was restarted for the runtime's setting");
            vscode.window.terminals.filter(terminal => terminal.name.includes('Harness leftover')).forEach(terminal => terminal.dispose());
        } finally {
            await restoreSettings(recorded, { restart: async () => vscode.commands.executeCommand('xtc.restartServer') });
            await vscode.commands.executeCommand('workbench.action.closePanel');
        }
    });

    windowTest('a cleanup whose disposal failed puts back the graph too, and restarts the server', async () => {
        const { cleanUp, serviceStatus } = await import('../playbook/support.js');
        await vscode.extensions.all.find(item => item.packageJSON.name === 'xtc-language')?.activate();
        const pid = () => whileUnderWay(async () => (await serviceStatus<{ pid: number }>())?.pid, value => value !== undefined, 'The language server answers');
        const folder = vscode.workspace.workspaceFolders![0];
        const graph = () => vscode.workspace.getConfiguration('xtc.compiler').inspect<unknown[]>('sourceModules');
        const recorded = await recordSettings();
        const was = graph();
        const before = await pid();
        try {
            await vscode.workspace.getConfiguration('xtc.compiler').update('sourceModules',
                [{ name: 'HarnessCleanup', uri: vscode.Uri.file(path.join(folder.uri.fsPath, 'harness-cleanup', 'HarnessCleanup.x')).toString() }],
                vscode.ConfigurationTarget.Workspace);
            const failed = { directory: path.join(folder.uri.fsPath, 'harness-cleanup'), dispose: async () => { throw new Error('The disposal failed'); } };
            const failures = await cleanUp(failed, recorded, true);
            assert.ok(failures.length === 1 && String(failures[0]).includes('The disposal failed'), failures.map(String).join('; '));
            assert.deepStrictEqual([graph()?.workspaceValue, graph()?.workspaceFolderValue], [was?.workspaceValue, was?.workspaceFolderValue],
                'The graph is as the case found it');
            assert.notStrictEqual(await pid(), before, 'The server was restarted');
        } finally {
            await restoreSettings(recorded);
        }
    });
});
