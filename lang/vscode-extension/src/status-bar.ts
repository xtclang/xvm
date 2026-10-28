import * as vscode from 'vscode';

let statusBarItem: vscode.StatusBarItem | undefined;

export function createStatusBar(): vscode.StatusBarItem {
    statusBarItem = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Right, 100);
    return statusBarItem;
}

export function updateStatusBar(state: 'starting' | 'ready' | 'stopped' | 'error', adapter?: string): void {
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
            const label = adapter === 'XDK' ? 'Compiler' : adapter === 'TreeSitter' ? 'Tree-sitter' : adapter;
            statusBarItem.text = `$(check) Ecstasy${label ? ` · ${label}` : ''}`;
            statusBarItem.tooltip = `Ecstasy Language Server: Ready${label ? ` (${label})` : ''} — click to switch adapter`;
            statusBarItem.backgroundColor = undefined;
            statusBarItem.command = 'xtc.selectLanguageAdapter';
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
