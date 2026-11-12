import * as assert from 'node:assert';
import * as vscode from 'vscode';
import { NOT_REACHED, onDidReportTest, runModules, type Selection, type TestReport } from '../../test-explorer';

suite('Test Explorer stop', () => {
    const controller = vscode.tests.createTestController('xtc-stop-test', 'Stop');
    suiteTeardown(() => controller.dispose());

    test('a stopped run ends the chosen tests of the modules it did not reach as not run', async () => {
        const selections = new WeakMap<vscode.TestItem, Selection>();
        const item = (module: string, selector: string, kind: Selection['kind'], ...children: vscode.TestItem[]) => {
            const it = controller.createTestItem(`${module}/${selector}`, selector || module);
            children.forEach(child => it.children.add(child));
            selections.set(it, { module, uri: `file:///${module}.x`, selector, kind });
            return it;
        };
        // two modules, Adding with adds, and Taking with takes and gives
        const adding = item('Adding', '', 'module', item('Adding', 'adds', 'test'));
        const taking = item('Taking', '', 'module', item('Taking', 'takes', 'test'), item('Taking', 'gives', 'test'));
        controller.items.replace([adding, taking]);

        const reports: TestReport[] = [];
        const listening = onDidReportTest(report => reports.push(report));
        const stop = new vscode.CancellationTokenSource();
        const testRun = controller.createTestRun(new vscode.TestRunRequest());
        const ran: string[] = [];
        try {
            // the run is stopped while the first module runs: the second is not reached
            await runModules(testRun, [adding, taking], selections, stop.token, async items => {
                ran.push(...items.map(it => it.id));
                stop.cancel();
            });
        } finally {
            testRun.end();
            listening.dispose();
            stop.dispose();
        }
        assert.deepStrictEqual(ran, ['Adding/']);
        const taken = reports.filter(report => report.id.startsWith('Taking/'));
        assert.deepStrictEqual(taken.map(report => report.id).sort(), ['Taking/gives', 'Taking/takes']);
        assert.ok(taken.every(report => report.state === 'errored' && report.message === NOT_REACHED), JSON.stringify(taken));
        // the module it reached ends its test too, which started and reported nothing, as not run
        assert.deepStrictEqual(reports.filter(report => report.id.startsWith('Adding/')).map(report => [report.id, report.state]),
            [['Adding/adds', 'errored']]);
    });
});
