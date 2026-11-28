import * as assert from 'node:assert';
import Mocha from 'mocha';
import { appliesOnRestart, restartsByItself } from '../settings';
import { untilTakes } from '../uiWait';
import { caseSignal, leftRunning, mustStopPlaybook, PlaybookCleanupFailure, PlaybookFinal, PlaybookLeftRunning, PlaybookTimeout, runCase,
    stopOnUnsafeFailure, waitFor, waitOn, waitWhile, withPlaybookCleanup } from '../wait';

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

    test("the extension suite's Mocha starts no other test after a timeout or cleanup failure", async () => {
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

    test('a playbook case answers its failure, and goes on in a wait however long the wait goes on', async () => {
        const failure = new Error('Assertion failed');
        const none = async () => undefined;
        assert.strictEqual(await runCase(async () => undefined, () => undefined, () => [], () => [], none), undefined);
        assert.strictEqual(await runCase(async () => { throw failure; }, () => undefined, () => [], () => [], none), failure);
        // the case's own look finds nothing under way, but its wait does, ten looks long: the wait decides
        let looks = 0;
        assert.strictEqual(await runCase(async () => {
            await waitWhile(async () => ++looks, value => value === 10, 'Files agree', value => value < 10 ? ['an edit'] : [], () => undefined);
        }, () => undefined, () => [], () => [], none), undefined);
    });

    test('a case in a call goes on while something is under way', async () => {
        let release: () => void = () => undefined;
        let looks = 0;
        // a call that ends once the case's look has found something under way five times
        assert.strictEqual(await runCase(() => new Promise<void>(resolve => { release = resolve; }), () => undefined, () => [], () => {
            if (++looks === 5) release();
            return ["the server's compiles"];
        }, async () => undefined), undefined);
        assert.ok(looks >= 5, `${looks} looks`);
    });

    test('a case in a call with nothing under way is given up on at three looks, and ends through its own cleanup', async () => {
        let release: () => void = () => undefined;
        let unstuck = 0;
        let restored: string | undefined;
        let aborted = false;
        const given = await runCase(async () => {
            caseSignal()?.addEventListener('abort', () => { aborted = true; });
            try { await new Promise<void>(resolve => { release = resolve; }); }
            finally { restored = await waitFor(async () => 'restored', value => value === 'restored', 'Restore the settings'); }
        }, () => undefined, () => [], () => [], async () => {
            unstuck++;
            release();
        });
        assert.ok(given instanceof PlaybookTimeout && !(given instanceof PlaybookLeftRunning) && given.message.includes('nothing under way'), String(given));
        assert.strictEqual(unstuck, 1, 'The call is ended once, as a restart of the server does');
        assert.ok(aborted, "The case's signal aborts");
        assert.strictEqual(restored, 'restored', 'The case has ended through its cleanup as its failure is answered');
    });

    test('a case whose call is ended by a restart that never ends is left to itself', async () => {
        let release: () => void = () => undefined;
        // the restart ends the case's call, and never ends itself, as one whose server never answers
        const left = await runCase(() => new Promise<void>(resolve => { release = resolve; }), () => undefined, () => [], () => [], () => {
            release();
            return new Promise<never>(() => {});
        });
        assert.ok(left instanceof PlaybookLeftRunning && left.message.includes('the restart of the language server'), String(left));
    });

    test('a case given up on that does not end is left to itself, after which the run stops', async () => {
        const left = await runCase(() => new Promise<never>(() => {}), () => undefined, () => [], () => [], async () => undefined);
        assert.ok(left instanceof PlaybookLeftRunning && left.message.includes('left to itself'), String(left));
        assert.ok(mustStopPlaybook(left));
        assert.strictEqual(leftRunning(new PlaybookCleanupFailure([new Error('Assertion failed'), left])), left);
        assert.strictEqual(leftRunning(new PlaybookCleanupFailure([new Error('Assertion failed')])), undefined);
    });

    test('a read that has not answered is under way until it answers, and one that fails fails the wait at once', async () => {
        let answer: (value: string) => void = () => undefined;
        let looks = 0;
        // nothing else is under way: the read alone keeps the wait going, past three looks
        assert.strictEqual(await waitOn(() => new Promise<string>(resolve => { answer = resolve; }), value => value === 'answered', 'The server answers',
            () => {
                if (++looks === 5) answer('answered');
                return [];
            }, () => undefined, 1), 'answered');
        assert.ok(looks >= 5, `${looks} looks`);
        // a read slower than a look, whose answers, each late, never do: they count toward the verdict, as quick ones do
        let reads = 0;
        let late: ((value: string) => void) | undefined;
        await assert.rejects(waitOn(() => {
            reads++;
            return new Promise<string>(resolve => { late = resolve; });
        }, value => value === 'listed', 'The tests are listed', () => {
            // only a look that found the read unanswered lets it answer
            late?.('not yet');
            late = undefined;
            return [];
        }, () => undefined, 1), error => error instanceof PlaybookFinal && error.message.includes('not yet'));
        assert.ok(reads >= 3, `${reads} reads`);
        const refusal = new Error('Unsupported operation');
        await assert.rejects(waitOn(async () => { throw refusal; }, Boolean, 'Refusal', () => ['an edit'], () => undefined, 1), error => error === refusal);
        await assert.rejects(waitOn(async () => 'still visible', value => value === 'gone', 'Hide hints', () => [], () => undefined, 1),
            error => error instanceof PlaybookFinal && error.message.includes('still visible'));
    });

    test('a setting that applies only on restart is told from one that the extension restarts the server for', () => {
        assert.ok(appliesOnRestart('xtc.java.vmOptions') && appliesOnRestart('xtc.server.maxHeap'));
        assert.ok(!appliesOnRestart('xtc.java.home') && restartsByItself('xtc.java.home'));
        assert.ok(restartsByItself('xtc.languageService.adapter') && !appliesOnRestart('xtc.languageService.adapter'));
        assert.ok(!appliesOnRestart('editor.wordWrapColumn') && !restartsByItself('editor.wordWrapColumn'));
    });

    test('a wait while something is under way fails at once once nothing is, and goes on while something is', async () => {
        let looks = 0;
        await assert.rejects(waitWhile(async () => ++looks, () => false, 'Files agree', () => [], () => undefined),
            error => error instanceof PlaybookFinal && error.message.includes('final') && looks === 3);
        looks = 0;
        assert.strictEqual(await waitWhile(async () => ++looks, value => value === 10, 'Files agree', value => value < 10 ? ['an edit'] : [], () => undefined), 10);
    });

    test('a UI action is tried again until it takes, and fails at once at three tries with nothing under way', async () => {
        let tries = 0;
        // without the minute's record of what happened, which the harness's other tests fill
        await untilTakes('The box shows', async () => { if (++tries < 2) throw new Error('not yet'); }, { recent: false });
        assert.strictEqual(tries, 2);
        tries = 0;
        await assert.rejects(untilTakes('The box shows', async () => { tries++; throw new Error('locator.waitFor: Timeout 2000ms exceeded'); },
            { recent: false }),
            error => error instanceof PlaybookFinal && error.message.includes('Timeout 2000ms') && tries >= 3);
    });

    test('cleanup runs after success or failure and preserves both failures when restoration fails', async () => {
        const assertion = new Error('Assertion failed');
        const restoration = new Error('Could not restore settings');
        for (const bodyFails of [false, true]) {
            for (const cleanupFails of [false, true]) {
                const calls: string[] = [];
                const operation = withPlaybookCleanup(async () => {
                    calls.push('body');
                    if (bodyFails) throw assertion;
                }, async () => {
                    calls.push('cleanup');
                    if (cleanupFails) throw restoration;
                });
                if (cleanupFails) {
                    await assert.rejects(operation, error => {
                        assert.ok(error instanceof PlaybookCleanupFailure);
                        assert.deepStrictEqual(error.failures, bodyFails ? [assertion, restoration] : [restoration]);
                        assert.ok(mustStopPlaybook(error));
                        return true;
                    });
                } else if (bodyFails) {
                    await assert.rejects(operation, error => error === assertion);
                } else {
                    await operation;
                }
                assert.deepStrictEqual(calls, ['body', 'cleanup']);
            }
        }
    });
});
