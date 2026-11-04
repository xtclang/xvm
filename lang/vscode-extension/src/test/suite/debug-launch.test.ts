import * as assert from 'node:assert';
import * as vscode from 'vscode';
import type { LanguageClient } from 'vscode-languageclient/node';
import { XtcDebugConfigurationProvider } from '../../debug-adapter';
import { waitFor } from '../wait';

suite('Debug launch', () => {
    test("F5 without a configuration in a class's file debugs the module whose directory holds it", async () => {
        const folder = vscode.workspace.workspaceFolders![0];
        const root = vscode.Uri.joinPath(folder.uri, 'hello.x');
        const inside = vscode.Uri.joinPath(folder.uri, 'hello', 'Greeter.x');
        const beside = vscode.Uri.joinPath(folder.uri, 'helloExtra', 'Greeter.x');
        // the language server's source graph, as xtc/compilerSourceModules answers it
        const client = {
            isRunning: () => true,
            sendRequest: async (method: string) => method === 'xtc/compilerSourceModules' ? [{ name: 'HelloEcstasy', uri: root.toString() }] : undefined,
        } as unknown as LanguageClient;
        const provider = new XtcDebugConfigurationProvider(() => client);
        // F5 resolves an empty configuration from the active editor
        const launch = async (file: vscode.Uri) => {
            await vscode.workspace.fs.writeFile(file, Buffer.from('class Greeter {}\n'));
            const editor = await vscode.window.showTextDocument(await vscode.workspace.openTextDocument(file));
            await waitFor(async () => editor.document.languageId, id => id === 'xtc', `${file.path} is an Ecstasy file`);
            return provider.resolveDebugConfiguration(folder, {} as vscode.DebugConfiguration);
        };
        try {
            const resolved = await launch(inside);
            assert.strictEqual(resolved?.module, 'HelloEcstasy');
            assert.strictEqual(resolved?.moduleUri, inside.toString());
            assert.strictEqual(resolved?.request, 'launch');
            // a directory that only starts with the module's name is not the module's
            assert.strictEqual(await launch(beside), undefined);
        } finally {
            await vscode.commands.executeCommand('workbench.action.closeAllEditors');
            for (const directory of ['hello', 'helloExtra']) {
                await vscode.workspace.fs.delete(vscode.Uri.joinPath(folder.uri, directory), { recursive: true, useTrash: false });
            }
        }
    });
});
