import { execFile } from 'node:child_process';
import { setTimeout as delay } from 'node:timers/promises';
import { promisify } from 'node:util';
import { chromium, type Browser, type Locator, type Page } from 'playwright-core';
import { HANG_MS } from './tour';

/** The recording's window: VS Code's, at the top left of the screen, above the caption band the composer adds. */
export const WIDTH = 1920;
export const HEIGHT = 936;

/** Where something is on the captured screen, in its pixels. */
export interface Box {
    x: number;
    y: number;
    width: number;
    height: number;
}

export interface Point {
    x: number;
    y: number;
}

/**
 * Whether the tour runs for a recording of the screen, `XTC_RECORD_VIDEO=1`: then its steps act by input a viewer
 * sees, the pointer moving to what it clicks and keys for commands, and type at a pace a viewer reads.
 */
export const RECORDING = process.env.XTC_RECORD_VIDEO === '1';

/**
 * What the tour did by input, at [time], in milliseconds since the epoch, the clock of the screen's recording, as the
 * IntelliJ tour's tour.json has it for the composer: the keys pressed, the text typed, what was clicked, at [x] and [y]
 * on the screen, or a command run without input, with the [label] that the step's badge shows, such as `Shift+Alt+O`,
 * `click: Run` or the text.
 */
export interface TourAction {
    time: number;
    kind: 'key' | 'type' | 'click' | 'action';
    label: string;
    x?: number;
    y?: number;
}

/** A key as a viewer reads it: `Control+Shift+KeyO` is `Ctrl+Shift+O`. */
function keyLabel(key: string): string {
    const names: Record<string, string> = {
        Control: 'Ctrl', ArrowRight: 'Right', ArrowLeft: 'Left', ArrowUp: 'Up', ArrowDown: 'Down', Slash: '/',
        BracketRight: ']', BracketLeft: '[', Semicolon: ';', Period: '.', Escape: 'Esc', Backquote: '`',
    };
    return key.split('+').map(part => names[part] ?? part.replace(/^(?:Key|Digit)(.)$/, '$1')).join('+');
}

/** How long a typed key takes while recording, which a viewer reads, and a pause after each pressed key or chord. */
const TYPING_MS = 70;
const KEY_PAUSE_MS = 250;
/** The pointer's moves to what it clicks, while recording: so many steps of so long each. */
const POINTER_STEPS = 24;
const POINTER_STEP_MS = 20;

/**
 * How long the driver gets to answer an evaluate of nothing, which it answers at once while its connection works, as
 * the window does over a connection of its own: a check of a call that has long been pending, never a ready signal.
 */
export const DRIVER_CHECK_MS = 30_000;

/** Whether [work] ends within [ms], whichever way. */
async function answers<T>(work: Promise<T>, ms: number): Promise<T | undefined> {
    const timer = new AbortController();
    try {
        return await Promise.race([work.catch(() => undefined), delay(ms, undefined, { signal: timer.signal }).catch(() => undefined)]);
    } finally {
        timer.abort();
    }
}

const run = promisify(execFile);

/**
 * What the tour does and reads in VS Code's window, through the page of its workbench, as Playwright drives it over
 * the window's DevTools port: keys and clicks reach the page as the user's would, without the desktop's pointer or
 * keyboard; and the screen of the virtual display, which [capture] takes.
 *
 * A subject, which a step highlights and waits for, is named by [locate]'s names: `editor`, `caret`, `hover`,
 * `suggest`, `parameterHints`, `quickInput`, `peek`, `menu`, `panel`, `sidebar`, `notification`, `debugToolbar`,
 * `line:<n>` and `gutter:<n>` of the active editor, `view:<title>` for a view of the side bar or the panel,
 * `status:<text>` for an item of the status bar, `text:<text>`, `css:<selector>`, or `none`.
 */
export class TourUi {
    /** Where the window's page is on the screen, which the capture starts at. */
    private origin: Point = { x: 0, y: 0 };
    /** Where the latest click was, which the step's frame points at. */
    private clicked: Point | undefined;
    /** What the tour did by input, which [actions] answers. */
    private readonly log: TourAction[] = [];
    /** The windows the display showed as the tour started, beside the workbench's, which are no step's. */
    private baseline = new Set<string>();

