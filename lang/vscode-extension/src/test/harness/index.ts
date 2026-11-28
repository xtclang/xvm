import * as path from 'node:path';
import { glob } from 'glob';
import Mocha from 'mocha';

/**
 * Runs the harness's own tests, which check the playbook's and the tour's waits and case runner, not the product: a
 * playbook or tour run runs them before its first case or step, so that it does not run on a broken harness, and
 * testPlaybookHarness runs them alone; the extension suite, which CI runs, does not. Fails once any of them failed.
 */
export async function runHarness(): Promise<void> {
    const mocha = new Mocha({ ui: 'tdd', color: true, timeout: 10_000 });
    for (const file of await glob('**/*.test.js', { cwd: __dirname })) mocha.addFile(path.resolve(__dirname, file));
    const failures = await new Promise<number>(resolve => mocha.run(resolve));
    if (failures > 0) throw new Error(`${failures} of the harness's tests failed: the playbook and the tour do not run on a broken harness`);
}

/** testPlaybookHarness's entry: the harness's tests alone. */
export async function run(): Promise<void> {
    await runHarness();
}
