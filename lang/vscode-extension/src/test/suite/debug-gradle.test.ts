import * as assert from 'node:assert';
import * as vscode from 'vscode';
import { gradleDebugCommand, gradleTestArgs, XtcDebugConfigurationProvider } from '../../debug-adapter';
import { gradleTask, gradleTasks } from '../../gradle-projects';
import { gradleRunCommand } from '../../task-provider';

suite('Gradle-mode debugging', () => {
    test('the debug task runs the module in a JVM of its own, with the debug properties', () => {
        const params = {
            sessionId: 's',
            module: 'App',
            method: 'check',
            args: ['one', 'two'],
            debug: 'connect:127.0.0.1:5005,suspend,token=t',
        };
        assert.deepStrictEqual(gradleDebugCommand(params, 'darwin', {}), {
            command: './gradlew',
            args: ['-q', 'runXtc', '--mode=ATTACHED', '--module=App', '--method=check', '--args=one,two',
                '-PxtcDebug=connect:127.0.0.1:5005,suspend,token=t'],
        });
        const plain = gradleDebugCommand({ sessionId: 's', module: 'App', args: [], debug: 'connect:127.0.0.1:1' }, 'win32', {});
        assert.deepStrictEqual(plain, {
            command: 'gradlew.bat',
            args: ['-q', 'runXtc', '--mode=ATTACHED', '--module=App', '-PxtcDebug=connect:127.0.0.1:1'],
        });
    });

    test('testXtc runs the chosen tests of the module, all of them for none, with xunit\'s json report', () => {
        assert.deepStrictEqual(gradleTestArgs('MathyTest', [
            { selector: 'tests', kind: 'package' },
            { selector: 'Inner', kind: 'class' },
            { selector: 'Inner.works', kind: 'test' },
        ], {}), ['-q', 'testXtc', '--module=MathyTest', '--test-package=tests', '--test-class=Inner', '--test-method=Inner.works',
            '--test-report=json']);
        assert.deepStrictEqual(gradleTestArgs('MathyTest', [], {}), ['-q', 'testXtc', '--module=MathyTest', '--test-report=json']);
    });

    test('a module\'s task is its Gradle project\'s, as the imported model names it, which reaches a composite\'s included build', () => {
        const projects = { 'json.xtclang.org': ':xdk:lib-json', App: ':' };
        const params = { sessionId: 's', module: 'json.xtclang.org', args: [], debug: 'connect:127.0.0.1:1' };
        assert.deepStrictEqual(gradleDebugCommand(params, 'darwin', projects).args.slice(0, 2), ['-q', ':xdk:lib-json:runXtc']);
        assert.deepStrictEqual(gradleTestArgs('json.xtclang.org', [], projects).slice(0, 2), ['-q', ':xdk:lib-json:testXtc']);
        assert.deepStrictEqual(gradleRunCommand({ type: 'xtc', moduleName: 'json.xtclang.org' }, projects).args.slice(0, 2),
            ['-q', ':xdk:lib-json:runXtc']);
        // a root project's task, and the bare name for a module that no model names
        assert.strictEqual(gradleTask('App', 'runXtc', projects), ':runXtc');
        assert.strictEqual(gradleTask('Other', 'runXtc', projects), 'runXtc');
        // every test module: each project's testXtc, or the bare name without a model
        assert.deepStrictEqual(gradleTasks('testXtc', projects), [':testXtc', ':xdk:lib-json:testXtc']);
        assert.deepStrictEqual(gradleTasks('testXtc', {}), ['testXtc']);
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
