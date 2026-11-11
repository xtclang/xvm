/*
 * What the tour covers, from the lists of what the plugin and its servers offer: the capabilities that
 * XtcLanguageServer.initialize advertises, the xtc/ requests and notifications, the capabilities and requests of
 * XtcDebugServer, and the actions, extensions and settings pages of the plugin's plugin.xml, with the IntelliJ and
 * LSP4IJ actions that a user uses them by. Each feature has the steps (frames of the recording, each with its check)
 * or the checks (verify, between the steps, without a frame) that exercise it, by their titles; or why the tour
 * cannot, and what covers it instead: a case of the compiler playbook (X...), the plugin's or the servers' own tests,
 * or LSP4IJ 0.30.0, whose client the IDE needs first (UP.. in doc/remaining.md).
 *
 * Language server: initialize's capabilities
 *   textDocumentSync: didOpen, didChange, didClose  step Errors as you type; check a test module sees an edit of the module it tests
 *   textDocumentSync: didSave                       check Actions on Save reformat a file as it is saved
 *   diagnosticProvider: textDocument/diagnostic     steps Errors as you type, The error's message, Warnings, The Problems window
 *   diagnosticProvider: workspace/diagnostic        check after the signature change, SaveEights.x, which is closed, and
 *                                                   Player.x are marked red as files with errors: fix in progress, as the
 *                                                   server pushes no closed file's diagnostics to a client that pulls; check
 *                                                   after the failed build, Project Errors list its error, which only a build
 *                                                   fills; the workspace pull itself: the lsp-server's XdkPullDiagnosticsTest
 *   hoverProvider                                   steps Quick documentation, The XDK's documentation
 *   completionProvider                              steps Member completion, Name completion; check a chosen completion makes code
 *                                                   that compiles
 *   completion: additionalTextEdits                 check completing a type of another module adds its import
 *   completionItem/resolve                          check a completion item's documentation shows beside the list
 *   inlineCompletionProvider                        check inline completion suggests a name in scope as it is typed
 *   definitionProvider                              steps Go to declaration, The XDK's sources
 *   declarationProvider                             check LSP's Go to declaration goes to the method's declaration too
 *   typeDefinitionProvider                          step Go to type declaration
 *   implementationProvider                          step Go to implementation
 *   referencesProvider                              step Find usages
 *   documentSymbolProvider                          steps File structure, The Structure window
 *   documentHighlightProvider                       step Highlight usages
 *   selectionRangeProvider                          step Extend selection
 *   foldingRangeProvider                            step Folding
 *   renameProvider, prepareRename                   IntelliJ renames through xtc/rename (below); the standard requests are VS Code's,
 *                                                   in its playbook
 *   codeActionProvider: quick fixes                 steps Quick fixes, Add an import; checks Create class makes a type that a use
 *                                                   in the module names, the fix for an unused import removes it, the fix for an
 *                                                   unused local removes it
 *   codeActionProvider: refactorings                steps Generate a doc comment, Extract and inline, Safe delete; checks Inline puts
 *                                                   a returned local's value in the return, the expression that a private method of
 *                                                   the file returns where it is called, a private static constant's value where its
 *                                                   file uses it; Safe delete deletes the unused private member. The server extracts
 *                                                   a whole returned expression or a typed local's initializer, inlines only a private
 *                                                   member of the same file, safely deletes a private member, and creates a class for
 *                                                   a new without arguments in the module
 *   codeAction/resolve                              steps Create a method, Extracted to a local, Extract a method, Generate a doc
 *                                                   comment (through xtc.resolveCodeAction, below)
 *   executeCommandProvider: xtc.applyCodeAction     not offered to IntelliJ, whose code actions are literals
 *   documentFormattingProvider                      step Reformat code; check Actions on Save reformat a file as it is saved
 *   documentRangeFormattingProvider                 check Reformat Code on a selection formats only the selection; several ranges:
 *                                                   playbook X132
 *   documentOnTypeFormattingProvider: Enter         step Enter and braces
 *   documentOnTypeFormattingProvider: }             check typing a closing brace indents its line; ; and ): playbook X290
 *   inlayHintProvider                               step Inlay hints and references; checks an inferred local's type is a hint,
 *                                                   turning the inlay hints off takes them away; resolve: playbook X131
 *   inlineValueProvider                             needs LSP4IJ 0.30.0 (UP50); the Coming card; playbook X331
 *   documentLinkProvider                            check a URL in a doc comment is a link; resolve: playbook X131
 *   signatureHelpProvider                           step Parameter info; check parameter info shows as a call's ( is typed
 *   semanticTokensProvider: full                    step Semantic highlighting
 *   semanticTokensProvider: delta, range            playbook X126
 *   workspaceSymbolProvider                         step Go to symbol; check Go to symbol finds the XDK's declarations too;
 *                                                   resolve: playbook X131
 *   fileOperations: willRename, didRename         checks renaming a file renames its type and every use, renaming the file
 *                                                   renames its type, moving a class to another package moves its file
 *   fileOperations: willCreate, didCreate         check a new file's type is one the project's code can use at once
 *   fileOperations: willDelete, didDelete         check deleting the file makes its use an error
 *   workspace.textDocumentContent                   LSP4IJ 0.21.0 has no client (UP25): step The XDK's sources opens the bundled
 *                                                   source read-only instead; playbook X254
 *   workspace.workspaceFolders                      step Open the project; changes: playbook WorkspaceFolders
 *   codeLensProvider: references                    step Inlay hints and references; checks the references lens lists a declaration's
 *                                                   uses, the lenses count references of declarations, not of a constructor's
 *                                                   finally block, turning the reference counts off takes their lenses away
 *   codeLensProvider: Run                           check the Run lens runs the module; playbook X280
 *   linkedEditingRangeProvider                      the IDE's editing needs LSP4IJ 0.30.0, the Coming card; playbook X133
 *   monikerProvider                                 no IntelliJ UI but the library view's: playbook X254
 *   typeHierarchyProvider                           step Type hierarchy
 *   callHierarchyProvider                           step Call hierarchy
 *   workDoneProgress                                playbook X145
 *   workspace/configuration (to the client)         check the code style's indent is the formatter's (xtc.formatting); step The
 *                                                   compiler (xtc.compiler); check turning the reference counts off (xtc.codeLens)
 *   workspace/inlayHint/refresh and the others      check turning the inlay hints off takes them away, and on brings them back
 *   window/logMessage, $/logTrace                   check Toggle Ecstasy Language Server Log; step Language servers
 *
 * Language server: xtc/ requests and notifications
 *   xtc/languageServiceStatus                       check the page's effective configuration is the running server's
 *   xtc/exportLogs                                  check Export Ecstasy Server Logs saves them as a ZIP
 *   xtc/compilerSourceModules                       step The compiler
 *   xtc/discoverConsumers                           the Compiler page's Add consumers from folders: playbook X99, X105
 *   xtc/rename                                      steps Rename, Renamed everywhere, Undo asks first, Undone everywhere, Rename a
 *                                                   class; check renaming a local and a parameter renames their uses
 *   xtc/renameFiles                                 checks renaming a file renames its type and every use, moving a class to
 *                                                   another package, renaming the file renames its type
 *   xtc/build                                       steps Build Project, A failed build
 *   xtc/run, xtc/runOutput                          steps Run, Test results; check the Run lens runs the module
 *   xtc/runInput                                    step Console input
 *   xtc/runExited                                   check the game plays to its end, and the run ends with its exit code
 *   xtc/stopRun                                     check Rerun runs it again, and Stop ends it
 *   xtc/runLenses                                   step The Run icon
 *   xtc/tests                                       steps Test icons, Run or debug a test
 *   xtc/testEvent                                   steps Test results, A failing test
 *   xtc/debugStart, xtc/debugMessage, xtc/debugEnd  step Debug, and every debugging step
 *   xtc/debugTask (to the client)                   step Debug with Gradle; check in command-line mode, Debug runs xtc
 *   xtc/healthCheck                                 VS Code's; its playbook
 *
 * Debug adapter: XtcDebugServer's capabilities and requests
 *   initialize, launch, configurationDone           step Debug
 *   attach                                          check an Ecstasy Attach configuration debugs the game that xtc runs outside
 *   setBreakpoints: lines                           step Frames and variables
 *   setBreakpoints: conditions                      steps A conditional breakpoint, Debug, A condition that cannot be evaluated
 *   setBreakpoints: hit conditions, log messages    needs LSP4IJ 0.30.0 (UP55, UP32); the Coming card; playbook X311
 *   setExceptionBreakpoints                         step An exception
 *   setExceptionBreakpoints: filter conditions      needs LSP4IJ 0.30.0 (UP34); the Coming card; playbook X312
 *   exceptionInfo                                   step An exception; its stack in the hint needs LSP4IJ 0.30.0 (UP33)
 *   setFunctionBreakpoints                          needs LSP4IJ 0.30.0 (UP46); the Coming card
 *   dataBreakpointInfo, setDataBreakpoints          needs LSP4IJ 0.30.0 (UP48); the Coming card
 *   threads                                         checks the threads are the program's fibers, the game's while it deals, the
 *                                                   Scoreboard service's while it records the win
 *   stackTrace, scopes, variables                   steps Debug, Frames and variables
 *   continue                                        steps Frames and variables, The program's console
 *   next, stepIn, stepOut                           steps Step over, Step into, Step out
 *   stepIn into the XDK, library frames             check Force Step Into goes into the XDK's code, whose source opens read-only
 *   stepInTargets                                   step Smart step into
 *   Run to Cursor (a temporary breakpoint)          step Run to cursor, however long the program takes (UP44's workaround)
 *   evaluate: repl                                  step Evaluate expression; checks with Let the program run, Evaluate Expression
 *                                                   calls the Scoreboard service, a line typed in the debug console while paused
 *   evaluate: watch                                 step Watches; check a watch of a call says that it is not evaluated
 *   evaluate: hover                                 check hovering a name in the paused frame shows its value
 *   evaluate: the program's input                   check a line typed in the debug console while the program runs is its input
 *   setVariable                                     check Stop at the program's first line stops before it prints, and Set Value
 *                                                   changes what it prints
 *   launch: stopOnEntry                             check Stop at the program's first line stops before it prints
 *   launch: evaluationRunsProgram                   check with Let the program run, Evaluate Expression calls the Scoreboard service
 *   output event                                    step The program's console
 *   terminate, disconnect                           every step that stops a session; check attach (disconnect detaches)
 *   completions                                     Evaluate Expression's completion: playbook X320 (UP37)
 *   pause                                           needs LSP4IJ 0.30.0 (UP30); the Coming card; playbook X308
 *   restartFrame                                    needs LSP4IJ 0.30.0 (UP47); the Coming card; playbook X329
 *   modules, loadedSources                          needs LSP4IJ 0.30.0 (UP45); the Coming card; playbook X326
 *   valueFormattingOptions (hex), xtc/valueFormat   needs LSP4IJ 0.30.0 (UP36); the Coming card
 *   variables: pages, lazy values                   needs LSP4IJ 0.30.0 (UP42, UP35); the Coming card
 *   invalidated event, cancel                       needs LSP4IJ 0.30.0 (UP40); LSP4IJ sends no cancel (UP41): VS Code's playbook
 *   singleThreadExecutionRequests                   LSP4IJ 0.21.0 does not ask for it: dap-server's tests
 *
 * The plugin: plugin.xml's actions
 *   xtc.selectLanguageAdapter                       check Ctrl+Alt+X, A, and the other adapter chosen, restarts the server with
 *                                                   it, and back; the adapter in use, chosen again, keeps the server
 *   xtc.showReferences                              check the references lens lists a declaration's uses
 *   xtc.exportServerLogs                            check Export Ecstasy Server Logs saves them as a ZIP
 *   xtc.resolveCodeAction                           steps Create a method, Extracted to a local, Extract a method, Generate a doc
 *                                                   comment; check Undo takes the created method back in one step, without
 *                                                   asking, as a change of one file
 *   xtc.runModule                                   check the Run lens runs the module
 *   xtc.toggleServerLog                             checks Ctrl+Alt+X, L shows the server's log, and hides it again; Tools |
 *                                                   Toggle Ecstasy Language Server Log, clicked, shows it
 *
 * The plugin: plugin.xml's extensions and settings pages
 *   fileType Ecstasy, TextMate grammar, icons, tab titles  check an Ecstasy file is the plugin's file type, with its icon and
 *                                                   its tab, and TextMate's grammar colors it; every frame
 *   lsp4ij server, fileNamePatternMapping           step Open the project; step Language servers
 *   lsp4ij debugAdapterServer                       step Debug, and every debugging step
 *   newProjectWizard.generator                      step New Project; making the project: playbook X303
 *   configurationType Ecstasy Application           steps The run configuration, Run; check the arguments that the configuration
 *                                                   gives are the program's
 *   configurationType Ecstasy Attach                check an Ecstasy Attach configuration debugs the game that xtc runs outside
 *   runConfigurationProducer                        step Debug, whose configuration is the open module's, as Current File
 *   projectTaskRunner                               steps Build Project, A failed build
 *   defaultLiveTemplates, liveTemplateContext       steps Live templates, Expand a template
 *   lang.parserDefinition (the Ecstasy PSI)         check an Ecstasy file's PSI is the plugin's language; the steps of the features
 *                                                   below, which IntelliJ looks up by language
 *   lang.psiStructureViewFactory                    steps File structure, The Structure window
 *   lang.foldingBuilder, codeBlockProvider          step Folding
 *   typeHierarchyProvider, callHierarchyProvider    steps Type hierarchy, Call hierarchy
 *   lang.commenter                                  step Comment out lines
 *   lang.importOptimizer                            step Optimize imports
 *   renameHandler (declarations, files)             steps Rename, Rename a class; check renaming a file renames its type and every use
 *   refactoring.moveHandler                         check moving a class to another package moves its file
 *   codeInsight.parameterInfo                       step Parameter info
 *   formattingService                               step Reformat code; checks on a selection, on save
 *   langCodeStyleSettingsProvider                   step Code style; check the code style's indent is the formatter's
 *   enterHandlerDelegate (empty blocks, braces)     step Enter and braces
 *   postStartupActivity, externalSystemTaskNotificationListener (the Gradle model)  step The compiler, whose modules
 *                                                   the Gradle import gave
 *   postStartupActivity (server control, editor delegates)  no user feature: ServerControlEndpointTest, the playbook
 *   notificationGroup Ecstasy Language Server       check Export Ecstasy Server Logs saves them as a ZIP, whose notification says so
 *   projectConfigurable xtc.build                   step The build mode
 *   projectConfigurable xtc.service                 step The language server; checks the page's effective configuration, turning
 *                                                   the inlay hints off
 *   projectConfigurable xtc.compiler                step The compiler
 *   applicationConfigurable xtc.runtime             step The server's runtime, whose page has the logs' and the JVM's options
 *   applicationConfigurable xtc.service.defaults    check the application's defaults page has the same settings; playbook X136
 *
 * IntelliJ and LSP4IJ, as a user uses them
 *   the main toolbar's Run and Debug                step Debug; check the arguments that the configuration gives are the program's;
 *                                                   step Run with xtc
 *   the gutter's Run and test icons                 steps Run, Run or debug a test, Test results
 *   Rerun, Stop                                     check Rerun runs it again, and Stop ends it
 *   build modes: embedded, Gradle, xtc              steps Run, Run with Gradle, Run with xtc, Debug with Gradle, Tests with Gradle;
 *                                                   checks in command-line mode, Debug runs xtc; the tests run with xtc test
 *   the Gradle window                               step The Gradle window
 *   a test method, a module's tests                 check a test method's icon runs that test alone; step Test results
 *   every test module, Run 'All Ecstasy Tests'      checks Run 'All Ecstasy Tests' on the test sources runs every test module's
 *                                                   tests, embedded, into one run; testXtc, run from the Gradle window, runs
 *                                                   every test module
 *   a test added while the IDE runs                 check a test added to the file gets its icon and runs with its module,
 *                                                   without a restart
 *   an error, apart from a failure                  check a test that throws is an error, apart from a failed assertion
 *   a disabled test                                 check a disabled test is reported as skipped
 *   Stop, in a test run                             check Stop ends a test run partway, and the test that was running does
 *                                                   not pass
 *   Rerun Failed Tests                              check Rerun Failed Tests runs only the test that failed
 *   the results tree to the source                  check a test in the results goes to its source
 *   embedded and Gradle results, test by test       check embedded and Gradle runs give each test the same result
 *   Debug a test                                    step Debug a test
 *   LSP4IJ's Restart                                check Restart in the Language Servers window restarts the server; the
 *                                                   plugin's own Restart, on Settings | Ecstasy Server Runtime and Logs, clicked
 *   LSP4IJ's log and trace                          step Language servers; check Toggle Ecstasy Language Server Log
 *   the server's process dies                       check the server comes back when its process dies
 *
 * Parity with the VS Code tour, by the coverage table of its script (lang/vscode-extension/src/test/tour/script.ts on
 * lagergren/vscode-tour, b6025441ec): the shared servers' features that one tour checks and the other does not.
 *   VS Code's only: the debug adapter's hit counts and logpoints, function and data breakpoints, exception filter
 *     conditions, pause, restartFrame, modules and loadedSources, hexadecimal values, lazy variables and pages,
 *     invalidated, the console's completions and singleThread, which LSP4IJ 0.21.0 does not ask for (the Coming card,
 *     UP30-UP55); inlineValue and linkedEditingRange, which need LSP4IJ 0.30.0; workspace.textDocumentContent, which
 *     LSP4IJ has no client for (UP25); semanticTokens delta and range, rangesFormatting, inlayHint/resolve,
 *     documentLink/resolve, workspaceSymbol/resolve, moniker, workDoneProgress and the refresh requests, which the VS
 *     Code tour checks by its server trace and IntelliJ by the playbook (X126, X131, X132, X145, X254);
 *     workspace/didChangeWatchedFiles; xtc/healthCheck; xtc/discoverConsumers (IntelliJ: playbook X99, X105)
 *   IntelliJ's only: xtc/runLenses, as VS Code takes its Run lens from textDocument/codeLens; willRenameFiles, which
 *     VS Code's participant replaces by xtc/renameFiles; the Gradle model's import; the IDE's Build window and Project
 *     Errors
 *   Neither: didChangeWorkspaceFolders, which both playbooks cover (WorkspaceFolders, --multi-root); the debug adapter's
 *     cancel, which LSP4IJ never sends and VS Code only for a request it abandons (playbook X314); executeCommand,
 *     advertised to neither, whose code actions are literals
 */

