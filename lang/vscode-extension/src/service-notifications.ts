import * as vscode from 'vscode';

/** Standard notification actions; support export also works after a failed launch. */
export function serviceFailure(message: string): void {
    void vscode.window.showErrorMessage(message, 'Open Settings', 'Show Logs', 'Export Logs').then(choice => {
        if (choice === 'Open Settings') return vscode.commands.executeCommand('workbench.action.openSettings', '@ext:xtclang.xtc-language');
        if (choice === 'Show Logs') return vscode.commands.executeCommand('xtc.showServerOutput');
        if (choice === 'Export Logs') return vscode.commands.executeCommand('xtc.exportServerLogs');
        return undefined;
    });
}
