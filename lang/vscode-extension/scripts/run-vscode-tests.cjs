#!/usr/bin/env node
// Cross-platform launcher for the VS Code extension integration tests.
//
// Responsibility: pick a headless wrapper if and only if we need one (Linux
// with no DISPLAY but xvfb-run installed), otherwise launch the test runner
// directly. This keeps `npm run test:vscode` (and the Gradle task that
// invokes it) platform-independent — same command, same script, every OS.
//
// Behaviour by platform:
//   * macOS / Windows: launch node ./out/test/runTest.js directly. Electron
//     has no true headless mode on these platforms, so a VS Code window
//     pops up briefly during the test. Acceptable for local dev.
//   * Linux with DISPLAY set: launch directly (developer has a graphical
//     session, no need for xvfb).
//   * Linux without DISPLAY and with xvfb-run on PATH: wrap with
//     `xvfb-run -a` so a virtual framebuffer is provided and no window
//     ever appears (the headless-CI happy path).
//   * Linux without DISPLAY and without xvfb-run: print a clear hint and
//     attempt the launch anyway — it will fail fast at the Electron level
//     with a missing-display error, which surfaces the real problem
//     instead of hiding it behind a generic Gradle exec failure.

'use strict';

const { spawn, spawnSync } = require('node:child_process');
const path = require('node:path');

const RUNNER = path.resolve(__dirname, '..', 'out', 'test', 'runTest.js');

function hasCommand(name) {
    const probe = spawnSync(process.platform === 'win32' ? 'where' : 'which', [name], { stdio: 'ignore' });
    return probe.status === 0;
}

const needsHeadlessWrapper = process.platform === 'linux' && !process.env.DISPLAY;
let launcher = process.execPath;
let launcherArgs = [RUNNER];

if (needsHeadlessWrapper) {
    if (hasCommand('xvfb-run')) {
        launcher = 'xvfb-run';
        launcherArgs = ['-a', process.execPath, RUNNER];
        console.log('[test:vscode] Linux without DISPLAY detected — wrapping with xvfb-run for headless mode.');
    } else {
        console.warn('[test:vscode] WARNING: running on Linux without DISPLAY and without xvfb-run on PATH.');
        console.warn('[test:vscode] The Electron test instance will fail to launch.');
        console.warn('[test:vscode] Install xvfb (e.g. `apt-get install -y xvfb`) for headless CI runs.');
    }
}

const child = spawn(launcher, launcherArgs, { stdio: 'inherit' });
child.on('exit', code => process.exit(code ?? 1));
child.on('error', err => {
    console.error('[test:vscode] failed to launch:', err);
    process.exit(1);
});
