import * as assert from 'node:assert';
import { execFile } from 'node:child_process';
import * as fs from 'node:fs/promises';
import * as os from 'node:os';
import * as path from 'node:path';
import { promisify } from 'node:util';
import { ServerControlEndpoint } from '../../server-control';

/** Exercise the actual CLI against real endpoints, including ambiguous windows and restart failures. */
suite('Editor-owned server control', () => {
    const execute = promisify(execFile);
    const script = path.resolve(__dirname, '../../../../scripts/xtc-lsp');
    let directory: string;
    const controls: ServerControlEndpoint[] = [];
    setup(async () => { directory = await fs.mkdtemp(path.join(os.tmpdir(), 'xtc-control-')); });
    teardown(async () => {
        await Promise.all(controls.splice(0).map(control => control.close()));
        await fs.rm(directory, { recursive: true, force: true });
    });
    const cli = (...args: string[]) => execute('python3', [script, ...args], {
        env: { ...process.env, XTC_EDITOR_CONTROL_DIR: directory }, timeout: 10000
    });

    test('exact project routing refuses ambiguity and ignores stale records', async () => {
        let first = 0, second = 0;
        const project = path.join(directory, 'project');
        controls.push(await ServerControlEndpoint.start(directory, () => [project], async () => { first++; }));
        await fs.writeFile(path.join(directory, 'stale.json'), '{broken');
        await cli('restart', '--project', project);
        assert.strictEqual(first, 1);
        controls.push(await ServerControlEndpoint.start(directory, () => [project], async () => { second++; }));
        await assert.rejects(cli('restart', '--project', project), /Multiple editors match/);
        await assert.rejects(cli('restart', '--project', path.join(directory, 'other')), /No live Ecstasy editor session/);
        assert.strictEqual(first, 1);
        assert.strictEqual(second, 0);
        const listing = (await cli('list')).stdout;
        const id = listing.trim().split(/\s+/)[0];
        await cli('restart', '--project', project, '--session', id);
        assert.strictEqual(first + second, 2);
        for (const file of (await fs.readdir(directory)).filter(file => file !== 'stale.json')) {
            const descriptor = JSON.parse(await fs.readFile(path.join(directory, file), 'utf8'));
            assert.ok(!listing.includes(descriptor.token));
            if (process.platform !== 'win32') assert.strictEqual((await fs.stat(path.join(directory, file))).mode & 0o077, 0);
        }
    });

    test('authentication, operation and busy checks precede restart, and disposal removes discovery', async () => {
        let calls = 0;
        let complete!: () => void;
        const pending = new Promise<void>(resolve => { complete = resolve; });
        const control = await ServerControlEndpoint.start(directory, () => ['/project'], async () => { calls++; await pending; });
        controls.push(control);
        const file = path.join(directory, (await fs.readdir(directory))[0]);
        const descriptor = JSON.parse(await fs.readFile(file, 'utf8'));
        const url = `http://127.0.0.1:${descriptor.port}`;
        const headers = { Authorization: `Bearer ${descriptor.token}` };
        assert.strictEqual((await fetch(`${url}/restart`, { method: 'POST' })).status, 403);
        assert.strictEqual((await fetch(`${url}/restart`, { headers })).status, 404);
        assert.strictEqual(calls, 0);
        const restart = fetch(`${url}/restart`, { method: 'POST', headers });
        const deadline = Date.now() + 5000;
        while (!calls && Date.now() < deadline) await new Promise(resolve => setTimeout(resolve, 5));
        assert.strictEqual(calls, 1);
        assert.strictEqual((await fetch(`${url}/restart`, { method: 'POST', headers })).status, 409);
        complete();
        assert.strictEqual((await restart).status, 200);
        assert.strictEqual(calls, 1);
        await control.close();
        assert.deepStrictEqual(await fs.readdir(directory), []);
    });

    test('restart failures reach the CLI and the endpoint remains usable', async () => {
        let fail = true;
        controls.push(await ServerControlEndpoint.start(directory, () => ['/project'], async () => {
            if (fail) throw new Error('Launch failed');
        }));
        await assert.rejects(cli('restart', '--project', '/project'), /Launch failed/);
        fail = false;
        await cli('restart', '--project', '/project');
    });
});
