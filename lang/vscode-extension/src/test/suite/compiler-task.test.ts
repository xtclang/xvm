import * as assert from 'node:assert';
import * as vscode from 'vscode';
import { runCompilerTask } from '../../compiler-task';

suite('Compiler task lifetime', () => {
    function fixture() {
        const processes = new vscode.EventEmitter<vscode.TaskProcessEndEvent>();
        const tasks = new vscode.EventEmitter<vscode.TaskEndEvent>();
        const cancel = new vscode.CancellationTokenSource();
        const task = new vscode.Task({ type: 'xtc-model', operation: 'owned' }, vscode.TaskScope.Workspace, 'Import', 'Ecstasy');
        const terminations: string[] = [];
        const execution = { task, terminate: () => { terminations.push('owned'); } };
        const host = { onDidEndTaskProcess: processes.event, onDidEndTask: tasks.event,
            executeTask: async () => execution };
        return { task, execution, host, cancel, terminations, tasks, processes,
            dispose: () => { cancel.dispose(); processes.dispose(); tasks.dispose(); } };
    }

    test('immediately exiting task is observed even before executeTask resolves', async () => {
        const f = fixture();
        try {
            f.host.executeTask = async () => { f.processes.fire({ execution: f.execution, exitCode: 0 }); return f.execution; };
            assert.strictEqual((await runCompilerTask(f.task, f.cancel.token, () => {}, f.host)).outcome, 'succeeded');
            f.cancel.cancel();
            await Promise.resolve();
            assert.deepStrictEqual(f.terminations, []);
        } finally { f.dispose(); }
    });

    test('cancellation before dispatch never starts the task', async () => {
        const f = fixture();
        try {
            f.host.executeTask = async () => { throw new Error('Must not launch'); };
            const result = runCompilerTask(f.task, f.cancel.token, () => {}, f.host);
            f.cancel.cancel();
            assert.strictEqual((await result).outcome, 'cancelled');
        } finally { f.dispose(); }
    });

    test('cancellation while the execution handle is pending terminates only the owned task', async () => {
        const f = fixture();
        try {
            const reports: string[] = [];
            f.host.executeTask = async () => {
                f.cancel.cancel();
                return { ...f.execution, terminate: () => {
                    f.terminations.push('owned');
                    f.processes.fire({ execution: f.execution, exitCode: undefined });
                    f.tasks.fire({ execution: f.execution });
                } };
            };
            const result = await runCompilerTask(f.task, f.cancel.token, message => reports.push(message), f.host);
            assert.strictEqual(result.outcome, 'cancelled');
            assert.deepStrictEqual(f.terminations, ['owned']);
            assert.deepStrictEqual(reports, ['Cancelling Gradle import…']);
        } finally { f.dispose(); }
    });

    test('unrelated task exits do not complete this import and missing process results fail', async () => {
        const f = fixture();
        try {
            const other = new vscode.Task({ type: 'xtc-model', operation: 'other' }, vscode.TaskScope.Workspace, 'Other', 'Ecstasy');
            f.host.executeTask = async () => {
                f.processes.fire({ execution: { ...f.execution, task: other }, exitCode: 0 });
                f.tasks.fire({ execution: f.execution });
                return f.execution;
            };
            assert.strictEqual((await runCompilerTask(f.task, f.cancel.token, () => {}, f.host)).outcome, 'failed');
        } finally { f.dispose(); }
    });

    test('startup failure retires cancellation listeners', async () => {
        const f = fixture();
        try {
            f.host.executeTask = async () => { throw new Error('Startup refused'); };
            await assert.rejects(runCompilerTask(f.task, f.cancel.token, () => {}, f.host), /Startup refused/);
            f.cancel.cancel();
            await Promise.resolve();
            assert.deepStrictEqual(f.terminations, []);
        } finally { f.dispose(); }
    });

    test('native process task success is observed through the actual VS Code event order', async () => {
        const cancel = new vscode.CancellationTokenSource();
        const task = nativeTask('success', "console.log('Ecstasy compiler import task test');");
        try {
            assert.strictEqual((await runCompilerTask(task, cancel.token, () => {})).outcome, 'succeeded');
        } finally { cancel.dispose(); }
    });

    test('native task cancellation terminates the process and completes as cancellation', async () => {
        const cancel = new vscode.CancellationTokenSource();
        const task = nativeTask('cancel', 'setInterval(() => {}, 1000);');
        const started = vscode.tasks.onDidStartTaskProcess(event => {
            if (event.execution.task.definition.operation === task.definition.operation) cancel.cancel();
        });
        try {
            assert.strictEqual((await runCompilerTask(task, cancel.token, () => {})).outcome, 'cancelled');
        } finally { started.dispose(); cancel.dispose(); }
    });

    function nativeTask(operation: string, script: string): vscode.Task {
        const owner = vscode.workspace.workspaceFolders?.[0];
        assert.ok(owner, 'Native import regression needs the test workspace');
        return new vscode.Task({ type: 'xtc-model', operation: `test-import-${operation}` }, owner, `Import ${operation}`, 'Ecstasy',
            new vscode.ProcessExecution(process.execPath, ['-e', script], { env: { ELECTRON_RUN_AS_NODE: '1' } }));
    }
});
