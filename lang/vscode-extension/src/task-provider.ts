import * as fs from 'node:fs';
import * as path from 'node:path';
import * as vscode from 'vscode';

export interface XtcTaskDefinition extends vscode.TaskDefinition {
    moduleName: string;
    methodName?: string;
    moduleArguments?: string;
    useGradle?: boolean;
    quietMode?: boolean;
}

export class XtcTaskProvider implements vscode.TaskProvider {
    static readonly type = 'xtc';

    provideTasks(): vscode.ProviderResult<vscode.Task[]> {
        const tasks: vscode.Task[] = [];
        const gradlew = process.platform === 'win32' ? 'gradlew.bat' : './gradlew';

        const { workspaceFolders } = vscode.workspace;
        if (workspaceFolders) {
            for (const folder of workspaceFolders) {
                const buildFile = path.join(folder.uri.fsPath, 'build.gradle.kts');
                if (fs.existsSync(buildFile)) {
                    tasks.push(
                        this.createLifecycleTask('Build', `${gradlew} build`, folder, vscode.TaskGroup.Build),
                        this.createLifecycleTask('Test', `${gradlew} test`, folder, vscode.TaskGroup.Test),
                        this.createLifecycleTask('Clean', `${gradlew} clean`, folder, vscode.TaskGroup.Clean)
                    );
                }
            }
        }

        return tasks;
    }

    resolveTask(task: vscode.Task): vscode.ProviderResult<vscode.Task> {
        const definition = task.definition as XtcTaskDefinition;
        if (definition.moduleName) {
            return createXtcRunTask(definition);
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
            { type: XtcTaskProvider.type, moduleName: `_lifecycle_${name.toLowerCase()}` } as XtcTaskDefinition,
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

export function createXtcRunTask(definition: XtcTaskDefinition): vscode.Task {
    const useGradle = definition.useGradle ?? true;
    const command = useGradle
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
    task.group = vscode.TaskGroup.Build;
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
