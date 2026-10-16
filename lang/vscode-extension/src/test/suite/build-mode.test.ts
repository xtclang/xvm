import * as assert from 'node:assert';
import * as fs from 'node:fs';
import * as os from 'node:os';
import * as path from 'node:path';
import * as vscode from 'vscode';
import { buildModeStatusText } from '../../build-mode-selection';
import { buildMode, GRADLE_NOT_USED, hasGradleBuild } from '../../embedded-execution';

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
            assert.ok(GRADLE_NOT_USED.includes('do not apply'), GRADLE_NOT_USED);
            assert.ok(GRADLE_NOT_USED.includes('Ecstasy: Select Build Mode'), GRADLE_NOT_USED);
        } finally {
            fs.rmSync(directory, { recursive: true, force: true });
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
