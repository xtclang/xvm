import * as assert from 'node:assert';
import * as vscode from 'vscode';
import { formattingSettings } from '../../editor-settings';
import { configuredSettings, configurationProperties } from '../../settings-report';

suite('Settings consumers and reporting', () => {
    test('every settings group is included in the report', () => {
        const groups = vscode.extensions.getExtension('xtclang.xtc-language')!.packageJSON.contributes.configuration;
        assert.strictEqual(groups.length, 4);
        assert.ok(configurationProperties(groups)['xtc.java.vmOptions']);
        assert.ok(configurationProperties(groups)['xtc.compiler.libraries']);
        assert.deepStrictEqual(configurationProperties({ properties: { legacy: {} } }), { legacy: {} });
    });

    test('the formatter receives the configured wrapping margin and rejects invalid margins', async () => {
        const settings = vscode.workspace.getConfiguration('xtc');
        const previous = settings.inspect('formatting.maxLineWidth')?.globalValue;
        try {
            await settings.update('formatting.maxLineWidth', 73, vscode.ConfigurationTarget.Global);
            assert.strictEqual((formattingSettings() as { maxLineWidth: number }).maxLineWidth, 73);
            await settings.update('formatting.maxLineWidth', 0, vscode.ConfigurationTarget.Global);
            assert.throws(formattingSettings, /maxLineWidth/);
        } finally {
            await settings.update('formatting.maxLineWidth', previous, vscode.ConfigurationTarget.Global);
        }
    });

    test('machine reports use the same normalized values as the launcher', async () => {
        const settings = vscode.workspace.getConfiguration('xtc');
        const previous = settings.inspect('server.logs')?.globalValue;
        try {
            await settings.update('server.logs', { historyDays: 2 }, vscode.ConfigurationTarget.Global);
            const report = configuredSettings({ 'xtc.server.logs': { scope: 'machine' } });
            assert.deepStrictEqual(report, { 'server.logs': {
                value: { historyDays: 2, maxFileMb: 10, totalSizeMb: 50, retainedSessions: 5 },
                origin: 'user', scope: 'machine'
            } });
        } finally {
            await settings.update('server.logs', previous, vscode.ConfigurationTarget.Global);
        }
    });
});
