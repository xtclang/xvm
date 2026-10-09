import * as assert from 'node:assert';
import * as path from 'node:path';
import { createServer } from 'node:net';
import { chromium, Browser, Page } from 'playwright-core';

/** Test-only attachment to the isolated Electron renderer; never controls the desktop pointer. */
export class WorkbenchUi {
    private constructor(private readonly browser: Browser, readonly page: Page) {}

    static async connect(): Promise<WorkbenchUi> {
        const port = Number(process.env.XTC_PLAYBOOK_UI_PORT);
        assert.ok(Number.isInteger(port) && port > 0, 'UI assertions require the isolated launcher endpoint');
        const browser = await chromium.connectOverCDP(`http://127.0.0.1:${port}`, { timeout: 15_000 });
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

    async cancelReferences(): Promise<void> {
        const notification = this.page.locator('.notification-list-item').filter({ hasText: 'Finding references' });
        const button = notification.getByRole('button', { name: 'Cancel', exact: true });
        await button.waitFor({ state: 'visible', timeout: 10_000 });
        await this.screenshot('X145-before-visible-cancel');
        // Trusted renderer input activates the actual workbench control, once. No token.cancel fallback.
        await button.click({ timeout: 5_000 });
    }

    async inlayLabels(): Promise<string[]> {
        return this.page.locator('.monaco-editor:visible .view-lines [class*="dyn-rule-"]').allTextContents();
    }

    async focusEditor(): Promise<void> {
        // Focus the actual text input rather than an Output panel left open by a preceding case.
        // DOM focus does not move the desktop pointer and never repeats an edit/history command.
        const input = this.page.locator('.editor-group-container.active .monaco-editor:visible :is(textarea.inputarea, .native-edit-context)');
        await input.focus({ timeout: 5_000 });
        assert.ok(await input.evaluate(element => element === element.ownerDocument.activeElement), 'Source editor owns keyboard focus');
    }

    async inlineText(): Promise<string> {
        return (await this.page.locator('.editor-group-container.active .monaco-editor:visible .ghost-text-decoration').allTextContents()).join('');
    }

    async close(): Promise<void> { await this.browser.close(); }
}

/** Let the OS allocate a loopback port; fail if another process wins the short launch race. */
export async function workbenchPort(): Promise<number> {
    const server = createServer();
    await new Promise<void>((resolve, reject) => { server.once('error', reject); server.listen(0, '127.0.0.1', resolve); });
    const address = server.address();
    assert.ok(address && typeof address !== 'string');
    await new Promise<void>((resolve, reject) => server.close(error => error ? reject(error) : resolve()));
    return address.port;
}
