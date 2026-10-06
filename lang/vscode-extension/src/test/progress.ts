import type Mocha from 'mocha';
import * as vscode from 'vscode';

interface WaitStep { readonly token: symbol; readonly message?: string; readonly elapsed?: number }
const waits = new vscode.EventEmitter<WaitStep>();

/** Describe bounded waits without hiding the case ID, counters or failure result. */
export async function withTestWait<T>(message: string, action: () => Promise<T>): Promise<T> {
    const token = Symbol(message);
    const started = performance.now();
    const report = () => waits.fire({ token, message, elapsed: Math.floor((performance.now() - started) / 1000) });
    report();
    const timer = setInterval(report, 1000);
    try { return await action(); }
    finally { clearInterval(timer); waits.fire({ token }); }
}

/** Share native status-bar progress between smoke tests and selected or full playbook runs. */
export function showTestProgress(runner: Mocha.Runner, title: string, label: (test: Mocha.Test) => string): void {
    const progress = vscode.window.createStatusBarItem('xtc.tests.progress', vscode.StatusBarAlignment.Left, 10_000);
    progress.name = `${title} progress`;
    const show = (current: string, detail: string) => {
        const completed = runner.stats?.tests ?? 0;
        const failed = runner.stats?.failures ?? 0;
        const skipped = runner.stats?.pending ?? 0;
        const summary = `${title}: ${completed}/${runner.total} completed, ${runner.total - completed} left | ${current}` +
            (failed ? ` | ${failed} failed` : '') + (skipped ? ` | ${skipped} skipped` : '');
        progress.text = `$(beaker) ${summary}`;
        progress.tooltip = detail;
        progress.accessibilityInformation = { label: `${summary}. ${detail}` };
        progress.show();
    };
    // Keep the ID and a useful description visible; the tooltip retains the complete title.
    const caption = (test: Mocha.Test) => {
        const text = label(test);
        return text.length > 90 ? `${text.slice(0, 87).trimEnd()}…` : text;
    };
    // Owned by this serial runner; nested waits restore their enclosing step on completion.
    const activeWaits = new Map<symbol, WaitStep>();
    let current: Mocha.Test | undefined;
    const subscription = waits.event(step => {
        if (step.message === undefined) activeWaits.delete(step.token);
        else activeWaits.set(step.token, step);
        if (!current) return;
        const active = [...activeWaits.values()].pop();
        const message = active?.message;
        if (message === undefined) { show(`${caption(current)} — running`, current.fullTitle()); return; }
        const detail = `${current.fullTitle()}\nWaiting: ${message} (${active?.elapsed}s / 30s)`;
        const short = message.length > 58 ? `${message.slice(0, 55)}…` : message;
        show(`${current.title.split(':')[0]} — ${short} (${active?.elapsed}s / 30s)`, detail);
    });
    show('starting', `Starting ${title}`);
    runner.on('test', test => { current = test; show(`${caption(test)} — running`, test.fullTitle()); });
    runner.on('test end', test => {
        current = undefined;
        activeWaits.clear();
        show(`${caption(test)} — ${test.state ?? 'skipped'}`, test.fullTitle());
    });
    runner.once('end', () => { subscription.dispose(); progress.dispose(); });
}
