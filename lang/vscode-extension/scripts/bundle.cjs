#!/usr/bin/env node
// Bundles the extension with esbuild. This is the single definition of the bundle, used by
// `npm run bundle`, `npm run watch` and the `:lang:vscode-extension:npmBundle` Gradle task.
//
// The extension runs on the Node that ships inside VS Code's Electron, not on the Node that
// builds it. package.json states that runtime once: engines.vscode is the oldest supported
// VS Code, and @types/node pins the Node major bundled with that release (VS Code 1.140 ships
// Node 24). The esbuild target is derived from the @types/node major so they cannot drift.
//
// Usage: node scripts/bundle.cjs <outfile> [--watch]

'use strict';

const path = require('node:path');
const esbuild = require('esbuild');

const root = path.resolve(__dirname, '..');
const manifest = require(path.join(root, 'package.json'));
const nodeMajor = /\d+/.exec(manifest.devDependencies['@types/node'] ?? '')?.[0];
const [outfile, ...flags] = process.argv.slice(2);

if (!nodeMajor) {
    console.error('[bundle] package.json must pin @types/node to the Node of the minimum VS Code.');
    process.exit(1);
}
if (!outfile) {
    console.error('Usage: node scripts/bundle.cjs <outfile> [--watch]');
    process.exit(1);
}

const watch = flags.includes('--watch');
const options = {
    absWorkingDir: root,
    entryPoints: ['src/extension.ts'],
    bundle: true,
    outfile: path.resolve(outfile),
    external: ['vscode'],
    platform: 'node',
    target: `node${nodeMajor}`,
    minify: !watch,
    logLevel: 'info',
};

if (watch) {
    esbuild.context(options).then(context => context.watch());
} else {
    esbuild.build(options).catch(() => process.exit(1));
}