    /**
     * How the TourUi's own waits wait, [Tour.until]'s way: until [condition] holds, failing with [message] once nothing
     * that could make it hold is under way. Until the tour sets it, a plain poll bounded by [HANG_MS].
     */
    waiter: (message: string, condition: () => Promise<boolean> | boolean) => Promise<void> = async (message, condition) => {
        const deadline = Date.now() + HANG_MS;
        while (!await Promise.resolve().then(condition).catch(() => false)) {
            if (Date.now() > deadline) throw new Error(`${message}, not within ${HANG_MS / 1_000} s`);
            await delay(250);
        }
    };

    private constructor(private browser: Browser, private current: Page, private readonly port: number) {}

    static async connect(): Promise<TourUi> {
        const port = Number(process.env.XTC_PLAYBOOK_UI_PORT);
        if (!Number.isInteger(port) || port <= 0) throw new Error('The tour needs the launcher\'s DevTools port');
        const [browser, page] = await TourUi.workbench(port);
        return new TourUi(browser, page, port);
    }

    /** The one workbench page of the tour's window, over a new connection to its DevTools [port]. */
    private static async workbench(port: number): Promise<[Browser, Page]> {
        const browser = await chromium.connectOverCDP(`http://127.0.0.1:${port}`, { timeout: 30_000 });
        const pages = browser.contexts().flatMap(context => context.pages()).filter(page => page.url().includes('/workbench/'));
        if (pages.length !== 1) {
            await browser.close();
            throw new Error(`Expected the one workbench of the tour's window, found ${pages.length}`);
        }
        return [browser, pages[0]];
    }

    /** The workbench's page, as the driver's connection has it. */
    get page(): Page {
        return this.current;
    }

    async close(): Promise<void> {
        await answers(this.browser.close(), DRIVER_CHECK_MS);
    }

    /**
     * Whether the driver's connection has stopped answering while the window still does: Playwright's evaluate of
     * nothing gets no answer within [DRIVER_CHECK_MS], which a working connection gives at once, and the page, over a
     * DevTools connection of its own, answers. A window that answers neither is busy or hung itself, not the driver.
     */
    async driverLost(): Promise<boolean> {
        if (await answers(this.current.evaluate('1'), DRIVER_CHECK_MS)) return false;
        return this.windowAnswers();
    }

    /** Whether the workbench's page answers an evaluate over a DevTools connection of its own, not the driver's. */
    async windowAnswers(): Promise<boolean> {
        return (await this.evaluateDirectly('1')) === 1;
    }

    /**
     * Evaluates [expression] in the workbench's page over a DevTools connection of its own, not the driver's, within
     * [DRIVER_CHECK_MS]; undefined when it does not answer.
     */
    async evaluateDirectly(expression: string): Promise<unknown> {
        const targets = await answers(fetch(`http://127.0.0.1:${this.port}/json/list`).then(response => response.json()) as Promise<{ type: string; url: string; webSocketDebuggerUrl: string }[]>,
            DRIVER_CHECK_MS);
        const target = targets ? targets.find(each => each.type === 'page' && each.url.includes('/workbench/')) : undefined;
        if (!target) return undefined;
        return new Promise(resolve => {
            const socket = new WebSocket(target.webSocketDebuggerUrl);
            const timer = setTimeout(() => { socket.close(); resolve(undefined); }, DRIVER_CHECK_MS);
            socket.onopen = () => socket.send(JSON.stringify({ id: 1, method: 'Runtime.evaluate', params: { expression, returnByValue: true } }));
            socket.onmessage = event => {
                const message = JSON.parse(String(event.data)) as { id?: number; result?: { result?: { value?: unknown } } };
                if (message.id !== 1) return;
                clearTimeout(timer);
                socket.close();
                resolve(message.result?.result?.value);
            };
            socket.onerror = () => { clearTimeout(timer); resolve(undefined); };
        });
    }

