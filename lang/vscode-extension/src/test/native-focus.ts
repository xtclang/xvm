import * as assert from 'node:assert';
import { setTimeout as delay } from 'node:timers/promises';
import * as vscode from 'vscode';
import { answered, TRY_MS, untilTakes } from './uiWait';
import { WorkbenchUi } from './workbenchUi';

/**
 * Native Undo dispatch depends on window focus as well as the selected editor/Explorer. Asks for the focus again until
 * the window has it, as [untilTakes] does, each try waiting for it at most [TRY_MS].
 */
export async function focusTestWindow(): Promise<void> {
    if (vscode.window.state.focused) return;
    await untilTakes('The test window takes the focus', async () => {
        // Activate through the host, without pointer movement or replaying the pending action.
        await answered(vscode.commands.executeCommand('workbench.action.focusWindow'), 'Focus Window');
        const timer = new AbortController();
        let watch: vscode.Disposable | undefined;
        try {
            const focused = new Promise<void>(resolve => {
                watch = vscode.window.onDidChangeWindowState(state => { if (state.focused) resolve(); });
                if (vscode.window.state.focused) resolve();
            });
            await Promise.race([focused, delay(TRY_MS, undefined, { signal: timer.signal }).then(() => undefined, () => undefined)]);
        } finally {
            timer.abort();
            watch?.dispose();
        }
        assert.ok(vscode.window.state.focused, 'Native action requires the test window to be focused');
    }, {});
}

export async function focusTestEditor(document: vscode.TextDocument): Promise<void> {
    await focusTestWindow();
    const ui = await WorkbenchUi.connect();
    try { await ui.focusEditor(() => vscode.window.showTextDocument(document, { preview: false, preserveFocus: false })); }
    finally { await ui.close(); }
}
