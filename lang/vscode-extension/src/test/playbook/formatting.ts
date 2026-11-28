import * as assert from 'node:assert';
// the .editorconfig files, which no watcher of the server's reports and the server reads at each format: written past
// the playbook's record of the changes the server has still to hear of, which would never be heard of
import * as files from 'node:fs/promises';
import * as vscode from 'vscode';
import { client, eventually, noErrors, playbook, position, serviceStatus, type Workspace } from './support';

/** Formats [document] as Format Document does, and checks that it is then [expected], as [what] says. */
async function formatted(document: vscode.TextDocument, expected: string, what: string): Promise<void> {
    await vscode.window.showTextDocument(document, { preview: false });
    await vscode.commands.executeCommand('editor.action.formatDocument');
    assert.strictEqual(document.getText(), expected, what);
}

/**
 * Sets xtc.formatting's [key] to [value] in the workspace and waits until the language server formats with [effective],
 * as its status says: the change, which the extension tells the server of, and the server's request for VS Code's
 * formatting settings after it, are under way. The case's cleanup puts the setting back.
 */
async function formattingSetting(key: string, value: unknown, effective: unknown): Promise<void> {
    await vscode.workspace.getConfiguration('xtc.formatting').update(key, value, vscode.ConfigurationTarget.Workspace);
    await eventually(() => serviceStatus<{ formatting?: Record<string, unknown> }>(), status => status?.formatting?.[key] === effective,
        `The language server formats with xtc.formatting.${key} ${JSON.stringify(effective)}`);
}

/** Runs [body] with the case's .editorconfig at [file], which it writes as [body] says, and removes it again. */
async function withEditorConfig(workspace: Workspace, file: string, body: (write: (text: string) => Promise<void>) => Promise<void>): Promise<void> {
    const path = workspace.uri(file).fsPath;
    try {
        await body(text => files.writeFile(path, text));
    } finally {
        await files.rm(path, { force: true });
    }
}

/**
 * X361–X365, registered after X360 in catalog order: what a file's .editorconfig sets over the editor's formatting
 * settings, the formatter markers, comment reflow, line endings and trailing whitespace.
 */
export function formattingCases(): void {
    playbook('X361', async (workspace, data) => {
        const document = await workspace.open(data.file, data.source);
        await noErrors(document.uri);
        // the editor's own settings, which the file's override
        const editor = await serviceStatus<{ formatting?: { indentSize: number; maxLineWidth: number } }>();
        assert.deepStrictEqual([editor?.formatting?.indentSize, editor?.formatting?.maxLineWidth], [data.indentSize, data.maxLineWidth]);
        await withEditorConfig(workspace, data.editorConfig, async write => {
            await write(data.settings);
            await formatted(document, data.fromFile, "The file's indent_size and max_line_length");
            await workspace.replace(document, data.source);
            // a later section's unset takes indent_size and max_line_length out: indent_style = tab indents by its
            // tab_width, one tab a level, where an indent_size the server ignored would leave the editor's 4 columns, and
            // the margin is the editor's
            await write(data.unset);
            await formatted(document, data.fromUnset, 'unset takes indent_size and max_line_length out');
        });
    });

    playbook('X362', async (workspace, data) => {
        const document = await workspace.open(data.file, data.source);
        await noErrors(document.uri);
        await formatted(document, data.unmarked, 'Without the markers every line is formatted');
        await workspace.replace(document, data.source);
        await formattingSetting('formatterTagsEnabled', true, true);
        await formatted(document, data.expected, 'The marked lines keep their layout, and the others are formatted');

        // as you type: the user's typing command, then the server's on-type formatting, which VS Code asks for ;
        await workspace.replace(document, data.typing);
        const editor = await vscode.window.showTextDocument(document, { preview: false });
        for (const anchor of [data.kept, data.formatted]) {
            const at = position(document, anchor, anchor.length);
            editor.selection = new vscode.Selection(at, at);
            await vscode.commands.executeCommand('default:type', { text: data.keys });
        }
        await eventually(async () => document.getText(), text => text === data.expected,
            'Typed, ; keeps the marked line where it is and indents the line after the markers');

        // with the markers off in VS Code's settings and on in .editorconfig
        await formattingSetting('formatterTagsEnabled', false, false);
        await workspace.replace(document, data.source);
        await withEditorConfig(workspace, data.editorConfig, async write => {
            await write(data.settings);
            await formatted(document, data.expected, 'With the markers on in .editorconfig, the marked lines keep their layout');
        });
    });

    playbook('X363', async (workspace, data) => {
        const document = await workspace.open(data.file, data.source);
        await noErrors(document.uri);
        await formatted(document, data.source, 'Without wrapping of comments the comment stays as it is');
        await formattingSetting('wrapComments', true, true);
        await formatted(document, data.expected, "The paragraph is refilled at the margin, and the @param lines and the code keep their layout");
        await formatted(document, data.expected, 'A second format changes nothing');
    });

    playbook('X364', async (workspace, data) => {
        const document = await workspace.open(data.file, data.source);
        await noErrors(document.uri);
        assert.strictEqual(document.eol, vscode.EndOfLine.LF, 'The document has LF line endings');
        await withEditorConfig(workspace, data.editorConfig, async write => {
            await write(data.settings);
            // the line ending the file asks for, which the server reads but cannot put in a text edit
            const uri = client().code2ProtocolConverter.asUri(document.uri);
            assert.strictEqual(await client().sendRequest('xtc/endOfLine', { uri }), data.endOfLine);
            await formatted(document, data.expected, 'Format Document indents the module and gives it CRLF line endings');
            assert.strictEqual(document.eol, vscode.EndOfLine.CRLF, 'The document has CRLF line endings');
        });
    });

    playbook('X365', async (workspace, data) => {
        const document = await workspace.open(data.file, data.source);
        await noErrors(document.uri);
        await withEditorConfig(workspace, data.editorConfig, async write => {
            await write(data.trim);
            await formatted(document, data.trimmed, 'trim_trailing_whitespace = true trims the trailing whitespace, which VS Code does not ask for');
            await workspace.replace(document, data.source);
            // VS Code asks the formatter to trim trailing whitespace with files.trimTrailingWhitespace, which the case's
            // cleanup puts back: without the property, it is trimmed
            await vscode.workspace.getConfiguration('files', document.uri).update('trimTrailingWhitespace', true, vscode.ConfigurationTarget.Workspace);
            await write(data.without);
            await formatted(document, data.trimmed, 'Without trim_trailing_whitespace, VS Code asks to trim the trailing whitespace');
            await workspace.replace(document, data.source);
            await write(data.keep);
            await formatted(document, data.kept, 'trim_trailing_whitespace = false keeps the trailing whitespace, which VS Code asks to trim');
        });
    });
}
