import * as path from 'node:path';
import { randomUUID } from 'node:crypto';
import * as vscode from 'vscode';
import type { LanguageClient } from 'vscode-languageclient/node';

import { gradleTestArgs, gradleWrapper, runTestProcess } from './debug-adapter';
import { buildMode, describeBuild, type RunResponse, trackRun } from './embedded-execution';
import { failureLine, LineSplitter, parseServiceMessage, selectorOf, type ServiceMessage } from './test-messages';

/**
 * The Test Explorer's Ecstasy tests: the language server finds each module's xunit tests (`xtc/tests`) in its latest
 * analysis, and runs or debugs those a run selects with xunit, embedded (`xtc/run` and the `xtc` debug type with
 * `tests`) or, in a folder whose build mode is Gradle, with its `testXtc`. The run's output reports each test as a
 * TeamCity service message, which marks it passed, failed or skipped.
 */

/** A test or a container of tests of a module, as `xtc/tests` gives it. */
interface ServerTest {
    selector: string;
    name: string;
    kind: 'module' | 'package' | 'class' | 'test';
    location?: { uri: string; startLine: number; startColumn: number; endLine: number; endColumn: number };
    children: ServerTest[];
}

/** A source module's tests, under the module. */
interface ModuleTests {
    module: string;
    uri: string;
    tests: ServerTest;
}

/** What a test item runs: in its module, by its root URI, the xunit selector and its kind. */
interface Selection {
    module: string;
    uri: string;
    selector: string;
    kind: ServerTest['kind'];
}

/** How long after the server's last new diagnostics, which follow an analysis, the tests are asked for again. */
const REFRESH_DELAY_MS = 500;

/** The message of a test that started and never ended, as when the run was stopped or its process died. */
const NOT_FINISHED = 'The test run ended before this test did.';

/** The message of a chosen test that never started, as when its module did not build. */
const NOT_RUN = 'This test did not run; the test output says why.';

/** A test's result in a run, as the Test Explorer shows it, by its item's ID; for the playbook. */
export interface TestReport {
    id: string;
    state: 'passed' | 'failed' | 'skipped' | 'errored';
    message?: string;
    /** A failure's line in the test's own file, one-based, when its stack names one. */
    line?: number;
}

const reports = new vscode.EventEmitter<TestReport>();

/** Fires when a run marks a test passed, failed, skipped or errored. */
export const onDidReportTest = reports.event;

const refreshes = new vscode.EventEmitter<readonly string[]>();

/** Fires with the ID of every item, the modules' and the ones under them, when the tests have been listed again. */
export const onDidRefreshTests = refreshes.event;

