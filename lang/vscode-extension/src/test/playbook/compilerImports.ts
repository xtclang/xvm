import * as assert from 'node:assert';
import * as fs from 'node:fs/promises';
import * as path from 'node:path';
import * as vscode from 'vscode';
import { modelPath, workspaceModelPath } from '../../build-model';
import { compilerBuildModels, refreshCompilerBuild } from '../../compiler-paths';
import { updateCompilerConfiguration } from '../../lsp-client';
import { WorkbenchUi } from '../workbenchUi';
import { catalog, sharedScenarioPath } from './shared';
import { client, diagnostics, eventually, noErrors, playbook } from './support';

/** Same real Gradle producer and gated outcomes as the IntelliJ driver; no mocked task/progress. */
export function compilerImportCases(): void {
    for (const id of ['X260', 'X261', 'X262', 'X263', 'X264'] as const) {
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
            const aggregate = path.join(root, workspaceModelPath);
            const previousAggregate = await fs.readFile(aggregate).catch(() => undefined);
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
                await fs.rm(aggregate, { force: true });
                await fs.mkdir(control, { recursive: true });
                await fs.writeFile(path.join(control, 'invocations.txt'), '');
                for (const file of files) {
                    await fs.mkdir(path.dirname(path.join(root, file)), { recursive: true });
                    const source = file.includes('gradlew') || file.startsWith('gradle/') ? path.join(repository, file) : path.join(shared, 'compiler-import', file);
                    await fs.copyFile(source, path.join(root, file));
                }
                if (process.platform !== 'win32') await fs.chmod(path.join(root, 'gradlew'), 0o755);
                if ('included' in data) {
                    for (const build of data.included) {
                        const target = path.join(root, build.folder);
                        await fs.mkdir(path.join(target, '.compiler-import-playbook'), { recursive: true });
                        await fs.copyFile(path.join(shared, 'compiler-import/build.gradle.kts'), path.join(target, 'build.gradle.kts'));
                        await fs.writeFile(path.join(target, 'settings.gradle.kts'), `rootProject.name = "${build.module}"\n` +
                            (build.folder === 'included' ? 'includeBuild("nested")\n' : ''));
                        const nested = JSON.parse(JSON.stringify(fixture.model).split('${workspace}').join(vscode.Uri.file(target).toString())
                            .split(fixture.module).join(build.module));
                        await fs.writeFile(path.join(target, `${build.module}.x`), fixture.source.replace(fixture.module, build.module));
                        await fs.mkdir(path.join(target, 'processed'), { recursive: true });
                        await fs.writeFile(path.join(target, fixture.resource), fixture.contents);
                        const gate = path.join(target, '.compiler-import-playbook');
                        await fs.writeFile(path.join(gate, 'model.json'), JSON.stringify(nested));
                        await fs.writeFile(path.join(gate, 'request.properties'), 'operation=included\noutput=valid\noutcome=success\n');
                        await fs.writeFile(path.join(gate, 'included.release'), 'release\n');
                    }
                    await fs.appendFile(path.join(root, 'settings.gradle.kts'), '\nincludeBuild("included")\n');
                }
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
                    const pending = 'automatic' in step ? (async () => {
                        const task = new vscode.Task({ type: 'gradle', operation: `${id}-sync` }, vscode.workspace.workspaceFolders![0],
                            'Synchronize compiler fixture', 'Gradle', new vscode.ProcessExecution(path.join(root, 'gradlew'), ['help', '--console=plain'], { cwd: root }));
                        await vscode.tasks.executeTask(task);
                        await eventually(async () => fs.stat(aggregate).then(() => true, () => false), Boolean, 'Automatic import publishes its aggregate');
                    })() : vscode.commands.executeCommand(command);
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
                        if ('included' in data) {
                            assert.strictEqual(compilerBuildModels()[0].sourceSets.length, data.included.length + 1);
                            assert.strictEqual(compilerBuildModels()[0].buildRoots?.length, data.included.length + 1);
                        }
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
                if (id === 'X264') {
                    await fs.writeFile(aggregate, '{');
                    assert.doesNotThrow(() => compilerBuildModels());
                    await fs.writeFile(aggregate, JSON.stringify(initial));
                    // No explicit configuration publication: the shipping filesystem watcher owns it.
                    await noErrors(document.uri);
                    assert.strictEqual(settings.inspect('sourceModules')?.workspaceValue, null);
                }
            } finally {
                executions.forEach(execution => execution.terminate());
                for (const step of data.steps) await fs.writeFile(path.join(control, `${id}-${step.operation}.release`), 'cleanup\n');
                tasks.dispose(); finished.dispose();
                await vscode.commands.executeCommand('notifications.hideList');
                await ui.close();
                for (const file of files) await fs.rm(path.join(root, file), { force: true });
                if (previousReport) await fs.writeFile(report, previousReport); else await fs.rm(report, { force: true });
                if (previousAggregate) await fs.writeFile(aggregate, previousAggregate); else await fs.rm(aggregate, { force: true });
                if ('included' in data) await fs.rm(path.join(root, 'included'), { recursive: true, force: true });
                await settings.update('sourceModules', previousGraph, vscode.ConfigurationTarget.Workspace);
                await updateCompilerConfiguration();
            }
        });
    }
    playbook('X265', async (workspace, data) => {
        const primary = vscode.workspace.workspaceFolders![0];
        const root = path.join(path.dirname(primary.uri.fsPath), 'retired-import');
        const fixture = catalog.common.compilerImport;
        const shared = path.dirname(sharedScenarioPath);
        const repository = path.resolve(shared, '../../..');
        const control = path.join(root, '.compiler-import-playbook');
        await fs.mkdir(control, { recursive: true });
        for (const file of ['build.gradle.kts', 'settings.gradle.kts', 'gradle.properties', 'gradlew', 'gradlew.bat',
            'gradle/wrapper/gradle-wrapper.jar', 'gradle/wrapper/gradle-wrapper.properties']) {
            await fs.mkdir(path.dirname(path.join(root, file)), { recursive: true });
            await fs.copyFile(file.includes('gradlew') || file.startsWith('gradle/') ? path.join(repository, file) :
                path.join(shared, 'compiler-import', file), path.join(root, file));
        }
        if (process.platform !== 'win32') await fs.chmod(path.join(root, 'gradlew'), 0o755);
        const model = JSON.parse(JSON.stringify(fixture.model).split('${workspace}').join(vscode.Uri.file(root).toString()));
        await fs.writeFile(path.join(root, fixture.file), fixture.source);
        await fs.mkdir(path.join(root, 'processed'), { recursive: true });
        await fs.writeFile(path.join(root, fixture.resource), fixture.contents);
        await fs.mkdir(path.dirname(path.join(root, modelPath)), { recursive: true });
        await fs.writeFile(path.join(root, modelPath), JSON.stringify(model));
        await fs.writeFile(path.join(control, 'model.json'), JSON.stringify(model));
        await fs.writeFile(path.join(control, 'request.properties'), `operation=${data.operation}\noutput=valid\noutcome=success\n`);
        const original = await client().sendRequest<{ pid: number }>('xtc/languageServiceStatus');
        const uri = vscode.Uri.file(root);
        const added = new Promise<void>(resolve => {
            const listener = vscode.workspace.onDidChangeWorkspaceFolders(event => {
                if (event.added.some(folder => folder.uri.toString() === uri.toString())) { listener.dispose(); resolve(); }
            });
        });
        assert.ok(vscode.workspace.updateWorkspaceFolders(vscode.workspace.workspaceFolders!.length, 0, { uri }));
        await added;
        const secondary = vscode.workspace.workspaceFolders!.find(folder => folder.uri.toString() === uri.toString())!;
        const pending = refreshCompilerBuild(false, secondary);
        try {
            await eventually(async () => fs.stat(path.join(control, `${data.operation}.started`)).then(() => true, () => false), Boolean, 'Secondary import is pending');
            assert.ok(vscode.workspace.updateWorkspaceFolders(secondary.index, 1));
            await eventually(async () => !vscode.workspace.workspaceFolders?.some(folder => folder.uri.toString() === uri.toString()), Boolean, 'Folder removed through native workspace API');
            await pending;
            await fs.writeFile(path.join(control, `${data.operation}.release`), 'late completion\n');
            const producer = Number(await fs.readFile(path.join(control, `${data.operation}.pid`), 'utf8'));
            await eventually(async () => {
                if (await fs.stat(path.join(control, `${data.operation}.finished`)).then(() => true, () => false)) return true;
                try { process.kill(producer, 0); return false; }
                catch (error) { return (error as NodeJS.ErrnoException).code === 'ESRCH'; }
            }, Boolean, 'Removed folder producer retired');
            assert.ok(compilerBuildModels().flatMap(item => item.sourceSets).every(entry => entry.projectDirectory !== model.sourceSets[0].projectDirectory));
            assert.strictEqual((await client().sendRequest<{ pid: number }>('xtc/languageServiceStatus')).pid, original.pid);
            await workspace.write('Survivor.x', 'module Survivor {}\n');
            const survivor = await workspace.open('Survivor.x');
            await noErrors(survivor.uri);
        } finally {
            await fs.writeFile(path.join(control, `${data.operation}.release`), 'cleanup\n');
            const remaining = vscode.workspace.workspaceFolders?.find(folder => folder.uri.toString() === uri.toString());
            if (remaining) vscode.workspace.updateWorkspaceFolders(remaining.index, 1);
            await pending.catch(() => {});
        }
    });
}