    /** Connects the driver to the window again, after its connection stopped answering; false when it cannot. */
    async reconnect(): Promise<boolean> {
        const lost = this.browser;
        try {
            [this.browser, this.current] = await TourUi.workbench(this.port);
        } catch (failure) {
            console.log(`[tour] the driver cannot connect again: ${String(failure)}`);
            return false;
        }
        void answers(lost.close(), DRIVER_CHECK_MS);
        return true;
    }

    /**
     * Places VS Code's window at the top left of the virtual display, [WIDTH] by [HEIGHT], as the recording shows it.
     * Without a display, as on a desktop, the window stays as it is and the page is captured instead of the screen.
     */
    async layout(): Promise<void> {
        if (!process.env.DISPLAY) return;
        const windows = (await run('xdotool', ['search', '--onlyvisible', '--name', 'Visual Studio Code'])).stdout
            .split('\n').map(line => line.trim()).filter(Boolean);
        for (const window of windows) {
            await run('xdotool', ['windowmove', '--sync', window, '0', '0']).catch(() => undefined);
            await run('xdotool', ['windowsize', '--sync', window, String(WIDTH), String(HEIGHT)]).catch(() => undefined);
        }
        const deadline = Date.now() + 15_000;
        while (Date.now() < deadline) {
            const size = await this.page.evaluate('[innerWidth, innerHeight]') as number[];
            if (size[0] === WIDTH && size[1] === HEIGHT) break;
            await new Promise(resolve => setTimeout(resolve, 250));
        }
        const geometry = windows.length
            ? (await run('xdotool', ['getwindowgeometry', '--shell', windows[0]])).stdout : '';
        this.origin = {
            x: Number(/^X=(\d+)/m.exec(geometry)?.[1] ?? 0),
            y: Number(/^Y=(\d+)/m.exec(geometry)?.[1] ?? 0),
        };
        this.baseline = new Set((await this.strays()).map(window => window.id));
    }

    /**
     * The windows on the virtual display other than the workbench's, by their IDs and names: a developer tools window,
     * or a dialog that a step left open, which would stay in the recording.
     */
    async strays(): Promise<{ id: string; name: string }[]> {
        if (!process.env.DISPLAY) return [];
        const ids = (await run('xdotool', ['search', '--onlyvisible', '--name', '.']).catch(() => ({ stdout: '' }))).stdout
            .split('\n').map(line => line.trim()).filter(Boolean);
        const windows = await Promise.all(ids.map(async id => ({
            id, name: (await run('xdotool', ['getwindowname', id]).catch(() => ({ stdout: '' }))).stdout.trim(),
        })));
        return windows.filter(window => window.name && !window.name.includes('Visual Studio Code') && !this.baseline.has(window.id));
    }


    /** Closes the window [id], as its close button does. */
    async closeWindow(id: string): Promise<void> {
        await run('xdotool', ['windowclose', id]).catch(() => undefined);
    }

    /** Captures the window into the PNG [file]: the virtual display's screen where it is, or else the page. */
    async capture(file: string): Promise<void> {
        if (process.env.DISPLAY) {
            const crop = `${WIDTH}x${HEIGHT}+${this.origin.x}+${this.origin.y}`;
            const done = await run('import', ['-window', 'root', '-crop', crop, '+repage', file]).then(() => true, () => false);
            if (done) return;
        }
        await this.page.screenshot({ path: file });
    }

    /** The subject that [focus] names, as the first of its matches that shows. */
    locator(focus: string): Locator | undefined {
        const [kind, ...rest] = focus.split(':');
        const value = rest.join(':');
        const page = this.page;
        const editor = '.editor-group-container.active .monaco-editor';
        const css = (selector: string) => page.locator(selector).filter({ visible: true }).first();
        switch (kind) {
            case 'none': return undefined;
            case 'editor': return css(`${editor} .overflow-guard`);
            case 'caret': return css(`${editor} .cursors-layer .cursor`);
            case 'hover': return css('.monaco-hover:not(.hidden)');
            case 'suggest': return css('.suggest-widget.visible');
            case 'parameterHints': return css('.parameter-hints-widget.visible');
            case 'quickInput': return css('.quick-input-widget');
            case 'peek': return css('.peekview-widget');
            case 'menu': return css('.context-view .monaco-menu, .action-widget');
            case 'panel': return css('#workbench\\.parts\\.panel');
            case 'sidebar': return css('#workbench\\.parts\\.sidebar');
            case 'notification': return css('.notifications-toasts .notification-toast');
            case 'debugToolbar': return css('.debug-toolbar');
            case 'view': return page.locator('.pane').filter({ has: page.locator('.pane-header', { hasText: value }) }).filter({ visible: true }).first();
            case 'status': return page.locator('#workbench\\.parts\\.statusbar .statusbar-item').filter({ hasText: value }).filter({ visible: true }).first();
            case 'text': return page.getByText(value).filter({ visible: true }).first();
            case 'css': return css(value);
            default: return undefined;
        }
    }

