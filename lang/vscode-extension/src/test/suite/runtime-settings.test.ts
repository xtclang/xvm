import * as assert from 'node:assert';
import { validateJvmOptions } from '../../runtime-settings';

suite('Machine-local JVM options', () => {
    test('supported tuning remains separate arguments', () => {
        const options = ['-Xms256M', '-Xmx2G', '-Xss1M', '-XX:ActiveProcessorCount=4', '-XX:+UseG1GC'];
        assert.deepStrictEqual(validateJvmOptions(options), options);
    });
    test('launch overrides conflicting collectors and impossible heaps are rejected', () => {
        for (const options of [['-jar'], ['@options'], ['-Dxtc.adapter=mock'], ['-javaagent:agent.jar'], ['-Xmx1G -Xss1M'], ['-Xmx2G', '-Xmx1G'], ['-Xms2G', '-Xmx1G'], ['-XX:+UseG1GC', '-XX:+UseZGC'], ['-Xmx999999999999999999999G']]) assert.throws(() => validateJvmOptions(options));
    });
});
