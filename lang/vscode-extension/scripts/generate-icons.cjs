#!/usr/bin/env node
// Generate VS Code extension icons from a source image.
// Produces:
//   <out-dir>/xtc.png       — 256x256 marketplace icon
//   <out-dir>/xtc-file.png  —  32x32  language file icon (shown next to .x files)
//
// Usage: node generate-icons.cjs <source-image> <out-dir>
// Invoked by the :lang:vscode-extension:generateIcons Gradle task, which
// passes absolute paths so the script does not depend on cwd.

const fs = require('node:fs');
const path = require('node:path');
const sharp = require('sharp');

const [, , srcPath, outDir] = process.argv;
if (!srcPath || !outDir) {
    console.error('Usage: generate-icons.cjs <source-image> <out-dir>');
    process.exit(1);
}
if (!fs.existsSync(srcPath)) {
    console.error(`Source image not found: ${srcPath}`);
    process.exit(1);
}

fs.mkdirSync(outDir, { recursive: true });

async function emit(size, name) {
    const outPath = path.join(outDir, name);
    await sharp(srcPath)
        .resize(size, size, { fit: 'cover', position: 'center' })
        .png({ compressionLevel: 9 })
        .toFile(outPath);
    const bytes = fs.statSync(outPath).size;
    console.log(`[icons] wrote ${outPath} (${size}x${size}, ${bytes} bytes)`);
}

(async () => {
    await emit(256, 'xtc.png');
    await emit(32, 'xtc-file.png');
})().catch((err) => {
    console.error('[icons] generation failed:', err);
    process.exit(1);
});
