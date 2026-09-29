import * as assert from 'node:assert';
import * as fs from 'node:fs/promises';
import * as os from 'node:os';
import * as path from 'node:path';
import * as vscode from 'vscode';
import { diagnostics, label, noErrors, playbook, targets } from './support';

export function platformCases(): void {
    playbook('X124', async (workspace, data) => {
        const external = await fs.mkdtemp(path.join(os.tmpdir(), 'xtc-playbook-resources-'));
        try {
            await workspace.write(data.file, data.text);
            const modules = [{ name: data.module, uri: workspace.uri(data.file).toString(), resourceRoots: [vscode.Uri.file(external).toString()] }];
            await workspace.configure(modules);
            const document = await workspace.open(data.file);
            await diagnostics(document.uri, values => values.length > 0, 'Missing external resource');
            await fs.writeFile(path.join(external, data.resource), data.contents);
            await noErrors(document.uri);
            await workspace.configure([{ ...modules[0], resourceRoots: [] }]);
            await diagnostics(document.uri, values => values.length > 0, 'Explicitly disabled resources');
            await workspace.configure(modules);
            await noErrors(document.uri);
            assert.deepStrictEqual(vscode.workspace.getConfiguration('xtc.compiler').get('sourceModules'), modules);
            await fs.unlink(path.join(external, data.resource));
            await diagnostics(document.uri, values => values.length > 0, 'Deleted external resource');
            await fs.writeFile(path.join(external, data.resource), data.contents);
            await noErrors(document.uri);
        } finally { await fs.rm(external, { recursive: true, force: true }); }
    });

    playbook('X125', async (workspace, data) => {
        const document = await workspace.open(data.file, data.hoverSource);
        await noErrors(document.uri);
        const hover = await vscode.commands.executeCommand<vscode.Hover[]>('vscode.executeHoverProvider', document.uri, document.positionAt(data.hoverSource.lastIndexOf(data.hoverAnchor)));
        assert.ok(JSON.stringify(hover).includes(data.hoverExpected));
        for (const call of data.signatures) {
            await workspace.replace(document, call.text.replace('§', ''));
            const help = await workspace.signature(document, document.positionAt(call.text.indexOf('§')));
            assert.strictEqual(help?.signatures[0].parameters.length, 2);
            assert.strictEqual(help?.activeParameter, call.active);
        }
        for (const text of data.completions) {
            await workspace.replace(document, text.replace('§', ''));
            const items = await vscode.commands.executeCommand<vscode.CompletionList>('vscode.executeCompletionItemProvider', document.uri, document.positionAt(text.indexOf('§')));
            assert.ok(items?.items.some(item => label(item) === data.completion));
        }
        await workspace.replace(document, data.narrowedSource);
        await noErrors(document.uri);
        const locations = await targets(document, 'TypeDefinition', document.positionAt(data.narrowedSource.lastIndexOf(data.narrowedAnchor)));
        assert.ok(locations.some(location => location.uri.path.endsWith(data.targetSuffix)));
    });
}
