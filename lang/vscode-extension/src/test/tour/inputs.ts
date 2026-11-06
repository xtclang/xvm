import * as vscode from 'vscode';

/**
 * The keys that run a command, by VS Code's default keybindings on Linux, the recording's, as Playwright names them:
 * a chord is its keys in order. Only commands whose keys work where the tour's steps run them, in the editor or
 * anywhere in the workbench, are here; the others run by the Command Palette, by their titles, or without input.
 */
const COMMAND_KEYS: Record<string, string[]> = {
    'editor.action.revealDefinition': ['F12'],
    'editor.action.goToImplementation': ['Control+F12'],
    'references-view.findReferences': ['Shift+Alt+F12'],
    'references-view.showCallHierarchy': ['Shift+Alt+KeyH'],
    'editor.action.showHover': ['Control+KeyK', 'Control+KeyI'],
    'editor.action.rename': ['F2'],
    'editor.action.quickFix': ['Control+Period'],
    'editor.action.refactor': ['Control+Shift+KeyR'],
    'editor.action.triggerParameterHints': ['Control+Shift+Space'],
    'editor.action.triggerSuggest': ['Control+Space'],
    'hideSuggestWidget': ['Escape'],
    'editor.action.commentLine': ['Control+Slash'],
    'editor.foldAll': ['Control+KeyK', 'Control+Digit0'],
    'editor.unfold': ['Control+Shift+BracketRight'],
    'editor.action.smartSelect.expand': ['Shift+Alt+ArrowRight'],
    'editor.action.marker.next': ['Alt+F8'],
    'editor.action.organizeImports': ['Shift+Alt+KeyO'],
    'workbench.action.showAllSymbols': ['Control+KeyT'],
    'workbench.actions.view.problems': ['Control+Shift+KeyM'],
    'workbench.view.explorer': ['Control+Shift+KeyE'],
    'workbench.action.debug.continue': ['F5'],
    'workbench.action.debug.stepOver': ['F10'],
    'workbench.action.debug.stepInto': ['F11'],
    'workbench.action.debug.stepOut': ['Shift+F11'],
    'workbench.action.debug.pause': ['F6'],
    'workbench.debug.action.toggleRepl': ['Control+Shift+KeyY'],
    'testing.runAtCursor': ['Control+Semicolon', 'KeyC'],
    'testing.runAll': ['Control+Semicolon', 'KeyA'],
    'testing.debugAtCursor': ['Control+Semicolon', 'Control+KeyC'],
};

/**
 * The Command Palette's titles of VS Code's commands that the tour runs by the palette: those without a key, or whose
 * key does something else where the tour runs.
 */
const PALETTE_TITLES: Record<string, string> = {
    // its key on Linux, Ctrl+Shift+I, also toggles the developer tools, which it opened in a recorded run
    'editor.action.formatDocument': 'Format Document',
    'editor.action.goToTypeDefinition': 'Go to Type Definition',
    'workbench.action.tasks.runTask': 'Tasks: Run Task',
};

/** The keys that run the command [id], when it has keys the tour uses. */
export function commandKeys(id: string): string[] | undefined {
    return COMMAND_KEYS[id];
}

/** What an extension's package.json contributes of its commands and settings. */
interface Contributions {
    contributes?: {
        commands?: { command: string; title: string; category?: string }[];
        menus?: { commandPalette?: { command: string; when?: string }[] };
        configuration?: SettingsGroup | SettingsGroup[];
    };
}

/** A group of an extension's settings, as its package.json contributes it. */
interface SettingsGroup {
    title?: string;
    properties?: Record<string, { scope?: string }>;
}

/** The groups of settings that the extensions contribute, with their titles and settings. */
export function settingsGroups(): SettingsGroup[] {
    return vscode.extensions.all.flatMap(extension => [(extension.packageJSON as Contributions).contributes?.configuration ?? []].flat());
}

/** The scope of the setting [name], as its extension's package.json declares it, if it does. */
export function settingScope(name: string): string | undefined {
    return settingsGroups().map(group => group.properties?.[name]).find(Boolean)?.scope;
}

/**
 * The title that the Command Palette lists the command [id] by: VS Code's, from [PALETTE_TITLES], or an extension's,
 * as its package.json contributes it, with its category, unless it keeps the command out of the palette.
 */
export function paletteTitle(id: string): string | undefined {
    if (PALETTE_TITLES[id]) return PALETTE_TITLES[id];
    for (const extension of vscode.extensions.all) {
        const contributes = (extension.packageJSON as Contributions).contributes;
        const found = contributes?.commands?.find(each => each.command === id);
        if (!found) continue;
        if (contributes?.menus?.commandPalette?.some(each => each.command === id && each.when !== undefined)) return undefined;
        return found.category ? `${found.category}: ${found.title}` : found.title;
    }
    return undefined;
}
