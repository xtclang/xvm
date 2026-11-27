import * as assert from 'node:assert';
import * as vscode from 'vscode';
import { LSPErrorCodes, ResponseError, type LanguageClient } from 'vscode-languageclient/node';
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

    /**
     * A language server whose answers to xtc/renameFiles are [answers] in turn, each a refusal because a change of the
     * project overtook it, ContentModified, a cancellation that the client did not ask for, as the server's when a
     * document's close retires its graph queries, an edit during which a document with unsaved text opens, or the edit
     * [edit], which the client converts to [converted]; [asked] counts the requests. Its handling of a failed request is
     * the language client's: a cancellation the client did not ask for fails, and ContentModified answers nothing.
     */
    function overtaken(answers: readonly ('overtaken' | 'cancelled' | 'opened' | 'edit')[], converted: vscode.WorkspaceEdit) {
        const edit = { documentChanges: [] };
        const asked = { count: 0 };
        const client = {
            sendRequest: async () => {
                const answer = answers[Math.min(asked.count++, answers.length - 1)];
                if (answer === 'overtaken') throw new ResponseError(LSPErrorCodes.ContentModified, 'The content was modified');
                if (answer === 'cancelled') throw new ResponseError(LSPErrorCodes.RequestCancelled, 'The request was cancelled');
                // a document that opens with unsaved text as the server proves the move, as an untitled one with content does
                if (answer === 'opened') await vscode.workspace.openTextDocument({ language: 'xtc', content: 'module Opened {}' });
                return { edit };
            },
            handleFailedRequest: (_type: unknown, token: vscode.CancellationToken, error: unknown, value: unknown) => {
                if (error instanceof ResponseError && error.code === LSPErrorCodes.RequestCancelled && !token.isCancellationRequested) {
                    throw new vscode.CancellationError();
                }
                return value;
            },
            protocol2CodeConverter: { asWorkspaceEdit: async () => converted },
            validateWorkspaceEdit: () => true,
        } as unknown as LanguageClient;
        return { client, asked };
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

    test('a move that a change of the project overtook asks the server again, and gets its edits', async () => {
        const hand = vscode.Uri.joinPath(folder(), 'cardGame', 'Hand.x');
        const uses = new vscode.WorkspaceEdit();
        uses.replace(vscode.Uri.joinPath(folder(), 'cardGame', 'Player.x'), new vscode.Range(0, 0, 0, 4), 'PlayerHand');
        const { client, asked } = overtaken(['overtaken', 'edit'], uses);
        const warnings: string[] = [];
        // the server's first answer came as a file written on disk changed the project: the second proves the move
        const edit = await moveWithConfiguration(client, move(hand, vscode.Uri.joinPath(folder(), 'cardGame', 'PlayerHand.x')),
            async moved => [...moved], message => warnings.push(message));
        assert.strictEqual(edit, uses);
        assert.strictEqual(asked.count, 2);
        assert.deepStrictEqual(warnings, []);
    });

    test('a move whose proof the server cancelled, as a document closed, or during which a document with unsaved text opened, asks the server again', async () => {
        const hand = vscode.Uri.joinPath(folder(), 'cardGame', 'Hand.x');
        const uses = new vscode.WorkspaceEdit();
        uses.replace(vscode.Uri.joinPath(folder(), 'cardGame', 'Player.x'), new vscode.Range(0, 0, 0, 4), 'PlayerHand');
        const before = new Set(vscode.workspace.textDocuments);
        try {
            for (const first of ['cancelled', 'opened'] as const) {
                const { client, asked } = overtaken([first, 'edit'], uses);
                const warnings: string[] = [];
                // run-InIBIg's X216 and run-Oz8vjq's X335: VS Code moved the files without the server's edits, and said nothing
                const edit = await moveWithConfiguration(client, move(hand, vscode.Uri.joinPath(folder(), 'cardGame', 'PlayerHand.x')),
                    async moved => [...moved], message => warnings.push(message));
                assert.strictEqual(edit, uses, first);
                assert.strictEqual(asked.count, 2, first);
                assert.deepStrictEqual(warnings, [], first);
            }
        } finally {
            for (const opened of vscode.workspace.textDocuments.filter(document => !before.has(document))) {
                await vscode.window.showTextDocument(opened);
                await vscode.commands.executeCommand('workbench.action.revertAndCloseActiveEditor');
            }
        }
    });

    test('a move that changes of the project overtake each time it is asked says that its uses were not updated', async () => {
        const hand = vscode.Uri.joinPath(folder(), 'cardGame', 'Hand.x');
        const { client, asked } = overtaken(['overtaken'], new vscode.WorkspaceEdit());
        const warnings: string[] = [];
        const edit = await moveWithConfiguration(client, move(hand, vscode.Uri.joinPath(folder(), 'cardGame', 'PlayerHand.x')),
            async moved => [...moved], message => warnings.push(message));
        assert.strictEqual(edit, null);
        assert.strictEqual(asked.count, 4);
        assert.strictEqual(warnings.length, 1);
        assert.ok(warnings[0].includes('Hand.x') && warnings[0].includes('not updated') && warnings[0].includes('the project changed'), warnings[0]);
    });

    test('a move of a source that nothing else uses moves without a warning', async () => {
        const warnings: string[] = [];
        const lone = vscode.Uri.joinPath(folder(), 'Lone.x');
        await moveWithConfiguration(server(async () => null), move(lone, vscode.Uri.joinPath(folder(), 'moved', 'Lone.x')),
            async () => [], message => warnings.push(message));
        assert.deepStrictEqual(warnings, []);
    });
});
