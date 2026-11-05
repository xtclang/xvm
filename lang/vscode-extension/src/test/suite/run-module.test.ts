import * as assert from 'node:assert';
import * as vscode from 'vscode';
import { waitFor } from '../wait';

suite('Run Module', () => {
    test("the editor's Run button and context menu run the editor's module", async () => {
        const extension = vscode.extensions.getExtension('xtclang.xtc-language');
        assert.ok(extension);
        await extension.activate();
        const uri = vscode.Uri.joinPath(vscode.workspace.workspaceFolders![0].uri, 'hello.x');
        const editor = await vscode.window.showTextDocument(await vscode.workspace.openTextDocument(uri));
        await waitFor(async () => editor.document.languageId, id => id === 'xtc', 'hello.x is an Ecstasy file');
        const started: vscode.TaskExecution[] = [];
        const listener = vscode.tasks.onDidStartTask(event => started.push(event.execution));
        try {
            // a command of the editor/title/run and editor/context menus gets the editor's resource and an object of
            // the menu's own, such as { groupId }, which is no module name
            await vscode.commands.executeCommand('xtc.runModule', uri, { groupId: editor.viewColumn });
            const [run] = await waitFor(async () => started, executions => executions.length > 0, 'Run Module starts a task');
            assert.strictEqual(run.task.definition.moduleName, 'HelloEcstasy');
            assert.ok(!run.task.name.includes('[object Object]'), run.task.name);
        } finally {
            listener.dispose();
            started.forEach(execution => execution.terminate());
            await vscode.commands.executeCommand('workbench.action.closeAllEditors');
        }
    });
});
