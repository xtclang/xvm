import * as assert from 'node:assert';
import * as vscode from 'vscode';
import { BaseLanguageClient, LSPErrorCodes, ResponseError, type LanguageClient, type MessageSignature } from 'vscode-languageclient/node';
import { RENAME_OVERTAKEN, RENAME_REFUSED, TEXTS_CHANGED, renameWithConfiguration, sourcesChanged, textHash } from '../../rename-proposal';

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

    test("a rename that the server can't prove while a module does not compile says the server's reason", async () => {
        const reason = "This rename can't be proven while Library does not compile: fix the errors, then rename again";
        assert.strictEqual(await said(server(async () => { throw new ResponseError(LSPErrorCodes.RequestFailed, reason, 'unproven'); })), reason);
    });

    test('a rename that a change of the project overtook says so, to rename again, rather than that there is no result', async () => {
        const overtaken = server(async () => { throw new ResponseError(LSPErrorCodes.ContentModified, 'The project changed while the rename was proven'); });
        assert.strictEqual(await said(overtaken), RENAME_OVERTAKEN);
    });

    /**
     * A language server whose answer to xtc/rename is the edit of [file], with [hash] as the hash of the text that it read
     * of the file, or none, as a server that proves no text, once [meanwhile] has run, as a change of the file after the
     * server read it.
     */
    function proving(file: vscode.Uri, hash: string | undefined, meanwhile: () => Thenable<void> = async () => undefined): LanguageClient {
        const edit = {
            documentChanges: [{
                textDocument: { uri: file.toString(), version: null },
                edits: [{ range: { start: { line: 0, character: 6 }, end: { line: 0, character: 12 } }, newText: 'Renamed' }],
            }],
        };
        return {
            ...server(async () => null),
            sendRequest: async () => {
                await meanwhile();
                return { edit, textHashes: hash === undefined ? undefined : { [file.toString()]: hash } };
            },
            protocol2CodeConverter: { asWorkspaceEdit: async () => new vscode.WorkspaceEdit() },
            validateWorkspaceEdit: () => true,
        } as unknown as LanguageClient;
    }

    /** The file [name] in a folder of the workspace's own, written with [content]. */
    async function written(name: string, content: Uint8Array): Promise<vscode.Uri> {
        const file = vscode.Uri.joinPath(vscode.workspace.workspaceFolders![0].uri, 'proved', name);
        await vscode.workspace.fs.writeFile(file, content);
        return file;
    }

    const read = 'class Proved {\r\n}\r\n';

    test("a rename's edit of a closed file is made while the file has the text that the server read, CRLF or LF", async () => {
        const before = new Set(vscode.workspace.textDocuments);
        try {
            // the server's hash of the file's text, whose line breaks VS Code's document keeps and IntelliJ's does not
            assert.strictEqual(await said(proving(await written('Proved.x', Buffer.from(read)), textHash('class Proved {\n}\n'))), undefined);
            // a byte order mark, which neither the server's text nor VS Code's document has
            const marked = await written('Marked.x', Buffer.concat([Buffer.from([0xEF, 0xBB, 0xBF]), Buffer.from(read)]));
            assert.strictEqual(await said(proving(marked, textHash(read))), undefined);
            // a change of the file on the disk after the server read it, which no document version says; a file of its
            // own, which no document of VS Code holds as the disk changes
            const changed = await written('Changed.x', Buffer.from(read));
            assert.strictEqual(await said(proving(changed, textHash(read), () => vscode.workspace.fs.writeFile(changed, Buffer.from(`// changed\n${read}`)))),
                TEXTS_CHANGED);
            // a server that proves no text read no closed file
            assert.strictEqual(await said(proving(await written('Unproved.x', Buffer.from(read)), undefined)), TEXTS_CHANGED);
        } finally {
            await closeOpenedSince(before);
            await vscode.workspace.fs.delete(vscode.Uri.joinPath(vscode.workspace.workspaceFolders![0].uri, 'proved'), { recursive: true });
        }
    });

    test("a rename's edit of an open document is made while its buffer has the text that the server read, not its file's", async () => {
        const before = new Set(vscode.workspace.textDocuments);
        try {
            const file = await written('Open.x', Buffer.from(read));
            const document = await vscode.workspace.openTextDocument(file);
            const edit = new vscode.WorkspaceEdit();
            edit.insert(file, new vscode.Position(0, 0), '// edited\n');
            assert.ok(await vscode.workspace.applyEdit(edit));
            assert.ok(document.isDirty);
            assert.strictEqual(await said(proving(file, textHash(document.getText()))), undefined);
            assert.strictEqual(await said(proving(file, textHash(read))), TEXTS_CHANGED);
        } finally {
            await closeOpenedSince(before);
            await vscode.workspace.fs.delete(vscode.Uri.joinPath(vscode.workspace.workspaceFolders![0].uri, 'proved'), { recursive: true });
        }
    });

    /** Reverts and closes each document that VS Code has opened since [before], as a test's check opens documents. */
    async function closeOpenedSince(before: ReadonlySet<vscode.TextDocument>): Promise<void> {
        for (const opened of vscode.workspace.textDocuments.filter(document => !before.has(document) && document.uri.scheme === 'file')) {
            await vscode.window.showTextDocument(opened);
            await vscode.commands.executeCommand('workbench.action.revertAndCloseActiveEditor');
        }
    }

    test("a text's hash is the server's, the same whatever its line breaks", () => {
        // RenameProposalTest's HASH, of the protocol's textHash
        const hash = '24387ab61b227358c8984aac4ad34e3a48230b95557358d6a20112944842391d';
        assert.strictEqual(textHash('class Other {\n}\n'), hash);
        assert.strictEqual(textHash('class Other {\r\n}\r\n'), hash);
        assert.strictEqual(textHash('class Other {\r}\r'), hash);
        assert.notStrictEqual(textHash('class Other {\n}'), hash);
    });

    /** A document as VS Code has it, at [version], with unsaved edits or not, open or closed. */
    function source(version: number, dirty: boolean, closed = false): vscode.TextDocument {
        return { version, isDirty: dirty, isClosed: closed } as vscode.TextDocument;
    }

    test('the sources a refactoring was proven from changed only by an edit, or by a close or open of unsaved text', () => {
        const kept = source(2, false);
        const edited = source(2, true);
        const before = [{ document: kept, version: 2, dirty: false }, { document: edited, version: 2, dirty: true }];
        assert.strictEqual(sourcesChanged(before, [kept, edited]), false, 'nothing changed');
        // as VS Code releases a document that no editor shows, or opens one for a peek, with its text as its file has it
        const released = [{ document: source(2, false, true), version: 2, dirty: false }, before[1]];
        assert.strictEqual(sourcesChanged(released, [edited, source(1, false)]), false, 'a release and an open of saved text');
        assert.strictEqual(sourcesChanged([{ document: source(3, true), version: 2, dirty: false }], []), true, 'an edit');
        assert.strictEqual(sourcesChanged([{ document: source(3, false, true), version: 2, dirty: false }], []), true, 'an edit, then a close');
        assert.strictEqual(sourcesChanged([{ document: source(2, true, true), version: 2, dirty: true }], []), true, 'a close of unsaved text');
        assert.strictEqual(sourcesChanged(before, [kept, edited, source(2, true)]), true, 'an open of unsaved text');
    });
});
