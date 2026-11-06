// Launches the VS Code tour: the extension's features, step by step, in a VS Code window that edits, runs, debugs
// and tests the card game of lang/test-fixtures/tour, in a project that `xtc init` makes, as Ecstasy: Create New
// Project does. The tour (tour/index.ts) captures the screen for the tour's video and slides, which
// lang/intellij-plugin/tour/compose.py makes from the run's tour.json and frames, and checks what each step shows.
//
// Invoked by `npm run test:tour` and by `:lang:vscode-extension:testCompilerPlaybook -PvscodeTour=true`; it runs in
// the playbook container, on its virtual display, whose screen it captures.

import { execFile } from 'node:child_process';
import * as fs from 'node:fs/promises';
import * as os from 'node:os';
import * as path from 'node:path';
import { promisify } from 'node:util';
import { runTests } from '@vscode/test-electron';
import { testVSCodeBuild } from './vscodeCache';
import { workbenchPort } from './workbenchUi';

/** The tour's project, which the card game's sources replace the sources of. */
const PROJECT = 'cardGame';

/**
 * Runs the tour; `--tour-sections=<ids>` records only those sections, as the container's memory needs, and
 * `--tour-hang=<seconds>` shortens the bound of its waits against a hang, an hour.
 */
export async function runTour(extensionRoot: string, args: readonly string[]): Promise<void> {
    const option = (name: string) => args.find(argument => argument.startsWith(`--${name}=`))?.slice(name.length + 3) ?? '';
    const unknown = args.filter(argument => !/^--tour-(sections|hang)=/.test(argument));
    if (unknown.length) throw new Error(`Expected --tour with an optional --tour-sections=<ids> and --tour-hang=<seconds>, not ${unknown.join(' ')}`);
    const sections = option('tour-sections');
    const hang = option('tour-hang');
    if (hang && !/^[1-9]\d*$/.test(hang)) throw new Error(`--tour-hang takes whole seconds, not ${hang}`);
    const { version, executable } = await testVSCodeBuild(extensionRoot);
    console.log(`[vscode-tour] Running on VS Code ${version} (${executable})`);
    const reports = path.join(extensionRoot, 'build', 'reports', 'vscode-tour');
    await fs.mkdir(reports, { recursive: true });
    const run = await fs.mkdtemp(path.join(reports, 'run-'));
    const xdk = path.resolve(extensionRoot, '..', '..', 'xdk', 'build', 'install', 'xdk');
    const fixture = path.resolve(extensionRoot, '..', 'test-fixtures', 'tour', PROJECT, 'src');
    const project = await cardGame(xdk, fixture);
    console.log(`[vscode-tour] Reports: ${run}; project: ${project}`);
    // Unix-domain sockets inside the user data directory have a short path limit (103 on macOS)
    const profile = await fs.mkdtemp(path.join(os.tmpdir(), 'xtc-tour-'));
    await fs.mkdir(path.join(profile, 'User'), { recursive: true });
    await fs.writeFile(path.join(profile, 'User', 'settings.json'), JSON.stringify(TOUR_SETTINGS, null, 2) + '\n');
    // no key opens the developer tools, whose window would stay over the recording; Linux's Ctrl+Shift+I is also
    // Format Document's
    await fs.writeFile(path.join(profile, 'User', 'keybindings.json'), JSON.stringify([
        { key: 'ctrl+shift+i', command: '-workbench.action.toggleDevTools' },
        { key: 'ctrl+alt+i', command: '-workbench.action.toggleDevTools' },
    ], null, 2) + '\n');
    const uiPort = await workbenchPort();
    try {
        await runTests({
            vscodeExecutablePath: executable,
            extensionDevelopmentPath: extensionRoot,
            extensionTestsPath: path.resolve(__dirname, 'tour', 'index'),
            // the other extensions stay out, as in the playbook: one that claims .x would take the card game's files
            launchArgs: [project, '--disable-extensions', `--user-data-dir=${profile}`, '--skip-welcome', '--skip-release-notes',
                '--disable-workspace-trust', `--remote-debugging-port=${uiPort}`, '--remote-debugging-address=127.0.0.1'],
            extensionTestsEnv: {
                XTC_TOUR_REPORT_DIR: run,
                XTC_TOUR_SECTIONS: sections,
                XTC_TOUR_HANG: hang,
                XTC_TOUR_FIXTURE: fixture,
                XTC_TOUR_XDK: xdk,
                XTC_TOUR_LOGS: path.join(profile, 'logs'),
                XTC_PLAYBOOK_UI_PORT: String(uiPort),
                // the container's screen recording asks the steps to act by input that shows, at a pace a viewer follows
                XTC_RECORD_VIDEO: process.env.XTC_RECORD_VIDEO ?? '',
                XTC_RECORDING_START_FILE: process.env.XTC_RECORDING_START_FILE ?? '',
                XTC_LSP_LOG_DIR: path.join(run, 'server-logs'),
                // A checkout exported without .git, such as the container's, gets it from its caller.
                XTC_COMMIT: process.env.XTC_COMMIT ?? await git(extensionRoot, 'rev-parse', 'HEAD'),
                // the XDK's launchers are on the PATH, as an installed XDK's are, for the terminal and Create Project
                PATH: `${path.join(xdk, 'bin')}${path.delimiter}${process.env.PATH ?? ''}`,
            },
        });
    } catch (error) {
        await fs.writeFile(path.join(run, 'launcher-error.txt'), String(error) + '\n');
        throw error;
    } finally {
        await fs.cp(path.join(profile, 'logs'), path.join(run, 'logs'), { recursive: true }).catch(() => undefined);
        await fs.rm(profile, { recursive: true, force: true });
        await fs.writeFile(path.join(run, '.completed'), 'VS Code exited\n');
        console.log(`[vscode-tour] ${path.join(run, 'tour.json')}`);
    }
}

