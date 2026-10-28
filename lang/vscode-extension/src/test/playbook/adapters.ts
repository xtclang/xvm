import * as assert from 'node:assert';
import * as vscode from 'vscode';
import { getClient } from '../../lsp-client';
import { WorkbenchUi } from '../workbenchUi';
import { eventually, noErrors, playbook } from './support';

async function adapterStatus() {
    const current = getClient();
    if (!current?.isRunning()) return undefined;
    try { return await current.sendRequest<{ pid: number; adapter: string }>('xtc/languageServiceStatus'); }
    catch { return undefined; }
}

/** Playwright key presses for a VS Code keybinding such as `ctrl+alt+x a`: one press per chord part. */
function keyPresses(binding: string): string[] {
    const modifiers: Record<string, string> = { ctrl: 'Control', alt: 'Alt', shift: 'Shift', cmd: 'Meta', meta: 'Meta' };
    return binding.split(' ').map(part => part.split('+').map(token =>
        modifiers[token] ?? (/^[a-z]$/.test(token) ? `Key${token.toUpperCase()}` : /^\d$/.test(token) ? `Digit${token}` : token)).join('+'));
}

/** Answers the rendered quick pick the way a user does: type the label, then Enter. */
async function choose(ui: WorkbenchUi, title: string, label: string): Promise<void> {
    const widget = ui.page.locator('.quick-input-widget');
    await widget.locator('.quick-input-title', { hasText: title }).waitFor({ state: 'visible', timeout: 15_000 });
    await ui.page.keyboard.type(label);
    await widget.locator('.monaco-list-row', { hasText: label }).first().waitFor({ state: 'visible', timeout: 5_000 });
    await ui.page.keyboard.press('Enter');
}

export function adapterChooserCases(): void {
    playbook('X286', async (workspace, data) => {
        const document = await workspace.open(data.file, data.source);
        await noErrors(document.uri);
        const settings = vscode.workspace.getConfiguration('xtc');
        const original = settings.inspect('languageService.adapter')?.workspaceValue;
        const ui = await WorkbenchUi.connect();
        let previous = (await adapterStatus())!.pid;
        try {
            // the extension's own keybinding, which must be the shortcut both editors share
            const extension = vscode.extensions.all.find(item => item.packageJSON.name === 'xtc-language')!;
            const binding = (extension.packageJSON.contributes.keybindings as { command: string; key: string; mac?: string }[])
                .find(item => item.command === data.actionId);
            assert.ok(binding, `${data.actionId} has a keybinding`);
            const key = process.platform === 'darwin' ? binding.mac ?? binding.key : binding.key;
            assert.strictEqual(key, data.shortcut, 'The keybinding matches the shared shortcut');
            await vscode.window.showTextDocument(document, { preview: false });
            await ui.focusEditor();
            for (const press of keyPresses(key)) await ui.page.keyboard.press(press);
            await choose(ui, data.chooserTitle, data.treeSitterLabel);
            const treeSitter = await eventually(adapterStatus, value => value?.adapter === 'TreeSitter' && value.pid !== previous, 'Shortcut chooser restarts into Tree-sitter');
            previous = treeSitter!.pid;

            // the same chooser from the Command Palette
            await ui.focusEditor();
            await ui.page.keyboard.press('F1');
            await ui.page.keyboard.type(data.commandTitle);
            await ui.page.locator('.quick-input-widget .monaco-list-row', { hasText: data.commandTitle }).first().waitFor({ state: 'visible', timeout: 15_000 });
            await ui.page.keyboard.press('Enter');
            await choose(ui, data.chooserTitle, data.compilerLabel);
            const compiler = await eventually(adapterStatus, value => value?.adapter === 'XDK' && value.pid !== previous, 'Command chooser restarts into the compiler');
            assert.notStrictEqual(compiler!.pid, treeSitter!.pid);
        } finally {
            await ui.close();
            await settings.update('languageService.adapter', original, vscode.ConfigurationTarget.Workspace);
            await eventually(adapterStatus, value => value?.adapter === 'XDK', 'Compiler adapter restored for later cases');
        }
    });
}
