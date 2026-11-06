/*
 * The VS Code tour's steps, in order: what each shows, says and does, as a user does it, and what each checks.
 *
 * Coverage: every feature of the language server (XtcLanguageServer.initialize and its xtc/* requests and
 * notifications), of the debug adapter (XtcDebugServer.initialize and its requests) and of the extension
 * (package.json's contributions and the extension's sources), with the step (a frame of the video) or the check
 * (no frame) that exercises it, by its title. "protocol" marks the end's checks that every advertised LSP and DAP
 * request reached the server, from the server's trace of what it received and the tour's tracker of the debug
 * sessions; "-" marks what VS Code itself never asks for, with the reason.
 *
 * Language server: capabilities
 * | Feature                                   | Covered by                                                        |
 * |-------------------------------------------|-------------------------------------------------------------------|
 * | initialize, initialized, shutdown         | step "Open the project"; check "The server advertises its features"; check "Restart the language server" |
 * | textDocumentSync: didOpen/didChange/didClose/didSave | step "Errors as you type"; check "the project is back as it was" (saves) |
 * | willSaveWaitUntil (saveFormatting=server) | check "Connection and runtime settings"                           |
 * | incremental sync (textSynchronization)    | check "Connection and runtime settings"                           |
 * | textDocument/diagnostic (pull)            | steps "Errors as you type", "The Problems view"                   |
 * | workspace/diagnostic, interFileDependencies | step "Errors in other files"; check "The other files compile again" |
 * | textDocument/hover                        | steps "Hover documentation", "The XDK's documentation"            |
 * | textDocument/completion (. : <)           | step "Member completion"                                          |
 * | completionItem/resolve                    | step "Accept a completion"                                        |
 * | textDocument/inlineCompletion             | check "Inline completion"                                         |
 * | textDocument/definition                   | steps "Go to definition", "The XDK's sources"                     |
 * | textDocument/declaration                  | check "Go to declaration"                                         |
 * | textDocument/typeDefinition               | step "Go to type definition"                                      |
 * | textDocument/implementation               | step "Go to implementations"                                      |
 * | textDocument/references (workDoneProgress)| step "Find all references"                                        |
 * | textDocument/documentSymbol               | step "The Outline view"; checks "Breadcrumbs", "Go to symbol in the file" |
 * | textDocument/documentHighlight            | step "Highlight occurrences"                                      |
 * | textDocument/selectionRange               | step "Expand the selection"                                       |
 * | textDocument/foldingRange                 | step "Folding"                                                    |
 * | textDocument/prepareRename, rename        | step "Rename a method"                                            |
 * | experimental xtcRenameProposal, xtc/rename| steps "Renamed everywhere", "Rename a class"; check "Undo a rename" |
 * | textDocument/codeAction, codeAction/resolve | steps "Quick fixes", "Create a method", "Add an import", "Refactorings", "Extracted to a local", "Extract a method"; check "Safe delete", "Generate a doc comment" |
 * | source.organizeImports                    | step "Organize imports"                                           |
 * | executeCommandProvider                    | - not advertised to VS Code, which takes code actions as literals |
 * | textDocument/formatting                   | step "Format the document"                                        |
 * | textDocument/rangeFormatting, rangesFormatting | checks "Format a selection", "Format the selections"           |
 * | textDocument/onTypeFormatting (\n } ; ))  | step "Format as you type"; check "Format a statement as it ends"  |
 * | textDocument/inlayHint, inlayHint/resolve | step "Inlay hints"                                                |
 * | textDocument/inlineValue                  | check "Inline values"                                             |
 * | textDocument/documentLink, documentLink/resolve | check "Document links"                                      |
 * | textDocument/signatureHelp ( , [          | step "Parameter hints"                                            |
 * | textDocument/semanticTokens/full, /full/delta | step "Semantic highlighting"; protocol (delta, as edits follow) |
 * | textDocument/semanticTokens/range         | check "Highlighting a range"                                      |
 * | workspace/symbol, workspaceSymbol/resolve | step "Go to a symbol"; check "Open a symbol"                      |
 * | xtc/renameFiles, didRenameFiles           | steps "Rename a file", "Move a file"                              |
 * | workspace/willRenameFiles                 | - the extension's participant asks xtc/renameFiles instead, as the server advertises xtcFileMoveProposal |
 * | workspace/will/didCreateFiles, will/didDeleteFiles | check "Create and delete a file"; step "Move a file" (its folder) |
 * | workspace/textDocumentContent             | step "The XDK's sources" (read-only)                              |
 * | workspaceFolders, didChangeWorkspaceFolders | - the tour's single-folder window would reload, ending the tour; the playbook's --multi-root run covers it |
 * | textDocument/codeLens, codeLens/resolve   | step "Code lenses"; check "A reference lens"                      |
 * | textDocument/linkedEditingRange           | check "Linked editing"                                            |
 * | textDocument/moniker                      | checks "Monikers", "One identity in the XDK's sources" (protocol: VS Code has no moniker view) |
 * | textDocument/prepareTypeHierarchy, subtypes, supertypes | step "Type hierarchy"; check "Supertypes"           |
 * | textDocument/prepareCallHierarchy, incomingCalls, outgoingCalls | step "Call hierarchy"; check "Outgoing calls" |
 * | workspace/configuration, didChangeConfiguration | checks "Formatting settings", "Inlay hint setting", "Reference lens setting" |
 * | workspace/*\/refresh (diagnostic, semanticTokens, inlayHint, codeLens, foldingRange, inlineValue) | protocol |
 * | window/workDoneProgress/create, $/progress | protocol (references, rename, diagnostics)                       |
 * | workspace/didChangeWatchedFiles           | check "the project is back as it was" (files written on disk)     |
 *
 * Language server: xtc/* requests and notifications
 * | xtc/build                                 | steps "Build", "A failed build"; check "Rebuild"                  |
 * | xtc/run, xtc/runOutput, xtc/runExited     | step "Run from the editor"; checks "The Run lens", "Run with a key", "A task with arguments" |
 * | xtc/runInput                              | step "Program input"                                              |
 * | xtc/stopRun                               | checks "Stop the program", "Cancel a test run"                    |
 * | xtc/tests                                 | step "The Testing view"; check "Refresh the tests"                |
 * | xtc/testEvent                             | steps "Run a test", "Run a module's tests", "Run all the tests", "A failing test", "A new test"; checks "An error, apart from a failure", "A skipped test" |
 * | xtc/debugStart, xtc/debugMessage, xtc/debugEnd | step "Debug" and every debugging step                        |
 * | xtc/debugTask                             | step "Debug with Gradle"                                        |
 * | xtc/rename, xtc/renameFiles               | as above                                                          |
 * | xtc/compilerSourceModules                 | step "Compiler paths"; check "Explicit source modules"            |
 * | xtc/discoverConsumers                     | check "Add consumers"                                             |
 * | xtc/languageServiceStatus                 | step "The effective configuration"                                |
 * | xtc/exportLogs                            | step "Export the server's logs"                                   |
 * | xtc/healthCheck                           | check "The server advertises its features"                        |
 * | xtc/runLenses, xtc/runnableTargets        | - IntelliJ's; VS Code takes the Run lens from textDocument/codeLens |
 *
 * Debug adapter (XtcDebugServer)
 * | launch, configurationDone, threads, stackTrace, scopes, variables | step "Debug"; check "Restart debugging" |
 * | setBreakpoints (line, condition, disabled)| steps "A conditional breakpoint", "Frames and the call stack"; check "Disable a breakpoint" |
 * | setBreakpoints (hitCondition)             | check "A hit count"                                               |
 * | setBreakpoints (logMessage)               | step "A logpoint"                                                 |
 * | setFunctionBreakpoints                    | step "A function breakpoint"                                      |
 * | dataBreakpointInfo, setDataBreakpoints    | step "A data breakpoint"                                          |
 * | setExceptionBreakpoints (filters, conditions), exceptionInfo | step "An exception"; check "All exceptions"    |
 * | continue, next, stepIn, stepOut           | steps "Step into", "Step over", "Step out"                        |
 * | runToCursor (a breakpoint and continue)   | step "Run to the cursor"                                          |
 * | stepInTargets                             | step "Step into a target"; step "Library frames"                  |
 * | restartFrame                              | step "Restart a frame"                                            |
 * | pause                                     | step "Pause"                                                      |
 * | evaluate (watch, repl, hover)             | steps "Watches", "The Debug Console"; checks "Hover a value", "A watch that would run code" |
 * | completions (trigger .)                   | check "Completion in the Debug Console"                           |
 * | setVariable                               | step "Set a value"                                                |
 * | valueFormattingOptions, xtc/valueFormat   | check "Hexadecimal values"                                        |
 * | loadedSources                             | check "Loaded sources"                                            |
 * | modules                                   | check "Modules" (protocol: VS Code has no Modules view)           |
 * | singleThread                              | check "Stepping one fiber" (what VS Code sends)                   |
 * | cancel                                    | - VS Code sends it only for a request it abandons; the playbook's X314 covers it |
 * | terminate, disconnect                     | check "Stop debugging"                                            |
 * | attach                                    | step "Attach to a program"                                        |
 * | xtcTest event                             | step "Debug a test"                                               |
 *
 * Extension: commands, keybindings and menus (package.json)
 * | xtc.showServerOutput (Ctrl+Alt+X L)       | step "The server's log"                                           |
 * | xtc.hideServerOutput (Ctrl+Alt+X L)       | check "Hide the server's log"                                     |
 * | xtc.showLanguageServiceStatus             | step "The effective configuration"                                |
 * | xtc.showCompilerPaths                     | step "Compiler paths"                                             |
 * | xtc.configureCompilerPaths                | step "Configure compiler paths"; checks "Add consumers", "Open the build file", "Explicit source modules" (reset) |
 * | xtc.configureCompilerLibraries            | step "Libraries and sources"                                      |
 * | xtc.refreshCompilerBuild                  | check "Refresh the Gradle model"                                  |
 * | xtc.prepareCompilerInputs                 | check "Prepare generated resources"                               |
 * | xtc.selectLanguageAdapter (Ctrl+Alt+X A)  | step "Switch the language adapter"; check "Back to the compiler"  |
 * | xtc.restartServer                         | check "Restart the language server"                               |
 * | xtc.exportServerLogs                      | step "Export the server's logs"                                   |
 * | xtc.openLanguageSettings                  | step "The extension's settings"                                   |
 * | xtc.createProject                         | step "Create a project"                                           |
 * | xtc.selectBuildMode, the status bar item  | step "The build mode"; steps "Run with Gradle", "Debug with Gradle", "Tests with Gradle" |
 * | xtc.build (Ctrl+F9), xtc.rebuild          | steps "Build", "A failed build"; check "Rebuild"                  |
 * | xtc.runModule (Ctrl+Shift+F10, editor/title/run, editor/context, Run lens) | step "Run from the editor"; checks "The Run lens", "Run with a key", "The editor's menu" |
 * | xtc.debug.toggleHexValues (debug/variables/context, debug/watch/context) | check "Hexadecimal values"         |
 * | xtc.debug.copyOption                      | step "Attach to a program"                                        |
 * | xtc.showReferences (the reference lens)   | check "A reference lens"                                          |
 * | the language status item (adapter, restart) | step "Open the project"; step "Switch the language adapter"     |
 *
 * Extension: settings
 * | xtc.build.mode                            | step "The build mode", and the Gradle steps                       |
 * | xtc.languageService.adapter               | step "Switch the language adapter"                                |
 * | xtc.formatting.* (indentSize, continuationIndentSize, tabSize, insertSpaces, maxLineWidth) | check "Formatting settings" |
 * | xtc.inlayHints.enabled                    | check "Inlay hint setting"                                        |
 * | xtc.codeLens.references                   | check "Reference lens setting"                                    |
 * | xtc.trace.server                          | check "Trace the server"                                          |
 * | xtc.compiler.sourceModules                | check "Explicit source modules"                                   |
 * | xtc.compiler.libraries                    | check "Explicit libraries"                                        |
 * | xtc.sourceRoots, xtc.java.home, xtc.java.vmOptions, xtc.server.logs, xtc.languageService.textSynchronization, xtc.languageService.saveFormatting | check "Connection and runtime settings" |
 *
 * Extension: language, test controller, tasks and debug configurations
 * | languages (.x), grammars, language-configuration | steps "Open the project", "Comment out lines"                |
 * | snippets                                  | step "Snippets"                                                   |
 * | configurationDefaults ([xtc] formatOnType, tabSize, semantic highlighting) | steps "Format as you type", "Semantic highlighting" |
 * | test controller: items, ranges            | steps "The Testing view", "Test icons", "A new test" (listed once it is written, and run) |
 * | test controller: Run profile              | steps "Run a test", "Run a module's tests", "Run all the tests", "A failing test" (its message at its line); checks "An error, apart from a failure", "A skipped test" |
 * | test controller: Debug profile            | step "Debug a test"                                               |
 * | test controller: refreshHandler           | check "Refresh the tests"                                         |
 * | test controller: Gradle mode (testXtc)    | step "Tests with Gradle"                                          |
 * | test controller: output                   | check "The tests' output"                                         |
 * | taskDefinitions xtc (moduleName, args, mode), task provider | step "Tasks"; checks "A task with arguments", "A Gradle task with arguments" |
 * | problemMatchers $xtc                      | check "A failed Gradle build"                                     |
 * | Gradle lifecycle tasks (Build, Test, Clean) | step "Tasks"; check "A Gradle build"                            |
 * | debuggers xtc launch (module, method, args, cwd, stopOnEntry, evaluationRunsProgram) | step "Debug" (no launch.json); step "Stop on entry"; checks "Launch with arguments", "Evaluation that runs the program" |
 * | debuggers xtc attach (port, host, token, stopOnEntry) | step "Attach to a program"                            |
 * | configurationSnippets                     | step "Launch configurations"                                      |
 */

import * as fs from 'node:fs/promises';
import * as os from 'node:os';
import * as path from 'node:path';
import * as vscode from 'vscode';
import { buildModeStatus } from '../../build-mode-selection';
import { getClient } from '../../lsp-client';
import { testLocation } from '../../test-explorer';
import {
    acceptSuggestion, actionRows, breakpoint, breakpoints, buildMode, chooseAction, codeActions, consoleText, debug, debugConfiguration,
    answer, debugConsole, ensure, expand, expect, fileDialog, quickRows, row, setting, stoppedAt, suggestions, terminal, typeCode, typeIn, variables,
} from './actions';
import {
    active, at, caretLine, command, compiles, current, editing, errors, hasError, launch, lineOf, open, output, positionOf, problems, replace, select, text, type,
} from './editor';
import { settingsGroups } from './inputs';
import { NEEDS, type Tour } from './tour';

/** What the tour found of the server's and the debug adapter's features, for results.json. */
export type Coverage = Record<string, unknown>;

/** The fix that imports Suit, as the server titles it, and not Organize Imports, which the same list has. */
const IMPORT_SUIT = /Import '[\w.]*Suit'|import for 'Suit'/;

/** The tests of the card game's modules, and of DeckTest's, which the test steps run. */
const ALL_TESTS = 64;
const DECK_TESTS = 8;

/** The tour's steps, in order. */
export async function script(tour: Tour, coverage: Coverage): Promise<void> {
    await opening(tour);
    await start(tour, coverage);
    await editingCode(tour);
    await navigation(tour);
    await diagnostics(tour);
    await refactoring(tour);
    await commands(tour);
    await building(tour);
    await debugging(tour);
    await testing(tour);
    await settings(tour);
    await protocol(tour, coverage);
    ending(tour);
}

/** Lays the window out, opens the game and waits for the language server's first analysis of it. */
async function opening(tour: Tour): Promise<void> {
    await tour.ui.layout();
    const extension = vscode.extensions.all.find(item => item.packageJSON.name === 'xtc-language');
    await extension?.activate();
    await command('workbench.action.closeAllEditors');
    // the test window says that the other extensions are off, which a user's would not
    await command('notifications.clearAll');
    await open(tour.source('Eights.x'));
    await tour.until('The language server starts', () => !!getClient()?.initializeResult && getClient()!.isRunning());
    await compiles(tour, tour.source('Eights.x'));
    // the commands of the workbench, for the tour's author: their IDs change between VS Code's versions
    const ids = (await vscode.commands.getCommands(true)).filter(id => /^(editor|workbench|debug|testing|references-view|xtc|outline|explorer|filesExplorer)\b/.test(id)).sort();
    await fs.writeFile(path.join(process.env.XTC_TOUR_REPORT_DIR!, 'commands.txt'), ids.join('\n') + '\n');
}

async function start(tour: Tour, coverage: Coverage): Promise<void> {
    tour.section('start', 'Start here');
    tour.card('Ecstasy in VS Code', 'The XTC extension on a real project: everything it does for Ecstasy code, from typing to tests.', {
        eyebrow: 'XTC LANGUAGE SUPPORT · VISUAL STUDIO CODE',
        heading: 'Ecstasy in VS Code, end to end',
        lead: 'A guided tour of the XTC extension, on a small game written in Ecstasy',
        boxes: [
            { title: 'Edit and understand', text: 'Highlighting, hovers, completion, navigation, diagnostics and fixes, refactoring and formatting.' },
            { title: 'Run, debug and test', text: 'Embedded and Gradle builds and runs, every kind of breakpoint, stepping, evaluation, and the Testing view.' },
        ],
    });
    tour.card('The project', 'Crazy Eights: two players, a deck, and the first to play all of their cards wins. A project as Ecstasy: Create New Project makes it.', {
        eyebrow: "THE TOUR'S PROJECT",
        heading: 'Crazy Eights',
        lead: 'Play a card of the same suit or rank as the top of the pile; an eight matches anything.',
        code: [
            'cardGame/',
            '  build.gradle.kts            the XTC Gradle plugin',
            '  src/main/x/cardGame.x       module cardGame: run() plays a game',
            '  src/main/x/cardGame/        Card, Deck, Hand, Player, Eights,',
            '                              PlayStrategy, Scoreboard, ...',
            '  src/test/x/                 xunit tests: DeckTest, PlayerTest, ...',
        ].join('\n'),
    });
    await tour.shot({
        title: 'Open the project',
        caption: 'The project in the Explorer, and the game in the editor. The extension starts its language server, with the XDK bundled, and the status bar says it is ready.',
        feature: 'initialize',
        focus: 'sidebar',
        act: async () => {
            await command('workbench.view.explorer');
            await open(tour.source('Eights.x'));
        },
        until: async () => (await tour.ui.text('sidebar')).includes('Eights.x'),
        check: async () => {
            const status = await tour.ui.text('status:Ecstasy');
            expect(status.includes('Compiler'), () => `The status bar's item: ${status}`);
            expect(active().document.languageId === 'xtc', () => `Eights.x is ${active().document.languageId}`);
        },
    });
    await tour.verify('The server advertises its features', 'initialize', async () => {
        const capabilities = getClient()!.initializeResult!.capabilities as unknown as Record<string, unknown>;
        coverage.capabilities = capabilities;
        const providers = ['hoverProvider', 'completionProvider', 'definitionProvider', 'typeDefinitionProvider', 'declarationProvider',
            'implementationProvider', 'referencesProvider', 'documentSymbolProvider', 'documentHighlightProvider', 'selectionRangeProvider',
            'foldingRangeProvider', 'renameProvider', 'codeActionProvider', 'documentFormattingProvider', 'documentRangeFormattingProvider',
            'documentOnTypeFormattingProvider', 'inlayHintProvider', 'inlineValueProvider', 'documentLinkProvider', 'signatureHelpProvider',
            'semanticTokensProvider', 'workspaceSymbolProvider', 'codeLensProvider', 'linkedEditingRangeProvider', 'monikerProvider',
            'typeHierarchyProvider', 'callHierarchyProvider', 'diagnosticProvider', 'inlineCompletionProvider'];
        const missing = providers.filter(provider => !capabilities[provider]);
        expect(missing.length === 0, () => `Not advertised: ${missing.join(', ')}`);
        const health = await getClient()!.sendRequest<{ healthy: boolean; adapter: string }>('xtc/healthCheck');
        expect(health.healthy && health.adapter === 'XDK', () => `The health check: ${JSON.stringify(health)}`);
    });
}

/**
 * Selects [uri] in the Explorer, as a click on it does; again while the Explorer's tree has not caught up with a file
 * or folder a step just made.
 */
async function reveal(tour: Tour, uri: vscode.Uri): Promise<void> {
    const name = path.basename(uri.fsPath);
    await tour.until(`The Explorer shows ${name}`,
        () => Promise.resolve(command('revealInExplorer', uri)).then(() => true, () => false));
    // the Explorer's commands, such as Rename, Cut and New File, act on its selected row, which the reveal selects
    await tour.until(`The Explorer selects ${name}`, async () =>
        (await tour.ui.page.locator('.explorer-folders-view .monaco-list-row.selected').allInnerTexts().catch(() => [] as string[]))
            .some(each => each.trim() === name));
}

