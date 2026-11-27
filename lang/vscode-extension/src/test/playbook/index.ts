import * as assert from 'node:assert';
import * as fs from './disk';
import * as path from 'node:path';
import Mocha from 'mocha';
import * as vscode from 'vscode';
import { runHarness } from '../harness';
import { showTestProgress } from '../progress';
import { leftRunning } from '../wait';
import { selectedScenarioIds, sharedScenarioHash, sharedScenarioIds, sharedScenarioPath } from './shared';
import { cases, knownFailures, progress } from './support';

async function hostResults() {
    const root = path.resolve(__dirname, '../../../../lsp-server/build/test-results');
    return Promise.all(['test', 'compilerStdioTest'].map(async task => {
        const directory = path.join(root, task);
        const files = (await fs.readdir(directory).catch(() => [] as string[])).filter(file => /^TEST-.*Xdk.*\.xml$/.test(file));
        const suites = await Promise.all(files.map(async file => {
            const xml = await fs.readFile(path.join(directory, file), 'utf8');
            const attributes = Object.fromEntries([...xml.slice(0, xml.indexOf('>', xml.indexOf('<testsuite'))).matchAll(/(\w+)="([^"]*)"/g)].map(match => [match[1], match[2]]));
            return { file: path.join(directory, file), timestamp: attributes.timestamp,
                tests: Number(attributes.tests), failures: Number(attributes.failures), errors: Number(attributes.errors), skipped: Number(attributes.skipped) };
        }));
        return { task, status: !suites.length ? 'not-run' : suites.some(suite => suite.failures || suite.errors || suite.skipped) ? 'failed-or-skipped' : 'passed', suites };
    }));
}

export async function run(): Promise<void> {
    const directory = process.env.XTC_PLAYBOOK_REPORT_DIR;
    assert.ok(directory, 'Launch with npm run test:playbook or the Gradle playbook task');
    const selected = selectedScenarioIds(process.env.XTC_PLAYBOOK_CASES);
    // the harness's own tests first: no case runs on a broken harness, which the run's results say, as the retention reads
    await runHarness().catch(async (failure: unknown) => {
        await fs.writeFile(path.join(directory, 'results.json'), JSON.stringify({
            commit: process.env.XTC_COMMIT, vscode: vscode.version, finished: new Date().toISOString(), status: 'failed',
            error: `The harness's tests failed, so no case ran: ${failure instanceof Error ? failure.message : String(failure)}`,
        }, null, 2) + '\n');
        throw failure;
    });
    progress("the harness's tests passed");
    const mocha = new Mocha({ ui: 'tdd', color: true, timeout: 90_000 });
    const ids = selected.map(id => id.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('|');
    mocha.grep(new RegExp(`(?:^| )(?:${ids}):`));
    mocha.addFile(path.join(__dirname, 'playbook.test.js'));
    const results: { id: string; title: string; status: string; durationMs?: number; error?: string; knownHost?: string }[] = [];
    // at most every two minutes, a line of how many of the selected cases have run, are left, passed and failed
    let summarized = Date.now();
    const summary = (): void => {
        if (Date.now() - summarized < 120_000) return;
        summarized = Date.now();
        const run = results.filter(result => selected.some(value => value === result.id));
        const count = (status: string) => run.filter(result => result.status === status).length;
        progress(`${run.length}/${selected.length} cases run, ${selected.length - run.length} left: ${count('passed')} passed, ${count('failed')} failed`
            + `, ${count('known-host-failure')} known host failures`);
    };
    // the case left to itself, which still acts on the editor, after which no other case runs
    let stopped: { id: string; error: string } | undefined;
    const failures = await new Promise<number>(resolve => {
        // a case that fails, a timeout or a failed cleanup too, fails alone: each case ends and restores the editor
        // before the next starts, so that the run goes on with every case it selected; but one left to itself would
        // act on the next one, so the run stops after it
        const runner = mocha.run(resolve);
        showTestProgress(runner, 'Ecstasy playbook', test => test.title,
            test => knownFailures.has(test.title.split(':')[0]) ? `known host failure: ${knownFailures.get(test.title.split(':')[0])!.up}` : undefined);
        runner.on('test', test => progress(`${test.title.replace(':', '')}: starts`));
        runner.on('pass', test => {
            progress(`${test.title.replace(':', '')}: passed`);
            results.push({ id: test.title.split(':')[0], title: test.title, status: 'passed', durationMs: test.duration });
            summary();
        });
        runner.on('fail', (test, error) => {
            progress(`${test.title.replace(':', '')}: failed — ${(error.message ?? '').split('\n').find((line: string) => line.trim())?.trim().slice(0, 120) ?? error.name}`);
            results.push({ id: test.title.split(':')[0], title: test.title, status: 'failed', error: error.stack });
            summary();
            console.error(`${test.title}\n${error.stack}`);
            const left = leftRunning(error);
            if (left) {
                stopped = { id: test.title.split(':')[0], error: left.message };
                progress(`${stopped.id} is left to itself, still running: no other case runs — ${left.message.slice(0, 200)}`);
                runner.abort();
            }
        });
        runner.on('pending', test => {
            const id = test.title.split(':')[0];
            // a case that failed with a known host bug's symptom, which Mocha has as pending, leaves the run green, and
            // is no pass; its title says so
            const known = knownFailures.get(id);
            if (known) {
                const line = known.error instanceof Error ? known.error.message.split('\n')[0] : String(known.error);
                progress(`${test.title.replace(':', '')} — ${line.slice(0, 120)}`);
                results.push({ id, title: test.title, status: 'known-host-failure', knownHost: known.up, durationMs: test.duration,
                    error: known.error instanceof Error ? known.error.stack : String(known.error) });
            } else {
                progress(`${test.title.replace(':', '')}: not-run`);
                results.push({ id, title: test.title, status: 'not-run' });
            }
            summary();
        });
    });
    const report = {
        commit: process.env.XTC_COMMIT, dirtyPaths: process.env.XTC_PLAYBOOK_DIRTY,
        vscode: vscode.version, finished: new Date().toISOString(), failures,
        // the case left to itself, after which the selected cases that had not run did not
        ...(stopped && { stopped }),
        progressCancellation: process.env.XTC_PLAYBOOK_CANCEL_UI === 'true' ? 'visible-control' : 'native-token',
        sharedScenarios: { file: sharedScenarioPath, sha256: sharedScenarioHash, ids: sharedScenarioIds },
        selection: { mode: selected.length === sharedScenarioIds.length ? 'full' : 'focused', ids: selected },
        cases: [...cases].map(([id, description]) => ({ ...description, id,
            ...(results.find(result => result.id === id) ?? { status: selected.some(value => value === id) ? 'not-run' : 'not-selected' }) })),
        errors: results.filter(result => !cases.has(result.id)),
        // the cases that failed with a known host bug's symptom, apart from both the passed and the failed ones
        knownHostFailures: results.filter(result => result.status === 'known-host-failure')
            .map(result => ({ id: result.id, up: result.knownHost, error: result.error })),
        scope: 'VS Code extension-host/provider checks; X145 --cancel-ui and X260 activate visible Cancel controls, and X259 inspects rendered inlay labels. X154/X276/X277 capture the startup theme without changing it; separate X277 --theme/--lexical-baseline launches compare dark/light/high-contrast styles; X276 checks resolved annotations and library ownership, and X277 checks lexical styles and damaged-source recovery. X278 applies the compiler documentation action with native Undo/Redo; X279 clicks rendered reference lenses, opens native reference peek and checks live settings. Other visual appearance and physical key/menu interaction remain manual.',
        hostChecks: process.env.XTC_PLAYBOOK_HOST_CHECKS === 'true' ? await hostResults() : [],
        hostChecksNote: process.env.XTC_PLAYBOOK_HOST_CHECKS === 'true'
            ? 'Gradle XML of the server suites this run ran first; inspect timestamps.'
            : 'Host checks not run: -PplaybookHostChecks=true runs the server suites before a full run.',
        supportingCoverage: {
            '7a.1–7a.2': 'XdkStdioTest: packaged compiler with absent and invalid XDK_HOME',
            '7a.3–7a.4': 'Case timings and XdkRetentionTest latency output; interactive cold/warm log interpretation remains manual',
            '7a.5–7a.6': 'X29/X30/X51 and controlled XdkCursorServerTest cancellation; queue-depth/log presentation remains manual',
            '7a.7': 'XdkRetentionTest bounded release checks; a prolonged interactive memory/GC soak remains manual',
            '7a.10–7a.14': 'X3–X4, X6–X20 and X33–X42',
            'Dependency host API': 'XdkDependencyTest and XdkLanguageServerTest (Gradle test task)',
            'Automatic source recompilation host API': 'XdkProjectTest and XdkProjectServerTest (Gradle test task)',
            'Configured graph and binary contracts': 'XdkProjectQueryTest and X59–X63; includes bundled XDK member resolution and rename rejection',
            'Property/accessor implementations': 'XdkSemanticLookupTest, XdkDependencyTest, packaged stdio and X64–X67; field/default/mixin bodies, source ownership and unsupported controls',
            'Deterministic rename/cursor races': 'XdkProjectQueryLifecycleTest, XdkCursorServerTest and XdkRenameServerTest (Gradle test task)'
        }
    };
    await fs.writeFile(path.join(directory, 'results.json'), JSON.stringify(report, null, 2) + '\n');
    await fs.writeFile(path.join(directory, 'results.txt'), [
        `Compiler playbook at ${report.commit}; VS Code ${report.vscode}`,
        `Selection: ${report.selection.mode} (${selected.join(', ')})`,
        ...(stopped ? [`Stopped: ${stopped.id} was left to itself, still running, so no other case ran: ${stopped.error}`] : []),
        ...report.cases.map(item => `${item.id}: ${item.status}${'knownHost' in item && item.knownHost ? ` (${item.knownHost})` : ''} — ${item.title}${'error' in item && item.error ? `\n  ${item.error}` : ''}${item.manual.length ? `\n  Manual: ${item.manual.join('; ')}` : ''}`),
        `Known host failures: ${report.knownHostFailures.map(item => `${item.id} (${item.up})`).join(', ') || 'none'}`,
        ...report.hostChecks.map(item => `Host ${item.task}: ${item.status} (${item.suites.reduce((sum, suite) => sum + suite.tests, 0)} XDK tests; XML timestamps in results.json)`),
        report.hostChecksNote,
        ...Object.entries(report.supportingCoverage).map(([id, coverage]) => `${id}: ${coverage}`),
        ...report.errors.map(error => error.error ?? error.title), report.scope
    ].join('\n') + '\n');
    assert.strictEqual(failures, 0, `Compiler playbook failed; see ${directory}/results.json`);
    // a known host failure is no pass, and leaves the run green
    assert.strictEqual(report.cases.filter(item => selected.some(id => id === item.id) && item.status !== 'passed'
        && item.status !== 'known-host-failure').length, 0, 'No silently skipped selected playbook cases');
}
