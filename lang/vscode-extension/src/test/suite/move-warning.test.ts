import * as assert from 'node:assert';
import * as vscode from 'vscode';
import type { LanguageClient } from 'vscode-languageclient/node';
import { moveWithConfiguration } from '../../rename-proposal';

suite('File moves', () => {
    const folder = () => vscode.workspace.workspaceFolders![0].uri;

    /** A move of [file] to [to], as VS Code's Explorer announces it to the participants. */
    function move(file: vscode.Uri, to: vscode.Uri): vscode.FileWillRenameEvent {
        return { files: [{ oldUri: file, newUri: to }], token: new vscode.CancellationTokenSource().token, waitUntil: () => undefined };
    }

    /** The language server's answer to xtc/renameFiles: no edits, or a failure with its reason. */
    function server(answer: () => Promise<null>): LanguageClient {
        return {
            sendRequest: answer,
            handleFailedRequest: (_type: unknown, _token: unknown, _error: unknown, value: unknown) => value,
        } as unknown as LanguageClient;
    }

    test("a move whose uses the server cannot update says so, naming the file and the server's reason", async () => {
        const suit = vscode.Uri.joinPath(folder(), 'cardGame', 'Suit.x');
        const warnings: string[] = [];
        // the Explorer moves the file however the participants answer; no edits for a used type leave its uses broken
        const edit = await moveWithConfiguration(server(async () => null), move(suit, vscode.Uri.joinPath(folder(), 'cardGame', 'cards', 'Suit.x')),
            async moved => [...moved], message => warnings.push(message));
        assert.strictEqual(edit, null);
        assert.strictEqual(warnings.length, 1);
        assert.ok(warnings[0].includes('Suit.x') && warnings[0].includes('not updated'), warnings[0]);

        const failed = await moveWithConfiguration(server(async () => { throw new Error('the destination package is shadowed'); }),
            move(suit, vscode.Uri.joinPath(folder(), 'cardGame', 'cards', 'Suit.x')), async moved => [...moved], message => warnings.push(message));
        assert.strictEqual(failed, null);
        assert.ok(warnings[1]?.includes('the destination package is shadowed'), warnings[1]);
    });

    test('a move of a source that nothing else uses moves without a warning', async () => {
        const warnings: string[] = [];
        const lone = vscode.Uri.joinPath(folder(), 'Lone.x');
        await moveWithConfiguration(server(async () => null), move(lone, vscode.Uri.joinPath(folder(), 'moved', 'Lone.x')),
            async () => [], message => warnings.push(message));
        assert.deepStrictEqual(warnings, []);
    });
});
