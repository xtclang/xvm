import * as assert from 'node:assert';
import * as fs from 'node:fs/promises';
import * as path from 'node:path';
import * as vscode from 'vscode';
import { getClient } from '../../lsp-client';
import { catalog } from '../playbook/shared';
import { client, eventually } from '../playbook/support';

/** Native windows with separate extension hosts; the launcher preserves the reopened profile. */
export async function run(): Promise<void> {
    const directory = process.env.XTC_LIFECYCLE_REPORT!;
    const role = process.env.XTC_LIFECYCLE_ROLE!;
    assert.ok(directory && ['primary', 'closing', 'reopened'].includes(role));
    const data = catalog.cases.X145.values;
    const folder = vscode.workspace.workspaceFolders![0].uri;
    const uri = vscode.Uri.joinPath(folder, data.file);
    const type = role === 'primary' ? 'Int' : 'String';
    const value = role === 'primary' ? '7' : '"second"';
    const text = `module ${data.module} {\n    static ${type} value = ${value};\n${Array.from({ length: data.methods }, (_, index) =>
        `    ${type} read${index}() { return value; }`).join('\n')}\n}\n// unsaved ${type}\n`;
    const receipt = async (name: string, value: unknown) => {
        const file = path.join(directory, `${name}.json`);
        await fs.writeFile(`${file}.tmp`, JSON.stringify(value, null, 2) + '\n');
        await fs.rename(`${file}.tmp`, file);
    };
    const wait = async (name: string) => {
        const deadline = Date.now() + 120_000;
        while (Date.now() < deadline) {
            try { return JSON.parse(await fs.readFile(path.join(directory, `${name}.json`), 'utf8')); }
            catch (error) { if ((error as NodeJS.ErrnoException).code !== 'ENOENT') throw error; }
            await new Promise(resolve => setTimeout(resolve, 100));
        }
        assert.fail(`Missing ${name} lifecycle receipt`);
    };
    try {
        const extension = vscode.extensions.all.find(item => item.packageJSON.name === 'xtc-language');
        assert.ok(extension);
        await extension.activate();
        const document = await vscode.workspace.openTextDocument(uri);
        await vscode.window.showTextDocument(document, { preview: false, preserveFocus: true });
        await eventually(async () => !!getClient()?.initializeResult, Boolean, 'Compiler starts in native window');
        const health = await client().sendRequest<{ adapter: string }>('xtc/healthCheck');
        assert.strictEqual(health.adapter, 'XDK');
        const status = await client().sendRequest<{ pid: number }>('xtc/languageServiceStatus');
        const params = { textDocument: { uri: uri.toString() }, position: { line: 1, character: 16 } };
        if (role === 'reopened') {
            assert.strictEqual(document.getText(), text, 'Hot exit restores the actual unsaved source');
            assert.ok(document.isDirty, 'The reopened buffer remains unsaved');
            const previous = await wait('closing');
            assert.notStrictEqual(status.pid, previous.pid);
            const hover = await client().sendRequest('textDocument/hover', params);
            assert.ok(JSON.stringify(hover).includes('String value'));
            await receipt('reopened', { pid: status.pid, previousPid: previous.pid, restored: true, status: 'passed' });
            return;
        }
        await receipt(`${role}-ready`, { pid: status.pid });
        const other = await wait(role === 'primary' ? 'closing-ready' : 'primary-ready');
        assert.notStrictEqual(status.pid, other.pid, 'Each window owns its compiler process');
        const edit = new vscode.WorkspaceEdit();
        edit.replace(uri, new vscode.Range(document.positionAt(0), document.positionAt(document.getText().length)), text);
        assert.ok(await vscode.workspace.applyEdit(edit));
        const pending = client().sendRequest<unknown[]>('textDocument/references', {
            ...params, context: { includeDeclaration: true }
        });
        let completed = false;
        const outcome = pending.then(value => ({ value, error: undefined }), (error: unknown) => ({ value: undefined, error }))
            .finally(() => { completed = true; });
        if (role === 'closing') {
            await eventually(async () => {
                assert.ok(!completed, 'Work remains pending until project close');
                return client().sendRequest<{ compilerQueue: { runningSize: number } }>('xtc/languageServiceStatus');
            }, value => value.compilerQueue.runningSize > 0, 'Compiler work started before native close');
            assert.ok(!completed, 'Close overlaps a pending reference request');
            assert.ok(document.isDirty);
            await receipt('closing', { pid: status.pid, pending: true, unsaved: true });
            // The native window owns shutdown and hot-exit backup. No client.stop or process kill.
            await vscode.commands.executeCommand('workbench.action.closeWindow');
            return;
        }
        await wait('reopened');
        const result = await outcome;
        assert.ifError(result.error);
        assert.strictEqual(result.value?.length, data.methods + 1);
        assert.strictEqual(document.getText(), text);
        assert.ok(document.isDirty);
        assert.strictEqual((await client().sendRequest<{ pid: number }>('xtc/languageServiceStatus')).pid, status.pid);
        const hover = await client().sendRequest('textDocument/hover', params);
        assert.ok(JSON.stringify(hover).includes('Int value'));
        await receipt('primary', { pid: status.pid, references: result.value.length, unsavedPreserved: true, status: 'passed' });
    } catch (error) {
        await receipt(`${role}-failure`, { error: String(error), stack: (error as Error).stack });
        throw error;
    }
}