/** Registers the Ecstasy test controller, with its Run and Debug profiles, for the language server [client]. */
export function registerTestExplorer(context: vscode.ExtensionContext, client: () => LanguageClient | undefined): void {
    const controller = vscode.tests.createTestController('xtc', 'Ecstasy');
    const selections = new WeakMap<vscode.TestItem, Selection>();
    // the results of the test runs that debug sessions run, by the marker their launch carries
    const debugged = new Map<string, Results>();

    const refresh = async (): Promise<void> => {
        const connection = client();
        if (!connection?.isRunning()) {
            return;
        }
        const modules = await connection.sendRequest<ModuleTests[]>('xtc/tests');
        place(controller.items, modules.map(module => [module, module.tests] as const));
        refreshes.fire(topLevel(controller.items).flatMap(under).map(it => it.id));
    };
    // the items already there stay, by their IDs, as a run may be reporting on them; one whose file changed is new
    const place = (collection: vscode.TestItemCollection, tests: readonly (readonly [ModuleTests, ServerTest])[]): void => {
        collection.replace(tests.map(([module, test]) => {
            const id = `${module.module}/${test.selector}`;
            const uri = vscode.Uri.parse(test.location?.uri ?? module.uri);
            const kept = collection.get(id);
            const it = kept?.uri?.toString() === uri.toString() ? kept : controller.createTestItem(id, test.name, uri);
            const at = test.location;
            it.label = test.kind === 'module' ? module.module : test.name;
            it.range = at && new vscode.Range(at.startLine, at.startColumn, at.endLine, at.endColumn);
            place(it.children, test.children.map(child => [module, child] as const));
            selections.set(it, { module: module.module, uri: module.uri, selector: test.selector, kind: test.kind });
            return it;
        }));
    };

    const run = async (request: vscode.TestRunRequest, token: vscode.CancellationToken, debug: boolean): Promise<void> => {
        const testRun = controller.createTestRun(request);
        try {
            const chosen = selected(request.include ?? topLevel(controller.items), new Set(request.exclude ?? []));
            for (const items of byModule(chosen, selections).values()) {
                if (token.isCancellationRequested) {
                    break;
                }
                const results = new Results(testRun, items, selections);
                const tests = items.some(it => selections.get(it)?.kind === 'module')
                    ? []
                    : items.map(it => selections.get(it)!).map(({ selector, kind }) => ({ selector, kind }));
                const selection = selections.get(items[0])!;
                await (debug
                    ? debugTests(selection, tests, results, testRun, token, debugged)
                    : buildMode(vscode.workspace.getWorkspaceFolder(vscode.Uri.parse(selection.uri))) === 'gradle'
                        ? runGradleTests(selection, tests, results, token)
                        : runTests(client(), selection, tests, results, token));
                results.end();
            }
        } finally {
            testRun.end();
        }
    };

    controller.refreshHandler = refresh;
    controller.resolveHandler = async parent => {
        if (!parent) {
            await refresh();
        }
    };
    controller.createRunProfile('Run', vscode.TestRunProfileKind.Run, (request, token) => run(request, token, false), true);
    controller.createRunProfile('Debug', vscode.TestRunProfileKind.Debug, (request, token) => run(request, token, true), true);

    let pending: NodeJS.Timeout | undefined;
    context.subscriptions.push(
        controller,
        vscode.languages.onDidChangeDiagnostics(event => {
            if (event.uris.some(uri => uri.path.endsWith('.x'))) {
                clearTimeout(pending);
                pending = setTimeout(() => void refresh().catch(() => undefined), REFRESH_DELAY_MS);
            }
        }),
        { dispose: () => clearTimeout(pending) },
        vscode.debug.registerDebugAdapterTrackerFactory('xtc', {
            createDebugAdapterTracker(session) {
                const results = debugged.get(session.configuration.xtcTestRun);
                return results && {
                    onDidSendMessage(message: { type?: string; event?: string; body?: { output?: string } }) {
                        if (message.type === 'event' && message.event === 'output' && message.body?.output) {
                            results.output(message.body.output);
                        }
                    },
                };
            },
        }),
    );
}

/**
 * The fewest items that select exactly the tests under [chosen] that are not under [excluded]: an item that is
 * under another chosen one goes, so a test chosen with its class runs once, and an item with an excluded test under
 * it stands for its other children instead, one by one, since xunit's selectors exclude nothing.
 */
export function selected(chosen: readonly vscode.TestItem[], excluded: ReadonlySet<vscode.TestItem>): vscode.TestItem[] {
    const above = (it: vscode.TestItem): vscode.TestItem[] => it.parent ? [it.parent, ...above(it.parent)] : [];
    const cover = (it: vscode.TestItem): vscode.TestItem[] => {
        if (excluded.has(it)) {
            return [];
        }
        return under(it).some(below => excluded.has(below)) ? topLevel(it.children).flatMap(cover) : [it];
    };
    return chosen
        .filter((it, index) => chosen.indexOf(it) === index && !above(it).some(parent => chosen.includes(parent)))
        .flatMap(cover);
}

/** The items of a collection, without their children. */
function topLevel(items: vscode.TestItemCollection): vscode.TestItem[] {
    return [...items].map(([, it]) => it);
}

/** The items by their module, in the order of each module's first. */
function byModule(items: readonly vscode.TestItem[], selections: WeakMap<vscode.TestItem, Selection>): Map<string, vscode.TestItem[]> {
    return items.reduce((modules, it) => {
        const module = selections.get(it)?.module;
        return module === undefined ? modules : modules.set(module, [...(modules.get(module) ?? []), it]);
    }, new Map<string, vscode.TestItem[]>());
}