/** The Explorer's view of the project's folder, by its title. */
function folders(tour: Tour) {
    return tour.ui.page.locator('.pane-header').filter({ hasText: /^\s*cardGame/i }).first();
}

/** Decodes the semantic tokens of [file] into the names of their types and modifiers. */
async function semanticTokens(file: string): Promise<{ line: number; start: number; length: number; type: string; modifiers: string[] }[]> {
    const uri = vscode.Uri.file(file);
    const legend = await command<vscode.SemanticTokensLegend>('vscode.provideDocumentSemanticTokensLegend', uri);
    const tokens = await command<vscode.SemanticTokens>('vscode.provideDocumentSemanticTokens', uri);
    const decoded = [];
    let line = 0;
    let start = 0;
    for (let index = 0; tokens && index + 4 < tokens.data.length; index += 5) {
        const [deltaLine, deltaStart, length, type, modifiers] = tokens.data.slice(index, index + 5);
        line += deltaLine;
        start = deltaLine === 0 ? start + deltaStart : deltaStart;
        decoded.push({
            line, start, length, type: legend.tokenTypes[type],
            modifiers: legend.tokenModifiers.filter((_, bit) => (modifiers & (1 << bit)) !== 0),
        });
    }
    return decoded;
}

async function editingCode(tour: Tour): Promise<void> {
    tour.section('editing', 'Editing code');
    const player = tour.source('Player.x');
    const eights = tour.source('Eights.x');
    const collection = tour.source('CardCollection.x');
    await tour.shot({
        title: 'Semantic highlighting',
        caption: 'The compiler colors what each name is: fields, parameters, locals, types and static members, beyond what the grammar can tell.',
        feature: 'textDocument/semanticTokens/full',
        focus: 'editor',
        act: async () => { await open(player, 'cardMatches(card, prev)'); },
        check: async () => {
            const tokens = await semanticTokens(player);
            const kind = (anchor: string, delta: number) => {
                const at = positionOf(anchor, delta);
                const token = tokens.find(each => each.line === at.line && each.start <= at.character && at.character < each.start + each.length);
                return token ? [token.type, ...token.modifiers].join(' ') : 'none';
            };
            const found = {
                prev: kind('cardMatches(card, prev)', 'cardMatches(card, '.length),
                card: kind('cardMatches(card, prev)', 'cardMatches('.length),
                hand: kind('hand.addCard(card);', 0),
                cardMatches: kind('static Boolean cardMatches', 'static Boolean '.length),
            };
            expect(found.prev.startsWith('parameter') && found.card.startsWith('variable') && found.hand.startsWith('property')
                && /^(method|function)\b/.test(found.cardMatches) && found.cardMatches.includes('static'), () => JSON.stringify(found));
        },
    });
    await tour.verify('Highlighting a range', 'textDocument/semanticTokens/range', async () => {
        const tokens = await command<vscode.SemanticTokens>('vscode.provideDocumentRangeSemanticTokens', vscode.Uri.file(player), new vscode.Range(54, 0, 68, 0));
        expect((tokens?.data.length ?? 0) > 20, () => `${tokens?.data.length} token values in lines 55 to 68`);
    });
    const deal = await lineOf(eights, 'deck.deal(one.hand');
    await tour.shot({
        title: 'Inlay hints',
        caption: "Hints name a call's parameters where its arguments don't say what they are: deal's that and numCards.",
        feature: 'textDocument/inlayHint',
        focus: `line:${deal}`,
        settleMs: 2_000,
        act: async () => { await open(eights, 'deck.deal(one.hand'); },
        until: async () => (await tour.ui.inlineTexts()).some(each => each.includes('numCards')),
        check: async () => {
            const hints = await command<vscode.InlayHint[]>('vscode.executeInlayHintProvider', vscode.Uri.file(eights), new vscode.Range(deal - 1, 0, deal, 0));
            const labels = hints.map(hint => typeof hint.label === 'string' ? hint.label : hint.label.map(part => part.value).join(''));
            expect(labels.some(label => label.includes('that')) && labels.some(label => label.includes('numCards')), () => labels.join(', '));
        },
    });
    await tour.shot({
        title: 'Hover documentation',
        caption: "Hovering a name, or Ctrl+K Ctrl+I, shows its declaration and its doc comment, from the compiler's view of it.",
        feature: 'textDocument/hover',
        focus: 'hover',
        settleMs: 2_000,
        act: async () => {
            await open(eights, 'INITIAL_HAND_SIZE', 3);
            await tour.ui.focusEditor();
            await command('editor.action.showHover');
        },
        until: async () => (await tour.ui.text('hover')).includes('INITIAL_HAND_SIZE'),
        check: async () => {
            const hover = await tour.ui.text('hover');
            expect(hover.includes('Number of cards dealt'), () => `No doc comment in: ${hover}`);
        },
        after: async () => { await tour.ui.press('Escape'); },
    });
    await tour.shot({
        title: "The XDK's documentation",
        caption: "Hovers work the same for the XDK's own types: HashMap's comes from the ecstasy module's sources.",
        feature: 'textDocument/hover',
        focus: 'hover',
        settleMs: 2_000,
        act: async () => {
            await open(tour.source('Scoreboard.x'), 'new HashMap', 'new Hash'.length);
            await tour.ui.focusEditor();
            await command('editor.action.showHover');
        },
        until: async () => (await tour.ui.text('hover')).includes('HashMap'),
        check: async () => {
            const hover = await tour.ui.text('hover');
            expect(hover.includes('hashed implementation'), () => `No HashMap documentation in: ${hover}`);
        },
        after: async () => { await tour.ui.press('Escape'); },
    });
    await tour.shot({
        title: 'Parameter hints',
        caption: 'Inside a call, Ctrl+Shift+Space shows the parameters of the method it calls, with the one at the cursor in bold.',
        feature: 'textDocument/signatureHelp',
        focus: 'parameterHints',
        settleMs: 2_000,
        act: async () => {
            await open(eights, 'deck.deal(one.hand, GameConstants', 'deck.deal(one.hand, '.length);
            await tour.ui.focusEditor();
            await command('editor.action.triggerParameterHints');
        },
        check: async () => {
            const hints = await tour.ui.text('parameterHints');
            expect(hints.includes('numCards'), () => `No parameters in: ${hints}`);
        },
        after: async () => { await command('closeParameterHints'); },
    });
    await tour.shot({
        title: 'Member completion',
        caption: "After a dot, completion lists the members of the expression's type, as the compiler resolves it, with their types.",
        feature: 'textDocument/completion',
        focus: 'suggest',
        settleMs: 2_000,
        act: async () => {
            await open(player, 'console.print($"{name} draws {card}");', 'console.print($"{name} draws {card}");'.length);
            await tour.ui.focusEditor();
            await tour.ui.press('Enter');
            await typeCode(tour, 'hand.');
        },
        until: async () => (await suggestions(tour)).some(item => item.includes('addCard')),
        check: async () => {
            const items = await suggestions(tour);
            expect(items.some(item => item.includes('addCard')) && items.some(item => item.includes('popCard')), () => items.join(' | '));
        },
    });
    await tour.shot({
        title: 'Accept a completion',
        caption: 'Typing narrows the list, and Enter takes the item, with its documentation beside it: the call it completes compiles.',
        feature: 'completionItem/resolve',
        focus: 'editor',
        settleMs: 2_000,
        act: async () => {
            await tour.ui.type('addC');
            await acceptSuggestion(tour, 'addCard');
            const current = () => active().document.lineAt(active().selection.active.line).text;
            await tour.until('The item is in the line', () => current().includes('addCard'), current);
            const line = current();
            // the item may bring its parentheses, with the caret between them, or its argument
            if (!/addCard\(card\)/.test(line)) await typeCode(tour, /addCard\(\s*\)/.test(line) ? 'card' : '(card');
            await command('hideSuggestWidget');
            await tour.ui.press('End');
            if (!active().document.lineAt(active().selection.active.line).text.trimEnd().endsWith(';')) await typeCode(tour, ';');
        },
        check: async () => {
            const line = active().document.lineAt(active().selection.active.line).text.trim();
            expect(line === 'hand.addCard(card);', () => `The line: ${line}`);
            await compiles(tour, player);
        },
        after: async () => { await tour.restore(); },
    });
    await tour.shot({
        title: 'Snippets',
        caption: "The extension's snippets complete too: meth and Enter write a method, and Tab moves through its type, name and parameters.",
        feature: 'contributes.snippets',
        focus: 'editor',
        settleMs: 2_000,
        act: async () => {
            await open(eights, 'public Hand   discardPile;', 'public Hand   discardPile;'.length);
            await tour.ui.focusEditor();
            await tour.ui.press('Enter', 'Enter');
            await tour.ui.type('meth');
            await command('editor.action.triggerSuggest');
            await acceptSuggestion(tour, 'meth');
            await tour.until('The snippet is in the file', async () => (await text(eights)).includes('myMethod'));
            await tour.ui.press('Tab');
        },
        check: async () => {
            const source = await text(eights);
            expect(/void myMethod\(\) \{\n {8}\n {4}\}/.test(source), () => `No method snippet in: ${source.slice(250, 500)}`);
        },
        after: async () => {
            await tour.ui.press('Escape');
            await tour.restore();
        },
    });
    await tour.shot({
        title: 'Code that needs formatting',
        caption: "swapCards' call is indented too far: the formatter indents Ecstasy code by its blocks, and keeps the spacing within a line as it is written.",
        feature: 'textDocument/formatting',
        focus: `line:${await lineOf(collection, 'cards.swap(index1, index2);')}`,
        act: async () => { await open(collection, 'cards.swap(index1, index2);'); },
        check: async () => {
            // deeper than the block's 12 spaces, as the fixture writes it, which the next step formats
            const line = (await text(collection)).split('\n').find(each => each.includes('cards.swap(index1, index2);')) ?? '';
            expect(line.length - line.trimStart().length > 12, () => `swapCards' call is indented as ${JSON.stringify(line)}`);
        },
    });
    await tour.shot({
        title: 'Format the document',
        caption: "Shift+Alt+F formats the file through the language server's formatter, with the editor's tab size and the extension's formatting settings.",
        feature: 'textDocument/formatting',
        focus: 'editor',
        settleMs: 2_500,
        act: async () => {
            await tour.ui.focusEditor();
            await command('editor.action.formatDocument');
        },
        until: async () => (await text(collection)).includes('\n            cards.swap(index1, index2);'),
        check: async () => {
            const source = await text(collection);
            expect(source.includes('\n            cards.swap(index1, index2);'), () => source.slice(1200, 1700));
            await compiles(tour, collection);
        },
        after: async () => { await tour.restore(); },
    });
    await tour.verify('Format a selection', 'textDocument/rangeFormatting', async () => {
        // one line indented wrong, in the one selection: VS Code asks for one range, as it asks for several above
        const editor = await open(collection);
        await replace('        cards.add(card);', '  cards.add(card);');
        const add = positionOf('  cards.add(card);');
        editor.selection = new vscode.Selection(add.line, 0, add.line + 1, 0);
        await command('editor.action.formatSelection');
        await tour.until('The selection is formatted', async () => (await text(collection)).includes('\n        cards.add(card);'), () => text(collection));
        await tour.restore();
    });
    await tour.verify('Format the selections', 'textDocument/rangesFormatting', async () => {
        // two lines indented wrong, each in a selection of its own
        const editor = await open(collection);
        await replace('        cards.add(card);', '  cards.add(card);');
        const add = positionOf('  cards.add(card);');
        const swap = positionOf('cards.swap(index1, index2);');
        editor.selections = [new vscode.Selection(add.line, 0, add.line + 1, 0), new vscode.Selection(swap.line, 0, swap.line + 1, 0)];
        await command('editor.action.formatSelection');
        await tour.until('Both selections are formatted', async () => {
            const source = await text(collection);
            return source.includes('\n            cards.swap(index1, index2);') && source.includes('\n        cards.add(card);');
        }, () => text(collection));
        await tour.restore();
    });
    await tour.shot({
        title: 'Format as you type',
        caption: 'Typing a brace and Enter indents the new line and closes the block: the server formats each line as it is typed.',
        feature: 'textDocument/onTypeFormatting',
        focus: 'caret',
        settleMs: 2_000,
        act: async () => {
            await open(player, 'return drawForMatch(eights, prev);');
            await at('        }\n        return drawForMatch', '        }'.length);
            await tour.ui.focusEditor();
            await tour.ui.press('Enter');
            await typeCode(tour, 'if (hand.isEmpty()) {\n');
        },
        until: async () => /if \(hand\.isEmpty\(\)\) \{\n {12}\n {8}\}/.test(await text(player)),
        check: async () => {
            const source = await text(player);
            expect(/if \(hand\.isEmpty\(\)\) \{\n {12}\n {8}\}/.test(source), () => source.slice(900, 1300));
        },
        after: async () => { await tour.restore(); },
    });
    await tour.verify('Format a statement as it ends', 'textDocument/onTypeFormatting', async () => {
        await open(player, 'return drawForMatch(eights, prev);');
        await at('        }\n        return drawForMatch', '        }'.length);
        await tour.ui.focusEditor();
        await tour.ui.press('Enter');
        // a statement typed from the line's start, which its ; indents
        await tour.ui.press('Shift+Home', 'Backspace');
        await typeCode(tour, 'Int extra = hand.size()');
        await command('hideSuggestWidget');
        await type(';');
        await tour.until('The statement is indented', async () => (await text(player)).includes('\n        Int extra = hand.size();'),
            () => JSON.stringify(active().document.lineAt(caretLine() - 1).text));
        await tour.restore();
    });
    await tour.shot({
        title: 'Comment out lines',
        caption: "Ctrl+/ comments out the selected lines with //, as the language's configuration says, and again uncomments them.",
        feature: 'editor.action.commentLine',
        focus: 'editor',
        act: async () => {
            await open(player);
            await select('return card1.suit == card2.suit\n            || card1.rank == card2.rank\n            || card1.rank == GameConstants.SPECIAL_CARD;');
            await command('editor.action.commentLine');
        },
        check: async () => {
            const source = await text(player);
            expect(/\/\/\s*return card1\.suit/.test(source) && /\/\/\s*\|\| card1\.rank == GameConstants/.test(source), () => source.slice(2400));
        },
        after: async () => { await tour.restore(); },
    });
    await tour.shot({
        title: 'Folding',
        caption: "Methods and doc comments fold, as the compiler's folding ranges mark them: Ctrl+K Ctrl+0 folds them all.",
        feature: 'textDocument/foldingRange',
        focus: 'editor',
        act: async () => {
            await open(eights);
            await tour.ui.focusEditor();
            await command('editor.foldAll');
            await tour.until('The regions are folded', async () =>
                await tour.ui.page.locator('.editor-group-container.active .codicon-folding-collapsed').count() > 0);
            // the class stays open, so that its folded members show
            await at('class Eights', 'class '.length);
            await command('editor.unfold');
        },
        check: async () => {
            const ranges = await command<vscode.FoldingRange[]>('vscode.executeFoldingRangeProvider', vscode.Uri.file(eights));
            const starts = ranges.map(range => range.start + 1);
            const lines = [await lineOf(eights, 'void reshuffle()'), await lineOf(eights, 'void playGame()'), 1];
            expect(lines.every(line => starts.includes(line)), () => `Folding ranges start at ${starts.join(', ')}; expected ${lines.join(', ')}`);
            const folded = await tour.ui.page.locator('.editor-group-container.active .codicon-folding-collapsed').count();
            expect(folded >= 5, () => `${folded} folded regions show`);
        },
        after: async () => { await command('editor.unfoldAll'); },
    });
    await tour.shot({
        title: 'Expand the selection',
        caption: 'Shift+Alt+Right grows the selection by the syntax around it: a name, the expression, the index, the statement. Shift+Alt+Left shrinks it.',
        feature: 'textDocument/selectionRange',
        focus: 'caret',
        act: async () => {
            await open(collection, 'cards[cards.size - 1]', 'cards[cards.'.length);
            await tour.ui.focusEditor();
            for (let times = 0; times < 3; times++) {
                const before = active().selection;
                await command('editor.action.smartSelect.expand');
                await tour.until('The selection grows', () => !active().selection.isEqual(before), () => active().document.getText(active().selection));
            }
        },
        check: async () => {
            const selected = active().document.getText(active().selection);
            expect(selected.includes('cards.size') && selected.length > 'cards.size'.length, () => `Selected: ${selected}`);
        },
    });
    await tour.shot({
        title: 'Highlight occurrences',
        caption: "With the cursor on a name, its uses in the file are highlighted, from the compiler's references rather than its text.",
        feature: 'textDocument/documentHighlight',
        focus: 'editor',
        settleMs: 2_500,
        act: async () => { await open(player, 'card := eights.drawCard()', 1); },
        check: async () => {
            const highlights = await command<vscode.DocumentHighlight[]>('vscode.executeDocumentHighlights', vscode.Uri.file(player), positionOf('card := eights.drawCard()', 1));
            const lines = new Set(highlights.map(each => each.range.start.line));
            expect(highlights.length >= 4 && lines.size >= 4, () => `${highlights.length} highlights on lines ${[...lines].map(line => line + 1).join(', ')}`);
        },
    });
    await tour.shot({
        title: 'The Outline view',
        caption: "The Outline view lists the file's classes, properties and methods, from the compiler's symbols, and follows the cursor.",
        feature: 'textDocument/documentSymbol',
        focus: 'view:Outline',
        settleMs: 2_000,
        act: async () => {
            await open(eights, 'void playGame()', 'void '.length);
            // the folders' view gives the Outline its room, as a click on its title does
            await tour.ui.click(folders(tour));
            await command('outline.focus');
        },
        until: async () => (await tour.ui.viewRows('Outline')).some(each => each.includes('playGame')),
        check: async () => {
            const symbols = await command<vscode.DocumentSymbol[]>('vscode.executeDocumentSymbolProvider', vscode.Uri.file(eights));
            const names = symbols.flatMap(function all(symbol): string[] { return [symbol.name, ...symbol.children.flatMap(all)]; });
            expect(['reshuffle', 'scoreboard', 'playGame'].every(name => names.some(each => each.startsWith(name))), () => names.join(', '));
            const rows = await tour.ui.viewRows('Outline');
            expect(rows.some(each => each.includes('playGame')), () => rows.join(' | '));
        },
        after: async () => {
            await tour.ui.click(folders(tour));
            await command('workbench.action.focusActiveEditorGroup');
        },
    });
    await tour.verify('Breadcrumbs', 'textDocument/documentSymbol', async () => {
        await open(eights, 'Player current = one;');
        await tour.until('The breadcrumbs show the method', async () => {
            const crumbs = await tour.ui.text('css:.editor-group-container.active .breadcrumbs-control');
            return crumbs.includes('Eights') && crumbs.includes('playGame');
        }, () => tour.ui.text('css:.editor-group-container.active .breadcrumbs-control'));
    });
    await tour.shot({
        title: 'Code lenses',
        caption: 'Above each declaration, a lens counts its references in the project, the tests included; a click lists them.',
        feature: 'textDocument/codeLens',
        focus: 'editor',
        settleMs: 2_000,
        act: async () => { await open(eights, 'Boolean isDone()'); },
        until: async () => (await tour.ui.page.locator('.editor-group-container.active .codelens-decoration').allInnerTexts()).some(each => /reference/.test(each)),
        check: async () => {
            const lenses = await command<vscode.CodeLens[]>('vscode.executeCodeLensProvider', vscode.Uri.file(eights), 100);
            const titles = lenses.map(lens => lens.command?.title ?? '');
            expect(titles.filter(title => /\d+ references?/.test(title)).length >= 5, () => titles.join(' | '));
        },
    });
    await tour.verify('A reference lens', 'xtc.showReferences', async () => {
        await open(eights, 'Boolean isDone()');
        // the editor keeps the lenses of lines out of view, as the class's fields' above, hidden: one it shows
        const lens = tour.ui.page.locator('.editor-group-container.active .codelens-decoration a').filter({ hasText: /reference/ })
            .filter({ visible: true }).first();
        await tour.ui.click(lens);
        await tour.until('The references peek', () => tour.ui.showing('peek'));
        const peek = await tour.ui.text('peek');
        await command('closeReferenceSearch');
        expect(/\w+\.x\b/.test(peek), () => `The peek: ${peek.slice(0, 300)}`);
    });
    await tour.verify('Document links', 'textDocument/documentLink', async () => {
        await open(tour.test('DeckTest.x'));
        // a link's target resolves against the analysis it was listed with: one that a later analysis retires is
        // listed again, as a user's second click would ask again
        let links: vscode.DocumentLink[] = [];
        await tour.until('The links resolve', async () => {
            links = await command<vscode.DocumentLink[]>('vscode.executeLinkProvider', vscode.Uri.file(tour.test('DeckTest.x')), 50);
            return links.length > 0 && links.every(link => link.target !== undefined);
        }, () => `${links.length} links, ${links.filter(link => !link.target).length} unresolved`);
    });
    await tour.verify('Linked editing', 'textDocument/linkedEditingRange', async () => {
        await setting('editor.linkedEditing', true);
        try {
            await open(player, 'Card card := eights.drawCard()', 'Card ca'.length);
            await tour.ui.focusEditor();
            // the editor asks for the linked ranges once the caret rests, and marks them
            await tour.until('The linked ranges are marked', async () => await tour.ui.page.locator('.editor-group-container.active .linked-editing-decoration').count() > 0);
            await type('X');
            await tour.until('The other occurrences change too', async () => ((await text(player)).match(/caXrd/g) ?? []).length >= 4,
                async () => ((await text(player)).match(/caXrd/g) ?? []).length);
            await compiles(tour, player);
        } finally {
            await setting('editor.linkedEditing', undefined);
            await tour.restore();
        }
    });
    await tour.verify('Inline completion', 'textDocument/inlineCompletion', async () => {
        await open(player, 'console.print($"{name} draws {card}");', 'console.print($"{name} draws {card}");'.length);
        await tour.ui.focusEditor();
        await tour.ui.press('Enter');
        await typeCode(tour, 'hand.addCard(ca');
        await command('hideSuggestWidget');
        await command('editor.action.inlineSuggest.trigger');
        await tour.until('Ghost text completes the name', async () => (await tour.ui.text('css:.editor-group-container.active .ghost-text-decoration')).length > 0,
            () => tour.ui.text('css:.editor-group-container.active .ghost-text-decoration'));
        await command('editor.action.inlineSuggest.commit');
        await tour.until('The completion is taken', async () => (await text(player)).includes('hand.addCard(card)'), () => active().document.lineAt(caretLine() - 1).text);
        await tour.restore();
    });
    await tour.verify('Formatting settings', 'xtc.formatting.indentSize', async () => {
        await setting('xtc.formatting.indentSize', 2);
        try {
            await open(collection);
            await command('editor.action.formatDocument');
            await tour.until('Two spaces indent the members', async () => (await text(collection)).includes('\n  void addCard(Card card) {'), () => text(collection));
        } finally {
            await setting('xtc.formatting.indentSize', undefined);
            await tour.restore();
        }
    });
    await tour.verify('Inlay hint setting', 'xtc.inlayHints.enabled', async () => {
        await open(eights, 'deck.deal(one.hand');
        await setting('xtc.inlayHints.enabled', false);
        try {
            await tour.until('The hints go', async () => !(await tour.ui.inlineTexts()).some(each => each.includes('numCards')), () => tour.ui.inlineTexts());
        } finally {
            await setting('xtc.inlayHints.enabled', undefined);
        }
        await tour.until('The hints come back', async () => (await tour.ui.inlineTexts()).some(each => each.includes('numCards')), () => tour.ui.inlineTexts());
    });
    await tour.verify('Reference lens setting', 'xtc.codeLens.references', async () => {
        await open(tour.file('src/main/x/cardGame.x'));
        const lenses = () => tour.ui.page.locator('.editor-group-container.active .codelens-decoration').allInnerTexts();
        await tour.until('The Run lens and the references show', async () => (await lenses()).some(each => each.includes('Run')), lenses);
        await setting('xtc.codeLens.references', false);
        try {
            await tour.until('Only the Run lens stays', async () => {
                const shown = await lenses();
                return shown.some(each => each.includes('Run')) && !shown.some(each => /reference/.test(each));
            }, lenses);
        } finally {
            await setting('xtc.codeLens.references', undefined);
        }
    });
    await tour.restore();
    await tour.verifyRestored();
}

