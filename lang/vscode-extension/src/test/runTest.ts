// Headless integration-test launcher. Downloads VS Code builds into the shared
// per-user cache (see vscodeCache.ts), then runs the Mocha suite inside each of
// them with the extension loaded from this build tree: once on the oldest VS
// Code that package.json's engines.vscode supports and once on the latest
// stable, so an API or Node feature missing from the minimum fails here, not
// for users.
//
// Invoked by `npm run test:vscode` and by the
// `:lang:vscode-extension:testVscodeExtension` Gradle task.

import { readFileSync } from 'node:fs';
import * as path from 'node:path';
import { downloadAndUnzipVSCode, runTests } from '@vscode/test-electron';
import {
    buildDirectory,
    markUsedAndPrune,
    removeCheckoutBuilds,
    sharedCachePath,
    withDownloadLock,
} from './vscodeCache';

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
    const cachePath = sharedCachePath();

    for (const build of removeCheckoutBuilds(path.join(extensionDevelopmentPath, '.vscode-test'))) {
        console.log(`[vscode-test] Removed ${build}: builds now live in ${cachePath}`);
    }

    const builds = await withDownloadLock(cachePath, async () => {
        const downloaded: { version: string; executable: string }[] = [];
        for (const version of [minimumVersion, 'stable']) {
            downloaded.push({ version, executable: await downloadAndUnzipVSCode({ version, cachePath }) });
        }
        const pruned = markUsedAndPrune(
            cachePath,
            downloaded.map(({ executable }) => buildDirectory(cachePath, executable)),
        );
        for (const build of pruned) {
            console.log(`[vscode-test] Removed ${build}: unused for 30 days`);
        }
        return downloaded;
    });

    for (const { version, executable } of builds) {
        console.log(`[vscode-test] Running the suite on VS Code ${version} (${executable})`);
        await runTests({
            vscodeExecutablePath: executable,
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
