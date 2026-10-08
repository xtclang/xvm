import * as assert from 'node:assert';
import { parseServiceSettings, synchronizationOptions } from '../../service-settings';

suite('Language service settings contract', () => {
    test('defaults remain Full synchronization and editor-owned saving', () => {
        const settings = parseServiceSettings({});
        assert.deepStrictEqual(settings, { adapter: 'default', textSynchronization: 'full', saveFormatting: 'editor', inlayHints: true });
        assert.ok(Object.isFrozen(settings));
    });
    test('invalid values never become a connection configuration', () => {
        for (const raw of [{ adapter: 'mock' }, { adapter: null }, { adapter: 'typo' }, { textSynchronization: 'patch' }, { saveFormatting: true }, { inlayHints: 'false' }, { textSynchronization: null }]) {
            assert.throws(() => parseServiceSettings(raw));
        }
    });
    test('native save formatting takes precedence over the server hook', () => {
        const settings = parseServiceSettings({ textSynchronization: 'incremental', saveFormatting: 'server' });
        assert.deepStrictEqual(synchronizationOptions(settings, false), { incremental: true, formatOnSave: true });
        assert.deepStrictEqual(synchronizationOptions(settings, true), { incremental: true, formatOnSave: false });
    });
});