/** Runs the module's [tests] with `xtc/run`, until the server reports the run's exit or the build fails. */
function runTests(connection: LanguageClient | undefined, selection: Selection, tests: { selector: string; kind: string }[],
                  results: Results, token: vscode.CancellationToken): Promise<void> {
    if (!connection?.isRunning()) {
        results.failAll('The Ecstasy language server is not running.');
        return Promise.resolve();
    }
    const runId = randomUUID();
    return new Promise(resolve => {
        const routed = trackRun(runId, {
            output: text => results.output(text),
            exited: (exitCode, error) => {
                if (error) results.note(error);
                results.note(`Tests finished with exit code ${exitCode ?? 1}`);
                done();
            },
        });
        const stop = token.onCancellationRequested(() => {
            void connection.sendRequest<boolean>('xtc/stopRun', { runId }).then(undefined, () => undefined);
        });
        const done = (): void => {
            routed.dispose();
            stop.dispose();
            resolve();
        };
        connection.sendRequest<RunResponse>('xtc/run', { module: selection.uri, moduleName: selection.module, runId, tests })
            .then(response => {
                if (!response.runId) {
                    results.failAll(`The build failed; no test ran.\n${describeBuild(response.build)}`);
                    done();
                }
            }, error => {
                results.failAll(`The tests could not run: ${error instanceof Error ? error.message : String(error)}`);
                done();
            });
    });
}

/** Runs the module's [tests] with its folder's Gradle build, `testXtc`, whose output reports them, until it ends. */
async function runGradleTests(selection: Selection, tests: { selector: string; kind: string }[], results: Results,
                              token: vscode.CancellationToken): Promise<void> {
    const folder = vscode.workspace.getWorkspaceFolder(vscode.Uri.parse(selection.uri));
    if (!folder) {
        results.failAll(`No workspace folder has ${selection.uri}.`);
        return;
    }
    const args = gradleTestArgs(selection.module, tests);
    results.note(`${gradleWrapper()} ${args.join(' ')}`);
    const exitCode = await runTestProcess(gradleWrapper(), args, folder.uri.fsPath, line => results.output(`${line}\n`), token);
    results.note(`Tests finished with exit code ${exitCode ?? 1}`);
}

/** Debugs the module's [tests] in an `xtc` debug session of the test run, until the session ends. */
async function debugTests(selection: Selection, tests: { selector: string; kind: string }[], results: Results,
                          testRun: vscode.TestRun, token: vscode.CancellationToken, debugged: Map<string, Results>): Promise<void> {
    const marker = randomUUID();
    const folder = vscode.workspace.getWorkspaceFolder(vscode.Uri.parse(selection.uri));
    const ours = (session: vscode.DebugSession): boolean => session.configuration.xtcTestRun === marker;
    debugged.set(marker, results);
    const session = new Promise<vscode.DebugSession>(resolve => {
        const listener = vscode.debug.onDidStartDebugSession(started => {
            if (ours(started)) {
                listener.dispose();
                resolve(started);
            }
        });
    });
    const ended = new Promise<void>(resolve => {
        const listener = vscode.debug.onDidTerminateDebugSession(terminated => {
            if (ours(terminated)) {
                listener.dispose();
                resolve();
            }
        });
    });
    try {
        const started = await vscode.debug.startDebugging(folder, {
            type: 'xtc',
            request: 'launch',
            name: `Debug the tests of ${selection.module}`,
            module: selection.module,
            moduleUri: selection.uri,
            tests,
            xtcTestRun: marker,
        }, { testRun });
        if (!started) {
            results.failAll('The debug session did not start.');
            return;
        }
        const stop = token.onCancellationRequested(() => void session.then(it => vscode.debug.stopDebugging(it)));
        await ended;
        stop.dispose();
    } finally {
        debugged.delete(marker);
    }
}

/** What a test run's output says of the tests of one module: each item's state, from its service messages. */
class Results {
    private readonly lines = new LineSplitter();
    /** The items of the module, by their selectors. */
    private readonly items: ReadonlyMap<string, vscode.TestItem>;
    /** The tests the run chose. */
    private readonly tests: readonly vscode.TestItem[];
    private readonly running = new Set<vscode.TestItem>();
    private readonly failed = new Set<vscode.TestItem>();
    /** The tests with a result. */
    private readonly ended = new Set<vscode.TestItem>();

