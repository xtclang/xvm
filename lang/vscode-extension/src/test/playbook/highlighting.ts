import * as assert from 'node:assert';
import * as fs from 'node:fs/promises';
import * as path from 'node:path';
import * as vscode from 'vscode';
import { SemanticTokens, SemanticTokensOptions } from 'vscode-languageclient/node';
import { WorkbenchUi } from '../workbenchUi';
import { captureColorTheme } from './colors';
import { client, diagnostics, eventually, noErrors, playbook } from './support';

async function tokens(document: vscode.TextDocument) {
    const legend = (client().initializeResult!.capabilities.semanticTokensProvider as SemanticTokensOptions).legend;
    const result = await client().sendRequest<SemanticTokens | null>('textDocument/semanticTokens/full', { textDocument: { uri: document.uri.toString() } });
    const data = result?.data ?? [];
    let line = 0;
    let column = 0;
    return Array.from({ length: data.length / 5 }, (_, index) => {
        const [lines, columns, length, type, mask] = data.slice(index * 5, index * 5 + 5);
        line += lines;
        column = (lines ? 0 : column) + columns;
        assert.ok(line < document.lineCount && column + length <= document.lineAt(line).text.length, 'Current-source token span');
        const start = document.offsetAt(new vscode.Position(line, column));
        return { start, end: start + length, text: document.getText().slice(start, start + length), type: legend.tokenTypes[type],
            modifiers: legend.tokenModifiers.filter((_, bit) => mask & (1 << bit)) };
    });
}

/** Read rendered attributes per character, independently of Monaco's span splits. */
async function lexicalStyles(ui: WorkbenchUi, anchors: string[]): Promise<string[][]> {
    return eventually(() => ui.page.locator('.editor-group-container.active .view-lines .view-line').evaluateAll((lines, wanted) => {
        const rows = lines.map(line => {
            const walker = line.ownerDocument.createTreeWalker(line, line.ownerDocument.defaultView!.NodeFilter.SHOW_TEXT);
            let text = '';
            const styles: string[] = [];
            while (walker.nextNode()) {
                const node = walker.currentNode;
                const value = (node.textContent ?? '').replace(/\u00a0/g, ' ');
                const css = line.ownerDocument.defaultView!.getComputedStyle(node.parentElement!);
                text += value;
                styles.push(...Array.from(value, () => `${css.color}/${css.fontStyle}/${css.fontWeight}/${css.textDecorationLine}`));
            }
            return { text, styles };
        });
        return wanted.map(anchor => {
            const row = rows.find(item => item.text.includes(anchor));
            const start = row?.text.indexOf(anchor) ?? -1;
            return row ? row.styles.slice(start, start + anchor.length) : [];
        });
    }, anchors), values => values.every((styles, index) => styles.length === anchors[index].length), 'Lexical fragments are rendered');
}

export function highlightingCases(): void {
    playbook('X276', async (workspace, data) => {
        const document = await workspace.open(data.file, data.source);
        await noErrors(document.uri);
        const actual = await tokens(document);
        for (const expected of data.tokens) {
            const start = data.source.indexOf(expected.anchor) + expected.offset;
            const token = actual.find(item => item.start === start);
            assert.ok(token, expected.anchor);
            assert.strictEqual(token.text, expected.text);
            assert.strictEqual(token.type, expected.type);
            assert.strictEqual(token.modifiers.includes('defaultLibrary'), expected.library);
        }
        const ui = await WorkbenchUi.connect();
        try { await ui.screenshot('X276-current-theme'); } finally { await ui.close(); }
    });

    playbook('X277', async (workspace, data) => {
        const document = await workspace.open(data.file, data.source);
        await noErrors(document.uri);
        const before = await tokens(document);
        for (const anchor of data.lexical) {
            const start = data.source.indexOf(anchor);
            assert.ok(!before.some(token => token.start < start + anchor.length && token.end > start), `No semantic overlay on ${anchor}`);
        }
        const ui = await WorkbenchUi.connect();
        try {
            for (const pair of data.matchingStyles) {
                const [control, expression] = await lexicalStyles(ui, [pair.left, pair.right]);
                assert.deepStrictEqual(expression.slice(0, pair.length), control.slice(0, pair.length),
                    `Expression bodies preserve lexical styling: ${pair.right}`);
            }
            const styles = await lexicalStyles(ui, data.lexical);
            await fs.writeFile(path.join(process.env.XTC_PLAYBOOK_REPORT_DIR!, 'X277-lexical-styles.json'), JSON.stringify({
                theme: vscode.workspace.getConfiguration('workbench').get('colorTheme'),
                semanticHighlighting: vscode.workspace.getConfiguration('editor', { uri: document.uri, languageId: document.languageId }).get('semanticHighlighting.enabled'),
                anchors: data.lexical, styles
            }, null, 2) + '\n');
            await captureColorTheme(ui, 'X277');
            for (const edit of data.recovery) {
                await workspace.replace(document, data.source.replace(edit.from, edit.to));
                await diagnostics(document.uri, values => values.some(item => item.severity === vscode.DiagnosticSeverity.Error), 'Damaged source reports an error');
                const current = await tokens(document);
                for (const token of current.filter(item => item.type !== 'comment')) assert.match(token.text, /^[A-Za-z_][A-Za-z_0-9]*$/);
                await workspace.replace(document, data.source);
                await noErrors(document.uri);
                assert.deepStrictEqual(await tokens(document), before, 'Repair restores exact classifications');
            }
        } finally {
            await ui.close();
        }
    });
}
