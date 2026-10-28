import * as assert from 'node:assert';
import { ConsoleLineEditor } from '../../console-input';

suite('Embedded run console input', () => {
    test('typed text is echoed and Enter sends the line', () => {
        const editor = new ConsoleLineEditor();
        assert.deepStrictEqual(editor.feed('Ada'), { echo: 'Ada', lines: [], end: false, interrupt: false });
        assert.deepStrictEqual(editor.feed('\r'), { echo: '\r\n', lines: ['Ada\n'], end: false, interrupt: false });
    });
    test('Backspace edits the line, and on an empty line does nothing', () => {
        const editor = new ConsoleLineEditor();
        const step = editor.feed('Adx\x7fa\r\x7f');
        assert.strictEqual(step.echo, 'Adx\b \ba\r\n');
        assert.deepStrictEqual(step.lines, ['Ada\n']);
    });
    test('pasted lines arrive whole, with one line end for CRLF', () => {
        const editor = new ConsoleLineEditor();
        assert.deepStrictEqual(editor.feed('one\r\ntwo\nthree').lines, ['one\n', 'two\n']);
        assert.deepStrictEqual(editor.feed('\r').lines, ['three\n']);
    });
    test('non-ASCII text, including characters outside the BMP, is one character each', () => {
        const editor = new ConsoleLineEditor();
        const step = editor.feed('ünï ✓ 𝄞\x7f\r');
        assert.deepStrictEqual(step.lines, ['ünï ✓ \n']);
    });
    test('cursor keys and other escape sequences are ignored', () => {
        const editor = new ConsoleLineEditor();
        const step = editor.feed('a\x1b[D\x1b[1;5Cb\x1bOAc\r');
        assert.strictEqual(step.echo, 'abc\r\n');
        assert.deepStrictEqual(step.lines, ['abc\n']);
    });
    test('Ctrl+D ends input only on an empty line; Ctrl+C asks to stop', () => {
        const editor = new ConsoleLineEditor();
        assert.strictEqual(editor.feed('partial\x04').end, false);
        assert.strictEqual(editor.feed('\r\x04').end, true);
        assert.strictEqual(editor.feed('\x03').interrupt, true);
    });
});
