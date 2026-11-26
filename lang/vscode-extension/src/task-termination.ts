import * as vscode from 'vscode';

/**
 * Terminates [execution], whenever it is asked: also between executeTask's answer and the task's start. VS Code drops
 * a terminate that comes then, as MainThreadTask.$terminateTask finds no active task to end ("Task to terminate not
 * found"), and the API's terminate() gives nothing back that says so (VS Code 1.140); the task then runs on. So the
 * task is terminated again as it starts, unless it has ended by then.
 */
export function terminateTask(execution: vscode.TaskExecution): void {
    const subscriptions: vscode.Disposable[] = [];
    const done = () => subscriptions.splice(0).forEach(subscription => subscription.dispose());
    subscriptions.push(
        vscode.tasks.onDidStartTask(event => {
            if (event.execution !== execution) return;
            done();
            execution.terminate();
        }),
        vscode.tasks.onDidEndTask(event => {
            if (event.execution === execution) done();
        }),
    );
    // one that has ended has nothing to stop, and no event will come for it
    if (!vscode.tasks.taskExecutions.includes(execution)) done();
    execution.terminate();
}