/**
 * The tour's project, in ~/projects as a user's would be: a project that `xtc init` makes, as Ecstasy: Create New
 * Project does, with the card game's sources in place of its own, so that it builds, runs and tests with Gradle as
 * well as embedded. The container's home is new for each run; elsewhere, the project must not be there yet.
 */
async function cardGame(xdk: string, fixture: string): Promise<string> {
    const projects = path.join(os.homedir(), 'projects');
    const project = path.join(projects, PROJECT);
    if (await fs.stat(project).then(() => true, () => false)) throw new Error(`${project} is in the way of the tour's project`);
    await fs.mkdir(projects, { recursive: true });
    const xtc = path.join(xdk, 'bin', process.platform === 'win32' ? 'xtc.bat' : 'xtc');
    await promisify(execFile)(xtc, ['init', PROJECT, '--dir', projects], { shell: process.platform === 'win32' })
        .catch(error => { throw new Error(`${xtc} init failed; run ./gradlew xdk:installDist first: ${error}`); });
    await fs.rm(path.join(project, 'src'), { recursive: true, force: true });
    await fs.cp(fixture, path.join(project, 'src'), { recursive: true });
    // the container holds VS Code, its language server and the tour's Gradle as well: the project's own Gradle, which
    // its Gradle-mode steps start, gets a heap that leaves them room
    await fs.appendFile(path.join(project, 'gradle.properties'), 'org.gradle.jvmargs=-Xmx768m -XX:MaxMetaspaceSize=384m\n');
    return project;
}

/**
 * The recording's VS Code: dark, with its own title bar, so the window has no frame of the window manager's and the
 * screen shows it as the page draws it; no welcome page, tips or AI features; a terminal drawn in the page, which the
 * tour reads; and the simple file dialogs, which are part of the page, for Export Server Logs and Create Project.
 */
const TOUR_SETTINGS = {
    'workbench.colorTheme': 'Default Dark Modern',
    'chat.disableAIFeatures': true,
    'window.titleBarStyle': 'custom',
    'window.dialogStyle': 'custom',
    'window.restoreWindows': 'none',
    'workbench.startupEditor': 'none',
    'workbench.tips.enabled': false,
    'workbench.enableExperiments': false,
    'workbench.secondarySideBar.defaultVisibility': 'hidden',
    'telemetry.telemetryLevel': 'off',
    'update.mode': 'none',
    'extensions.ignoreRecommendations': true,
    'files.simpleDialog.enable': true,
    // VS Code keeps one code action request at a time: an automatic one for the light bulb, as a cursor or marker moves,
    // cancels a Ctrl+. still waiting for the server, which then shows nothing; without the light bulb there is none
    'editor.lightbulb.enabled': 'off',
    // the server's execution trace, which the protocol section reads, kept whole for a run of every section: 50 MB
    // by default, of which the oldest files go first
    'xtc.server.logs': { totalSizeMb: 1000 },
    // VS Code moves or renames a file without the server's edits once its participants take longer than this, a minute
    // by default, which the server's proof of a move can on a loaded machine: an hour, as the tour's bound for a hang
    'files.participants.timeout': 3_600_000,
    'editor.fontSize': 15,
    'terminal.integrated.fontSize': 14,
    'terminal.integrated.gpuAcceleration': 'off',
    'debug.console.fontSize': 14,
};

function git(cwd: string, ...args: string[]): Promise<string> {
    return promisify(execFile)('git', args, { cwd, encoding: 'utf8' }).then(result => result.stdout.trim());
}
