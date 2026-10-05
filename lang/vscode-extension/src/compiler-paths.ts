import { randomUUID } from 'node:crypto';
import * as fs from 'node:fs';
import * as path from 'node:path';
import * as vscode from 'vscode';
import { BuildModel, describeBuildModel, modelPath, parseBuildModel } from './build-model';
import { CompilerImport } from './compiler-import';
import { runCompilerTask } from './compiler-task';
import { getClient, updateCompilerConfiguration } from './lsp-client';
import { compilerSourceModules } from './rename-proposal';
import { SourceModule } from './source-graph-configuration';

const imports = new Map<string, CompilerImport>();
const activeImports = new Map<string, vscode.CancellationTokenSource>();

function importOwner(folder: vscode.WorkspaceFolder): CompilerImport {
    const key = folder.uri.toString();
    const existing = imports.get(key);
    if (existing) return existing;
    if (folder.uri.scheme !== 'file') throw new Error('Compiler build import requires a local workspace folder.');
    const file = path.join(folder.uri.fsPath, modelPath);
    const owner = new CompilerImport(() => fs.existsSync(file) ? fs.readFileSync(file, 'utf8') : undefined, parseBuildModel);
    imports.set(key, owner);
    return owner;
}

export function compilerBuildModels(): BuildModel[] {
    const folders = vscode.workspace.workspaceFolders ?? [];
    const active = new Set(folders.map(folder => folder.uri.toString()));
    for (const key of imports.keys()) if (!active.has(key)) imports.delete(key);
    return folders.flatMap(folder => {
        if (folder.uri.scheme !== 'file') return [];
        const owner = importOwner(folder);
        try {
            owner.current();
        } catch (error) { console.warn(`Invalid Ecstasy Gradle model; retaining previous import: ${error}`); }
        const text = owner.retained();
        return text === undefined ? [] : [parseBuildModel(text)];
    });
}

async function folder(): Promise<vscode.WorkspaceFolder | undefined> {
    const folders = vscode.workspace.workspaceFolders ?? [];
    return folders.length === 1 ? folders[0] : vscode.window.showWorkspaceFolderPick();
}

/** Native task execution keeps build ownership and output visible and never starts Gradle in LSP. */
export async function refreshCompilerBuild(prepare = false, selected?: vscode.WorkspaceFolder): Promise<void> {
    const owner = selected ?? await folder();
    if (!owner) return;
    if (!vscode.workspace.isTrusted) throw new Error('Trust this workspace before running its Gradle build.');
    if (owner.uri.scheme !== 'file') throw new Error('Compiler build import requires a local workspace folder.');
    const key = owner.uri.toString();
    const model = importOwner(owner);
    compilerBuildModels();
    const claim = model.begin(prepare);
    const cancellation = new vscode.CancellationTokenSource();
    activeImports.set(key, cancellation);
    try {
        const wrapper = vscode.Uri.joinPath(owner.uri, process.platform === 'win32' ? 'gradlew.bat' : 'gradlew');
        await vscode.workspace.fs.stat(wrapper);
        const operation = randomUUID();
        const task = new vscode.Task({ type: 'xtc-model', prepare, operation }, owner,
            prepare ? 'Prepare compiler inputs' : 'Refresh compiler paths', 'Ecstasy',
            new vscode.ProcessExecution(wrapper.fsPath, [prepare ? 'prepareXtcLspModel' : 'exportXtcLspModel', '--console=plain'], { cwd: owner.uri.fsPath }));
        const outcome = await vscode.window.withProgress({
            location: vscode.ProgressLocation.Notification,
            title: `Ecstasy — ${prepare ? 'preparing generated inputs' : 'refreshing compiler paths'} (${owner.name})`,
            cancellable: true,
        }, async (progress, token) => {
            const cancel = token.onCancellationRequested(() => cancellation.cancel());
            try {
                if (token.isCancellationRequested) cancellation.cancel();
                progress.report({ message: owner.uri.fsPath });
                return await runCompilerTask(task, cancellation.token, message => progress.report({ message }));
            } finally { cancel.dispose(); }
        });
        const result = model.finish(claim, cancellation.token.isCancellationRequested ? 'cancelled' : outcome.outcome, outcome.message);
        if (result.outcome === 'failed') throw new Error(result.message);
        if (result.outcome === 'succeeded') await updateCompilerConfiguration();
    } catch (error) {
        // finish only while this operation still owns the model; a validation failure already
        // retired it. Errors are rethrown below after recording the terminal model state.
        if (model.isRunning(claim)) model.finish(claim, cancellation.token.isCancellationRequested ? 'cancelled' : 'failed', String(error));
        if (!cancellation.token.isCancellationRequested) throw error;
    } finally {
        if (activeImports.get(key) === cancellation) activeImports.delete(key);
        cancellation.dispose();
    }
}

