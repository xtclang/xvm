import * as assert from 'node:assert';
import * as fs from 'node:fs/promises';
import * as path from 'node:path';
import * as vscode from 'vscode';
import { cutAndPasteDirectories } from '../explorer-move';

/** Reproduce X130 using plain text and an empty extension, without Ecstasy or an LSP client. */
export async function run(): Promise<void> {
    const root = vscode.workspace.workspaceFolders![0].uri;
    const sources = ['old', 'second'];
    const completed: string[] = [];
    const report = { vscode: vscode.version, ecstasyLoaded: !!vscode.extensions.getExtension('xtclang.xtc-language'), completed };
    try {
        assert.strictEqual(report.ecstasyLoaded, false, 'The host probe must not load the Ecstasy extension');
        for (const folder of [...sources, 'target']) await fs.mkdir(path.join(root.fsPath, folder));
        for (const folder of sources) await fs.writeFile(path.join(root.fsPath, folder, 'data.txt'), folder + '\n');
        const failure = await cutAndPasteDirectories(sources.map(source => vscode.Uri.joinPath(root, source)), vscode.Uri.joinPath(root, 'target'));
        async function verify(moved: boolean) {
            const deadline = Date.now() + 10_000;
            for (;;) {
                try { await verifyContents(moved); return; }
                catch (error) {
                    if (Date.now() >= deadline) throw error;
                    // Native command completion precedes filesystem events. Observe only;
                    // never replay the move, Undo or Redo while waiting for their effects.
                    await new Promise(resolve => setTimeout(resolve, 75));
                }
            }
        }
        async function verifyContents(moved: boolean) {
            for (const folder of sources) {
                const current = path.join(root.fsPath, ...(moved ? ['target', folder] : [folder]));
                assert.strictEqual(await fs.readFile(path.join(current, 'data.txt'), 'utf8'), folder + '\n');
                const previous = path.join(root.fsPath, ...(moved ? [folder] : ['target', folder]));
                await assert.rejects(fs.stat(previous), { code: 'ENOENT' });
            }
        }
        await verify(true);
        completed.push('Move and contents');
        for (const [action, moved] of [['undo', false], ['redo', true]] as const) {
            await vscode.commands.executeCommand('workbench.files.action.focusFilesExplorer');
            await vscode.commands.executeCommand(action);
            await verify(moved);
            completed.push(action + ' and contents');
        }
        if (failure) throw failure;
        await fs.writeFile(path.join(process.env.XTC_EXPLORER_PROBE_REPORT!, 'results.json'), JSON.stringify({ ...report, status: 'not-reproduced' }, null, 2) + '\n');
        console.log('Explorer host defect not reproduced in this attempt; this is not proof that X130 is fixed.');
    } catch (error) {
        await fs.writeFile(path.join(process.env.XTC_EXPLORER_PROBE_REPORT!, 'results.json'), JSON.stringify({
            ...report, status: 'failed', error: error instanceof Error ? error.stack : String(error)
        }, null, 2) + '\n');
        throw error;
    }
}
