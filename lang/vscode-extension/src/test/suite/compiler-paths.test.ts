import * as assert from 'node:assert';
import * as vscode from 'vscode';
import { CompilerPathDraft } from '../../compiler-paths';

suite('Compiler path draft ownership', () => {
    test('model refresh and a newer dialog invalidate a pending edit', () => {
        const changes = new vscode.EventEmitter<void>();
        const old = new CompilerPathDraft(changes.event);
        try {
            old.assertCurrent();
            changes.fire();
            assert.throws(() => old.assertCurrent(), /Compiler paths changed/);
            const current = new CompilerPathDraft(changes.event);
            try { current.assertCurrent(); } finally { current.dispose(); }
        } finally { old.dispose(); changes.dispose(); }
    });

    test('a settings update during dialogs is retained instead of overwritten', async () => {
        const changes = new vscode.EventEmitter<void>();
        const config = vscode.workspace.getConfiguration('xtc.compiler');
        const previous = config.inspect('sourceModules')?.workspaceValue;
        const replacement = [{ name: 'DraftOwnership', uri: 'file:///draft-ownership.x' }];
        const draft = new CompilerPathDraft(changes.event);
        try {
            await config.update('sourceModules', replacement, vscode.ConfigurationTarget.Workspace);
            assert.throws(() => draft.assertCurrent(), /Compiler paths changed/);
            assert.deepStrictEqual(vscode.workspace.getConfiguration('xtc.compiler').get('sourceModules'), replacement);
        } finally {
            draft.dispose(); changes.dispose();
            await config.update('sourceModules', previous, vscode.ConfigurationTarget.Workspace);
        }
    });

    test('disposed dialogs cannot publish their draft', () => {
        const changes = new vscode.EventEmitter<void>();
        const draft = new CompilerPathDraft(changes.event);
        draft.dispose();
        assert.throws(() => draft.assertCurrent(), /Compiler paths changed/);
        changes.dispose();
    });
});
