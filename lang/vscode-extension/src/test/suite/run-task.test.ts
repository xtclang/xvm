import * as assert from 'node:assert';
import * as fs from 'node:fs/promises';
import * as os from 'node:os';
import * as path from 'node:path';
import * as vscode from 'vscode';
import type { BuildModel, BuildSourceSet } from '../../build-model';
import { gradleTasks } from '../../gradle-projects';
import { buildTasks, gradleBuild, gradleRoot } from '../../gradle-root';
import { createXtcRunTask, gradleBuildTask, runModuleTask, XtcTaskProvider, type XtcTaskDefinition } from '../../task-provider';

/** The command line a shell task runs, its command and its arguments, as the terminal shows it. */
function commandLine(definition: XtcTaskDefinition): string {
    const execution = createXtcRunTask(definition, () => undefined).execution;
    assert.ok(execution instanceof vscode.ShellExecution, 'A Gradle or command-line run is a shell task');
    const value = (part: string | vscode.ShellQuotedString) => typeof part === 'string' ? part : part.value;
    return execution.commandLine ?? [execution.command!, ...(execution.args ?? [])].map(value).join(' ');
}

/** The shell execution's options of [task], a Gradle or command-line run. */
function shellOptions(task: vscode.Task): vscode.ShellExecutionOptions | undefined {
    assert.ok(task.execution instanceof vscode.ShellExecution, 'A Gradle or command-line run is a shell task');
    return task.execution.options;
}

/**
 * Runs [body] with folders, as the window's would be, in a temporary directory outside every folder of the window:
 * [outer], a Gradle build with its wrapper, [sub], a project of it, [included], a build of its own in it, and [lone],
 * a folder without either.
 */
async function withFolders(body: (folders: Record<'outer' | 'sub' | 'included' | 'lone', vscode.WorkspaceFolder>) => Promise<void>): Promise<void> {
    const root = await fs.mkdtemp(path.join(os.tmpdir(), 'xtc-gradle-root-'));
    try {
        const folder = (name: string, index: number): vscode.WorkspaceFolder => ({ uri: vscode.Uri.file(path.join(root, name)), name: path.basename(name), index });
        await fs.mkdir(path.join(root, 'outer', 'sub'), { recursive: true });
        await fs.mkdir(path.join(root, 'outer', 'included'), { recursive: true });
        await fs.mkdir(path.join(root, 'lone'), { recursive: true });
        await fs.writeFile(path.join(root, 'outer', 'gradlew'), '#!/bin/sh\n');
        await fs.writeFile(path.join(root, 'outer', 'included', 'settings.gradle.kts'), 'rootProject.name = "included"\n');
        await body({ outer: folder('outer', 1), sub: folder('outer/sub', 2), included: folder('outer/included', 3), lone: folder('lone', 4) });
    } finally {
        await fs.rm(root, { recursive: true, force: true });
    }
}

/**
 * A source set of the project [projectPath] of the Gradle build at [build], whose path in the build tree that the import
 * ran in is [buildTreePath], as an import's model has it, with a project id as the XTC plugin makes it.
 */
function sourceSet(build: vscode.Uri, projectPath: string, buildTreePath: string): BuildSourceSet {
    const directory = projectPath === ':' ? build : vscode.Uri.joinPath(build, ...projectPath.slice(1).split(':'));
    return {
        projectId: `${build}/#${projectPath}`, projectPath, buildTreePath, projectDirectory: `${directory}/`,
        buildFile: vscode.Uri.joinPath(directory, 'build.gradle.kts').toString(), sourceSet: 'main', sourceRoots: [], sourceFiles: [],
        moduleRoots: [], resourceSourceRoots: [], resourceRoots: [], resourceTask: `${projectPath}:processXtcResources`,
        projectDependencies: [], modulePath: [],
    };
}

/** The model that an import writes: its [sourceSets], and its [builds], the build it ran in first, as file URIs. */
function buildModel(builds: readonly vscode.Uri[], sourceSets: BuildSourceSet[]): BuildModel {
    return { schemaVersion: 1, sourceSets, buildRoots: builds.map(build => `${build}/`) };
}

