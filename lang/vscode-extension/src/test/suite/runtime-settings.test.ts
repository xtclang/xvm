import * as assert from 'node:assert';
import { ADAPTIVE_JVM_OPTIONS, memoryJvmOptions, runtimeLibraryArguments, validateJvmOptions, validateLogRetention } from '../../runtime-settings';

suite('Machine-local JVM options', () => {
    test('supported tuning remains separate arguments', () => {
        const options = ['-Xms256M', '-Xmx2G', '-Xss1M', '-XX:ActiveProcessorCount=4', '-XX:+UseG1GC'];
        assert.deepStrictEqual(validateJvmOptions(options), options);
    });
    test('launch overrides conflicting collectors and impossible heaps are rejected', () => {
        for (const options of [['-jar'], ['@options'], ['-Dxtc.adapter=mock'], ['-javaagent:agent.jar'], ['-Xmx1G -Xss1M'], ['-Xmx2G', '-Xmx1G'], ['-Xms2G', '-Xmx1G'], ['-XX:+UseG1GC', '-XX:+UseZGC'], ['-Xmx999999999999999999999G']]) assert.throws(() => validateJvmOptions(options));
    });
    test('the memory policy gives the JVM\'s own heap, a chosen maximum, or ZGC that adapts, unless the options choose', () => {
        assert.deepStrictEqual(memoryJvmOptions([], 'default', ''), []);
        assert.deepStrictEqual(memoryJvmOptions(['-Xss1M'], 'default', '4G'), ['-Xmx4G']);
        assert.deepStrictEqual(memoryJvmOptions([], 'adaptive', '4G'), ADAPTIVE_JVM_OPTIONS);
        for (const options of [['-Xmx2G'], ['-XX:+UseG1GC'], ['-XX:+UseZGC']]) assert.deepStrictEqual(memoryJvmOptions(options, 'adaptive', ''), []);
        assert.throws(() => memoryJvmOptions([], 'default', 'lots'));
    });
    test('retention rejects malformed values and impossible caps', () => {
        assert.strictEqual(validateLogRetention({ historyDays: 2 }).historyDays, 2);
        for (const value of [null, [], { historyDays: 0 }, { historyDays: 1.5 }, { totalSizeMb: 1 }, { retainedSessions: 21 }, { typo: 2 }]) assert.throws(() => validateLogRetention(value));
    });
    test('the server compiles opened XDK libraries and takes the workspace\'s own in their place unless the settings turn it off', () => {
        assert.deepStrictEqual(runtimeLibraryArguments(), ['-Dxtc.lsp.compileLibraries=true', '-Dxtc.lsp.replaceLibraries=true']);
    });
});
