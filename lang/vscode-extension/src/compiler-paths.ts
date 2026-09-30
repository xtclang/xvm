import * as path from 'node:path';
import { randomUUID } from 'node:crypto';
import * as vscode from 'vscode';
import { BuildModel, describeBuildModel, modelPath, readBuildModel } from './build-model';
import { getClient, updateCompilerConfiguration } from './lsp-client';
import { compilerSourceModules } from './rename-proposal';
import { SourceModule } from './source-graph-configuration';

const lastGoodModels = new Map<string, BuildModel>();

export function compilerBuildModels(): BuildModel[] {
    const folders = vscode.workspace.workspaceFolders ?? [];
    const active = new Set(folders.map(folder => folder.uri.toString()));
    for (const key of lastGoodModels.keys()) if (!active.has(key)) lastGoodModels.delete(key);
    return folders.flatMap(folder => {
        const key = folder.uri.toString();
        try {
            const model = readBuildModel(folder.uri.fsPath);
            if (model) lastGoodModels.set(key, model); else lastGoodModels.delete(key);
        } catch (error) { console.warn(`Invalid Ecstasy Gradle model; retaining previous import: ${error}`); }
        const model = lastGoodModels.get(key);
        return model ? [model] : [];
    });
}

async function folder(): Promise<vscode.WorkspaceFolder | undefined> {
    const folders = vscode.workspace.workspaceFolders ?? [];
    return folders.length === 1 ? folders[0] : vscode.window.showWorkspaceFolderPick();
}

/** Native task execution keeps build ownership and output visible and never starts Gradle in LSP. */
export async function refreshCompilerBuild(prepare = false): Promise<void> {
    const owner = await folder();
    if (!owner) return;
    if (!vscode.workspace.isTrusted) throw new Error('Trust this workspace before running its Gradle build.');
    const wrapper = vscode.Uri.joinPath(owner.uri, process.platform === 'win32' ? 'gradlew.bat' : 'gradlew');
    await vscode.workspace.fs.stat(wrapper);
    const operation = randomUUID();
    const task = new vscode.Task({ type: 'xtc-model', prepare, operation }, owner,
        prepare ? 'Prepare compiler inputs' : 'Refresh compiler paths', 'Ecstasy',
        new vscode.ProcessExecution(wrapper.fsPath, [prepare ? 'prepareXtcLspModel' : 'exportXtcLspModel', '--console=plain'], { cwd: owner.uri.fsPath }));
    await new Promise<void>((resolve, reject) => {
        const ended = vscode.tasks.onDidEndTaskProcess(event => {
            if (event.execution.task.definition.operation !== operation) return;
            ended.dispose();
            if (event.exitCode === 0) resolve();
            else reject(new Error(`Gradle model export failed (${event.exitCode}); previous compiler configuration retained.`));
        });
        vscode.tasks.executeTask(task).then(undefined, error => { ended.dispose(); reject(error); });
    });
    if (!readBuildModel(owner.uri.fsPath)) throw new Error('Gradle did not export an Ecstasy compiler model.');
    await updateCompilerConfiguration();
}

