// Mocha test loader, called by @vscode/test-electron inside the spawned
// VS Code instance. Discovers `*.test.js` files in this directory (after
// TS compile) and runs them as a single Mocha suite.

import * as path from 'node:path';
import { glob } from 'glob';
import Mocha from 'mocha';
import { showTestProgress } from '../progress';
import { stopOnUnsafeFailure } from '../wait';

export async function run(): Promise<void> {
    const mocha = new Mocha({ ui: 'tdd', color: true, timeout: 10000 });
    const testsRoot = path.resolve(__dirname);
    const files = await glob('**/*.test.js', { cwd: testsRoot });
    for (const file of files) {
        mocha.addFile(path.resolve(testsRoot, file));
    }
    await new Promise<void>((resolve, reject) => {
        const runner = mocha.run(failures => {
            if (failures > 0) {
                reject(new Error(`${failures} test(s) failed.`));
            } else {
                resolve();
            }
        });
        stopOnUnsafeFailure(runner);
        showTestProgress(runner, 'Ecstasy tests', test =>
            test.title.length <= 48 ? test.title : test.title.slice(0, 45) + '…');
    });
}
