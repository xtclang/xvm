import * as assert from 'node:assert';
import * as vscode from 'vscode';
import { focusTestEditor } from '../native-focus';
import { WorkbenchUi } from '../workbenchUi';
import { catalog } from './shared';
import { client, diagnostics, eventually, noErrors, playbook, symbols } from './support';

const scenario = catalog.common.colorPrototype;

/** Capture the startup theme without repainting the workbench through other themes. */
export async function captureColorTheme(ui: WorkbenchUi, id: string): Promise<void> {
    const theme = vscode.workspace.getConfiguration('workbench').get<string>('colorTheme', 'current');
    await ui.screenshot(`${id}-${theme.replace(/ /g, '-')}`);
}

function bytes(color: vscode.Color): number[] {
    return [color.red, color.green, color.blue, color.alpha].map(channel => Math.round(channel * 255));
}

async function colors(document: vscode.TextDocument): Promise<vscode.ColorInformation[]> {
    const values = await vscode.commands.executeCommand<vscode.ColorInformation[]>('vscode.executeDocumentColorProvider', document.uri) ?? [];
    return values.sort((left, right) => left.range.start.compareTo(right.range.start));
}

async function renderedColors(ui: WorkbenchUi, expected: number[][]): Promise<void> {
    await eventually(() => ui.page.locator('.editor-group-container.active .colorpicker-color-decoration')
        .evaluateAll(elements => elements.map(element => {
            const css = element.ownerDocument.defaultView!.getComputedStyle(element).backgroundColor;
            const channels = css.match(/[\d.]+/g)?.map(Number) ?? [];
            return [...channels.slice(0, 3), Math.round((channels[3] ?? 1) * 255)];
        })), values => values.length === expected.length && values.every((color, index) =>
        color.every((channel, component) => Math.abs(channel - expected[index][component]) <= 1)),
    'Rendered swatches match the current constructor values');
}

/** Real workbench hover, sliders and source edits; never calls colorPresentation directly. */
async function openPicker(ui: WorkbenchUi, index: number): Promise<void> {
    const decoration = ui.page.locator('.editor-group-container.active .colorpicker-color-decoration').nth(index);
    await decoration.hover({ timeout: 10_000 });
    await ui.page.locator('.colorpicker-body:visible').waitFor({ state: 'visible', timeout: 10_000 });
}

async function changeSlider(ui: WorkbenchUi, name: 'opacity' | 'hue'): Promise<number[]> {
    const strip = ui.page.locator(`.colorpicker-body:visible .${name}-strip`);
    const bounds = await strip.boundingBox();
    assert.ok(bounds);
    const presentation = ui.page.locator('.colorpicker-header:visible .picked-color-presentation');
    const previous = await presentation.textContent();
    // Renderer input leaves the desktop pointer untouched. One click is one native edit.
    await strip.click({ position: { x: bounds.width / 2, y: bounds.height / 2 }, timeout: 5_000 });
    const css = await ui.page.locator('.colorpicker-header:visible .picked-color')
        .evaluate(element => element.ownerDocument.defaultView!.getComputedStyle(element).backgroundColor);
    const rgba = css.match(/[\d.]+/g)?.map(Number);
    assert.ok(rgba && rgba.length >= 3, `Native picker color: ${css}`);
    const displayed = await eventually(() => presentation.textContent(),
        value => value !== previous && /^RGBA\(\d+, \d+, \d+, \d+\)$/.test(value ?? ''),
        'Native picker receives the changed color presentation');
    const selected = displayed!.match(/\d+/g)!.map(Number);
    const rendered = [...rgba.slice(0, 3), Math.round((rgba[3] ?? 1) * 255)];
    // CSS serializes alpha with reduced precision. The picker label gives exact byte values;
    // independently compare its channels to the rendered selection within one byte.
    assert.ok(selected.every((channel, index) => Math.abs(channel - rendered[index]) <= 1), `${displayed}; rendered ${css}`);
    return selected;
}

async function dismiss(ui: WorkbenchUi): Promise<void> {
    await vscode.commands.executeCommand('editor.action.hideHover');
    await ui.page.locator('.colorpicker-body:visible').waitFor({ state: 'hidden', timeout: 5_000 });
}

function expectedSource(source: string, anchor: string, rgba: number[]): string {
    const [red, green, blue, alpha] = rgba;
    const replacement = anchor.includes('blue =')
        ? `new Rgba(blue = ${blue}, red = ${red}, alpha = ${alpha}, green = ${green})`
        : `new Rgba(${red}, ${green}, ${blue}${alpha === 255 ? '' : `, alpha = ${alpha}`})`;
    assert.ok(source.includes(anchor));
    return source.replace(anchor, replacement);
}

