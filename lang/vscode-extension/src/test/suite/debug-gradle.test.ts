import * as assert from 'node:assert';
import * as vscode from 'vscode';
import { gradleDebugCommand, gradleTestArgs, XtcDebugConfigurationProvider } from '../../debug-adapter';

suite('Gradle-mode debugging', () => {
    test('the debug task runs the module in a JVM of its own, with the debug properties', () => {
        const params = {
            sessionId: 's',
            module: 'App',
            method: 'check',
            args: ['one', 'two'],
            debug: 'connect:127.0.0.1:5005,suspend,token=t',
        };
        assert.deepStrictEqual(gradleDebugCommand(params, 'darwin'), {
            command: './gradlew',
            args: ['-q', 'runXtc', '--mode=ATTACHED', '--module=App', '--method=check', '--args=one,two',
                '-PxtcDebug=connect:127.0.0.1:5005,suspend,token=t'],
        });
        const plain = gradleDebugCommand({ sessionId: 's', module: 'App', args: [], debug: 'connect:127.0.0.1:1' }, 'win32');
        assert.deepStrictEqual(plain, {
            command: 'gradlew.bat',
            args: ['-q', 'runXtc', '--mode=ATTACHED', '--module=App', '-PxtcDebug=connect:127.0.0.1:1'],
        });
    });

    test('testXtc runs the chosen tests of the module, all of them for none, with xunit\'s TeamCity report', () => {
        assert.deepStrictEqual(gradleTestArgs('MathyTest', [
            { selector: 'tests', kind: 'package' },
            { selector: 'Inner', kind: 'class' },
            { selector: 'Inner.works', kind: 'test' },
        ]), ['-q', 'testXtc', '--module=MathyTest', '--test-package=tests', '--test-class=Inner', '--test-method=Inner.works',
            '--test-report=teamcity']);
        assert.deepStrictEqual(gradleTestArgs('MathyTest', []), ['-q', 'testXtc', '--module=MathyTest', '--test-report=teamcity']);
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
            // a Test Explorer debug run of tests too: the module's testXtc runs them
            const tests = { ...launch, tests: [{ selector: 'Inner.works', kind: 'test' }] };
            assert.deepStrictEqual(await provider.resolveDebugConfiguration(undefined, { ...tests }), { ...tests, buildMode: 'gradle' });
        } finally {
            await settings().update('build.mode', before, vscode.ConfigurationTarget.Workspace);
        }
    });
});
