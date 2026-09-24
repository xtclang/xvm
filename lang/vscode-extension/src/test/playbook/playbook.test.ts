import * as assert from 'node:assert';
import * as vscode from 'vscode';
import { getClient } from '../../lsp-client';
import { completionCases } from './completion';
import { configurationCases, dependencyCases } from './dependencies';
import { moduleCases } from './modules';
import { navigationCases } from './navigation';
import { renameCases } from './rename';
import { semanticCases } from './semantics';
import { client, diagnosticCode, diagnostics, eventually, loadFixtures, playbook } from './support';

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
    configurationCases();

    playbook('7a.8', 'compiler-only duplicate annotation warning is delivered exactly once', async workspace => {
        const document = await workspace.open('DupAnno.x');
        const result = await diagnostics(document.uri, values => values.some(item => diagnosticCode(item) === 'VERIFY-75'), 'Duplicate annotation warning');
        assert.strictEqual(result.length, 1);
        assert.strictEqual(result[0].severity, vscode.DiagnosticSeverity.Warning);
    });

    playbook('7a.9', 'bad source has a bounded diagnostic result', async workspace => {
        const document = await workspace.open('Broken.x', 'module Broken {\n' + Array.from({ length: 150 }, (_, index) => `Missing${index} value${index};`).join('\n') + '\n}');
        const result = await diagnostics(document.uri, values => values.length > 0, 'Invalid source diagnostics');
        assert.ok(result.filter(item => item.severity === vscode.DiagnosticSeverity.Error).length <= 100, `${result.length} diagnostics`);
        assert.ok(result.every(item => diagnosticCode(item) !== 'EMB-5'));
    });
});
