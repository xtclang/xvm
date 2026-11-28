import * as assert from 'node:assert';
import * as path from 'node:path';
import { chromium, Browser, Locator, Page } from 'playwright-core';
import { answered, TRY_MS, untilTakes, whileUnderWay, type UnderWay } from './uiWait';

/** Test-only attachment to the isolated Electron renderer; never controls the desktop pointer. */
export class WorkbenchUi {
    private constructor(private readonly browser: Browser, readonly page: Page) {}

    static async connect(): Promise<WorkbenchUi> {
        const port = Number(process.env.XTC_PLAYBOOK_UI_PORT);
        assert.ok(Number.isInteger(port) && port > 0, 'UI assertions require the isolated launcher endpoint');
        // one connection, as long as it has not answered, or anything else is under way, and the hang's bound at most
        const connecting = chromium.connectOverCDP(`http://127.0.0.1:${port}`, { timeout: 0 });
        const browser = await whileUnderWay(() => connecting, () => true, "The window's DevTools connection");
        try {
            const pages = browser.contexts().flatMap(context => context.pages())
                .filter(page => page.url().includes('/workbench/'));
            assert.strictEqual(pages.length, 1, 'Exactly one workbench belongs to this isolated test instance');
            return new WorkbenchUi(browser, pages[0]);
        } catch (error) { await browser.close(); throw error; }
    }

    async screenshot(name: string): Promise<void> {
        await this.page.screenshot({ path: path.join(process.env.XTC_PLAYBOOK_REPORT_DIR!, `${name}.png`) });
    }

    /**
     * Does [action], a Playwright action that waits for its target at most [TRY_MS], again until it takes, while the
     * workbench, or what [underWay] adds, does anything that could make it take ([untilTakes]); an action that times out
     * has not acted, so a try again acts once.
     */
    async tries(what: string, action: () => Promise<unknown>, underWay: UnderWay = { server: true }): Promise<void> {
        await untilTakes(what, action, underWay);
    }

    /** Waits, [tries]'s way, until [locator] shows, or with [hidden] until it no longer does; [what] names it. */
    async shows(locator: Locator, what: string, hidden = false, underWay: UnderWay = { server: true }): Promise<void> {
        await this.tries(`${what} ${hidden ? 'closes' : 'shows'}`, () => locator.waitFor({ state: hidden ? 'hidden' : 'visible', timeout: TRY_MS }), underWay);
    }

    async cancelReferences(): Promise<void> {
        const notification = this.page.locator('.notification-list-item').filter({ hasText: 'Finding references' });
        const button = notification.getByRole('button', { name: 'Cancel', exact: true });
        await this.shows(button, "Finding references' Cancel");
        await this.screenshot('X145-before-visible-cancel');
        // Trusted renderer input activates the actual workbench control, once. No token.cancel fallback.
        await this.tries('Cancel takes the click', () => button.click({ timeout: TRY_MS }));
    }

    async inlayLabels(): Promise<string[]> {
        return this.page.locator('.monaco-editor:visible .view-lines [class*="dyn-rule-"]').allTextContents();
    }

    /**
     * Gives the keyboard to the active editor's text, again until it has it, as [tries] does, each try after [show],
     * which shows the editor, as run-GVNl3L's X194 found it not showing within the 5 s that a single focus waited.
     */
    async focusEditor(show: () => Thenable<unknown> = async () => undefined): Promise<void> {
        // Focus the actual text input rather than an Output panel left open by a preceding case.
        // DOM focus does not move the desktop pointer and never repeats an edit/history command.
        const input = this.page.locator('.editor-group-container.active .monaco-editor:visible :is(textarea.inputarea, .native-edit-context)');
        // Only the workbench's events count: what the server does draws no editor.
        await this.tries('The source editor takes the keyboard', async () => {
            await answered(show(), 'Showing the document');
            await input.focus({ timeout: TRY_MS });
            assert.ok(await answered(input.evaluate(element => element === element.ownerDocument.activeElement), 'The focus check'),
                'Source editor owns keyboard focus');
        }, {});
    }

    async inlineText(): Promise<string> {
        return (await this.page.locator('.editor-group-container.active .monaco-editor:visible .ghost-text-decoration').allTextContents()).join('');
    }

    async close(): Promise<void> { await this.browser.close(); }
}
