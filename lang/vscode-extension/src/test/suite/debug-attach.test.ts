import * as assert from 'node:assert';
import * as vscode from 'vscode';
import { ATTACH_PORT, debugOption, XtcDebugConfigurationProvider } from '../../debug-adapter';

suite('Debug attach', () => {
    test('the copied option waits for an attach on this machine, with the server jar as the agent', () => {
        assert.strictEqual(debugOption('/x/server/lsp-server.jar'),
            `--debug listen:127.0.0.1:${ATTACH_PORT},suspend,agent=/x/server/lsp-server.jar`);
        assert.strictEqual(debugOption('C:\\Users\\A B\\lsp-server.jar', 6000),
            '--debug "listen:127.0.0.1:6000,suspend,agent=C:\\Users\\A B\\lsp-server.jar"');
    });

    test('an attach needs no module and starts a debug session in Gradle mode too', async () => {
        const settings = () => vscode.workspace.getConfiguration('xtc');
        const before = settings().inspect<string>('build.mode')?.workspaceValue;
        await settings().update('build.mode', 'gradle', vscode.ConfigurationTarget.Workspace);
        try {
            const provider = new XtcDebugConfigurationProvider(() => undefined);
            const attach = { type: 'xtc', request: 'attach', name: 'Attach to Ecstasy Program', port: ATTACH_PORT };
            assert.deepStrictEqual(await provider.resolveDebugConfiguration(undefined, { ...attach }), attach);
        } finally {
            await settings().update('build.mode', before, vscode.ConfigurationTarget.Workspace);
        }
    });

    test('the attach configuration, its snippet and the copy command are contributed', async () => {
        const extension = vscode.extensions.getExtension('xtclang.xtc-language');
        assert.ok(extension);
        const debuggers = extension.packageJSON.contributes.debuggers as {
            configurationAttributes: Record<string, { required: string[] }>;
            configurationSnippets: { body: { request: string; port?: number } }[];
        }[];
        // VS Code adds name, type and request to every configuration's required attributes
        assert.ok(debuggers[0].configurationAttributes.attach.required.includes('port'));
        const snippet = debuggers[0].configurationSnippets.find(each => each.body.request === 'attach');
        assert.strictEqual(snippet?.body.port, ATTACH_PORT);
        await extension.activate();
        assert.ok((await vscode.commands.getCommands(true)).includes('xtc.debug.copyOption'));
    });
});
