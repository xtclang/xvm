import * as vscode from 'vscode';

/**
 * The edit that gives a document the line ending its `.editorconfig` files ask for, which the language server reads
 * (`xtc/endOfLine`) but cannot put in a text edit: VS Code gives an edit's line breaks the document's own. Nothing when
 * they ask for none, for `cr`, which VS Code does not have, or when the document already ends its lines so.
 */
export function endOfLineEdit(current: vscode.EndOfLine, wanted: unknown): vscode.TextEdit | undefined {
    const eol = wanted === 'lf' ? vscode.EndOfLine.LF : wanted === 'crlf' ? vscode.EndOfLine.CRLF : undefined;
    return eol !== undefined && eol !== current ? vscode.TextEdit.setEndOfLine(eol) : undefined;
}
