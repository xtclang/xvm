import * as assert from 'node:assert';
import * as fs from 'node:fs/promises';
import * as os from 'node:os';
import * as path from 'node:path';
import * as vscode from 'vscode';
import { gradleRoot } from '../../gradle-root';
import { createXtcRunTask, runModuleTask, type XtcTaskDefinition } from '../../task-provider';

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

    test('an argument with a space stays one argument', () => {
        const execution = createXtcRunTask({ type: 'xtc', moduleName: 'App', args: ['Erin Smith', 'Frank'], mode: 'gradle' }, () => undefined).execution;
        assert.ok(execution instanceof vscode.ShellExecution);
        // the shell quotes an argument of a command with arguments; a command line is the shell's to split
        assert.strictEqual(execution.commandLine, undefined);
        assert.ok(execution.args?.includes('--args=Erin Smith,Frank'), JSON.stringify(execution.args));
    });
});
