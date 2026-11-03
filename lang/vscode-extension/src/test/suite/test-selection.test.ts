import * as assert from 'node:assert';
import * as vscode from 'vscode';
import { selected } from '../../test-explorer';

suite('Test Explorer selection', () => {
    const controller = vscode.tests.createTestController('xtc-selection-test', 'Selection');
    suiteTeardown(() => controller.dispose());

    const item = (id: string, ...children: vscode.TestItem[]): vscode.TestItem => {
        const it = controller.createTestItem(id, id);
        children.forEach(child => it.children.add(child));
        return it;
    };
    // Mathy/ with adds and Inner, and Inner with works and also
    const works = item('Mathy/Inner.works');
    const also = item('Mathy/Inner.also');
    const inner = item('Mathy/Inner', works, also);
    const adds = item('Mathy/adds');
    const module = item('Mathy/', adds, inner);
    controller.items.replace([module]);
    const ids = (items: vscode.TestItem[]) => items.map(it => it.id);

    test('a test chosen with its class runs once, as the class', () => {
        assert.deepStrictEqual(ids(selected([works, inner], new Set())), ['Mathy/Inner']);
        assert.deepStrictEqual(ids(selected([module, adds, works], new Set())), ['Mathy/']);
        assert.deepStrictEqual(ids(selected([adds, adds], new Set())), ['Mathy/adds']);
    });

    test('an excluded test leaves the other tests of its class and module, one by one', () => {
        assert.deepStrictEqual(ids(selected([inner], new Set([works]))), ['Mathy/Inner.also']);
        assert.deepStrictEqual(ids(selected([module], new Set([works]))), ['Mathy/adds', 'Mathy/Inner.also']);
        assert.deepStrictEqual(ids(selected([module], new Set([inner]))), ['Mathy/adds']);
        assert.deepStrictEqual(ids(selected([works], new Set([works]))), []);
    });
});
