import * as assert from 'node:assert';
import { failureLine, isTestEvent, LineSplitter, MARKER, parseTestEvent } from '../../test-events';

suite('Test events', () => {
    test('a line of xunit\'s json report is its event', () => {
        const event = parseTestEvent(`${MARKER}{"event":"finished","id":"Mathy:void fails()","status":"failed",`
            + '"message":"a \\"quoted\\" line\\nand the next","details":"IllegalState: no\\n\\tat fails() (Mathy.x:10)",'
            + '"durationMillis":12}\r\n');
        assert.deepStrictEqual(event, {
            event: 'finished',
            id: 'Mathy:void fails()',
            status: 'failed',
            message: 'a "quoted" line\nand the next',
            details: 'IllegalState: no\n\tat fails() (Mathy.x:10)',
            durationMillis: 12,
        });
        assert.deepStrictEqual(parseTestEvent(`${MARKER}{"event":"run","version":1}`), { event: 'run', version: 1 });
    });

    test('other output is no event, nor is a marked line that is not an event\'s object', () => {
        assert.strictEqual(parseTestEvent('Started:   Mathy.adds()'), undefined);
        assert.strictEqual(parseTestEvent('{"event":"run","version":1}'), undefined);
        assert.strictEqual(parseTestEvent(`${MARKER}not json`), undefined);
        assert.strictEqual(parseTestEvent(`${MARKER}["event"]`), undefined);
        assert.strictEqual(parseTestEvent(`${MARKER}{"id":"M:"}`), undefined);
        assert.strictEqual(parseTestEvent(`${MARKER}{"event":3}`), undefined);
        assert.ok(isTestEvent({ event: 'started', since: 'later' }));
        assert.ok(!isTestEvent(null));
    });

    test('a failure is placed at the line its stack names in the test\'s own file', () => {
        const details = 'IllegalState: boom\n\tat helper() (Other.x:3)\n\tat fails() (Mathy.x:10)\n\tat executor.invoke() (executor.x:30)';
        assert.strictEqual(failureLine(details, 'Mathy.x'), 10);
        assert.strictEqual(failureLine(details, 'Other.x'), 3);
        assert.strictEqual(failureLine(details, 'Absent.x'), undefined);
    });

    test('output that arrives in pieces is split into whole lines', () => {
        const lines = new LineSplitter();
        assert.deepStrictEqual(lines.feed(`${MARKER}{"event":"sta`), []);
        assert.deepStrictEqual(lines.feed('rted"}\r\ninside a\nlast'), [`${MARKER}{"event":"started"}`, 'inside a']);
        assert.deepStrictEqual(lines.flush(), ['last']);
        assert.deepStrictEqual(lines.flush(), []);
    });
});
