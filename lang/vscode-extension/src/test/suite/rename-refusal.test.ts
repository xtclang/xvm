import * as assert from 'node:assert';
import * as vscode from 'vscode';
import { BaseLanguageClient, LSPErrorCodes, ResponseError, type LanguageClient, type MessageSignature } from 'vscode-languageclient/node';
import { RENAME_REFUSED, renameWithConfiguration } from '../../rename-proposal';

suite('Rename refusals', () => {
    /**
     * A language server whose answer to xtc/rename is [answer]. A failure goes through the language client's own
     * handling of a failed request, which ends a request without an answer or throws its error on.
     */
    function server(answer: () => Promise<null>): LanguageClient {
        const logger = { error: () => undefined } as unknown as BaseLanguageClient;
        return {
            sendRequest: answer,
            handleFailedRequest: (type: MessageSignature, token: vscode.CancellationToken, error: unknown, value: null, notify: boolean) =>
                BaseLanguageClient.prototype.handleFailedRequest.call(logger, type, token, error, value, notify),
            code2ProtocolConverter: {
                asTextDocumentIdentifier: (document: vscode.TextDocument) => ({ uri: document.uri.toString() }),
                asPosition: (position: vscode.Position) => ({ line: position.line, character: position.character }),
            },
        } as unknown as LanguageClient;
    }

    /**
     * What VS Code says of a rename of card to name, which a rename provider asks [client] for: the message of its rename
     * box, with which VS Code's rename command fails, or nothing when it makes an edit.
     */
    async function said(client: LanguageClient): Promise<string | undefined> {
        const document = await vscode.workspace.openTextDocument({ language: 'plaintext', content: 'Card card := eights.drawCard();' });
        const registration = vscode.languages.registerRenameProvider({ scheme: 'untitled', language: 'plaintext' }, {
            provideRenameEdits: (document, position, name, token) => renameWithConfiguration(client, document, position, name, token),
        });
        try {
            return await vscode.commands.executeCommand('vscode.executeDocumentRenameProvider', document.uri, new vscode.Position(0, 6), 'name')
                .then(() => undefined, (error: unknown) => error instanceof Error ? error.message : String(error));
        } finally {
            registration.dispose();
        }
    }

    test('a rename that the server answers with no edit says it is refused, not that there is no result', async () => {
        assert.strictEqual(await said(server(async () => null)), RENAME_REFUSED);
    });

    test("a rename that the server refuses with its reason says the server's reason", async () => {
        const reason = 'A rename in a workspace that compiles ecstasy from its sources needs a language server heap of at least 2 GB';
        assert.strictEqual(await said(server(async () => { throw new ResponseError(LSPErrorCodes.RequestFailed, reason); })), reason);
    });

    test('a rename that ends without an answer, as the server drops it as a document changes, says there is no result', async () => {
        const dropped = server(async () => { throw new ResponseError(LSPErrorCodes.ContentModified, 'Document changed during analysis'); });
        assert.strictEqual(await said(dropped), 'No result.');
    });
});
