import * as assert from 'node:assert';
import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import * as path from 'node:path';

// Type-only import: the canonical JSON stays outside the extension package and is loaded below.
import type scenarioFile from '../../../../test-fixtures/compiler-playbook/scenarios.json';

type Catalog = typeof scenarioFile;
export type ScenarioId = keyof Catalog['cases'];
export type ScenarioValues<K extends ScenarioId> = Catalog['cases'][K]['values'];
export const sharedScenarioPath = path.resolve(__dirname, '../../../../test-fixtures/compiler-playbook/scenarios.json');
const contents = readFileSync(sharedScenarioPath, 'utf8');
export const sharedScenarioHash = createHash('sha256').update(contents).digest('hex');
export const catalog: Catalog = JSON.parse(contents);
assert.strictEqual(catalog.schemaVersion, 2, 'Unsupported shared playbook schema');
export const sharedScenarioIds = Object.keys(catalog.cases) as ScenarioId[];
const expectedIds = [...Array.from({ length: 90 }, (_, index) => `X${index + 1}`), 'CFG1', 'CFG2', 'CFG3', '7a.8', '7a.9'];
assert.deepStrictEqual(sharedScenarioIds, expectedIds, 'The catalog must describe the complete playbook in order');
for (const [id, scenario] of Object.entries(catalog.cases)) {
    assert.ok(scenario.title && scenario.values && Array.isArray(scenario.manual), `Invalid scenario ${id}`);
    assert.ok(['full', 'partial', 'not-implemented'].includes(scenario.intellij.coverage), `Missing IntelliJ coverage for ${id}`);
    assert.ok(scenario.intellij.coverage === 'full' || scenario.intellij.limitations.length > 0, `Explain the IntelliJ gap for ${id}`);
}

export function scenarioRegex(pattern: { source: string; flags: string }): RegExp {
    return new RegExp(pattern.source, pattern.flags);
}

/** ${0}, ${1}, ... insert literal data; catalog contents are never evaluated as code. */
export function scenarioText(template: string, ...values: (string | number | undefined)[]): string {
    const used = new Set<number>();
    const result = template.replace(/\$\{(\d+)\}/g, (_, index) => {
        const position = Number(index);
        assert.ok(position < values.length, `Missing substitution ${position} in ${template}`);
        used.add(position);
        return String(values[position]);
    });
    assert.strictEqual(used.size, values.length, `Unused substitutions in ${template}`);
    return result;
}

export const shared = {
    sourceModules: catalog.common.sourceModules,
    diagnostics: { id: 'X2', ...catalog.cases.X2.values },
    definitions: { id: 'X4', ...catalog.cases.X4.values },
    completion: { id: 'X7', ...catalog.cases.X7.values },
    dependencyNavigation: { id: 'X45', ...catalog.cases.X45.values },
    dependencyEdit: { id: 'X46', ...catalog.cases.X46.values },
    configuration: { id: 'CFG1', ...catalog.cases.CFG1.values },
    warning: { id: '7a.8', ...catalog.cases['7a.8'].values }
};

/** An edit/anchor must be unique: silently editing another example invalidates the test. */
function unique(text: string, anchor: string): number {
    assert.ok(anchor.length > 0, 'Empty shared playbook anchor');
    const offset = text.indexOf(anchor);
    assert.ok(offset >= 0 && text.indexOf(anchor, offset + 1) < 0, `Expected one shared playbook anchor: ${JSON.stringify(anchor)}`);
    return offset;
}

export function editScenario(text: string, edit: { from: string; to: string }): string {
    const offset = unique(text, edit.from);
    return text.slice(0, offset) + edit.to + text.slice(offset + edit.from.length);
}

export function scenarioOffset(text: string, marked: string): number {
    const marker = unique(marked, '§');
    return unique(text, marked.replace('§', '')) + marker;
}

export function validateSharedFixtures(fixture: (file: string) => string): void {
    for (const error of shared.diagnostics.errors) {
        const text = editScenario(fixture(shared.diagnostics.file), error.edit);
        assert.ok(scenarioOffset(text, error.rangeStart) <= scenarioOffset(text, error.rangeEnd));
    }
    for (const location of shared.definitions.locations) {
        scenarioOffset(fixture(shared.definitions.file), location.cursor);
        scenarioOffset(fixture(location.targetFile), location.target);
    }
    const incomplete = editScenario(fixture(shared.completion.file), shared.completion.edit);
    scenarioOffset(incomplete, shared.completion.cursor);
    editScenario(incomplete, shared.completion.accepted);
    scenarioOffset(fixture(shared.dependencyNavigation.file), shared.dependencyNavigation.location.cursor);
    scenarioOffset(fixture(shared.dependencyNavigation.location.targetFile), shared.dependencyNavigation.location.target);
    editScenario(fixture(shared.dependencyEdit.file), shared.dependencyEdit.edit);
    fixture(shared.dependencyEdit.consumer);
    fixture(shared.configuration.consumer);
    scenarioOffset(fixture(shared.warning.file), shared.warning.declaration);
    editScenario(fixture(shared.warning.file), shared.warning.edit);
    for (const module of shared.sourceModules) { fixture(module.uri); }
}
