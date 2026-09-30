import * as fs from 'node:fs';
import * as path from 'node:path';

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
export interface BuildModel { schemaVersion: 1; sourceSets: BuildSourceSet[] }
export const modelPath = '.gradle/xtc/lsp-model.json';

/** Read Gradle's evaluated contract; neither host interprets build script text. */
export function readBuildModel(folder: string): BuildModel | undefined {
    const file = path.join(folder, modelPath);
    if (!fs.existsSync(file)) return undefined;
    return parseBuildModel(fs.readFileSync(file, 'utf8'));
}

export function parseBuildModel(text: string): BuildModel {
    const model = JSON.parse(text) as BuildModel;
    if (model.schemaVersion !== 1 || !Array.isArray(model.sourceSets)) throw new Error('Unsupported Ecstasy Gradle model; refresh build configuration.');
    const owners = new Set<string>();
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
        return [`${entry.projectPath} / ${entry.sourceSet} [Gradle model]`, `  Build: ${entry.buildFile}`,
            ...paths('Source root', entry.sourceRoots), ...paths('Resource input', entry.resourceSourceRoots),
            ...paths('Processed resource (compiler input)', entry.resourceRoots), ...paths('Binary module path', entry.modulePath),
            `  Prepare: ${entry.resourceTask}`].join('\n');
    }).join('\n\n');
}
