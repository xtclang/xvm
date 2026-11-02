import * as assert from 'node:assert';
import * as vscode from 'vscode';
import { gradleDebugCommand, XtcDebugConfigurationProvider } from '../../debug-adapter';

suite('Gradle-mode debugging', () => {
    test('the debug task runs the module in a JVM of its own, with the debug properties', () => {
        const params = {
            sessionId: 's',
            module: 'App',
            method: 'check',
            args: ['one', 'two'],
            debug: 'connect:127.0.0.1:5005,suspend,token=t',
            agent: '/x/server/lsp-server.jar',
        };
        assert.deepStrictEqual(gradleDebugCommand(params, 'darwin'), {
            command: './gradlew',
            args: ['-q', 'runXtc', '--mode=ATTACHED', '--module=App', '--method=check', '--args=one,two',
                '-PxtcDebug=connect:127.0.0.1:5005,suspend,token=t', '-PxtcDebugAgent=/x/server/lsp-server.jar'],
        });
        const plain = gradleDebugCommand({ sessionId: 's', module: 'App', args: [], debug: 'connect:127.0.0.1:1' }, 'win32');
        assert.deepStrictEqual(plain, {
            command: 'gradlew.bat',
            args: ['-q', 'runXtc', '--mode=ATTACHED', '--module=App', '-PxtcDebug=connect:127.0.0.1:1'],
        });
    });

    test('a Debug launch in Gradle mode starts a debug session that Gradle runs the module for', async () => {
        const settings = () => vscode.workspace.getConfiguration('xtc');
        const before = settings().inspect<string>('build.mode')?.workspaceValue;
        await settings().update('build.mode', 'gradle', vscode.ConfigurationTarget.Workspace);
        try {
            const provider = new XtcDebugConfigurationProvider(() => undefined);
            const launch = { type: 'xtc', request: 'launch', name: 'Debug App', module: 'App' };
            assert.deepStrictEqual(await provider.resolveDebugConfiguration(undefined, { ...launch }),
                { ...launch, buildMode: 'gradle' });
        } finally {
            await settings().update('build.mode', before, vscode.ConfigurationTarget.Workspace);
        }
    });
});
