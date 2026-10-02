import type Mocha from 'mocha';
import * as vscode from 'vscode';

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
    show('starting', `Starting ${title}`);
    runner.on('test', test => show(`${caption(test)} — running`, test.fullTitle()));
    runner.on('test end', test => show(`${caption(test)} — ${test.state ?? 'skipped'}`, test.fullTitle()));
    runner.once('end', () => progress.dispose());
}
