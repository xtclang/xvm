// Headless integration-test launcher. Downloads (and caches) a VS Code
// build via @vscode/test-electron, then runs the Mocha suite inside that
// VS Code instance with the extension loaded from this build tree.
//
// Invoked by `npm run test:vscode` and by the
// `:lang:vscode-extension:testVscodeExtension` Gradle task.

import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import * as fs from 'node:fs/promises';
import * as os from 'node:os';
import * as path from 'node:path';
import { downloadAndUnzipVSCode, runTests } from '@vscode/test-electron';
import { buildDirectory, markUsedAndPrune, removeCheckoutBuilds, sharedCachePath, withDownloadLock } from './vscodeCache';

async function main(): Promise<void> {
    // __dirname at runtime resolves to <ext>/out/test, so the extension
    // root and the fixtures directory are two levels up.
    const extensionDevelopmentPath = path.resolve(__dirname, '..', '..');
    const args = process.argv.slice(2);
    if (args.some(argument => argument !== '--playbook' && argument !== '--multi-root' && !argument.startsWith('--cases='))) {
        throw new Error('Expected --playbook and optional --cases=ID[,ID] and --multi-root');
    }
    const playbook = args.includes('--playbook');
    const multiRoot = args.includes('--multi-root');
    if (multiRoot && !playbook) throw new Error('Use --multi-root with --playbook');
    const selections = args.filter(argument => argument.startsWith('--cases='));
    if (selections.length > 1 || (selections.length > 0 && !playbook)) {
        throw new Error('Use --cases=ID[,ID] once, with --playbook');
    }
    const selected = playbook
        ? (await import('./playbook/shared.js')).selectedScenarioIds(selections[0]?.slice('--cases='.length))
        : [];
    const extensionTestsPath = path.resolve(__dirname, playbook ? 'playbook' : 'suite', 'index');
    const reports = path.join(extensionDevelopmentPath, 'build', 'reports', playbook ? 'compiler-playbook' : 'extension-tests');
    await fs.mkdir(reports, { recursive: true });
    const runDirectory = await fs.mkdtemp(path.join(reports, 'run-'));
    // Unix-domain sockets inside user-data-dir have a short OS path limit (103 on macOS).
    // Smoke tests also mutate settings; never reuse a profile or the repository fixtures.
    const profile = await fs.mkdtemp(path.join(os.tmpdir(), 'xtc-code-'));
    const fixturesPath = path.join(runDirectory, 'workspace');
    if (!playbook) {
        await fs.cp(path.join(extensionDevelopmentPath, 'src', 'test', 'fixtures'), fixturesPath, {
            recursive: true, filter: source => path.basename(source) !== '.vscode'
        });
    }
    await fs.mkdir(path.join(fixturesPath, '.vscode'), { recursive: true });
    await fs.writeFile(path.join(fixturesPath, '.vscode', 'settings.json'), JSON.stringify({
        'files.autoSave': 'off', 'editor.semanticHighlighting.enabled': true,
        'editor.inlayHints.enabled': 'on', 'xtc.inlayHints.enabled': true
    }, null, 2));
    console.log(`[vscode-test] Reports and isolated workspace: ${runDirectory}`);
    await fs.writeFile(path.join(reports, 'latest-run.txt'), runDirectory + '\n');
    const workspaceFile = multiRoot ? path.join(runDirectory, 'compiler.code-workspace') : undefined;
    if (workspaceFile) {
        await fs.mkdir(path.join(runDirectory, 'external'), { recursive: true });
        await fs.writeFile(workspaceFile, JSON.stringify({
            folders: [{ path: 'workspace' }, { path: 'external' }], settings: {}
        }, null, 2) + '\n');
    }

    const manifest = JSON.parse(readFileSync(path.join(extensionDevelopmentPath, 'package.json'), 'utf8')) as {
        engines: { vscode: string };
    };
    const minimumVersion = manifest.engines.vscode.replace(/^\^/, '');
    const cachePath = sharedCachePath();

    for (const build of removeCheckoutBuilds(path.join(extensionDevelopmentPath, '.vscode-test'))) {
        console.log(`[vscode-test] Removed ${build}: builds now live in ${cachePath}`);
    }

    const builds = await withDownloadLock(cachePath, async () => {
        const downloaded: { version: string; executable: string }[] = [];
        for (const version of (playbook ? ['stable'] : [minimumVersion, 'stable'])) {
            downloaded.push({ version, executable: await downloadAndUnzipVSCode({ version, cachePath }) });
        }
        const pruned = markUsedAndPrune(
            cachePath,
            downloaded.map(({ executable }) => buildDirectory(cachePath, executable)),
        );
        for (const build of pruned) {
            console.log(`[vscode-test] Removed ${build}: unused for 30 days`);
        }
        return downloaded;
    });

    try {
        for (const { version, executable } of builds) {
            console.log(`[vscode-test] Running on VS Code ${version} (${executable})`);
        await runTests({
            vscodeExecutablePath: executable,
            extensionDevelopmentPath,
            extensionTestsPath,
            // --disable-extensions stops third-party extensions (anything that
            // claims `.x` — e.g. Logos parser-generator extensions) from
            // preempting our language registration; that way the test asserts
            // OUR behaviour, not the intersection of the user's installed
            // extensions and ours.
            launchArgs: [workspaceFile ?? fixturesPath, '--disable-extensions',
                `--user-data-dir=${profile}`, '--skip-welcome', '--skip-release-notes'
            ],
            extensionTestsEnv: playbook ? {
                XTC_PLAYBOOK_REPORT_DIR: runDirectory,
                XTC_PLAYBOOK_CASES: selected.join(','),
                XTC_PLAYBOOK_COMMIT: execFileSync('git', ['rev-parse', 'HEAD'], { cwd: extensionDevelopmentPath, encoding: 'utf8' }).trim(),
                XTC_PLAYBOOK_DIRTY: execFileSync('git', ['status', '--porcelain'], { cwd: extensionDevelopmentPath, encoding: 'utf8' }).trim()
            } : undefined,
        });
        }
    } catch (error) {
        await fs.writeFile(path.join(runDirectory, 'launcher-error.txt'), String(error) + '\n');
        throw error;
    } finally {
        await fs.cp(path.join(profile, 'logs'), path.join(runDirectory, 'logs'), { recursive: true }).catch(() => undefined);
        await fs.rm(profile, { recursive: true, force: true });
    }
}

main().catch((err: unknown) => {
    console.error('[vscode-test] failed:', err);
    process.exit(1);
});