package org.xtclang.idea.playbook

import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

/** The recording's screen: the IDE's window, above the caption band that the composer adds under it. */
private const val WIDTH = 1920
private const val HEIGHT = 936

/** The game's module file, whose gutter has its Run icon. */
private const val MODULE = "src/main/x/cardGame.x"

/** isDone's return, its expression extracted to a local. */
private val EXTRACTED = Regex("""\n\s+Boolean \w+ = one\.hand\.isEmpty\(\) \|\| two\.hand\.isEmpty\(\);\s+return \w+;""")

/** The tests of DeckTest, which the test steps run. */
private const val DECK_TESTS = 8

/** The block that Enter and braces opens before drawForMatch's return: the return in it, indented, and its brace closing it. */
private val WRAPPED = Regex("""if \(hand\.isEmpty\(\)\) \{\n {12}return drawForMatch\(eights, prev\);\n {8}\}""")

/** The tour's steps, in order: what each shows, says and does. */
internal fun Tour.script() {
    awaitProject()
    ui.prepare(WIDTH, HEIGHT, "Dark", 15)
    ui.open(source("Eights.x"), 0)
    awaitServer()
    awaitIdle()
    // the first analysis of the project, which the first steps show; a bound against a hang only
    within(HANG) { ui.analyzed(source("Eights.x")) && ui.lenses().isNotEmpty() }

    start()
    reading()
    navigation()
    writing()
    diagnostics()
    refactoring()
    files()
    building()
    debugging()
    testing()
    settings()
    coming()
}

private fun Tour.start() {
    section("Start here")
    card(
        "Ecstasy in IntelliJ",
        "The XTC plugin on a real project: everything it does for Ecstasy code, from typing to tests.",
        TourCard(
            eyebrow = "XTC LANGUAGE SUPPORT · INTELLIJ IDEA",
            heading = "Ecstasy in IntelliJ, end to end",
            lead = "A guided tour of the XTC plugin, on a small game written in Ecstasy",
            boxes =
                listOf(
                    TourBox(
                        "Edit and understand",
                        "Highlighting, documentation, completion, navigation, diagnostics and fixes, refactoring and formatting.",
                    ),
                    TourBox(
                        "Run, debug and test",
                        "Embedded, Gradle and command-line runs, breakpoints and stepping, evaluation, and xunit tests.",
                    ),
                ),
        ),
    )
    card(
        "The project",
        "Crazy Eights: two players, a deck, and the first to play all of their cards wins. A project as New Project makes it.",
        TourCard(
            eyebrow = "THE TOUR'S PROJECT",
            heading = "Crazy Eights",
            lead = "Play a card of the same suit or rank as the top of the pile; an eight matches anything.",
            code =
                """
                cardGame/
                  build.gradle.kts            the XTC Gradle plugin
                  src/main/x/cardGame.x       module cardGame: run() plays a game
                  src/main/x/cardGame/        Card, Deck, Hand, Player, Eights,
                                              PlayStrategy, Scoreboard, ...
                  src/test/x/                 xunit tests: DeckTest, PlayerTest, ...
                """.trimIndent(),
        ),
    )
    shot(
        "Open the project",
        "The project's sources, and the game open in the editor. The plugin starts its language server, with the XDK bundled.",
        feature = "initialize · workspace folders",
        focus = "toolwindow:Project",
        check = { expect("cardGame" in toolWindowText("Project")) { "The Project window: ${toolWindowText("Project").take(300)}" } },
    )
    verify("a probe's call that a modal dialog holds fails at once, with the dialog's title, and the dialog closes") {
        // the harness checks itself: run 12 hung for 25 minutes behind a dialog that a non-modal call waited on
        showModalMessage("Harness Check", "A dialog that waits for an answer")
        expect(within(HANG) { dialogShowing() }) { "The dialog did not show" }
        // the driver wraps the probe's failure, so its message is in a cause, not in the failure the tour gets
        val causes =
            generateSequence(runCatching { editorState() }.exceptionOrNull()) { it.cause }
                .joinToString(" <- ") { "${it.javaClass.simpleName}: ${it.message}" }
        expect("Harness Check" in causes) { "A call behind the dialog did not fail with its title: ${causes.ifEmpty { "it answered" }}" }
        closeDialogs()
        expect(within(HANG) { !dialogShowing() }) { "The dialog did not close" }
    }
    verify("an Ecstasy file is the plugin's file type, with its icon and its tab, and TextMate's grammar colors it") {
        open(source("Eights.x"), 0)
        val state = JsonParser.parseString(fileState(source("Eights.x"), "class")).asJsonObject
        expect(state["fileType"].asString == "Ecstasy" && state["plugin"].asBoolean && state["tab"].asString == "Eights.x") {
            "Eights.x: $state"
        }
        // TextMate's token for the keyword, which the grammar's scopes name
        expect("TextMate" in state["highlighter"].asString && state["token"].asString.let { "keyword" in it || "storage" in it }) {
            "Eights.x's highlighting: $state"
        }
    }
    verify("an Ecstasy file's PSI is the plugin's language, which IntelliJ looks its features up by") {
        val state = JsonParser.parseString(fileState(source("Eights.x"), "class")).asJsonObject
        expect(state["language"].asString == "Ecstasy" && state["psi"].asString.isNotEmpty()) { "Eights.x's PSI: $state" }
    }
}

private fun Tour.reading() {
    section("Reading code")
    shot(
        "Semantic highlighting",
        "The compiler colors what each name is: fields, parameters, locals, types and static members, beyond what the grammar can tell.",
        feature = "textDocument/semanticTokens/full",
        focus = "editor",
        until = { keysAt("prev == Null || cardMatches", 0).isNotBlank() },
        check = {
            // the caret's own highlighting, the name under it, is not a token's
            val method = keysAt("cardMatches(card, prev)", 0).lines().filter { it.isNotBlank() && "UNDER_CARET" !in it }
            val parameter = keysAt("prev == Null || cardMatches", 0).lines().filter { it.isNotBlank() }
            expect(method.isNotEmpty() && parameter.isNotEmpty() && method != parameter) {
                "The tokens: cardMatches $method, prev $parameter"
            }
        },
    ) {
        open(source("Player.x"), 0)
        at("cardMatches(card, prev)")
    }
    var deal = 0
    shot(
        "Inlay hints and references",
        "Hints name a call's parameters where its arguments don't, and the line above each declaration counts its references.",
        feature = "textDocument/inlayHint · codeLens",
        focus = "line",
        settle = 2.seconds,
        until = { "numCards:" in inlays(deal) && "reference" in lenses() },
        check = {
            expect("numCards:" in inlays(deal)) { "The hints on deal's line: ${inlays(deal)}" }
            expect("reference" in lenses()) { "The lenses: ${lenses().take(300)}" }
        },
    ) {
        open(source("Eights.x"), 0)
        deal = at("deck.deal(one.hand")
    }
    verify("an inferred local's type is a hint") {
        open(source("Eights.x"), 0)
        val line = at("val winner")
        expect(within(HANG) { ": Player" in inlays(line) }) { "The hints on winner's line: ${inlays(line)}" }
    }
    verify("a URL in a doc comment is a link") {
        open(source("Card.x"), 0)
        at("https://")
        // the link is the URL, without the sentence's full stop after it
        expect(within(HANG) { "\"text\":\"https://en.wikipedia.org/wiki/Standard_52-card_deck\"" in highlighted("HYPERLINK") }) {
            "The links: ${highlighted("HYPERLINK")}"
        }
    }
    verify("the lenses count references of declarations, not of a constructor's finally block") {
        open(source("Deck.x"), 0)
        val finally = at("} finally {")
        expect(within(HANG) { lenses().lines().any { it.endsWith("@0") } }) { "No lenses in Deck.x: ${lenses()}" }
        expect(lenses().lines().none { it.endsWith("@$finally") }) { "The lenses: ${lenses()}" }
    }
    shot(
        "Quick documentation",
        "Ctrl+Q, or hovering, shows a declaration's signature and its doc comment, from the compiler's view of it.",
        feature = "textDocument/hover",
        focus = "popup",
        until = { "Number of cards dealt" in popupText() },
        check = { popupText().let { expect("Number of cards dealt" in it) { "No doc comment in: $it" } } },
        settle = 2.seconds,
        after = { dismiss() },
    ) {
        open(source("Eights.x"), 0)
        at("INITIAL_HAND_SIZE", delta = 3)
        action("QuickJavaDoc")
    }
    shot(
        "The XDK's documentation",
        "Documentation works the same for the XDK's own types: HashMap's comes from the ecstasy module's sources.",
        feature = "textDocument/hover",
        focus = "popup",
        until = { "implementation of the" in popupText() },
        check = {
            popupText().let {
                expect(
                    "class HashMap" in it && "implementation of the" in it,
                ) { "No HashMap documentation in: $it" }
            }
        },
        settle = 2.seconds,
        after = { dismiss() },
    ) {
        open(source("Scoreboard.x"), 0)
        at("new HashMap", delta = "new Hash".length)
        action("QuickJavaDoc")
    }
    // what the hint showed when the frame was taken: a status tooltip can be the newest popup by the check
    var parameters = ""
    shot(
        "Parameter info",
        "Inside a call, Ctrl+P shows the parameters of the method it calls, with the one at the cursor in bold.",
        feature = "textDocument/signatureHelp",
        focus = "popup",
        until = { popupText().also { parameters = it }.let { "numCards" in it } },
        check = { expect("numCards" in parameters) { "No parameters in: $parameters; now ${popupText()}" } },
        settle = 2.seconds,
        after = { dismiss() },
    ) {
        open(source("Eights.x"), 0)
        at("deck.deal(one.hand, GameConstants", delta = "deck.deal(one.hand, ".length)
        action("ParameterInfo")
    }
    shot(
        "Highlight usages",
        "With the cursor on a name, its uses in the file are highlighted, from the compiler's references rather than its text.",
        feature = "textDocument/documentHighlight",
        focus = "editor",
        settle = 2.seconds,
        until = { usages("card") >= 3 },
        check = { expect(usages("card") >= 3) { "The highlighted uses: ${highlighted("IDENTIFIER_UNDER_CARET")}" } },
    ) {
        open(source("Player.x"), 0)
        at("card := eights.drawCard()", delta = 1)
    }
    shot(
        "Extend selection",
        "Ctrl+W grows the selection by the syntax around it: a name, the expression, the statement, the block. Ctrl+Shift+W shrinks it.",
        feature = "textDocument/selectionRange",
        focus = "caret",
        check = {
            editor()["selection"]?.takeUnless { it.isJsonNull }?.asString.orEmpty().let {
                expect(
                    "cards.size" in it,
                ) { "Selected: $it" }
            }
        },
    ) {
        open(source("CardCollection.x"), 0)
        at("cards[cards.size - 1]", delta = "cards[cards.".length)
        repeat(3) {
            action("EditorSelectWord")
            Thread.sleep(700)
        }
    }
    shot(
        "Folding",
        "Methods and doc comments fold, as the compiler's folding ranges mark them; Ctrl+Shift+minus folds them all.",
        feature = "textDocument/foldingRange",
        focus = "editor",
        until = { folds().lines().size >= 3 },
        check = { expect(folds().lines().size >= 3) { "The folded regions: ${folds()}" } },
        after = { action("ExpandAllRegions") },
    ) {
        open(source("Eights.x"), 0)
        action("CollapseAllRegions")
        within(HANG) { folds().isNotBlank() }
        // the class's own body stays open, so that its folded members show
        at("class Eights", delta = "class ".length)
        action("ExpandRegion")
    }
    shot(
        "File structure",
        "Ctrl+F12 lists the file's classes, properties and methods, from the compiler's symbols; type to filter, Enter to go.",
        feature = "textDocument/documentSymbol",
        focus = "popup",
        until = { "reshuffle" in popupText() },
        check = { popupText().let { expect("playGame" in it && "reshuffle" in it) { "The structure: $it" } } },
        settle = 2.seconds,
        after = { key("ESCAPE") },
    ) {
        action("FileStructurePopup")
    }
    shot(
        "The Structure window",
        "The Structure window keeps the outline beside the editor, and follows the cursor as it moves.",
        feature = "textDocument/documentSymbol",
        focus = "toolwindow:Structure",
        until = { "playGame" in toolWindowText("Structure") },
        check = { toolWindowText("Structure").let { expect("playGame" in it) { "The Structure window: ${it.take(300)}" } } },
        after = { toolWindow("Structure", false) },
    ) {
        toolWindow("Structure", true)
    }
}

/** How many of the selected editor's uses of [name] the caret's name highlights. */
private fun TourProbe.usages(name: String): Int =
    JsonParser
        .parseString(highlighted("IDENTIFIER_UNDER_CARET"))
        .asJsonArray
        .count { it.asJsonObject["text"].asString == name }

