import * as assert from 'node:assert';
import * as vscode from 'vscode';

/** Native batch move, shared with the extension-free host reproduction. Never replay Paste. */
export async function cutAndPasteDirectories(sources: readonly vscode.Uri[], destination: vscode.Uri): Promise<Error | undefined> {
    const expected = sources.map(source => source.fsPath).sort();
    const deadline = Date.now() + 30_000;
    await vscode.commands.executeCommand('workbench.files.action.refreshFilesExplorer');
    // Only selection is repeatable: watcher refreshes can retire Explorer nodes before Cut.
    for (;;) {
        try {
            await vscode.commands.executeCommand('workbench.files.action.focusFilesExplorer');
            await vscode.commands.executeCommand('workbench.files.action.collapseExplorerFolders');
            await vscode.commands.executeCommand('revealInExplorer', sources[0]);
            for (const _ of sources.slice(1)) await vscode.commands.executeCommand('list.expandSelectionDown');
            await vscode.commands.executeCommand('copyFilePath');
            const selected = (await vscode.env.clipboard.readText()).split(/\r?\n/).sort();
            if (JSON.stringify(selected) === JSON.stringify(expected)) {
                await vscode.commands.executeCommand('filesExplorer.cut');
                break;
            }
        } catch (error) {
            if (!String(error).includes('Data tree node not found')) throw error;
        }
        assert.ok(Date.now() < deadline, `Explorer did not select ${expected.join(', ')}`);
        await new Promise(resolve => setTimeout(resolve, 75));
    }
    await vscode.commands.executeCommand('revealInExplorer', destination);
    return vscode.commands.executeCommand('filesExplorer.paste').then(
        () => undefined,
        (error: unknown) => {
            // The host can move successfully, then repaint retired Cut nodes in its finally
            // block. Return only that failure so callers can collect Undo/Redo evidence before
            // failing. Other errors abort immediately. No VS Code internals are patched.
            assert.ok(error instanceof Error && error.message.includes('Data tree node not found')
                && error.stack?.includes('itemsCopied'), String(error));
            return error;
        }
    );
}
