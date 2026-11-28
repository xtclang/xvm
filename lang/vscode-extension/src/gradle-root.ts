import * as fs from 'node:fs';
import * as path from 'node:path';
import * as vscode from 'vscode';

import { readBuildModel, type BuildModel, type BuildSourceSet } from './build-model';
import { realPath } from './embedded-execution';
import { taskOf } from './gradle-projects';

/** What marks the directory where a Gradle task of a folder runs: the wrapper that the task runs, or a build's settings. */
const ROOT_FILES = ['gradlew', 'gradlew.bat', 'settings.gradle.kts', 'settings.gradle'];

/** The wrapper alone, which an included build without one of its own runs from the build that includes it. */
const WRAPPER_FILES = ['gradlew', 'gradlew.bat'];

/**
 * Where the Gradle tasks of a folder run, `./gradlew` among them: the directory [root]; and, for a folder whose build the
 * imported Gradle model says is an included build of another, the paths in the build tree of that build's projects that
 * the model names, [projects], as `:included:app`, which address its tasks from the root of the build that includes it.
 */
export interface GradleBuild {
    readonly root: string;
    readonly projects?: readonly string[];
}

/** The directory where the Gradle tasks of [folder] run ([gradleBuild]). */
export function gradleRoot(folder: vscode.WorkspaceFolder, models: readonly BuildModel[] = folderModels()): string {
    return gradleBuild(folder, models).root;
}

/**
 * Where the Gradle tasks of [folder] run. For a folder whose build the imported Gradle models, [models], each workspace
 * folder's, say is an included build of another, as its projects' paths in the build tree say: the root of the build that
 * includes it, where its wrapper is, as the model of the folder whose import ran there names it, its first build root;
 * without such a model, the build's own root when it has a wrapper of its own, as its model may be left over from a
 * build that no longer includes it, else the nearest directory above it that has the wrapper. Else, as without a model,
 * the nearest directory at or above the folder that has the wrapper or a build's settings, as for a folder that is a
 * project inside another folder's Gradle build; the folder itself when none has.
 */
export function gradleBuild(folder: vscode.WorkspaceFolder, models: readonly BuildModel[] = folderModels()): GradleBuild {
    const start = folder.uri.fsPath;
    return includingBuild(start, models) ?? { root: nearest(start, ROOT_FILES) ?? start };
}

/** [names], Gradle tasks of [build]: of each of its projects for an included build ([GradleBuild.projects]), else as they are. */
export function buildTasks(build: GradleBuild, names: readonly string[]): string[] {
    const projects = build.projects;
    return projects === undefined ? [...names] : names.flatMap(name => projects.map(project => taskOf(project, name)));
}

/**
 * The build that runs the Gradle build of [directory] when [models] say that it is an included build of another: the
 * nearest build of the models' projects that holds the directory, whose projects' paths in the build tree are not their
 * paths in their build, as `:included:app` for `:app`. Undefined for another build, one that no model names, and one
 * with a wrapper of its own that no model says another build includes.
 */
function includingBuild(directory: string, models: readonly BuildModel[]): GradleBuild | undefined {
    const entries = models.flatMap(model => model.sourceSets);
    const real = realPath(directory);
    const build = [...new Set(entries.flatMap(entry => buildOf(entry) ?? []))]
        .map(uri => ({ uri, real: realPath(fsPath(uri)) }))
        .filter(root => within(real, root.real))
        .sort((one, other) => other.real.length - one.real.length)[0]?.uri;
    if (build === undefined || !included(build, entries)) return undefined;
    // an import's model has the build it ran in first among its build roots, and the builds it includes after it
    const including = models.find(model => {
        const roots = model.buildRoots ?? [];
        return roots.length > 0 && !same(roots[0], build) && !included(roots[0], entries) && roots.some(root => same(root, build));
    });
    if (including === undefined && has(fsPath(build), WRAPPER_FILES)) return undefined;
    const root = including === undefined ? nearest(path.dirname(fsPath(build)), WRAPPER_FILES) : fsPath(including.buildRoots![0]);
    if (root === undefined) return undefined;
    // its projects' paths in the build tree, as the including build's import has them where there is one
    const projects = (including?.sourceSets ?? entries).filter(entry => ownedBy(entry, build) && inIncludedBuild(entry)).map(entry => entry.buildTreePath!);
    return { root, projects: [...new Set(projects)].sort() };
}

/** The root directory of [entry]'s build, as a file URI: its project id's part before `#`; none in an id without one. */
function buildOf(entry: BuildSourceSet): string | undefined {
    const at = entry.projectId.lastIndexOf('#');
    return at < 0 ? undefined : entry.projectId.slice(0, at);
}

/** Whether [entry]'s project is of the build at [root], a file URI. */
function ownedBy(entry: BuildSourceSet, root: string): boolean {
    const build = buildOf(entry);
    return build !== undefined && same(build, root);
}

/** Whether [entry]'s project is of an included build: its path in the build tree is not its path in its build. */
function inIncludedBuild(entry: BuildSourceSet): boolean {
    return typeof entry.buildTreePath === 'string' && entry.buildTreePath !== entry.projectPath;
}

/** Whether the build at [root], a file URI, is an included build, as its projects' paths in the build tree, of [entries], say. */
function included(root: string, entries: readonly BuildSourceSet[]): boolean {
    return entries.some(entry => ownedBy(entry, root) && inIncludedBuild(entry));
}

/** Whether the file URIs [one] and [other] are the same directory. */
function same(one: string, other: string): boolean {
    return realPath(fsPath(one)) === realPath(fsPath(other));
}

/** The directory of the file URI [uri], without a trailing separator. */
function fsPath(uri: string): string {
    return path.resolve(vscode.Uri.parse(uri).fsPath);
}

/** Whether [file] is [root] or in it, both absolute paths of one kind, as both real ones. */
export function within(file: string, root: string): boolean {
    const relative = path.relative(root, file);
    return relative === '' || (relative.split(path.sep)[0] !== '..' && !path.isAbsolute(relative));
}

/** Whether [directory] has one of [files]. */
function has(directory: string, files: readonly string[]): boolean {
    return files.some(file => fs.existsSync(path.join(directory, file)));
}

/** The nearest directory at or above [start] that has one of [files]; none when no directory up to the root has. */
function nearest(start: string, files: readonly string[]): string | undefined {
    for (let directory = start; ; directory = path.dirname(directory)) {
        if (has(directory, files)) return directory;
        if (path.dirname(directory) === directory) return undefined;
    }
}

/** Each workspace folder's imported Gradle model, as its report has it now: none for a folder without one, or whose one does not parse. */
function folderModels(): BuildModel[] {
    return (vscode.workspace.workspaceFolders ?? []).filter(folder => folder.uri.scheme === 'file').flatMap(folder => {
        try {
            return [readBuildModel(folder.uri.fsPath)].filter(model => model !== undefined);
        } catch {
            return [];
        }
    });
}
