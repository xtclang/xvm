import { Edit, modify, parse, ParseError } from 'jsonc-parser';

export interface SourceModule { name: string; uri: string; dependencies?: string[]; resourceRoots?: string[] | null }

export function sourceModulesIn(text: string, path: string[]): unknown {
    const errors: ParseError[] = [];
    const value = parse(text, errors, { allowTrailingComma: true });
    if (errors.length) throw new Error('Fix the workspace settings JSON before renaming a configured module.');
    return path.reduce((current, key) => current?.[key], value);
}

/** Match the server's URI rules: relative roots require exactly one workspace folder. */
export function sourceGraphKey(value: unknown, base?: string): string {
    if (!Array.isArray(value)) throw new Error('Module rename requires an explicit source graph in workspace settings.');
    const string = (value: unknown): value is string => typeof value === 'string' && value.trim().length > 0;
    const modules = value.map(module => {
        if (!module || !string(module.name) || !string(module.uri)
            || (module.dependencies !== undefined && (!Array.isArray(module.dependencies) || !module.dependencies.every(string)))
            || (module.resourceRoots != null && (!Array.isArray(module.resourceRoots) || !module.resourceRoots.every(string)))) {
            throw new Error('Source modules require non-blank names, file URIs and dependency names.');
        }
        const uri = new URL(module.uri, base);
        if (uri.protocol !== 'file:') throw new Error('Source module roots must use file URIs.');
        const resources: string[] | null = module.resourceRoots == null ? null : module.resourceRoots.map((path: string) => {
            const root = new URL(path, base);
            if (root.protocol !== 'file:') throw new Error('Resource roots must use file URIs.');
            return root.href.replace(/\/+$/, '') + '/';
        });
        if (resources && new Set(resources).size !== resources.length) throw new Error('Duplicate resource roots.');
        return { name: module.name, uri: uri.href, resourceRoots: resources, dependencies: [...new Set<string>(module.dependencies ?? [])].sort() };
    });
    if (new Set(modules.map(module => module.name)).size !== modules.length
        || new Set(modules.map(module => module.uri)).size !== modules.length) {
        throw new Error('Duplicate source module names or roots.');
    }
    return JSON.stringify(modules.sort((left, right) => left.name.localeCompare(right.name)));
}

/** Preserve comments, workspace folders and unrelated settings; never create an absent graph. */
export function sourceGraphEdits(text: string, path: string[], before: SourceModule[], after: SourceModule[], base?: string): Edit[] {
    if (sourceGraphKey(sourceModulesIn(text, path), base) !== sourceGraphKey(before, base)) {
        throw new Error('Compiler source graph changed; rename was not applied.');
    }
    sourceGraphKey(after, base);
    return modify(text, path, after, { formattingOptions: { insertSpaces: true, tabSize: 4 } });
}