    /** Whether the subject that [focus] names shows; `none`, and a line the editor shows, always do. */
    async showing(focus: string): Promise<boolean> {
        if (focus === 'none') return true;
        return (await this.locate(focus)) !== undefined;
    }

    /** Where the subject that [focus] names is on the captured screen, or undefined while it does not show. */
    async locate(focus: string): Promise<Box | undefined> {
        const [kind, value] = focus.split(':');
        if (kind === 'line' || kind === 'gutter') return this.line(Number(value), kind === 'gutter');
        const box = await this.locator(focus)?.boundingBox({ timeout: 1_000 }).catch(() => null);
        return box && box.width > 0 && box.height > 0 ? box : undefined;
    }

    /** The 1-based [line] of the active editor, its text or its gutter, as it shows on the screen. */
    private async line(line: number, gutter: boolean): Promise<Box | undefined> {
        const editor = this.page.locator('.editor-group-container.active .monaco-editor').filter({ visible: true }).first();
        const number = editor.locator('.margin-view-overlays .line-numbers').filter({ hasText: new RegExp(`^\\s*${line}\\s*$`) }).first();
        const [area, numbered, margin] = await Promise.all([
            editor.locator('.overflow-guard').boundingBox({ timeout: 1_000 }).catch(() => null),
            number.boundingBox({ timeout: 1_000 }).catch(() => null),
            editor.locator('.margin').first().boundingBox({ timeout: 1_000 }).catch(() => null),
        ]);
        if (!area || !numbered) return undefined;
        return gutter && margin
            ? { x: margin.x, y: numbered.y, width: margin.width, height: numbered.height }
            : { x: area.x, y: numbered.y, width: area.width, height: numbered.height };
    }

    /** Where the frame of a step points: at the latest click, or else at the caret of the active editor. */
    async pointer(): Promise<Point | undefined> {
        if (this.clicked) return this.clicked;
        const caret = await this.locate('caret');
        return caret && { x: caret.x + caret.width / 2, y: caret.y + caret.height / 2 };
    }

    /** Forgets the latest click, as each step starts. */
    forget(): void {
        this.clicked = undefined;
    }

    /** What the tour did by input, in order: every key, click and text of its steps and checks. */
    get actions(): readonly TourAction[] {
        return this.log;
    }

    /** Logs the input of [kind] and [label] as done now, at [point] of the page for a click. */
    note(kind: TourAction['kind'], label: string, point?: Point): void {
        this.log.push({ time: Date.now(), kind, label, ...(point && this.onScreen(point)) });
    }

    /** The window's bounds on the screen, `x,y,width,height`, which the composer crops a step's clip to. */
    frame(): string {
        return `${this.origin.x},${this.origin.y},${WIDTH},${HEIGHT}`;
    }

    /** The text that the subject [focus] names shows, or nothing while it does not show. */
    async text(focus: string): Promise<string> {
        return await this.locator(focus)?.innerText({ timeout: 2_000 }).catch(() => '') ?? '';
    }

    /**
     * Presses each of [keys], as Playwright names them: `Enter`, `Control+Shift+P`, `Control+Alt+X`; while recording,
     * the screen holds a moment after them, for the viewer, but not between them, which a chord's keys are.
     */
    async press(...keys: string[]): Promise<void> {
        this.note('key', keys.map(keyLabel).join(', '));
        for (const key of keys) await this.page.keyboard.press(key);
        if (RECORDING) await delay(KEY_PAUSE_MS);
    }

