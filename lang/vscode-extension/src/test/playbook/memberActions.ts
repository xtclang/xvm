import * as assert from 'node:assert';
import * as vscode from 'vscode';
import { focusTestWindow } from '../native-focus';
import { discovered } from './liveWorkspace';
import { diagnostics, eventually, noErrors, playbook, position } from './support';

export function memberActionCases(): void {
    playbook('X122', async (workspace, data) => {
        await workspace.write(data.file, data.variants[0].source);
        await discovered(workspace, async () => {
            const document = await workspace.open(data.file);
            for (const variant of data.variants) {
                await workspace.replace(document, variant.source);
                const originalDiagnostics = async () => {
                    if ('initiallyValid' in variant && !variant.initiallyValid) {
                        await diagnostics(document.uri, values => values.some(item => item.severity === vscode.DiagnosticSeverity.Error),
                            'Missing implementation is diagnosed before applying the action');
                    } else await noErrors(document.uri);
                };
                await originalDiagnostics();
                const at = position(document, data.anchor);
                const actions = await eventually(async () => vscode.commands.executeCommand<vscode.CodeAction[]>(
                    'vscode.executeCodeActionProvider', document.uri, new vscode.Range(at, at), undefined, 100),
                actions => Array.isArray(actions) && (!variant.title || actions.some(action => action.title === variant.title)), 'Member action reply');
                if (!variant.title) {
                    assert.ok(!actions?.some(action => /^(Implement|Override) /.test(action.title) && action.title.includes(` ${data.refusalMember}(`)));
                    assert.strictEqual(document.getText(), variant.source);
                    continue;
                }
                const action = actions?.find(action => action.title === variant.title);
                assert.ok(action?.edit);
                assert.ok(await vscode.workspace.applyEdit(action.edit));
                assert.strictEqual(document.getText(), variant.expected);
                await noErrors(document.uri);
                for (const [command, expected] of [['undo', variant.source], ['redo', variant.expected], ['undo', variant.source]]) {
                    await focusTestWindow();
                    await vscode.commands.executeCommand('workbench.action.focusActiveEditorGroup');
                    await vscode.commands.executeCommand(command);
                    await eventually(async () => document.getText(), text => text === expected, `${command} member action`);
                    if (command === 'redo') await noErrors(document.uri);
                    else await originalDiagnostics();
                }
            }
        });
    });
}

export function localRefactoringCases(ids: readonly ('X148' | 'X156' | 'X157' | 'X177' | 'X178' | 'X179' | 'X180' | 'X181' | 'X182' | 'X183' | 'X184' | 'X185' | 'X186' | 'X187' | 'X188' | 'X189' | 'X190' | 'X191' | 'X192' | 'X193' | 'X194' | 'X195' | 'X196' | 'X197' | 'X198' | 'X199' | 'X200' | 'X201' | 'X202' | 'X203' | 'X204' | 'X205' | 'X206' | 'X207' | 'X208' | 'X209' | 'X210' | 'X211' | 'X212' | 'X213' | 'X214' | 'X215' | 'X221' | 'X222' | 'X223' | 'X224' | 'X225' | 'X226' | 'X227' | 'X228' | 'X229' | 'X230' | 'X231' | 'X232' | 'X233' | 'X234' | 'X235' | 'X236' | 'X237' | 'X238' | 'X239' | 'X240' | 'X241' | 'X242')[] = ['X148']): void {
    for (const id of ids) playbook(id, async (workspace, data) => {
        await workspace.write(data.file, data.source);
        if ('destinationFile' in data) await workspace.write(data.destinationFile, data.destinationSource);
        if ('files' in data) for (const file of data.files) await workspace.write(file.file, file.source);
        await discovered(workspace, async () => {
            if ('sourceModules' in data) await workspace.configure(data.sourceModules.map(module => ({ ...module, uri: workspace.uri(module.uri).toString() })));
            const document = await workspace.open(data.file);
            const originalDiagnostics = async () => {
                if ('initiallyValid' in data && !data.initiallyValid) {
                    await diagnostics(document.uri, values => values.some(item => item.severity === vscode.DiagnosticSeverity.Error),
                        'Missing method is diagnosed before applying the action');
                } else await noErrors(document.uri);
            };
            const verifyAdditionalFiles = async (applied: boolean) => {
                if ('files' in data) for (const file of data.files) {
                    const document = await vscode.workspace.openTextDocument(workspace.uri(file.file));
                    const expected = applied && 'expected' in file ? file.expected : file.source;
                    assert.strictEqual(document.getText(), expected);
                }
            };
            await originalDiagnostics();
            const kind = 'initiallyValid' in data ? vscode.CodeActionKind.QuickFix.value : vscode.CodeActionKind.Refactor.value;
            const start = 'selectionOffset' in data ? document.positionAt(data.selectionOffset) : position(document, data.selected);
            const range = new vscode.Range(start, document.positionAt(document.offsetAt(start) + data.selected.length));
            const readActions = async () => {
                try {
                    return await vscode.commands.executeCommand<vscode.CodeAction[]>(
                        'vscode.executeCodeActionProvider', document.uri, range, kind, 100) ?? [];
                } catch (error) {
                    // Host refresh can cancel a read-only lookup. Never retry edits or history.
                    if (error instanceof Error && error.name === 'Canceled') {
                        console.log(`[playbook] ${id}: provider lookup cancelled before any edit; retrying lookup`);
                        return undefined;
                    }
                    throw error;
                }
            };
            if ('refused' in data && data.refused) {
                const actions = await eventually(readActions, items => items !== undefined, 'Completed code-action refusal query');
                assert.ok(!actions?.some(item => item.title === data.title));
                assert.strictEqual(document.getText(), data.source);
                return;
            }
            const action = await eventually(async () => {
                const actions = await readActions();
                return actions?.find(item => item.title === data.title);
            }, item => !!item?.edit, data.title);
            assert.ok(action?.edit);
            assert.ok(await vscode.workspace.applyEdit(action.edit));
            const destination = 'destinationFile' in data ? await workspace.open(data.destinationFile) : document;
            const original = 'destinationFile' in data ? data.destinationSource : data.source;
            assert.strictEqual(destination.getText(), data.expected);
            await verifyAdditionalFiles(true);
            if (destination !== document) assert.strictEqual(document.getText(), data.source);
            await noErrors(document.uri);
            for (const [command, expected] of [['undo', original], ['redo', data.expected], ['undo', original]]) {
                await focusTestWindow();
                await vscode.commands.executeCommand('workbench.action.focusActiveEditorGroup');
                await vscode.commands.executeCommand(command);
                await eventually(async () => destination.getText(), text => text === expected, `${command} local refactoring`);
                if (destination !== document) assert.strictEqual(document.getText(), data.source);
                await verifyAdditionalFiles(command === 'redo');
                if (command === 'redo') await noErrors(document.uri);
                else await originalDiagnostics();
            }
        });
    });

}
