import * as assert from 'node:assert';
import * as fs from 'node:fs/promises';
import * as path from 'node:path';
import Mocha from 'mocha';
import * as vscode from 'vscode';
import { sharedScenarioHash, sharedScenarioIds, sharedScenarioPath } from './shared';
import { cases } from './support';

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
    const mocha = new Mocha({ ui: 'tdd', color: true, timeout: 90_000 });
    mocha.addFile(path.join(__dirname, 'playbook.test.js'));
    const results: { id: string; title: string; status: string; durationMs?: number; error?: string }[] = [];
    const failures = await new Promise<number>(resolve => {
        const runner = mocha.run(resolve);
        runner.on('pass', test => results.push({ id: test.title.split(':')[0], title: test.title, status: 'passed', durationMs: test.duration }));
        runner.on('fail', (test, error) => {
            results.push({ id: test.title.split(':')[0], title: test.title, status: 'failed', error: error.stack });
            console.error(`${test.title}\n${error.stack}`);
        });
        runner.on('pending', test => results.push({ id: test.title.split(':')[0], title: test.title, status: 'not-run' }));
    });
    const report = {
        commit: process.env.XTC_PLAYBOOK_COMMIT, dirtyPaths: process.env.XTC_PLAYBOOK_DIRTY,
        vscode: vscode.version, finished: new Date().toISOString(), failures,
        sharedScenarios: { file: sharedScenarioPath, sha256: sharedScenarioHash, ids: sharedScenarioIds },
        cases: [...cases].map(([id, description]) => ({ ...description, id,
            ...(results.find(result => result.id === id) ?? { status: 'not-run' }) })),
        errors: results.filter(result => !cases.has(result.id)),
        scope: 'VS Code extension-host/provider checks. Visual appearance and physical key/menu interaction remain manual.',
        hostChecks: await hostResults(),
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
        ...report.cases.map(item => `${item.id}: ${item.status} — ${item.title}${'error' in item && item.error ? `\n  ${item.error}` : ''}${item.manual.length ? `\n  Manual: ${item.manual.join('; ')}` : ''}`),
        ...report.hostChecks.map(item => `Host ${item.task}: ${item.status} (${item.suites.reduce((sum, suite) => sum + suite.tests, 0)} XDK tests; XML timestamps in results.json)`),
        ...Object.entries(report.supportingCoverage).map(([id, coverage]) => `${id}: ${coverage}`),
        ...report.errors.map(error => error.error ?? error.title), report.scope
    ].join('\n') + '\n');
    assert.strictEqual(failures, 0, `Compiler playbook failed; see ${directory}/results.json`);
    assert.strictEqual(report.cases.filter(item => item.status !== 'passed').length, 0, 'No silently skipped playbook cases');
}
