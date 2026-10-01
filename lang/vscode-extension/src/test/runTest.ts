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
    const extensionRoot = path.resolve(__dirname, '..', '..');
    const args = process.argv.slice(2);
    if (args.length === 1 && args[0] === '--project-lifecycle') {
        await (await import('./runLifecycle.js')).runLifecycle(extensionRoot);
        return;
    }
    if (args.some(argument => !['--playbook', '--multi-root', '--cancel-ui', '--explorer-move-probe', '--refresh-during-move'].includes(argument) && !argument.startsWith('--cases='))) {
        throw new Error('Expected --playbook with optional --cases=ID[,ID], --multi-root and --cancel-ui, or --explorer-move-probe with optional --refresh-during-move');
    }
    const explorerProbe = args.includes('--explorer-move-probe');
    const refreshDuringMove = args.includes('--refresh-during-move');
    if (refreshDuringMove && !explorerProbe) throw new Error('--refresh-during-move requires --explorer-move-probe');
    if (explorerProbe && args.some(argument => !['--explorer-move-probe', '--refresh-during-move'].includes(argument))) {
        throw new Error('Run --explorer-move-probe with only the optional --refresh-during-move flag');
    }
    const playbook = args.includes('--playbook');
    const multiRoot = args.includes('--multi-root');
    const cancelUi = args.includes('--cancel-ui');
    if (multiRoot && !playbook) throw new Error('Use --multi-root with --playbook');
    if (cancelUi && !playbook) throw new Error('Use --cancel-ui with --playbook');
    const selections = args.filter(argument => argument.startsWith('--cases='));
    if (selections.length > 1 || (selections.length > 0 && !playbook)) {
        throw new Error('Use --cases=ID[,ID] once, with --playbook');
    }
    const selected = playbook
        ? (await import('./playbook/shared.js')).selectedScenarioIds(selections[0]?.slice('--cases='.length))
        : [];
    if (cancelUi && !selected.includes('X145')) throw new Error('--cancel-ui requires X145 in the selected cases');
    const extensionTestsPath = path.resolve(__dirname, explorerProbe ? 'explorer-probe' : playbook ? 'playbook' : 'suite', 'index');
    const reports = path.join(extensionRoot, 'build', 'reports', explorerProbe ? 'explorer-probe' : playbook ? 'compiler-playbook' : 'extension-tests');
    await fs.mkdir(reports, { recursive: true });
    const runDirectory = await fs.mkdtemp(path.join(reports, 'run-'));
    const extensionDevelopmentPath = explorerProbe ? path.join(runDirectory, 'empty-extension') : extensionRoot;
    if (explorerProbe) {
        await fs.mkdir(extensionDevelopmentPath);
        await fs.writeFile(path.join(extensionDevelopmentPath, 'package.json'), JSON.stringify({
            name: 'explorer-move-probe', publisher: 'local-test', version: '0.0.0', engines: { vscode: '^1.95.0' }
        }, null, 2) + '\n');
    }
    // Unix-domain sockets inside user-data-dir have a short OS path limit (103 on macOS).
    // Smoke tests also mutate settings; never reuse a profile or the repository fixtures.
    const profile = await fs.mkdtemp(path.join(os.tmpdir(), 'xtc-code-'));
    const fixturesPath = path.join(runDirectory, 'workspace');
    if (!playbook && !explorerProbe) {
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

    try {
        await runTests({
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
                XTC_PLAYBOOK_CANCEL_UI: String(cancelUi),
                XTC_PLAYBOOK_COMMIT: execFileSync('git', ['rev-parse', 'HEAD'], { cwd: extensionDevelopmentPath, encoding: 'utf8' }).trim(),
                XTC_PLAYBOOK_DIRTY: execFileSync('git', ['status', '--porcelain'], { cwd: extensionDevelopmentPath, encoding: 'utf8' }).trim()
            } : explorerProbe ? { XTC_EXPLORER_PROBE_REPORT: runDirectory,
                XTC_EXPLORER_PROBE_REFRESH: String(refreshDuringMove) } : undefined,
        });
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