    /** Types [text] as keys, as the user does, into what has the keyboard's focus; while recording, at a reading pace. */
    async type(text: string): Promise<void> {
        this.note('type', text.trim() || 'Enter');
        await this.page.keyboard.type(text, { delay: RECORDING ? TYPING_MS : 15 });
    }

    /**
     * Clicks the subject [focus] names, or [locator], with the [button] of the mouse; while recording, the screen's
     * pointer moves to it first, as the user's does.
     */
    async click(target: string | Locator, button: 'left' | 'right' = 'left'): Promise<void> {
        const locator = typeof target === 'string' ? this.locator(target) : target;
        if (!locator) throw new Error(`Nothing to click for ${String(target)}`);
        await this.until(locator, String(typeof target === 'string' ? target : 'What the step clicks'));
        if (RECORDING) await locator.scrollIntoViewIfNeeded({ timeout: HANG_MS });
        const box = await locator.boundingBox();
        if (box) {
            this.clicked = { x: box.x + box.width / 2, y: box.y + box.height / 2 };
            if (RECORDING) await this.movePointer(this.clicked);
        }
        const label = typeof target === 'string' ? target
            : (await locator.innerText({ timeout: 1_000 }).catch(() => '')).split('\n').map(line => line.trim()).find(Boolean)?.slice(0, 80);
        this.note('click', `${button === 'right' ? 'right-click' : 'click'}: ${label ?? ''}`.trim(), this.clicked);
        await locator.click({ button, timeout: HANG_MS });
    }

    /** Waits, [waiter]'s way, until [locator] shows, or with [hidden] until it no longer does; [what] names it. */
    private async until(locator: Locator, what: string, hidden = false): Promise<void> {
        await this.waiter(hidden ? `${what} closes` : `${what} shows`, async () => await locator.isVisible() !== hidden);
    }

    /** Where [point] of the page is on the screen. */
    private onScreen(point: Point): Point {
        return { x: Math.round(this.origin.x + point.x), y: Math.round(this.origin.y + point.y) };
    }

    /**
     * Moves the screen's pointer from where it is to [point] of the page, eased over about half a second, as a hand
     * does: the input Playwright sends the page moves no pointer, and the recording shows the screen's.
     */
    private async movePointer(point: Point): Promise<void> {
        if (!process.env.DISPLAY) return;
        const to = this.onScreen(point);
        const from = await run('xdotool', ['getmouselocation', '--shell']).then(({ stdout }) => ({
            x: Number(/^X=(\d+)/m.exec(stdout)?.[1] ?? to.x),
            y: Number(/^Y=(\d+)/m.exec(stdout)?.[1] ?? to.y),
        }), () => to);
        const moves = Array.from({ length: POINTER_STEPS }, (_, index) => {
            const done = (index + 1) / POINTER_STEPS;
            const eased = done * done * (3 - 2 * done);
            return ['mousemove', String(Math.round(from.x + (to.x - from.x) * eased)), String(Math.round(from.y + (to.y - from.y) * eased)),
                'sleep', String(POINTER_STEP_MS / 1_000)];
        });
        await run('xdotool', moves.flat()).catch(() => undefined);
    }

    /** Runs the command whose title in the Command Palette is [title], as the user does: F1, its title, and Enter. */
    async palette(title: string): Promise<void> {
        await this.press('F1');
        await this.pick(title);
    }

    /**
     * Opens the file named [name] by Quick Open, as the user does: Ctrl+P, its name, and Enter, once a row shows it;
     * ends once the box has closed.
     */
    async quickOpen(name: string): Promise<void> {
        await this.press('Control+P');
        const widget = this.page.locator('.quick-input-widget');
        await this.until(widget, 'Quick Open');
        await this.type(name);
        await this.until(widget.locator('.monaco-list-row', { hasText: name }).first(), `Quick Open's row of ${name}`);
        await this.press('Enter');
        await this.until(widget, 'Quick Open', true);
    }

    /** Puts the active editor's caret at its 1-based [line] and [column] by Go to Line, as the user does: Ctrl+G, `line:column`, and Enter. */
    async goTo(line: number, column: number): Promise<void> {
        await this.press('Control+G');
        const widget = this.page.locator('.quick-input-widget');
        await this.until(widget, 'Go to Line');
        await this.type(`${line}:${column}`);
        await this.press('Enter');
        await this.until(widget, 'Go to Line', true);
    }

