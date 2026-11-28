import * as assert from 'node:assert';
import { KEPT_TEXT, keptText } from '../../embedded-execution';

suite('Embedded build and run kept text', () => {
    test('text is kept whole up to the limit', () => {
        assert.strictEqual(keptText('one\n', 'two\n'), 'one\ntwo\n');
        const full = 'x'.repeat(KEPT_TEXT);
        assert.strictEqual(keptText(full.slice(1), 'x'), full);
    });
    test('past the limit only the last characters are kept', () => {
        const kept = keptText('a'.repeat(KEPT_TEXT), 'tail');
        assert.strictEqual(kept.length, KEPT_TEXT);
        assert.ok(kept.endsWith('atail'));
    });
    test('a surrogate pair at the cut is dropped whole', () => {
        const kept = keptText('😀'.repeat(KEPT_TEXT / 2), 'z');
        assert.strictEqual(kept.length, KEPT_TEXT - 1);
        assert.ok(kept.startsWith('😀') && kept.endsWith('😀z'));
    });
});
