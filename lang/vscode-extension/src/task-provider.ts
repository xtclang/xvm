import * as fs from 'node:fs';
import * as path from 'node:path';
import * as vscode from 'vscode';
import type { LanguageClient } from 'vscode-languageclient/node';

import { buildMode, createEmbeddedBuildTask, createEmbeddedRunTask, type BuildMode } from './embedded-execution';
import { gradleProjects, gradleTask, gradleTasks } from './gradle-projects';
import { buildTasks, gradleBuild, gradleRoot, within, type GradleBuild } from './gradle-root';

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
        return (vscode.workspace.workspaceFolders ?? []).flatMap(folder => this.folderTasks(folder));
    }

    /**
     * The tasks of [folder]: its embedded Build and Rebuild in embedded mode; for a folder with a Gradle build, whose tasks
     * run where [build] says, its Gradle Build in Gradle mode, and its Test and Clean in either, as embedded mode has none.
     */
    folderTasks(folder: vscode.WorkspaceFolder,
        build = fs.existsSync(path.join(folder.uri.fsPath, 'build.gradle.kts')) ? gradleBuild(folder) : undefined): vscode.Task[] {
        const tasks: vscode.Task[] = [];
        if (buildMode(folder) === 'embedded') {
            tasks.push(
                createEmbeddedBuildTask(this.client, folder, false),
                createEmbeddedBuildTask(this.client, folder, true)
            );
        } else if (build) {
            tasks.push(gradleBuildTask(folder, false, build));
        }
        if (build) {
            tasks.push(
                lifecycleTask('Test', gradleTasks('testXtc'), folder, build, vscode.TaskGroup.Test),
                lifecycleTask('Clean', buildTasks(build, ['clean']), folder, build, vscode.TaskGroup.Clean)
            );
        }
        return tasks;
    }

    resolveTask(task: vscode.Task): vscode.ProviderResult<vscode.Task> {
        const definition = task.definition as XtcTaskDefinition;
        if (definition.moduleName && !definition.moduleName.startsWith('_')) {
            // a task of tasks.json belongs to its folder, whose build mode it takes
            return createXtcRunTask(definition, this.client, typeof task.scope === 'object' ? task.scope : undefined);
        }
        return undefined;
    }
}

/** The Gradle wrapper that a folder's Gradle tasks run. */
const GRADLEW = process.platform === 'win32' ? 'gradlew.bat' : './gradlew';

/**
 * [folder]'s Gradle Build, or with [rebuild] its Rebuild, which Ecstasy: Build and Rebuild run in Gradle mode, as the
 * folder's own Build task is: its build's `build`, after `clean` for Rebuild, of an included build's projects
 * ([buildTasks]), where [build] says its tasks run.
 */
export function gradleBuildTask(folder: vscode.WorkspaceFolder, rebuild: boolean, build: GradleBuild = gradleBuild(folder)): vscode.Task {
    return lifecycleTask(rebuild ? 'Rebuild' : 'Build', buildTasks(build, rebuild ? ['clean', 'build'] : ['build']), folder, build, vscode.TaskGroup.Build);
}

/** The task [name] of [folder]'s Gradle build, [build], in [group]: the wrapper with [tasks], where [build] says its tasks run. */
function lifecycleTask(name: string, tasks: readonly string[], folder: vscode.WorkspaceFolder, build: GradleBuild, group: vscode.TaskGroup): vscode.Task {
    const lifecycle = group === vscode.TaskGroup.Test ? 'test' : group === vscode.TaskGroup.Clean ? 'clean' : 'build';
    const task = new vscode.Task(
        { type: XtcTaskProvider.type, moduleName: `_lifecycle_${lifecycle}`, mode: 'gradle' } as XtcTaskDefinition,
        folder,
        name,
        'xtc',
        new vscode.ShellExecution(`${GRADLEW} ${tasks.join(' ')}`, { cwd: build.root }),
        group === vscode.TaskGroup.Clean ? [] : ['$xtc']
    );
    task.group = group;
    task.presentationOptions = { reveal: vscode.TaskRevealKind.Always, panel: vscode.TaskPanelKind.Shared };
    return task;
}

/**
 * The folder that a run of the module at [moduleUri] takes its build mode from: the folder that holds the module, or
 * else [scope], the folder that the run's task belongs to, or else the only folder open. None in a window of several
 * folders, where [buildMode] takes Gradle as soon as one of them builds and runs with it, rather than the first folder's
 * mode.
 */
export function runFolder(moduleUri: string | undefined, scope?: vscode.WorkspaceFolder): vscode.WorkspaceFolder | undefined {
    const owner = moduleUri ? vscode.workspace.getWorkspaceFolder(vscode.Uri.parse(moduleUri)) : undefined;
    const folders = vscode.workspace.workspaceFolders ?? [];
    return owner ?? scope ?? (folders.length === 1 ? folders[0] : undefined);
}

/** How a run definition executes: an explicit mode, the legacy switch, or the build mode of its folder ([runFolder]). */
function runMode(definition: XtcTaskDefinition, scope?: vscode.WorkspaceFolder): BuildMode | 'cli' {
    if (definition.mode) {
        return definition.mode;
    }
    if (definition.useGradle !== undefined) {
        return definition.useGradle ? 'gradle' : 'cli';
    }
    return buildMode(runFolder(definition.moduleUri, scope));
}

