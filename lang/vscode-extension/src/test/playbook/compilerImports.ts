import * as assert from 'node:assert';
import * as fs from 'node:fs/promises';
import * as path from 'node:path';
import * as vscode from 'vscode';
import { modelPath } from '../../build-model';
import { compilerBuildModels } from '../../compiler-paths';
import { updateCompilerConfiguration } from '../../lsp-client';
import { WorkbenchUi } from '../workbenchUi';
import { catalog, sharedScenarioPath } from './shared';
import { client, diagnostics, eventually, noErrors, playbook } from './support';

/** Same real Gradle producer and gated outcomes as the IntelliJ driver; no mocked task/progress. */
export function compilerImportCases(): void {
    for (const id of ['X260', 'X261', 'X262'] as const) {
        playbook(id, async (workspace, data) => {
            const root = vscode.workspace.workspaceFolders![0].uri.fsPath;
            assert.strictEqual(root, path.join(process.env.XTC_PLAYBOOK_REPORT_DIR!, 'workspace'));
            const fixture = catalog.common.compilerImport;
            const shared = path.dirname(sharedScenarioPath);
            const repository = path.resolve(shared, '../../..');
            const files = ['build.gradle.kts', 'settings.gradle.kts', 'gradle.properties', 'gradlew', 'gradlew.bat',
                'gradle/wrapper/gradle-wrapper.jar', 'gradle/wrapper/gradle-wrapper.properties'];
            for (const file of files) assert.ok(!await fs.stat(path.join(root, file)).then(() => true, () => false), `Isolated fixture must not replace ${file}`);
            const control = path.join(root, '.compiler-import-playbook');
            const report = path.join(root, modelPath);
            const previousReport = await fs.readFile(report).catch(() => undefined);
            const settings = vscode.workspace.getConfiguration('xtc.compiler');
            const previousGraph = settings.inspect('sourceModules')?.workspaceValue;
            const ui = await WorkbenchUi.connect();
            const executions = new Set<vscode.TaskExecution>();
            const tasks = vscode.tasks.onDidStartTask(event => {
                if (event.execution.task.definition.type === 'xtc-model') executions.add(event.execution);
            });
            const finished = vscode.tasks.onDidEndTask(event => executions.delete(event.execution));
            const initial = JSON.parse(JSON.stringify(fixture.model).split('${workspace}').join(vscode.Uri.file(workspace.directory).toString()));
            const replacement = structuredClone(initial);
            replacement.sourceSets[0].resourceRoots = [];
            const accepted = () => compilerBuildModels().map(model => JSON.stringify(model));
            try {
                await fs.mkdir(control, { recursive: true });
                await fs.writeFile(path.join(control, 'invocations.txt'), '');
                for (const file of files) {
                    await fs.mkdir(path.dirname(path.join(root, file)), { recursive: true });
                    const source = file.includes('gradlew') || file.startsWith('gradle/') ? path.join(repository, file) : path.join(shared, 'compiler-import', file);
                    await fs.copyFile(source, path.join(root, file));
                }
                if (process.platform !== 'win32') await fs.chmod(path.join(root, 'gradlew'), 0o755);
                await workspace.write(fixture.file, fixture.source);
                await workspace.write(fixture.resource, fixture.contents);
                await fs.mkdir(path.dirname(report), { recursive: true });
                await fs.writeFile(report, JSON.stringify(initial));
                await settings.update('sourceModules', null, vscode.ConfigurationTarget.Workspace);
                await updateCompilerConfiguration();
                const document = await workspace.open(fixture.file);
                await noErrors(document.uri);
                const baseline = accepted();
                assert.strictEqual(baseline.length, 1);
                for (const step of data.steps) {
                    const operation = `${id}-${step.operation}`;
                    console.log(`[${operation}] Starting native Gradle import`);
                    await fs.writeFile(path.join(control, 'model.json'), JSON.stringify(replacement));
                    await fs.writeFile(path.join(control, 'request.properties'),
                        `operation=${operation}\noutput=${step.output}\noutcome=${step.outcome}\n`);
                    await vscode.commands.executeCommand('notifications.clearAll');
                    const command = step.prepare ? 'xtc.prepareCompilerInputs' : 'xtc.refreshCompilerBuild';
                    const pending = vscode.commands.executeCommand(command);
                    await eventually(async () => fs.readFile(path.join(control, `${operation}.started`), 'utf8').catch(() => ''),
                        value => value === (step.prepare ? 'prepareXtcLspModel' : 'exportXtcLspModel'), 'Real Gradle task published its output and is waiting');
                    // Force a consumer read while the task is gated, even if the watcher has not fired.
                    await updateCompilerConfiguration();
                    assert.deepStrictEqual(accepted(), baseline, 'Pending output cannot replace the accepted report');
                    await noErrors(document.uri);
                    if (step.duplicate) {
                        await vscode.commands.executeCommand('xtc.refreshCompilerBuild');
                        await ui.page.locator('.notification-list-item').filter({ hasText: 'already running' }).waitFor({ state: 'visible' });
                        assert.strictEqual(executions.size, 1, 'Duplicate request must not create another native task');
                    }
                    if (step.cancel) {
                        await vscode.commands.executeCommand('notifications.showList');
                        const notification = ui.page.locator('.notification-list-item').filter({ hasText: 'preparing generated inputs' });
                        const button = notification.getByRole('button', { name: 'Cancel', exact: true });
                        await button.waitFor({ state: 'visible', timeout: 10_000 });
                        await ui.screenshot(`${id}-before-cancel`);
                        await button.click({ timeout: 5_000 });
                    } else await fs.writeFile(path.join(control, `${operation}.release`), 'release\n');
                    await pending;
                    console.log(`[${operation}] Native import returned; checking accepted inputs`);
                    await eventually(async () => executions.size, count => count === 0, 'Owned native task has ended');
                    // Retire a Gradle daemon action even if the client process was terminated first.
                    await fs.writeFile(path.join(control, `${operation}.release`), 'cleanup\n');
                    const producer = Number(await fs.readFile(path.join(control, `${operation}.pid`), 'utf8'));
                    assert.ok(Number.isSafeInteger(producer) && producer > 0);
                    await eventually(async () => {
                        if (await fs.stat(path.join(control, `${operation}.finished`)).then(() => true, () => false)) return true;
                        // Native cancellation may kill the producer JVM, so its finally cannot run.
                        try { process.kill(producer, 0); return false; }
                        catch (error) { return step.cancel && (error as NodeJS.ErrnoException).code === 'ESRCH'; }
                    }, Boolean, 'Gradle fixture action retired or its cancelled JVM exited');
                    await updateCompilerConfiguration();
                    await client().sendRequest('xtc/healthCheck');
                    if (step.accepted) {
                        assert.notDeepStrictEqual(accepted(), baseline);
                        await diagnostics(document.uri, values => values.length > 0, 'Successful retry accepts the model without resource roots');
                    } else {
                        assert.deepStrictEqual(accepted(), baseline, 'Rejected output remains rejected after later reads');
                        await noErrors(document.uri);
                    }
                    await vscode.commands.executeCommand('xtc.showCompilerPaths');
                    await eventually(async () => vscode.workspace.textDocuments.filter(item => item.uri.scheme === 'output').map(item => item.getText()).join('\n'),
                        text => text.includes('Last import:') && text.toLowerCase().includes(step.expected), 'Paths view records the completed import outcome');
                    assert.strictEqual((await fs.readFile(path.join(control, 'invocations.txt'), 'utf8')).split('\n').filter(line => line.startsWith(`${operation}:`)).length, 1);
                    assert.strictEqual(document.getText(), fixture.source);
                }
            } finally {
                executions.forEach(execution => execution.terminate());
                for (const step of data.steps) await fs.writeFile(path.join(control, `${id}-${step.operation}.release`), 'cleanup\n');
                tasks.dispose(); finished.dispose();
                await vscode.commands.executeCommand('notifications.hideList');
                await ui.close();
                for (const file of files) await fs.rm(path.join(root, file), { force: true });
                if (previousReport) await fs.writeFile(report, previousReport); else await fs.rm(report, { force: true });
                await settings.update('sourceModules', previousGraph, vscode.ConfigurationTarget.Workspace);
                await updateCompilerConfiguration();
            }
        });
    }
}