private fun Tour.navigation() {
    section("Navigation")
    shot(
        "Go to declaration",
        "Ctrl+B, or Ctrl+click, on a call goes to the method it calls: shuffle() is CardCollection's, which Deck inherits.",
        feature = "textDocument/definition",
        focus = "caret",
        until = { editor()["file"].asString == "CardCollection.x" },
        check = { expect(editor()["file"].asString == "CardCollection.x") { "In ${editor()["file"]}" } },
        settle = 2.seconds,
    ) {
        open(source("Eights.x"), 0)
        at("deck.shuffle()", delta = "deck.".length)
        action("GotoDeclaration")
    }
    verify("LSP's Go to declaration goes to the method's declaration too") {
        open(source("Eights.x"), 0)
        at("deck.shuffle()", delta = "deck.".length)
        action("LSP.GotoDeclaration")
        expect(within(HANG) { editor()["file"].asString == "CardCollection.x" }) { "In ${editor()["file"]}" }
    }
    shot(
        "Go to type declaration",
        "Ctrl+Shift+B goes to the type of what is under the cursor: deck is a Deck, declared in Deck.x.",
        feature = "textDocument/typeDefinition",
        focus = "editor",
        until = { editor()["file"].asString == "Deck.x" },
        check = { expect(editor()["file"].asString == "Deck.x") { "In ${editor()["file"]}" } },
        settle = 2.seconds,
    ) {
        open(source("Eights.x"), 0)
        at("deck.shuffle()", delta = 2)
        action("LSP.GotoTypeDefinition")
    }
    shot(
        "Go to implementation",
        "Ctrl+Alt+B on an interface's method lists its implementations: FirstMatch's and SaveEights'.",
        feature = "textDocument/implementation",
        focus = "popup",
        until = { popupText().let { "FirstMatch" in it && "SaveEights" in it } },
        check = { popupText().let { expect("FirstMatch" in it && "SaveEights" in it) { "The implementations: $it" } } },
        settle = 2.seconds,
        after = { dismiss() },
    ) {
        open(source("PlayStrategy.x"), 0)
        at("choose(Hand", delta = 2)
        action("LSP.GotoImplementation")
    }
    shot(
        "Find usages",
        "Alt+F7 finds every use of a declaration across the project's modules, the tests' included.",
        feature = "textDocument/references",
        focus = "toolwindow:Find",
        // the results group by kind and by file, which open as their arrows are clicked
        until = {
            expand("Find", "")
            toolWindowText("Find").let { "FirstMatch" in it && "PlayerTest" in it }
        },
        check = {
            toolWindowText("Find").let {
                expect("FirstMatch" in it && "SaveEights" in it && "PlayerTest" in it) { "The usages: ${it.take(600)}" }
            }
        },
        settle = 3.seconds,
        after = { toolWindow("Find", false) },
    ) {
        open(source("Player.x"), 0)
        at("static Boolean cardMatches", delta = "static Boolean ".length)
        action("FindUsages")
    }
    verify("the references lens lists a declaration's uses") {
        open(source("Player.x"), 0)
        val line = at("static Boolean cardMatches")
        expect(within(HANG) { lenses().lines().any { it.endsWith("@$line") && "reference" in it } }) { "The lenses: ${lenses()}" }
        clickLens("", line)
        expect(within(HANG) { popupText().let { "FirstMatch" in it && "SaveEights" in it } }) { "The references: ${popupText()}" }
        dismiss()
    }
    shot(
        "Call hierarchy",
        "Ctrl+Alt+H shows who calls a method, and who calls them: cardMatches is called by the player and by both strategies.",
        feature = "callHierarchy/incomingCalls",
        focus = "toolwindow:Hierarchy",
        until = { toolWindowText("Hierarchy").let { "choose" in it && "drawForMatch" in it } },
        check = { toolWindowText("Hierarchy").let { expect("choose" in it && "drawForMatch" in it) { "The callers: ${it.take(400)}" } } },
        settle = 3.seconds,
    ) {
        open(source("Player.x"), 0)
        at("static Boolean cardMatches", delta = "static Boolean ".length)
        action("CallHierarchy")
    }
    shot(
        "Type hierarchy",
        "Ctrl+H shows a class's supertypes and subtypes: Deck and Hand extend CardCollection.",
        feature = "typeHierarchy/subtypes",
        focus = "toolwindow:Hierarchy",
        until = { toolWindowText("Hierarchy").let { "Deck" in it && "Hand" in it } },
        check = { toolWindowText("Hierarchy").let { expect("Deck" in it && "Hand" in it) { "The subtypes: ${it.take(400)}" } } },
        settle = 3.seconds,
        after = { toolWindow("Hierarchy", false) },
    ) {
        open(source("CardCollection.x"), 0)
        at("class CardCollection", delta = "class Card".length)
        action("TypeHierarchy")
    }
    shot(
        "Go to symbol",
        "Ctrl+Alt+Shift+N finds a class, method or property by name, in the project and the XDK.",
        feature = "workspace/symbol",
        focus = "popup",
        until = { "reshuffle" in popupText() },
        check = { popupText().let { expect("reshuffle" in it) { "The symbols: ${it.take(300)}" } } },
        settle = 3.seconds,
        // the key reaches the popup through the window system, later: in tour run 12813917966189450822 the check's Go
        //  to symbol came first, while this popup still showed, and opened none
        after = {
            key("ESCAPE")
            within(HANG) { !showing("popup") }
        },
    ) {
        action("GotoSymbol")
        // its search field takes the name once it has the focus
        within(HANG) { runCatching { fill("reshuffle") }.isSuccess }
    }
    verify("Go to symbol finds the XDK's declarations too") {
        action("GotoSymbol")
        within(HANG) { runCatching { fill("getOrDefault") }.isSuccess }
        expect(within(HANG) { "getOrDefault" in popupText() && "Map" in popupText() }) { "The symbols: ${popupText().take(400)}" }
        key("ESCAPE")
        within(HANG) { !showing("popup") }
    }
    shot(
        "The XDK's sources",
        "Go to declaration also opens the XDK's sources, read-only: HashMap is in the ecstasy module's collections package.",
        feature = "textDocument/definition",
        focus = "editor",
        until = { editor()["file"].asString == "HashMap.x" },
        check = { editor().let { expect(it["file"].asString == "HashMap.x" && !it["writable"].asBoolean) { "In $it" } } },
        settle = 3.seconds,
    ) {
        open(source("Scoreboard.x"), 0)
        at("new HashMap", delta = "new Hash".length)
        action("GotoDeclaration")
    }
}

private fun Tour.writing() {
    section("Writing code")
    shot(
        "Member completion",
        "After a dot, completion lists the members of the expression's type, as the compiler resolves it, with their types.",
        feature = "textDocument/completion",
        focus = "lookup",
        until = { "addCard" in lookupItems() },
        check = { lookupItems().let { expect("addCard" in it && "popCard" in it) { "The items: $it" } } },
        settle = 2.seconds,
        after = {
            dismiss()
            restore()
        },
    ) {
        open(source("Player.x"), 0)
        at("console.print(\$\"{name} draws {card}\");", delta = "console.print(\$\"{name} draws {card}\");".length)
        type("\nhand.")
        within(HANG) { analyzed(source("Player.x")) }
        action("CodeCompletion")
    }
    verify("a completion item's documentation shows beside the list") {
        open(source("Player.x"), 0)
        at("console.print(\$\"{name} draws {card}\");", delta = "console.print(\$\"{name} draws {card}\");".length)
        type("\nhand.")
        // the keys in the focused editor, as a user presses them there
        focusedEditor()
        action("CodeCompletion")
        expect(within(HANG) { "popCard" in lookupItems().lines() }) { "The items: ${lookupItems()}" }
        expect(selectLookup("popCard")) { "No popCard among ${lookupItems()}" }
        action("QuickJavaDoc")
        // how the list stands as Ctrl+Q has gone, for a failure's message: run 16's had closed an idle window later
        val pressed = lookupState()
        expect(within(HANG) { "Take the top card off the pile" in popupText() }) {
            "The documentation: ${popupText().take(300)}; as Ctrl+Q went, $pressed; now ${lookupState()}; the daemon for " +
                "Player.x: ${daemonState(source("Player.x"))}"
        }
        dismiss()
    }
    restore()
    verify("a chosen completion makes code that compiles") {
        open(source("Player.x"), 0)
        at("console.print(\$\"{name} draws {card}\");", delta = "console.print(\$\"{name} draws {card}\");".length)
        type("\nInt left = hand.si")
        action("CodeCompletion")

        // Ctrl+Space inserts a sole suggestion at once, and lists several to choose from; a method's item, size or
        //  size(), inserts its call
        fun size() = lookupItems().lines().firstOrNull { it == "size" || it.startsWith("size(") }
        expect(within(HANG) { size() != null || fileHas(source("Player.x"), "Int left = hand.size") }) {
            "The items: ${lookupItems()}; ${fileText(source("Player.x")).lines().firstOrNull { "Int left" in it }}"
        }
        size()?.let { item -> expect(pickLookup(item)) { "No $item among ${lookupItems()}" } }
        type(";")
        expect(within(HANG) { fileHas(source("Player.x"), "Int left = hand.size();") }) {
            "Completed: ${fileText(source("Player.x")).lines().firstOrNull { "Int left" in it }}"
        }
        compilesCleanly(source("Player.x"))
    }
    restore()
    shot(
        "Name completion",
        "As a name is typed, completion offers the types, variables and keywords in scope: here the game's Scoreboard.",
        feature = "textDocument/completion",
        focus = "lookup",
        until = { "Scoreboard" in lookupItems() },
        check = { lookupItems().let { expect("Scoreboard" in it) { "The items: $it" } } },
        settle = 2.seconds,
        after = {
            dismiss()
            restore()
        },
    ) {
        open(source("Eights.x"), 0)
        at("Player current = one;", delta = "Player current = one;".length)
        type("\nScore")
        within(HANG) { analyzed(source("Eights.x")) }
        action("CodeCompletion")
    }
    verify("inline completion suggests a name in scope as it is typed") {
        open(source("Eights.x"), 0)
        at("Player current = one;", delta = "Player current = one;".length)
        type("\nPlayer next = curr")
        expect(within(HANG) { "ent" in ghostText() }) { "No ghost text: \"${ghostText()}\"" }
        dismiss()
    }
    restore()
    verify("completing a type of another module adds its import") {
        open(test("PlayerTest.x"), 0)
        at("Player player = new Player(\"Alice\");", delta = "Player player = new Player(\"Alice\");".length)
        type("\nDec")
        action("CodeCompletion")
        expect(within(HANG) { "Deck" in lookupItems().lines() }) { "The items: ${lookupItems()}" }
        expect(pickLookup("Deck")) { "No Deck among ${lookupItems()}" }
        insert(" deck = new Deck(\"Test\");")
        expect(within(HANG) { fileHas(test("PlayerTest.x"), "import cardGame.Deck;") }) { "No import of Deck in PlayerTest.x" }
        compilesCleanly(test("PlayerTest.x"))
    }
    restore()
    verify("parameter info shows as a call's ( is typed") {
        open(source("Eights.x"), 0)
        at("deck.shuffle();", delta = "deck.shuffle();".length)
        type("\ndeck.deal(")
        expect(within(HANG) { "numCards" in popupText() }) { "No parameter info: ${popupText()}" }
        dismiss()
    }
    restore()
    shot(
        "Live templates",
        "Ctrl+J lists the Ecstasy live templates: a module, class, interface, service, mixin, enum, const, package or method.",
        feature = "InsertLiveTemplate",
        focus = "lookup",
        until = { "meth" in lookupItems() },
        check = { lookupItems().let { expect("meth" in it && "cls" in it) { "The templates: $it" } } },
        settle = 2.seconds,
        after = { dismiss() },
    ) {
        open(source("Eights.x"), 0)
        at("public Hand   discardPile;", delta = "public Hand   discardPile;".length)
        type("\n\n")
        println("Tour: live template contexts at the caret: ${templateContexts()}")
        action("InsertLiveTemplate")
    }
    shot(
        "Expand a template",
        "An abbreviation and Tab expand its template, and Tab then moves through its placeholders: the type, name and parameters.",
        feature = "ExpandLiveTemplateByTab",
        focus = "editor",
        until = { "myMethod(" in fileText(source("Eights.x")) },
        check = { expect("myMethod(" in fileText(source("Eights.x"))) { "No expanded method in Eights.x" } },
        settle = 2.seconds,
        after = {
            key("ESCAPE")
            restore()
        },
    ) {
        type("meth")
        action("ExpandLiveTemplateByTab")
    }
    shot(
        "Enter and braces",
        "A brace and Enter open a block around the statement that follows, indented as the code style says, and close it.",
        feature = "textDocument/onTypeFormatting",
        focus = "caret",
        until = { WRAPPED in fileText(source("Player.x")) },
        check = { expect(WRAPPED in fileText(source("Player.x"))) { "No block around the return in Player.x" } },
        after = { restore() },
    ) {
        open(source("Player.x"), 0)
        at("return drawForMatch(eights, prev);")
        type("if (hand.isEmpty()) {\n")
    }
    verify("typing a closing brace indents its line") {
        open(source("Hand.x"), 0)
        replace("        console.print();\n    }\n", 0, "        console.print();\n            \n")
        at("        console.print();\n            \n", delta = "        console.print();\n            ".length)
        type("}")
        // display's own brace, at the method's indent, which only the typed brace can be
        expect(within(HANG) { fileHas(source("Hand.x"), "        console.print();\n    }\n") }) {
            "The brace's line: ${fileText(source("Hand.x")).lines().dropWhile { "console.print();" !in it }.take(3)}"
        }
    }
    restore()
    shot(
        "Comment out lines",
        "Ctrl+/ comments out the selected lines with //, and again removes them; Ctrl+Shift+/ wraps a selection in /* */.",
        feature = "CommentByLineComment",
        focus = "editor",
        check = { expect(Regex("""//\s*return card1\.suit""") in fileText(source("Player.x"))) { "The lines are not commented out" } },
        after = { restore() },
    ) {
        open(source("Player.x"), 0)
        select(
            "return card1.suit == card2.suit\n" +
                "            || card1.rank == card2.rank\n" +
                "            || card1.rank == GameConstants.SPECIAL_CARD;",
        )
        action("CommentByLineComment")
    }
    verify("Ctrl+Shift+/ wraps the selection in a block comment") {
        open(source("Player.x"), 0)
        select("card1.rank == GameConstants.SPECIAL_CARD")
        action("CommentByBlockComment")
        expect(within(HANG) { Regex("""/\*\s*card1\.rank == GameConstants\.SPECIAL_CARD\s*\*/""") in fileText(source("Player.x")) }) {
            "No block comment around the selection in Player.x"
        }
    }
    restore()
    shot(
        "Code that needs formatting",
        "swapCards is indented too far, and a space sits before a parenthesis: the code style's settings say how Ecstasy code is laid out.",
        feature = "Code Style | Ecstasy",
        focus = "editor",
        check = {
            fileText(source("CardCollection.x")).let {
                expect("\n              cards.swap(index1, index2);" in it) { "swapCards is not indented too far: ${it.take(300)}" }
            }
        },
    ) {
        open(source("CardCollection.x"), 0)
        at("cards.swap(index1, index2);")
    }
    shot(
        "Reformat code",
        "Ctrl+Alt+L reformats the file, or the selection, through the language server's formatter, with the IDE's code style.",
        feature = "textDocument/formatting",
        focus = "editor",
        until = { "\n            cards.swap(index1, index2);" in fileText(source("CardCollection.x")) },
        check = {
            expect(
                "\n            cards.swap(index1, index2);" in fileText(source("CardCollection.x")),
            ) { "swapCards is still indented too far" }
            compilesCleanly(source("CardCollection.x"))
        },
        settle = 3.seconds,
        after = { restore() },
    ) {
        at("cards.swap(index1, index2);")
        action("ReformatCode")
    }
    verify("Reformat Code on a selection formats only the selection") {
        open(source("CardCollection.x"), 0)
        replace("        cards.add(card);", 0, "          cards.add(card);")
        select(
            "    void swapCards(Int index1, Int index2) {\n" +
                "        if (index1 < cards.size && index2 < cards.size) {\n" +
                "              cards.swap(index1, index2);\n" +
                "        }\n" +
                "    }",
        )
        action("ReformatCode")
        expect(within(HANG) { fileHas(source("CardCollection.x"), "\n            cards.swap(index1, index2);") }) {
            "swapCards is still indented too far"
        }
        expect(fileHas(source("CardCollection.x"), "\n          cards.add(card);")) { "Reformatting the selection changed addCard too" }
    }
    restore()
    verify("Actions on Save reformat a file as it is saved") {
        formatOnSave(true)
        try {
            open(source("CardCollection.x"), 0)
            replace("    void clear() {", 0, "    void clear()  {")
            action("SaveAll")
            expect(within(HANG) { fileHas(source("CardCollection.x"), "\n            cards.swap(index1, index2);") }) {
                "Saving did not reformat swapCards"
            }
        } finally {
            formatOnSave(false)
        }
    }
    restore()
    shot(
        "Code style",
        "Settings | Editor | Code Style | Ecstasy: indents, continuation indents and the other settings the formatter follows.",
        feature = "Code Style | Ecstasy",
        focus = "popup",
        until = { "Continuation indent" in popupText() },
        check = { popupText().let { expect("Continuation indent" in it) { "The page: ${it.take(300)}" } } },
        settle = 3.seconds,
    ) {
        settings("preferences.sourceCode.Ecstasy")
    }
    verify("the code style's indent is the formatter's") {
        // the Code Style page shows: an indent of 2, and Reformat Code indents with it
        expect(within(HANG) { "Continuation indent" in popupText() }) { "No Code Style page" }
        form("Indent:", "2")
        expect(confirm("OK")) { "The Code Style page did not close" }
        try {
            open(source("CardCollection.x"), 0)
            action("ReformatCode")
            expect(within(HANG) { fileHas(source("CardCollection.x"), "\n  void addCard(Card card) {\n    cards.add(card);") }) {
                "Not indented by 2: ${fileText(source("CardCollection.x")).lines().take(12)}"
            }
        } finally {
            settings("preferences.sourceCode.Ecstasy")
            within(HANG) { "Continuation indent" in popupText() }
            form("Indent:", "4")
            confirm("OK")
        }
    }
    restore()
    verifyRestored()
}

