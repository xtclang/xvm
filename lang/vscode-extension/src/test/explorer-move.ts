import * as assert from 'node:assert';
import * as vscode from 'vscode';
import type { ExplorerMoveTrace } from './explorer-move-trace';

/** Native batch move, shared with the extension-free host reproduction. Never replay Paste. */
export async function cutAndPasteDirectories(sources: readonly vscode.Uri[], destination: vscode.Uri, trace: ExplorerMoveTrace): Promise<Error | undefined> {
    const expected = sources.map(source => source.fsPath).sort();
    const deadline = Date.now() + 30_000;
    await trace.command('workbench.files.action.refreshFilesExplorer');
    // Only selection is repeatable: watcher refreshes can retire Explorer nodes before Cut.
    for (;;) {
        try {
            await trace.command('workbench.files.action.focusFilesExplorer');
            await trace.command('workbench.files.action.collapseExplorerFolders');
            await trace.command('revealInExplorer', sources[0]);
            for (const _ of sources.slice(1)) await trace.command('list.expandSelectionDown');
            await trace.command('copyFilePath');
            const selected = (await vscode.env.clipboard.readText()).split(/\r?\n/).sort();
            if (JSON.stringify(selected) === JSON.stringify(expected)) {
                await trace.command('filesExplorer.cut');
                break;
            }
        } catch (error) {
            if (!String(error).includes('Data tree node not found')) throw error;
        }
        assert.ok(Date.now() < deadline, `Explorer did not select ${expected.join(', ')}`);
        await new Promise(resolve => setTimeout(resolve, 75));
    }
    await trace.command('revealInExplorer', destination);
    return trace.command('filesExplorer.paste').then(
        () => undefined,
        (error: unknown) => {
            // The host can move successfully, then repaint retired Cut nodes in its finally
            // block. Return only that failure so callers can collect Undo/Redo evidence before
            // failing. Other errors abort immediately. No VS Code internals are patched.
            // TODO VSCODE: UP16 — retire this diagnostic capture after native Paste safely
            // clears stale Cut items; the scenario must keep failing while the host throws.
            assert.ok(error instanceof Error && error.message.includes('Data tree node not found')
                && error.stack?.includes('itemsCopied'), String(error));
            return error;
        }
    );
}
