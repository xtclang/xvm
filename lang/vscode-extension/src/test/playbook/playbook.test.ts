import * as assert from 'node:assert';
import * as vscode from 'vscode';
import { getClient } from '../../lsp-client';
import { advancedCases, typeHeaderCases } from './advanced';
import { completionCases } from './completion';
import { configurationCases, dependencyCases } from './dependencies';
import { graphCases } from './graph';
import { liveWorkspaceCases } from './liveWorkspace';
import { moduleCases } from './modules';
import { navigationCases } from './navigation';
import { propertyCases } from './properties';
import { renameCases } from './rename';
import { editScenario, scenarioOffset, scenarioText, shared } from './shared';
import { semanticCases } from './semantics';
import { client, diagnosticCode, diagnostics, eventually, fixture, loadFixtures, nextProblem, noErrors, playbook } from './support';

suite('XdkAdapter playbook', function () {
    suiteSetup(async function () {
        this.timeout(60_000);
        await loadFixtures();
        const extension = vscode.extensions.all.find(item => item.packageJSON.name === 'xtc-language');
        assert.ok(extension, 'Development extension loaded');
        await extension.activate();
        await eventually(async () => !!getClient()?.initializeResult, Boolean, 'Language server initialization');
        const health = await client().sendRequest<{ healthy: boolean; adapter: string }>('xtc/healthCheck');
        assert.ok(health.healthy);
        assert.strictEqual(health.adapter, 'XDK', 'Build this suite with -Plsp.adapter=compiler; another backend must not silently skip tests');
    });

    navigationCases();
    completionCases();
    moduleCases();
    semanticCases();
    dependencyCases();
    renameCases();
    graphCases();
    propertyCases();
    advancedCases();
    liveWorkspaceCases();
    typeHeaderCases(['X106']);
    configurationCases();

    playbook('7a.8', async (workspace, data) => {
        const scenario = shared.warning;
        const document = await workspace.open(scenario.file);
        const result = await diagnostics(document.uri, values => values.some(item => diagnosticCode(item) === scenario.code), 'Duplicate annotation warning');
        assert.strictEqual(result.length, scenario.count);
        assert.strictEqual(vscode.DiagnosticSeverity[result[0].severity].toUpperCase(), scenario.severity);
        assert.ok(result[0].message.toLowerCase().includes(scenario.messageContains.toLowerCase()));
        assert.strictEqual(result[0].source, 'xtc');
        const declaration = document.positionAt(scenarioOffset(document.getText(), scenario.declaration));
        assert.ok(result[0].range.isEqual(new vscode.Range(declaration, declaration.translate(0, data.warningCount))));
        await nextProblem(document, result);
        const errorDocument = await workspace.open(data.errorFile);
        await workspace.replace(errorDocument, fixture(data.errorFile).replace(data.replaceFrom, data.replaceWith));
        await diagnostics(errorDocument.uri, values => values.some(item => item.severity === vscode.DiagnosticSeverity.Error), 'Error alongside warning');
        await diagnostics(document.uri, values => values.length === data.warningCount && values[0].severity === vscode.DiagnosticSeverity.Warning, 'Warning remains in other file');
        await workspace.replace(document, editScenario(fixture(scenario.file), scenario.edit));
        await noErrors(document.uri);
        await diagnostics(errorDocument.uri, values => values.some(item => item.severity === vscode.DiagnosticSeverity.Error), 'Clearing warning preserves error');
        await workspace.replace(errorDocument, fixture(data.errorFile));
        await noErrors(errorDocument.uri);
        await workspace.replace(document, fixture(scenario.file));
        await diagnostics(document.uri, values => values.length === scenario.count && diagnosticCode(values[0]) === scenario.code, 'Warning restored');
    });

    playbook('7a.9', async (workspace, data) => {
        const document = await workspace.open(data.file, data.moduleStart + Array.from({ length: data.declarationCount }, (_, index) => scenarioText(data.declaration, index, index)).join('\n') + data.moduleEnd);
        const result = await diagnostics(document.uri, values => values.length > 0, 'Invalid source diagnostics');
        assert.ok(result.filter(item => item.severity === vscode.DiagnosticSeverity.Error).length <= data.maximumErrors, `${result.length} diagnostics`);
        assert.ok(result.every(item => diagnosticCode(item) !== data.diagnosticCode));
    });
});