private fun Tour.diagnostics() {
    section("Diagnostics")
    shot(
        "Errors as you type",
        "A misspelled method is an error as soon as it is typed: the compiler checks the editor's text, not the saved file.",
        feature = "textDocument/diagnostic",
        focus = "caret",
        until = { "popCrd" in diagnostics(source("Eights.x")) },
        check = { expect("popCrd" in diagnostics(source("Eights.x"))) { "No error: ${diagnostics(source("Eights.x"))}" } },
        settle = 3.seconds,
    ) {
        open(source("Eights.x"), 0)
        replace("return drawPile.popCard();", 0, "return drawPile.popCrd();")
        at("popCrd", delta = 2)
    }
    shot(
        "The error's message",
        "Ctrl+F1 shows the compiler's message where the cursor is; the scrollbar marks every problem in the file.",
        feature = "textDocument/diagnostic",
        focus = "popup",
        until = { "COMPILER-56" in popupText() },
        check = { popupText().let { expect("COMPILER-56" in it) { "The message: $it" } } },
        settle = 2.seconds,
        after = {
            dismiss()
            restore()
        },
    ) {
        action("ShowErrorDescription")
    }
    shot(
        "Warnings",
        "Warnings are marked too: card is always a Card, so this assertion can never fail, and the compiler says so.",
        feature = "textDocument/diagnostic",
        focus = "popup",
        until = { "always matches" in popupText() },
        check = { expect("always matches" in diagnostics(source("Player.x"))) { "No warning: ${diagnostics(source("Player.x"))}" } },
        settle = 2.seconds,
        after = { dismiss() },
    ) {
        open(source("Player.x"), 0)
        replace("                hand.addCard(card);", 0, "                assert card.is(Card);\n                hand.addCard(card);")
        within(HANG) { "always matches" in diagnostics(source("Player.x")) }
        at("card.is(Card)", delta = 7)
        // Ctrl+F1 shows the highlight that holds the caret, once the editor's analysis of this text has ended, in the
        //  focused editor: run 16 pressed it with the warning in the file's highlights, and its popup never showed
        within(HANG) { analyzed(source("Player.x")) && "always matches" in highlightsAtCaret() }
        focusedEditor()
        action("ShowErrorDescription")
    }
    shot(
        "The Problems window",
        "The Problems window lists the file's errors and warnings, and the project's, each a click away from its code.",
        feature = "textDocument/diagnostic",
        focus = "toolwindow:Problems View",
        until = { "always matches" in toolWindowText("Problems View") },
        check = { toolWindowText("Problems View").let { expect("always matches" in it) { "The problems: ${it.take(300)}" } } },
        settle = 3.seconds,
        after = {
            toolWindow("Problems View", false)
            restore()
        },
    ) {
        toolWindow("Problems View", true)
    }
    val callers = listOf(source("FirstMatch.x"), source("SaveEights.x"), test("PlayerTest.x"))
    verify("after the signature change, SaveEights.x, which is closed, and Player.x are marked red as files with errors") {
        callers.forEach(::close)
        open(source("Player.x"), 0)
        replace(
            "static Boolean cardMatches(Card card1, Card card2)",
            0,
            "static Boolean cardMatches(Card card1, Card card2, Boolean strict)",
        )
        // the Project view's red mark: the Problems window's Project Errors fill only from a build
        expect(within(HANG) { problemFile(source("SaveEights.x")) && problemFile(source("Player.x")) }) {
            "Marked: SaveEights.x ${problemFile(source("SaveEights.x"))}, Player.x ${problemFile(source("Player.x"))}"
        }
    }
    verify("a changed signature is an error in its callers, in other files and in a test module, until it is fixed") {
        if ("Boolean strict" !in fileText(source("Player.x"))) {
            open(source("Player.x"), 0)
            replace(
                "static Boolean cardMatches(Card card1, Card card2)",
                0,
                "static Boolean cardMatches(Card card1, Card card2, Boolean strict)",
            )
        }
        callers.forEach { caller ->
            open(caller, 0)
            // a test module shows that its module does not compile, not its own call's error: a question for design
            hasError(caller, if (caller == test("PlayerTest.x")) "" else "cardMatches")
        }
        open(source("Player.x"), 0)
        replace(", Boolean strict)", 0, ")")
        callers.forEach { caller ->
            open(caller, 0)
            compilesCleanly(caller)
        }
    }
    restore()
    shot(
        "Quick fixes",
        "Alt+Enter on an error offers its fixes: announce doesn't exist yet, so the server offers to create it.",
        feature = "textDocument/codeAction",
        focus = "popup",
        until = { "Create private method 'announce'" in popupText() },
        check = { popupText().let { expect("Create private method 'announce'" in it) { "The fixes: $it" } } },
        settle = 2.seconds,
    ) {
        open(source("Eights.x"), 0)
        at("Player current = one;", delta = "Player current = one;".length)
        insert("\n        announce(current);")
        hasError(source("Eights.x"), "announce")
        at("announce(current)", delta = 2)
        check(intentions("Create private method")) { "No Create private method fix: ${offered()}" }
    }
    shot(
        "Create a method",
        "The fix writes the method, with the parameter types that the call implies, for its body to be filled in.",
        feature = "codeAction/resolve",
        focus = "editor",
        until = { Regex("""announce\(Player""") in fileText(source("Eights.x")) },
        check = {
            expect(Regex("""announce\(Player""") in fileText(source("Eights.x"))) { "No announce method in Eights.x" }
            compilesCleanly(source("Eights.x"))
        },
        settle = 3.seconds,
    ) {
        check(choose("Create private method")) { "No Create private method fix: ${rows()}" }
    }
    verify("Undo takes the created method back in one step, without asking, as a change of one file") {
        // the file's editor as the step left it: opened at a line, the caret would leave the change, and the first Undo
        //  would only take it back there
        selectFile(source("Eights.x"))
        // the step before creates the method; without it there is nothing to undo, and the check would pass for it
        expect("announce(Player" in fileText(source("Eights.x"))) { "No created method to undo: Create a method made none" }
        undo(asks = false) { fileText(source("Eights.x")).let { "announce(current);" in it && "announce(Player" !in it } }
    }
    restore()
    verify("Create class makes a type that a use in the module names") {
        open(file(MODULE), 0)
        at("game.playGame();", delta = "game.playGame();".length)
        insert("\n        Joker joker = new Joker();")
        hasError(file(MODULE), "Joker")
        // Alt+Enter where the error is underlined, on the declared type: LSP4IJ asks for the quick fixes of the
        //  diagnostic under the caret, and new Joker has none
        at("Joker joker", delta = "Jo".length)
        apply("Create class 'Joker'")
        expect(within(HANG) { fileHas(file(MODULE), "class Joker") }) { "No class Joker in cardGame.x" }
        compilesCleanly(file(MODULE))
    }
    restore()
    shot(
        "Add an import",
        "A type that isn't imported has a fix that adds its import, here in a test module that imports the game's types.",
        feature = "textDocument/codeAction",
        focus = "popup",
        until = { "mport" in popupText() },
        check = { popupText().let { expect("mport" in it) { "The fixes: $it" } } },
        settle = 2.seconds,
        after = {
            dismiss()
            restore()
        },
    ) {
        open(test("DeckTest.x"), 0)
        replace("    import cardGame.Suit;\n", 0, "")
        hasError(test("DeckTest.x"), "Suit")
        at("Suit.values", delta = 1)
        check(intentions("mport")) { "No import fix: ${offered()}" }
    }
    verify("the fix for an unused import removes it") {
        open(test("PlayerTest.x"), 0)
        replace("    import cardGame.Card;\n", 0, "    import cardGame.Card;\n    import cardGame.Deck;\n")
        at("import cardGame.Deck", delta = "import cardGame.".length)
        apply("Remove unused import")
        expect(within(HANG) { "import cardGame.Deck;" !in fileText(test("PlayerTest.x")) }) { "The unused import is still there" }
        compilesCleanly(test("PlayerTest.x"))
    }
    restore()
    verify("the fix for an unused local removes it") {
        open(source("Hand.x"), 0)
        replace("        console.print();\n", 0, "        Int unused = 0;\n        console.print();\n")
        at("Int unused", delta = "Int un".length)
        apply("Remove unused local variable")
        expect(within(HANG) { "Int unused" !in fileText(source("Hand.x")) }) { "The unused local is still there" }
        compilesCleanly(source("Hand.x"))
    }
    restore()
    shot(
        "Generate a doc comment",
        "Alt+Enter on a declaration also offers to write its doc comment, with its parameters and its return value.",
        feature = "codeAction/resolve",
        focus = "editor",
        until = { Regex("""/\*\*[^/]*\*/\s*Player nextPlayer""") in fileText(source("Eights.x")) },
        check = {
            expect(Regex("""/\*\*[^/]*\*/\s*Player nextPlayer""") in fileText(source("Eights.x"))) { "No doc comment on nextPlayer" }
            compilesCleanly(source("Eights.x"))
        },
        settle = 3.seconds,
        after = { restore() },
    ) {
        open(source("Eights.x"), 0)
        at("Player nextPlayer(", delta = "Player next".length)
        apply("Generate documentation comment")
    }
    shot(
        "Optimize imports",
        "Ctrl+Alt+O removes the imports that nothing uses and sorts the rest; an unused import is grayed until then.",
        feature = "OptimizeImports",
        focus = "editor",
        until = { "import cardGame.Deck;" !in fileText(test("PlayerTest.x")) },
        check = {
            expect("import cardGame.Deck;" !in fileText(test("PlayerTest.x"))) { "The unused import is still there" }
            compilesCleanly(test("PlayerTest.x"))
        },
        settle = 3.seconds,
        after = { restore() },
    ) {
        open(test("PlayerTest.x"), 0)
        replace("    import cardGame.Card;\n", 0, "    import cardGame.Card;\n    import cardGame.Deck;\n")
        within(HANG) { analyzed(test("PlayerTest.x")) }
        action("OptimizeImports")
    }
    restore()
    verifyRestored()
}

private fun Tour.refactoring() {
    section("Refactoring")
    // Player.cardMatches, which both strategies and the player's tests call too: its rename changes four files, in two
    //  modules, as the VS Code tour's does
    val callers = listOf(source("Player.x"), source("FirstMatch.x"), source("SaveEights.x"), test("PlayerTest.x"))

    // whether every caller calls [name], and whether none names [name]
    fun TourProbe.named(name: String) = callers.all { "$name(" in fileText(it) }

    fun TourProbe.unnamed(name: String) = callers.none { name in fileText(it) }

    // the lines of [caller] that name either method, for a failure's message
    fun TourProbe.calls(caller: String) =
        "${caller.substringAfterLast('/')}: " +
            fileText(caller).lines().filter { "canFollow" in it || "cardMatches" in it }.map(String::trim)

    shot(
        "Rename",
        "Shift+F6 renames a method: the dialog checks the new name before anything changes.",
        feature = "textDocument/prepareRename",
        focus = "popup",
        until = { "canFollow" in popupText() },
        check = { popupText().let { expect("canFollow" in it) { "The dialog: $it" } } },
        settle = 2.seconds,
    ) {
        open(source("Player.x"), 0)
        at("cardMatches(card, prev)", delta = "card".length)
        action("RenameElement")
        expect(within(HANG) { dialogShowing() }) { "No Rename dialog" }
        fillDialog("canFollow")
    }
    shot(
        "Renamed everywhere",
        "The rename changes the declaration and every call, in the player, both strategies and the player's tests, as one " +
            "change of the project.",
        feature = "xtc/rename",
        focus = "editor",
        check = {
            callers.forEach { caller ->
                expect(fileText(caller).let { "canFollow(" in it && "cardMatches(" !in it }) { "Not renamed: ${calls(caller)}" }
            }
            // a call that the rename missed would name a method that is gone; an editor analyzes only the file it shows,
            //  and selected, not opened at a line, Player.x keeps its caret at the rename, where Undo must find it
            callers.forEach { caller ->
                selectFile(caller)
                compilesCleanly(caller)
            }
        },
        settle = 3.seconds,
        until = { named("canFollow") && unnamed("cardMatches(") },
    ) {
        check(confirm("Refactor")) { "The rename dialog did not take the new name" }
    }
    shot(
        "Undo asks first",
        "Ctrl+Z takes the rename back in one step, and first asks, as for any change of the project.",
        feature = "xtc/rename",
        focus = "popup",
        until = { dialogTitled("Undo") },
        check = { popupText().let { expect("Undo Rename?" in it) { "The question: $it" } } },
        settle = 2.seconds,
    ) {
        // the editor as the rename left it: the first Undo after the caret has left the change only takes it back there
        selectFile(source("Player.x"))
        undoKey { named("cardMatches") && unnamed("canFollow") }
        expect(dialogTitled("Undo")) { "Undo did not ask first" }
    }
    shot(
        "Undone everywhere",
        "OK takes the rename back in every file at once, and the project compiles as it did.",
        feature = "xtc/rename",
        focus = "editor",
        check = {
            callers.forEach { caller ->
                expect(fileText(caller).let { "cardMatches(" in it && "canFollow" !in it }) { "Not undone: ${calls(caller)}" }
            }
            callers.forEach { caller ->
                selectFile(caller)
                compilesCleanly(caller)
            }
        },
        settle = 3.seconds,
        until = { named("cardMatches") && unnamed("canFollow") },
    ) {
        check(confirm("OK")) { "Undo's question did not take its answer: ${popupText()}" }
    }
    restore()
    shot(
        "Rename a class",
        "Renaming a class renames its file too, as Ecstasy's one type per file expects, and every use of it.",
        feature = "xtc/rename",
        focus = "editor",
        check = {
            expect(fileHas(source("Tally.x"), "service Tally") && "Scoreboard" !in fileText(source("Eights.x"))) {
                "Scoreboard was not renamed to Tally everywhere"
            }
            compilesCleanly(source("Eights.x"))
        },
        settle = 3.seconds,
        after = { restore() },
    ) {
        open(source("Scoreboard.x"), 0)
        at("service Scoreboard", delta = "service Score".length)
        action("RenameElement")
        expect(within(HANG) { dialogShowing() }) { "No Rename dialog" }
        fillDialog("Tally")
        check(confirm("Refactor")) { "The rename dialog did not take the new name" }
        check(within(HANG) { fileHas(source("Eights.x"), "public Tally scoreboard") }) { "Eights.x does not use Tally" }
        open(source("Eights.x"), 0)
        at("public Tally scoreboard")
    }
    verify("renaming a local and a parameter renames their uses") {
        open(source("Player.x"), 0)
        at("card := eights.drawCard()", delta = 1)
        rename("drawn")
        expect(
            within(HANG) {
                fileHas(source("Player.x"), "Card drawn := eights.drawCard()") &&
                    fileHas(source("Player.x"), "hand.addCard(drawn);")
            },
        ) {
            "The local: ${fileText(source("Player.x")).lines().filter { "drawCard" in it || "addCard" in it }}"
        }
        at("drawForMatch(Eights eights, Card? prev)", delta = "drawForMatch(Eights eights, Card? p".length)
        rename("previous")
        expect(
            within(HANG) {
                fileHas(source("Player.x"), "Card? previous) {") &&
                    fileHas(source("Player.x"), "cardMatches(drawn, previous)")
            },
        ) {
            "The parameter: ${fileText(source("Player.x")).lines().filter { "previous" in it || "prev" in it }}"
        }
        compilesCleanly(source("Player.x"))
    }
    restore()
    verify("renaming a file renames its type and every use") {
        close(source("FirstMatch.x"))
        projectAction(source("FirstMatch.x"), "RenameElement")
        expect(within(HANG) { dialogShowing() }) { "No Rename dialog for FirstMatch.x" }
        fillDialog("FirstMatching.x")
        expect(confirm("Refactor")) { "The Rename dialog did not take FirstMatching.x" }
        expect(within(HANG) { exists(source("FirstMatching.x")) && fileHas(source("Player.x"), "new FirstMatching()") }) {
            "Player.x: ${fileText(source("Player.x")).lines().filter { "FirstMatch" in it }}"
        }
        expect(
            fileHas(source("FirstMatching.x"), "const FirstMatching"),
        ) { "FirstMatching.x: ${fileText(source("FirstMatching.x")).take(300)}" }
        open(source("Player.x"), 0)
        compilesCleanly(source("Player.x"))
    }
    restore()
    verify("moving a class to another package moves its file and keeps its uses compiling") {
        newDirectory(file("src/main/x/cardGame"), "strategies")
        move(source("SaveEights.x"), file("src/main/x/cardGame/strategies"))
        expect(within(HANG) { dialogShowing() }) { "No Move dialog" }
        expect(confirm("Refactor")) { "The Move dialog did not move SaveEights.x" }
        expect(
            within(HANG) { exists(source("strategies/SaveEights.x")) && !exists(source("SaveEights.x")) },
        ) { "SaveEights.x was not moved" }
        expect(within(HANG) { fileHas(source("Eights.x"), "strategies.SaveEights") }) {
            "Eights.x: ${fileText(source("Eights.x")).lines().filter { "SaveEights" in it }}"
        }
        open(source("Eights.x"), 0)
        compilesCleanly(source("Eights.x"))
        open(source("strategies/SaveEights.x"), 0)
        compilesCleanly(source("strategies/SaveEights.x"))
    }
    restore()
    shot(
        "Extract and inline",
        "Alt+Enter on a whole returned expression, or a typed local's initializer, offers to extract it to a local or a private method.",
        feature = "textDocument/codeAction",
        focus = "popup",
        // the list's rows, as the newest popup can be the description of the row it selects
        until = { "Extract expression to local" in rows() },
        check = { rows().let { expect("Extract expression to local" in it) { "The refactorings: $it" } } },
        settle = 2.seconds,
    ) {
        open(source("Eights.x"), 0)
        // the server extracts a whole returned expression, or a typed local's initializer
        select("one.hand.isEmpty() || two.hand.isEmpty()")
        check(intentions("Extract expression to local")) { "No Extract expression to local: ${offered()}" }
    }
    shot(
        "Extracted to a local",
        "The expression is now a local of the type that the method returns, and the return reads it.",
        feature = "codeAction/resolve",
        focus = "editor",
        until = { EXTRACTED in fileText(source("Eights.x")) },
        check = {
            expect(EXTRACTED in fileText(source("Eights.x"))) {
                "isDone: ${fileText(source("Eights.x")).lines().filter { "isEmpty() ||" in it || "return" in it }}"
            }
            compilesCleanly(source("Eights.x"))
        },
        settle = 3.seconds,
        after = { restore() },
    ) {
        // the list may have been asked again meanwhile (UP07), so it is opened again until it has the row
        if (!choose("Extract expression to local")) {
            select("one.hand.isEmpty() || two.hand.isEmpty()")
            apply("Extract expression to local")
        }
    }
    shot(
        "Extract a method",
        "Statements extract to a private method too: the three lines that print the hand sizes become one call.",
        feature = "codeAction/resolve",
        focus = "editor",
        until = { Regex("""private void \w+\(""") in fileText(source("Eights.x")) },
        check = {
            expect(Regex("""private void \w+\(""") in fileText(source("Eights.x"))) { "No extracted method in Eights.x" }
            compilesCleanly(source("Eights.x"))
        },
        settle = 3.seconds,
        after = { restore() },
    ) {
        open(source("Eights.x"), 0)
        // whole lines, as an editor selects them, from the first line's start to the start of the line after the last
        select(
            "        console.print(${'$'}\"{one.name} hand size: {one.hand.size()}\");\n" +
                "        console.print(${'$'}\"{two.name} hand size: {two.hand.size()}\");\n" +
                "        console.print(${'$'}\"Draw pile size: {drawPile.size()}\");\n",
        )
        // the list shows the server's actions that have come when it opens, so it opens until this one has
        apply("Extract statements to private method")
    }
    verify("Inline puts a returned local's value in the return") {
        open(source("Eights.x"), 0)
        replace(
            "        return one.hand.isEmpty() || two.hand.isEmpty();",
            0,
            "        Boolean done = one.hand.isEmpty() || two.hand.isEmpty();\n        return done;",
        )
        at("Boolean done", delta = "Boolean do".length)
        apply("Inline returned local variable")
        expect(
            within(HANG) {
                fileText(source("Eights.x")).let { "return one.hand.isEmpty() || two.hand.isEmpty();" in it && "Boolean done" !in it }
            },
        ) { "isDone: ${fileText(source("Eights.x")).lines().filter { "done" in it || "isEmpty() ||" in it }}" }
        compilesCleanly(source("Eights.x"))
    }
    restore()
    verify("Inline puts the expression that a private method of the file returns where it is called") {
        open(source("Eights.x"), 0)
        // the server inlines a private method of the same file that returns an expression, and keeps the method
        at("val winner = victor();", delta = "val winner = vic".length)
        apply("Inline private method call")
        expect(within(HANG) { fileHas(source("Eights.x"), "val winner = (one.hand.isEmpty() ? one : two);") }) {
            "playGame: ${fileText(source("Eights.x")).lines().filter { "val winner" in it }}"
        }
        compilesCleanly(source("Eights.x"))
    }
    restore()
    verify("Inline puts a private static constant's value where its file uses it") {
        open(source("Eights.x"), 0)
        // a private static constant of the same file, whose value the compiler knows
        at("console.print(PROMPT);", delta = "console.print(PRO".length)
        apply("Inline constant property")
        expect(within(HANG) { fileHas(source("Eights.x"), "console.print((\"Press Enter for next turn...\"));") }) {
            "displayState: ${fileText(source("Eights.x")).lines().filter { "Press Enter" in it || "PROMPT)" in it }}"
        }
        compilesCleanly(source("Eights.x"))
    }
    restore()
    shot(
        "Safe delete",
        "A private member that nothing uses, Hand.heading, can be deleted safely: the server checks for uses first.",
        feature = "textDocument/codeAction",
        focus = "popup",
        until = { "Safely delete" in rows() },
        check = { rows().let { expect("Safely delete private member 'heading'" in it) { "The actions: $it" } } },
        settle = 2.seconds,
    ) {
        open(source("Hand.x"), 0)
        // a private member, whose uses are its own file's
        at("private String heading()", delta = "private String head".length)
        check(intentions("Safely delete")) { "No Safely delete: ${offered()}" }
    }
    verify("Safe delete deletes the unused member") {
        if (!choose("Safely delete")) apply("Safely delete")
        expect(within(HANG) { "String heading()" !in fileText(source("Hand.x")) }) { "heading is still in Hand.x" }
        compilesCleanly(source("Hand.x"))
    }
    restore()
    verifyRestored()
}

