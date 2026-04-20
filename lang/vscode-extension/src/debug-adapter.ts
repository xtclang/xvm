import * as path from 'node:path';
import * as fs from 'node:fs';
import * as vscode from 'vscode';

import { buildJvmArgs, findJavaExecutable } from './java';

export class XtcDebugAdapterDescriptorFactory implements vscode.DebugAdapterDescriptorFactory {
    constructor(private readonly context: vscode.ExtensionContext) {}

    createDebugAdapterDescriptor(
        _session: vscode.DebugSession,
        _executable: vscode.DebugAdapterExecutable | undefined
    ): vscode.ProviderResult<vscode.DebugAdapterDescriptor> {
        const serverJar = this.context.asAbsolutePath(path.join('server', 'dap-server.jar'));
        if (!fs.existsSync(serverJar)) {
            void vscode.window.showErrorMessage(
                'XTC DAP server JAR not found. Build lang:vscode-extension to enable debugging.',
                'Show Build Command'
            ).then(choice => {
                if (choice === 'Show Build Command') {
                    void vscode.commands.executeCommand('xtc.showServerOutput');
                }
            });
            return undefined;
        }

        const javaExecutable = findJavaExecutable();
        const logLevel = process.env.XTC_LOG_LEVEL?.toUpperCase() ?? 'INFO';
        const jvmArgs = buildJvmArgs(serverJar, logLevel);

        return new vscode.DebugAdapterExecutable(javaExecutable, jvmArgs);
    }
}

export class XtcDebugConfigurationProvider implements vscode.DebugConfigurationProvider {
    resolveDebugConfiguration(
        _folder: vscode.WorkspaceFolder | undefined,
        config: vscode.DebugConfiguration,
        _token?: vscode.CancellationToken
    ): vscode.ProviderResult<vscode.DebugConfiguration> {
        if (!config.type && !config.request && !config.name) {
            const editor = vscode.window.activeTextEditor;
            if (editor?.document.languageId === 'xtc') {
                config.type = 'xtc';
                config.name = 'Debug XTC Module';
                config.request = 'launch';
                const text = editor.document.getText();
                const match = /^\s*module\s+(\w+)/m.exec(text);
                if (match) {
                    config.module = match[1];
                }
            }
        }
        if (!config.module) {
            return vscode.window.showInformationMessage('Cannot find an XTC module to debug').then(() => undefined);
        }
        return config;
    }
}
