import * as vscode from 'vscode';
import { LanguageClient } from 'vscode-languageclient/node';

/**
 * The Gradle project of each of the workspace's source modules, by module name, as its path in the build tree, which
 * the language server says from the imported Gradle model: a module's runXtc and testXtc run in it, the only way to
 * reach an included build of a composite build. Each server says it again.
 */
let projects: Readonly<Record<string, string>> = {};
/** Whether the user was told to import the Gradle build, once per session. */
let told = false;

// Gradle's answer to a task that no project of the build has, such as a bare runXtc at a composite build's root
const missingTask = /Task '(run|test)Xtc' not found/;

export function gradleProjects(): Readonly<Record<string, string>> {
    return projects;
}

/** Takes the projects that the language server says (`xtc/gradleProjects`) each time they change. */
export function attachGradleProjects(client: LanguageClient): vscode.Disposable {
    return client.onNotification('xtc/gradleProjects', (params: { projects?: Record<string, string> }) => {
        projects = params.projects ?? {};
    });
}

/** A stopped language server's projects are forgotten; the next server says them again. */
export function forgetGradleProjects(): void {
    projects = {};
}

/**
 * [task] of [module]'s Gradle project, by its path in the build tree from [known], such as `:xdk:lib-json:testXtc`,
 * which reaches a composite build's included builds; the bare name when no imported model names the module's project.
 */
export function gradleTask(module: string | undefined, task: string, known: Readonly<Record<string, string>> = projects): string {
    const project = module === undefined ? undefined : known[module];
    return project === undefined ? task : taskOf(project, task);
}

/** [task] of every project that the imported model names, as for every test module; the bare name without one. */
export function gradleTasks(task: string, known: Readonly<Record<string, string>> = projects): string[] {
    const all = [...new Set(Object.values(known))].sort().map(project => taskOf(project, task));
    return all.length > 0 ? all : [task];
}

/**
 * Says once, when [line] of a Gradle run's errors is Gradle finding no such task while no imported model names the
 * module's project, as at a composite build's root, to import the Gradle build.
 */
export function noticeMissingTask(line: string): void {
    if (!told && Object.keys(projects).length === 0 && missingTask.test(line)) {
        told = true;
        void vscode.window.showInformationMessage(
            'Gradle has no runXtc or testXtc at this build\'s root, as in a composite build, whose included builds a '
            + 'task\'s bare name does not reach. Run "Ecstasy: Refresh Gradle Compiler Model", and runs, debug sessions '
            + 'and tests use the task of each module\'s own project.');
    }
}

/** [task] of the project at [project], its path in the build tree: a root project's path is `:`, whose task is `:runXtc`. */
export function taskOf(project: string, task: string): string {
    return project === ':' ? `:${task}` : `${project}:${task}`;
}