private fun Tour.files() {
    section("Project and files")
    shot(
        "New Project",
        "File | New | Project | Ecstasy makes a new project as xtc init does: an application or a library, with the XTC Gradle plugin.",
        feature = "newProjectWizard",
        focus = "popup",
        until = { "Project type" in popupText() },
        check = { popupText().let { expect("Project type" in it) { "The wizard: ${it.take(400)}" } } },
        settle = 3.seconds,
        after = { closeDialogs() },
    ) {
        action("NewProject")
        expect(within(HANG) { dialogShowing() }) { "No New Project dialog" }
        expect(within(HANG) { selectItem("Ecstasy") }) { "No Ecstasy generator in New Project: ${popupText().take(400)}" }
    }
    prepare { within(HANG) { !dialogShowing() } }
    verify("a new file's type is one the project's code can use at once") {
        newFile(file("src/main/x/cardGame"), "Joker.x", "/**\n * A wild card, which matches any other.\n */\nconst Joker(String name);\n")
        open(source("Eights.x"), 0)
        at("Deck deck = new Deck(GameConstants.DECK_NAME);", delta = "Deck deck = new Deck(GameConstants.DECK_NAME);".length)
        insert("\n        Joker joker = new Joker(\"wild\");")
        compilesCleanly(source("Eights.x"))
    }
    verify("renaming the file renames its type, and its use still compiles") {
        projectAction(source("Joker.x"), "RenameElement")
        expect(within(HANG) { dialogShowing() }) { "No Rename dialog for Joker.x" }
        fillDialog("Wild.x")
        expect(confirm("Refactor")) { "The Rename dialog did not take Wild.x" }
        expect(within(HANG) { exists(source("Wild.x")) && fileHas(source("Eights.x"), "Wild joker = new Wild(") }) {
            "Eights.x: ${fileText(source("Eights.x")).lines().filter { "joker" in it }}"
        }
        open(source("Eights.x"), 0)
        compilesCleanly(source("Eights.x"))
    }
    verify("deleting the file makes its use an error") {
        projectAction(source("Wild.x"), "\$Delete")
        expect(within(HANG) { dialogShowing() }) { "No Delete dialog for Wild.x" }
        expect(confirm("OK", "Delete")) { "The Delete dialog did not delete Wild.x" }
        expect(within(HANG) { !exists(source("Wild.x")) }) { "Wild.x is still there" }
        open(source("Eights.x"), 0)
        hasError(source("Eights.x"), "Wild")
    }
    restore()
    verify("a test module sees an edit of the module it tests before it is saved") {
        open(source("Hand.x"), 0)
        replace("    void display() {", 0, "    Int count() = size();\n\n    void display() {")
        open(test("HandTest.x"), 0)
        replace("assert hand.size() == 0 as", 0, "assert hand.count() == 0 as")
        compilesCleanly(test("HandTest.x"))
        open(source("Hand.x"), 0)
        replace("    Int count() = size();\n\n", 0, "")
        open(test("HandTest.x"), 0)
        hasError(test("HandTest.x"), "count")
    }
    restore()
    verifyRestored()
}

private fun Tour.building() {
    section("Build and run")
    card(
        "Build and run",
        "Ecstasy projects build and run three ways, chosen per project, and per run configuration.",
        TourCard(
            eyebrow = "BUILD AND RUN",
            heading = "Embedded, Gradle, or the command line",
            lead = "The same run configuration, and the same Run, Debug and Test buttons, for each",
            boxes =
                listOf(
                    TourBox("Embedded", "The language server's compiler and execution worker: no Gradle, and the fastest round trip."),
                    TourBox("Gradle", "The XTC Gradle plugin's compileXtc, runXtc and testXtc, with the build's own settings and XDK."),
                    TourBox("Command line", "The XDK's xtc run, as a terminal runs it, with the plugin's XDK."),
                ),
        ),
    )
    shot(
        "The build mode",
        "Settings | Build, Execution, Deployment | Ecstasy: the embedded compiler is the default, also beside a Gradle build.",
        feature = "Settings | Build | Ecstasy",
        focus = "popup",
        until = { "Embedded compiler" in popupText() },
        check = { popupText().let { expect("Embedded compiler" in it) { "The page: ${it.take(300)}" } } },
        settle = 3.seconds,
        after = { closeDialogs() },
    ) {
        settings("xtc.build")
    }
    shot(
        "Build Project",
        "Ctrl+F9 builds every module with the language server's compiler, without Gradle, and reports to the Build window.",
        feature = "xtc/build",
        focus = "toolwindow:Build",
        check = {
            toolWindowText(
                "Build",
            ).let {
                expect(
                    "with the embedded compiler" in it && "compileXtc" !in it && "extractXdk" !in it && "Executing" !in it,
                ) { "Embedded mode ran Gradle: ${it.take(400)}; the modules: ${buildModules()}" }
            }
        },
        settle = 3.seconds,
        until = { busy().isEmpty() && "with the embedded compiler" in toolWindowText("Build") },
    ) {
        action("CompileDirty")
        // a build that succeeds leaves the Build window closed
        toolWindow("Build", true)
    }
    shot(
        "A failed build",
        "A build's errors are listed with their files and lines, each a click away from its code.",
        feature = "xtc/build",
        focus = "toolwindow:Build",
        until = { busy().isEmpty() && "COMPILER-56" in toolWindowText("Build") },
        check = { toolWindowText("Build").let { expect("COMPILER-56" in it && "compileXtc" !in it) { "The build: ${it.take(400)}" } } },
        settle = 3.seconds,
        after = { restore() },
    ) {
        open(source("Eights.x"), 0)
        replace("return drawPile.popCard();", 0, "return drawPile.popCrd();")
        action("CompileDirty")
    }
    verify("after the failed build, the Problems window's Project Errors list its error in Eights.x") {
        if ("popCrd" !in fileText(source("Eights.x"))) {
            open(source("Eights.x"), 0)
            replace("return drawPile.popCard();", 0, "return drawPile.popCrd();")
            // the earlier build's report goes first, so that only this build's own can satisfy the wait
            clearToolWindow("Build")
            action("CompileDirty")
            expect(within(HANG) { busy().isEmpty() && "COMPILER-56" in toolWindowText("Build") }) {
                "The build did not report its error: ${toolWindowText("Build").take(400)}"
            }
        }
        toolWindowTab("Problems View", "Project Errors")
        expect(within(HANG) { toolWindowText("Problems View").let { "Eights.x" in it && "popCrd" in it } }) {
            // what Project Errors' collector holds, and the builds since the failed one, whose start clears its problems
            "The project's errors: ${toolWindowText("Problems View").take(500)}; the collector: ${projectProblems()}; " +
                "the Build window: ${toolWindowText("Build").take(500)}"
        }
        toolWindow("Problems View", false)
    }
    restore()
    var run = 0
    var started = ""
    shot(
        "The Run icon",
        "The Run icon in the gutter of a module with a run() method runs it; it makes the module's run configuration.",
        feature = "xtc/runLenses",
        focus = "gutter",
        // the Run lens above the module's line too, which moves the line down as it comes: run 20's click on the icon,
        //  aimed before the lens came, hit the lens's row instead
        until = { "Run cardGame@$run" in gutterIcons() && lenses().lines().any { "Run cardGame" in it } },
        check = { expect("Run cardGame@$run" in gutterIcons()) { "The gutter's icons: ${gutterIcons()}" } },
    ) {
        open(file(MODULE), 0)
        run = at("module cardGame")
    }
    shot(
        "Run",
        "The game runs in the embedded execution worker, beside the language server, and its Console is the Run window.",
        feature = "xtc/run · xtc/runOutput",
        focus = "toolwindow:Run",
        check = { expect("Starting Eights game." in console()) { "The console: ${console().take(300)}" } },
        settle = 2.seconds,
        until = { runId() != started && "Press Enter" in console() },
    ) {
        started = runId()
        gutter(run, "Run cardGame")
    }
    shot(
        "Console input",
        "The program reads the Run window's input: each Enter plays a turn, and the Run window shows it.",
        feature = "xtc/runInput",
        focus = "toolwindow:Run",
        check = {
            console().let {
                expect(
                    " plays " in it || " draws " in it || " passes" in it,
                ) { "No turn played: ${it.takeLast(300)}" }
            }
        },
        settle = 2.seconds,
        until = { console().split("Press Enter").size > 3 },
    ) {
        typeInConsole("\n", false)
        within(HANG) { console().split("Press Enter").size > 2 }
        typeInConsole("\n", false)
    }
    verify("the game plays to its end, and the run ends with its exit code") {
        // enough Enters for every turn of a game, which ends when a hand is empty
        input("\n".repeat(600))
        expect(within(HANG) { "wins!" in console() && runEnded() }) { "The game did not end: ${console().takeLast(300)}" }
        expect(exitCode() == "0" && "exit code 0" in console()) { "Exit code ${exitCode()}: ${console().takeLast(200)}" }
    }
    shot(
        "The run configuration",
        "Run | Edit Configurations: the module, its arguments, and what it runs with: the project's mode, embedded, Gradle or xtc.",
        feature = "Run | Edit Configurations",
        focus = "popup",
        until = { "Module arguments" in popupText() },
        check = { popupText().let { expect("cardGame" in it && "Carol,Dave" in it) { "The dialog: ${it.take(400)}" } } },
        settle = 3.seconds,
    ) {
        action("editRunConfigurations")
        expect(within(HANG) { "Module arguments" in popupText() }) { "No run configuration's editor: ${popupText().take(300)}" }
        form("Module arguments:", "Carol,Dave")
    }
    verify("the arguments that the configuration gives are the program's") {
        expect(confirm("OK")) { "Edit Configurations did not close" }
        started = runId()
        toolbar("Run")
        expect(within(HANG) { runId() != started && "Carol hand size" in console() }) { "The console: ${console().takeLast(300)}" }
    }
    verify("Rerun runs it again, and Stop ends it") {
        val first = runId()
        windowAction("Run", "Rerun")
        expect(
            within(HANG) { runId() != first && "Carol hand size" in console() },
        ) { "No new run: ${runId()}, ${console().takeLast(200)}" }
        windowAction("Run", "Stop")
        expect(within(HANG) { runEnded() }) { "The run did not stop" }
        configure("moduleArguments", "")
    }
    verify("the Run lens runs the module") {
        open(file(MODULE), 0)
        // the lens's text starts with its run icon, ▶
        expect(within(HANG) { lenses().lines().any { "Run cardGame" in it } }) { "The lenses: ${lenses()}" }
        started = runId()
        clickLens("Run cardGame", -1)
        expect(within(HANG) { runId() != started && "Press Enter" in console() }) { "The console: ${console().takeLast(300)}" }
        stopAll()
    }
    shot(
        "Run with Gradle",
        "In Gradle mode, the same configuration runs the XTC Gradle plugin's runXtc task, with the build's settings and XDK.",
        feature = "runXtc",
        focus = "toolwindow:Run",
        check = { expect("Press Enter" in console()) { "The console: ${console().takeLast(300)}" } },
        settle = 2.seconds,
        until = { runId() != started && "Press Enter" in console() },
        after = { stopAll() },
    ) {
        buildMode("GRADLE")
        open(file(MODULE), 0)
        started = runId()
        gutter(run, "Run cardGame")
    }
    shot(
        "The Gradle window",
        "The Gradle window lists the XTC plugin's tasks, compileXtc, runXtc and testXtc among them, to run as any Gradle task.",
        feature = "Gradle tool window",
        focus = "toolwindow:Gradle",
        until = { "runXtc" in toolWindowText("Gradle") },
        check = { toolWindowText("Gradle").let { expect("runXtc" in it) { "The Gradle window: ${it.take(300)}" } } },
        settle = 3.seconds,
        after = { toolWindow("Gradle", false) },
    ) {
        toolWindow("Gradle", true)
        within(HANG) { "Tasks" in toolWindowText("Gradle") }
        expand("Gradle", "Tasks")
        expand("Gradle", "application")
    }
    shot(
        "Run with xtc",
        "Set to the command line, the configuration runs the XDK's xtc run, as a terminal would, in the same Run window.",
        feature = "xtc run",
        focus = "toolwindow:Run",
        check = { expect("Press Enter" in console() && "xtc" in console()) { "The console: ${console().takeLast(300)}" } },
        settle = 2.seconds,
        until = { runId() != started && "Press Enter" in console() },
        after = {
            stopAll()
            runWith("PROJECT")
            buildMode("EMBEDDED")
        },
    ) {
        runWith("COMMAND_LINE")
        started = runId()
        toolbar("Run")
    }
    restore()
    verifyRestored()
}

