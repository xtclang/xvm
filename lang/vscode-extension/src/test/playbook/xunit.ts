import * as assert from 'node:assert';
import { spawn } from 'node:child_process';
import * as fs from './disk';
import * as os from 'node:os';
import * as path from 'node:path';
import * as vscode from 'vscode';
import { testLocation } from '../../test-explorer';
import { watchProcess } from '../uiWait';
import { caseSignal } from '../wait';
import { configureModules, TestWatch, watchTests } from './execution';
import { client, diagnosticCode, diagnostics, eventually, noErrors, playbook } from './support';

/** How many embedded runs the server still tracks, by its language service status. */
async function embeddedRuns(): Promise<number> {
    return (await client().sendRequest<{ embeddedRuns?: number }>('xtc/languageServiceStatus')).embeddedRuns ?? -1;
}

/**
 * X342-X349, registered after X341 in catalog order: xunit tests stopped partway, added while the project is open,
 * thrown, skipped, rerun when they failed, at their sources, in several modules, and run by the command line.
 */
export function xunitCases(): void {
    playbook('X342', async (workspace, data) => {
        await workspace.module(data.file, data.source, data.module);
        await watchTests(async tests => {
            await tests.listing(data.items);
            const running = vscode.commands.executeCommand('testing.runAll');
            // xunit runs the module's tests one at a time, in an order of its own: the stop comes as either waiting test starts
            const blocking: string[] = data.blocking;
            await eventually(async () => blocking.some(id => tests.started.includes(id)), started => started, 'A waiting test starts');
            await vscode.commands.executeCommand('testing.cancelRun');
            await running;
            const reports = await tests.results([data.passed, ...blocking], 'The stopped run reports every test');
            // a test that had started and not ended is ended by the stop, one that had not started did not run
            const notPassed = (id: string) => tests.started.includes(id) ? data.ended : data.notRun;
            const [passed, ...waiting] = reports;
            assert.ok(passed.state === 'passed' || (passed.state === 'errored' && passed.message?.includes(data.notRun)), JSON.stringify(passed));
            waiting.forEach(report => assert.ok(report.state === 'errored' && report.message?.includes(notPassed(report.id)), JSON.stringify(report)));
            await eventually(embeddedRuns, count => count === 0, 'No program of the stopped run is left');
        });
    });

    playbook('X343', async (workspace, data) => {
        const document = await workspace.module(data.file, data.source, data.module);
        await watchTests(async tests => {
            await tests.listing(data.items);
            const editor = await vscode.window.showTextDocument(document);
            const end = document.positionAt(document.getText().indexOf(data.after) + data.after.length);
            assert.ok(await editor.edit(edit => edit.insert(end, data.added)), 'The test is typed');
            assert.ok(await document.save(), 'The module is saved');
            // listed again as the server's analysis of the edit changes the module's tests, with no refresh asked for
            await eventually(async () => tests.listed, listed => listed.includes(data.addedItem), 'The added test is listed without a restart');
            const name = new vscode.Position(data.nameLine - 1, data.nameColumn);
            editor.selection = new vscode.Selection(name, name);
            tests.reports.length = 0;
            await vscode.commands.executeCommand('testing.runAtCursor');
            const [added] = await tests.results([data.addedItem], 'The added test runs');
            assert.strictEqual(added.state, 'passed', JSON.stringify(added));
            assert.deepStrictEqual(tests.reports.map(report => report.id), [data.addedItem], 'It runs alone');
        });
    });

    playbook('X344', async (workspace, data) => {
        await workspace.module(data.file, data.source, data.module);
        await watchTests(async tests => {
            await tests.listing(data.items);
            await vscode.commands.executeCommand('testing.runAll');
            const [failed, errored] = await tests.results([data.failed, data.errored], 'Run All reports both tests');
            assert.ok(failed.state === 'failed' && failed.message === data.failure && failed.line === data.failureLine, JSON.stringify(failed));
            assert.ok(errored.state === 'errored' && errored.message?.includes(data.error) && errored.line === data.errorLine,
                `A test that throws is an error, apart from a failure: ${JSON.stringify(errored)}`);
        });
    });

    playbook('X345', async (workspace, data) => {
        await workspace.module(data.file, data.source, data.module);
        await watchTests(async tests => {
            await tests.listing(data.items);
            await vscode.commands.executeCommand('testing.runAll');
            const [passed, disabled, precondition] = await tests.results([data.passed, data.disabled, data.precondition], 'Run All reports every test');
            assert.strictEqual(passed.state, 'passed', JSON.stringify(passed));
            assert.ok(disabled.state === 'skipped' && disabled.message?.includes(data.disabledReason), JSON.stringify(disabled));
            assert.ok(precondition.state === 'skipped' && precondition.message?.includes(data.preconditionReason), JSON.stringify(precondition));
        });
    });

    playbook('X346', async (workspace, data) => {
        await workspace.module(data.file, data.source, data.module);
        await watchTests(async tests => {
            await tests.listing(data.items);
            await vscode.commands.executeCommand('testing.runAll');
            const first = await tests.results([data.passed, ...data.failed], 'Run All reports every test');
            assert.deepStrictEqual(first.map(report => report.state), ['passed', ...data.failed.map(() => 'failed')], JSON.stringify(first));
            tests.reports.length = 0;
            await vscode.commands.executeCommand('testing.reRunFailTests');
            const rerun = await tests.results(data.failed, 'The rerun reports the failed tests');
            assert.ok(rerun.every(report => report.state === 'failed'), JSON.stringify(rerun));
            assert.deepStrictEqual([...new Set(tests.reports.map(report => report.id))].sort(), [...data.failed].sort(),
                'The rerun runs only the failed tests');
        });
    });

    playbook('X347', async (workspace, data) => {
        const document = await workspace.module(data.file, data.source, data.module);
        await watchTests(async tests => {
            await tests.listing(data.items);
            for (const [id, line] of Object.entries(data.tests)) {
                const location = testLocation(id);
                assert.ok(location && location.uri.toString() === document.uri.toString()
                    && location.range.start.line + 1 <= line && line <= location.range.end.line + 1,
                `${id} goes to its declaration, line ${line}: ${JSON.stringify(location)}`);
            }
            await vscode.commands.executeCommand('testing.runAll');
            const [failed] = await tests.results([data.failed], 'Run All reports the nested test');
            assert.ok(failed.state === 'failed' && failed.message === data.failure && failed.line === data.failureLine, JSON.stringify(failed));
        });
    });

    playbook('X348', async (workspace, data) => {
        for (const each of data.modules) await workspace.open(each.file, each.source);
        await configureModules(workspace, data.modules.map(each => ({ name: each.module, file: each.file })));
        for (const each of data.modules) await noErrors(workspace.uri(each.file));
        await watchTests(async tests => {
            await tests.listing([...data.passed, data.failed]);
            await vscode.commands.executeCommand('testing.runAll');
            const reports = await tests.results([...data.passed, data.failed], 'Run All reports every module\'s tests');
            assert.deepStrictEqual(reports.map(report => report.state), [...data.passed.map(() => 'passed'), 'failed'], JSON.stringify(reports));
            assert.strictEqual(reports[reports.length - 1].message, data.failure);
        });
    });

    playbook('X349', async (workspace, data) => {
        const document = await workspace.module(data.file, data.source, data.module);
        const xtc = path.resolve(__dirname, '../../../../../xdk/build/install/xdk/bin', process.platform === 'win32' ? 'xtc.bat' : 'xtc');
        assert.ok(await fs.stat(xtc).then(() => true, () => false), `X349 runs ${xtc}; run ./gradlew xdk:installDist first`);
        const tests = new TestWatch();
        const output = await fs.mkdtemp(path.join(os.tmpdir(), 'xtc-test-'));
        try {
            await tests.listing(data.items);
            await vscode.commands.executeCommand('testing.runAll');
            const embedded = await tests.results([data.passed, data.failed], 'The embedded run reports both tests');
            // the command line's xunit events: each started test's selector, and its finished status and message
            const printed = await new Promise<string>((resolve, reject) => {
                const program = spawn(xtc, ['test', '-o', output, '-I', 'xvm.xunit.report=json', document.uri.fsPath],
                    { cwd: path.dirname(document.uri.fsPath), shell: process.platform === 'win32', signal: caseSignal() });
                // under way while it prints or computes, and ended with the case once that is given up on
                watchProcess(program, 'xtc test');
                let text = '';
                program.stdout.on('data', (chunk: Buffer) => { text += chunk.toString('utf8'); });
                program.stderr.on('data', (chunk: Buffer) => { text += chunk.toString('utf8'); });
                program.on('error', reject);
                program.on('exit', () => resolve(text));
            });
            const events = printed.split(/\r?\n/).filter(line => line.startsWith('##xunit '))
                .map(line => JSON.parse(line.slice('##xunit '.length)) as { event: string; id?: string; selector?: string; status?: string; message?: string });
            const selectors = new Map(events.filter(each => each.event === 'started').map(each => [each.id, each.selector]));
            const finished = new Map(events.filter(each => each.event === 'finished' && selectors.get(each.id) !== undefined)
                .map(each => [`${data.module}/${selectors.get(each.id)}`, each]));
            for (const report of embedded) {
                const line = finished.get(report.id);
                assert.ok(line, `The command line reports ${report.id}: ${printed}`);
                assert.strictEqual(line.status, report.state, `${report.id}, on the command line and embedded`);
                if (report.state === 'failed') assert.strictEqual(line.message, report.message);
            }
            assert.strictEqual(tests.result(data.failed)?.message, data.failure);
        } finally {
            tests.dispose();
            await fs.rm(output, { recursive: true, force: true });
        }
    });
}

