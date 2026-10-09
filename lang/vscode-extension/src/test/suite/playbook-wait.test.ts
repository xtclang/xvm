import * as assert from 'node:assert';
import Mocha from 'mocha';
import { PlaybookCleanupFailure, PlaybookTimeout, stopOnUnsafeFailure, waitFor } from '../wait';

suite('Playbook timeout isolation', () => {
    test('condition timeout fails with its last observation', async () => {
        await assert.rejects(waitFor(async () => 'still visible', value => value === 'gone', 'Hide hints', 10),
            error => error instanceof PlaybookTimeout && error.message.includes('still visible'));
    });

    test('a read that never replies is bounded too', async () => {
        await assert.rejects(waitFor(() => new Promise<never>(() => {}), Boolean, 'Provider reply', 10), PlaybookTimeout);
    });

    test('an explicit refusal stays distinct from success and timeout', async () => {
        const refusal = new Error('Unsupported operation');
        await assert.rejects(waitFor(async () => { throw refusal; }, Boolean, 'Refusal', 100), error => error === refusal);
        assert.strictEqual(await waitFor(async () => 'ready', value => value === 'ready', 'Success', 100), 'ready');
    });

    test('Mocha cannot start another case after a timeout or cleanup failure', async () => {
        for (const operation of [
            () => new Promise<never>(() => {}),
            async () => { throw new PlaybookTimeout('Unfinished editor command'); },
            async () => { throw Object.assign(new Error('Renderer never replied'), { name: 'TimeoutError' }); },
            async () => { throw new PlaybookCleanupFailure([new Error('Could not restore settings')]); }
        ]) {
            const mocha = new Mocha({ reporter: class extends Mocha.reporters.Base {}, timeout: 10 });
            let followingRan = false;
            mocha.suite.addTest(new Mocha.Test('unsafe case', operation));
            mocha.suite.addTest(new Mocha.Test('following case', () => { followingRan = true; }));
            const failures = await new Promise<number>(resolve => stopOnUnsafeFailure(mocha.run(resolve)));
            assert.strictEqual(failures, 1);
            assert.strictEqual(followingRan, false);
        }
    });
});