private fun Tour.debugging() {
    section("Debugging")
    card(
        "Debugging",
        "Debug runs a configuration under the debugger, in each of its modes, and the IDE's own debugger shows it.",
        TourCard(
            eyebrow = "DEBUGGING",
            heading = "The IDE's debugger, for Ecstasy",
            lead = "Through LSP4IJ's debug adapter client, to a debug session in the language server",
            boxes =
                listOf(
                    TourBox("Stop", "Line, conditional and exception breakpoints; stop at the program's first line; attach."),
                    TourBox("Step", "Over, into, out, smart step into, and run to the cursor; the XDK's frames and sources."),
                    TourBox("Inspect", "Frames and fibers, variables, Set Value, watches, hovers, Evaluate Expression and the console."),
                ),
        ),
    )
    prepare {
        // the section stands on its own, recorded alone or after any other: nothing left open from before, and the
        //  game's run configuration selected, as Build and run's Run icon makes it, for Debug to debug
        dismiss()
        closeDialogs()
        expect(within(HANG) { !dialogShowing() }) { "A dialog stays open" }
        runConfiguration("cardGame", "cardGame", file(MODULE))
    }
    var deck = 0
    shot(
        "A conditional breakpoint",
        "A breakpoint can have a condition: this one, in Deck's constructor, stops only when the deck adds the Queen of Spades.",
        feature = "setBreakpoints (condition)",
        focus = "popup",
        until = { "Rank.Queen" in popupText() },
        // the breakpoint's condition, as the balloon keeps it: a tooltip can be the newest popup by now
        check = { breakpointConditions().let { expect("Rank.Queen" in it) { "The breakpoints: $it; the popup: ${popupText()}" } } },
        settle = 2.seconds,
        after = { dismiss() },
    ) {
        open(source("Deck.x"), 0)
        deck = at("addCard(new Card(suit, rank));")
        breakpoint(deck, "rank == Rank.Queen && suit == Suit.Spades")
        editBreakpoint(deck)
    }
    prepare {
        // the breakpoint's editor is a balloon, which Escape closes
        key("ESCAPE")
        dismiss()
    }
    shot(
        "Debug",
        "Shift+F9, or the toolbar's Debug button, debugs the run configuration: the program stops where the condition first holds.",
        feature = "launch · configurationDone",
        needs = setOf(Need.RUN_CONFIGURATION),
        focus = "toolwindow:Debug",
        check = {
            values().let { expect("Queen" in it["rank"].orEmpty() && "Spades" in it["suit"].orEmpty()) { "The variables: $it" } }
        },
        settle = 3.seconds,
        until = { pausedAt(deck + 1) },
    ) {
        open(file(MODULE), 0)
        toolbar("Debug")
    }
    var deal = 0
    shot(
        "Frames and variables",
        "Resumed, it stops at a line breakpoint while dealing: the frames show deal called from initializeGame, in Eights' constructor.",
        feature = "stackTrace · scopes · variables",
        needs = setOf(Need.DEBUG_SESSION),
        focus = "toolwindow:Debug",
        check = { frameNames().let { expect(it.any { "initializeGame" in it } && it.first().contains("deal")) { "The frames: $it" } } },
        settle = 3.seconds,
        until = { pausedAt(deal + 1) },
    ) {
        clearBreakpoints()
        open(source("CardCollection.x"), 0)
        deal = at("if (Card card := popCard())")
        breakpoint(deal, "")
        action("Resume")
    }
    var from = ""
    shot(
        "Step into",
        "F7 steps into the call on the line: popCard, which takes the top card off the deck.",
        feature = "stepIn",
        needs = setOf(Need.DEBUG_SESSION),
        focus = "toolwindow:Debug",
        check = { frameNames().let { expect(it.first().contains("popCard")) { "The frames: $it" } } },
        settle = 2.seconds,
        until = { pausedAt() && !pausedAt(deal + 1) },
    ) {
        clearBreakpoints()
        action("StepInto")
    }
    shot(
        "Step over",
        "F8 runs the current line and stops at the next one; the Variables view shows what the line changed.",
        feature = "next",
        needs = setOf(Need.DEBUG_SESSION),
        focus = "toolwindow:Debug",
        settle = 2.seconds,
        until = { position().let { it.isNotEmpty() && it != from } },
        check = { frameNames().let { expect(it.first().contains("popCard")) { "Step over left popCard: $it" } } },
    ) {
        from = position()
        action("StepOver")
    }
    shot(
        "Step out",
        "Shift+F8 runs to the end of popCard and stops back in deal, with the card it returned.",
        feature = "stepOut",
        needs = setOf(Need.DEBUG_SESSION),
        focus = "toolwindow:Debug",
        settle = 2.seconds,
        until = { position().let { it.isNotEmpty() && it != from && "CardCollection.x" in it } && frameNames().first().contains("deal") },
        check = { frameNames().let { expect(it.first().contains("deal")) { "The frames: $it" } } },
    ) {
        from = position()
        action("StepOut")
    }
    var dealAll = 0
    shot(
        "Run to cursor",
        "Alt+F9 runs to the line with the cursor, here dealAll's, as a breakpoint there would, without one.",
        feature = "setBreakpoints · continue",
        needs = setOf(Need.DEBUG_SESSION),
        focus = "toolwindow:Debug",
        settle = 2.seconds,
        until = { pausedAt(dealAll + 1) },
        check = { frameNames().let { expect(it.first().contains("dealAll")) { "The frames: $it" } } },
    ) {
        open(source("CardCollection.x"), 0)
        dealAll = at("deal(that, size());")
        action("RunToCursor")
    }
    shot(
        "Smart step into",
        "Shift+F7 on a line with several calls asks which one to step into: deal, or size.",
        feature = "stepInTargets",
        needs = setOf(Need.DEBUG_SESSION),
        focus = "line",
        until = { smartSteps().containsAll(listOf("deal", "size")) },
        check = { smartSteps().let { expect(it.containsAll(listOf("deal", "size"))) { "The targets: $it; ${smartTargets()}" } } },
        settle = 2.seconds,
        after = {
            key("ESCAPE")
            dismiss()
        },
    ) {
        action("SmartStepInto")
    }
    shot(
        "Evaluate expression",
        "Alt+F8 evaluates an expression in the paused frame: the selection, size(), is the number of cards left in the deck.",
        feature = "evaluate (repl)",
        needs = setOf(Need.DEBUG_SESSION),
        focus = "popup",
        until = { "42" in popupText() },
        check = { popupText().let { expect("42" in it) { "The result: ${it.take(300)}" } } },
        settle = 3.seconds,
        after = { closeDialogs() },
    ) {
        select("size()", occurrence = 1)
        action("EvaluateExpression")
        expect(within(HANG) { dialogShowing() && buttonEnabled("Evaluate") }) { "No Evaluate dialog" }
        click("Evaluate")
    }
    prepare { within(HANG) { !dialogShowing() } }
    shot(
        "Watches",
        "An expression added to the watches is evaluated again at every stop, beside the frame's variables: here the deck's label.",
        feature = "evaluate (watch)",
        needs = setOf(Need.DEBUG_SESSION),
        focus = "toolwindow:Debug",
        until = { "\"Deck\"" in watches() },
        check = { watches().let { expect("label" in it && "\"Deck\"" in it) { "The watches: $it" } } },
        settle = 3.seconds,
    ) {
        // a watch evaluates only what runs no code: variables, their fields and elements, literals and operators
        addWatch("label")
    }
    verify("a watch of a call says that it is not evaluated, as it would run the program's code", setOf(Need.DEBUG_SESSION)) {
        addWatch("size()", occurrence = 1)
        expect(within(HANG) { "is not evaluated" in watches() }) { "The watches: ${watches()}" }
    }
    verify("hovering a name in the paused frame shows its value", setOf(Need.DEBUG_SESSION)) {
        // the server's answer to the editor's evaluation, which LSP4IJ asks with the context hover
        evaluate("that", "EDITOR").let { expect("\"error\"" !in it && "Hand" in it) { "The hover's evaluation: $it" } }
        open(source("CardCollection.x"), 0)
        at("deal(that, size());", delta = "deal(th".length)
        // the value hint shows in the focused editor, as when the mouse rests on the name
        editorAction("QuickEvaluateExpression")
        expect(within(HANG) { popupText().let { "that" in it && "Hand" in it } }) {
            "The hover: ${popupText().take(300)}; ${quickEvaluateState()}"
        }
        dismiss()
    }
    verify("the threads are the program's fibers, the game's while it deals", setOf(Need.DEBUG_SESSION)) {
        threads().let { expect("cardGame" in it && "dealAll" in it) { "The threads: ${it.take(600)}" } }
    }
    verify("a line typed in the debug console while paused is evaluated in the frame", setOf(Need.DEBUG_SESSION)) {
        debugTab("Console")
        typeInConsole("label\n", true)
        expect(within(HANG) { "\"Deck\"" in debugConsole() }) { "The console: ${debugConsole().takeLast(300)}; ${debugInput()}" }
    }
    verify("a call typed in the debug console while paused is answered, or says why it is not evaluated", setOf(Need.DEBUG_SESSION)) {
        typeInConsole("size()\n", true)
        expect(within(HANG) { debugConsole().let { "42" in it || "is not evaluated" in it } }) {
            "The console: ${debugConsole().takeLast(300)}; ${debugInput()}"
        }
    }
    shot(
        "The program's console",
        "Resumed, the game waits for Enter: the Debug window's Console shows its output, and a line typed there is its input.",
        feature = "output · evaluate (repl)",
        needs = setOf(Need.DEBUG_SESSION),
        focus = "toolwindow:Debug",
        check = { expect("Press Enter" in debugConsole()) { "The console: ${debugConsole().takeLast(300)}" } },
        settle = 3.seconds,
        until = { !pausedAt() && "Press Enter" in debugConsole() },
    ) {
        action("Resume")
        within(HANG) { !pausedAt() }
        debugTab("Console")
    }
    verify("a line typed in the debug console while the program runs is its input", setOf(Need.DEBUG_SESSION)) {
        val prompts = debugConsole().split("Press Enter").size
        typeInConsole("\n", true)
        expect(
            within(HANG) { debugConsole().split("Press Enter").size > prompts },
        ) { "No turn played: ${debugConsole().takeLast(300)}" }
    }
    prepare { stopAll() }
    var thrown = 0
    shot(
        "An exception",
        "An exception that nothing catches stops the program where it is thrown: here popCard reads one card past the end.",
        feature = "setExceptionBreakpoints · exceptionInfo",
        needs = setOf(Need.RUN_CONFIGURATION),
        focus = "toolwindow:Debug",
        check = {
            expect(frameNames().any { "popCard" in it }) { "The frames: ${frameNames()}" }
            val shown = hints() + variables("")
            expect("Bounds" in shown || "bounds" in shown) { "The exception: ${shown.take(400)}" }
        },
        settle = 3.seconds,
        until = { pausedAt() && frameNames().any { "popCard" in it } },
        after = {
            stopAll()
            restore()
        },
    ) {
        within(HANG) { !debug()["session"].asBoolean }
        open(source("CardCollection.x"), 0)
        replace("Card card = cards[cards.size - 1];", 0, "Card card = cards[cards.size];")
        thrown = at("Card card = cards[cards.size];")
        open(file(MODULE), 0)
        action("Debug")
    }
    verify("Force Step Into goes into the XDK's code, whose source opens read-only", setOf(Need.RUN_CONFIGURATION)) {
        within(HANG) { !debug()["session"].asBoolean }
        open(source("CardCollection.x"), 0)
        val add = at("cards.add(card);")
        breakpoint(add, "")
        open(file(MODULE), 0)
        action("Debug")
        expect(within(HANG) { pausedIn("CardCollection.x", add + 1) }) { "Not paused in addCard: ${position()}" }
        clearBreakpoints()
        action("ForceStepInto")
        expect(within(HANG) { pausedAt() && !pausedIn("CardCollection.x") }) { "Force Step Into stayed at ${position()}" }
        expect(within(HANG) { editor().let { it["file"].asString == debug()["file"]?.asString && !it["writable"].asBoolean } }) {
            "The XDK's source: ${editor()}, paused at ${position()}"
        }
        stopAll()
    }
    verify("Stop at the program's first line stops before it prints, and Set Value changes what it prints", setOf(Need.RUN_CONFIGURATION)) {
        within(HANG) { !debug()["session"].asBoolean }
        configure("stopOnEntry", "true")
        try {
            open(file(MODULE), 0)
            action("Debug")
            expect(within(HANG) { pausedIn("cardGame.x") }) { "Not stopped at its first line: ${position()}" }
            expect("Starting Eights game." !in debugConsole()) { "It printed before its first line: ${debugConsole()}" }
        } finally {
            configure("stopOnEntry", "false")
        }
        open(source("Player.x"), 0)
        val named = at("this.name = name;")
        breakpoint(named, "")
        action("Resume")
        expect(within(HANG) { pausedIn("Player.x", named + 1) }) { "Not paused in Player's constructor: ${position()}" }
        expect("Alice" in values()["name"].orEmpty()) { "The variables: ${values()}" }
        setValue("name", "\"Carol\"").let { expect(it.isEmpty()) { "Set Value: $it" } }
        clearBreakpoints()
        action("Resume")
        expect(within(HANG) { "Carol hand size" in debugConsole() }) { "The console: ${debugConsole().takeLast(300)}" }
        stopAll()
    }
    verify("with Let the program run, Evaluate Expression calls the Scoreboard service", setOf(Need.RUN_CONFIGURATION)) {
        within(HANG) { !debug()["session"].asBoolean }
        configure("evaluationRunsProgram", "true")
        try {
            open(source("Eights.x"), 0)
            val shown = at("console.print(GameConstants.DISPLAY_SEPARATOR);")
            breakpoint(shown, "")
            open(file(MODULE), 0)
            action("Debug")
            expect(within(HANG) { pausedIn("Eights.x", shown + 1) }) { "Not paused in displayState: ${position()}" }
            val answer = JsonParser.parseString(evaluate("scoreboard.winsOf(\"Alice\")", "DIALOG")).asJsonObject
            expect(answer["value"]?.asString == "0") { "scoreboard.winsOf: $answer" }
        } finally {
            configure("evaluationRunsProgram", "false")
            clearBreakpoints()
            stopAll()
        }
    }
    verify("an Ecstasy Attach configuration debugs the game that xtc runs outside the IDE") {
        within(HANG) { !debug()["session"].asBoolean }
        val xtc = PlaybookIde.XDK_BIN.resolve("xtc").toString()
        program(xtc, "run", "--debug", "listen:127.0.0.1:0,suspend", "--deduce", "cardGame").use { game ->
            val listening = Regex("Listening for the debugger at [^ ]+:(\\d+)")
            expect(within(HANG) { listening.containsMatchIn(game.output) }) { "The program: ${game.output.takeLast(400)}" }
            val port = listening.find(game.output)!!.groupValues[1].toInt()
            open(source("Deck.x"), 0)
            val added = at("addCard(new Card(suit, rank));")
            breakpoint(added, "")
            val was = attachConfiguration("Attach to cardGame", port)
            try {
                toolbar("Debug")
                expect(
                    within(HANG) { pausedIn("Deck.x", added + 1) },
                ) { "Not paused in Deck: ${position()}; ${game.output.takeLast(300)}" }
                expect("Starting Eights game." in game.output) { "The program: ${game.output.takeLast(300)}" }
            } finally {
                clearBreakpoints()
                stopAll()
                selectConfiguration(was)
            }
        }
    }
    verify("the threads are the program's fibers, the Scoreboard service's while it records the win") {
        within(HANG) { !debug()["session"].asBoolean }
        val xtc = PlaybookIde.XDK_BIN.resolve("xtc").toString()
        program(xtc, "run", "--debug", "listen:127.0.0.1:0,suspend", "--deduce", "cardGame").use { game ->
            val listening = Regex("Listening for the debugger at [^ ]+:(\\d+)")
            expect(within(HANG) { listening.containsMatchIn(game.output) }) { "The program: ${game.output.takeLast(400)}" }
            val port = listening.find(game.output)!!.groupValues[1].toInt()
            // the service has a fiber only while it runs a call, and the game calls it once a player has won
            open(source("Scoreboard.x"), 0)
            val recorded = at("wins.put(name, winsOf(name) + 1);")
            breakpoint(recorded, "")
            val was = attachConfiguration("Attach to cardGame", port)
            try {
                toolbar("Debug")
                // every turn waits for Enter: the game has them all at once, and plays to its end
                game.input("\n".repeat(5000))
                expect(within(HANG) { pausedIn("Scoreboard.x", recorded + 1) }) {
                    "Not paused in recordWin: ${position()}; ${game.output.takeLast(300)}"
                }
                threads().let { expect("Scoreboard" in it) { "The threads: ${it.take(600)}" } }
            } finally {
                clearBreakpoints()
                stopAll()
                selectConfiguration(was)
            }
        }
    }
    shot(
        "Debug with Gradle",
        "In Gradle mode, Debug runs runXtc with the debugger, and stops at the same breakpoints, in the same Debug window.",
        feature = "runXtc · xtc/debugTask",
        focus = "toolwindow:Debug",
        check = { frameNames().let { expect(it.first().contains("deal")) { "The frames: $it" } } },
        settle = 3.seconds,
        until = { pausedAt(deal + 1) },
        after = {
            stopAll()
            buildMode("EMBEDDED")
            clearBreakpoints()
        },
    ) {
        within(HANG) { !debug()["session"].asBoolean }
        buildMode("GRADLE")
        open(source("CardCollection.x"), 0)
        breakpoint(deal, "")
        open(file(MODULE), 0)
        action("Debug")
    }
    verify("in command-line mode, Debug runs xtc under the debugger, and stops at a breakpoint") {
        within(HANG) { !debug()["session"].asBoolean }
        runWith("COMMAND_LINE")
        try {
            open(source("CardCollection.x"), 0)
            breakpoint(deal, "")
            open(file(MODULE), 0)
            action("Debug")
            expect(within(HANG) { pausedIn("CardCollection.x", deal + 1) }) { "Not paused in deal: ${position()}" }
        } finally {
            stopAll()
            clearBreakpoints()
            runWith("PROJECT")
        }
    }
    shot(
        "A condition that cannot be evaluated",
        "A breakpoint whose condition cannot be evaluated stops anyway, to be safe, and says why it stopped.",
        feature = "setBreakpoints (condition) · stopped",
        focus = "toolwindow:Debug",
        // the adapter says why in an output event, which the Debug window's Console shows, as every client does
        check = {
            expect(listOf(debugConsole(), toolWindowText("Debug"), popupText()).any { "cannot be evaluated" in it }) {
                "The console: ${debugConsole().takeLast(300)}; the Debug window: ${toolWindowText("Debug").take(300)}"
            }
        },
        settle = 3.seconds,
        until = { pausedAt(deck + 1) },
        after = {
            stopAll()
            clearBreakpoints()
        },
    ) {
        within(HANG) { !debug()["session"].asBoolean }
        open(source("Deck.x"), 0)
        breakpoint(deck, "noSuchName == 1")
        open(file(MODULE), 0)
        toolbar("Debug")
    }
    verify("Debug of Current File on the module file starts a session, which ends cleanly") {
        within(HANG) { !debug()["session"].asBoolean }
        closeDialogs()
        expect(within(HANG) { !dialogShowing() }) { "A dialog stays open" }
        open(file(MODULE), 0)
        selectCurrentFile()
        // the widget's Debug as it stands for Current File: enabled or not, and what its tooltip says
        val state = toolbarState("Debug")
        toolbar("Debug")
        expect(
            within(HANG) { debug()["session"].asBoolean },
        ) { "No debug session; the Debug button was $state, now ${toolbarState("Debug")}" }
        stopAll()
        expect(within(HANG) { !debug()["session"].asBoolean }) { "The session did not end: ${debug()}" }
    }
    restore()
    verifyRestored()
}

