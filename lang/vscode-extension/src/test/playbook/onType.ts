import * as assert from 'node:assert';
import * as vscode from 'vscode';
import { eventually, playbook } from './support';

/** The leading whitespace of a line: what typing and on-type formatting decide. */
function indentOf(document: vscode.TextDocument, line: number): number {
    const text = document.lineAt(line).text;
    return text.length - text.trimStart().length;
}

/**
 * The indentation of the line at [at] once the server's on-type edits for [ch] at [at] are applied to
 * the current text. Equal to the line's indentation now, the text is where the server would put it,
 * so no late on-type edit can still change it.
 */
async function indentAfterServerEdits(document: vscode.TextDocument, at: vscode.Position, ch: string): Promise<number> {
    const edits = await vscode.commands.executeCommand<vscode.TextEdit[]>('vscode.executeFormatOnTypeProvider',
        document.uri, at, ch, { tabSize: 4, insertSpaces: true }) ?? [];
    let text = document.getText();
    for (const edit of [...edits].sort((a, b) => document.offsetAt(b.range.start) - document.offsetAt(a.range.start))) {
        text = text.slice(0, document.offsetAt(edit.range.start)) + edit.newText + text.slice(document.offsetAt(edit.range.end));
    }
    const line = text.split('\n')[at.line] ?? '';
    return line.length - line.trimStart().length;
}

/** Indentation while typing; registered after the build mode cases to keep catalog order. */
export function onTypeCases(): void {
    playbook('X290', async (workspace, data) => {
        const document = await workspace.open(data.file, data.scenarios[0].source.replace('§', ''));
        const editor = await vscode.window.showTextDocument(document, { preview: false });
        // every scenario runs, so one run reports each one that fails
        const failures: string[] = [];
        for (const scenario of data.scenarios) {
            await workspace.replace(document, scenario.source.replace('§', ''));
            const at = document.positionAt(scenario.source.indexOf('§'));
            editor.selection = new vscode.Selection(at, at);
            // the user's typing command: auto-indent, then the server's on-type formatting
            await vscode.commands.executeCommand('default:type', { text: scenario.keys });
            try {
                await eventually(async () => indentOf(document, editor.selection.active.line), indent => indent === scenario.indent,
                    `${scenario.name}: the cursor line is indented by ${scenario.indent}`);
                const after = await indentAfterServerEdits(document, editor.selection.active, scenario.keys.slice(-1));
                assert.strictEqual(after, scenario.indent, `${scenario.name}: the server's on-type edits would re-indent the line`);
            } catch (failure) {
                failures.push(`${failure instanceof Error ? failure.message : String(failure)}\n${document.getText()}`);
            }
        }
        assert.deepStrictEqual(failures, []);
    });
}
