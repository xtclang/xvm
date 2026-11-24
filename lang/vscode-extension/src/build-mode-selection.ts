import * as vscode from 'vscode';
import {
    buildMode, hasGradleBuild, onDidChangeReplacedLibraries, replacedLibraries, type BuildMode,
} from './embedded-execution';

type BuildModeSetting = BuildMode | 'auto';

const choices: ReadonlyArray<vscode.QuickPickItem & { mode: BuildModeSetting }> = [
    {
        mode: 'embedded',
        label: 'Ecstasy compiler (embedded)',
        detail: "Build, run and rebuild with the language server's compiler, also in a folder with a Gradle build.",
    },
    { mode: 'gradle', label: 'Gradle', detail: "Build and run through the folder's Gradle build." },
    { mode: 'auto', label: 'Automatic', detail: 'Gradle in a folder with a Gradle build, the embedded compiler otherwise.' },
];

/**
 * The picker's choices for a folder whose modules are built against [replaced] libraries, which the workspace's own
 * modules replace: the embedded compiler says it is not available there, as VS Code cannot disable a choice.
 */
export function buildModeChoices(current: string, replaced: readonly string[]): (vscode.QuickPickItem & { mode: BuildModeSetting })[] {
    return choices.map(choice => ({
        ...choice,
        description: choice.mode === 'embedded' && replaced.length > 0
            ? `Not available: this folder's modules replace ${replaced.join(', ')}${choice.mode === current ? '; selected' : ''}`
            : choice.mode === current ? 'Selected' : undefined,
    }));
}

/** The folder a build mode applies to: the active editor's, or the only folder open. */
function activeFolder(): vscode.WorkspaceFolder | undefined {
    const document = vscode.window.activeTextEditor?.document.uri;
    const folders = vscode.workspace.workspaceFolders ?? [];
    return (document && vscode.workspace.getWorkspaceFolder(document)) ?? (folders.length === 1 ? folders[0] : undefined);
}

/**
 * Ecstasy: Select Build Mode. Stores xtc.build.mode for one folder; [requested] skips the picker, and
 * [folder] names the folder, for keybindings and tests.
 */
export async function selectBuildMode(requested?: unknown, folder?: vscode.Uri): Promise<void> {
    if (requested !== undefined && !choices.some(choice => choice.mode === requested)) {
        throw new Error(`Unknown Ecstasy build mode: ${String(requested)}`);
    }
    const target = (folder && vscode.workspace.getWorkspaceFolder(folder)) ?? activeFolder()
        ?? await vscode.window.showWorkspaceFolderPick({ placeHolder: 'Folder whose Ecstasy build mode to choose' });
    if (!target) {
        if (!vscode.workspace.workspaceFolders?.length) {
            void vscode.window.showInformationMessage('Open a folder to choose how Ecstasy builds and runs it.');
        }
        return;
    }
    const settings = vscode.workspace.getConfiguration('xtc', target.uri);
    const current = settings.get<string>('build.mode', 'embedded');
    const selected = requested as BuildModeSetting | undefined
        ?? (await vscode.window.showQuickPick(buildModeChoices(current, replacedLibraries(target)),
            { title: 'Ecstasy: Select Build Mode', placeHolder: `How Build, Run and Rebuild work in ${target.name}` }))?.mode;
    if (selected === undefined || selected === current) return;
    const scope = (vscode.workspace.workspaceFolders?.length ?? 0) > 1
        ? vscode.ConfigurationTarget.WorkspaceFolder : vscode.ConfigurationTarget.Workspace;
    await settings.update('build.mode', selected, scope);
}

/** Why [folder] keeps Gradle after a choice of the embedded compiler while its modules replace [replaced] libraries. */
export function embeddedUnavailable(folder: string, replaced: readonly string[]): string {
    return `The embedded compiler cannot build or run ${folder} while its modules replace the bundled libraries `
        + `${replaced.join(', ')}: the language server builds and runs only with the bundled XDK. ${folder} keeps `
        + 'using Gradle; the choice is kept, and applies once no library is replaced.';
}

/**
 * Warns when a folder's build mode becomes the embedded compiler, by the picker or by an edit of xtc.build.mode in
 * the settings, while its modules are built against libraries that the workspace's own modules replace.
 */
export function watchEmbeddedChoice(): vscode.Disposable {
    return vscode.workspace.onDidChangeConfiguration(event => {
        for (const folder of vscode.workspace.workspaceFolders ?? []) {
            const replaced = replacedLibraries(folder);
            if (replaced.length > 0 && event.affectsConfiguration('xtc.build.mode', folder.uri)
                && vscode.workspace.getConfiguration('xtc', folder.uri).get<string>('build.mode') === 'embedded') {
                void vscode.window.showWarningMessage(embeddedUnavailable(folder.name, replaced));
            }
        }
    });
}

/** What the build mode status bar item shows, or undefined while it is hidden; for the playbook. */
let shown: string | undefined;

export function buildModeStatus(): string | undefined {
    return shown;
}

/** The status bar text for a folder's effective build mode. */
export function buildModeStatusText(mode: BuildMode): string {
    return mode === 'gradle' ? '$(tools) Gradle build' : '$(tools) Embedded build';
}

/**
 * Shows the active folder's build mode in the status bar when it has a Gradle build, where the choice
 * matters; clicking it opens Ecstasy: Select Build Mode.
 */
export function createBuildModeStatus(): vscode.Disposable {
    const item = vscode.window.createStatusBarItem('xtc.buildMode', vscode.StatusBarAlignment.Right, 99);
    item.name = 'Ecstasy Build Mode';
    item.command = 'xtc.selectBuildMode';
    const update = () => {
        const folder = activeFolder();
        if (!folder || !hasGradleBuild(folder)) {
            item.hide();
            shown = undefined;
            return;
        }
        const mode = buildMode(folder);
        item.text = buildModeStatusText(mode);
        shown = item.text;
        const replaced = replacedLibraries(folder);
        item.tooltip = replaced.length > 0
            ? `Ecstasy builds and runs ${folder.name} through its Gradle build while its own modules replace the bundled `
                + `libraries ${replaced.join(', ')}.`
            : mode === 'gradle'
            ? `Ecstasy builds and runs ${folder.name} through its Gradle build. Click to choose the embedded compiler.`
            : `Ecstasy builds and runs ${folder.name} with the embedded compiler; its Gradle build's settings do not apply. Click to choose Gradle.`;
        item.show();
    };
    update();
    return vscode.Disposable.from(
        item,
        { dispose: () => { shown = undefined; } },
        vscode.window.onDidChangeActiveTextEditor(update),
        vscode.workspace.onDidChangeWorkspaceFolders(update),
        onDidChangeReplacedLibraries(update),
        vscode.workspace.onDidChangeConfiguration(event => {
            if (event.affectsConfiguration('xtc.build.mode')) update();
        }),
    );
}
