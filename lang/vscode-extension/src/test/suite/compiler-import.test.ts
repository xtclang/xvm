import * as assert from 'node:assert';
import { CompilerImport } from '../../compiler-import';

suite('Compiler import ownership', () => {
    function fixture() {
        let disk: string | undefined = 'original';
        const owner = new CompilerImport(() => disk, text => { if (text === 'invalid') throw new Error('Invalid model'); });
        return { owner, write: (text?: string) => { disk = text; } };
    }

    test('watchers retain accepted inputs until successful completion and reject overlapping imports', () => {
        const { owner, write } = fixture();
        assert.strictEqual(owner.current(), 'original');
        const operation = owner.begin(false);
        write('new');
        assert.strictEqual(owner.current(), 'original');
        assert.match(owner.description(), /Refreshing/);
        assert.throws(() => owner.begin(true), /already running/);
        assert.strictEqual(owner.finish(operation, 'succeeded').outcome, 'succeeded');
        assert.strictEqual(owner.current(), 'new');
        assert.match(owner.description(), /Last import:/);
    });

    test('cancelled and failed valid reports stay unaccepted until explicit retry', () => {
        for (const outcome of ['cancelled', 'failed'] as const) {
            const { owner, write } = fixture();
            owner.current();
            const operation = owner.begin(true);
            write('unaccepted');
            assert.strictEqual(owner.finish(operation, outcome).outcome, outcome);
            assert.strictEqual(owner.current(), 'original');
            assert.strictEqual(owner.current(), 'original');
            owner.finish(owner.begin(false), 'succeeded');
            assert.strictEqual(owner.current(), 'unaccepted');
        }
    });

    test('invalid or missing outputs cannot replace accepted inputs and later repairs remain observable', () => {
        const { owner, write } = fixture();
        owner.current();
        for (const output of ['invalid', undefined]) {
            const operation = owner.begin(false);
            write(output);
            assert.strictEqual(owner.finish(operation, 'succeeded').outcome, 'failed');
            assert.strictEqual(owner.current(), 'original');
        }
        write('externally repaired');
        assert.strictEqual(owner.current(), 'externally repaired');
        write(undefined);
        assert.strictEqual(owner.current(), undefined);
    });

    test('late completion cannot retire or publish over a newer import', () => {
        const { owner, write } = fixture();
        owner.current();
        const first = owner.begin(false);
        owner.finish(first, 'cancelled');
        const second = owner.begin(true);
        write('new');
        assert.throws(() => owner.finish(first, 'succeeded'), /no longer owns/);
        assert.strictEqual(owner.current(), 'original');
        owner.finish(second, 'succeeded');
        assert.strictEqual(owner.current(), 'new');
    });
});