    constructor(private readonly run: vscode.TestRun, chosen: readonly vscode.TestItem[], selections: WeakMap<vscode.TestItem, Selection>) {
        this.items = new Map(under(moduleOf(chosen[0])).flatMap(it => {
            const selection = selections.get(it);
            return selection ? [[selection.selector, it] as const] : [];
        }));
        this.tests = chosen.flatMap(under).filter(it => selections.get(it)?.kind === 'test');
        this.tests.forEach(it => run.enqueued(it));
    }

    output(text: string): void {
        this.lines.feed(text).forEach(line => this.line(line));
    }

    /** A line of the run's own, not the program's. */
    note(text: string): void {
        this.run.appendOutput(`${text.replace(/\r?\n/g, '\r\n')}\r\n`);
    }

    /** Ends the module's tests: one that started and did not end is errored, and so is a chosen one that never started. */
    end(): void {
        this.lines.flush().forEach(line => this.line(line));
        this.running.forEach(it => this.errored(it, NOT_FINISHED));
        this.running.clear();
        this.tests.filter(it => !this.ended.has(it)).forEach(it => this.errored(it, NOT_RUN));
    }

    /** Errors the chosen tests with [message], as when their module did not build. */
    failAll(message: string): void {
        this.note(message);
        this.tests.forEach(it => this.errored(it, message));
    }

    private line(line: string): void {
        const message = parseServiceMessage(line);
        if (message) {
            this.report(message);
        } else {
            this.run.appendOutput(`${line}\r\n`);
        }
    }

    private report(message: ServiceMessage): void {
        const selector = selectorOf(message);
        const it = selector === undefined ? undefined : this.items.get(selector);
        if (!it) {
            return;
        }
        switch (message.name) {
            case 'testStarted':
                this.running.add(it);
                this.run.started(it);
                break;
            case 'testFailed': {
                this.failed.add(it);
                const shown = failure(it, message);
                this.run.failed(it, shown);
                this.ended.add(it);
                const line = it.uri && failureLine(message.attributes.details ?? '', path.basename(it.uri.fsPath));
                reports.fire({ id: it.id, state: 'failed', message: message.attributes.message, line: line || undefined });
                break;
            }
            case 'testIgnored':
                this.failed.add(it);
                this.run.skipped(it);
                this.ended.add(it);
                reports.fire({ id: it.id, state: 'skipped', message: message.attributes.message });
                break;
            case 'testFinished':
                this.running.delete(it);
                if (!this.failed.has(it)) {
                    this.run.passed(it, Number(message.attributes.duration) || undefined);
                    this.ended.add(it);
                    reports.fire({ id: it.id, state: 'passed' });
                }
                break;
        }
    }

    private errored(it: vscode.TestItem, message: string): void {
        this.run.errored(it, new vscode.TestMessage(message));
        this.ended.add(it);
        reports.fire({ id: it.id, state: 'errored', message });
    }
}

/** A failed test's message, placed at the line of its source that the failure's stack names, or at the test. */
function failure(it: vscode.TestItem, message: ServiceMessage): vscode.TestMessage {
    const details = message.attributes.details ?? '';
    const shown = new vscode.TestMessage(details ? `${message.attributes.message ?? ''}\n\n${details}` : message.attributes.message ?? '');
    const line = it.uri && failureLine(details, path.basename(it.uri.fsPath));
    if (it.uri && (line || it.range)) {
        shown.location = new vscode.Location(it.uri, line ? new vscode.Position(line - 1, 0) : it.range!.start);
    }
    return shown;
}

/** The module item that [it] is under, or [it] itself. */
function moduleOf(it: vscode.TestItem): vscode.TestItem {
    return it.parent ? moduleOf(it.parent) : it;
}

/** [it] and every item under it. */
function under(it: vscode.TestItem): vscode.TestItem[] {
    return [it, ...topLevel(it.children).flatMap(under)];
}
