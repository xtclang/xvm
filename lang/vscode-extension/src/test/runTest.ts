// Headless integration-test launcher. Downloads (and caches) VS Code builds
// via @vscode/test-electron, then runs the Mocha suite inside each of them
// with the extension loaded from this build tree: once on the oldest VS Code
// that package.json's engines.vscode supports and once on the latest stable,
// so an API or Node feature missing from the minimum fails here, not for users.
//
// Invoked by `npm run test:vscode` and by the
// `:lang:vscode-extension:testVscodeExtension` Gradle task.

import { readFileSync } from 'node:fs';
import * as path from 'node:path';
import { runTests } from '@vscode/test-electron';

async function main(): Promise<void> {
    // __dirname at runtime resolves to <ext>/out/test, so the extension
    // root and the fixtures directory are two levels up.
    const extensionDevelopmentPath = path.resolve(__dirname, '..', '..');
    const extensionTestsPath = path.resolve(__dirname, 'suite', 'index');
    const fixturesPath = path.resolve(extensionDevelopmentPath, 'src', 'test', 'fixtures');
    const manifest = JSON.parse(readFileSync(path.join(extensionDevelopmentPath, 'package.json'), 'utf8')) as {
        engines: { vscode: string };
    };
    const minimumVersion = manifest.engines.vscode.replace(/^\^/, '');

    for (const version of [minimumVersion, 'stable']) {
        console.log(`[vscode-test] Running the suite on VS Code ${version}`);
        await runTests({
            version,
            extensionDevelopmentPath,
            extensionTestsPath,
            // --disable-extensions stops third-party extensions (anything that
            // claims `.x` — e.g. Logos parser-generator extensions) from
            // preempting our language registration; that way the test asserts
            // OUR behaviour, not the intersection of the user's installed
            // extensions and ours.
            launchArgs: [fixturesPath, '--disable-extensions'],
        });
    }
}

main().catch((err: unknown) => {
    console.error('[vscode-test] failed:', err);
    process.exit(1);
});
