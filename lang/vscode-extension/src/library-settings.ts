import * as vscode from 'vscode';
import { getClient, updateCompilerConfiguration } from './lsp-client';
import { LibraryOptions, normalizeLibraries } from './library-configuration';

export function libraryOptions(): LibraryOptions {
    return normalizeLibraries(vscode.workspace.getConfiguration('xtc.compiler').get('libraries'),
        (vscode.workspace.workspaceFolders ?? []).map(folder => folder.uri.toString()));
}

/** Edits are local until Apply. Escape from any step discards the complete path-list draft. */
export async function editOrderedPaths(initial: readonly string[], title: string, directoriesOnly: boolean): Promise<string[] | undefined> {
    let draft = [...initial];
    while (true) {
        const items = [
            { label: 'Use these paths', index: -1 }, { label: 'Choose path…', index: -2 }, { label: 'Enter path…', index: -3 },
            ...draft.map((value, index) => ({ label: `${index + 1}. ${value}`, index }))
        ];
        const picked = await vscode.window.showQuickPick(items, { title, placeHolder: 'Paths are searched in this order; select a path to move or remove it' });
        if (!picked) return undefined;
        if (picked.index === -1) return draft;
        if (picked.index === -2) {
            const chosen = await vscode.window.showOpenDialog({ title, canSelectMany: true, canSelectFolders: true, canSelectFiles: !directoriesOnly });
            if (!chosen) return undefined;
            draft = [...draft, ...chosen.map(uri => uri.toString())];
        } else if (picked.index === -3) {
            const value = await vscode.window.showInputBox({ title, prompt: 'File URI or path relative to the single workspace folder' });
            if (value === undefined) return undefined;
            if (value.trim()) draft = [...draft, value.trim()];
        } else {
            const action = await vscode.window.showQuickPick(['Move up', 'Move down', 'Remove'], { title });
            if (!action) return undefined;
            const index = picked.index;
            if (action === 'Remove') draft = draft.filter((_, at) => at !== index);
            else {
                const to = index + (action === 'Move up' ? -1 : 1);
                if (to >= 0 && to < draft.length) [draft[index], draft[to]] = [draft[to], draft[index]];
            }
        }
    }
}

export async function configureCompilerLibraries(): Promise<void> {
    const settings = vscode.workspace.getConfiguration('xtc.compiler');
    const before = JSON.stringify(settings.get('libraries'));
    const folders = (vscode.workspace.workspaceFolders ?? []).map(folder => folder.uri.toString());
    const connection = getClient();
    let draft = libraryOptions();
    while (true) {
        const picked = await vscode.window.showQuickPick([
            'Apply library settings', 'Reload applied libraries', 'Edit binary paths', 'Inherit Gradle libraries', 'Add source attachment',
            ...draft.sourceAttachments.map(item => `Edit sources: ${item.module}`)
        ], { title: 'Ecstasy Libraries and Sources', placeHolder: 'Bundled XDK is always included and read-only. Attached sources provide navigation only.' });
        if (!picked) return;
        if (picked === 'Reload applied libraries') { await updateCompilerConfiguration(); return; }
        if (picked === 'Apply library settings') {
            if (before !== JSON.stringify(vscode.workspace.getConfiguration('xtc.compiler').get('libraries')) || connection !== getClient() ||
                JSON.stringify(folders) !== JSON.stringify((vscode.workspace.workspaceFolders ?? []).map(folder => folder.uri.toString()))) {
                throw new Error('Compiler libraries or workspace changed while this dialog was open. Reopen it to edit current settings.');
            }
            await settings.update('libraries', normalizeLibraries(draft, folders, true), vscode.ConfigurationTarget.Workspace);
            return;
        }
        if (picked === 'Inherit Gradle libraries') { draft = { ...draft, modulePath: null }; continue; }
        if (picked === 'Edit binary paths') {
            const paths = await editOrderedPaths(draft.modulePath ?? [], 'Ecstasy Binary Library Paths', false);
            if (paths === undefined) return;
            draft = { ...draft, modulePath: paths };
            continue;
        }
        const module = picked === 'Add source attachment'
            ? await vscode.window.showInputBox({ title: 'Ecstasy Source Attachment', prompt: 'Exact binary module name' })
            : picked.slice('Edit sources: '.length);
        if (!module) return;
        const previous = draft.sourceAttachments.find(item => item.module === module);
        const roots = await editOrderedPaths(previous?.roots ?? [], `Ecstasy Sources: ${module}`, true);
        if (roots === undefined) return;
        draft = { ...draft, sourceAttachments: [...draft.sourceAttachments.filter(item => item.module !== module), ...(roots.length ? [{ module, roots }] : [])] };
    }
}
