import * as assert from 'node:assert';
import * as fs from 'node:fs';
import * as os from 'node:os';
import * as path from 'node:path';
import * as vscode from 'vscode';
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
        } finally {
            fs.rmSync(directory, { recursive: true, force: true });
        }
    });
});