    /** Clicks the item of the open context menu whose label is [label]. */
    async menu(label: string): Promise<void> {
        // a context menu shows all its items at once: one without the item, or with it disabled, will not get it
        const menu = this.page.locator('.context-view .monaco-menu').filter({ visible: true }).first();
        await this.until(menu, 'The context menu');
        const item = menu.locator('.action-item').filter({ hasText: label }).filter({ visible: true }).first();
        if (await item.count() === 0 || (await item.getAttribute('class'))?.includes('disabled')) {
            throw new Error(`The menu has no ${label} to choose: ${(await menu.innerText().catch(() => '')).split('\n').filter(Boolean).join(' | ')}`);
        }
        await this.click(item);
    }

    /** Gives the keyboard to the text of the active editor, as clicking into it does. */
    async focusEditor(): Promise<void> {
        const input = this.page.locator('.editor-group-container.active .monaco-editor:visible :is(textarea.inputarea, .native-edit-context)');
        await input.first().focus({ timeout: HANG_MS });
    }

    /** Whether the keyboard is in [target], as it is once a box or widget that opens has taken it. */
    async focusedIn(target: Locator): Promise<boolean> {
        // the page's script, as the extension host has no DOM types
        return await target.first().evaluate((element: unknown) => (element as { contains(node: unknown): boolean })
            .contains((globalThis as unknown as { document: { activeElement: unknown } }).document.activeElement), undefined, { timeout: 1_000 })
            .catch(() => false);
    }

    /** The rows of the open quick input, such as Quick Open's or a picker's. */
    async quickItems(): Promise<string[]> {
        return this.page.locator('.quick-input-widget .monaco-list-row').filter({ visible: true }).allInnerTexts().catch(() => []);
    }

    /** Waits for the quick input, types [filter] into it and picks its row with [label] by Enter. */
    async pick(label: string, filter = label): Promise<void> {
        const widget = this.page.locator('.quick-input-widget');
        await this.until(widget, 'The picker');
        // a picker has rows; an input box that closes as the picker opens has none, and must not take the filter
        await this.until(widget.locator('.monaco-list-row').first(), "The picker's rows");
        // the filter goes to the picker's box once that has the keyboard: until then, as while the picker before it
        // still shows as it closes, the keys go where the keyboard was, such as an editor, which the first one edits
        if (filter) {
            await this.waiter('The picker takes the keyboard', () => this.focusedIn(widget.locator('input')));
            await this.type(filter);
        }
        const row = widget.locator('.monaco-list-row', { hasText: label }).first();
        await this.until(row, `The picker's row ${label}`);
        // Enter takes the focused row, which a recently used one that the filter also matches can be, as Tasks: Rerun
        // All Running Tasks is for Tasks: Run Task: the row with the label is clicked when it is not the focused one
        const focused = await widget.locator('.monaco-list-row.focused').first().innerText({ timeout: 1_000 }).catch(() => '');
        if (focused.includes(label)) await this.press('Enter');
        else await this.click(row);
    }

    /** The text the active terminal shows; the tour's terminal draws in the page, which has its rows' text. */
    async terminalText(): Promise<string> {
        return this.page.locator('.terminal-wrapper.active .xterm-rows, .terminal-wrapper .xterm-rows').filter({ visible: true })
            .allInnerTexts().then(texts => texts.join('\n')).catch(() => '');
    }

    /** The rows of the view whose title is [title], as its tree shows them. */
    async viewRows(title: string): Promise<string[]> {
        return this.locator(`view:${title}`)?.locator('.monaco-list-row').allInnerTexts().catch(() => []) ?? [];
    }

    /**
     * The lines the active editor shows, as drawn: with what it shows inline, after or within them, such as inlay hints
     * and a debug session's inline values.
     */
    async inlineTexts(): Promise<string[]> {
        return this.page.locator('.editor-group-container.active .monaco-editor .view-lines .view-line').filter({ visible: true })
            .allInnerTexts().catch(() => []);
    }
}
