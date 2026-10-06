import * as assert from 'node:assert';
import * as fs from 'node:fs/promises';
import * as os from 'node:os';
import * as path from 'node:path';
import type { Memento } from 'vscode';
import { offlineArchive, SupportLogs } from '../../support-logs';
import { readArchive } from '../archive';

suite('Offline project support logs', () => {
    test('retired callbacks cannot overwrite a later launch and recorded failures survive reopening', async () => {
        const values = new Map<string, unknown>();
        const storage: Memento = {
            keys: () => [...values.keys()],
            get: <T>(key: string, fallback?: T) => values.get(key) as T ?? fallback as T,
            update: async (key, value) => { values.set(key, value); }
        };
        const logs = new SupportLogs(storage);
        const retired = logs.begin('OLD');
        const current = logs.begin('NEW');
        logs.finish(retired, 'STALE FAILURE');
        logs.finish(current, ' CURRENT FAILURE');
        const archive = await readArchive(await new SupportLogs(storage).export());
        assert.strictEqual(archive.get('launcher.log')?.toString(), 'NEW CURRENT FAILURE');
        assert.strictEqual(archive.size, 2);
    });

    test('export is bounded excludes source files symlinks and other projects and tolerates pruning', async () => {
        const directory = await fs.mkdtemp(path.join(os.tmpdir(), 'ecstasy-support-'));
        try {
            const own = path.join(directory, 'server-123-456');
            const other = path.join(directory, 'server-789-456');
            const traces = path.join(directory, 'traces');
            await Promise.all([own, other, traces].map(root => fs.mkdir(root)));
            await fs.writeFile(path.join(own, 'server.log'), 'x'.repeat(700_000) + 'TAIL');
            await fs.writeFile(path.join(other, 'server.log'), 'OTHER PROJECT');
            await fs.writeFile(path.join(own, 'Main.x'), 'SOURCE CONTENT');
            await fs.symlink(path.join(other, 'server.log'), path.join(own, 'server.1.log'));
            await fs.writeFile(path.join(traces, 'lsp-trace-123-456.jsonl'), 'OWN TRACE');
            await fs.writeFile(path.join(traces, 'lsp-trace-789-456.jsonl'), 'OTHER TRACE');
            const session = { id: 'owned', started: '', launcher: 'FAILED LAUNCH', logs: { directory: own, traceDirectory: traces } };
            const entries = await readArchive(await offlineArchive(session));
            assert.strictEqual(entries.size, 4);
            const log = [...entries].find(([name]) => name.endsWith('server.log'))![1].toString();
            assert.strictEqual(log.length, 512 * 1024);
            assert.ok(log.endsWith('TAIL'));
            assert.doesNotMatch([...entries.values()].join(''), /OTHER PROJECT|OTHER TRACE|SOURCE CONTENT/);
            assert.ok(JSON.parse(entries.get('manifest.json')!.toString()).files.some((file: { truncated: boolean }) => file.truncated));
            await fs.rm(own, { recursive: true });
            const pruned = await readArchive(await offlineArchive({ ...session, logs: { directory: own, traceDirectory: own } }));
            assert.strictEqual(pruned.size, 2);
        } finally { await fs.rm(directory, { recursive: true, force: true }); }
    });
});
