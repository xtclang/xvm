import * as vscode from 'vscode';
import { readServiceSettings } from './editor-settings';
import { ServiceSettings, parseServiceSettings } from './service-settings';

const choices: ReadonlyArray<vscode.QuickPickItem & { adapter: ServiceSettings['adapter'] }> = [
    { adapter: 'compiler', label: 'Ecstasy Compiler', detail: 'Compiler diagnostics, type resolution and semantic features.' },
    { adapter: 'treesitter', label: 'Tree-sitter', detail: 'Syntax-based analysis without compiler type checking.' },
    { adapter: 'default', label: 'Bundled default', detail: 'Use the adapter chosen by this server build (normally Compiler).' },
];

/** Persist only the window's adapter choice; the configuration listener owns the restart. */
export async function selectLanguageAdapter(requested?: unknown): Promise<void> {
    const current = readServiceSettings().adapter;
    const selected = requested === undefined
        ? (await vscode.window.showQuickPick(choices.map(choice => ({
            ...choice, description: choice.adapter === current ? 'Selected' : undefined,
        })), { title: 'Ecstasy: Switch Language Adapter', placeHolder: 'Selecting a different adapter restarts the language server. Unsaved buffers are preserved.' }))?.adapter
        : parseServiceSettings({ adapter: requested }).adapter;
    if (selected === undefined || selected === current) return;
    const target = vscode.workspace.workspaceFolders?.length
        ? vscode.ConfigurationTarget.Workspace : vscode.ConfigurationTarget.Global;
    await vscode.workspace.getConfiguration('xtc').update('languageService.adapter', selected, target);
}
