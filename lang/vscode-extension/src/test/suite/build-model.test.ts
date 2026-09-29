import * as assert from 'node:assert';
import { BuildSourceSet, parseBuildModel } from '../../build-model';

suite('Evaluated compiler build model', () => {
    const entry: BuildSourceSet = {
        projectId: 'file:///workspace/#:library', projectPath: ':library',
        projectDirectory: 'file:///workspace/library/', buildFile: 'file:///workspace/library/build.gradle.kts',
        sourceSet: 'main', sourceRoots: ['file:///outside/custom-source/'],
        sourceFiles: ['file:///outside/custom-source/Library.x'], moduleRoots: ['file:///outside/custom-source/Library.x'],
        resourceSourceRoots: ['file:///outside/assets/'], resourceRoots: ['file:///workspace/build/processed/', 'file:///fallback/'],
        resourceTask: ':library:processXtcResources', projectDependencies: [], modulePath: ['file:///outside/Library.xtc']
    };
    const text = (sourceSets: unknown[] = [entry], schemaVersion = 1) => JSON.stringify({ schemaVersion, sourceSets });

    test('preserves external paths, ordered processed resources and distinct main/test owners', () => {
        const model = parseBuildModel(text([entry, { ...entry, sourceSet: 'test' }]));
        assert.strictEqual(model.sourceSets.length, 2);
        assert.deepStrictEqual(model.sourceSets[0], entry);
    });

    test('rejects malformed or ambiguous models before installing configuration', () => {
        [text([entry], 2), text([entry, entry]), text([{ ...entry, sourceRoots: ['relative/path'] }]),
            text([{ ...entry, projectId: 42 }]), text([{ ...entry, resourceRoots: undefined }]),
            text([{ ...entry, projectDependencies: [1] }])].forEach(value => assert.throws(() => parseBuildModel(value)));
    });
});
