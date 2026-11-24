import * as assert from 'node:assert';
import * as fs from 'node:fs';
import * as os from 'node:os';
import * as path from 'node:path';
import * as vscode from 'vscode';
import { buildModeChoices, buildModeStatusText, embeddedUnavailable } from '../../build-mode-selection';
import { buildMode, GRADLE_NOT_USED, hasGradleBuild, replacedIn } from '../../embedded-execution';

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
    test('a replaced library reaches only the folders the server names', () => {
        const root = fs.mkdtempSync(path.join(os.tmpdir(), 'xtc-replaced-'));
        try {
            const xdk = path.join(root, 'xdk');
            const other = path.join(root, 'other');
            [xdk, other].forEach(folder => fs.mkdirSync(folder));
            // the server names its folders canonically, as file URIs with a trailing slash
            const scope = { libraries: ['json.xtclang.org'], folders: [`${vscode.Uri.file(fs.realpathSync.native(xdk)).toString()}/`] };
            assert.deepStrictEqual(replacedIn(xdk, scope), ['json.xtclang.org']);
            assert.deepStrictEqual(replacedIn(other, scope), []);
            // no folders: every folder, as from a server that names none
            assert.deepStrictEqual(replacedIn(other, { libraries: ['json.xtclang.org'], folders: [] }), ['json.xtclang.org']);
        } finally {
            fs.rmSync(root, { recursive: true, force: true });
        }
    });
    test('the picker says the embedded compiler is not available where libraries are replaced', () => {
        const description = (replaced: string[], current = 'gradle') =>
            buildModeChoices(current, replaced).find(choice => choice.mode === 'embedded')?.description;
        assert.match(description(['json.xtclang.org']) ?? '', /^Not available: .*json\.xtclang\.org$/);
        assert.match(description(['json.xtclang.org'], 'embedded') ?? '', /; selected$/);
        assert.strictEqual(description([]), undefined);
        assert.strictEqual(description([], 'embedded'), 'Selected');
        assert.strictEqual(buildModeChoices('gradle', ['json.xtclang.org']).find(choice => choice.mode === 'gradle')?.description, 'Selected');
        // a choice of it there, by the picker or the settings, warns that the folder keeps using Gradle
        assert.match(embeddedUnavailable('xvm', ['ecstasy.xtclang.org']), /cannot build or run xvm .*ecstasy\.xtclang\.org.*keeps using Gradle/);
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
