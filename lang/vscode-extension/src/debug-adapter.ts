import * as path from 'node:path';
import * as fs from 'node:fs';
import * as vscode from 'vscode';
import type { LanguageClient } from 'vscode-languageclient/node';

import { buildMode } from './embedded-execution';
import { buildJvmArgs, findJavaExecutable } from './java';
import { createXtcRunTask, type XtcTaskDefinition } from './task-provider';

export class XtcDebugAdapterDescriptorFactory implements vscode.DebugAdapterDescriptorFactory {
    constructor(private readonly context: vscode.ExtensionContext) {}

    async createDebugAdapterDescriptor(
        _session: vscode.DebugSession,
        _executable: vscode.DebugAdapterExecutable | undefined
    ): Promise<vscode.DebugAdapterDescriptor | undefined> {
        const serverJar = this.context.asAbsolutePath(path.join('server', 'dap-server.jar'));
        if (!fs.existsSync(serverJar)) {
            void vscode.window.showErrorMessage(
                'Ecstasy DAP server JAR not found. Build lang:vscode-extension to enable debugging.',
                'Show Build Command'
            ).then(choice => {
                if (choice === 'Show Build Command') {
                    void vscode.commands.executeCommand('xtc.showServerOutput');
                }
            });
            return undefined;
        }

        const javaExecutable = await findJavaExecutable(this.context);
        const logLevel = process.env.XTC_LOG_LEVEL?.toUpperCase() ?? 'INFO';
        const jvmArgs = buildJvmArgs(serverJar, logLevel);

        return new vscode.DebugAdapterExecutable(javaExecutable, jvmArgs);
    }
}

/**
 * There is no Ecstasy debugger yet, so Run and Debug launches run the module the same way the Run button
 * does (embedded or Gradle, per the folder's build mode) instead of opening a debug session that cannot stop
 * at breakpoints.
 */
export class XtcDebugConfigurationProvider implements vscode.DebugConfigurationProvider {
    constructor(private readonly client: () => LanguageClient | undefined) {}

    async resolveDebugConfiguration(
        folder: vscode.WorkspaceFolder | undefined,
        config: vscode.DebugConfiguration,
        _token?: vscode.CancellationToken
    ): Promise<vscode.DebugConfiguration | undefined> {
        const resolved = this.fill(config);
        if (!resolved) {
            return undefined;
        }
        if (!resolved.noDebug) {
            void vscode.window.showInformationMessage('Ecstasy debugging is not available yet; running without the debugger.');
        }
        const editor = vscode.window.activeTextEditor;
        const definition: XtcTaskDefinition = {
            type: 'xtc',
            moduleName: resolved.module,
            methodName: resolved.method,
            moduleArguments: typeof resolved.args === 'string' ? resolved.args : undefined,
            args: Array.isArray(resolved.args) ? resolved.args : undefined,
            moduleUri: editor?.document.languageId === 'xtc' ? editor.document.uri.toString() : undefined,
            mode: buildMode(folder),
        };
        await vscode.tasks.executeTask(createXtcRunTask(definition, this.client));
        // returning undefined ends the launch without starting a debug session
        return undefined;
    }

    private fill(config: vscode.DebugConfiguration): vscode.DebugConfiguration | undefined {
        if (!config.type && !config.request && !config.name) {
            const editor = vscode.window.activeTextEditor;
            if (editor?.document.languageId === 'xtc') {
                config.type = 'xtc';
                config.name = 'Run Ecstasy Module';
                config.request = 'launch';
                const text = editor.document.getText();
                const match = /^\s*module\s+(\w+)/m.exec(text);
                if (match) {
                    config.module = match[1];
                }
            }
        }
        if (!config.module) {
            void vscode.window.showInformationMessage('Cannot find an Ecstasy module to run');
            return undefined;
        }
        return config;
    }
}