export function colorCases(): void {
    for (const id of ['X273', 'X274', 'X275'] as const) {
        playbook(id, async (workspace, data) => {
            assert.strictEqual(client().initializeResult?.capabilities.colorProvider, true, 'Launcher enables the color prototype for this selection');
            const document = await workspace.open(scenario.file, scenario.source);
            await noErrors(document.uri);
            const editor = await vscode.window.showTextDocument(document);
            const config = vscode.workspace.getConfiguration('editor', document.uri);
            const previousDecorators = config.inspect<boolean>('colorDecorators')?.workspaceValue;
            await config.update('colorDecorators', true, vscode.ConfigurationTarget.Workspace);
            const ui = await WorkbenchUi.connect();
            const rendered = () => ui.page.locator('.editor-group-container.active .colorpicker-color-decoration').count();
            try {
                await focusTestEditor(document);
                const initial = await colors(document);
                assert.deepStrictEqual(initial.map(info => ({ anchor: document.getText(info.range), rgba: bytes(info.color) })), scenario.colors);
                await eventually(rendered, count => count === 2, 'Exactly two native color swatches, no dynamic/string swatches');
                await renderedColors(ui, scenario.colors.map(color => color.rgba));
                await ui.screenshot(`${id}-swatches`);
                if (data.mode === 'recovery') {
                    await workspace.replace(document, scenario.source.replace(scenario.invalid.from, scenario.invalid.to));
                    await diagnostics(document.uri, values => values.some(value => value.severity === vscode.DiagnosticSeverity.Error), 'Out-of-range channel is diagnosed');
                    assert.deepStrictEqual(await colors(document), []);
                    await eventually(rendered, count => count === 0, 'Failed compilation retires rendered color swatches');
                    await workspace.replace(document, scenario.source);
                    await noErrors(document.uri);
                    await eventually(rendered, count => count === 2, 'Repair restores both native color swatches');
                    await renderedColors(ui, scenario.colors.map(color => color.rgba));
                    return;
                }
                await openPicker(ui, 0);
                await ui.screenshot(`${id}-picker`);
                if (data.mode === 'history') {
                    await dismiss(ui);
                    assert.strictEqual(document.getText(), scenario.source, 'Dismissal without selecting a color preserves all source');
                    await openPicker(ui, 0);
                }
                const changed = await changeSlider(ui, 'opacity');
                assert.notStrictEqual(changed[3], 255, 'The native alpha control changed opacity');
                const expected = expectedSource(scenario.source, scenario.colors[0].anchor, changed);
                await eventually(async () => document.getText(), text => text === expected, 'Native picker applies exact source-preserving alpha edit');
                await dismiss(ui);
                await noErrors(document.uri);
                assert.deepStrictEqual(bytes((await colors(document))[0].color), changed);
                await renderedColors(ui, [changed, scenario.colors[1].rgba]);
                if (data.mode === 'history') {
                    await focusTestEditor(document);
                    await vscode.commands.executeCommand('undo');
                    await eventually(async () => document.getText(), text => text === scenario.source, 'One Undo restores the original constructor');
                    await symbols(document);
                    assert.deepStrictEqual(bytes((await colors(document))[0].color), scenario.colors[0].rgba);
                    await renderedColors(ui, scenario.colors.map(color => color.rgba));
                    await vscode.commands.executeCommand('redo');
                    await eventually(async () => document.getText(), text => text === expected, 'One Redo restores the picker edit');
                    await noErrors(document.uri);
                    await renderedColors(ui, [changed, scenario.colors[1].rgba]);
                } else {
                    await openPicker(ui, 1);
                    const named = await changeSlider(ui, 'hue');
                    assert.notDeepStrictEqual(named.slice(0, 3), scenario.colors[1].rgba.slice(0, 3));
                    const namedSource = expectedSource(expected, scenario.colors[1].anchor, named);
                    await eventually(async () => document.getText(), text => text === namedSource, 'Named arguments keep their order and labels');
                    await dismiss(ui);
                    await noErrors(document.uri);
                    assert.deepStrictEqual(bytes((await colors(document))[1].color), named);
                    await renderedColors(ui, [changed, named]);
                    await captureColorTheme(ui, id);
                }
                editor.selection = new vscode.Selection(0, 0, 0, 0);
            } finally {
                await vscode.commands.executeCommand('editor.action.hideHover');
                await ui.close();
                await config.update('colorDecorators', previousDecorators, vscode.ConfigurationTarget.Workspace);
            }
        });
    }
}
