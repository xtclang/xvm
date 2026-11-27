import * as assert from 'node:assert';
import * as vscode from 'vscode';
import { endOfLineEdit } from '../../end-of-line';

suite('Line endings from .editorconfig', () => {
    test("Format Document sets the line ending the server reads when the document's differs", () => {
        assert.strictEqual(endOfLineEdit(vscode.EndOfLine.LF, 'crlf')?.newEol, vscode.EndOfLine.CRLF);
        assert.strictEqual(endOfLineEdit(vscode.EndOfLine.CRLF, 'lf')?.newEol, vscode.EndOfLine.LF);
    });
    test('nothing for the line ending the document has, for cr, or when the files ask for none', () => {
        assert.strictEqual(endOfLineEdit(vscode.EndOfLine.LF, 'lf'), undefined);
        assert.strictEqual(endOfLineEdit(vscode.EndOfLine.LF, 'cr'), undefined);
        assert.strictEqual(endOfLineEdit(vscode.EndOfLine.CRLF, null), undefined);
    });
});
