import * as assert from 'node:assert';
import { defaultJvmOptions, validateJvmOptions, validateLogRetention } from '../../runtime-settings';

suite('Machine-local JVM options', () => {
    test('supported tuning remains separate arguments', () => {
        const options = ['-Xms256M', '-Xmx2G', '-Xss1M', '-XX:ActiveProcessorCount=4', '-XX:+UseG1GC'];
        assert.deepStrictEqual(validateJvmOptions(options), options);
    });
    test('launch overrides conflicting collectors and impossible heaps are rejected', () => {
        for (const options of [['-jar'], ['@options'], ['-Dxtc.adapter=mock'], ['-javaagent:agent.jar'], ['-Xmx1G -Xss1M'], ['-Xmx2G', '-Xmx1G'], ['-Xms2G', '-Xmx1G'], ['-XX:+UseG1GC', '-XX:+UseZGC'], ['-Xmx999999999999999999999G']]) assert.throws(() => validateJvmOptions(options));
    });
    test('the server runs ZGC under a 1 GB soft limit unless the options choose a collector or a maximum heap', () => {
        const defaults = ['-XX:+UseZGC', '-XX:SoftMaxHeapSize=1G'];
        assert.deepStrictEqual(defaultJvmOptions([]), defaults);
        assert.deepStrictEqual(defaultJvmOptions(['-Xms256M', '-Xss1M']), defaults);
        for (const options of [['-Xmx4G'], ['-XX:+UseG1GC'], ['-XX:+UseZGC']]) assert.deepStrictEqual(defaultJvmOptions(options), []);
    });
    test('retention rejects malformed values and impossible caps', () => {
        assert.strictEqual(validateLogRetention({ historyDays: 2 }).historyDays, 2);
        for (const value of [null, [], { historyDays: 0 }, { historyDays: 1.5 }, { totalSizeMb: 1 }, { retainedSessions: 21 }, { typo: 2 }]) assert.throws(() => validateLogRetention(value));
    });
});
