import * as assert from 'node:assert';
import * as vscode from 'vscode';

/** Native Undo dispatch depends on window focus as well as the selected editor/Explorer. */
export async function focusTestWindow(): Promise<void> {
    if (vscode.window.state.focused) return;
    // Activate through the host, without pointer movement or replaying the pending action.
    await vscode.commands.executeCommand('workbench.action.focusWindow');
    const deadline = Date.now() + 5_000;
    while (!vscode.window.state.focused && Date.now() < deadline) {
        await new Promise(resolve => setTimeout(resolve, 25));
    }
    assert.ok(vscode.window.state.focused, 'Native action requires the test window to be focused');
}
