import * as assert from 'node:assert';
import * as fs from 'node:fs';
import * as os from 'node:os';
import * as path from 'node:path';
import * as vscode from 'vscode';
import type { LanguageClient } from 'vscode-languageclient/node';
import { buildModeChoices, buildModeStatusText, embeddedUnavailable } from '../../build-mode-selection';
import {
    attachEmbeddedExecution, buildMode, forgetReplacedLibraries, gradleReason, gradleReasonIn, GRADLE_NOT_USED, hasGradleBuild,
} from '../../embedded-execution';
import { createXtcRunTask, runFolder } from '../../task-provider';

suite('Build mode', () => {
    test('embedded is the default, also for a folder with a Gradle build', () => {
        assert.strictEqual(vscode.workspace.getConfiguration('xtc').inspect<string>('build.mode')?.defaultValue, 'embedded');
        const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'xtc-build-mode-'));
        try {
            const folder: vscode.WorkspaceFolder = { uri: vscode.Uri.file(directory), name: 'gradle', index: 0 };
            assert.strictEqual(hasGradleBuild(folder), false);
            fs.writeFileSync(path.join(directory, 'settings.gradle.kts'), '');
            assert.strictEqual(hasGradleBuild(folder), true);
            assert.strictEqual(buildMode(folder), 'embedded');
            assert.ok(GRADLE_NOT_USED.includes('only xtcRun\'s method, arguments and JVM options'), GRADLE_NOT_USED);
            assert.ok(GRADLE_NOT_USED.includes('Ecstasy: Select Build Mode'), GRADLE_NOT_USED);
        } finally {
            fs.rmSync(directory, { recursive: true, force: true });
        }
    });
    test('a replaced library reaches only the folders the server names', () => {
        const root = fs.mkdtempSync(path.join(os.tmpdir(), 'xtc-replaced-'));
        try {
            const xdk = path.join(root, 'xdk');
            const other = path.join(root, 'other');
            [xdk, other].forEach(folder => fs.mkdirSync(folder));
            // the server names its folders canonically, as file URIs with a trailing slash
            const uri = `${vscode.Uri.file(fs.realpathSync.native(xdk)).toString()}/`;
            const reason = 'replaces the bundled libraries json.xtclang.org with its own modules';
            const scope = { libraries: ['json.xtclang.org'], folders: [uri], reasons: { [uri]: reason } };
            assert.strictEqual(gradleReasonIn(xdk, scope), reason);
            assert.strictEqual(gradleReasonIn(other, scope), undefined);
            // a server that gives no reason, as an older one, replaces the libraries for the folders it names
            assert.strictEqual(gradleReasonIn(xdk, { ...scope, reasons: {} }), reason);
            // no folders: every folder, as from a server that names none
            assert.strictEqual(gradleReasonIn(other, { libraries: ['json.xtclang.org'], folders: [], reasons: {} }), reason);
        } finally {
            fs.rmSync(root, { recursive: true, force: true });
        }
    });
    test('the picker says the embedded compiler is not available where libraries are replaced', () => {
        const replaced = 'replaces the bundled libraries json.xtclang.org with its own modules';
        const description = (reason: string | undefined, current = 'gradle') =>
            buildModeChoices(current, reason).find(choice => choice.mode === 'embedded')?.description;
        assert.match(description(replaced) ?? '', /^Not available: this folder replaces .*json\.xtclang\.org with its own modules$/);
        assert.match(description(replaced, 'embedded') ?? '', /; selected$/);
        assert.strictEqual(description(undefined), undefined);
        assert.strictEqual(description(undefined, 'embedded'), 'Selected');
        assert.strictEqual(buildModeChoices('gradle', replaced).find(choice => choice.mode === 'gradle')?.description, 'Selected');
        // a choice of it there, by the picker or the settings, warns that the folder keeps using Gradle
        assert.match(embeddedUnavailable('xvm', 'replaces the bundled libraries ecstasy.xtclang.org with its own modules'),
            /cannot build or run xvm, which replaces .*ecstasy\.xtclang\.org.*keeps using Gradle/);
    });
    test('a folder on another XDK than the bundled one builds with Gradle beside one on the bundled XDK, saying which', () => {
        const root = fs.mkdtempSync(path.join(os.tmpdir(), 'xtc-other-xdk-'));
        // the server's notice, as it comes from the connection
        const handlers = new Map<string, (params: unknown) => void>();
        const client = {
            onNotification: (method: string, handler: (params: unknown) => void) => {
                handlers.set(method, handler);
                return new vscode.Disposable(() => handlers.delete(method));
            },
        } as unknown as LanguageClient;
        const attached = attachEmbeddedExecution(client);
        try {
            const [foreign, bundled] = ['foreign', 'bundled'].map((name, index) => {
                fs.mkdirSync(path.join(root, name));
                return { uri: vscode.Uri.file(path.join(root, name)), name, index } satisfies vscode.WorkspaceFolder;
            });
            const uri = `${vscode.Uri.file(fs.realpathSync.native(foreign.uri.fsPath)).toString()}/`;
            const reason = 'builds with XDK 0.5.0, not the bundled XDK 0.4.4';
            handlers.get('xtc/replacedLibraries')?.({ libraries: [], folders: [uri], reasons: { [uri]: reason } });
            assert.strictEqual(gradleReason(foreign), reason);
            assert.strictEqual(buildMode(foreign), 'gradle');
            assert.strictEqual(gradleReason(bundled), undefined);
            assert.strictEqual(buildMode(bundled), 'embedded');
            // the picker, the warning on a choice of the embedded compiler and the status bar say which XDK
            assert.strictEqual(buildModeChoices('embedded', gradleReason(foreign)).find(choice => choice.mode === 'embedded')?.description,
                `Not available: this folder ${reason}; selected`);
            assert.match(embeddedUnavailable('foreign', reason), /cannot build or run foreign, which builds with XDK 0\.5\.0, not the bundled XDK 0\.4\.4:/);
            // a run without a folder, as a multi-root workspace's own launch configuration, uses Gradle where one does
            assert.strictEqual(gradleReason(undefined), reason);
            assert.strictEqual(buildMode(undefined), 'gradle');
            // a run task of tasks.json, which names no module file, takes its own folder's mode, not the first folder's
            const run = (scope: vscode.WorkspaceFolder) => createXtcRunTask({ type: 'xtc', moduleName: 'App' }, () => undefined, scope).execution;
            assert.ok(run(foreign) instanceof vscode.ShellExecution, 'the foreign folder runs App with Gradle');
            assert.ok(!(run(bundled) instanceof vscode.ShellExecution), 'the bundled folder runs App embedded');
            assert.strictEqual(runFolder(undefined, foreign), foreign);
            // Run Module with a module name typed in: the only folder of the window, or none of several
            const open = vscode.workspace.workspaceFolders ?? [];
            assert.strictEqual(runFolder(undefined), open.length === 1 ? open[0] : undefined);
            assert.strictEqual(gradleReasonIn(undefined, { libraries: [], folders: [], reasons: {} }), undefined);
            // a stopped server builds no folder with Gradle any more
            forgetReplacedLibraries();
            assert.strictEqual(buildMode(undefined), 'embedded');
            assert.strictEqual(buildMode(foreign), 'embedded');
        } finally {
            forgetReplacedLibraries();
            attached.dispose();
            fs.rmSync(root, { recursive: true, force: true });
        }
    });
    test('the status bar names the effective mode', () => {
        assert.strictEqual(buildModeStatusText('embedded'), '$(tools) Embedded build');
        assert.strictEqual(buildModeStatusText('gradle'), '$(tools) Gradle build');
    });
    test('Select Build Mode stores the choice for the folder and refuses unknown modes', async () => {
        const folder = vscode.workspace.workspaceFolders?.[0];
        assert.ok(folder, 'the test window opens a folder');
        const settings = () => vscode.workspace.getConfiguration('xtc', folder.uri);
        const before = settings().inspect<string>('build.mode')?.workspaceValue;
        try {
            await vscode.commands.executeCommand('xtc.selectBuildMode', 'gradle', folder.uri);
            assert.strictEqual(settings().get('build.mode'), 'gradle');
            assert.strictEqual(buildMode(folder), 'gradle');
            await vscode.commands.executeCommand('xtc.selectBuildMode', 'auto', folder.uri);
            assert.strictEqual(settings().get('build.mode'), 'auto');
            await assert.rejects(async () => vscode.commands.executeCommand('xtc.selectBuildMode', 'maven', folder.uri), /maven/);
            assert.strictEqual(settings().get('build.mode'), 'auto');
        } finally {
            await settings().update('build.mode', before, vscode.ConfigurationTarget.Workspace);
        }
    });
});