suite('Run tasks', () => {
    test("a Gradle run task passes its args to runXtc, comma-separated, as it does its moduleArguments", () => {
        assert.strictEqual(commandLine({ type: 'xtc', moduleName: 'App', args: ['Erin', 'Frank'], mode: 'gradle' }),
            `${process.platform === 'win32' ? 'gradlew.bat' : './gradlew'} -q runXtc --module=App --args=Erin,Frank`);
        assert.ok(commandLine({ type: 'xtc', moduleName: 'App', moduleArguments: 'one,two', mode: 'gradle' }).endsWith(' --args=one,two'));
        assert.ok(commandLine({ type: 'xtc', moduleName: 'App', mode: 'gradle' }).endsWith(' runXtc --module=App'));
    });

    test("a command-line run task passes its args to xtc run as they are", () => {
        assert.strictEqual(commandLine({ type: 'xtc', moduleName: 'App', args: ['Erin', 'Frank'], useGradle: false }), 'xtc run App Erin Frank');
        assert.strictEqual(commandLine({ type: 'xtc', moduleName: 'App', moduleArguments: 'one, two', useGradle: false }), 'xtc run App one two');
    });

    test("a Gradle run task runs in the Gradle build of its module's folder, or of the folder of its task", async () => {
        const window = vscode.workspace.workspaceFolders?.[0];
        assert.ok(window, 'The suite opens a folder');
        await withFolders(async ({ outer, sub, lone }) => {
            const cwd = (definition: XtcTaskDefinition, scope?: vscode.WorkspaceFolder) => shellOptions(createXtcRunTask(definition, () => undefined, scope))?.cwd;
            // a folder inside another folder's Gradle build runs there, where the wrapper is
            assert.strictEqual(cwd({ type: 'xtc', moduleName: 'App', mode: 'gradle' }, sub), outer.uri.fsPath);
            // a module in no folder of the window runs in its task's folder, a second one beside the window's
            const outside = vscode.Uri.joinPath(lone.uri, 'App.x').toString();
            assert.strictEqual(cwd({ type: 'xtc', moduleName: 'App', moduleUri: outside, mode: 'gradle' }, lone), lone.uri.fsPath);
            // a module in the window's folder runs there, whatever folder its task is of
            const inside = vscode.Uri.joinPath(window.uri, 'App.x').toString();
            assert.strictEqual(cwd({ type: 'xtc', moduleName: 'App', moduleUri: inside, mode: 'gradle' }, lone), gradleRoot(window));
            assert.notStrictEqual(gradleRoot(window), lone.uri.fsPath);
        });
    });

    test("Run Module's task runs in the Gradle build of the folder of the module's source", async () => {
        const window = vscode.workspace.workspaceFolders?.[0];
        assert.ok(window, 'The suite opens a folder');
        await withFolders(async ({ outer, sub }) => {
            const source = vscode.Uri.joinPath(sub.uri, 'App.x').toString();
            const task = runModuleTask(() => undefined, source, 'App', 'gradle', uri => uri === source ? sub : undefined);
            assert.strictEqual(shellOptions(task)?.cwd, outer.uri.fsPath);
            // a source in no folder takes the window's only folder, as the suite's window has one
            assert.strictEqual(shellOptions(runModuleTask(() => undefined, source, 'App', 'gradle', () => undefined))?.cwd, gradleRoot(window));
        });
    });

    test("a folder's Gradle tasks run at the nearest directory with the wrapper or a build's settings, or in the folder", async () => {
        await withFolders(async ({ outer, sub, included, lone }) => {
            assert.strictEqual(gradleRoot(outer), outer.uri.fsPath);
            assert.strictEqual(gradleRoot(sub), outer.uri.fsPath);
            assert.strictEqual(gradleRoot(included), included.uri.fsPath);
            assert.strictEqual(gradleRoot(lone), lone.uri.fsPath);
        });
    });

    test("an included build without a wrapper runs from the build that includes it, as the imported model says, with its own projects' tasks", async () => {
        await withFolders(async ({ outer, sub, included, lone }) => {
            // outer includes the build in its directory and lone, beside it; outer's import wrote outer's model
            const projects = [sourceSet(outer.uri, ':', ':'), sourceSet(outer.uri, ':sub', ':sub'), sourceSet(included.uri, ':', ':included'),
                sourceSet(included.uri, ':app', ':included:app'), sourceSet(lone.uri, ':', ':lone')];
            const outers = [buildModel([outer.uri, included.uri, lone.uri], projects)];
            const build = gradleBuild(included, outers);
            assert.deepStrictEqual(build, { root: outer.uri.fsPath, projects: [':included', ':included:app'] });
            assert.deepStrictEqual(buildTasks(build, ['clean', 'build']), [':included:clean', ':included:app:clean', ':included:build', ':included:app:build']);
            assert.deepStrictEqual(gradleBuild(lone, outers), { root: outer.uri.fsPath, projects: [':lone'] });
            // the build that includes them, and a project of it, are no included builds
            assert.deepStrictEqual(gradleBuild(outer, outers), { root: outer.uri.fsPath });
            assert.deepStrictEqual(buildTasks(gradleBuild(sub, outers), ['build']), ['build']);
            // with the included builds' own models alone, which name no build that includes them: the nearest wrapper above,
            // which lone, beside outer, has none of, so it runs at its own root, as without a model
            const own = [buildModel([included.uri], projects.slice(2, 4)), buildModel([lone.uri], projects.slice(4))];
            assert.deepStrictEqual(gradleBuild(included, own), { root: outer.uri.fsPath, projects: [':included', ':included:app'] });
            assert.deepStrictEqual(gradleBuild(lone, own), { root: lone.uri.fsPath });
            // and a model of an older plugin, without the paths in the build tree, says nothing of it
            const older = projects.map(entry => ({ ...entry, buildTreePath: undefined }));
            assert.strictEqual(gradleRoot(included, [buildModel([outer.uri, included.uri, lone.uri], older)]), included.uri.fsPath);
            // a report of a run of the build on its own, left over, names its projects by their paths in it, which are no
            // paths in the build tree of the build that includes it, and a root project's tasks are `:build`
            const alone = buildModel([included.uri], [sourceSet(included.uri, ':', ':'), sourceSet(included.uri, ':app', ':app')]);
            assert.deepStrictEqual(gradleBuild(included, [alone, ...outers]), build);
            assert.deepStrictEqual(gradleBuild(included, [alone, ...own]), build);
            assert.deepStrictEqual(buildTasks({ root: outer.uri.fsPath, projects: [':'] }, ['build']), [':build']);
            // with a wrapper of its own, the build that no model says another includes, as one whose model was left over from
            // a build that included it once, runs on its own; a build that a model says outer includes still runs from outer
            await fs.writeFile(path.join(included.uri.fsPath, 'gradlew'), '#!/bin/sh\n');
            assert.deepStrictEqual(gradleBuild(included, own), { root: included.uri.fsPath });
            assert.deepStrictEqual(gradleBuild(included, outers), build);
        });
    });

    test("a folder's Build, Rebuild, Test and Clean run where its Gradle build does, an included build's addressed to its projects", async () => {
        await withFolders(async ({ outer, included }) => {
            const gradlew = process.platform === 'win32' ? 'gradlew.bat' : './gradlew';
            const build = gradleBuild(included, [buildModel([outer.uri, included.uri],
                [sourceSet(included.uri, ':', ':included'), sourceSet(included.uri, ':app', ':included:app')])]);
            const shell = (task: vscode.Task | undefined) => {
                assert.ok(task?.execution instanceof vscode.ShellExecution, 'A Gradle task of the folder is a shell task');
                return { line: task.execution.commandLine, cwd: task.execution.options?.cwd };
            };
            // Ecstasy: Build and Rebuild in Gradle mode, which run the task that the folder's Build is
            const at = outer.uri.fsPath;
            assert.deepStrictEqual(shell(gradleBuildTask(included, false, build)), { line: `${gradlew} :included:build :included:app:build`, cwd: at });
            assert.deepStrictEqual(shell(gradleBuildTask(included, true, build)),
                { line: `${gradlew} :included:clean :included:app:clean :included:build :included:app:build`, cwd: at });
            // the folder's Test and Clean, which a folder with a Gradle build has in either mode, and a folder without one has not
            const tasks = (gradle: typeof build | undefined) => new XtcTaskProvider(() => undefined).folderTasks(included, gradle)
                .filter(task => task.execution instanceof vscode.ShellExecution);
            const named = (name: string) => tasks(build).find(task => task.name === name);
            assert.deepStrictEqual(shell(named('Test')), { line: `${gradlew} ${gradleTasks('testXtc').join(' ')}`, cwd: at });
            assert.deepStrictEqual(shell(named('Clean')), { line: `${gradlew} :included:clean :included:app:clean`, cwd: at });
            assert.deepStrictEqual(tasks(undefined), []);
        });
    });

    test('an argument with a space stays one argument', () => {
        const execution = createXtcRunTask({ type: 'xtc', moduleName: 'App', args: ['Erin Smith', 'Frank'], mode: 'gradle' }, () => undefined).execution;
        assert.ok(execution instanceof vscode.ShellExecution);
        // the shell quotes an argument of a command with arguments; a command line is the shell's to split
        assert.strictEqual(execution.commandLine, undefined);
        assert.ok(execution.args?.includes('--args=Erin Smith,Frank'), JSON.stringify(execution.args));
    });
});