/** The calls that the paused line's Smart Step Into highlights, its targets, by their names. */
private fun TourProbe.smartSteps(): List<String> =
    JsonParser
        .parseString(highlighted("SMART_STEP_INTO"))
        .asJsonArray
        .map { it.asJsonObject["text"].asString }

private fun Tour.testing() {
    section("Tests")
    card(
        "Tests",
        "xunit tests run and debug from the gutter, with the IDE's test runner, embedded, with Gradle or with xtc.",
        TourCard(
            eyebrow = "TESTS",
            heading = "xunit, in the IDE's test runner",
            lead = "The same results tree, embedded, with Gradle's testXtc, or with xtc test",
            boxes =
                listOf(
                    TourBox("Run", "A module's tests, a test, or several, from the gutter or a run configuration; rerun the failed ones."),
                    TourBox("Read", "Passed, failed and skipped tests, with their output, messages and the failing line, a click away."),
                    TourBox("Debug", "Any test under the debugger, with its breakpoints."),
                ),
        ),
    )
    var module = 0
    var started = ""
    shot(
        "Test icons",
        "The gutter has a test icon on every test module and every @Test method, from the server's view of the tests.",
        feature = "xtc/tests",
        focus = "gutter",
        settle = 3.seconds,
        until = { gutterIcons().lines().count { it.startsWith("Test") } > DECK_TESTS },
        check = { expect(gutterIcons().lines().count { it.startsWith("Test") } > DECK_TESTS) { "The icons: ${gutterIcons()}" } },
    ) {
        open(test("DeckTest.x"), 0)
        module = at("module DeckTest")
    }
    shot(
        "Run or debug a test",
        "A click on a test icon offers to run or debug it: here every test in DeckTest.",
        feature = "xtc/tests",
        focus = "popup",
        until = { "Debug" in popupText() },
        check = { popupText().let { expect("Run 'Tests of DeckTest'" in it && "Debug" in it) { "The choices: $it" } } },
        settle = 2.seconds,
    ) {
        gutter(module, "Tests of DeckTest")
    }
    var embedded = emptyMap<String, String>()
    shot(
        "Test results",
        "The tests run embedded, and the IDE's test runner shows each one's result, time and output.",
        feature = "xtc/run · xtc/testEvent",
        focus = "toolwindow:Run",
        check = {
            testCounts().let { expect(it["passed"] == DECK_TESTS && it["failed"] == null) { "DeckTest: $it" } }
            embedded = testStates()
        },
        settle = 3.seconds,
        until = { runId() != started && runEnded() && testCounts().values.sum() >= DECK_TESTS },
    ) {
        watchTests()
        started = runId()
        check(choose("Run")) { "No Run: ${rows()}" }
    }
    var failing = 0
    shot(
        "A failing test",
        "A failing test is red, with its assertion's message and the line that failed, a click away.",
        feature = "xtc/testEvent",
        focus = "toolwindow:Run",
        check = { testCounts().let { expect(it["failed"] == 1 && it["passed"] == DECK_TESTS - 1) { "DeckTest: $it" } } },
        settle = 3.seconds,
        until = { runId() != started && runEnded() && testCounts().values.sum() >= DECK_TESTS },
    ) {
        open(test("DeckTest.x"), 0)
        replace("assert deck.size() == GameConstants.DECK_SIZE as", 0, "assert deck.size() == GameConstants.DECK_SIZE + 1 as")
        failing = at("void testDeckHas52Cards")
        within(HANG) { analyzed(test("DeckTest.x")) }
        gutter(module, "Tests of DeckTest")
        expect(within(HANG) { "Run" in rows() }) { "No test choices: ${rows()}" }
        watchTests()
        started = runId()
        check(choose("Run")) { "No Run: ${rows()}" }
    }
    verify("a test in the results goes to its source") {
        expect(selectRow("Run", "testDeckHas52Cards")) { "No testDeckHas52Cards in ${toolWindowText("Run").take(400)}" }
        windowAction("Run", "EditSource")
        expect(within(HANG) { editor().let { it["file"].asString == "DeckTest.x" && it["line"].asInt == failing } }) {
            "In ${editor()}, not DeckTest.x:$failing"
        }
    }
    verify("Rerun Failed Tests runs only the test that failed", setOf(Need.RUN_WINDOW)) {
        expect(within(HANG) { windowActionEnabled("Run", "RerunFailedTests") }) { "Rerun Failed Tests is not enabled" }
        watchTests()
        started = runId()
        windowAction("Run", "RerunFailedTests")
        expect(within(HANG) { runId() != started && runEnded() && testCounts().values.sum() >= 1 }) { "No rerun: ${testCounts()}" }
        expect(testCounts() == mapOf("failed" to 1)) { "The rerun: ${testCounts()}" }
    }
    restore()
    verify("a test method's icon runs that test alone") {
        open(test("DeckTest.x"), 0)
        val one = at("void testDeckCanBeShuffled")
        gutter(one, "Test ")
        expect(within(HANG) { "Run" in rows() }) { "No test choices: ${rows()}" }
        watchTests()
        started = runId()
        check(choose("Run")) { "No Run: ${rows()}" }
        expect(
            within(HANG) {
                runId() != started && runEnded() && testCounts().values.sum() >= 1
            },
        ) { "The test did not run: ${testCounts()}" }
        expect(testCounts() == mapOf("passed" to 1)) { "The tests that ran: ${testStates()}" }
    }
    var shuffle = 0
    shot(
        "Debug a test",
        "Debug on a test's icon runs it under the debugger: it stops at the test's breakpoint, here before the shuffle.",
        feature = "launch (tests)",
        focus = "toolwindow:Debug",
        settle = 3.seconds,
        until = { pausedAt(shuffle + 1) },
        check = { frameNames().let { expect(it.first().contains("testDeckCanBeShuffled")) { "The frames: $it" } } },
        after = {
            stopAll()
            clearBreakpoints()
        },
    ) {
        open(test("DeckTest.x"), 0)
        shuffle = at("deck.shuffle();")
        breakpoint(shuffle, "")
        val test = at("void testDeckCanBeShuffled")
        gutter(test, "Test ")
        expect(within(HANG) { "Debug" in rows() }) { "No test choices: ${rows()}" }
        check(choose("Debug")) { "No Debug: ${rows()}" }
    }
    shot(
        "Tests with Gradle",
        "In Gradle mode the same icons run the XTC plugin's testXtc, and xunit's results come back to the same test runner.",
        feature = "testXtc",
        focus = "toolwindow:Run",
        check = { testCounts().let { expect(it["passed"] == DECK_TESTS && it["failed"] == null) { "DeckTest with Gradle: $it" } } },
        settle = 3.seconds,
        until = { runId() != started && runEnded() && testCounts().values.sum() >= DECK_TESTS },
        after = { buildMode("EMBEDDED") },
    ) {
        within(HANG) { !debug()["session"].asBoolean }
        buildMode("GRADLE")
        open(test("DeckTest.x"), 0)
        gutter(module, "Tests of DeckTest")
        expect(within(HANG) { "Run" in rows() }) { "No test choices: ${rows()}" }
        watchTests()
        started = runId()
        check(choose("Run")) { "No Run: ${rows()}" }
    }
    verify("embedded and Gradle runs give each test the same result") {
        val gradle = testStates()
        expect(embedded.isNotEmpty() && gradle == embedded) { "Embedded: $embedded; Gradle: $gradle" }
    }
    verify("testXtc, run from the Gradle window, runs every test module") {
        toolWindow("Gradle", true)
        expect(within(HANG) { "Tasks" in toolWindowText("Gradle") }) { "The Gradle window: ${toolWindowText("Gradle").take(300)}" }
        expand("Gradle", "Tasks")
        expand("Gradle", "verification")
        expect(within(HANG) { selectRow("Gradle", "testXtc") }) { "No testXtc in ${toolWindowText("Gradle").take(400)}" }
        // the earlier runs go first, so that only this run's own end can satisfy the wait: run 14 read the console a
        //  second into the run, as the earlier Gradle run's content had ended and settled
        clearToolWindow("Run")
        windowAction("Gradle", "ExternalSystem.RunTask")
        val modules = TEST_MODULES
        // IntelliJ ends a Gradle run's console, whether its build succeeds or fails: a build's with "Execution finished",
        //  and a test run's, as run 14b's was, with "Process finished with exit code"
        expect(
            within(HANG) { toolWindowText("Run").let { "Execution finished 'testXtc'" in it || "Process finished with exit code" in it } },
        ) {
            "testXtc did not end: ${toolWindowText("Run").takeLast(400)}"
        }
        // the run's root, whose console has the whole run, not the test that the tree selected last, read once the
        // console shows all of it: a console shows a run's last output only once it flushes it, after the process ends
        selectRow("Run", "Test Results")
        expect(within(HANG) { consolesSettled() }) { "testXtc's console did not show all its output" }
        val output = toolWindowText("Run")
        val succeeded = "BUILD SUCCESSFUL" in output || "Process finished with exit code 0" in output
        val missing = modules.filterNot { it in output } + listOf("BUILD SUCCESSFUL or exit code 0").filterNot { succeeded }
        expect(missing.isEmpty()) { "testXtc's console lacks $missing, in ${output.length} characters: ${output.takeLast(600)}" }
        toolWindow("Gradle", false)
    }
    verify("in command-line mode, the tests run with xtc test, with the same results") {
        open(test("DeckTest.x"), 0)
        gutter(module, "Tests of DeckTest")
        expect(within(HANG) { "Run" in rows() }) { "No test choices: ${rows()}" }
        dismiss()
        selectConfiguration("Tests of DeckTest")
        runWith("COMMAND_LINE")
        try {
            watchTests()
            started = runId()
            toolbar("Run")
            expect(within(HANG) { runId() != started && runEnded() && testCounts().values.sum() >= DECK_TESTS }) {
                "The tests did not run: ${testCounts()}"
            }
            expect(testStates() == embedded) { "Embedded: $embedded; xtc test: ${testStates()}" }
        } finally {
            runWith("PROJECT")
        }
    }
    verify("Run 'All Ecstasy Tests' on the test sources runs every test module's tests, embedded, into one run") {
        watchTests()
        started = runId()
        // the run configurations before the context's, for a failure's message: run 14b's context made "All Ecstasy
        //  Tests (1)" beside an "All Ecstasy Tests" that the tour makes nowhere
        val earlier = runConfigurations()
        val offered = runFromFolder(test("DeckTest.x").substringBeforeLast('/'), "All Ecstasy Tests")
        expect(offered.lines().any { it.startsWith("All Ecstasy Tests") }) { "The test sources' context offers: $offered" }
        // the run manager makes a new configuration's name unique, as run 14b's "All Ecstasy Tests (1)" was, when one
        //  of the name is there and the producer does not take it as this context's
        expect("All Ecstasy Tests" in offered.lines()) {
            "Run All is offered as $offered; the run configurations before: $earlier; after: ${runConfigurations()}"
        }
        expect(within(HANG) { runId() != started && runEnded() }) { "Run All did not end: ${toolWindowText("Run").takeLast(400)}" }
        // the run's tree shows its modules once it has their nodes: run 14b read only "Test Results, 64 tests passed"
        //  as the run ended
        expect(
            within(HANG) { toolWindowText("Run").let { tree -> TEST_MODULES.all { it in tree } } } &&
                testCounts()["failed"] == null &&
                (testCounts()["passed"] ?: 0) > DECK_TESTS,
        ) {
            "Run All: ${testCounts()}; ${toolWindowText("Run").take(600)}"
        }
    }
    // tests that the file gains while the IDE runs: one that passes, one that fails, one that throws, a disabled one,
    // then one that waits an hour, which only Stop ends
    verify("a test added to the file gets its icon and runs with its module, without a restart") {
        open(test("DeckTest.x"), 0)
        replace(DECK_HEADER, 0, DECK_HEADER + XUNIT_IMPORT)
        replace(DECK_END, 0, ADDED_TESTS)
        expect(within(HANG) { "testDeckAddedLater" in gutterIcons() }) { "The icons: ${gutterIcons()}" }
        gutter(module, "Tests of DeckTest")
        expect(within(HANG) { "Run" in rows() }) { "No test choices: ${rows()}" }
        watchTests()
        started = runId()
        check(choose("Run")) { "No Run: ${rows()}" }
        expect(within(HANG) { runId() != started && runEnded() && testCounts().values.sum() >= DECK_TESTS + 4 }) {
            "The tests did not run: ${testStates()}"
        }
        expect(testResult("testDeckAddedLater")?.get("state")?.asString == "passed") { "The tests: ${testStates()}" }
    }
    verify("a test that throws is an error, apart from a failed assertion") {
        val thrown = testResult("testDeckThrows")
        val failed = testResult("testDeckFailsOnPurpose")
        expect(failed?.get("state")?.asString == "failed") { "The failed assertion: $failed" }
        expect(thrown?.get("state")?.asString == "error") {
            "The test that throws: $thrown, not an error, as the failed assertion: $failed"
        }
    }
    verify("a disabled test is reported as skipped") {
        testResult("testDeckDisabled").let { expect(it?.get("state")?.asString == "ignored") { "The disabled test: $it; ${testStates()}" } }
    }
    verify("Stop ends a test run partway, and the test that was running does not pass", setOf(Need.SERVER)) {
        replace(ADDED_END, 0, WAITING_TEST)
        expect(within(HANG) { "testDeckWaits" in gutterIcons() }) { "The icons: ${gutterIcons()}" }
        gutter(module, "Tests of DeckTest")
        expect(within(HANG) { "Run" in rows() }) { "No test choices: ${rows()}" }
        watchTests()
        started = runId()
        check(choose("Run")) { "No Run: ${rows()}" }
        // the test waits an hour for a timer, so the run cannot end before Stop
        expect(within(HANG) { runId() != started && "testDeckWaits" in testsRunning() }) {
            "testDeckWaits did not start: ${testStates()}; running: ${testsRunning()}"
        }
        windowAction("Run", "Stop")
        expect(within(HANG) { runEnded() }) { "Stop did not end the run: running ${testsRunning()}" }
        // the console's tree: its root "Terminated", its status "Stopped."
        expect(within(HANG) { testRunStopped() || toolWindowText("Run").let { "Terminated" in it && "Stopped" in it } }) {
            "The run's console does not say it was stopped: ${toolWindowText("Run").takeLast(300)}"
        }
        expect(testResult("testDeckWaits")?.get("state")?.asString != "passed") { "testDeckWaits: ${testResult("testDeckWaits")}" }
    }
    restore()
    verifyRestored()
}

/** The project's test modules, which testXtc and Run All Ecstasy Tests run. */
private val TEST_MODULES = listOf("CardCollectionTest", "CardGameTest", "CardTest", "DeckTest", "EightsTest", "HandTest", "PlayerTest")

/** DeckTest's module header, after which the tests that the tour adds import xunit, for their @Disabled. */
private const val DECK_HEADER = "    package cardGame import cardGame;\n"

