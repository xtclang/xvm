import * as assert from 'node:assert';
import * as fs from 'node:fs/promises';
import * as path from 'node:path';
import * as vscode from 'vscode';
import { cutAndPasteDirectories } from '../explorer-move';
import { ExplorerMoveTrace } from '../explorer-move-trace';
import { catalog } from '../playbook/shared';

/** Reproduce X130 using plain text and an empty extension, without Ecstasy or an LSP client. */
export async function run(): Promise<void> {
    const root = vscode.Uri.joinPath(vscode.workspace.workspaceFolders![0].uri, 'X130');
    const data = catalog.cases.X130.values;
    const sources = data.sources;
    const trace = new ExplorerMoveTrace(path.join(process.env.XTC_EXPLORER_PROBE_REPORT!, 'move-trace.json'), root);
    const completed: string[] = [];
    const refreshDuringMove = process.env.XTC_EXPLORER_PROBE_REFRESH === 'true';
    const report = { vscode: vscode.version, ecstasyLoaded: !!vscode.extensions.getExtension('xtclang.xtc-language'), refreshDuringMove, completed };
    // Model a refresh while an asynchronous rename participant is pending. This uses only
    // public host APIs and supplies no edit; there is no compiler, delay or vendor patch.
    const participant = refreshDuringMove ? vscode.workspace.onWillRenameFiles(event => {
        event.waitUntil(trace.command('workbench.files.action.refreshFilesExplorer').then(() => undefined));
    }) : undefined;
    try {
        assert.strictEqual(report.ecstasyLoaded, false, 'The host probe must not load the Ecstasy extension');
        for (const file of data.files) {
            const target = path.join(root.fsPath, file.file);
            await fs.mkdir(path.dirname(target), { recursive: true });
            await fs.writeFile(target, file.text);
        }
        await fs.mkdir(path.join(root.fsPath, data.destination));
        const consumer = await vscode.workspace.openTextDocument(vscode.Uri.joinPath(root, data.consumer));
        await vscode.window.showTextDocument(consumer, { preview: false });
        const failure = await cutAndPasteDirectories(sources.map(source => vscode.Uri.joinPath(root, source)), vscode.Uri.joinPath(root, data.destination), trace);
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
                const current = path.join(root.fsPath, ...(moved ? [data.destination, folder] : [folder]));
                assert.ok((await fs.stat(current)).isDirectory());
                const previous = path.join(root.fsPath, ...(moved ? [folder] : [data.destination, folder]));
                await assert.rejects(fs.stat(previous), { code: 'ENOENT' });
            }
            for (const file of data.files) {
                const prefix = moved && sources.some(source => file.file.startsWith(source + '/')) ? [data.destination] : [];
                assert.strictEqual(await fs.readFile(path.join(root.fsPath, ...prefix, file.file), 'utf8'), file.text);
            }
        }
        await verify(true);
        completed.push('Move and contents');
        for (const [action, moved] of [['undo', false], ['redo', true]] as const) {
            await trace.command('workbench.files.action.focusFilesExplorer');
            await trace.command(action);
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
    } finally { participant?.dispose(); await trace.save(); }
}
