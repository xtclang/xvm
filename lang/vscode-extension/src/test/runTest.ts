// Headless integration-test launcher. Downloads (and caches) a VS Code
// build via @vscode/test-electron, then runs the Mocha suite inside that
// VS Code instance with the extension loaded from this build tree.
//
// Invoked by `npm run test:vscode` and by the
// `:lang:vscode-extension:testVscodeExtension` Gradle task.

import * as path from 'node:path';
import { runTests } from '@vscode/test-electron';

async function main(): Promise<void> {
    // __dirname at runtime resolves to <ext>/out/test, so the extension
    // root and the fixtures directory are two levels up.
    const extensionDevelopmentPath = path.resolve(__dirname, '..', '..');
    const extensionTestsPath = path.resolve(__dirname, 'suite', 'index');
    const fixturesPath = path.resolve(extensionDevelopmentPath, 'src', 'test', 'fixtures');

    await runTests({
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

main().catch((err: unknown) => {
    console.error('[vscode-test] failed:', err);
    process.exit(1);
});