private const val XUNIT_IMPORT = "    package xunit import xunit.xtclang.org;\n"

/** DeckTest's last lines, which the tour's tests go before the module's end of. */
private const val DECK_END = "as \"Deck should be empty\";\n    }\n}"

/** The tests that the tour adds to DeckTest: one that passes, one that fails, one that throws, and a disabled one. */
private val ADDED_TESTS =
    """
    |as "Deck should be empty";
    |    }
    |
    |    @Test
    |    void testDeckAddedLater() {
    |        assert new Deck("Added Deck").size() == GameConstants.DECK_SIZE;
    |    }
    |
    |    @Test
    |    void testDeckFailsOnPurpose() {
    |        assert new Deck("Odd Deck").size() == GameConstants.DECK_SIZE + 1 as "a failure, on purpose";
    |    }
    |
    |    @Test
    |    void testDeckThrows() {
    |        throw new Unsupported("an error, on purpose");
    |    }
    |
    |    @Test
    |    @xunit.annotations.Disabled("skipped, on purpose")
    |    void testDeckDisabled() {
    |        assert False;
    |    }
    |}
    """.trimMargin().trimEnd()

/** The last lines of DeckTest with the tour's tests, the disabled one's end. */
private const val ADDED_END = "        assert False;\n    }\n}"

/** A test that the tour adds to DeckTest after the others: it waits an hour for a timer, so that its run ends by Stop. */
private val WAITING_TEST =
    """
    |        assert False;
    |    }
    |
    |    @Test
    |    void testDeckWaits() {
    |        @Inject Timer timer;
    |        @Future Boolean done;
    |        timer.schedule(Duration:1h, () -> {done = True;});
    |        assert done;
    |    }
    |}
    """.trimMargin().trimEnd()

private fun Tour.settings() {
    section("Settings and the server")
    shot(
        "The language server",
        "Settings | Languages & Frameworks: the Ecstasy language service's features can each be turned off, per project.",
        feature = "Settings | Ecstasy Language Service",
        focus = "popup",
        until = { "Show Ecstasy inlay hints" in popupText() },
        check = {
            popupText().let {
                expect(
                    "Show Ecstasy inlay hints" in it && "Language adapter" in it,
                ) { "The page: ${it.take(300)}" }
            }
        },
        settle = 3.seconds,
    ) {
        settings("xtc.service")
    }
    verify("the page's effective configuration is the running server's") {
        click("Refresh effective configuration")
        expect(within(HANG) { popupText().let { "Effective running service" in it && "\"adapter\"" in it } }) {
            "The report: ${popupText().takeLast(400)}"
        }
        closeDialogs()
    }
    verify("turning the inlay hints off takes them away, and on brings them back") {
        within(HANG) { !dialogShowing() }
        open(source("Eights.x"), 0)
        val line = at("deck.deal(one.hand")
        expect(within(HANG) { "numCards:" in inlays(line) }) { "No hints to turn off: ${inlays(line)}" }
        serviceSetting("Show Ecstasy inlay hints", false)
        try {
            open(source("Eights.x"), 0)
            expect(within(HANG) { inlays(line).isBlank() }) { "The hints are still there: ${inlays(line)}" }
        } finally {
            serviceSetting("Show Ecstasy inlay hints", true)
        }
        open(source("Eights.x"), 0)
        expect(within(HANG) { "numCards:" in inlays(line) }) { "The hints did not come back: ${inlays(line)}" }
    }
    verify("turning the reference counts off takes their lenses away, and on brings them back") {
        within(HANG) { !dialogShowing() }
        open(source("Player.x"), 0)
        expect(within(HANG) { "reference" in lenses() }) { "No reference lenses to turn off: ${lenses()}" }
        serviceSetting("Show Ecstasy reference counts", false)
        try {
            open(source("Player.x"), 0)
            expect(within(HANG) { "reference" !in lenses() }) { "The reference lenses are still there: ${lenses()}" }
        } finally {
            serviceSetting("Show Ecstasy reference counts", true)
        }
        open(source("Player.x"), 0)
        expect(within(HANG) { "reference" in lenses() }) { "The reference lenses did not come back: ${lenses()}" }
    }
    shot(
        "The compiler",
        "The compiler's page: what it found to build, under Build import's effective paths, and how it imported them.",
        feature = "xtc/compilerSourceModules",
        focus = "popup",
        until = { popupText().let { "Effective compiler modules" in it && "DeckTest" in it } },
        check = {
            popupText().let {
                expect("Effective compiler modules" in it && "cardGame" in it && "DeckTest" in it) { "The page: ${it.take(400)}" }
            }
        },
        settle = 3.seconds,
        after = { closeDialogs() },
    ) {
        settings("xtc.compiler")
        // the Source modules table overrides what discovery finds, and is empty while it decides
        expect(within(HANG) { selectItem("Build import") }) { "No Build import page: ${popupText().take(300)}" }
        expect(within(HANG) { expandGroup("Effective paths and import details") }) { "No effective paths: ${popupText().take(300)}" }
    }
    verify("the application's defaults page has the same settings, for projects that inherit them") {
        within(HANG) { !dialogShowing() }
        settings("xtc.service.defaults")
        expect(within(HANG) { popupText().let { "Show Ecstasy inlay hints" in it && "Language adapter" in it } }) {
            "The page: ${popupText().take(400)}"
        }
        closeDialogs()
    }
    shot(
        "The server's runtime",
        "The server's runtime: the Java it runs on, its memory, and its logs, for when something goes wrong.",
        feature = "Settings | Ecstasy Server Runtime",
        focus = "popup",
        settle = 3.seconds,
        until = { "Log history (days)" in popupText() },
        check = { popupText().let { expect("Log history (days)" in it && "JVM option" in it) { "The page: ${it.take(400)}" } } },
        after = { closeDialogs() },
    ) {
        settings("xtc.runtime")
    }
    prepare { within(HANG) { !dialogShowing() } }
    shot(
        "Language servers",
        "LSP4IJ's Language Servers window shows the Ecstasy server's state, and traces what the IDE and the server say.",
        feature = "LSP4IJ Language Servers · \$/logTrace",
        focus = "toolwindow:Language Servers",
        until = { toolWindowText("Language Servers").let { "Ecstasy Language Server" in it && "textDocument/" in it } },
        check = {
            toolWindowText("Language Servers").let {
                expect("Ecstasy Language Server" in it && "textDocument/" in it) { "The window: ${it.take(400)}" }
            }
        },
        settle = 3.seconds,
        after = {
            serverTrace("off")
            toolWindow("Language Servers", false)
        },
    ) {
        serverTrace("verbose")
        open(source("Eights.x"), 0)
        at("INITIAL_HAND_SIZE", delta = 3)
        action("QuickJavaDoc")
        within(HANG) { "Number of cards dealt" in popupText() }
        dismiss()
        toolWindow("Language Servers", true)
        // the server's process, whose Traces tab shows what the IDE and the server said
        within(HANG) { selectRow("Language Servers", "started") }
    }
    verify("Ctrl+Alt+X, L shows the server's log, and hides it again") {
        // the keys, as a user presses them: the keymap decides, so a shortcut the plugin lost does nothing
        open(source("Eights.x"), 0)
        editorChord("ctrl alt X", "L")
        expect(within(HANG) { toolWindowText("Language Servers").let { "initialize" in it || "INFO" in it } }) {
            "The log: ${toolWindowText("Language Servers").take(400)}"
        }
        open(source("Eights.x"), 0)
        editorChord("ctrl alt X", "L")
        expect(within(HANG) { toolWindowText("Language Servers").isEmpty() }) { "The Language Servers window is still open" }
    }
    verify("Tools | Toggle Ecstasy Language Server Log, clicked, shows the server's log") {
        open(source("Eights.x"), 0)
        // a click on Tools in the menu bar, which the tour's IDE shows as a bar of its own; the menus it leaves open
        //  close as it ends, which failed the Settings checks after it in run 16
        try {
            openMenu("Tools")
            // what shows once Tools is clicked, for a failure's message
            val opened = menuItems()
            val clicked =
                within(HANG) { clickMenuItem("Toggle Ecstasy Language Server Log") || toolWindowText("Language Servers").isNotEmpty() }
            // what showed to click, when the toggle was not clicked
            val items = if (clicked) "" else "; the menu items once Tools was open: $opened, and when the click gave up: ${menuItems()}"
            expect(within(HANG) { toolWindowText("Language Servers").let { "initialize" in it || "INFO" in it } }) {
                "The log: ${toolWindowText("Language Servers").take(400)}$items"
            }
        } finally {
            closeMenus()
        }
        toolWindow("Language Servers", false)
    }
    verify("Export Ecstasy Server Logs saves them as a ZIP") {
        val zip = file("ecstasy-server-logs.zip")
        open(source("Eights.x"), 0)
        action("xtc.exportServerLogs")
        expect(within(HANG) { dialogShowing() }) { "No save dialog" }
        // the dialog opens on the project's folder; its name field takes a name, not a path, which it marks invalid
        fillDialog("ecstasy-server-logs.zip")
        expect(confirm("OK", "Save")) { "The save dialog did not close" }
        expect(within(HANG) { Files.exists(Path.of(zip)) && Files.size(Path.of(zip)) > 0 }) { "No logs at $zip" }
        // and the notification says so
        toolWindow("Notifications", true)
        expect(within(HANG) { "Saved ecstasy-server-logs.zip" in toolWindowText("Notifications") }) {
            "The notifications: ${toolWindowText("Notifications").take(400)}"
        }
        toolWindow("Notifications", false)
        delete(zip)
    }
    verify("Restart in the Language Servers window restarts the server, and its features answer again") {
        val before = serverState()
        // a real click on the window's Restart, which the server's node offers; its action where no button shows
        if (!windowButton("Language Servers", "lsp.console.explorer.restart")) serverAction("lsp.console.explorer.restart")
        expect(
            within(HANG) {
                serverState().let { it.startsWith("started") && it != before }
            },
        ) { "The server: ${serverState()}, before $before" }
        // the first Ctrl+Q after the restart, which LSP4IJ asks once, before it has reopened the file
        open(source("Eights.x"), 0)
        at("INITIAL_HAND_SIZE", delta = 3)
        action("QuickJavaDoc")
        val first = within(HANG) { "Number of cards dealt" in popupText() }
        answers()
        expect(first) { "The first documentation after the restart showed nothing" }
        toolWindow("Language Servers", false)
    }
    verify("switching the language adapter restarts the server with it, and back again", setOf(Need.SERVER)) {
        val before = serverState()
        // Ctrl+Alt+X, A, as a user presses it, and the other adapter chosen in its popup
        open(source("Eights.x"), 0)
        editorChord("ctrl alt X", "A")
        expect(within(HANG) { pick("Tree-sitter") }) { "No adapter chooser: ${popupText().take(300)}" }
        try {
            expect(within(HANG) { serverState().let { it.startsWith("started") && it != before } }) { "The server: ${serverState()}" }
            // the tree-sitter adapter has the syntax's features: the file's structure among them, once LSP4IJ has
            //  opened the file at the new server: File Structure asks the server as it opens, and shows the file
            //  alone without its answer: run-tour-10558846463380451308 opened it a second before the file connected.
            //  Connected is not yet opened: run-tour-7286541464185912361 pressed Ctrl+F12 at 14:26:44.179, the didOpen
            //  came at 44.695, and the structure was the empty answer of the server before, which refused it as it
            //  stopped; the green run before it showed that server's symbols, never tree-sitter's
            expect(within(HANG) { serverHasFile(source("Eights.x")) }) { "Eights.x is not open at the server ${serverState()}" }
            open(source("Eights.x"), 0)
            action("FileStructurePopup")
            expect(within(HANG) { "reshuffle" in popupText() }) { "The structure with tree-sitter: ${popupText().take(300)}" }
            key("ESCAPE")
        } finally {
            // back to the bundled adapter, however the checks above went, so that the next checks have the compiler's
            //  server: in that run they ran on tree-sitter, and failed for want of documentation and errors
            dismiss()
            val treeSitter = serverState()
            open(source("Eights.x"), 0)
            editorChord("ctrl alt X", "A")
            expect(within(HANG) { pick("Bundled default") }) { "No adapter chooser: ${popupText().take(300)}" }
            expect(
                within(HANG) { serverState().let { it.startsWith("started") && it != treeSitter } },
            ) { "The server: ${serverState()}" }
        }
        answers()
        // the adapter in use, chosen again, keeps the server as it is: no restart, and no message either
        val same = serverState()
        open(source("Eights.x"), 0)
        editorChord("ctrl alt X", "A")
        expect(within(HANG) { pick("Bundled default") }) { "No adapter chooser: ${popupText().take(300)}" }
        expect(!within(HANG) { serverState() != same }) { "Choosing the adapter in use restarted the server: ${serverState()}" }
    }
    verify("Restart on the Ecstasy Server Runtime and Logs page, clicked, restarts the server, which answers") {
        val before = serverState()
        settings("xtc.runtime")
        expect(within(HANG) { realClick("Restart running Ecstasy servers with saved settings") }) { "No Restart: ${popupText().take(300)}" }
        expect(within(HANG) { serverState().let { it.startsWith("started") && it != before } }) { "The server: ${serverState()}" }
        closeDialogs()
        answers()
    }
    verify("the server comes back when its process dies, and its features answer again") {
        val before = serverState()
        val pid = before.substringAfter(' ').toLongOrNull()
        expect(pid != null) { "No server process: $before" }
        ProcessHandle.of(pid!!).ifPresent(ProcessHandle::destroyForcibly)
        expect(within(HANG) { serverState() != before }) { "The server still runs: ${serverState()}" }
        // an edit, as a user's next keystroke, asks for the server again
        open(source("Eights.x"), 0)
        replace("return drawPile.popCard();", 0, "return drawPile.popCrd();")
        expect(within(HANG) { serverState().startsWith("started") }) { "The server did not come back: ${serverState()}" }
        hasError(source("Eights.x"), "popCrd")
        restore()
    }
    restore()
    verifyRestored()
}

/** Sets the Ecstasy Language Service page's check box [label], as the page and its OK do, with the project's own settings. */
private fun TourProbe.serviceSetting(
    label: String,
    on: Boolean,
) {
    settings("xtc.service")
    expect(within(HANG) { label in popupText() }) { "No Ecstasy Language Service page" }
    form("Use application language-service defaults", "false")
    form(label, on.toString())
    expect(confirm("OK")) { "The settings did not close" }
}

/** Fails unless the server answers: Quick Documentation shows a doc comment, and an error is marked as it is typed. */
private fun Tour.answers() {
    ui.open(source("Eights.x"), 0)
    ui.at("INITIAL_HAND_SIZE", delta = 3)
    // Ctrl+Q again until it shows, as a user presses it: a server that has just started rejects a request that
    // comes before it has the file, and LSP4IJ does not ask again
    expect(
        within(HANG) {
            ui.action("QuickJavaDoc")
            within(5.seconds) { "Number of cards dealt" in ui.popupText() }
        },
    ) { "No documentation: ${ui.popupText().take(300)}" }
    ui.dismiss()
    ui.replace("return drawPile.popCard();", 0, "return drawPile.popCrd();")
    ui.hasError(source("Eights.x"), "popCrd")
    ui.replace("return drawPile.popCrd();", 0, "return drawPile.popCard();")
    ui.compilesCleanly(source("Eights.x"))
}

private fun Tour.coming() {
    section("Coming with LSP4IJ 0.30.0")
    card(
        "Coming with LSP4IJ 0.30.0",
        "The server already does these; the IDE shows them once it moves to LSP4IJ 0.30.0, and the tour adds them then.",
        TourCard(
            eyebrow = "COMING WITH LSP4IJ 0.30.0",
            heading = "Already in the server, next in the IDE",
            lead = "Features of the debug adapter and the language server that need LSP4IJ 0.30.0's client",
            boxes =
                listOf(
                    TourBox(
                        "Breakpoints",
                        "Function breakpoints, data breakpoints on a value's change, hit counts, logpoints, " +
                            "and exception filter conditions.",
                    ),
                    TourBox(
                        "Debugger",
                        "Pause, restart a frame, inline values, hex display, paged values, the variables a console line " +
                            "changed, and loaded sources and modules.",
                    ),
                    TourBox("Editing", "Linked editing: a name's occurrences change together as one is typed."),
                ),
        ),
    )
    section("The end")
    card(
        "That's the tour",
        "The XTC plugin for IntelliJ: install it from the Marketplace, and open an Ecstasy project, or make one with New Project.",
        TourCard(
            eyebrow = "XTC LANGUAGE SUPPORT · INTELLIJ IDEA",
            heading = "Ecstasy in IntelliJ",
            lead = "Edit, navigate, refactor, build, run, debug and test: one language server, in every editor",
            code =
                """
                File | New | Project | Ecstasy          a new project, with the XTC Gradle plugin
                Settings | Build, Execution, Deployment | Ecstasy
                                                        the build mode: embedded or Gradle
                Settings | Languages & Frameworks       the language service and its compiler
                """.trimIndent(),
        ),
    )
}
