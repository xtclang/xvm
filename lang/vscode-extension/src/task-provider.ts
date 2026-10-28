import * as fs from 'node:fs';
import * as path from 'node:path';
import * as vscode from 'vscode';
import type { LanguageClient } from 'vscode-languageclient/node';

import { buildMode, createEmbeddedBuildTask, createEmbeddedRunTask, type BuildMode } from './embedded-execution';

export interface XtcTaskDefinition extends vscode.TaskDefinition {
    moduleName: string;
    methodName?: string;
    /** Comma-separated arguments; prefer [args], which is passed exactly as given. */
    moduleArguments?: string;
    args?: string[];
    /** A source URI identifying the module for embedded runs; the module name is used otherwise. */
    moduleUri?: string;
    mode?: BuildMode;
    /** Legacy switch: true runs with Gradle, false with the `xtc` command line. Absent follows [mode]. */
    useGradle?: boolean;
    quietMode?: boolean;
}

type ClientAccess = () => LanguageClient | undefined;

export class XtcTaskProvider implements vscode.TaskProvider {
    static readonly type = 'xtc';

    constructor(private readonly client: ClientAccess) {}

    provideTasks(): vscode.ProviderResult<vscode.Task[]> {
        const tasks: vscode.Task[] = [];
        const gradlew = process.platform === 'win32' ? 'gradlew.bat' : './gradlew';

        for (const folder of vscode.workspace.workspaceFolders ?? []) {
            if (buildMode(folder) === 'embedded') {
                tasks.push(
                    createEmbeddedBuildTask(this.client, folder, false),
                    createEmbeddedBuildTask(this.client, folder, true)
                );
            } else if (fs.existsSync(path.join(folder.uri.fsPath, 'build.gradle.kts'))) {
                tasks.push(
                    this.createLifecycleTask('Build', `${gradlew} build`, folder, vscode.TaskGroup.Build),
                    this.createLifecycleTask('Test', `${gradlew} test`, folder, vscode.TaskGroup.Test),
                    this.createLifecycleTask('Clean', `${gradlew} clean`, folder, vscode.TaskGroup.Clean)
                );
            }
        }

        return tasks;
    }

    resolveTask(task: vscode.Task): vscode.ProviderResult<vscode.Task> {
        const definition = task.definition as XtcTaskDefinition;
        if (definition.moduleName && !definition.moduleName.startsWith('_')) {
            return createXtcRunTask(definition, this.client);
        }
        return undefined;
    }

    private createLifecycleTask(
        name: string,
        command: string,
        folder: vscode.WorkspaceFolder,
        group: vscode.TaskGroup
    ): vscode.Task {
        const task = new vscode.Task(
            { type: XtcTaskProvider.type, moduleName: `_lifecycle_${name.toLowerCase()}`, mode: 'gradle' } as XtcTaskDefinition,
            folder,
            name,
            'xtc',
            new vscode.ShellExecution(command),
            name === 'Build' || name === 'Test' ? ['$xtc'] : []
        );
        task.group = group;
        task.presentationOptions = { reveal: vscode.TaskRevealKind.Always, panel: vscode.TaskPanelKind.Shared };
        return task;
    }
}

/** How a run definition executes: an explicit mode, the legacy switch, or the folder's build mode. */
function runMode(definition: XtcTaskDefinition): BuildMode | 'cli' {
    if (definition.mode) {
        return definition.mode;
    }
    if (definition.useGradle !== undefined) {
        return definition.useGradle ? 'gradle' : 'cli';
    }
    const uri = definition.moduleUri ? vscode.Uri.parse(definition.moduleUri) : undefined;
    const folder = uri ? vscode.workspace.getWorkspaceFolder(uri) : vscode.workspace.workspaceFolders?.[0];
    return buildMode(folder);
}

export function createXtcRunTask(definition: XtcTaskDefinition, client: ClientAccess): vscode.Task {
    const mode = runMode(definition);
    if (mode === 'embedded') {
        return createEmbeddedRunTask(client, definition, {
            module: definition.moduleUri ?? definition.moduleName,
            label: definition.moduleName,
            method: definition.methodName,
            args: definition.args ?? splitModuleArguments(definition.moduleArguments),
        });
    }

    const command = mode === 'gradle'
        ? buildGradleRunCommand(definition)
        : buildDirectRunCommand(definition);

    const task = new vscode.Task(
        definition,
        vscode.TaskScope.Workspace,
        `Run ${definition.moduleName}`,
        'xtc',
        new vscode.ShellExecution(command),
        []
    );
    task.presentationOptions = { reveal: vscode.TaskRevealKind.Always, panel: vscode.TaskPanelKind.Shared };
    return task;
}

function buildGradleRunCommand(definition: XtcTaskDefinition): string {
    const quietMode = definition.quietMode ?? true;
    const gradlew = process.platform === 'win32' ? 'gradlew.bat' : './gradlew';
    const args: string[] = [
        ...(quietMode ? ['-q'] : []),
        'runXtc',
        ...(definition.moduleName ? [`--module=${definition.moduleName}`] : []),
        ...(definition.methodName ? [`--method=${definition.methodName}`] : []),
        ...(definition.moduleArguments ? [`--args=${definition.moduleArguments}`] : [])
    ];

    return `${gradlew} ${args.join(' ')}`;
}

function buildDirectRunCommand(definition: XtcTaskDefinition): string {
    const args: string[] = [
        'run',
        ...(definition.moduleName ? [definition.moduleName] : []),
        ...splitModuleArguments(definition.moduleArguments)
    ];

    return `xtc ${args.join(' ')}`;
}

function splitModuleArguments(moduleArguments?: string): string[] {
    if (!moduleArguments) {
        return [];
    }

    return moduleArguments
        .split(',')
        .map(arg => arg.trim())
        .filter(Boolean);
}
