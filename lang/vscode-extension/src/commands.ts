import * as vscode from 'vscode';

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

export function registerCommands(context: vscode.ExtensionContext, outputChannel: vscode.OutputChannel): void {
    context.subscriptions.push(
        vscode.commands.registerCommand('xtc.runModule', async (_uri: string, moduleName: string) => {
            if (!moduleName) {
                const input = await vscode.window.showInputBox({
                    prompt: 'Enter XTC module name to run',
                    placeHolder: 'myapp',
                });
                if (!input) {
                    return;
                }
                moduleName = input;
            }

            const definition: XtcTaskDefinition = {
                type: XtcTaskProvider.type,
                moduleName,
                useGradle: true,
                quietMode: true,
            };
            await vscode.tasks.executeTask(createXtcRunTask(definition));
        }),

        vscode.commands.registerCommand('xtc.showServerOutput', () => {
            outputChannel.show();
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
            const terminal = vscode.window.createTerminal('XTC');
            terminal.show();
            terminal.sendText(`xtc init "${projectName}" --type ${projectType.toLowerCase()} --dir "${parentPath}"`);

            vscode.window.showInformationMessage(`Creating XTC ${projectType} project: ${projectName}`);
        })
    );
}
