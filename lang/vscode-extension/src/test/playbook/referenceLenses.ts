import * as assert from 'node:assert';
import * as vscode from 'vscode';
import { WorkbenchUi } from '../workbenchUi';
import { client, eventually, noErrors, playbook } from './support';

export function referenceLensCases(): void {
    playbook('X279', async (workspace, data) => {
        await workspace.write(data.file, data.source);
        await workspace.write(data.consumerFile, data.consumer);
        await workspace.configure([
            { name: data.module, uri: workspace.uri(data.file).toString() },
            { name: data.consumerModule, uri: workspace.uri(data.consumerFile).toString(), dependencies: [data.module] }
        ]);
        const document = await workspace.open(data.file);
        const settings = vscode.workspace.getConfiguration('xtc.codeLens', document.uri);
        const before = settings.inspect<boolean>('references')?.workspaceValue;
        const ui = await WorkbenchUi.connect();
        const token = new vscode.CancellationTokenSource();
        try {
            await settings.update('references', true, vscode.ConfigurationTarget.Workspace);
            await noErrors(document.uri);
            const provider = client().getFeature('textDocument/codeLens').getProvider(document)!.provider!;
            const lenses = async () => Promise.all((await provider.provideCodeLenses(document, token.token) ?? [])
                .map(lens => lens.isResolved ? lens : provider.resolveCodeLens!(lens, token.token)));
            await eventually(lenses, values => values.some(lens => lens?.command?.title === data.initialTitle), 'Closed-consumer reference count');
            const link = ui.page.locator('.editor-group-container.active .codelens-decoration a').filter({ hasText: data.initialTitle });
            await link.waitFor({ state: 'visible', timeout: 30_000 });
            await ui.screenshot('X279-reference-lens');
            await link.click({ timeout: 5_000 });
            const peek = ui.page.locator('.peekview-widget');
            await peek.waitFor({ state: 'visible', timeout: 15_000 });
            await eventually(() => peek.innerText(), text => text.includes(data.consumerFile) && text.includes(data.file), 'Native reference peek contains both files');
            await vscode.commands.executeCommand('closeReferenceSearch');
            const consumer = await workspace.open(data.consumerFile);
            await workspace.replace(consumer, data.changedConsumer);
            await noErrors(consumer.uri);
            await workspace.open(data.file);
            await eventually(lenses, values => values.some(lens => lens?.command?.title === data.changedTitle), 'Edited-consumer reference count');
            await settings.update('references', false, vscode.ConfigurationTarget.Workspace);
            await eventually(lenses, values => values.length === 1 && values[0]?.command?.command === 'xtc.runModule', 'Reference toggle retains Run');
            await eventually(() => ui.page.locator('.editor-group-container.active .codelens-decoration').allTextContents(),
                values => values.some(text => text.includes('Run')) && values.every(text => !text.includes('reference')), 'Native reference lenses disappear');
            await settings.update('references', true, vscode.ConfigurationTarget.Workspace);
            await eventually(lenses, values => values.some(lens => lens?.command?.title === data.changedTitle), 'Reference toggle restores current count');
            assert.strictEqual(document.getText(), data.source);
        } finally {
            token.dispose();
            await settings.update('references', before, vscode.ConfigurationTarget.Workspace);
            await ui.close();
        }
    });
}
