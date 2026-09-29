import * as assert from 'node:assert';
import { applyEdits } from 'jsonc-parser';
import { SourceModule, sourceGraphEdits, sourceGraphKey, sourceModulesIn } from '../../source-graph-configuration';

suite('Rename settings boundaries', () => {
    const before: SourceModule[] = [{ name: 'Library', uri: 'file:///libraries/Library.x' },
        { name: 'Consumer', uri: 'file:///application/Consumer.x', dependencies: ['Library'] }];
    const after: SourceModule[] = [{ name: 'Renamed', uri: 'file:///libraries/Renamed.x' },
        { name: 'Consumer', uri: 'file:///application/Consumer.x', dependencies: ['Renamed'] }];
    const path = ['settings', 'xtc.compiler.sourceModules'];
    const text = `{
        // Preserve the workspace, not just its compiler graph.
        "folders": [{"path": "../libraries"}, {"path": "../application"}],
        "settings": {"editor.tabSize": 8, "xtc.compiler.sourceModules": ${JSON.stringify(before)},},
    }`;

    test('resource roots preserve precedence and are guarded through rename history', () => {
        const original = [{ ...before[0], resourceRoots: ['file:///custom/', 'file:///fallback/'] }];
        const renamed = [{ ...after[0], resourceRoots: original[0].resourceRoots }];
        const settings = JSON.stringify({ settings: { 'xtc.compiler.sourceModules': original } });
        const edited = applyEdits(settings, sourceGraphEdits(settings, path, original, renamed));
        assert.deepStrictEqual(sourceModulesIn(edited, path), renamed);
        assert.notStrictEqual(sourceGraphKey(original), sourceGraphKey([{ ...original[0], resourceRoots: [...original[0].resourceRoots].reverse() }]));
        assert.notStrictEqual(sourceGraphKey([{ ...before[0], resourceRoots: [] }]), sourceGraphKey([before[0]]));
        assert.strictEqual(sourceGraphKey([{ ...before[0], resourceRoots: null }]), sourceGraphKey([before[0]]));
        assert.throws(() => sourceGraphEdits(settings, path, [{ ...before[0], resourceRoots: [] }], renamed), /graph changed/);
        assert.throws(() => sourceGraphKey([{ ...before[0], resourceRoots: ['file:///custom/', 'file:///custom/'] }]), /Duplicate/);
    });

    test('saved multi-root settings preserve folders, comments and unrelated preferences through history', () => {
        const changed = applyEdits(text, sourceGraphEdits(text, path, before, after));
        assert.deepStrictEqual(sourceModulesIn(changed, path), after);
        assert.ok(changed.includes('// Preserve the workspace'));
        assert.ok(changed.includes('"folders": [{"path": "../libraries"}, {"path": "../application"}]'));
        const independentlyEdited = changed.replace('"editor.tabSize": 8', '"editor.tabSize": 2');
        const undone = applyEdits(independentlyEdited, sourceGraphEdits(independentlyEdited, path, after, before));
        assert.deepStrictEqual(sourceModulesIn(undone, path), before);
        assert.ok(undone.includes('"editor.tabSize": 2'));
        assert.deepStrictEqual(sourceModulesIn(applyEdits(undone, sourceGraphEdits(undone, path, before, after)), path), after);
    });

    test('late proposals and history cannot overwrite independently changed dependency edges', () => {
        const changed = text.replace('"dependencies":["Library"]', '"dependencies":[]');
        assert.throws(() => sourceGraphEdits(changed, path, before, after), /graph changed/);
        assert.throws(() => sourceGraphEdits(text, path, after, before), /graph changed/);
        assert.deepStrictEqual(sourceModulesIn(text, path), before);
    });

    test('global-only or folder-only graphs cannot be materialized into workspace settings by rename', () => {
        assert.throws(() => sourceGraphEdits('{"settings":{}}', path, before, after), /explicit source graph/);
        assert.throws(() => sourceGraphEdits('{"xtc.compiler.sourceModules":null}', ['xtc.compiler.sourceModules'], before, after), /explicit source graph/);
    });

    test('relative roots require a single unambiguous workspace base', () => {
        const relative = [{ name: 'Library', uri: 'nested/../Library.x' }];
        assert.strictEqual(sourceGraphKey(relative, 'file:///libraries/'), sourceGraphKey([before[0]]));
        assert.throws(() => sourceGraphKey(relative));
    });

    test('malformed graphs, duplicate identities and unsupported URI schemes are refused', () => {
        [null, {}, [null], [{ name: 1, uri: 'file:///Library.x' }],
            [{ name: 'Library', uri: 'https://example.org/Library.x' }],
            [{ ...before[0], dependencies: [1] }], [before[0], before[0]],
            [before[0], { ...before[0], name: 'Different' }]].forEach(graph => assert.throws(() => sourceGraphKey(graph)));
        assert.throws(() => sourceGraphEdits('{', path, before, after), /settings JSON/);
        assert.throws(() => sourceGraphEdits(text, path, before, [after[0], after[0]]), /Duplicate/);
    });
});
