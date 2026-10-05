import * as fs from 'node:fs';
import * as path from 'node:path';
import { isDeepStrictEqual } from 'node:util';

export interface BuildSourceSet {
    projectId: string;
    projectPath: string;
    projectDirectory: string;
    buildFile: string;
    sourceSet: string;
    sourceRoots: string[];
    sourceFiles: string[];
    moduleRoots: string[];
    resourceSourceRoots: string[];
    resourceRoots: string[];
    resourceTask: string;
    projectDependencies: string[];
    modulePath: string[];
}
export interface BuildModel { schemaVersion: 1; sourceSets: BuildSourceSet[]; buildRoots?: string[] }
export const modelPath = '.gradle/xtc/lsp-model.json';
export const workspaceModelPath = '.gradle/xtc/lsp-workspace.json';

export function mergeBuildModels(models: BuildModel[]): BuildModel {
    const entries = new Map<string, BuildSourceSet>();
    for (const entry of models.flatMap(model => model.sourceSets)) {
        const key = JSON.stringify([entry.projectId, entry.sourceSet]);
        const previous = entries.get(key);
        if (previous && !isDeepStrictEqual(previous, entry)) throw new Error('Conflicting Gradle source-set ownership.');
        entries.set(key, entry);
    }
    return { schemaVersion: 1, sourceSets: [...entries.values()], buildRoots: [...new Set(models.flatMap(model => model.buildRoots ?? []))] };
}

export function readCompilerReport(folder: string): string | undefined {
    const report = [workspaceModelPath, modelPath].map(file => path.join(folder, file)).find(file => fs.existsSync(file));
    return report === undefined ? undefined : fs.readFileSync(report, 'utf8');
}

/** Read Gradle's evaluated contract; neither host interprets build script text. */
export function readBuildModel(folder: string): BuildModel | undefined {
    const text = readCompilerReport(folder);
    return text === undefined ? undefined : parseBuildModel(text);
}

export function parseBuildModel(text: string): BuildModel {
    const model = JSON.parse(text) as BuildModel;
    if (model.schemaVersion !== 1 || !Array.isArray(model.sourceSets)) throw new Error('Unsupported Ecstasy Gradle model; refresh build configuration.');
    const owners = new Set<string>();
    if (model.buildRoots !== undefined && (!Array.isArray(model.buildRoots) ||
        model.buildRoots.some(uri => typeof uri !== 'string' || new URL(uri).protocol !== 'file:'))) throw new Error('Invalid Gradle build roots.');
    for (const entry of model.sourceSets) {
        for (const field of ['projectId', 'projectPath', 'projectDirectory', 'buildFile', 'sourceSet', 'resourceTask'] as const) {
            if (typeof entry?.[field] !== 'string' || !entry[field].trim()) throw new Error(`Invalid Gradle ${field}`);
        }
        const owner = JSON.stringify([entry.projectId, entry.sourceSet]);
        if (!entry.projectId || !entry.sourceSet || owners.has(owner)) throw new Error('Invalid or duplicate Gradle source-set owner.');
        owners.add(owner);
        for (const field of ['sourceRoots', 'sourceFiles', 'moduleRoots', 'resourceSourceRoots', 'resourceRoots', 'modulePath'] as const) {
            if (!Array.isArray(entry[field]) || entry[field].some(uri => typeof uri !== 'string' || new URL(uri).protocol !== 'file:')) throw new Error(`Invalid Gradle ${field}`);
        }
        if (new URL(entry.buildFile).protocol !== 'file:' || new URL(entry.projectDirectory).protocol !== 'file:') throw new Error('Invalid Gradle project location');
        if (!Array.isArray(entry.projectDependencies) || entry.projectDependencies.some(item => typeof item !== 'string')) throw new Error('Invalid Gradle project dependencies');
    }
    return model;
}

export function describeBuildModel(model: BuildModel): string {
    return model.sourceSets.map(entry => {
        const paths = (kind: string, values: string[]) => values.map(uri => {
            const file = new URL(uri);
            return `  ${kind}: ${uri}${fs.existsSync(file) ? '' : ' [missing; prepare generated inputs]'}`;
        });
        const missing = entry.resourceRoots.filter(uri => !fs.existsSync(new URL(uri))).length;
        return [`${entry.projectPath} / ${entry.sourceSet} [Gradle model]`, `  Build: ${entry.buildFile}`,
            `  Processed resources: ${missing === 0 ? 'ready' : `${missing} missing; prepare generated resources`}`,
            ...paths('Source root', entry.sourceRoots), ...paths('Resource input', entry.resourceSourceRoots),
            ...paths('Processed resource (compiler input)', entry.resourceRoots), ...paths('Binary module path', entry.modulePath),
            `  Prepare: ${entry.resourceTask}`].join('\n');
    }).join('\n\n');
}
