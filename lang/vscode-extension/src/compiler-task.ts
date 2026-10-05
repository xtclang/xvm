import * as vscode from 'vscode';
import { ImportResult } from './compiler-import';

type TaskHost = Pick<typeof vscode.tasks, 'executeTask' | 'onDidEndTask' | 'onDidEndTaskProcess'>;

/** Wait for the owned native task, including cancellation before executeTask returns its handle. */
export async function runCompilerTask(task: vscode.Task, token: vscode.CancellationToken,
    report: (message: string) => void, host: TaskHost = vscode.tasks): Promise<Pick<ImportResult, 'outcome' | 'message'>> {
    if (token.isCancellationRequested) return { outcome: 'cancelled', message: 'Import cancelled.' };
    const retired = new AbortController();
    // Defer dispatch until listeners are installed: even an immediately exiting process is observed.
    const started = Promise.resolve().then(() => token.isCancellationRequested ? undefined : host.executeTask(task));
    const owns = (execution: vscode.TaskExecution) => execution.task.definition.operation === task.definition.operation;
    const subscriptions: vscode.Disposable[] = [];
    try {
        return await new Promise((resolve, reject) => {
            subscriptions.push(
                host.onDidEndTaskProcess(event => {
                    if (!owns(event.execution)) return;
                    resolve(token.isCancellationRequested ? { outcome: 'cancelled', message: 'Import cancelled.' } :
                        event.exitCode === 0 ? { outcome: 'succeeded', message: 'Validating evaluated compiler inputs…' } :
                            { outcome: 'failed', message: `Gradle import failed (${event.exitCode ?? 'no exit code'}); previous compiler configuration retained. See the Ecstasy task terminal.` });
                }),
                host.onDidEndTask(event => {
                    if (owns(event.execution)) resolve(token.isCancellationRequested ? { outcome: 'cancelled', message: 'Import cancelled.' } :
                        { outcome: 'failed', message: 'Gradle task ended without a successful process result; previous compiler configuration retained.' });
                }),
                token.onCancellationRequested(() => {
                    report('Cancelling Gradle import…');
                    void started.then(execution => {
                        if (!retired.signal.aborted) execution?.terminate();
                    }, () => { /* Dispatch failure is handled by the task promise below. */ });
                }),
            );
            void started.then(execution => {
                if (!execution) resolve({ outcome: 'cancelled', message: 'Import cancelled.' });
            }, reject);
        });
    } finally {
        retired.abort();
        subscriptions.forEach(subscription => subscription.dispose());
    }
}