/**
 * X355, registered after X354 in catalog order: tests that don't show, of a module that has an error and of one whose
 * dependency has one, which says its dependencies are unavailable, until the errors are fixed.
 */
export function hiddenTestCases(): void {
    playbook('X355', async (workspace, data) => {
        for (const each of data.modules) await workspace.write(each.file, each.source);
        await workspace.configure(data.modules.map(each => ({ name: each.module, uri: workspace.uri(each.file).toString(),
            dependencies: 'dependencies' in each ? each.dependencies : undefined })));
        const source = (file: string) => data.modules.find(each => each.file === file)!.source;
        const broken = await workspace.open(data.broken.file);
        const blocked = await workspace.open(data.blocked.file);
        await diagnostics(broken.uri, values => values.some(item => item.severity === vscode.DiagnosticSeverity.Error), 'The test module\'s error');
        await diagnostics(blocked.uri, values => values.some(item => diagnosticCode(item) === data.blocked.code && item.message.includes(data.blocked.unavailable)),
            'The test module\'s dependencies are unavailable');
        await watchTests(async tests => {
            // the listing that has the tests of the module that compiles is of the modules as they are now, whose
            // other tests no compile has found
            await tests.listing([data.listed]);
            for (const item of [data.broken.item, data.blocked.item]) assert.ok(!tests.listed.includes(item), `${item} is listed: ${JSON.stringify(tests.listed)}`);

            await workspace.replace(broken, source(data.broken.file).replace(data.broken.replaceFrom, data.broken.replaceWith));
            const dependency = await workspace.open(data.dependency.file);
            await workspace.replace(dependency, source(data.dependency.file).replace(data.dependency.replaceFrom, data.dependency.replaceWith));
            await noErrors(broken.uri);
            await noErrors(blocked.uri);
            await tests.listing([data.broken.item, data.blocked.item], 'The Test Explorer lists the tests once their errors are fixed');
        });
    });
}