/** A dialog owns a settings snapshot only until another edit, import or connection replaces it. */
export class CompilerPathDraft implements vscode.Disposable {
    private readonly invalid = new AbortController();
    private readonly connection = getClient();
    private readonly snapshot = this.inputs();
    private readonly subscriptions: vscode.Disposable[];

    constructor(changed: vscode.Event<void>) {
        const invalidate = () => this.invalid.abort();
        this.subscriptions = [changed(invalidate),
            vscode.workspace.onDidChangeWorkspaceFolders(invalidate),
            vscode.workspace.onDidChangeConfiguration(event => {
                if (event.affectsConfiguration('xtc.compiler')) invalidate();
            })];
    }

    private inputs(): string {
        return JSON.stringify([compilerSourceModules(),
            vscode.workspace.workspaceFolders?.map(folder => folder.uri.toString()),
            vscode.workspace.workspaceFile?.toString()]);
    }

    assertCurrent(): void {
        if (this.invalid.signal.aborted || this.connection !== getClient() || this.snapshot !== this.inputs()) {
            throw new Error('Compiler paths changed while this dialog was open. Reopen Configure Compiler Paths to edit the current configuration.');
        }
    }

    dispose(): void {
        this.invalid.abort();
        this.subscriptions.forEach(subscription => subscription.dispose());
    }
}

