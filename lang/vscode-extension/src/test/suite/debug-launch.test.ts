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

    test('a launch and an attach set the time limits of code the debugger runs, with the server\'s own as defaults', async () => {
        const extension = vscode.extensions.getExtension('xtclang.xtc-language');
        assert.ok(extension);
        type Attribute = { type: string; default: number; minimum: number; maximum: number; description: string };
        const attributes = (extension.packageJSON.contributes.debuggers as {
            configurationAttributes: Record<string, { properties: Record<string, Attribute> }>;
        }[])[0].configurationAttributes;
        for (const request of ['launch', 'attach']) {
            const { evaluationTimeLimit, lazyValueTimeLimit } = attributes[request].properties;
            assert.deepStrictEqual([evaluationTimeLimit.type, evaluationTimeLimit.default, evaluationTimeLimit.minimum, evaluationTimeLimit.maximum],
                ['integer', 2000, 100, 60000], request);
            assert.deepStrictEqual([lazyValueTimeLimit.type, lazyValueTimeLimit.default, lazyValueTimeLimit.minimum, lazyValueTimeLimit.maximum],
                ['integer', 1000, 100, 30000], request);
            assert.match(evaluationTimeLimit.description, /milliseconds/);
            assert.match(lazyValueTimeLimit.description, /milliseconds/);
        }

        // the language server's session reads them from the configuration, which resolving keeps as it is
        const provider = new XtcDebugConfigurationProvider(() => undefined);
        const limits = { evaluationTimeLimit: 5000, lazyValueTimeLimit: 2500 };
        const attach = { type: 'xtc', request: 'attach', name: 'Attach', port: 5005, ...limits };
        assert.deepStrictEqual(await provider.resolveDebugConfiguration(undefined, { ...attach }), attach);
        const launch = await provider.resolveDebugConfiguration(undefined, { type: 'xtc', request: 'launch', name: 'Debug', module: 'App', ...limits });
        assert.deepStrictEqual([launch?.evaluationTimeLimit, launch?.lazyValueTimeLimit], [5000, 2500]);
    });
});
