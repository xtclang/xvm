// Headless integration-test launcher. Downloads (and caches) a VS Code
// build via @vscode/test-electron, then runs the Mocha suite inside that
// VS Code instance with the extension loaded from this build tree.
//
// Invoked by `npm run test:vscode` and by the
// `:lang:vscode-extension:testVscodeExtension` Gradle task.

import { execFileSync } from 'node:child_process';
import * as fs from 'node:fs/promises';
import * as os from 'node:os';
import * as path from 'node:path';
import { runTests } from '@vscode/test-electron';

async function main(): Promise<void> {
    // __dirname at runtime resolves to <ext>/out/test, so the extension
    // root and the fixtures directory are two levels up.
    const extensionDevelopmentPath = path.resolve(__dirname, '..', '..');
    const playbook = process.argv.includes('--playbook');
    const extensionTestsPath = path.resolve(__dirname, playbook ? 'playbook' : 'suite', 'index');
    const reports = path.join(extensionDevelopmentPath, 'build', 'reports', 'compiler-playbook');
    if (playbook) { await fs.mkdir(reports, { recursive: true }); }
    const runDirectory = playbook ? await fs.mkdtemp(path.join(reports, 'run-')) : undefined;
    // Unix-domain sockets inside user-data-dir have a short OS path limit (103 on macOS).
    const profile = playbook ? await fs.mkdtemp(path.join(os.tmpdir(), 'xtc-code-')) : undefined;
    const fixturesPath = runDirectory
        ? path.join(runDirectory, 'workspace')
        : path.resolve(extensionDevelopmentPath, 'src', 'test', 'fixtures');
    if (runDirectory) {
        await fs.mkdir(path.join(fixturesPath, '.vscode'), { recursive: true });
        await fs.writeFile(path.join(fixturesPath, '.vscode', 'settings.json'), JSON.stringify({
            'files.autoSave': 'off', 'editor.semanticHighlighting.enabled': true,
            'editor.inlayHints.enabled': 'on', 'xtc.inlayHints.enabled': true
        }, null, 2));
        console.log(`[compiler-playbook] Reports and isolated workspace: ${runDirectory}`);
        await fs.writeFile(path.join(reports, 'latest-run.txt'), runDirectory + '\n');
    }

    try {
        await runTests({
            extensionDevelopmentPath,
            extensionTestsPath,
            // --disable-extensions stops third-party extensions (anything that
            // claims `.x` — e.g. Logos parser-generator extensions) from
            // preempting our language registration; that way the test asserts
            // OUR behaviour, not the intersection of the user's installed
            // extensions and ours.
            launchArgs: [fixturesPath, '--disable-extensions', ...(runDirectory ? [
                `--user-data-dir=${profile}`, '--skip-welcome', '--skip-release-notes'
            ] : [])],
            extensionTestsEnv: runDirectory ? {
                XTC_PLAYBOOK_REPORT_DIR: runDirectory,
                XTC_PLAYBOOK_COMMIT: execFileSync('git', ['rev-parse', 'HEAD'], { cwd: extensionDevelopmentPath, encoding: 'utf8' }).trim(),
                XTC_PLAYBOOK_DIRTY: execFileSync('git', ['status', '--porcelain'], { cwd: extensionDevelopmentPath, encoding: 'utf8' }).trim()
            } : undefined,
        });
    } catch (error) {
        if (runDirectory) {
            await fs.writeFile(path.join(runDirectory, 'launcher-error.txt'), String(error) + '\n');
        }
        throw error;
    } finally {
        if (runDirectory && profile) {
            await fs.cp(path.join(profile, 'logs'), path.join(runDirectory, 'logs'), { recursive: true }).catch(() => undefined);
            await fs.rm(profile, { recursive: true, force: true });
        }
    }
}

main().catch((err: unknown) => {
    console.error('[vscode-test] failed:', err);
    process.exit(1);
});