async function navigation(tour: Tour): Promise<void> {
    tour.section('navigation', 'Navigation');
    const eights = tour.source('Eights.x');
    await tour.shot({
        title: 'Go to definition',
        caption: "F12, or Ctrl+click, on a call goes to the method it calls: shuffle() is CardCollection's, which Deck inherits.",
        feature: 'textDocument/definition',
        focus: 'caret',
        settleMs: 2_000,
        act: async () => {
            await open(eights, 'deck.shuffle()', 'deck.'.length);
            await tour.ui.focusEditor();
            await command('editor.action.revealDefinition');
        },
        until: async () => path.basename(vscode.window.activeTextEditor?.document.uri.fsPath ?? '') === 'CardCollection.x',
        check: async () => {
            await editing(tour, 'CardCollection.x');
            expect(caretLine() === await lineOf(tour.source('CardCollection.x'), 'void shuffle()'), () => `At line ${caretLine()}`);
        },
    });
    await tour.verify('Go to declaration', 'textDocument/declaration', async () => {
        await open(eights, 'deck.shuffle()', 'deck.'.length);
        await command('editor.action.revealDeclaration');
        await editing(tour, 'CardCollection.x');
    });
    await tour.shot({
        title: 'Go to type definition',
        caption: 'Go to Type Definition goes to the type of what is under the cursor: deck is a Deck, declared in Deck.x.',
        feature: 'textDocument/typeDefinition',
        focus: 'editor',
        settleMs: 2_000,
        act: async () => {
            await open(eights, 'deck.shuffle()', 2);
            await command('editor.action.goToTypeDefinition');
        },
        until: async () => path.basename(vscode.window.activeTextEditor?.document.uri.fsPath ?? '') === 'Deck.x',
        check: async () => { await editing(tour, 'Deck.x'); },
    });
    await tour.shot({
        title: 'Go to implementations',
        caption: "Ctrl+F12 on an interface's method lists its implementations in a peek: FirstMatch's and SaveEights'.",
        feature: 'textDocument/implementation',
        focus: 'peek',
        settleMs: 2_500,
        act: async () => {
            await open(tour.source('PlayStrategy.x'), 'choose(Hand', 2);
            await tour.ui.focusEditor();
            await command('editor.action.goToImplementation');
        },
        until: async () => (await tour.ui.text('peek')).includes('SaveEights'),
        check: async () => {
            const peek = await tour.ui.text('peek');
            expect(peek.includes('FirstMatch') && peek.includes('SaveEights'), () => `The implementations: ${peek.slice(0, 400)}`);
        },
        after: async () => { await command('closeReferenceSearch'); },
    });
    await tour.shot({
        title: 'Find all references',
        caption: "Shift+Alt+F12 finds every use of a declaration across the project's modules, the tests' included, in the References view.",
        feature: 'textDocument/references',
        focus: 'sidebar',
        settleMs: 3_000,
        act: async () => {
            await open(tour.source('Player.x'), 'static Boolean cardMatches', 'static Boolean '.length);
            await command('references-view.findReferences');
        },
        until: async () => (await tour.ui.text('sidebar')).includes('SaveEights'),
        check: async () => {
            const shown = await tour.ui.text('sidebar');
            expect(['FirstMatch.x', 'SaveEights.x', 'Player.x', 'PlayerTest.x'].every(file => shown.includes(file)), () => `The references: ${shown.slice(0, 500)}`);
        },
    });
    await tour.shot({
        title: 'Call hierarchy',
        caption: 'Show Call Hierarchy lists who calls a method, and who calls them: cardMatches is called by the player, both strategies and a test.',
        feature: 'callHierarchy/incomingCalls',
        focus: 'sidebar',
        settleMs: 3_000,
        act: async () => {
            await open(tour.source('Player.x'), 'static Boolean cardMatches', 'static Boolean '.length);
            await command('references-view.showCallHierarchy');
        },
        until: async () => (await tour.ui.text('sidebar')).includes('choose'),
        check: async () => {
            const shown = await tour.ui.text('sidebar');
            expect(shown.includes('choose') && shown.includes('drawForMatch'), () => `The callers: ${shown.slice(0, 500)}`);
        },
    });
    await tour.verify('Outgoing calls', 'callHierarchy/outgoingCalls', async () => {
        await open(eights, 'void playGame()', 'void '.length);
        const items = await command<vscode.CallHierarchyItem[]>('vscode.prepareCallHierarchy', vscode.Uri.file(eights), positionOf('void playGame()', 'void '.length));
        const calls = await command<vscode.CallHierarchyOutgoingCall[]>('vscode.provideOutgoingCalls', items[0]);
        const names = calls.map(call => call.to.name);
        expect(['isDone', 'displayState', 'takeTurn', 'nextPlayer'].every(name => names.some(each => each.includes(name))), () => names.join(', '));
    });
    await tour.shot({
        title: 'Type hierarchy',
        caption: "Show Type Hierarchy lists a class's subtypes, or its supertypes: Deck and Hand extend CardCollection.",
        feature: 'typeHierarchy/subtypes',
        focus: 'sidebar',
        settleMs: 3_000,
        act: async () => {
            await open(tour.source('CardCollection.x'), 'class CardCollection', 'class Card'.length);
            await command('references-view.showTypeHierarchy');
        },
        until: async () => (await tour.ui.text('sidebar')).includes('Deck'),
        check: async () => {
            const shown = await tour.ui.text('sidebar');
            expect(shown.includes('Deck') && shown.includes('Hand'), () => `The subtypes: ${shown.slice(0, 500)}`);
        },
        after: async () => { await command('workbench.view.explorer'); },
    });
    await tour.verify('Supertypes', 'typeHierarchy/supertypes', async () => {
        const deck = tour.source('Deck.x');
        await open(deck, 'class Deck', 'class '.length);
        const items = await command<vscode.TypeHierarchyItem[]>('vscode.prepareTypeHierarchy', vscode.Uri.file(deck), positionOf('class Deck', 'class '.length));
        const supertypes = await command<vscode.TypeHierarchyItem[]>('vscode.provideSupertypes', items[0]);
        expect(supertypes.some(item => item.name.includes('CardCollection')), () => supertypes.map(item => item.name).join(', '));
    });
    await tour.shot({
        title: 'Go to a symbol',
        caption: 'Ctrl+T finds a class, method or property by name, in the project and the XDK; Enter goes to it.',
        feature: 'workspace/symbol',
        focus: 'quickInput',
        settleMs: 2_500,
        act: async () => {
            await command('workbench.action.showAllSymbols');
            await typeIn(tour, tour.ui.page.locator('.quick-input-widget'), 'reshuffle', 'Go to Symbol in Workspace');
        },
        until: async () => (await quickRows(tour)).some(each => each.includes('reshuffle')),
        check: async () => {
            const rows = await quickRows(tour);
            expect(rows.some(each => each.includes('reshuffle') && each.includes('Eights')), () => rows.join(' | '));
        },
    });
    await tour.verify('Open a symbol', 'workspaceSymbol/resolve', async () => {
        if (!await tour.ui.showing('quickInput')) {
            await command('workbench.action.showAllSymbols');
            await tour.ui.type('reshuffle');
            await tour.until('The symbol is listed', async () => (await quickRows(tour)).some(each => each.includes('reshuffle')), () => quickRows(tour));
        }
        await tour.ui.press('Enter');
        await editing(tour, 'Eights.x');
        expect(caretLine() === await lineOf(eights, 'void reshuffle()'), () => `At line ${caretLine()}`);
    });
    await tour.verify('Go to symbol in the file', 'textDocument/documentSymbol', async () => {
        await open(eights);
        await command('workbench.action.gotoSymbol');
        await tour.until('The file\'s symbols are listed', async () => (await quickRows(tour)).some(each => each.includes('playGame')), () => quickRows(tour));
        await tour.ui.press('Escape');
    });
    await tour.shot({
        title: "The XDK's sources",
        caption: "Go to Definition also opens the XDK's sources, read-only: HashMap is in the ecstasy module's maps package.",
        feature: 'workspace/textDocumentContent',
        focus: 'editor',
        settleMs: 3_000,
        act: async () => {
            await open(tour.source('Scoreboard.x'), 'new HashMap', 'new Hash'.length);
            await command('editor.action.revealDefinition');
        },
        until: async () => path.basename(vscode.window.activeTextEditor?.document.uri.path ?? '') === 'HashMap.x',
        check: async () => {
            const document = active().document;
            expect(path.basename(document.uri.path) === 'HashMap.x', () => `In ${document.uri.toString()}`);
            const before = { text: document.getText(), version: document.version };
            // a key typed into a read-only editor makes VS Code say so, after it has refused the edit
            await tour.ui.focusEditor();
            await tour.ui.type('x');
            await tour.until('VS Code refuses the edit', async () => /read-only/i.test(await tour.ui.text('css:.monaco-editor-overlaymessage')),
                () => `${document.uri.scheme}: ${document.getText() === before.text ? 'unchanged' : 'edited'}`);
            expect(document.getText() === before.text && document.version === before.version && !document.isDirty,
                () => `HashMap.x (${document.uri.scheme}) took an edit`);
        },
    });
    await tour.verify("One identity in the XDK's sources", 'textDocument/moniker', async () => {
        // the library's source is a read-only view that the server does not analyze, so it has no outline and no
        // navigation from it; its declarations still have the identities that the project's uses of them import
        const library = active().document;
        expect(path.basename(library.uri.path) === 'HashMap.x', () => `In ${library.uri.toString()}`);
        const monikers = (uri: string, position: vscode.Position) =>
            getClient()!.sendRequest<{ identifier: string; scheme: string; kind?: string }[] | null>('textDocument/moniker', {
                textDocument: { uri }, position: { line: position.line, character: position.character },
            }).then(found => found ?? []);
        const declared = await monikers(library.uri.toString(), library.positionAt(library.getText().indexOf('class HashMap') + 'class '.length));
        const scoreboard = tour.source('Scoreboard.x');
        const line = await lineOf(scoreboard, 'new HashMap') - 1;
        const used = await monikers(vscode.Uri.file(scoreboard).toString(),
            new vscode.Position(line, (await text(scoreboard)).split('\n')[line].indexOf('new HashMap') + 'new Hash'.length));
        expect(declared.some(each => each.kind === 'export'
            && used.some(use => use.kind === 'import' && use.scheme === each.scheme && use.identifier === each.identifier)),
            () => `HashMap.x declares ${JSON.stringify(declared)}; Scoreboard.x uses ${JSON.stringify(used)}`);
    });
    await tour.verify('Monikers', 'textDocument/moniker', async () => {
        const deck = tour.source('Deck.x');
        await open(deck);
        const monikers = await getClient()!.sendRequest<{ identifier: string; scheme: string }[] | null>('textDocument/moniker', {
            textDocument: { uri: vscode.Uri.file(deck).toString() }, position: { line: 0, character: 'class De'.length },
        });
        // an export moniker of the class, by the compiled module's identity: the same in every project that uses it
        expect((monikers ?? []).some(moniker => moniker.scheme.startsWith('ecstasy') && moniker.identifier.length > 0
            && (moniker as { kind?: string }).kind === 'export'), () => JSON.stringify(monikers));
    });
    await command('workbench.action.closeAllEditors');
    await tour.verifyRestored();
}

