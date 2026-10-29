import * as path from 'node:path';
import * as vscode from 'vscode';
import type { LanguageClient, Location, Position } from 'vscode-languageclient/node';

import { buildMode, createEmbeddedBuildTask } from './embedded-execution';
import { createXtcRunTask, XtcTaskDefinition, XtcTaskProvider } from './task-provider';

const MODULE_NAME_REGEX = /^[a-zA-Z_]\w*$/;

function validateProjectName(value: string): string | null {
    if (!value || value.trim().length === 0) {
        return 'Project name cannot be empty';
    }
    if (!MODULE_NAME_REGEX.test(value)) {
        return 'Project name must start with a letter or underscore and contain only letters, digits, and underscores';
    }
    return null;
}

function activeEcstasyDocument(): vscode.TextDocument | undefined {
    const document = vscode.window.activeTextEditor?.document;
    return document?.languageId === 'xtc' ? document : undefined;
}

/** The module a root source declares, for Gradle runs, which are addressed by module name. */
function declaredModule(document: vscode.TextDocument): string | undefined {
    return /^\s*module\s+([\w.]+)/m.exec(document.getText())?.[1];
}

/** Builds the active editor's folder (or the first folder) the way its build mode says. */
async function runBuild(client: () => LanguageClient | undefined, rebuild: boolean): Promise<void> {
    const document = activeEcstasyDocument();
    const folder = (document && vscode.workspace.getWorkspaceFolder(document.uri)) ?? vscode.workspace.workspaceFolders?.[0];
    if (!folder) {
        void vscode.window.showInformationMessage('Open a folder to build Ecstasy modules.');
        return;
    }
    if (buildMode(folder) === 'embedded') {
        await vscode.tasks.executeTask(createEmbeddedBuildTask(client, folder, rebuild));
        return;
    }
    const gradlew = process.platform === 'win32' ? 'gradlew.bat' : './gradlew';
    const task = new vscode.Task(
        { type: XtcTaskProvider.type, moduleName: '_lifecycle_build', mode: 'gradle' } as XtcTaskDefinition,
        folder, rebuild ? 'Rebuild' : 'Build', 'xtc',
        new vscode.ShellExecution(rebuild ? `${gradlew} clean build` : `${gradlew} build`), ['$xtc']);
    task.group = vscode.TaskGroup.Build;
    await vscode.tasks.executeTask(task);
}

export function registerCommands(context: vscode.ExtensionContext, outputChannel: vscode.OutputChannel,
                                 client: () => LanguageClient | undefined): void {
    context.subscriptions.push(
        vscode.commands.registerCommand('xtc.openLanguageSettings', () =>
            vscode.commands.executeCommand('workbench.action.openSettings', '@ext:xtclang.xtc-language')),
        vscode.commands.registerCommand('xtc.showReferences', (uri: string, at: Position, locations: Location[]) => {
            const targets = locations.map(({ uri, range }) => new vscode.Location(
                vscode.Uri.parse(uri), new vscode.Range(
                    range.start.line, range.start.character, range.end.line, range.end.character)));
            return vscode.commands.executeCommand('editor.action.showReferences',
                vscode.Uri.parse(uri), new vscode.Position(at.line, at.character), targets);
        }),
        // From the Run lens (source URI and module name), the editor title button or the keyboard (active
        // editor), or the command palette (asks for a module name).
        vscode.commands.registerCommand('xtc.runModule', async (uri?: string, moduleName?: string) => {
            const document = activeEcstasyDocument();
            const source = uri ?? document?.uri.toString();
            const folder = source ? vscode.workspace.getWorkspaceFolder(vscode.Uri.parse(source)) : undefined;
            const mode = buildMode(folder ?? vscode.workspace.workspaceFolders?.[0]);
            moduleName ??= document ? declaredModule(document) : undefined;
            if (!moduleName && !(mode === 'embedded' && source)) {
                moduleName = await vscode.window.showInputBox({
                    prompt: 'Enter Ecstasy module name to run',
                    placeHolder: 'myapp',
                });
                if (!moduleName) {
                    return;
                }
            }

            const definition: XtcTaskDefinition = {
                type: XtcTaskProvider.type,
                moduleName: moduleName ?? path.basename(vscode.Uri.parse(source!).fsPath, '.x'),
                moduleUri: mode === 'embedded' ? source : undefined,
                mode,
                quietMode: true,
            };
            await vscode.tasks.executeTask(createXtcRunTask(definition, client));
        }),

        vscode.commands.registerCommand('xtc.build', () => runBuild(client, false)),
        vscode.commands.registerCommand('xtc.rebuild', () => runBuild(client, true)),

        vscode.commands.registerCommand('xtc.showServerOutput', () => {
            outputChannel.show();
        }),

        vscode.commands.registerCommand('xtc.hideServerOutput', () => {
            outputChannel.hide();
        }),

        vscode.commands.registerCommand('xtc.createProject', async () => {
            const projectName = await vscode.window.showInputBox({
                prompt: 'Enter project name',
                placeHolder: 'myproject',
                validateInput: validateProjectName
            });

            if (!projectName) {
                return;
            }

            const projectType = await vscode.window.showQuickPick(
                ['Application', 'Library', 'Service'],
                { placeHolder: 'Select project type' }
            );

            if (!projectType) {
                return;
            }

            const folderUri = await vscode.window.showOpenDialog({
                canSelectFiles: false,
                canSelectFolders: true,
                canSelectMany: false,
                openLabel: 'Select Parent Folder'
            });

            if (!folderUri || folderUri.length === 0) {
                return;
            }

            const parentPath = folderUri[0].fsPath;
            const terminal = vscode.window.createTerminal('Ecstasy');
            terminal.show();
            terminal.sendText(`xtc init "${projectName}" --type ${projectType.toLowerCase()} --dir "${parentPath}"`);

            vscode.window.showInformationMessage(`Creating Ecstasy ${projectType} project: ${projectName}`);
        })
    );
}
