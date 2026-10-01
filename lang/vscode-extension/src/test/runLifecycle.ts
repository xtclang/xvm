import * as assert from 'node:assert';
import * as fs from 'node:fs/promises';
import * as os from 'node:os';
import * as path from 'node:path';
import { runTests } from '@vscode/test-electron';
import { catalog } from './playbook/shared';

/** Keep a second real window alive while another closes with pending work and reopens from backup. */
export async function runLifecycle(extensionRoot: string): Promise<void> {
    const reports = path.join(extensionRoot, 'build/reports/project-lifecycle');
    await fs.mkdir(reports, { recursive: true });
    const directory = await fs.mkdtemp(path.join(reports, 'run-'));
    const profiles = await fs.mkdtemp(path.join(os.tmpdir(), 'xtc-code-life-'));
    const data = catalog.cases.X145.values;
    for (const role of ['primary', 'secondary']) {
        const workspace = path.join(directory, role);
        await fs.mkdir(path.join(workspace, '.vscode'), { recursive: true });
        await fs.writeFile(path.join(workspace, data.file), data.source);
        await fs.writeFile(path.join(workspace, '.vscode/settings.json'), JSON.stringify({
            'files.autoSave': 'off', 'files.hotExit': 'onExitAndWindowClose',
            'xtc.compiler.sourceModules': [{ name: data.module, uri: data.file }]
        }, null, 2) + '\n');
    }
    console.log(`[project-lifecycle] Native window receipts: ${directory}`);
    const launch = (role: string) => runTests({
        extensionDevelopmentPath: extensionRoot,
        extensionTestsPath: path.join(__dirname, 'lifecycle/index'),
        extensionTestsEnv: { XTC_LIFECYCLE_ROLE: role, XTC_LIFECYCLE_REPORT: directory },
        launchArgs: [path.join(directory, role === 'primary' ? 'primary' : 'secondary'), '--disable-extensions',
            `--user-data-dir=${path.join(profiles, role === 'primary' ? 'primary' : 'secondary')}`,
            '--skip-welcome', '--skip-release-notes', '--new-window']
    });
    const read = async (name: string) => JSON.parse(await fs.readFile(path.join(directory, `${name}.json`), 'utf8'));
    const waitForExit = async (pid: number) => {
        const deadline = Date.now() + 15_000;
        while (Date.now() < deadline) {
            try { process.kill(pid, 0); }
            catch (error) { if ((error as NodeJS.ErrnoException).code === 'ESRCH') return; throw error; }
            await new Promise(resolve => setTimeout(resolve, 100));
        }
        assert.fail(`Native window left compiler process ${pid} alive`);
    };
    const primary = launch('primary').then(() => undefined, error => error as Error);
    try {
        await launch('closing');
        const closed = await read('closing');
        assert.ok(closed.pending && closed.unsaved);
        await waitForExit(closed.pid);
        await launch('reopened');
        const reopened = await read('reopened');
        assert.strictEqual(reopened.status, 'passed');
        await waitForExit(reopened.pid);
        assert.ifError(await primary);
        const first = await read('primary');
        assert.strictEqual(first.status, 'passed');
        await waitForExit(first.pid);
        await fs.writeFile(path.join(directory, 'results.json'), JSON.stringify({
            status: 'passed', vscodeWindows: 2, separateProfiles: true,
            primary: first, closing: closed, reopened
        }, null, 2) + '\n');
    } finally {
        await primary;
        // Retain the real host logs before removing only these disposable test profiles.
        for (const role of ['primary', 'secondary']) {
            await fs.cp(path.join(profiles, role, 'logs'), path.join(directory, `${role}-logs`), { recursive: true })
                .catch(error => { if ((error as NodeJS.ErrnoException).code !== 'ENOENT') throw error; });
        }
        await fs.rm(profiles, { recursive: true, force: true });
    }
}