async function diagnostics(tour: Tour): Promise<void> {
    tour.section('diagnostics', 'Diagnostics and fixes');
    const eights = tour.source('Eights.x');
    const draw = await lineOf(eights, 'return drawPile.popCard();');
    await tour.shot({
        title: 'Errors as you type',
        caption: "A misspelled method is an error as soon as it is typed: the compiler checks the editor's text, not the saved file.",
        feature: 'textDocument/diagnostic',
        focus: `line:${draw}`,
        settleMs: 2_000,
        act: async () => {
            await open(eights);
            await replace('return drawPile.popCard();', 'return drawPile.popCrd();');
            await at('popCrd', 2);
        },
        until: () => current(eights) && errors(eights).some(item => item.message.includes('popCrd')),
        check: async () => { await hasError(tour, eights, 'popCrd'); },
    });
    await tour.shot({
        title: 'The Problems view',
        caption: "Ctrl+Shift+M opens the Problems view: the project's errors and warnings, each a click away from its code.",
        feature: 'textDocument/diagnostic',
        focus: 'panel',
        settleMs: 2_000,
        act: async () => { await command('workbench.actions.view.problems'); },
        until: async () => (await tour.ui.text('panel')).includes('popCrd'),
        check: async () => {
            const shown = await tour.ui.text('panel');
            expect(shown.includes('popCrd') && shown.includes('Eights.x'), () => `The Problems view: ${shown.slice(0, 400)}`);
        },
    });
    await tour.shot({
        title: "The error's message",
        caption: "F8 goes to the next problem and shows the compiler's message and its code under the line.",
        feature: 'editor.action.marker.next',
        focus: 'css:.marker-widget',
        settleMs: 2_000,
        act: async () => {
            await open(eights, 'class Eights');
            await tour.ui.focusEditor();
            await command('editor.action.marker.next');
        },
        check: async () => {
            const shown = await tour.ui.text('css:.marker-widget');
            expect(shown.includes('popCrd') && shown.includes('COMPILER'), () => `The message: ${shown}`);
        },
        after: async () => {
            await command('closeMarkersNavigation');
            await tour.restore();
        },
    });
    const card = tour.source('Card.x');
    await tour.shot({
        title: 'Errors in other files',
        caption: 'Renaming a property breaks its uses in other files and in the test modules, which are not open: the Problems view lists them all.',
        feature: 'workspace/diagnostic',
        focus: 'panel',
        settleMs: 3_000,
        act: async () => {
            await open(card);
            await replace('Rank rank', 'Rank rnk');
            await command('workbench.actions.view.problems');
        },
        until: () => errors(tour.source('Player.x')).length > 0 && errors(tour.test('CardTest.x')).length > 0,
        check: async () => {
            const broken = ['Player.x', 'SaveEights.x'].map(name => tour.source(name)).concat(['CardTest.x', 'DeckTest.x'].map(name => tour.test(name)));
            await tour.until('Errors in the other files and modules', () => broken.every(file => errors(file).length > 0),
                () => broken.map(file => `${path.basename(file)}: ${problems(file).length}`).join(', '));
        },
        after: async () => {
            await tour.restore();
        },
    });
    await tour.verify('The other files compile again', 'workspace/diagnostic', async () => {
        await tour.until('No errors in the project', () => vscode.languages.getDiagnostics().every(([, items]) => !items.some(item => item.severity === vscode.DiagnosticSeverity.Error)),
            () => vscode.languages.getDiagnostics().filter(([, items]) => items.some(item => item.severity === vscode.DiagnosticSeverity.Error)).map(([uri]) => path.basename(uri.fsPath)));
    });
    await tour.shot({
        title: 'Quick fixes',
        caption: "Ctrl+. on an error offers its fixes: announce doesn't exist yet, so the server offers to create it.",
        feature: 'textDocument/codeAction',
        focus: 'menu',
        settleMs: 2_000,
        act: async () => {
            await open(eights, 'Player current = one;', 'Player current = one;'.length);
            await tour.ui.focusEditor();
            await tour.ui.press('Enter');
            await typeCode(tour, 'announce(current);');
            await hasError(tour, eights, 'announce');
            await at('announce(current)', 2);
            await codeActions(tour, 'Create private method');
        },
        check: async () => {
            const rows = await actionRows(tour);
            expect(rows.some(each => each.includes("Create private method 'announce'")), () => rows.join(' | '));
        },
    });
    await tour.shot({
        title: 'Create a method',
        caption: 'The fix writes the method, with the parameter types that the call implies, for its body to be filled in.',
        feature: 'codeAction/resolve',
        focus: 'editor',
        settleMs: 3_000,
        act: async () => { await chooseAction(tour, 'Create private method'); },
        until: async () => /announce\(Player/.test(await text(eights)),
        check: async () => {
            expect(/announce\(Player/.test(await text(eights)), () => 'No announce method in Eights.x');
            await compiles(tour, eights);
        },
        after: async () => { await tour.restore(); },
    });
    const deckTest = tour.test('DeckTest.x');
    await tour.shot({
        title: 'Add an import',
        caption: "A type that isn't imported has a fix that adds its import, here in a test module that imports the game's types.",
        feature: 'textDocument/codeAction',
        focus: 'menu',
        settleMs: 2_000,
        act: async () => {
            await open(deckTest);
            await replace('    import cardGame.Suit;\n', '');
            await hasError(tour, deckTest, 'Suit');
            await at('Suit.values', 1);
            await codeActions(tour, IMPORT_SUIT);
        },
        check: async () => {
            const rows = await actionRows(tour);
            expect(rows.some(each => IMPORT_SUIT.test(each)), () => rows.join(' | '));
            await chooseAction(tour, IMPORT_SUIT);
            await tour.until('The import is back', async () => (await text(deckTest)).includes('import cardGame.Suit;'), () => text(deckTest));
            await compiles(tour, deckTest);
        },
        after: async () => { await tour.restore(); },
    });
    const playerTest = tour.test('PlayerTest.x');
    await tour.shot({
        title: 'Organize imports',
        caption: 'Shift+Alt+O removes the imports that nothing uses and sorts the rest; an unused import is faded until then.',
        feature: 'source.organizeImports',
        focus: 'editor',
        settleMs: 3_000,
        act: async () => {
            await open(playerTest);
            await replace('    import cardGame.Card;\n', '    import cardGame.Card;\n    import cardGame.Deck;\n');
            await compiles(tour, playerTest);
            // the action is the analysis's: one that a newer analysis retires is asked for again, as a user would, until
            // the import goes or a hang's bound ends; the pause between the asks decides nothing
            const unused = async () => (await text(playerTest)).includes('import cardGame.Deck;');
            await tour.until('Organize Imports removes the unused import', async () => {
                if (!await unused()) return true;
                await tour.ui.focusEditor();
                await command('editor.action.organizeImports');
                return tour.within(10_000, async () => !await unused());
            }, () => text(playerTest));
        },
        until: async () => !(await text(playerTest)).includes('import cardGame.Deck;'),
        check: async () => {
            const source = await text(playerTest);
            expect(!source.includes('import cardGame.Deck;'), () => 'The unused import is still there');
            const imports = source.split('\n').filter(line => line.trim().startsWith('import '));
            expect(imports.join('\n') === [...imports].sort().join('\n'), () => `Not sorted: ${imports.join(', ')}`);
            await compiles(tour, playerTest);
        },
        after: async () => { await tour.restore(); },
    });
    const player = tour.source('Player.x');
    await tour.verify('Warnings', 'textDocument/diagnostic', async () => {
        await open(player);
        await replace('                hand.addCard(card);', '                assert card.is(Card);\n                hand.addCard(card);');
        await tour.until('A warning on the assertion', () => current(player) && vscode.languages.getDiagnostics(vscode.Uri.file(player))
            .some(item => item.severity === vscode.DiagnosticSeverity.Warning), () => problems(player));
        await tour.restore();
    });
    await tour.verify('Generate a doc comment', 'codeAction/resolve', async () => {
        await open(eights, 'Player nextPlayer(', 'Player next'.length);
        // a source action, which Source Action... lists, not Quick Fix
        await codeActions(tour, 'Generate documentation comment', 'editor.action.sourceAction');
        await chooseAction(tour, 'Generate documentation comment');
        await tour.until('A doc comment on nextPlayer', async () => /\/\*\*[^/]*\*\/\s*Player nextPlayer/.test(await text(eights)), () => text(eights));
        await compiles(tour, eights);
        await tour.restore();
    });
    await command('workbench.action.closePanel');
    await tour.restore();
    await tour.verifyRestored();
}

async function refactoring(tour: Tour): Promise<void> {
    tour.section('refactoring', 'Refactoring');
    const player = tour.source('Player.x');
    const eights = tour.source('Eights.x');
    await tour.shot({
        title: 'Rename a method',
        caption: 'F2 renames a method: the box takes the new name, which the server checks before anything changes.',
        feature: 'textDocument/prepareRename',
        focus: 'css:.rename-box',
        settleMs: 2_000,
        act: async () => {
            await open(player, 'return drawForMatch(eights, prev);', 'return draw'.length);
            await tour.ui.focusEditor();
            await launch('editor.action.rename');
            await tour.until('The rename box', () => tour.ui.showing('css:.rename-box'));
            await tour.ui.press('Control+a');
            await tour.ui.type('drawUntilMatch');
        },
        check: async () => {
            // the box has the new name, the server's prepareRename has said what it renames, and nothing has changed
            const value = await tour.ui.page.locator('.rename-box input').first().inputValue({ timeout: 2_000 }).catch(() => '');
            expect(value === 'drawUntilMatch', () => `The rename box has ${JSON.stringify(value)}`);
            const prepared = await command<{ range: vscode.Range; placeholder: string } | undefined>('vscode.prepareRename',
                active().document.uri, positionOf('drawForMatch(eights, prev)', 'draw'.length));
            expect(prepared?.placeholder === 'drawForMatch', () => `prepareRename answered ${JSON.stringify(prepared)}`);
            expect((await text(player)).includes('drawForMatch(eights, prev)'), () => 'Player.x changed before Enter');
        },
    });
    await tour.shot({
        title: 'Renamed everywhere',
        caption: 'Enter renames the declaration and every use, in every file of the project, as one change that Undo reverts.',
        feature: 'xtc/rename',
        focus: 'editor',
        settleMs: 3_000,
        act: async () => { await tour.ui.press('Enter'); },
        until: async () => (await text(player)).includes('drawUntilMatch(eights, prev)'),
        check: async () => {
            const source = await text(player);
            expect(!source.includes('drawForMatch') && source.includes('conditional Card drawUntilMatch('), () => 'Player.x was not renamed');
            await compiles(tour, player);
        },
    });
    await tour.verify('Undo a rename', 'xtc/rename', async () => {
        await open(player);
        await tour.ui.focusEditor();
        await command('undo');
        // undoing a change of several files asks whether to undo it in all of them
        const dialog = tour.ui.page.locator('.monaco-dialog-box .monaco-button').filter({ hasText: /Undo in/ }).first();
        const undone = async () => (await text(player)).includes('drawForMatch(eights, prev)');
        await tour.until('The undo asks or is done', async () => await dialog.count() > 0 || await undone(), () => text(player));
        if (await dialog.count() > 0) await tour.ui.click(dialog);
        await tour.until('The rename is undone', undone, () => text(player));
        await tour.restore();
    });
    await tour.shot({
        title: 'Rename a class',
        caption: "Renaming a class renames its file too, as Ecstasy's one type per file expects, and every use of it.",
        feature: 'xtc/rename',
        focus: 'editor',
        settleMs: 3_000,
        act: async () => {
            await open(tour.source('Scoreboard.x'), 'service Scoreboard', 'service Score'.length);
            await tour.ui.focusEditor();
            await launch('editor.action.rename');
            await tour.until('The rename box', () => tour.ui.showing('css:.rename-box'));
            await tour.ui.press('Control+a');
            await tour.ui.type('Tally');
            await tour.ui.press('Enter');
            await tour.until('Eights.x uses Tally', async () => (await text(eights)).includes('public Tally scoreboard'), () => text(eights));
            await open(eights, 'public Tally scoreboard');
        },
        // the file's rename follows the edits, once VS Code's File Rename participants, the server's among them, answer
        until: async () => (await text(tour.source('Tally.x'))).includes('service Tally'),
        check: async () => {
            const tally = await text(tour.source('Tally.x'));
            expect(tally.includes('service Tally') && !(await text(eights)).includes('Scoreboard'), () => 'Scoreboard was not renamed to Tally everywhere');
            await compiles(tour, eights);
        },
        after: async () => { await tour.restore(); },
    });
    const hand = tour.source('Hand.x');
    await tour.shot({
        title: 'Rename a file',
        caption: "F2 in the Explorer renames a file, and the class in it with it: Hand.x becomes PlayerHand.x, and its uses PlayerHand.",
        feature: 'workspace/willRenameFiles',
        focus: 'sidebar',
        settleMs: 3_000,
        act: async () => {
            await command('workbench.view.explorer');
            await reveal(tour, vscode.Uri.file(hand));
            await launch('renameFile');
            await tour.until('The name box', () => tour.ui.showing('css:.explorer-folders-view .monaco-inputbox input'));
            await tour.ui.press('Control+a');
            await tour.ui.type('PlayerHand.x');
            await tour.ui.press('Enter');
            await tour.until('Player.x uses PlayerHand', async () => (await text(player)).includes('PlayerHand'), () => text(player));
        },
        check: async () => {
            const renamed = await text(tour.source('PlayerHand.x'));
            expect(renamed.includes('class PlayerHand'), () => `PlayerHand.x: ${renamed.slice(0, 200)}`);
            await open(player);
            await compiles(tour, player);
        },
        after: async () => { await tour.restore(); },
    });
    await tour.shot({
        title: 'Move a file',
        caption: "Cut and Paste in the Explorer moves a type to another package: Suit moves to cards, and every use follows, as cards.Suit, or in Deck, whose cards is a property, by an import.",
        feature: 'xtc/renameFiles',
        focus: 'sidebar',
        settleMs: 3_000,
        act: async () => {
            await command('workbench.view.explorer');
            await reveal(tour, vscode.Uri.file(tour.source('Card.x')));
            await launch('explorer.newFolder');
            await tour.until('The folder name box', () => tour.ui.showing('css:.explorer-folders-view .monaco-inputbox input'));
            await tour.ui.type('cards');
            await tour.ui.press('Enter');
            await tour.until('The cards folder', () => fs.stat(tour.source('cards')).then(() => true, () => false));
            await reveal(tour, vscode.Uri.file(tour.source('Suit.x')));
            await command('filesExplorer.cut');
            await reveal(tour, vscode.Uri.file(tour.source('cards')));
            // the Explorer moves the file, and then fails to find the row of where it was, a TreeError: the file in its
            // new place, and the edits of its uses, are what the step checks
            await Promise.resolve(command('filesExplorer.paste')).catch((error: unknown) => {
                if (!/TreeError .*Data tree node not found/.test(String(error))) throw error;
            });
            await tour.until('Suit.x is in cards', () => fs.stat(tour.source('cards/Suit.x')).then(() => true, () => false));
        },
        check: async () => {
            await tour.until('Card.x follows the move', async () => (await text(tour.source('Card.x'))).includes('cards.Suit'), () => text(tour.source('Card.x')));
            // in Deck, which inherits CardCollection's cards, that name is the property: Deck imports Suit instead
            const deck = await text(tour.source('Deck.x'));
            expect(deck.includes('import cards.Suit;'), () => deck.slice(0, 300));
            await open(tour.source('Card.x'));
            await compiles(tour, tour.source('Card.x'));
            await open(tour.test('DeckTest.x'));
            await compiles(tour, tour.test('DeckTest.x'));
        },
        after: async () => { await tour.restore(); },
    });
    await tour.verify('Create and delete a file', 'workspace/didCreateFiles', async () => {
        const joker = tour.source('Joker.x');
        // the client's trace of its messages, with their parameters, in the server's log that logs/ keeps: what each
        // didChange of the new file carried, if it does not compile
        await setting('xtc.trace.server', 'verbose');
        try {
            await createAndDelete(tour, joker);
        } finally {
            await setting('xtc.trace.server', undefined);
        }
    });
    await tour.shot({
        title: 'Refactorings',
        caption: "Ctrl+Shift+R on a selection offers the server's refactorings: extract it to a local or a method.",
        feature: 'textDocument/codeAction',
        focus: 'menu',
        settleMs: 2_000,
        act: async () => {
            await open(eights);
            await select('current == one ? two : one');
            await codeActions(tour, 'Extract expression to local', 'editor.action.refactor');
        },
        check: async () => {
            const rows = await actionRows(tour);
            expect(rows.some(each => each.includes('Extract expression to local')), () => rows.join(' | '));
        },
    });
    await tour.shot({
        title: 'Extracted to a local',
        caption: 'The returned expression is now a local variable, of the type the method returns, and the return reads it.',
        feature: 'codeAction/resolve',
        focus: 'editor',
        settleMs: 3_000,
        act: async () => { await chooseAction(tour, 'Extract expression to local'); },
        until: async () => /\n\s+Player \w+ = current == one \? two : one;/.test(await text(eights)),
        check: async () => {
            expect(/\n\s+Player \w+ = current == one \? two : one;\n\s+return \w+;/.test(await text(eights)), () => 'No local in nextPlayer');
            await compiles(tour, eights);
        },
        after: async () => {
            await command('workbench.action.focusActiveEditorGroup');
            await tour.ui.press('Escape');
            await tour.restore();
        },
    });
    await tour.shot({
        title: 'Extract a method',
        caption: 'Statements extract to a private method too: the three lines that print the hand sizes become one call.',
        feature: 'codeAction/resolve',
        focus: 'editor',
        settleMs: 3_000,
        act: async () => {
            await open(eights);
            await select('console.print($"{one.name} hand size: {one.hand.size()}");\n        console.print($"{two.name} hand size: {two.hand.size()}");\n        console.print($"Draw pile size: {drawPile.size()}");');
            await codeActions(tour, 'Extract statements to private method', 'editor.action.refactor');
            await chooseAction(tour, 'Extract statements to private method');
        },
        until: async () => /private void \w+\(/.test(await text(eights)),
        check: async () => {
            expect(/private void \w+\(/.test(await text(eights)), () => 'No extracted method in Eights.x');
            await compiles(tour, eights);
        },
        after: async () => {
            await tour.ui.press('Escape');
            await tour.restore();
        },
    });
    await tour.verify('Safe delete', 'textDocument/codeAction', async () => {
        await open(tour.source('Hand.x'), 'void display()', 'void dis'.length);
        await codeActions(tour, 'Safely delete');
        await tour.ui.press('Escape');
    });
    await tour.restore();
    await tour.verifyRestored();
}

/** Creates [joker] in the Explorer, fills it in its editor, checks that it compiles, and deletes it in the Explorer. */
async function createAndDelete(tour: Tour, joker: string): Promise<void> {
    await command('workbench.view.explorer');
    await reveal(tour, vscode.Uri.file(tour.source('Card.x')));
    await launch('explorer.newFile');
    await tour.until('The file name box', () => tour.ui.showing('css:.explorer-folders-view .monaco-inputbox input'));
    await tour.ui.type('Joker.x');
    await tour.ui.press('Enter');
    await editing(tour, 'Joker.x');
    // the new file's editor, which the Explorer opens, takes the class's text
    await open(joker);
    await active().edit(edit => edit.insert(new vscode.Position(0, 0), 'const Joker {}\n'));
    await vscode.workspace.saveAll(false);
    // a file that does not compile is deleted all the same, which the deletion's own operations need
    const failure = await compiles(tour, joker).then(() => undefined, async (error: Error) => {
        // whether the server's text of the file is the editor's: its outline of it has Joker then; and the module graph
        // that the server compiles it in
        const symbols = await vscode.commands.executeCommand<vscode.DocumentSymbol[] | undefined>('vscode.executeDocumentSymbolProvider', vscode.Uri.file(joker));
        const modules = await getClient()?.sendRequest('xtc/compilerSourceModules').catch((reason: unknown) => String(reason));
        return new Error(`${error.message}; the server's outline of it: ${(symbols ?? []).map(each => each.name).join(', ') || 'empty'}; `
            + `its modules: ${JSON.stringify(modules)}`);
    });
    await reveal(tour, vscode.Uri.file(joker));
    await launch('deleteFile');
    // deleting asks first, in VS Code's own dialog
    const confirm = tour.ui.page.locator('.monaco-dialog-box .monaco-button').filter({ hasText: /Delete/ }).first();
    const deleted = () => fs.stat(joker).then(() => false, () => true);
    await tour.until('The delete asks or is done', async () => await confirm.count() > 0 || await deleted());
    if (await confirm.count() > 0) await tour.ui.click(confirm);
    await tour.until('Joker.x is deleted', deleted);
    if (failure) throw failure;
}

async function commands(tour: Tour): Promise<void> {
    tour.section('commands', "The extension's commands");
    await tour.shot({
        title: "The server's log",
        caption: "Ctrl+Alt+X L shows the language server's log, and again hides it: what it started with, and what it did.",
        feature: 'xtc.showServerOutput',
        focus: 'panel',
        settleMs: 2_000,
        act: async () => {
            await open(tour.source('Eights.x'));
            await tour.ui.focusEditor();
            await tour.ui.press('Control+Alt+KeyX', 'KeyL');
        },
        until: async () => output('Ecstasy Language Server').length > 0,
        check: async () => {
            const log = output('Ecstasy Language Server');
            expect(log.includes('Starting Ecstasy Language Server'), () => `The log: ${log.slice(0, 300)}`);
        },
    });
    await tour.verify("Hide the server's log", 'xtc.hideServerOutput', async () => {
        await tour.ui.press('Control+Alt+KeyX', 'KeyL');
        await tour.until('The log hides', async () => !(await tour.ui.showing('panel')) || !(await tour.ui.text('panel')).includes('Starting Ecstasy Language Server'),
            () => tour.ui.text('panel'));
    });
    await tour.shot({
        title: 'The effective configuration',
        caption: 'Ecstasy: Show Effective Language Service Configuration reports the settings the server runs with, and what it found.',
        feature: 'xtc/languageServiceStatus',
        focus: 'panel',
        settleMs: 2_000,
        act: async () => { await command('xtc.showLanguageServiceStatus'); },
        until: async () => output('Ecstasy Effective Configuration').includes('effective'),
        check: async () => {
            const report = output('Ecstasy Effective Configuration');
            expect(report.includes('"adapter"') && report.includes('XDK'), () => report.slice(0, 400));
        },
    });
    await tour.shot({
        title: 'Compiler paths',
        caption: "Ecstasy: Show Effective Compiler Paths lists the modules the compiler builds, from the project's layout or its Gradle build.",
        feature: 'xtc/compilerSourceModules',
        focus: 'panel',
        settleMs: 2_000,
        act: async () => { await command('xtc.showCompilerPaths'); },
        until: async () => output('Ecstasy Compiler Paths').includes('cardGame'),
        check: async () => {
            const report = output('Ecstasy Compiler Paths');
            expect(['cardGame', 'DeckTest', 'PlayerTest'].every(name => report.includes(name)), () => report.slice(0, 600));
        },
    });
    await tour.shot({
        title: 'Configure compiler paths',
        caption: 'Ecstasy: Configure Compiler Paths edits what the compiler builds: explicit modules, consumers from other folders, or the Gradle model.',
        feature: 'xtc.configureCompilerPaths',
        focus: 'quickInput',
        settleMs: 2_000,
        act: async () => { await launch('xtc.configureCompilerPaths'); },
        until: async () => (await quickRows(tour)).some(each => each.includes('Show effective paths')),
        check: async () => {
            const rows = await quickRows(tour);
            expect(['Refresh Gradle model', 'Edit explicit module paths', 'Add consumers from folders'].every(each => rows.some(shown => shown.includes(each))), () => rows.join(' | '));
        },
        after: async () => { await tour.ui.press('Escape'); },
    });
    await tour.verify('Add consumers', 'xtc/discoverConsumers', async () => {
        await launch('xtc.configureCompilerPaths');
        await tour.ui.pick('Add consumers from folders', 'Add consumers');
        await fileDialog(tour, tour.file('src/test'));
        await tour.until('The search answers', async () => (await tour.ui.text('notification')).length > 0 || (await quickRows(tour)).length > 0,
            () => tour.ui.text('notification'));
        const notice = await tour.ui.text('notification');
        await tour.ui.press('Escape');
        await command('notifications.clearAll');
        expect(/imports the current source graph|already taken/.test(notice) || notice === '', () => `The search: ${notice}`);
    });
    await tour.shot({
        title: 'Libraries and sources',
        caption: 'Ecstasy: Configure Libraries and Sources sets the compiled modules the project builds against, and their sources.',
        feature: 'xtc.configureCompilerLibraries',
        focus: 'quickInput',
        settleMs: 2_000,
        act: async () => { await launch('xtc.configureCompilerLibraries'); },
        until: () => tour.ui.showing('quickInput'),
        check: async () => {
            const rows = await quickRows(tour);
            expect(rows.length > 0, () => 'No choices');
        },
        after: async () => { await tour.ui.press('Escape'); },
    });
    await tour.shot({
        title: 'Switch the language adapter',
        caption: "Ctrl+Alt+X A, or a click on Ecstasy in the status bar, switches the server's adapter: the compiler's, or Tree-sitter's syntax only.",
        feature: 'xtc.selectLanguageAdapter',
        focus: 'quickInput',
        settleMs: 2_000,
        act: async () => {
            await open(tour.source('Eights.x'));
            await tour.ui.focusEditor();
            await tour.ui.press('Control+Alt+KeyX', 'KeyA');
        },
        until: async () => (await quickRows(tour)).some(each => each.includes('Tree-sitter')),
        check: async () => {
            await tour.ui.pick('Tree-sitter');
            await tour.until('The Tree-sitter adapter runs', async () => (await tour.ui.text('status:Ecstasy')).includes('Tree-sitter'), () => tour.ui.text('status:Ecstasy'));
        },
    });
    await tour.verify('Back to the compiler', 'xtc.selectLanguageAdapter', async () => {
        await command('xtc.selectLanguageAdapter', 'compiler');
        await tour.until('The compiler adapter runs', async () => (await tour.ui.text('status:Ecstasy')).includes('Compiler'), () => tour.ui.text('status:Ecstasy'));
        await setting('xtc.languageService.adapter', undefined);
        await tour.until('The default adapter runs', async () => (await tour.ui.text('status:Ecstasy')).includes('Compiler') && !!getClient()?.isRunning(), () => tour.ui.text('status:Ecstasy'));
        await compiles(tour, tour.source('Eights.x'));
    });
    await tour.verify('Restart the language server', 'xtc.restartServer', async () => {
        const before = getClient();
        await command('xtc.restartServer');
        await tour.until('A new server runs', () => getClient() !== before && !!getClient()?.isRunning(), () => getClient()?.state);
        await open(tour.source('Eights.x'));
        await replace('return drawPile.popCard();', 'return drawPile.popCrd();');
        await hasError(tour, tour.source('Eights.x'), 'popCrd');
        await tour.restore();
        await compiles(tour, tour.source('Eights.x'));
    });
    const archive = path.join(os.homedir(), 'ecstasy-server-logs.zip');
    await tour.shot({
        title: "Export the server's logs",
        caption: "Ecstasy: Export Server Logs saves the server's logs to a ZIP, to attach to a report when something goes wrong.",
        feature: 'xtc/exportLogs',
        focus: 'quickInput',
        settleMs: 2_000,
        act: async () => {
            await fs.rm(archive, { force: true });
            await launch('xtc.exportServerLogs');
        },
        until: () => tour.ui.showing('quickInput'),
        check: async () => {
            await tour.ui.press('Enter');
            await tour.until('The archive is saved', () => fs.stat(archive).then(stat => stat.size > 0, () => false));
            const head = (await fs.readFile(archive)).subarray(0, 2).toString('latin1');
            expect(head === 'PK', () => `Not a ZIP: ${head}`);
        },
        after: async () => { await fs.rm(archive, { force: true }); },
    });
    const created = path.join(path.dirname(tour.project), 'hello');
    await tour.shot({
        title: 'Create a project',
        caption: 'Ecstasy: Create New Project asks for a name, a kind and a folder, and runs xtc init in the terminal: a Gradle project, ready to build.',
        feature: 'xtc.createProject',
        focus: 'panel',
        settleMs: 3_000,
        act: async () => {
            await fs.rm(created, { recursive: true, force: true });
            await launch('xtc.createProject');
            await answer(tour, 'hello');
            await tour.ui.pick('Application');
            await fileDialog(tour, path.dirname(tour.project));
        },
        until: () => fs.stat(path.join(created, 'build.gradle.kts')).then(() => true, () => false),
        check: async () => {
            expect(await fs.stat(path.join(created, 'src', 'main', 'x', 'hello.x')).then(() => true, () => false), () => 'No hello.x');
        },
        after: async () => {
            terminal('Ecstasy')?.dispose();
            await fs.rm(created, { recursive: true, force: true });
            await command('workbench.action.closePanel');
        },
    });
    await tour.verifyRestored();
}

async function building(tour: Tour): Promise<void> {
    tour.section('building', 'Build and run');
    tour.card('Build and run', 'Ecstasy projects build and run two ways in VS Code, chosen per folder: embedded, or with Gradle.', {
        eyebrow: 'BUILD AND RUN',
        heading: 'Embedded, or Gradle',
        lead: 'The same Build and Run commands, keys, lenses and tasks, for each',
        boxes: [
            { title: 'Embedded', text: "The language server's compiler and execution worker: no Gradle, and the fastest round trip." },
            { title: 'Gradle', text: "The XTC Gradle plugin's compileXtc, runXtc and testXtc, with the build's own settings and XDK." },
        ],
    });
    const module = tour.file('src/main/x/cardGame.x');
    const eights = tour.source('Eights.x');
    await tour.shot({
        title: 'The build mode',
        caption: "In a folder with a Gradle build, the status bar says how it builds; a click chooses the embedded compiler, Gradle, or automatic.",
        feature: 'xtc.selectBuildMode',
        focus: 'quickInput',
        settleMs: 2_000,
        act: async () => {
            await open(module);
            await tour.until('The build mode shows', () => buildModeStatus() !== undefined);
            await tour.ui.click('status:Embedded build');
        },
        until: async () => (await quickRows(tour)).some(each => each.includes('Gradle')),
        check: async () => {
            const rows = await quickRows(tour);
            expect(['embedded', 'Gradle', 'Automatic'].every(each => rows.some(shown => shown.includes(each))), () => rows.join(' | '));
        },
        after: async () => { await tour.ui.press('Escape'); },
    });
    await tour.shot({
        title: 'Build',
        caption: "Ctrl+F9 builds every module with the language server's compiler, without Gradle, in the terminal.",
        feature: 'xtc/build',
        focus: 'panel',
        settleMs: 2_000,
        act: async () => {
            tour.forgetExecutions();
            await open(eights);
            await tour.ui.focusEditor();
            await tour.ui.press('Control+F9');
        },
        until: () => tour.execution('build', 'all Ecstasy modules')?.exitCode !== undefined,
        check: async () => {
            const build = tour.execution('build', 'all Ecstasy modules');
            expect(build?.exitCode === 0 && build.text.includes('Build succeeded.') && build.text.includes('cardGame'), () => build?.text ?? 'No build');
        },
    });
    await tour.shot({
        title: 'A failed build',
        caption: "A build's errors are listed with their files and lines, each a click away from its code.",
        feature: 'xtc/build',
        focus: 'panel',
        settleMs: 2_000,
        act: async () => {
            tour.forgetExecutions();
            await open(eights);
            await replace('return drawPile.popCard();', 'return drawPile.popCrd();');
            await tour.ui.focusEditor();
            await tour.ui.press('Control+F9');
        },
        until: () => tour.execution('build', 'all Ecstasy modules')?.exitCode !== undefined,
        check: async () => {
            const build = tour.execution('build', 'all Ecstasy modules');
            expect(build?.exitCode === 1 && build.text.includes('Eights.x:') && build.text.includes('popCrd'), () => build?.text ?? 'No build');
        },
        after: async () => { await tour.restore(); },
    });
    await tour.verify('Rebuild', 'xtc.rebuild', async () => {
        tour.forgetExecutions();
        await command('xtc.rebuild');
        await tour.until('The rebuild ends', () => tour.execution('build', 'all Ecstasy modules')?.exitCode !== undefined);
        const build = tour.execution('build', 'all Ecstasy modules')!;
        expect(build.exitCode === 0 && build.text.includes('Rebuilding'), () => build.text);
    });
    await tour.shot({
        title: 'Run from the editor',
        caption: "The Run button of a module's editor runs it in the embedded execution worker, beside the language server, in a terminal of its own.",
        feature: 'xtc/run',
        focus: 'panel',
        settleMs: 2_000,
        act: async () => {
            tour.forgetExecutions();
            await open(module);
            await tour.ui.click(tour.ui.page.locator('.editor-group-container.active .editor-actions .action-label[aria-label*="Run Module"]').first());
        },
        until: () => tour.execution('run', 'cardGame')?.text.includes('Press Enter') ?? false,
        check: async () => {
            const run = tour.execution('run', 'cardGame')!;
            expect(run.text.includes('Starting Eights game.'), () => run.text);
        },
    });
    await tour.shot({
        title: 'Program input',
        caption: "The program reads what is typed in its terminal: each Enter plays a turn.",
        feature: 'xtc/runInput',
        focus: 'panel',
        settleMs: 2_000,
        act: async () => {
            await command('workbench.action.terminal.focus');
            // a line for each prompt: the second once the game has read the first and asks again
            const prompts = () => (tour.execution('run', 'cardGame')?.text.split('Press Enter').length ?? 1) - 1;
            const before = prompts();
            await tour.ui.press('Enter');
            await tour.until('The game asks again', () => prompts() > before, () => tour.execution('run', 'cardGame')?.text.slice(-300));
            await tour.ui.press('Enter');
        },
        until: () => (tour.execution('run', 'cardGame')?.text.split('Press Enter').length ?? 0) > 3,
        check: async () => {
            const run = tour.execution('run', 'cardGame')!.text;
            expect(/ plays | draws | passes/.test(run), () => `No turn played: ${run.slice(-300)}`);
        },
    });
    await tour.verify('Stop the program', 'xtc/stopRun', async () => {
        const ended = () => tour.execution('run', 'cardGame')?.exitCode !== undefined;
        await launch('workbench.action.tasks.terminate');
        // Terminate Task asks which task only while more than one runs
        await tour.until('The run ends or Terminate Task asks which', async () => ended() || await tour.ui.showing('quickInput'));
        if (!ended()) await tour.ui.pick('Run cardGame');
        await tour.until('The run ends', ended, () => tour.execution('run', 'cardGame')?.text.slice(-200));
        // the run says it was stopped, with nothing of the failure that the stop makes in the program
        const shown = tour.execution('run', 'cardGame')?.text ?? '';
        expect(shown.trimEnd().endsWith('Process stopped') && !shown.includes('Container is terminating'), () => shown.slice(-400));
    });
    await tour.verify('The Run lens', 'textDocument/codeLens', async () => {
        tour.forgetExecutions();
        await open(module);
        await tour.ui.click(tour.ui.page.locator('.editor-group-container.active .codelens-decoration a').filter({ hasText: 'Run cardGame' }).first());
        await tour.until('The lens runs the game', () => tour.execution('run', 'cardGame')?.text.includes('Starting Eights game.') ?? false, () => tour.execution('run', 'cardGame')?.text);
        await tour.stopAll();
    });
    await tour.verify('Run with a key', 'xtc.runModule', async () => {
        tour.forgetExecutions();
        await open(module);
        await tour.ui.focusEditor();
        await tour.ui.press('Control+Shift+F10');
        await tour.until('Ctrl+Shift+F10 runs the game', () => tour.execution('run', 'cardGame')?.text.includes('Starting Eights game.') ?? false, () => tour.execution('run', 'cardGame')?.text);
        await tour.stopAll();
    });
    await tour.verify("The editor's menu", 'editor/context', async () => {
        await open(module, 'module cardGame');
        await tour.ui.click('editor', 'right');
        await tour.until('The menu has the Ecstasy commands', async () => {
            const menu = await tour.ui.text('menu');
            return menu.includes('Ecstasy: Run Module') && menu.includes('Ecstasy: Build');
        }, () => tour.ui.text('menu'));
        await tour.ui.press('Escape');
    });
    await tour.verify('A task with arguments', 'taskDefinitions', () => playWithArguments(tour, 'embedded'));
    await tour.shot({
        title: 'Run with Gradle',
        caption: "In Gradle mode, the same Run runs the XTC Gradle plugin's runXtc task, with the build's settings and XDK, in the terminal.",
        feature: 'xtc.runModule',
        focus: 'panel',
        settleMs: 2_000,
        act: async () => {
            await buildMode(tour, 'gradle');
            await tour.until('The status bar says Gradle', () => buildModeStatus()?.includes('Gradle') ?? false, () => buildModeStatus());
            await open(module);
            await tour.ui.click(tour.ui.page.locator('.editor-group-container.active .editor-actions .action-label[aria-label*="Run Module"]').first());
        },
        until: async () => (await tour.ui.terminalText()).includes('Press Enter'),
        check: async () => {
            // runXtc's program has no input in Gradle's terminal, so it plays to its end, and the terminal shows its end
            await tour.until('The game ends', async () => /\w+ wins!/.test(await tour.ui.terminalText()), async () => (await tour.ui.terminalText()).slice(-400));
            const shown = await tour.ui.terminalText();
            expect(/ plays | draws /.test(shown), () => shown.slice(-400));
        },
        after: async () => { await tour.stopAll(); },
    });
    await tour.verify('A Gradle task with arguments', 'taskDefinitions', () => playWithArguments(tour, 'gradle'));
    await tour.shot({
        title: 'Tasks',
        caption: "Run Task lists the extension's tasks: in Gradle mode the build's Build, Test and Clean, beside the tasks.json ones.",
        feature: 'taskDefinitions',
        focus: 'quickInput',
        settleMs: 2_500,
        act: async () => {
            await launch('workbench.action.tasks.runTask');
            await tour.ui.pick('xtc');
        },
        until: async () => (await quickRows(tour)).some(each => /Build|Clean|Test/.test(each)),
        check: async () => {
            const rows = await quickRows(tour);
            expect(['Build', 'Test', 'Clean'].every(name => rows.some(each => each.includes(name))), () => rows.join(' | '));
        },
        after: async () => { await tour.ui.press('Escape'); },
    });
    await tour.verify('A Gradle build', 'xtc.build', async () => {
        const before = tour.taskExits.length;
        await open(eights);
        await tour.ui.focusEditor();
        await tour.ui.press('Control+F9');
        await tour.until('The Gradle build ends', () => tour.taskExits.slice(before).some(each => each.name === 'Build'), () => tour.ui.terminalText());
        const exit = tour.taskExits.slice(before).find(each => each.name === 'Build')!;
        await ensure(exit.exitCode === 0, async () => `Exit code ${exit.exitCode}: ${(await tour.ui.terminalText()).slice(-400)}`);
    });
    await tour.verify('A failed Gradle build', 'problemMatchers.xtc', async () => {
        const before = tour.taskExits.length;
        const deck = tour.source('Deck.x');
        await fs.writeFile(deck, (await fs.readFile(deck, 'utf8')).replace('addCard(new Card(suit, rank));', 'addCard(new Card(suit, rnk));'));
        await command('xtc.build');
        await tour.until('The Gradle build fails', () => tour.taskExits.slice(before).some(each => each.name === 'Build'), () => tour.ui.terminalText());
        expect(tour.taskExits.slice(before).find(each => each.name === 'Build')!.exitCode !== 0, () => 'The build passed');
        const matcher = /^.*?(Error|Warning|Info): (.+\.x):\d+ \[(\d+):(\d+)\.\.(\d+):(\d+)\] (\S+): (.*)$/m;
        const shown = await tour.ui.terminalText();
        expect(matcher.test(shown.replace(/\s+\n/g, '\n')) || errors(deck).some(item => item.message.includes('rnk')), () => `Nothing the $xtc problem matcher reads: ${shown.slice(-600)}`);
        await tour.restore();
        // the problem matcher's errors stay until the task runs again: the build of the sources as they were clears them
        const after = tour.taskExits.length;
        await command('xtc.build');
        await tour.until('The Gradle build passes again', () => tour.taskExits.slice(after).some(each => each.name === 'Build'), () => tour.ui.terminalText());
        expect(tour.taskExits.slice(after).find(each => each.name === 'Build')!.exitCode === 0, () => 'The build of the restored sources failed');
        await tour.until("The problem matcher's errors are gone", () => !errors(deck).some(item => item.message.includes('rnk')), () => errors(deck).map(item => item.message));
    });
    await tour.verify('Refresh the Gradle model', 'xtc.refreshCompilerBuild', async () => {
        const model = tour.file('.gradle/xtc/lsp-model.json');
        await fs.rm(model, { force: true });
        await command('xtc.refreshCompilerBuild');
        await tour.until('The Gradle model is written', () => fs.stat(model).then(() => true, () => false), () => output('Ecstasy Compiler Paths').slice(-300));
    });
    await tour.verify('Open the build file', 'xtc.configureCompilerPaths', async () => {
        // the Gradle model names the build file of each source set, which Configure Compiler Paths opens
        await launch('xtc.configureCompilerPaths');
        await tour.ui.pick('Open build file');
        await tour.ui.pick('build.gradle.kts');
        await editing(tour, 'build.gradle.kts');
        await command('workbench.action.closeActiveEditor');
    });
    await tour.verify('Prepare generated resources', 'xtc.prepareCompilerInputs', async () => {
        // each import names itself in the workspace model, which its task writes again however up to date the build's
        // own tasks are, as the compiler model of a build whose inputs did not change is not
        const workspace = tour.file('.gradle/xtc/lsp-workspace.json');
        const importId = async () => (JSON.parse(await fs.readFile(workspace, 'utf8').catch(() => '{}')) as { importId?: string }).importId;
        const before = await importId();
        const ended = tour.taskExits.length;
        // the command returns once its import's task process has ended, which the tour has heard of by then: it
        // listens from its start, before the command does
        await command('xtc.prepareCompilerInputs');
        const ran = tour.taskExits.slice(ended).find(each => each.name === 'Prepare compiler inputs');
        await ensure(ran?.exitCode === 0, async () => `The import: ${JSON.stringify(ran)}; notifications: ${await tour.ui.text('notification')}; ${output('Ecstasy Compiler Paths').slice(-400)}`);
        const after = await importId();
        expect(after !== undefined && after !== before, () => `The workspace model's import: ${before}, then ${after}`);
    });
    await buildMode(tour, 'embedded');
    await tour.stopAll();
    await command('workbench.action.closePanel');
    await tour.restore();
    await tour.verifyRestored();
}

/**
 * Plays the debugged game's turns, a line typed in the Debug Console for each of its prompts since [from], and for the
 * one it already [waits] at, until [done] holds: one line a prompt, however fast the game reads them.
 */
async function play(tour: Tour, from: number, what: string, done: () => boolean, waits = false): Promise<void> {
    let answered = waits ? -1 : 0;
    await tour.until(what, async () => {
        if (done()) return true;
        if (tour.dapOutput(from).split('Press Enter').length - 1 > answered) {
            await debugConsole(tour, ' ');
            answered++;
        }
        return done();
    }, () => tour.dapOutput(from).slice(-300));
}

/**
 * Runs a task of tasks.json in [mode] that passes two arguments to the game, whose run(String[]) names its players
 * from them, and checks that the game plays with those players.
 */
async function playWithArguments(tour: Tour, mode: 'embedded' | 'gradle'): Promise<void> {
    const module = tour.file('src/main/x/cardGame.x');
    const label = `Play as Erin and Frank, ${mode}`;
    await open(module);
    await fs.mkdir(tour.file('.vscode'), { recursive: true });
    await fs.writeFile(tour.file('.vscode/tasks.json'), JSON.stringify({
        version: '2.0.0',
        tasks: [{ type: 'xtc', label, moduleName: 'cardGame', args: ['Erin', 'Frank'], mode }],
    }, null, 4) + '\n');
    try {
        tour.forgetExecutions();
        // VS Code reads tasks.json as it changes, and runs a task that it knows by its label without asking
        await tour.until('VS Code has the task', async () => (await vscode.tasks.fetchTasks()).some(task => task.name === label));
        await launch('workbench.action.tasks.runTask', label);
        const running = () => vscode.tasks.taskExecutions.some(execution => execution.task.name === label);
        await tour.until('The task runs or asks which', async () => running() || await tour.ui.showing('quickInput'));
        if (!running()) await tour.ui.pick(label);
        const played = async () => mode === 'embedded'
            ? tour.execution('run', 'cardGame')?.text ?? ''
            : tour.ui.terminalText();
        await tour.until('The game plays with the named players', async () => (await played()).includes('Erin hand size'),
            async () => (await played()).slice(-400));
    } finally {
        await tour.stopAll();
        await fs.rm(tour.file('.vscode/tasks.json'), { force: true });
        await tour.restore();
    }
}

async function debugging(tour: Tour): Promise<void> {
    tour.section('debugging', 'Debugging');
    tour.card('Debugging', "F5 debugs the module, embedded or with Gradle, in VS Code's own debugger, through the extension's debug adapter.", {
        eyebrow: 'DEBUGGING',
        heading: "VS Code's debugger, for Ecstasy",
        lead: 'A debug session in the language server, which builds and runs the program as Run does',
        boxes: [
            { title: 'Stop', text: 'Line, conditional, hit count, function, data and exception breakpoints, and logpoints.' },
            { title: 'Step', text: 'Over, into, out, into a target, to the cursor, restart a frame, and pause.' },
            { title: 'Inspect', text: 'Fibers and frames, variables and Set Value, watches, hovers and the Debug Console.' },
        ],
    });
    const module = tour.file('src/main/x/cardGame.x');
    const deck = tour.source('Deck.x');
    const collection = tour.source('CardCollection.x');
    const eights = tour.source('Eights.x');
    await tour.verify("Debug from a class's file", 'launch', async () => {
        // F5 without a launch configuration debugs the module of the active editor, which a class's file is part of
        const at = tour.dap.length;
        await open(eights);
        await debug();
        await tour.until('The module launches', () => tour.dapResponses('launch', at).length > 0,
            async () => `No launch; notifications: ${await tour.ui.text('notification')}`);
        expect(tour.dapResponses('launch', at).every(each => each.success), () => JSON.stringify(tour.dapResponses('launch', at)));
        await tour.stopAll();
        await command('notifications.clearAll');
    });
    const adding = await lineOf(deck, 'addCard(new Card(suit, rank));');
    await tour.shot({
        title: 'A conditional breakpoint',
        caption: "A breakpoint can have a condition: this one, in Deck's constructor, stops only when the deck adds the Queen of Spades.",
        feature: 'setBreakpoints',
        focus: 'css:.breakpoint-widget',
        settleMs: 2_000,
        act: async () => {
            await open(deck, 'addCard(new Card(suit, rank));');
            await tour.ui.focusEditor();
            await command('editor.debug.action.conditionalBreakpoint');
            await tour.until('The breakpoint widget', () => tour.ui.showing('css:.breakpoint-widget'));
            await typeIn(tour, tour.ui.page.locator('.breakpoint-widget'), 'rank == Rank.Queen && suit == Suit.Spades', 'The breakpoint widget');
        },
        check: async () => {
            await tour.ui.press('Enter');
            await tour.until('The breakpoint has its condition', () => breakpoints().includes(`Deck.x:${adding} if rank == Rank.Queen && suit == Suit.Spades`), breakpoints);
        },
    });
    const from = tour.dap.length;
    await tour.shot({
        title: 'Debug',
        caption: 'F5 debugs the module of the editor: the program stops where the condition first holds, and the Variables view shows the card.',
        feature: 'launch',
        focus: 'sidebar',
        settleMs: 3_000,
        act: async () => {
            await open(module);
            await debug();
            await stoppedAt(tour, 'Deck.x', adding);
            // the Variables view lists a scope's variables once it is open, which VS Code leaves to the user
            await expand(tour, 'Variables', 'Locals');
        },
        until: async () => (await variables(tour)).some(each => /\bsuit\b/.test(each)),
        check: async () => {
            const shown = (await variables(tour)).join(' | ');
            expect(shown.includes('Queen') && shown.includes('Spades'), () => `The variables: ${shown}`);
            const sent = tour.dapRequests();
            expect(['launch', 'configurationDone', 'setBreakpoints', 'threads', 'stackTrace', 'scopes', 'variables'].every(each => sent.has(each)), () => [...sent].join(', '));
            expect(tour.dapResponses('launch', from).every(each => each.success), () => JSON.stringify(tour.dapResponses('launch', from)));
        },
    });
    await tour.verify('Restart debugging', 'launch', async () => {
        // VS Code restarts a session whose adapter has no restart request by ending its program and launching it again
        const at = tour.dap.length;
        await command('workbench.action.debug.restart');
        await tour.until('The program is launched again', () => tour.dapResponses('launch', at).some(each => each.success),
            () => tour.dapResponses('launch', at));
        await stoppedAt(tour, 'Deck.x', adding);
    }, [NEEDS.session]);
    const dealing = await lineOf(collection, 'if (Card card := popCard())');
    await tour.shot({
        title: 'Frames and the call stack',
        caption: "Continued, it stops at a line breakpoint while dealing: the call stack shows deal called from initializeGame, in Eights' constructor.",
        feature: 'stackTrace',
        needs: [NEEDS.paused],
        focus: 'view:Call Stack',
        settleMs: 3_000,
        act: async () => {
            vscode.debug.removeBreakpoints(vscode.debug.breakpoints);
            await breakpoint(collection, dealing);
            await command('workbench.action.debug.continue');
        },
        until: () => tour.pausedAt('CardCollection.x', dealing),
        check: async () => {
            const frames = tour.stopped()!.frames.join(' | ');
            expect(frames.includes('deal') && frames.includes('initializeGame'), () => `The frames: ${frames}`);
        },
    });
    await tour.shot({
        title: 'Step into',
        caption: 'F11 steps into the call on the line: popCard, which takes the top card off the deck.',
        feature: 'stepIn',
        needs: [NEEDS.paused],
        focus: 'view:Call Stack',
        settleMs: 2_000,
        act: async () => {
            vscode.debug.removeBreakpoints(vscode.debug.breakpoints);
            await command('workbench.action.debug.stepInto');
        },
        until: () => tour.pausedAt() && tour.stopped()!.frames[0]?.includes('popCard'),
        check: async () => {
            expect(tour.stopped()?.frames[0]?.includes('popCard'), () => `The frames: ${tour.stopped()?.frames.join(' | ')}`);
        },
    });
    let position: { file?: string; line?: number } = {};
    await tour.shot({
        title: 'Step over',
        caption: 'F10 runs the current line and stops at the next one; the Variables view shows what the line changed.',
        feature: 'next',
        needs: [NEEDS.paused],
        focus: 'sidebar',
        settleMs: 2_000,
        act: async () => {
            position = tour.stopped() ?? {};
            await command('workbench.action.debug.stepOver');
        },
        until: () => tour.pausedAt() && tour.stopped()!.line !== position.line,
        check: async () => {
            expect(tour.stopped()?.frames[0]?.includes('popCard') && tour.stopped()!.line! > position.line!, () => JSON.stringify(tour.stopped()));
        },
    });
    await tour.shot({
        title: 'Step out',
        caption: 'Shift+F11 runs to the end of popCard and stops back in deal, with the card it returned.',
        feature: 'stepOut',
        needs: [NEEDS.paused],
        focus: 'view:Call Stack',
        settleMs: 2_000,
        act: async () => { await command('workbench.action.debug.stepOut'); },
        until: () => tour.pausedAt('CardCollection.x') && !tour.stopped()!.frames[0]?.includes('popCard'),
        check: async () => {
            expect(tour.stopped()?.frames[0]?.includes('deal'), () => `The frames: ${tour.stopped()?.frames.join(' | ')}`);
        },
    });
    await tour.shot({
        title: 'Set a value',
        caption: "Set Value changes a variable of the paused frame: numCards is now 6, so this player is dealt one card more.",
        feature: 'setVariable',
        needs: [NEEDS.paused],
        focus: 'view:Variables',
        settleMs: 2_000,
        act: async () => {
            await command('workbench.debug.action.focusVariablesView');
            await tour.ui.click(row(tour, 'Variables', 'numCards'), 'right');
            await tour.ui.menu('Set Value');
            // the value's box, in its row: until it has the keyboard, keys would go to the editor
            const box = tour.ui.locator('view:Variables')!.locator('.monaco-inputbox');
            await tour.until('The value box takes the keyboard', () => tour.ui.focusedIn(box));
            await tour.ui.press('Control+a');
            await tour.ui.type('6');
            await tour.ui.press('Enter');
        },
        until: async () => (await variables(tour)).some(each => /numCards\s*[:=]?\s*6/.test(each)),
        check: async () => {
            const answers = tour.dapResponses('setVariable');
            expect(answers.some(each => each.success && String(each.body?.value) === '6'), () => JSON.stringify(answers));
        },
    });
    await tour.verify('Hexadecimal values', 'xtc/valueFormat', async () => {
        await tour.ui.click(row(tour, 'Variables', 'numCards'), 'right');
        await tour.ui.menu('Show Values as Hexadecimal');
        await tour.until('The values show in hexadecimal', async () => (await variables(tour)).some(each => /0x[0-9a-fA-F]+/.test(each)), () => variables(tour));
        await command('xtc.debug.toggleHexValues');
        await tour.until('The values show in decimal again', async () => !(await variables(tour)).some(each => /0x[0-9a-fA-F]+/.test(each)), () => variables(tour));
    }, [NEEDS.paused]);
    await tour.verify('Inline values', 'textDocument/inlineValue', async () => {
        await open(collection);
        await tour.until('The editor shows the values inline', async () => (await tour.ui.inlineTexts()).some(each => /numCards\s*[=:]\s*\d/.test(each)),
            () => tour.ui.inlineTexts());
    }, [NEEDS.paused]);
    const dealAll = await lineOf(collection, 'deal(that, size());');
    await tour.shot({
        title: 'Run to the cursor',
        caption: "Run to Cursor runs to the line with the cursor, here dealAll's, as a breakpoint there would, without one.",
        feature: 'continue',
        needs: [NEEDS.paused],
        focus: `line:${dealAll}`,
        settleMs: 2_000,
        act: async () => {
            await open(collection, 'deal(that, size());');
            await command('editor.debug.action.runToCursor');
        },
        until: () => tour.pausedAt('CardCollection.x', dealAll),
        check: async () => {
            // stopped at the cursor's line, which has no breakpoint
            expect(tour.pausedAt('CardCollection.x', dealAll), () => JSON.stringify(tour.stopped()));
            const there = vscode.debug.breakpoints.filter(each => each instanceof vscode.SourceBreakpoint
                && path.basename(each.location.uri.fsPath) === 'CardCollection.x' && each.location.range.start.line + 1 === dealAll);
            expect(there.length === 0, () => `dealAll's line has ${there.length} breakpoints`);
        },
    });
    await tour.shot({
        title: 'Step into a target',
        caption: 'Step Into Target, on a line with several calls, asks which one to step into: deal, or size.',
        feature: 'stepInTargets',
        needs: [NEEDS.paused],
        focus: 'quickInput',
        settleMs: 2_000,
        act: async () => {
            await open(collection, 'deal(that, size());');
            await launch('workbench.action.debug.stepIntoTarget');
        },
        until: async () => (await quickRows(tour)).some(each => each.includes('size')) || (await tour.ui.text('menu')).includes('size'),
        check: async () => {
            const shown = (await quickRows(tour)).join(' | ') + (await tour.ui.text('menu'));
            expect(shown.includes('size') && shown.includes('deal'), () => `The targets: ${shown}`);
            if (await tour.ui.showing('quickInput')) await tour.ui.pick('size', '');
            else await tour.ui.menu('size');
            await tour.until('In size()', () => tour.stopped()?.frames[0]?.includes('size') ?? false, () => tour.stopped());
            await command('workbench.action.debug.stepOut');
            await stoppedAt(tour, 'CardCollection.x');
        },
    });
    await tour.shot({
        title: 'Watches',
        caption: "A watch, here deal's numCards, is evaluated again at every stop; watches, as hovers, read the program's values and run none of its code.",
        feature: 'evaluate',
        needs: [NEEDS.paused],
        focus: 'view:Watch',
        settleMs: 2_500,
        act: async () => {
            await open(collection);
            await select('numCards');
            await command('editor.debug.action.selectionToWatch');
        },
        // the watch's row shows its value once the debug adapter has evaluated it, after the row itself
        until: async () => (await tour.ui.viewRows('Watch')).some(each => /numCards\s*[:=]?\s*\S/.test(each)),
        check: async () => {
            const rows = await tour.ui.viewRows('Watch');
            expect(rows.some(each => /numCards\s*[:=]?\s*\d+/.test(each)), () => rows.join(' | '));
        },
    });
    await tour.verify('A watch that would run code', 'evaluate', async () => {
        // a call runs the program's code, which a watch does not: it says so instead, while the Debug Console runs it
        await open(collection);
        await select('size()', 1);
        await command('editor.debug.action.selectionToWatch');
        const shown = async () => (await tour.ui.viewRows('Watch')).find(each => each.includes('size()')) ?? '';
        await tour.until('The watch has its answer', async () => /size\(\)\s*[:=]?\s*\S/.test(await shown()), shown);
        const answer = await shown();
        expect(/is not evaluated: only variables/.test(answer), () => answer);
        await command('workbench.debug.viewlet.action.removeAllWatchExpressions');
    }, [NEEDS.paused]);
    await tour.verify('Hover a value', 'evaluate', async () => {
        await open(collection, 'deal(that, size());', 'deal(th'.length);
        await tour.ui.focusEditor();
        await command('editor.debug.action.showDebugHover');
        await tour.until('The debug hover', async () => (await tour.ui.text('css:.debug-hover-widget')).length > 0, () => tour.ui.text('css:.debug-hover-widget'));
        await tour.ui.press('Escape');
    }, [NEEDS.paused]);
    await tour.shot({
        title: 'The Debug Console',
        caption: 'Ctrl+Shift+Y opens the Debug Console: an expression typed there runs in the paused frame, with completion of its names.',
        feature: 'evaluate',
        needs: [NEEDS.paused],
        focus: 'panel',
        settleMs: 2_500,
        act: async () => {
            await command('workbench.debug.action.toggleRepl');
            await debugConsole(tour, 'size() + 1');
        },
        until: async () => /\b\d+\b/.test((await consoleText(tour)).split('size() + 1').pop() ?? ''),
        check: async () => {
            const answers = tour.dapResponses('evaluate').filter(each => each.success);
            await ensure(answers.length > 0, () => consoleText(tour));
        },
    });
    await tour.verify('Completion in the Debug Console', 'completions', async () => {
        await command('workbench.debug.action.focusRepl');
        await tour.ui.type('siz');
        await command('editor.action.triggerSuggest');
        await tour.until('The console completes the name', () => tour.dapResponses('completions').some(each => JSON.stringify(each.body).includes('size')),
            () => tour.dapResponses('completions').map(each => JSON.stringify(each.body)).join(' '));
        await tour.ui.press('Escape');
        await tour.ui.press('Control+a', 'Backspace');
    }, [NEEDS.paused]);
    await tour.verify('Stepping one fiber', 'singleThread', async () => {
        const steps = tour.dap.filter(({ from, message }) => from === 'vscode' && message.type === 'request' && ['next', 'stepIn', 'stepOut', 'continue'].includes(message.command!));
        expect(steps.length > 0, () => 'No steps');
        tour.notes.singleThread = steps.some(({ message }) => message.arguments?.singleThread === true);
    }, [NEEDS.paused]);
    await tour.shot({
        title: 'Restart a frame',
        caption: 'Restart Frame, on a frame chosen in the call stack, runs it again from its first line: dealAll starts over.',
        feature: 'restartFrame',
        needs: [NEEDS.paused],
        focus: 'view:Call Stack',
        settleMs: 2_000,
        act: async () => {
            // the debug adapter says which frames can restart, as VS Code offers Restart Frame only on those; it names a
            // frame anew in each stackTrace it answers, so the frame the call stack chose is found by its ID in any of them
            const frames = () => tour.dapResponses('stackTrace').flatMap(each => each.body?.stackFrames ?? []);
            const dealAll = (frame: { name: string }) => /(^|\.)dealAll$/.test(frame.name);
            const frame = frames().filter(dealAll).pop();
            expect(frame?.canRestart === true, () => `dealAll's frame cannot restart: ${JSON.stringify(frames().map(each => [each.name, each.canRestart]))}`);
            await command('workbench.debug.action.focusCallStackView');
            await tour.ui.click(row(tour, 'Call Stack', 'dealAll'));
            await tour.until('The call stack has chosen dealAll', () => {
                const chosen = (vscode.debug.activeStackItem as vscode.DebugStackFrame | undefined)?.frameId;
                return frames().some(each => each.id === chosen && dealAll(each));
            }, () => JSON.stringify(vscode.debug.activeStackItem));
            await command('workbench.action.debug.restartFrame');
        },
        until: () => tour.dapRequests().has('restartFrame') && tour.pausedAt('CardCollection.x'),
        check: async () => {
            expect(tour.dapResponses('restartFrame').some(each => each.success), () => JSON.stringify(tour.dapResponses('restartFrame')));
        },
    });
    const plays = await lineOf(eights, 'console.print($"{player.name} plays {card}");');
    await tour.shot({
        title: 'A logpoint',
        caption: 'A logpoint prints a message instead of stopping: who plays, and the cards left in the hand, in the Debug Console.',
        feature: 'setBreakpoints',
        needs: [NEEDS.paused],
        focus: 'css:.breakpoint-widget',
        settleMs: 2_000,
        act: async () => {
            await open(eights, 'console.print($"{player.name} plays {card}");');
            await tour.ui.focusEditor();
            await command('editor.debug.action.addLogPoint');
            await tour.until('The logpoint widget', () => tour.ui.showing('css:.breakpoint-widget'));
            // the debugger evaluates a message's paths, variables and their fields, and runs none of the program's code, as
            // a call would: an array's value is its element type and size
            await typeIn(tour, tour.ui.page.locator('.breakpoint-widget'), '{player.name} holds {player.hand.cards}', 'The logpoint widget');
        },
        check: async () => {
            await tour.ui.press('Enter');
            await tour.until('The logpoint is set', () => breakpoints().some(each => each.startsWith(`Eights.x:${plays} log`)), breakpoints);
            // the debug adapter installs a logpoint whose message it can evaluate, and says why not of another
            const installed = () => tour.dapResponses('setBreakpoints').flatMap(each => (each.body?.breakpoints ?? []) as { line?: number; verified?: boolean }[])
                .filter(each => each.line === plays).pop();
            await tour.until('The debug adapter installs the logpoint', () => installed()?.verified === true, () => JSON.stringify(installed()));
        },
    });
    const logged = tour.dap.length;
    await tour.shot({
        title: 'Pause',
        caption: "Continued, the game waits for Enter; a line typed in the Debug Console is its input. Paused while it waits, it stops as it next runs, once it has the line.",
        feature: 'pause',
        needs: [NEEDS.paused],
        focus: 'view:Call Stack',
        settleMs: 3_000,
        act: async () => {
            await command('workbench.action.debug.continue');
            await tour.until('The game waits for Enter', () => tour.dapOutput(logged).includes('Press Enter'), () => tour.dapOutput(logged).slice(-300));
            await play(tour, logged, 'The logpoint prints its message', () => tour.dapOutput(logged).includes(' holds '));
            const paused = tour.dap.length;
            await command('workbench.action.debug.pause');
            // a program whose fibers all wait, as for input, stops when one of them next runs, as the adapter says:
            // the line that the game waits for runs it
            await tour.until('The adapter answers the pause', () => tour.dap.slice(paused).some(({ from, message }) => from === 'adapter'
                && message.type === 'response' && message.command === 'pause'));
            await debugConsole(tour, ' ');
        },
        until: () => tour.stopped()?.reason === 'pause',
        check: async () => {
            // the debugger shows a String as Ecstasy writes it, in quotes: "Alice" holds cardGame:Card[4]
            expect(/\S+ holds \S*Card\[\d+\]/.test(tour.dapOutput(logged)), () => `No log message: ${tour.dapOutput(logged).slice(-400)}`);
            // it stops at the next line the game runs, once it has its line, in the game's own code
            const stop = tour.stopped()!;
            expect(stop.frames.length > 0 && (stop.file ?? '').startsWith(tour.project), () => `Stopped in ${stop.file}: ${stop.frames.join(' | ')}`);
        },
    });
    await tour.verify('Disable a breakpoint', 'setBreakpoints', async () => {
        // the Breakpoints view's check box disables the logpoint: VS Code sends Eights.x's breakpoints without it
        await command('workbench.debug.action.focusBreakpointsView');
        const logpoint = row(tour, 'Breakpoints', 'Eights.x');
        // the row's check box, an input or VS Code's own toggle, as All Exceptions' is
        const box = () => logpoint.locator('input[type="checkbox"], .monaco-custom-toggle, .monaco-checkbox').first();
        const at = tour.dap.length;
        await tour.ui.click(box());
        const sent = () => tour.dap.slice(at).filter(({ from, message }) => from === 'vscode' && message.command === 'setBreakpoints'
            && JSON.stringify(message.arguments).includes('Eights.x')).map(({ message }) => message.arguments as { breakpoints?: unknown[] });
        await tour.until('VS Code sends Eights.x without the logpoint', () => sent().some(each => (each.breakpoints ?? []).length === 0), sent);
        await tour.ui.click(box());
        await tour.until('VS Code sends the logpoint again', () => sent().some(each => (each.breakpoints ?? []).length === 1), sent);
    }, [NEEDS.session]);
    await tour.verify('Loaded sources', 'loadedSources', async () => {
        await command('workbench.debug.loadedScriptsView.focus');
        await tour.until('VS Code asks for the loaded sources', () => tour.dapResponses('loadedSources').some(each => each.success),
            () => [...tour.dapRequests()].join(', '));
    }, [NEEDS.session]);
    await tour.verify('Modules', 'modules', async () => {
        const session = vscode.debug.activeDebugSession!;
        const answer = await session.customRequest('modules', {}) as { modules: { name: string }[] };
        expect(answer.modules.some(each => each.name.includes('cardGame')), () => JSON.stringify(answer));
    }, [NEEDS.session]);
    await tour.shot({
        title: 'A function breakpoint',
        caption: "A function breakpoint stops in a method by its name, wherever it is: recordWin, as the game ends, typed in the Breakpoints view.",
        feature: 'setFunctionBreakpoints',
        needs: [NEEDS.paused],
        focus: 'view:Breakpoints',
        settleMs: 2_000,
        act: async () => {
            vscode.debug.removeBreakpoints(vscode.debug.breakpoints);
            await command('workbench.debug.viewlet.action.addFunctionBreakpointAction');
            await typeIn(tour, tour.ui.locator('view:Breakpoints')!.locator('.monaco-inputbox'), 'recordWin', 'The function breakpoint box');
            await tour.ui.press('Enter');
        },
        until: () => breakpoints().includes('function recordWin'),
        check: async () => {
            await tour.until('The function breakpoint is verified', () => tour.dapResponses('setFunctionBreakpoints').some(each =>
                JSON.stringify(each.body).includes('"verified":true')), () => tour.dapResponses('setFunctionBreakpoints').map(each => JSON.stringify(each.body)));
        },
    });
    await tour.shot({
        title: 'Fibers',
        caption: "Played to its end, the game stops in recordWin: the Scoreboard service runs on a fiber of its own, beside the game's, in the call stack.",
        feature: 'threads',
        needs: [NEEDS.paused],
        focus: 'view:Call Stack',
        settleMs: 3_000,
        act: async () => {
            const from = tour.dap.length;
            await command('workbench.action.debug.continue');
            // paused where it waits for Enter, the game waits for a line still
            await play(tour, from, 'The game ends in recordWin', () => tour.pausedAt('Scoreboard.x'), true);
        },
        until: () => tour.pausedAt('Scoreboard.x'),
        check: async () => {
            const threads = tour.dapResponses('threads').pop()?.body?.threads ?? [];
            expect(threads.length >= 2, () => `The fibers: ${JSON.stringify(threads)}`);
        },
    });
    await tour.shot({
        title: 'Library frames',
        caption: "Stepping into the map's put goes into the XDK: its frames and its sources show like the program's own, read-only.",
        feature: 'stepInTargets',
        needs: [NEEDS.paused],
        focus: 'editor',
        settleMs: 3_000,
        act: async () => {
            await open(tour.source('Scoreboard.x'), 'wins.put(name');
            await launch('workbench.action.debug.stepIntoTarget');
            await tour.until('The targets', async () => (await quickRows(tour)).some(each => each.includes('put')) || (await tour.ui.text('menu')).includes('put'));
            if (await tour.ui.showing('quickInput')) await tour.ui.pick('put', '');
            else await tour.ui.menu('put');
        },
        until: () => tour.stopped()?.frames[0]?.includes('put') ?? false,
        check: async () => {
            const stop = tour.stopped()!;
            expect(!(stop.file ?? '').startsWith(tour.project), () => `In ${stop.file}`);
        },
    });
    await tour.verify('Library frames', 'stackTrace', async () => {
        // the stack of the stop in the map's put, which the step before stepped into: the XDK's frames among the game's
        const stack = tour.dapResponses('stackTrace').pop()!.body!.stackFrames!;
        expect(stack.some(frame => !(frame.source?.path ?? '').startsWith(tour.project)), () => stack.map(frame => `${frame.name} ${frame.source?.path}`).join(' | '));
    }, [NEEDS.paused]);
    await tour.verify('Stop debugging', 'terminate', async () => {
        await command('workbench.action.debug.stop');
        await tour.until('The session ends', () => !vscode.debug.activeDebugSession);
        const sent = tour.dapRequests();
        expect(sent.has('terminate') || sent.has('disconnect'), () => [...sent].join(', '));
    }, [NEEDS.session]);
    vscode.debug.removeBreakpoints(vscode.debug.breakpoints);
    const strategy = await lineOf(eights, 'two.strategy = new SaveEights();');
    await tour.shot({
        title: 'A data breakpoint',
        caption: "Break on Value Change, on a field in the Variables view, stops where it changes: who gives Bob his strategy?",
        feature: 'setDataBreakpoints',
        focus: `line:${strategy + 1}`,
        settleMs: 3_000,
        act: async () => {
            await breakpoint(eights, strategy);
            await open(module);
            await debug();
            await stoppedAt(tour, 'Eights.x', strategy);
            vscode.debug.removeBreakpoints(vscode.debug.breakpoints);
            await command('workbench.debug.action.focusVariablesView');
            await expand(tour, 'Variables', 'this');
            await expand(tour, 'Variables', 'two');
            await tour.ui.click(row(tour, 'Variables', 'strategy'), 'right');
            const asked = tour.dap.length;
            await tour.ui.menu('Break on Value Change');
            // VS Code sets the data breakpoint a moment after the menu: a continue before it would run past the change
            await tour.until('The data breakpoint is set', () => tour.dap.slice(asked).some(({ from, message }) => from === 'adapter'
                && message.command === 'setDataBreakpoints' && JSON.stringify(message.body).includes('"verified":true')));
            await command('workbench.action.debug.continue');
        },
        until: () => tour.stopped()?.reason === 'data breakpoint' || (tour.stopped()?.reason ?? '').includes('data'),
        check: async () => {
            expect(tour.dapResponses('dataBreakpointInfo').some(each => each.body?.dataId), () => JSON.stringify(tour.dapResponses('dataBreakpointInfo')));
            expect(tour.stopped()?.line === strategy + 1 || tour.stopped()?.line === strategy, () => JSON.stringify(tour.stopped()));
        },
        after: async () => {
            await tour.stopAll();
            vscode.debug.removeBreakpoints(vscode.debug.breakpoints);
        },
    });
    await tour.shot({
        title: 'An exception',
        caption: "A bug: popCard reads past the end of its cards. The debugger stops where the uncaught exception is thrown, with its message and stack.",
        feature: 'exceptionInfo',
        focus: 'css:.exception-widget',
        settleMs: 3_000,
        act: async () => {
            await open(collection);
            await replace('Card card = cards[cards.size - 1];', 'Card card = cards[cards.size];');
            await vscode.workspace.saveAll(false);
            await open(module);
            await debug();
        },
        until: () => tour.stopped()?.reason === 'exception',
        check: async () => {
            const info = tour.dapResponses('exceptionInfo').pop();
            expect(info?.success && JSON.stringify(info.body).includes('OutOfBounds'), () => JSON.stringify(info));
        },
    });
    await tour.verify('All exceptions', 'setExceptionBreakpoints', async () => {
        await command('workbench.debug.action.focusBreakpointsView');
        const all = row(tour, 'Breakpoints', 'All Exceptions');
        await tour.ui.click(all.locator('input[type="checkbox"], .monaco-custom-toggle, .codicon').first());
        await tour.until('VS Code chooses All Exceptions', () => tour.dap.some(({ from, message }) => from === 'vscode' && message.command === 'setExceptionBreakpoints'
            && JSON.stringify(message.arguments).includes('all')), () => tour.dap.filter(({ message }) => message.command === 'setExceptionBreakpoints').map(({ message }) => JSON.stringify(message.arguments ?? message.body)));
        await tour.ui.click(all.locator('input[type="checkbox"], .monaco-custom-toggle, .codicon').first());
        await tour.stopAll();
        await tour.restore();
    });
    await tour.shot({
        title: 'Launch configurations',
        caption: "Add Configuration in launch.json offers the extension's snippets: a module to launch, or a program to attach to.",
        feature: 'debuggers.configurationSnippets',
        focus: 'suggest',
        settleMs: 2_000,
        act: async () => {
            await fs.mkdir(tour.file('.vscode'), { recursive: true });
            await fs.writeFile(tour.file('.vscode/launch.json'), JSON.stringify({ version: '0.2.0', configurations: [] }, null, 4) + '\n');
            await open(tour.file('.vscode/launch.json'), '[]', 1);
            await command('debug.addConfiguration');
        },
        until: async () => (await suggestions(tour)).some(each => each.includes('Ecstasy: Launch Module')),
        check: async () => {
            const items = await suggestions(tour);
            expect(items.some(each => each.includes('Ecstasy: Attach to Program')), () => items.join(' | '));
            await acceptSuggestion(tour, 'Ecstasy: Launch Module');
            await tour.until('The snippet is in launch.json', async () => (await text(tour.file('.vscode/launch.json'))).includes('"request": "launch"'), () => text(tour.file('.vscode/launch.json')));
        },
        after: async () => {
            await tour.ui.press('Escape');
            await command('workbench.action.revertAndCloseActiveEditor');
        },
    });
    await fs.writeFile(tour.file('.vscode/launch.json'), JSON.stringify({
        version: '0.2.0',
        configurations: [
            { type: 'xtc', request: 'launch', name: 'Debug from the first line', module: 'cardGame', stopOnEntry: true },
            { type: 'xtc', request: 'launch', name: 'Debug the scoreboard', module: 'cardGame', evaluationRunsProgram: true },
            { type: 'xtc', request: 'launch', name: 'Play as Carol and Dave', module: 'cardGame', method: 'run', args: 'Carol,Dave', cwd: '${workspaceFolder}' },
            { type: 'xtc', request: 'attach', name: 'Attach to cardGame', port: 5005 },
        ],
    }, null, 4) + '\n');
    const first = await lineOf(module, 'console.print("Starting Eights game.");');
    await tour.shot({
        title: 'Stop on entry',
        caption: 'A launch configuration with stopOnEntry stops the program at its first line, before it does anything.',
        feature: 'launch',
        focus: `line:${first}`,
        settleMs: 3_000,
        act: async () => {
            await open(module);
            await debugConfiguration(tour, 'Debug from the first line');
        },
        until: () => tour.pausedAt('cardGame.x'),
        check: async () => {
            const stop = tour.stopped()!;
            expect(stop.reason === 'entry' && stop.line !== undefined && stop.line <= first, () => JSON.stringify(stop));
        },
        after: async () => { await tour.stopAll(); },
    });
    await tour.verify('Launch with arguments', 'launch.args', async () => {
        // the game's run(String[]) names the players from its arguments, which the configuration gives with its method and folder
        await open(module);
        const at = tour.dap.length;
        try {
            await debugConfiguration(tour, 'Play as Carol and Dave');
            await tour.until('The game plays with the named players', () => tour.dapOutput(at).includes('Carol hand size') && tour.dapOutput(at).includes('Dave hand size'),
                () => tour.dapOutput(at).slice(-400));
        } finally {
            await tour.stopAll();
            await tour.restore();
        }
    });
    await tour.verify('Evaluation that runs the program', 'launch.evaluationRunsProgram', async () => {
        const calls = await lineOf(eights, 'scoreboard.recordWin(winner.name);');
        await breakpoint(eights, await lineOf(eights, 'Player current = one;'));
        await debugConfiguration(tour, 'Debug the scoreboard');
        await stoppedAt(tour, 'Eights.x');
        const at = tour.dap.length;
        await debugConsole(tour, 'scoreboard.winsOf("Alice")');
        await tour.until('The service answers', () => tour.dapResponses('evaluate', at).some(each => each.success && each.body?.result === '0'),
            () => tour.dapResponses('evaluate', at).map(each => JSON.stringify(each.body ?? each.message)));
        expect(calls > 0, () => 'No recordWin');
        await tour.stopAll();
        vscode.debug.removeBreakpoints(vscode.debug.breakpoints);
    });
    await tour.verify('A hit count', 'setBreakpoints', async () => {
        const location = new vscode.Location(vscode.Uri.file(collection), new vscode.Position(dealing - 1, 0));
        vscode.debug.addBreakpoints([new vscode.SourceBreakpoint(location, true, undefined, '3')]);
        await open(module);
        await debugConfiguration(tour, 'Debug the scoreboard');
        await stoppedAt(tour, 'CardCollection.x', dealing);
        await debugConsole(tour, 'i');
        await tour.until('The third time through the loop', () => tour.dapResponses('evaluate').some(each => each.body?.result === '2'),
            () => tour.dapResponses('evaluate').slice(-2).map(each => JSON.stringify(each.body)));
        await tour.stopAll();
        vscode.debug.removeBreakpoints(vscode.debug.breakpoints);
    });
    await tour.shot({
        title: 'Attach to a program',
        caption: "Ecstasy: Copy Option to Debug a Program copies xtc run's --debug option; the program waits for the Attach configuration, and stops at its breakpoints.",
        feature: 'attach',
        focus: 'sidebar',
        settleMs: 3_000,
        act: async () => {
            await command('xtc.debug.copyOption');
            const option = await vscode.env.clipboard.readText();
            expect(option === '--debug listen:127.0.0.1:5005,suspend', () => `Copied: ${option}`);
            await breakpoint(module, first);
            const shell = vscode.window.createTerminal({ name: 'attach', cwd: tour.project });
            shell.show();
            shell.sendText(`xtc run -o build/attach ${option} src/main/x/cardGame.x`);
            await tour.until('The program listens', async () => (await tour.ui.terminalText()).includes('Listening for the debugger'), () => tour.ui.terminalText());
            await debugConfiguration(tour, 'Attach to cardGame');
        },
        until: () => tour.pausedAt('cardGame.x', first),
        check: async () => {
            expect(tour.dapResponses('attach').some(each => each.success), () => JSON.stringify(tour.dapResponses('attach')));
        },
        after: async () => {
            await tour.stopAll();
            terminal('attach')?.dispose();
            vscode.debug.removeBreakpoints(vscode.debug.breakpoints);
        },
    });
    await tour.shot({
        title: 'Debug with Gradle',
        caption: 'In Gradle mode, F5 runs runXtc with the debugger, whose program connects to the session, and stops at the same breakpoints.',
        feature: 'xtc/debugTask',
        focus: 'sidebar',
        settleMs: 3_000,
        act: async () => {
            await fs.rm(tour.file('.vscode/launch.json'), { force: true });
            await buildMode(tour, 'gradle');
            await breakpoint(collection, dealing);
            await open(module);
            await debug();
        },
        until: () => tour.pausedAt('CardCollection.x', dealing),
        check: async () => {
            // stopped at the breakpoint, in a program that Gradle's runXtc runs with the debugger's option
            expect(tour.pausedAt('CardCollection.x', dealing), () => JSON.stringify(tour.stopped()));
            const tasks = vscode.tasks.taskExecutions.map(each => ({
                name: each.task.name,
                args: ((each.task.execution as vscode.ShellExecution | undefined)?.args ?? []).map(arg => typeof arg === 'string' ? arg : arg.value),
            }));
            expect(tasks.some(each => each.args.includes('runXtc') && each.args.some(arg => arg.includes('xtcDebug'))),
                () => `The tasks: ${JSON.stringify(tasks)}`);
        },
        after: async () => {
            await tour.stopAll();
            await buildMode(tour, 'embedded');
            vscode.debug.removeBreakpoints(vscode.debug.breakpoints);
        },
    });
    await fs.rm(tour.file('.vscode/launch.json'), { force: true });
    await command('workbench.action.closePanel');
    await command('workbench.view.explorer');
    await tour.restore();
    await tour.verifyRestored();
}

async function testing(tour: Tour): Promise<void> {
    tour.section('tests', 'Tests');
    tour.card('Tests', "xunit tests run and debug in VS Code's Testing view and from the editor's gutter, embedded or with Gradle.", {
        eyebrow: 'TESTS',
        heading: 'xunit, in the Testing view',
        lead: "The same tree and results, embedded or with Gradle's testXtc",
        boxes: [
            { title: 'Run', text: "A test, a module's tests, or all of them, from the Testing view or the gutter." },
            { title: 'Read', text: 'Passed, failed and skipped tests, with their output, messages and the failing line.' },
            { title: 'Debug', text: 'Any test under the debugger, with its breakpoints.' },
        ],
    });
    const deckTest = tour.test('DeckTest.x');
    await tour.shot({
        title: 'The Testing view',
        caption: "The Testing view lists the test modules and their tests, from the server's analysis; it runs, debugs and filters them.",
        feature: 'xtc/tests',
        focus: 'sidebar',
        settleMs: 3_000,
        act: async () => {
            await open(deckTest);
            await command('workbench.view.testing.focus');
        },
        until: async () => (await tour.ui.text('sidebar')).includes('DeckTest'),
        check: async () => {
            await ensure(tour.testItems.some(id => id.endsWith('testDeckCanBeShuffled')) || (await tour.ui.text('sidebar')).includes('PlayerTest'),
                async () => `The tests: ${(await tour.ui.text('sidebar')).slice(0, 400)}`);
        },
    });
    const module = await lineOf(deckTest, 'module DeckTest');
    await tour.shot({
        title: 'Test icons',
        caption: 'The gutter has a run icon on every test module and every @Test method; a click runs it, a right click debugs it.',
        feature: 'testController.items',
        needs: [NEEDS.tests],
        focus: `gutter:${module}`,
        settleMs: 2_000,
        act: async () => { await open(deckTest, 'module DeckTest'); },
        until: async () => (await tour.ui.page.locator('.editor-group-container.active .glyph-margin-widgets [class*="testing-run"]').count()) > 0,
        check: async () => {
            // every @Test method is a test item at its declaration, and the gutter, which draws only the lines the editor
            // shows, has an icon on the first line of each declaration it shows, beside the module's
            const ids = [...(await text(deckTest)).matchAll(/@Test\b[^{;]*?\bvoid\s+(\w+)\s*\(/g)].map(match => `DeckTest/${match[1]}`);
            const located = ids.map(id => ({ id, location: testLocation(id) }));
            expect(ids.length >= DECK_TESTS && located.every(({ location }) => location?.uri.fsPath === deckTest), () => JSON.stringify(located));
            const editor = active();
            const shown = located.filter(({ location }) => editor.visibleRanges.some(range => range.contains(location!.range.start))).length;
            const icons = await tour.ui.page.locator('.editor-group-container.active .glyph-margin-widgets [class*="testing"]').count();
            expect(shown > 0 && icons >= shown, () => `${icons} test icons for the ${shown} tests the editor shows`);
        },
    });
    await tour.shot({
        title: 'Run a test',
        caption: "Run Test at Cursor runs the test the cursor is in, embedded: the gutter and the Testing view show it passed.",
        feature: 'testController.runProfile',
        needs: [NEEDS.tests],
        focus: 'sidebar',
        settleMs: 2_000,
        act: async () => {
            tour.watchTests();
            await open(deckTest, 'void testDeckCanBeShuffled', 'void test'.length);
            await command('testing.runAtCursor');
        },
        until: () => tour.tests.size > 0,
        check: async () => {
            expect(tour.testCounts().passed === 1 && [...tour.tests.keys()].some(id => id.endsWith('testDeckCanBeShuffled')), () => JSON.stringify([...tour.tests.values()]));
        },
    });
    await tour.shot({
        title: "Run a module's tests",
        caption: "On the module's line, it runs every test of DeckTest.",
        feature: 'xtc/testEvent',
        needs: [NEEDS.tests],
        focus: 'sidebar',
        settleMs: 2_000,
        act: async () => {
            tour.watchTests();
            await open(deckTest, 'module DeckTest', 'module '.length);
            await command('testing.runAtCursor');
        },
        until: () => tour.tests.size >= DECK_TESTS,
        check: async () => {
            const counts = tour.testCounts();
            expect(counts.passed === DECK_TESTS && !counts.failed, () => JSON.stringify(counts));
        },
    });
    await tour.shot({
        title: 'Run all the tests',
        caption: "Run All Tests runs every module's tests, and the Test Results view shows what they printed.",
        feature: 'testing.runAll',
        needs: [NEEDS.tests],
        focus: 'sidebar',
        settleMs: 3_000,
        act: async () => {
            tour.watchTests();
            await command('testing.runAll');
        },
        until: () => tour.tests.size >= ALL_TESTS,
        check: async () => {
            const counts = tour.testCounts();
            expect(counts.passed === ALL_TESTS && !counts.failed, () => JSON.stringify(counts));
        },
    });
    await tour.verify('Cancel a test run', 'xtc/stopRun', async () => {
        // a run that is canceled ends, whenever it is canceled: every test it chose has a result, passed or not finished
        tour.watchTests();
        await launch('testing.runAll');
        await tour.until('The tests are running', async () => (await tour.ui.text('sidebar')).length > 0);
        await command('testing.cancelRun');
        await tour.until('Every chosen test has a result', () => tour.tests.size >= ALL_TESTS, () => tour.testCounts());
        const unfinished = [...tour.tests.values()].filter(each => each.state !== 'passed');
        expect(unfinished.every(each => each.state === 'errored' && /ended before|did not run/.test(each.message ?? '')),
            () => JSON.stringify(unfinished.slice(0, 3)));
    }, [NEEDS.tests]);
    await tour.verify("The tests' output", 'testController.output', async () => {
        await command('testing.showMostRecentOutput');
        // the latest run's output, which a canceled Run All has of the modules it reached, whichever they are
        await tour.until('The output shows', async () => /\b(CardCollection|CardGame|Card|Deck|Eights|Hand|Player)Test\b/.test(await tour.ui.text('panel')),
            () => tour.ui.text('panel'));
    }, [NEEDS.tests]);
    await tour.verify('Refresh the tests', 'testController.refreshHandler', async () => {
        tour.testItems = [];
        await command('testing.refreshTests');
        await tour.until('The tests are listed again', () => tour.testItems.length > ALL_TESTS, () => tour.testItems.length);
    });
    const failing = await lineOf(deckTest, 'assert deck.size() == GameConstants.DECK_SIZE as');
    await tour.shot({
        title: 'A failing test',
        caption: "A failing test is red, and its assertion's message shows at the line that failed.",
        feature: 'testController.runProfile',
        needs: [NEEDS.tests],
        focus: 'peek',
        settleMs: 3_000,
        act: async () => {
            await open(deckTest);
            await replace('assert deck.size() == GameConstants.DECK_SIZE as', 'assert deck.size() == GameConstants.DECK_SIZE + 1 as');
            await compiles(tour, deckTest);
            tour.watchTests();
            await at('void testDeckHas52Cards', 'void test'.length);
            await command('testing.runAtCursor');
            await tour.until('The test fails', () => tour.testCounts().failed === 1);
            await command('testing.openOutputPeek').then(undefined, () => undefined);
        },
        until: () => tour.ui.showing('peek'),
        check: async () => {
            const failed = [...tour.tests.values()].find(each => each.state === 'failed');
            expect(failed?.line === failing, () => JSON.stringify(failed));
        },
        after: async () => {
            await command('closeReferenceSearch').then(undefined, () => undefined);
            await tour.ui.press('Escape');
            await tour.restore();
        },
    });
    // the tests a step writes into DeckTest, before its first test, which the section's restore takes out again
    const first = '    @Test\n    void testDeckHas52Cards() {';
    const added = async (name: string, test: string, imports = '') => {
        await open(deckTest);
        if (imports) await replace('    import cardGame.GameConstants;\n', `    import cardGame.GameConstants;\n${imports}`);
        await replace(first, `${test}\n\n${first}`);
        await command('workbench.action.files.save');
        await tour.until(`The Testing view lists ${name}`, () => tour.testItems.some(id => id.endsWith(name)), () => tour.testItems.slice(-4));
        tour.watchTests();
        await at(`void ${name}`, 'void '.length);
        await command('testing.runAtCursor');
        await tour.until(`${name} has a result`, () => [...tour.tests.keys()].some(id => id.endsWith(name)), () => tour.testCounts());
        return [...tour.tests.values()].find(each => each.id.endsWith(name))!;
    };
    await tour.shot({
        title: 'A new test',
        caption: 'A test written while the project is open is in the Testing view as soon as the server has read it, with its gutter icon, and runs: no restart.',
        feature: 'testController.items',
        needs: [NEEDS.tests],
        focus: 'sidebar',
        settleMs: 2_000,
        act: async () => {
            await added('testANewDeckIsFull', '    @Test\n    void testANewDeckIsFull() {\n'
                + '        assert new Deck("New Deck").size() == GameConstants.DECK_SIZE;\n    }');
        },
        until: () => [...tour.tests.keys()].some(id => id.endsWith('testANewDeckIsFull')),
        check: async () => {
            const report = [...tour.tests.values()].find(each => each.id.endsWith('testANewDeckIsFull'));
            expect(report?.state === 'passed', () => JSON.stringify(report));
        },
        after: async () => { await tour.restore(); },
    });
    await tour.verify('An error, apart from a failure', 'xtc/testEvent', async () => {
        // a test that throws is an error, with its exception's message at the line it threw from, not a failed assertion;
        // not IllegalState, which a failed assert throws
        const report = await added('testAJokerIsNoCard', '    @Test\n    void testAJokerIsNoCard() {\n'
            + '        throw new Unsupported("a joker is no card");\n    }');
        expect(report.state === 'errored' && (report.message ?? '').includes('a joker is no card')
            && report.line === await lineOf(deckTest, 'throw new Unsupported'), () => JSON.stringify(report));
        await tour.restore();
    }, [NEEDS.tests]);
    await tour.verify('A skipped test', 'xtc/testEvent', async () => {
        // xunit's @Disabled skips a test, which is neither passed nor failed, with its reason
        const report = await added('testJokersLater', '    @Disabled("jokers come later")\n    @Test\n    void testJokersLater() {\n'
            + '        assert False;\n    }', '    package xunit import xunit.xtclang.org;\n    import xunit.annotations.Disabled;\n');
        expect(report.state === 'skipped' && (report.message ?? '').includes('jokers come later'), () => JSON.stringify(report));
        await tour.restore();
    }, [NEEDS.tests]);
    const shuffle = await lineOf(deckTest, 'deck.shuffle();');
    await tour.shot({
        title: 'Debug a test',
        caption: 'Debug Test at Cursor runs it under the debugger: it stops at the test\'s breakpoint, here before the shuffle.',
        feature: 'testController.debugProfile',
        needs: [NEEDS.tests],
        focus: `line:${shuffle}`,
        settleMs: 3_000,
        act: async () => {
            tour.watchTests();
            await breakpoint(deckTest, shuffle);
            await at('void testDeckCanBeShuffled', 'void test'.length);
            // Debug Test at Cursor runs the test whose range has the caret, as the Testing view last listed it: after the
            // steps before, which add tests to DeckTest.x and take them out, it lists the file again a moment later
            await tour.until('The Testing view has testDeckCanBeShuffled at the caret', () =>
                testLocation('DeckTest/testDeckCanBeShuffled')?.range.contains(active().selection.active) ?? false,
            () => JSON.stringify(testLocation('DeckTest/testDeckCanBeShuffled')?.range));
            // the command answers once its run has ended, which waits for the stopped test
            await launch('testing.debugAtCursor');
        },
        until: () => tour.pausedAt('DeckTest.x', shuffle),
        check: async () => {
            await command('workbench.action.debug.continue');
            await tour.until('The debugged test passes', () => tour.testCounts().passed === 1, () => JSON.stringify([...tour.tests.values()]));
        },
        after: async () => {
            await tour.stopAll();
            vscode.debug.removeBreakpoints(vscode.debug.breakpoints);
        },
    });
    await tour.shot({
        title: 'Tests with Gradle',
        caption: "In Gradle mode the same Testing view runs the XTC plugin's testXtc, and xunit's results come back to it.",
        feature: 'testXtc',
        needs: [NEEDS.tests],
        focus: 'sidebar',
        settleMs: 3_000,
        act: async () => {
            await buildMode(tour, 'gradle');
            tour.watchTests();
            await open(deckTest, 'module DeckTest', 'module '.length);
            await command('testing.runAtCursor');
        },
        until: () => tour.tests.size >= DECK_TESTS,
        check: async () => {
            const counts = tour.testCounts();
            expect(counts.passed === DECK_TESTS && !counts.failed, () => JSON.stringify(counts));
        },
        after: async () => {
            await tour.stopAll();
            await buildMode(tour, 'embedded');
        },
    });
    await command('workbench.action.closePanel');
    await command('workbench.view.explorer');
    await tour.restore();
    await tour.verifyRestored();
}

async function settings(tour: Tour): Promise<void> {
    tour.section('settings', 'Settings');
    await tour.shot({
        title: "The extension's settings",
        caption: "Ecstasy: Open Language Service Settings lists the extension's settings: the build mode, formatting, hints and lenses, and the server's runtime.",
        feature: 'xtc.openLanguageSettings',
        focus: 'editor',
        settleMs: 3_000,
        act: async () => { await command('xtc.openLanguageSettings'); },
        until: async () => (await tour.ui.text('css:.settings-editor')).includes('Build'),
        check: async () => {
            // the editor draws the settings the window has room for, but its count of those found, and its table of
            // contents, the extension's groups, whatever the window's size
            const groups = settingsGroups().filter(group => Object.keys(group.properties ?? {}).some(name => name.startsWith('xtc.')));
            const count = groups.reduce((sum, group) => sum + Object.keys(group.properties ?? {}).length, 0);
            const shown = await tour.ui.text('css:.settings-editor');
            expect(shown.includes(`${count} Settings Found`) && groups.every(group => shown.includes(group.title ?? '')), () => shown.slice(0, 400));
        },
        after: async () => { await command('workbench.action.closeActiveEditor'); },
    });
    await tour.verify('Trace the server', 'xtc.trace.server', async () => {
        await setting('xtc.trace.server', 'messages');
        try {
            await open(tour.source('Eights.x'), 'deck.shuffle()', 'deck.'.length);
            await command('vscode.executeHoverProvider', active().document.uri, active().selection.active);
            await command('xtc.showServerOutput');
            await tour.until('The log traces the messages', () => /textDocument\/hover/.test(output('Ecstasy Language Server')), () => output('Ecstasy Language Server').slice(-300));
        } finally {
            await setting('xtc.trace.server', undefined);
            await command('workbench.action.closePanel');
        }
    });
    await tour.verify('Explicit source modules', 'xtc.compiler.sourceModules', async () => {
        const uri = (file: string) => vscode.Uri.file(file).toString();
        await setting('xtc.compiler.sourceModules', [
            { name: 'cardGame', uri: uri(tour.file('src/main/x/cardGame.x')) },
            { name: 'DeckTest', uri: uri(tour.test('DeckTest.x')), dependencies: ['cardGame'] },
        ]);
        try {
            await command('xtc.showCompilerPaths');
            await tour.until('The paths are explicit', () => output('Ecstasy Compiler Paths').includes('explicit workspace override'), () => output('Ecstasy Compiler Paths').slice(0, 300));
            await open(tour.test('DeckTest.x'));
            await compiles(tour, tour.test('DeckTest.x'));
            // Configure Compiler Paths goes back to the modules that discovery finds
            await launch('xtc.configureCompilerPaths');
            await tour.ui.pick('Reset to build model / discovery', 'Reset');
            await tour.until('The paths are discovered again', () => vscode.workspace.getConfiguration('xtc.compiler').get('sourceModules') === null,
                () => JSON.stringify(vscode.workspace.getConfiguration('xtc.compiler').get('sourceModules')));
        } finally {
            await setting('xtc.compiler.sourceModules', undefined);
        }
    });
    await tour.verify('Explicit libraries', 'xtc.compiler.libraries', async () => {
        await setting('xtc.compiler.libraries', { modulePath: [], sourceAttachments: [] });
        try {
            await command('xtc.showCompilerPaths');
            await tour.until('The libraries are explicit', () => output('Ecstasy Compiler Paths').includes('explicit workspace/application override'), () => output('Ecstasy Compiler Paths').slice(0, 300));
        } finally {
            await setting('xtc.compiler.libraries', undefined);
        }
    });
    await tour.verify('Connection and runtime settings', 'xtc.languageService.textSynchronization', async () => {
        const java = process.env.JAVA_HOME;
        const before = getClient();
        const roots = await fs.mkdtemp(path.join(os.tmpdir(), 'xtc-roots-'));
        try {
            await setting('xtc.java.vmOptions', ['-Xmx1500m']);
            await setting('xtc.server.logs', { historyDays: 3, maxFileMb: 10, totalSizeMb: 50, retainedSessions: 5 });
            if (java) await setting('xtc.java.home', java);
            await setting('xtc.sourceRoots', [roots]);
            await setting('xtc.languageService.saveFormatting', 'server');
            await setting('xtc.languageService.textSynchronization', 'incremental');
            await tour.until('The server restarts with them', () => getClient() !== before && !!getClient()?.isRunning(), () => getClient()?.state);
            const status = await getClient()!.sendRequest<Record<string, unknown>>('xtc/languageServiceStatus');
            const sync = getClient()!.initializeResult?.capabilities.textDocumentSync as { change?: number; willSaveWaitUntil?: boolean } | undefined;
            expect(sync?.change === 2 && sync.willSaveWaitUntil === true, () => `The synchronization: ${JSON.stringify(sync)}; ${JSON.stringify(status).slice(0, 300)}`);
            const collection = tour.source('CardCollection.x');
            await open(collection);
            await replace('cards.add(card);', 'cards.ad(card);');
            await hasError(tour, collection, 'ad');
            await replace('cards.ad(card);', 'cards.add(card);');
            await compiles(tour, collection);
            await vscode.workspace.saveAll(false);
            expect(!active().document.isDirty, () => 'CardCollection.x is not saved');
            await tour.until('Saving formats the file', async () => (await fs.readFile(collection, 'utf8')).includes('\n            cards.swap(index1, index2);'), () => fs.readFile(collection, 'utf8'));
        } finally {
            for (const name of ['xtc.java.vmOptions', 'xtc.server.logs', 'xtc.java.home', 'xtc.sourceRoots', 'xtc.languageService.saveFormatting', 'xtc.languageService.textSynchronization']) {
                await setting(name, undefined);
            }
            await command('notifications.clearAll');
            await command('xtc.restartServer');
            await tour.until('The server runs with the defaults', () => !!getClient()?.isRunning(), () => getClient()?.state);
            await fs.rm(roots, { recursive: true, force: true });
            await tour.restore();
        }
    });
    await tour.verifyRestored();
}

/**
 * Checks that the tour's steps reached the server with each LSP request it advertises and each DAP request of its debug
 * adapter: from the server's traces of what it received, and the tour's tracker of the debug sessions.
 */
async function protocol(tour: Tour, coverage: Coverage): Promise<void> {
    tour.section('protocol', 'Protocol');
    const directory = process.env.XTC_LSP_LOG_DIR!;
    const traces = (await fs.readdir(directory, { recursive: true }).catch(() => [] as string[])).filter(file => /lsp-trace-.*\.jsonl$/.test(file));
    const received = new Set<string>();
    const sent = new Set<string>();
    // the code action requests' cost, a measure rather than a check: each request's end, and the compiles its query's
    // jobs made, which the trace links request to query to job to compile by their parents
    const actionEnds = new Map<number, { outcome?: string; errorCode?: number; elapsedMs?: number }>();
    const parents = new Map<number, number>();
    const compiles = new Map<number, number>();
    for (const trace of traces) {
        for (const line of (await fs.readFile(path.join(directory, trace), 'utf8')).split('\n')) {
            try {
                const entry = JSON.parse(line) as {
                    id?: number; parent?: number; kind?: string; operation?: string; direction?: string; event?: string;
                    outcome?: string; errorCode?: number; elapsedMs?: number;
                };
                if (entry.kind === 'lsp-request' && entry.direction === 'from-client') received.add(entry.operation!);
                if (entry.kind === 'lsp-request' && entry.direction === 'to-client') sent.add(entry.operation!);
                if (entry.kind === 'lsp-notification' && entry.event === 'received') received.add(entry.operation!);
                // only the links from a compile's job to its request: a code action's query, and the compiler's jobs
                const link = (entry.kind === 'lsp-query' && entry.operation === 'textDocument/codeAction') || entry.kind === 'compiler-queue';
                if (link && entry.id !== undefined && entry.parent !== undefined) parents.set(entry.id, entry.parent);
                if (entry.kind === 'lsp-request' && entry.operation === 'textDocument/codeAction' && entry.event === 'end') actionEnds.set(entry.id!, entry);
                if (entry.operation === 'EmbeddingSupport.compileModule(tree)' && entry.event === 'start' && entry.parent !== undefined) {
                    compiles.set(entry.parent, (compiles.get(entry.parent) ?? 0) + 1);
                }
            } catch {
                // a line cut off by the server's end
            }
        }
    }
    coverage.codeActionCost = codeActionCost(actionEnds, parents, compiles);
    const lsp = ['initialize', 'shutdown', 'textDocument/hover', 'textDocument/completion', 'completionItem/resolve', 'textDocument/definition',
        'textDocument/declaration', 'textDocument/typeDefinition', 'textDocument/implementation', 'textDocument/references', 'textDocument/documentSymbol',
        'textDocument/documentHighlight', 'textDocument/selectionRange', 'textDocument/foldingRange', 'textDocument/prepareRename', 'xtc/rename',
        'textDocument/codeAction', 'codeAction/resolve', 'textDocument/formatting', 'textDocument/rangeFormatting', 'textDocument/onTypeFormatting',
        'textDocument/inlayHint', 'textDocument/inlineValue', 'textDocument/documentLink', 'textDocument/signatureHelp', 'textDocument/semanticTokens/full',
        'textDocument/semanticTokens/full/delta', 'textDocument/semanticTokens/range', 'workspace/symbol', 'xtc/renameFiles',
        'textDocument/codeLens', 'codeLens/resolve', 'textDocument/linkedEditingRange', 'textDocument/moniker', 'textDocument/prepareTypeHierarchy',
        'typeHierarchy/subtypes', 'typeHierarchy/supertypes', 'textDocument/prepareCallHierarchy', 'callHierarchy/incomingCalls', 'callHierarchy/outgoingCalls',
        'textDocument/diagnostic', 'workspace/diagnostic', 'textDocument/inlineCompletion', 'textDocument/didOpen', 'textDocument/didChange',
        'textDocument/didClose', 'textDocument/didSave', 'workspace/didChangeConfiguration', 'workspace/didChangeWatchedFiles', 'workspace/didRenameFiles',
        'workspace/willCreateFiles', 'workspace/didCreateFiles', 'workspace/willDeleteFiles', 'workspace/didDeleteFiles',
        'xtc/build', 'xtc/run', 'xtc/runInput', 'xtc/stopRun', 'xtc/tests', 'xtc/debugStart', 'xtc/debugMessage', 'xtc/debugEnd', 'xtc/compilerSourceModules',
        'xtc/discoverConsumers', 'xtc/languageServiceStatus', 'xtc/exportLogs', 'xtc/healthCheck'];
    const dap = ['initialize', 'launch', 'attach', 'configurationDone', 'setBreakpoints', 'setFunctionBreakpoints', 'dataBreakpointInfo',
        'setDataBreakpoints', 'setExceptionBreakpoints', 'exceptionInfo', 'threads', 'stackTrace', 'scopes', 'variables', 'continue', 'next', 'stepIn',
        'stepOut', 'stepInTargets', 'restartFrame', 'pause', 'evaluate', 'setVariable', 'completions', 'loadedSources', 'modules', 'xtc/valueFormat',
        'disconnect'];
    const requests = tour.dapRequests();
    Object.assign(coverage, {
        lspReceived: [...received].sort(),
        lspSent: [...sent].sort(),
        lspMissing: lsp.filter(method => !received.has(method)),
        dapRequests: [...requests].sort(),
        dapEvents: [...new Set(tour.dap.filter(({ from, message }) => from === 'adapter' && message.type === 'event').map(({ message }) => message.event))].sort(),
        dapMissing: dap.filter(command => !requests.has(command)),
        notes: tour.notes,
    });
    // a run of some sections sends some of the requests only
    if (!tour.everything) return;
    await tour.verify('Every LSP request reached the server', 'protocol', async () => {
        const missing = coverage.lspMissing as string[];
        expect(missing.length === 0, () => `Never received: ${missing.join(', ')}`);
    });
    await tour.verify('Every DAP request reached the debug adapter', 'protocol', async () => {
        const missing = coverage.dapMissing as string[];
        expect(missing.length === 0, () => `Never sent: ${missing.join(', ')}`);
    });
}

/**
 * The code action requests of the run, by how they ended, and for the answered ones the server's time and the compiles
 * made for them: a measure that runs can be compared by, as a light bulb's requests cancel one another.
 */
function codeActionCost(ends: Map<number, { outcome?: string; errorCode?: number; elapsedMs?: number }>, parents: Map<number, number>,
    compiles: Map<number, number>): Record<string, unknown> {
    // a compile's job, the job's query, the query's request
    const owner = (id: number): number | undefined => parents.get(parents.get(id) ?? -1);
    const perRequest = new Map<number, number>();
    for (const [job, count] of compiles) {
        const request = owner(job);
        if (request !== undefined && ends.has(request)) perRequest.set(request, (perRequest.get(request) ?? 0) + count);
    }
    const answered = [...ends].filter(([, end]) => end.outcome === 'replied');
    const quantiles = (values: number[]) => {
        const sorted = [...values].sort((a, b) => a - b);
        const at = (fraction: number) => sorted.length ? Math.round(sorted[Math.min(sorted.length - 1, Math.floor(fraction * sorted.length))]) : 0;
        return { median: at(0.5), p90: at(0.9), max: at(1) };
    };
    return {
        requests: ends.size,
        answered: answered.length,
        cancelled: [...ends.values()].filter(end => end.errorCode === -32800).length,
        contentModified: [...ends.values()].filter(end => end.errorCode === -32801).length,
        answeredMs: quantiles(answered.map(([, end]) => end.elapsedMs ?? 0)),
        answeredCompiles: { total: answered.reduce((sum, [id]) => sum + (perRequest.get(id) ?? 0), 0), ...quantiles(answered.map(([id]) => perRequest.get(id) ?? 0)) },
    };
}

function ending(tour: Tour): void {
    tour.section('end', 'The end');
    tour.card("That's the tour", 'The XTC extension for VS Code: install it from the Marketplace, and open an Ecstasy project, or make one with Ecstasy: Create New Project.', {
        eyebrow: 'XTC LANGUAGE SUPPORT · VISUAL STUDIO CODE',
        heading: 'Ecstasy in VS Code',
        lead: 'Edit, navigate, refactor, build, run, debug and test: one language server, in every editor',
        code: [
            'Ecstasy: Create New Project          a new project, with the XTC Gradle plugin',
            'Ecstasy: Select Build Mode           embedded or Gradle',
            'Ecstasy: Open Language Service Settings',
            '                                     formatting, hints, lenses and the server',
        ].join('\n'),
    });
}