/**
 * The folder whose build runs Ecstasy: Run Module's module, of the source [source], or of none, as for a module name
 * typed in: the folder of [folders], the window's, that holds the source, or the window's only one. In a window of
 * several, for a source that none of them holds, or none, the one that [pick], VS Code's folder picker, answers, as the
 * module's name does not tell which folder's build has it: the server names a module's project by its path in its build
 * tree, which two builds' root projects share as `:`. Undefined when the pick is canceled; no folder in a window without.
 */
export async function moduleRunFolder(source: string | undefined, folders: readonly vscode.WorkspaceFolder[] = vscode.workspace.workspaceFolders ?? [],
    pick: () => Thenable<vscode.WorkspaceFolder | undefined> = () => vscode.window.showWorkspaceFolderPick({ placeHolder: 'The folder whose build runs the module' })):
    Promise<{ folder?: vscode.WorkspaceFolder } | undefined> {
    const file = source === undefined ? undefined : vscode.Uri.parse(source);
    // the innermost of them, as VS Code takes a file's folder
    const owner = file && folders.filter(folder => folder.uri.scheme === file.scheme && within(file.fsPath, folder.uri.fsPath))
        .sort((one, other) => other.uri.fsPath.length - one.uri.fsPath.length)[0];
    if (owner || folders.length < 2) return { folder: owner ?? folders[0] };
    const picked = await pick();
    return picked && { folder: picked };
}

/**
 * Ecstasy: Run Module's task, from a Run lens, an editor or the command: the module [moduleName] of the source [source],
 * in [mode]. An embedded run takes the source itself; every run takes [folder], by default the folder of the source
 * ([runFolder]), as its task's, where it runs, as a module's name alone does not tell it in a window of several folders.
 */
export function runModuleTask(client: ClientAccess, source: string | undefined, moduleName: string, mode: BuildMode,
    folder: vscode.WorkspaceFolder | undefined = runFolder(source)): vscode.Task {
    const definition: XtcTaskDefinition = {
        type: XtcTaskProvider.type,
        moduleName,
        moduleUri: mode === 'embedded' ? source : undefined,
        mode,
        quietMode: true,
    };
    return createXtcRunTask(definition, client, folder);
}

/** The task that runs [definition]'s module, in the build mode of its folder, or of [scope], the folder its task is of. */
export function createXtcRunTask(definition: XtcTaskDefinition, client: ClientAccess, scope?: vscode.WorkspaceFolder): vscode.Task {
    const mode = runMode(definition, scope);
    if (mode === 'embedded') {
        return createEmbeddedRunTask(client, definition, {
            module: definition.moduleUri ?? definition.moduleName,
            // a saved task keeps its root URI after the file is renamed or moved; the name still finds the module
            moduleName: definition.moduleName,
            label: definition.moduleName,
            method: definition.methodName,
            args: moduleArguments(definition),
            folder: runFolder(definition.moduleUri, scope),
        });
    }

    const { command, args } = mode === 'gradle'
        ? gradleRunCommand(definition)
        : directRunCommand(definition);
    // where the module's folder's Gradle build is: a task of the window's runs where VS Code's terminal starts, which in
    // a window of several folders need not be that folder
    const folder = runFolder(definition.moduleUri, scope);

    const task = new vscode.Task(
        definition,
        vscode.TaskScope.Workspace,
        `Run ${definition.moduleName}`,
        'xtc',
        new vscode.ShellExecution(command, args, folder ? { cwd: gradleRoot(folder) } : undefined),
        []
    );
    task.presentationOptions = { reveal: vscode.TaskRevealKind.Always, panel: vscode.TaskPanelKind.Shared };
    return task;
}

/** The arguments a run passes to the module's entry method: the definition's args, or else its moduleArguments. */
function moduleArguments(definition: XtcTaskDefinition): string[] {
    return definition.args ?? splitModuleArguments(definition.moduleArguments);
}

/**
 * The Gradle wrapper and its arguments that run the definition's module with runXtc, in the module's Gradle project
 * from [projects], which takes the module's arguments comma-separated.
 */
export function gradleRunCommand(definition: XtcTaskDefinition, projects = gradleProjects()): { command: string; args: string[] } {
    const quietMode = definition.quietMode ?? true;
    const passed = moduleArguments(definition);
    return {
        command: process.platform === 'win32' ? 'gradlew.bat' : './gradlew',
        args: [
            ...(quietMode ? ['-q'] : []),
            gradleTask(definition.moduleName, 'runXtc', projects),
            ...(definition.moduleName ? [`--module=${definition.moduleName}`] : []),
            ...(definition.methodName ? [`--method=${definition.methodName}`] : []),
            ...(passed.length > 0 ? [`--args=${passed.join(',')}`] : []),
        ],
    };
}

/** The xtc command and its arguments that run the definition's module, with the module's arguments as they are. */
export function directRunCommand(definition: XtcTaskDefinition): { command: string; args: string[] } {
    return {
        command: 'xtc',
        args: ['run', ...(definition.moduleName ? [definition.moduleName] : []), ...moduleArguments(definition)],
    };
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