export function registerCompilerPaths(context: vscode.ExtensionContext): void {
    const output = vscode.window.createOutputChannel('Ecstasy Compiler Paths');
    const draftsChanged = new vscode.EventEmitter<void>();
    const report = async () => {
        output.clear();
        output.appendLine(compilerSourceModules() === null ? 'Effective source origin: Gradle model where imported; workspace conventions otherwise.' : 'Effective source origin: explicit workspace override (preserved across Gradle refresh).');
        const modules = await getClient()?.sendRequest<SourceModule[]>('xtc/compilerSourceModules');
        output.appendLine(JSON.stringify(modules ?? [], null, 2));
        compilerBuildModels().forEach(model => output.appendLine(describeBuildModel(model)));
        (vscode.workspace.workspaceFolders ?? []).forEach(folder => {
            const model = imports.get(folder.uri.toString());
            if (model) output.appendLine(`${folder.name}: ${model.description()}`);
        });
        output.show(true);
    };
    const configure = async () => {
        draftsChanged.fire();
        const draft = new CompilerPathDraft(draftsChanged.event);
        try {
            const selection = await vscode.window.showQuickPick(['Show effective paths', 'Refresh Gradle model', 'Prepare generated resources', 'Open build file', 'Edit explicit module paths', 'Reset to build model / discovery']);
            if (selection === 'Show effective paths') return report();
            if (selection === 'Refresh Gradle model') return refreshCompilerBuild();
            if (selection === 'Prepare generated resources') return refreshCompilerBuild(true);
            if (selection === 'Reset to build model / discovery') {
                draft.assertCurrent();
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
            const mode = await vscode.window.showQuickPick(['Choose module file', 'Enter module path']);
            if (!mode) return;
            const uri = mode === 'Choose module file'
                ? (await vscode.window.showOpenDialog({ canSelectFiles: true, canSelectFolders: false, canSelectMany: false, filters: { Ecstasy: ['x'] }, defaultUri: owner.uri }))?.[0]?.toString()
                : await vscode.window.showInputBox({ prompt: 'Module root file URI or path relative to the selected workspace folder', value: previous?.uri });
            if (!uri) return;
            const resources = await vscode.window.showInputBox({ prompt: 'Ordered resource paths as a JSON array; blank = conventions, [] = none', value: previous?.resourceRoots == null ? '' : JSON.stringify(previous.resourceRoots) });
            if (resources === undefined) return;
            const dependencies = await vscode.window.showInputBox({ prompt: 'Source dependencies, separated by commas', value: previous?.dependencies?.join(', ') ?? '' });
            if (dependencies === undefined) return;
            const resolve = (value: string) => value.startsWith('file:') ? vscode.Uri.parse(value).toString() : vscode.Uri.file(path.resolve(owner.uri.fsPath, value)).toString();
            const roots: unknown = resources.trim() ? JSON.parse(resources) : null;
            if (roots !== null && (!Array.isArray(roots) || roots.some(value => typeof value !== 'string'))) throw new Error('Resource roots must be a JSON array of paths.');
            const replacement: SourceModule = { name, uri: resolve(uri), dependencies: dependencies.split(',').map(value => value.trim()).filter(Boolean), resourceRoots: roots === null ? null : (roots as string[]).map(resolve) };
            draft.assertCurrent();
            await vscode.workspace.getConfiguration('xtc.compiler').update('sourceModules', [...effective.filter(module => module.name !== name), replacement], vscode.ConfigurationTarget.Workspace);
        } finally {
            draft.dispose();
        }
    };
    const guarded = (action: () => Promise<void>) => async () => {
        try { await action(); } catch (error) { void vscode.window.showErrorMessage(String(error)); }
    };
    const watchers = new Map<string, vscode.Disposable>();
    const changed = () => { draftsChanged.fire(); void updateCompilerConfiguration().catch(error => output.appendLine(String(error))); };
    const watch = (owner: vscode.WorkspaceFolder) => {
        const watcher = vscode.workspace.createFileSystemWatcher(new vscode.RelativePattern(owner, modelPath));
        watchers.set(owner.uri.toString(), vscode.Disposable.from(watcher, watcher.onDidCreate(changed), watcher.onDidChange(changed), watcher.onDidDelete(changed)));
    };
    (vscode.workspace.workspaceFolders ?? []).forEach(watch);
    context.subscriptions.push(output, { dispose: () => {
        draftsChanged.fire();
        draftsChanged.dispose();
        watchers.forEach(watcher => watcher.dispose());
        activeImports.forEach(cancellation => cancellation.cancel());
        imports.clear();
    } },
        vscode.tasks.onDidEndTaskProcess(event => {
            const task = event.execution.task;
            const owner = task.scope;
            // Observe the public task lifecycle rather than depending on a particular Java or
            // Gradle extension. Our own xtc-model tasks cannot recursively trigger another export.
            if (event.exitCode !== 0 || task.definition.type !== 'gradle' || !owner || typeof owner === 'number' ||
                !vscode.workspace.isTrusted || owner.uri.scheme !== 'file' || activeImports.has(owner.uri.toString()) ||
                !fs.existsSync(path.join(owner.uri.fsPath, modelPath))) return;
            void refreshCompilerBuild(false, owner).catch(error => output.appendLine(`Automatic compiler refresh failed: ${error}`));
        }),
        vscode.workspace.onDidChangeWorkspaceFolders(event => {
            event.removed.forEach(owner => {
                const key = owner.uri.toString();
                activeImports.get(key)?.cancel();
                imports.delete(key);
                watchers.get(key)?.dispose(); watchers.delete(key);
            });
            event.added.forEach(watch);
            changed();
        }),
        vscode.commands.registerCommand('xtc.configureCompilerPaths', guarded(configure)),
        vscode.commands.registerCommand('xtc.showCompilerPaths', guarded(report)),
        vscode.commands.registerCommand('xtc.refreshCompilerBuild', guarded(() => refreshCompilerBuild())),
        vscode.commands.registerCommand('xtc.prepareCompilerInputs', guarded(() => refreshCompilerBuild(true))));
}
