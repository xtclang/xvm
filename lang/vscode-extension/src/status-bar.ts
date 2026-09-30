import * as vscode from 'vscode';

let statusBarItem: vscode.StatusBarItem | undefined;

export function createStatusBar(): vscode.StatusBarItem {
    statusBarItem = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Right, 100);
    return statusBarItem;
}

export function updateStatusBar(state: 'starting' | 'ready' | 'stopped' | 'error'): void {
    if (!statusBarItem) {
        return;
    }

    switch (state) {
        case 'starting':
            statusBarItem.text = '$(sync~spin) Ecstasy';
            statusBarItem.tooltip = 'Ecstasy Language Server: Starting...';
            statusBarItem.backgroundColor = undefined;
            statusBarItem.command = 'xtc.showServerOutput';
            break;
        case 'ready':
            statusBarItem.text = '$(check) Ecstasy';
            statusBarItem.tooltip = 'Ecstasy Language Server: Ready';
            statusBarItem.backgroundColor = undefined;
            statusBarItem.command = 'xtc.showServerOutput';
            break;
        case 'stopped':
            statusBarItem.text = '$(error) Ecstasy';
            statusBarItem.tooltip = 'Ecstasy Language Server: Stopped - click to restart';
            statusBarItem.backgroundColor = new vscode.ThemeColor('statusBarItem.errorBackground');
            statusBarItem.command = 'xtc.restartServer';
            break;
        case 'error':
            statusBarItem.text = '$(warning) Ecstasy';
            statusBarItem.tooltip = 'Ecstasy Language Server: Error - click to restart';
            statusBarItem.backgroundColor = new vscode.ThemeColor('statusBarItem.warningBackground');
            statusBarItem.command = 'xtc.restartServer';
            break;
    }

    statusBarItem.show();
}