export function registerCompilerPaths(context: vscode.ExtensionContext): void {
    const output = vscode.window.createOutputChannel('Ecstasy Compiler Paths');
    const report = async () => {
        output.clear();
        output.appendLine(compilerSourceModules() === null ? 'Effective source origin: Gradle model where imported; workspace conventions otherwise.' : 'Effective source origin: explicit workspace override (preserved across Gradle refresh).');
        const modules = await getClient()?.sendRequest<SourceModule[]>('xtc/compilerSourceModules');
        output.appendLine(JSON.stringify(modules ?? [], null, 2));
        compilerBuildModels().forEach(model => output.appendLine(describeBuildModel(model)));
        output.show(true);
    };
    const configure = async () => {
        const selection = await vscode.window.showQuickPick(['Show effective paths', 'Refresh Gradle model', 'Prepare generated resources', 'Open build file', 'Edit explicit module paths', 'Reset to build model / discovery']);
        if (selection === 'Show effective paths') return report();
        if (selection === 'Refresh Gradle model') return refreshCompilerBuild();
        if (selection === 'Prepare generated resources') return refreshCompilerBuild(true);
        if (selection === 'Reset to build model / discovery') {
            await vscode.workspace.getConfiguration('xtc.compiler').update('sourceModules', null, vscode.ConfigurationTarget.Workspace);
            return;
        }
        if (selection === 'Open build file') {
            const entries = compilerBuildModels().flatMap(model => model.sourceSets);
            const selected = await vscode.window.showQuickPick([...new Set(entries.map(entry => entry.buildFile))]);
            if (selected) await vscode.window.showTextDocument(vscode.Uri.parse(selected));
            return;
        }
        if (selection !== 'Edit explicit module paths') return;
        const owner = await folder();
        if (!owner) return;
        const effective = (compilerSourceModules() as SourceModule[] | null) ?? await getClient()?.sendRequest<SourceModule[]>('xtc/compilerSourceModules') ?? [];
        const chosen = await vscode.window.showQuickPick([...effective.map(module => module.name), 'Add module']);
        if (!chosen) return;
        const previous = effective.find(module => module.name === chosen);
        const name = previous?.name ?? await vscode.window.showInputBox({ prompt: 'Module name' });
        if (!name) return;
        const uri = await vscode.window.showInputBox({ prompt: 'Module root file URI or path relative to the selected workspace folder', value: previous?.uri });
        if (!uri) return;
        const resources = await vscode.window.showInputBox({ prompt: 'Ordered resource paths as a JSON array; blank = conventions, [] = none', value: previous?.resourceRoots == null ? '' : JSON.stringify(previous.resourceRoots) });
        if (resources === undefined) return;
        const dependencies = await vscode.window.showInputBox({ prompt: 'Source dependencies, separated by commas', value: previous?.dependencies?.join(', ') ?? '' });
        if (dependencies === undefined) return;
        const resolve = (value: string) => value.startsWith('file:') ? vscode.Uri.parse(value).toString() : vscode.Uri.file(path.resolve(owner.uri.fsPath, value)).toString();
        const roots: unknown = resources.trim() ? JSON.parse(resources) : null;
        if (roots !== null && (!Array.isArray(roots) || roots.some(value => typeof value !== 'string'))) throw new Error('Resource roots must be a JSON array of paths.');
        const replacement: SourceModule = { name, uri: resolve(uri), dependencies: dependencies.split(',').map(value => value.trim()).filter(Boolean), resourceRoots: roots === null ? null : (roots as string[]).map(resolve) };
        await vscode.workspace.getConfiguration('xtc.compiler').update('sourceModules', [...effective.filter(module => module.name !== name), replacement], vscode.ConfigurationTarget.Workspace);
    };
    const guarded = (action: () => Promise<void>) => async () => {
        try { await action(); } catch (error) { void vscode.window.showErrorMessage(String(error)); }
    };
    const watchers = new Map<string, vscode.Disposable>();
    const changed = () => { void updateCompilerConfiguration().catch(error => output.appendLine(String(error))); };
    const watch = (owner: vscode.WorkspaceFolder) => {
        const watcher = vscode.workspace.createFileSystemWatcher(new vscode.RelativePattern(owner, modelPath));
        watchers.set(owner.uri.toString(), vscode.Disposable.from(watcher, watcher.onDidCreate(changed), watcher.onDidChange(changed), watcher.onDidDelete(changed)));
    };
    (vscode.workspace.workspaceFolders ?? []).forEach(watch);
    context.subscriptions.push(output, { dispose: () => watchers.forEach(watcher => watcher.dispose()) },
        vscode.workspace.onDidChangeWorkspaceFolders(event => {
            event.removed.forEach(owner => { watchers.get(owner.uri.toString())?.dispose(); watchers.delete(owner.uri.toString()); });
            event.added.forEach(watch);
            changed();
        }),
        vscode.commands.registerCommand('xtc.configureCompilerPaths', guarded(configure)),
        vscode.commands.registerCommand('xtc.showCompilerPaths', guarded(report)),
        vscode.commands.registerCommand('xtc.refreshCompilerBuild', guarded(() => refreshCompilerBuild())),
        vscode.commands.registerCommand('xtc.prepareCompilerInputs', guarded(() => refreshCompilerBuild(true))));
}
