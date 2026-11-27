import * as fs from 'node:fs/promises';
import * as path from 'node:path';
import { isDeepStrictEqual } from 'node:util';
import { parse } from 'jsonc-parser';
import * as vscode from 'vscode';

/**
 * A settings file of the editor, by the configuration target it is, and what it held, by its settings' keys: a workspace
 * file holds its settings in its [section].
 */
interface SettingsFile {
    readonly target: vscode.ConfigurationTarget;
    readonly file: string;
    readonly folder?: vscode.WorkspaceFolder;
    readonly section?: string;
    readonly values: Record<string, unknown>;
}

/** The settings of the editor that a case can change, as they are, and how many of the server's runtime had changed then. */
export interface RecordedSettings {
    readonly files: readonly SettingsFile[];
    readonly runtime: number;
}

/**
 * The settings that apply to the language server only when it starts, which the extension only offers to apply, as
 * its notice after a change of them says ("Restart the language server to apply them").
 */
export function appliesOnRestart(key: string): boolean {
    return key === 'xtc.java.vmOptions' || key.startsWith('xtc.server.');
}

/** The connection's settings, for which the extension restarts the server by itself, as it does for a change of them. */
export function restartsByItself(key: string): boolean {
    return key === 'xtc.java.home' || key === 'xtc.sourceRoots' || key.startsWith('xtc.languageService.');
}

/** How many times a setting that [appliesOnRestart] has changed, since the first record, by its watch. */
let runtimeChanges = 0;
let runtimeWatch: vscode.Disposable | undefined;
const RUNTIME_SETTINGS = ['xtc.java.vmOptions', 'xtc.server'];

/**
 * Records every setting that the editor's settings files hold, which a case may change: the user's, whose file the
 * launcher names in `XTC_TEST_USER_SETTINGS`, the workspace's, and, with a workspace file, its folders'. From the first
 * record on, it counts the changes of the settings that [appliesOnRestart].
 */
export async function recordSettings(): Promise<RecordedSettings> {
    runtimeWatch ??= vscode.workspace.onDidChangeConfiguration(event => {
        if (RUNTIME_SETTINGS.some(key => event.affectsConfiguration(key))) runtimeChanges++;
    });
    const user = process.env.XTC_TEST_USER_SETTINGS;
    const workspace = vscode.workspace.workspaceFile;
    const folders = vscode.workspace.workspaceFolders ?? [];
    const folderFile = (folder: vscode.WorkspaceFolder) => path.join(folder.uri.fsPath, '.vscode', 'settings.json');
    const files: Omit<SettingsFile, 'values'>[] = [
        ...(user ? [{ target: vscode.ConfigurationTarget.Global, file: user }] : []),
        ...(workspace?.scheme === 'file'
            ? [{ target: vscode.ConfigurationTarget.Workspace, file: workspace.fsPath, section: 'settings' },
                ...folders.map(folder => ({ target: vscode.ConfigurationTarget.WorkspaceFolder, file: folderFile(folder), folder }))]
            : folders.slice(0, 1).map(folder => ({ target: vscode.ConfigurationTarget.Workspace, file: folderFile(folder) }))),
    ];
    return { files: await Promise.all(files.map(async each => ({ ...each, values: await values(each) }))), runtime: runtimeChanges };
}

/**
 * Puts back, through VS Code, as a case's own update does, every setting that differs from [recorded], but those that
 * [kept] names: a language's block, as `[xtc]`, setting by setting. When one it puts back [appliesOnRestart], or one
 * changed since the record and the case [failed], as before its own restart could run, [restart] restarts the server,
 * which the extension only offers to do, and its notice is dismissed; when one [restartsByItself], [restart] waits for
 * the extension's restart, which it joins, to its end, also one that runs twice for two of them. Answers the keys it
 * put back.
 */
export async function restoreSettings(recorded: RecordedSettings, { kept = () => false, failed = false, restart }: {
    kept?: (key: string) => boolean; failed?: boolean; restart?: () => Promise<unknown>;
} = {}): Promise<string[]> {
    const restored: string[] = [];
    for (const each of recorded.files) {
        const now = await values(each);
        for (const key of new Set([...Object.keys(each.values), ...Object.keys(now)])) {
            if (kept(key)) continue;
            const language = /^\[(.+)\]$/.exec(key)?.[1];
            if (language === undefined) {
                if (isDeepStrictEqual(now[key], each.values[key])) continue;
                await vscode.workspace.getConfiguration(undefined, each.folder?.uri).update(key, each.values[key], each.target);
                restored.push(key);
                continue;
            }
            // a language's settings, in its block, each through VS Code's override of it
            const was = block(each.values[key]);
            const is = block(now[key]);
            const scope = { uri: each.folder?.uri, languageId: language };
            for (const inner of new Set([...Object.keys(was), ...Object.keys(is)])) {
                if (isDeepStrictEqual(is[inner], was[inner])) continue;
                await vscode.workspace.getConfiguration(undefined, scope).update(inner, was[inner], each.target, true);
                restored.push(`${key}.${inner}`);
            }
        }
    }
    const offered = restored.some(key => appliesOnRestart(key)) || (failed && runtimeChanges !== recorded.runtime);
    if (restart && (offered || restored.some(key => restartsByItself(key)))) {
        await restart();
        if (offered) await vscode.commands.executeCommand('notifications.clearAll');
    }
    return restored;
}

/** The settings of a language's block, as `[xtc]` holds them: none when it has none. */
function block(value: unknown): Record<string, unknown> {
    return typeof value === 'object' && value !== null ? value as Record<string, unknown> : {};
}

/** The settings that [settings]'s file holds now, by their keys: none when it has none, or is not there. */
async function values(settings: Omit<SettingsFile, 'values'>): Promise<Record<string, unknown>> {
    const text = await fs.readFile(settings.file, 'utf8').catch(() => '');
    const parsed = (parse(text) ?? {}) as Record<string, unknown>;
    const held = settings.section === undefined ? parsed : parsed[settings.section];
    return typeof held === 'object' && held !== null ? held as Record<string, unknown> : {};
}
