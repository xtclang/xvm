import * as assert from 'node:assert';
import { spawn } from 'node:child_process';
import * as fs from 'node:fs/promises';
import * as os from 'node:os';
import * as path from 'node:path';
import { catalog } from './playbook/shared';
import { testVSCodeBuild } from './vscodeCache';

interface LifecycleReceipt {
    pid: number;
    applicationPid: number;
    status?: string;
    pending?: boolean;
    unsaved?: boolean;
}

/** Keep a second real window alive while another closes with pending work and reopens from backup. */
export async function runLifecycle(extensionRoot: string, sharedProcess = false): Promise<void> {
    const { version, executable } = await testVSCodeBuild(extensionRoot);
    const reports = path.join(extensionRoot, 'build/reports/project-lifecycle');
    await fs.mkdir(reports, { recursive: true });
    const directory = await fs.mkdtemp(path.join(reports, 'run-'));
    const profiles = await fs.mkdtemp(path.join(os.tmpdir(), 'xtc-code-life-'));
    const extensions = path.join(profiles, 'extensions');
    const driver = path.join(extensions, 'local-test.lifecycle-0.0.0');
    await fs.mkdir(driver, { recursive: true });
    await fs.symlink(extensionRoot, path.join(extensions, 'xtclang.xtc-language'), process.platform === 'win32' ? 'junction' : 'dir');
    await fs.writeFile(path.join(driver, 'package.json'), JSON.stringify({
        name: 'lifecycle', publisher: 'local-test', version: '0.0.0',
        engines: { vscode: `^${version}` }, activationEvents: ['onStartupFinished'], main: './index.js'
    }, null, 2) + '\n');
    await fs.writeFile(path.join(driver, 'index.js'), `
const vscode = require('vscode');
exports.activate = () => {
    require(${JSON.stringify(path.join(__dirname, 'lifecycle/index.js'))}).run().then(
        () => vscode.commands.executeCommand(process.env.XTC_LIFECYCLE_SHARED_PROCESS === 'true' &&
            vscode.workspace.workspaceFolders?.[0].name !== 'primary' ? 'workbench.action.closeWindow' : 'workbench.action.quit'),
        error => { console.error(error); return vscode.commands.executeCommand('workbench.action.quit'); }
    );
};
`);
    const data = catalog.cases.X145.values;
    for (const role of ['primary', 'secondary']) {
        const workspace = path.join(directory, role);
        await fs.mkdir(path.join(workspace, '.vscode'), { recursive: true });
        await fs.writeFile(path.join(workspace, data.file), data.source);
        await fs.writeFile(path.join(workspace, '.vscode/settings.json'), JSON.stringify({
            'files.autoSave': 'off',
            'xtc.compiler.sourceModules': [{ name: data.module, uri: data.file }]
        }, null, 2) + '\n');
    }
    // Hot exit and window restoration are application settings: workspace values are ignored.
    for (const profile of sharedProcess ? ['shared'] : ['primary', 'secondary']) {
        const user = path.join(profiles, profile, 'User');
        await fs.mkdir(user, { recursive: true });
        await fs.writeFile(path.join(user, 'settings.json'), JSON.stringify({
            'files.hotExit': 'onExitAndWindowClose', 'window.restoreWindows': 'none'
        }, null, 2) + '\n');
    }
    console.log(`[project-lifecycle] Native window receipts: ${directory}`);
    // Development windows deliberately have no persistent backup path in VS Code. Install only
    // these two local extensions into an isolated normal profile so hot exit is exercised for real.
    const launch = (role: string) => {
        const child = spawn(executable, [path.join(directory, role === 'primary' ? 'primary' : 'secondary'),
            `--user-data-dir=${path.join(profiles, sharedProcess ? 'shared' : role === 'primary' ? 'primary' : 'secondary')}`,
            `--extensions-dir=${extensions}`, '--skip-welcome', '--skip-release-notes', '--new-window',
            '--disable-workspace-trust', '--disable-updates', '--no-sandbox', '--disable-gpu-sandbox'], {
            env: { ...process.env, XTC_LIFECYCLE_ROLE: role, XTC_LIFECYCLE_REPORT: directory, XTC_LIFECYCLE_SHARED_PROCESS: String(sharedProcess) }, stdio: 'inherit'
        });
        assert.ok(child.pid);
        const completion = new Promise<void>((resolve, reject) => {
            const deadline = setTimeout(() => {
                reject(new Error(`${role} native window did not finish within 180 seconds`));
                child.kill(); // Failed-run cleanup only; never evidence of successful process retirement.
            }, 180_000);
            child.once('error', error => { clearTimeout(deadline); reject(error); });
            child.once('exit', (code, signal) => {
                clearTimeout(deadline);
                if (code === 0) resolve();
                else reject(new Error(`${role} native window exited with code ${code}, signal ${signal}`));
            });
        });
        return { pid: child.pid, completion };
    };
    const read = async (name: string): Promise<LifecycleReceipt> => {
        const deadline = Date.now() + 120_000;
        while (Date.now() < deadline) {
            const failures = (await fs.readdir(directory)).filter(file => file.endsWith('-failure.json'));
            assert.deepStrictEqual(failures, [], `Window failed while waiting for ${name}`);
            try { return JSON.parse(await fs.readFile(path.join(directory, `${name}.json`), 'utf8')); }
            catch (error) { if ((error as NodeJS.ErrnoException).code !== 'ENOENT') throw error; }
            await new Promise(resolve => setTimeout(resolve, 100));
        }
        assert.fail(`Missing ${name} receipt`);
    };
    const waitForExit = async (pid: number) => {
        const deadline = Date.now() + 15_000;
        while (Date.now() < deadline) {
            try { process.kill(pid, 0); }
            catch (error) { if ((error as NodeJS.ErrnoException).code === 'ESRCH') return; throw error; }
            await new Promise(resolve => setTimeout(resolve, 100));
        }
        assert.fail(`Native window left compiler process ${pid} alive`);
    };
    const primaryProcess = launch('primary');
    const primary = primaryProcess.completion.then(() => undefined, error => error as Error);
    try {
        // Ensure the first process owns the profile before another CLI hands it a new window.
        if (sharedProcess) await read('primary-ready');
        await launch('closing').completion;
        const closed = await read('closing');
        assert.ok(!(await fs.readdir(directory)).some(file => file.endsWith('-failure.json')));
        assert.ok(closed.pending && closed.unsaved);
        await waitForExit(closed.pid);
        await launch('reopened').completion;
        const reopened = await read('reopened');
        assert.strictEqual(reopened.status, 'passed');
        await waitForExit(reopened.pid);
        assert.ifError(await primary);
        const first = await read('primary');
        assert.strictEqual(first.status, 'passed');
        await waitForExit(first.pid);
        if (sharedProcess) {
            assert.strictEqual(first.applicationPid, primaryProcess.pid);
            assert.strictEqual(closed.applicationPid, first.applicationPid, 'Closed window shared the live main process');
            assert.strictEqual(reopened.applicationPid, first.applicationPid, 'Reopening reused the same main process');
        }
        await fs.writeFile(path.join(directory, 'results.json'), JSON.stringify({
            status: 'passed', vscodeWindows: 2, separateProfiles: !sharedProcess, sharedProcess, installedExtensions: true,
            primary: first, closing: closed, reopened
        }, null, 2) + '\n');
    } catch (error) {
        const failure = JSON.stringify({ status: 'failed', error: String(error) }, null, 2) + '\n';
        await fs.writeFile(path.join(directory, 'launcher-failure.json'), failure);
        await fs.writeFile(path.join(directory, 'results.json'), failure);
        throw error;
    } finally {
        await primary;
        // Retain the real host logs before removing only these disposable test profiles.
        for (const role of sharedProcess ? ['shared'] : ['primary', 'secondary']) {
            await fs.cp(path.join(profiles, role, 'logs'), path.join(directory, `${role}-logs`), { recursive: true })
                .catch(error => { if ((error as NodeJS.ErrnoException).code !== 'ENOENT') throw error; });
        }
        // Retain native backup evidence on failure without changing the next run's profile.
        if (await fs.stat(path.join(directory, 'launcher-failure.json')).then(() => true, () => false)) {
            for (const profile of sharedProcess ? ['shared'] : ['primary', 'secondary']) {
                await fs.cp(path.join(profiles, profile, 'Backups'), path.join(directory, `${profile}-backups`), { recursive: true })
                    .catch(error => { if ((error as NodeJS.ErrnoException).code !== 'ENOENT') throw error; });
            }
        }
        await fs.rm(profiles, { recursive: true, force: true });
    }
}
