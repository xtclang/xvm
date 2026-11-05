package org.xtclang.idea.playbook

import kotlin.time.Duration.Companion.seconds

/** The recording's screen: the IDE's window, above the caption band that the composer adds under it. */
private const val WIDTH = 1920
private const val HEIGHT = 936

/** The game's module file, whose gutter has its Run icon. */
private const val MODULE = "src/main/x/cardGame.x"

/** The tests of DeckTest, which the test steps run. */
private const val DECK_TESTS = 8

/** The tour's steps, in order: what each shows, says and does. */
internal fun Tour.script() {
    awaitProject()
    ui.prepare(WIDTH, HEIGHT, "Dark", 15)
    ui.open(source("Eights.x"), 0)
    awaitServer()
    awaitIdle()
    // the first analysis of the project, which the first steps show
    Thread.sleep(15.seconds.inWholeMilliseconds)

    start()
    reading()
    navigation()
    writing()
    diagnostics()
    refactoring()
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
        focus = "toolwindow:Project",
        check = { expect("cardGame" in toolWindowText("Project")) { "The Project window: ${toolWindowText("Project").take(300)}" } },
    )
}

private fun Tour.reading() {
    section("Reading code")
    shot(
        "Semantic highlighting",
        "The compiler colors what each name is: fields, parameters, locals, types and static members, beyond what the grammar can tell.",
        focus = "editor",
    ) {
        open(source("Player.x"), 0)
        at("cardMatches(card, prev)")
    }
    shot(
        "Inlay hints and references",
        "Hints name a call's parameters where its arguments don't, and the line above each declaration counts its references.",
        focus = "line",
        settle = 2.seconds,
    ) {
        open(source("Eights.x"), 0)
        at("deck.deal(one.hand")
    }
    shot(
        "Quick documentation",
        "Ctrl+Q, or hovering, shows a declaration's signature and its doc comment, from the compiler's view of it.",
        focus = "popup",
        check = { popupText().let { expect("Number of cards dealt" in it) { "No doc comment in: $it" } } },
        settle = 2.seconds,
        after = { dismiss() },
    ) {
        at("INITIAL_HAND_SIZE", delta = 3)
        action("QuickJavaDoc")
    }
    shot(
        "The XDK's documentation",
        "Documentation works the same for the XDK's own types: HashMap's comes from the ecstasy module's sources.",
        focus = "popup",
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
    shot(
        "Parameter info",
        "Inside a call, Ctrl+P shows the parameters of the method it calls, with the one at the cursor in bold.",
        focus = "popup",
        check = { popupText().let { expect("numCards" in it) { "No parameters in: $it" } } },
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
        focus = "editor",
        settle = 3.seconds,
    ) {
        open(source("Player.x"), 0)
        at("card := eights.drawCard()", delta = 1)
    }
    shot(
        "Extend selection",
        "Ctrl+W grows the selection by the syntax around it: a name, the expression, the statement, the block. Ctrl+Shift+W shrinks it.",
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
        focus = "editor",
        after = { action("ExpandAllRegions") },
    ) {
        open(source("Eights.x"), 0)
        action("CollapseAllRegions")
        Thread.sleep(1000)
        // the class's own body stays open, so that its folded members show
        at("class Eights", delta = "class ".length)
        action("ExpandRegion")
    }
    shot(
        "File structure",
        "Ctrl+F12 lists the file's classes, properties and methods, from the compiler's symbols; type to filter, Enter to go.",
        focus = "popup",
        check = { popupText().let { expect("playGame" in it && "reshuffle" in it) { "The structure: $it" } } },
        settle = 2.seconds,
        after = { key("ESCAPE") },
    ) {
        action("FileStructurePopup")
    }
    shot(
        "The Structure window",
        "The Structure window keeps the outline beside the editor, and follows the cursor as it moves.",
        focus = "toolwindow:Structure",
        check = { toolWindowText("Structure").let { expect("playGame" in it) { "The Structure window: ${it.take(300)}" } } },
        after = { toolWindow("Structure", false) },
    ) {
        toolWindow("Structure", true)
    }
}

private fun Tour.navigation() {
    section("Navigation")
    shot(
        "Go to declaration",
        "Ctrl+B, or Ctrl+click, on a call goes to the method it calls: shuffle() is CardCollection's, which Deck inherits.",
        focus = "caret",
        check = { expect(editor()["file"].asString == "CardCollection.x") { "In ${editor()["file"]}" } },
        settle = 2.seconds,
    ) {
        open(source("Eights.x"), 0)
        at("deck.shuffle()", delta = "deck.".length)
        action("GotoDeclaration")
    }
    shot(
        "Go to type declaration",
        "Ctrl+Shift+B goes to the type of what is under the cursor: deck is a Deck, declared in Deck.x.",
        focus = "editor",
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
        focus = "popup",
        until = { "SaveEights" in popupText() },
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
        focus = "toolwindow:Find",
        check = { toolWindowText("Find").let { expect("FirstMatch" in it && "SaveEights" in it) { "The usages: ${it.take(400)}" } } },
        settle = 3.seconds,
        after = { toolWindow("Find", false) },
    ) {
        open(source("Player.x"), 0)
        at("static Boolean cardMatches", delta = "static Boolean ".length)
        action("FindUsages")
    }
    shot(
        "Call hierarchy",
        "Ctrl+Alt+H shows who calls a method, and who calls them: cardMatches is called by the player and by both strategies.",
        focus = "toolwindow:Hierarchy",
        until = { "choose" in toolWindowText("Hierarchy") },
        check = { toolWindowText("Hierarchy").let { expect("choose" in it) { "The callers: ${it.take(400)}" } } },
        settle = 3.seconds,
    ) {
        at("static Boolean cardMatches", delta = "static Boolean ".length)
        action("CallHierarchy")
    }
    shot(
        "Type hierarchy",
        "Ctrl+H shows a class's supertypes and subtypes: Deck and Hand extend CardCollection.",
        focus = "toolwindow:Hierarchy",
        until = { "Deck" in toolWindowText("Hierarchy") },
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
        focus = "popup",
        check = { popupText().let { expect("reshuffle" in it) { "The symbols: ${it.take(300)}" } } },
        settle = 3.seconds,
        after = { key("ESCAPE") },
    ) {
        action("GotoSymbol")
        Thread.sleep(1500)
        fill("reshuffle")
    }
    shot(
        "The XDK's sources",
        "Go to declaration also opens the XDK's sources, read-only: HashMap is in the ecstasy module's collections package.",
        focus = "editor",
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
        focus = "lookup",
        until = { "addCard" in lookupItems() },
        check = { lookupItems().let { expect("addCard" in it && "popCard" in it) { "The items: $it" } } },
        settle = 2.seconds,
        wait = 30.seconds,
        after = {
            dismiss()
            restore()
        },
    ) {
        open(source("Player.x"), 0)
        at("console.print(\$\"{name} draws {card}\");", delta = "console.print(\$\"{name} draws {card}\");".length)
        type("\nhand.")
        Thread.sleep(1000)
        action("CodeCompletion")
    }
    shot(
        "Name completion",
        "As a name is typed, completion offers the types, variables and keywords in scope: here the game's Scoreboard.",
        focus = "lookup",
        until = { "Scoreboard" in lookupItems() },
        check = { lookupItems().let { expect("Scoreboard" in it) { "The items: $it" } } },
        settle = 2.seconds,
        wait = 30.seconds,
        after = {
            dismiss()
            restore()
        },
    ) {
        open(source("Eights.x"), 0)
        at("Player current = one;", delta = "Player current = one;".length)
        type("\nScore")
        Thread.sleep(1000)
        action("CodeCompletion")
    }
    shot(
        "Live templates",
        "Ctrl+J lists the Ecstasy live templates: a module, class, interface, service, mixin, enum, const, package or method.",
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
        focus = "editor",
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
        "Typing a block's brace and Enter indents the new line and closes the block, as the code style says.",
        focus = "caret",
        check = {
            expect(
                Regex("""if \(hand\.isEmpty\(\)\) \{\n\s+\n\s+\}""") in fileText(source("Player.x")),
            ) { "No closed block in Player.x" }
        },
        after = { restore() },
    ) {
        open(source("Player.x"), 0)
        at("return drawForMatch(eights, prev);")
        type("if (hand.isEmpty()) {\n")
    }
    shot(
        "Comment out lines",
        "Ctrl+/ comments out the selected lines with //, and again removes them; Ctrl+Shift+/ wraps a selection in /* */.",
        focus = "editor",
        check = { expect(Regex("""//\s*return card1\.suit""") in fileText(source("Player.x"))) { "The lines are not commented out" } },
        after = { restore() },
    ) {
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
        expect(Regex("""/\*\s*card1\.rank == GameConstants\.SPECIAL_CARD\s*\*/""") in fileText(source("Player.x"))) {
            "No block comment around the selection in Player.x"
        }
    }
    restore()
    shot(
        "Code that needs formatting",
        "swapCards is indented too far, and a space sits before a parenthesis: the code style's settings say how Ecstasy code is laid out.",
        focus = "editor",
    ) {
        open(source("CardCollection.x"), 0)
        at("cards.swap(index1, index2);")
    }
    shot(
        "Reformat code",
        "Ctrl+Alt+L reformats the file, or the selection, through the language server's formatter, with the IDE's code style.",
        focus = "editor",
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
    shot(
        "Code style",
        "Settings | Editor | Code Style | Ecstasy: indents, continuation indents and the other settings the formatter follows.",
        focus = "popup",
        check = { popupText().let { expect("Continuation indent" in it) { "The page: ${it.take(300)}" } } },
        settle = 3.seconds,
        after = { closeDialogs() },
    ) {
        settings("preferences.sourceCode.Ecstasy")
    }
    restore()
    verifyRestored()
}

private fun Tour.diagnostics() {
    section("Diagnostics")
    shot(
        "Errors as you type",
        "A misspelled method is an error as soon as it is typed: the compiler checks the editor's text, not the saved file.",
        focus = "caret",
        check = {
            expect(
                within(20.seconds) { "popCrd" in diagnostics(source("Eights.x")) },
            ) { "No error: ${diagnostics(source("Eights.x"))}" }
        },
        settle = 5.seconds,
    ) {
        open(source("Eights.x"), 0)
        replace("return drawPile.popCard();", 0, "return drawPile.popCrd();")
        at("popCrd", delta = 2)
    }
    shot(
        "The error's message",
        "Ctrl+F1 shows the compiler's message where the cursor is; the scrollbar marks every problem in the file.",
        focus = "popup",
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
        focus = "popup",
        check = { expect("always matches" in diagnostics(source("Player.x"))) { "No warning: ${diagnostics(source("Player.x"))}" } },
        settle = 2.seconds,
        wait = 30.seconds,
        after = { dismiss() },
    ) {
        open(source("Player.x"), 0)
        replace("                hand.addCard(card);", 0, "                assert card.is(Card);\n                hand.addCard(card);")
        Thread.sleep(5000)
        at("card.is(Card)", delta = 7)
        action("ShowErrorDescription")
    }
    shot(
        "The Problems window",
        "The Problems window lists the file's errors and warnings, and the project's, each a click away from its code.",
        focus = "toolwindow:Problems View",
        check = { toolWindowText("Problems View").let { expect("always matches" in it) { "The problems: ${it.take(300)}" } } },
        settle = 3.seconds,
        after = {
            toolWindow("Problems View", false)
            restore()
        },
    ) {
        toolWindow("Problems View", true)
    }
    shot(
        "Quick fixes",
        "Alt+Enter on an error offers its fixes: announce doesn't exist yet, so the server offers to create it.",
        focus = "popup",
        check = { popupText().let { expect("Create private method 'announce'" in it) { "The fixes: $it" } } },
        settle = 2.seconds,
        wait = 30.seconds,
    ) {
        open(source("Eights.x"), 0)
        at("Player current = one;", delta = "Player current = one;".length)
        type("\nannounce(current);")
        Thread.sleep(3000)
        at("announce(current)", delta = 2)
        check(intentions("Create private method")) { "No Create private method fix: ${rows()}" }
    }
    shot(
        "Create a method",
        "The fix writes the method, with the parameter types that the call implies, for its body to be filled in.",
        focus = "editor",
        check = {
            expect(Regex("""announce\(Player""") in fileText(source("Eights.x"))) { "No announce method in Eights.x" }
            compilesCleanly(source("Eights.x"))
        },
        settle = 3.seconds,
        after = { restore() },
    ) {
        check(choose("Create private method")) { "No Create private method fix: ${rows()}" }
    }
    shot(
        "Add an import",
        "A type that isn't imported has a fix that adds its import, here in a test module that imports the game's types.",
        focus = "popup",
        check = { popupText().let { expect("mport" in it) { "The fixes: $it" } } },
        settle = 2.seconds,
        wait = 30.seconds,
        after = {
            dismiss()
            restore()
        },
    ) {
        open(test("DeckTest.x"), 0)
        replace("    import cardGame.Suit;\n", 0, "")
        Thread.sleep(3000)
        at("Suit.values", delta = 1)
        check(intentions("mport")) { "No import fix: ${rows()}" }
    }
    shot(
        "Generate a doc comment",
        "Alt+Enter on a declaration also offers to write its doc comment, with its parameters and its return value.",
        focus = "editor",
        check = {
            expect(Regex("""/\*\*[^/]*\*/\s*Player nextPlayer""") in fileText(source("Eights.x"))) { "No doc comment on nextPlayer" }
            compilesCleanly(source("Eights.x"))
        },
        settle = 3.seconds,
        after = { restore() },
    ) {
        open(source("Eights.x"), 0)
        at("Player nextPlayer(", delta = "Player next".length)
        check(intentions("Generate documentation comment")) { "No Generate documentation comment: ${rows()}" }
        check(choose("Generate documentation comment")) { "No Generate documentation comment: ${rows()}" }
    }
    shot(
        "Optimize imports",
        "Ctrl+Alt+O removes the imports that nothing uses and sorts the rest; an unused import is grayed until then.",
        focus = "editor",
        check = {
            expect("import cardGame.Deck;" !in fileText(test("PlayerTest.x"))) { "The unused import is still there" }
            compilesCleanly(test("PlayerTest.x"))
        },
        settle = 3.seconds,
        after = { restore() },
    ) {
        open(test("PlayerTest.x"), 0)
        replace("    import cardGame.Card;\n", 0, "    import cardGame.Card;\n    import cardGame.Deck;\n")
        Thread.sleep(4000)
        action("OptimizeImports")
    }
    restore()
    verifyRestored()
}

private fun Tour.refactoring() {
    section("Refactoring")
    shot(
        "Rename",
        "Shift+F6 renames a method: the dialog checks the new name before anything changes.",
        focus = "popup",
        check = { popupText().let { expect("drawUntilMatch" in it) { "The dialog: $it" } } },
        settle = 2.seconds,
    ) {
        open(source("Player.x"), 0)
        at("return drawForMatch(eights, prev);", delta = "return draw".length)
        action("RenameElement")
        Thread.sleep(2000)
        fillDialog("drawUntilMatch")
    }
    shot(
        "Renamed everywhere",
        "The rename changes the declaration and every use, in every file of the project, as one change that Undo reverts.",
        focus = "editor",
        check = {
            fileText(source("Player.x")).let {
                expect("drawForMatch" !in it && "conditional Card drawUntilMatch(" in it) { "Player.x was not renamed" }
            }
            compilesCleanly(source("Player.x"))
        },
        settle = 3.seconds,
        until = { fileHas(source("Player.x"), "drawUntilMatch(eights, prev)") },
        after = { restore() },
    ) {
        check(confirm("Refactor")) { "The rename dialog did not take the new name" }
    }
    shot(
        "Rename a class",
        "Renaming a class renames its file too, as Ecstasy's one type per file expects, and every use of it.",
        focus = "editor",
        check = {
            expect(fileHas(source("Tally.x"), "service Tally") && "Scoreboard" !in fileText(source("Eights.x"))) {
                "Scoreboard was not renamed to Tally everywhere"
            }
            compilesCleanly(source("Eights.x"))
        },
        settle = 3.seconds,
        wait = 30.seconds,
        after = { restore() },
    ) {
        open(source("Scoreboard.x"), 0)
        at("service Scoreboard", delta = "service Score".length)
        action("RenameElement")
        Thread.sleep(2000)
        fillDialog("Tally")
        check(confirm("Refactor")) { "The rename dialog did not take the new name" }
        check(within(60.seconds) { fileHas(source("Eights.x"), "public Tally scoreboard") }) { "Eights.x does not use Tally" }
        open(source("Eights.x"), 0)
        at("public Tally scoreboard")
    }
    shot(
        "Extract and inline",
        "Alt+Enter on a selection offers the server's refactorings: extract it to a local or a method, or inline one.",
        focus = "popup",
        check = { popupText().let { expect("Extract expression to local" in it) { "The refactorings: $it" } } },
        settle = 2.seconds,
    ) {
        open(source("Eights.x"), 0)
        select("discardPile.size() <= GameConstants.MIN_DISCARD_PILE_FOR_RESHUFFLE")
        check(intentions("Extract expression to local")) { "No Extract expression to local: ${rows()}" }
    }
    shot(
        "Extracted to a local",
        "The expression is now a local variable, named for what it is, and the condition reads it.",
        focus = "editor",
        check = {
            expect(Regex("""\n\s+\w+ \w+ = discardPile\.size\(\) <=""") in fileText(source("Eights.x"))) { "No local in reshuffle" }
            compilesCleanly(source("Eights.x"))
        },
        settle = 3.seconds,
        after = { restore() },
    ) {
        check(choose("Extract expression to local")) { "No Extract expression to local: ${rows()}" }
    }
    shot(
        "Extract a method",
        "Statements extract to a private method too: the three lines that print the hand sizes become one call.",
        focus = "editor",
        check = {
            expect(Regex("""private void \w+\(""") in fileText(source("Eights.x"))) { "No extracted method in Eights.x" }
            compilesCleanly(source("Eights.x"))
        },
        settle = 3.seconds,
        after = { restore() },
    ) {
        select(
            """
            console.print(${'$'}"{one.name} hand size: {one.hand.size()}");
                    console.print(${'$'}"{two.name} hand size: {two.hand.size()}");
                    console.print(${'$'}"Draw pile size: {drawPile.size()}");
            """.trimIndent(),
        )
        check(intentions("Extract statements to private method")) { "No Extract statements to private method: ${rows()}" }
        check(choose("Extract statements to private method")) { "No Extract statements to private method: ${rows()}" }
    }
    shot(
        "Safe delete",
        "A member that nothing uses, Hand.display, can be deleted safely: the server checks for uses first.",
        focus = "popup",
        check = { popupText().let { expect("Safely delete" in it) { "The actions: $it" } } },
        settle = 2.seconds,
        after = { dismiss() },
    ) {
        open(source("Hand.x"), 0)
        at("void display()", delta = "void dis".length)
        check(intentions("Safely delete")) { "No Safely delete: ${rows()}" }
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
        focus = "popup",
        check = { popupText().let { expect("Embedded compiler" in it) { "The page: ${it.take(300)}" } } },
        settle = 3.seconds,
        after = { closeDialogs() },
    ) {
        settings("xtc.build")
    }
    shot(
        "Build Project",
        "Ctrl+F9 builds every module with the language server's compiler, without Gradle, and reports to the Build window.",
        focus = "toolwindow:Build",
        check = {
            toolWindowText(
                "Build",
            ).let { expect("compileXtc" !in it && "extractXdk" !in it) { "Embedded mode ran Gradle: ${it.take(400)}" } }
        },
        settle = 3.seconds,
        wait = 90.seconds,
        until = { busy().isEmpty() },
    ) {
        action("CompileDirty")
        Thread.sleep(3000)
        // a build that succeeds leaves the Build window closed
        within(90.seconds) { busy().isEmpty() }
        toolWindow("Build", true)
    }
    shot(
        "A failed build",
        "A build's errors are listed with their files and lines, each a click away from its code.",
        focus = "toolwindow:Build",
        check = { toolWindowText("Build").let { expect("COMPILER-56" in it && "compileXtc" !in it) { "The build: ${it.take(400)}" } } },
        settle = 3.seconds,
        wait = 90.seconds,
        until = { busy().isEmpty() },
        after = { restore() },
    ) {
        open(source("Eights.x"), 0)
        replace("return drawPile.popCard();", 0, "return drawPile.popCrd();")
        action("CompileDirty")
        Thread.sleep(3000)
    }
    var run = 0
    shot(
        "The Run icon",
        "The Run icon in the gutter of a module with a run() method runs it; it makes the module's run configuration.",
        focus = "gutter",
    ) {
        open(file(MODULE), 0)
        run = at("module cardGame")
    }
    shot(
        "Run",
        "The game runs in the embedded execution worker, beside the language server, and its Console is the Run window.",
        focus = "toolwindow:Run",
        check = { expect("Starting Eights game." in console()) { "The console: ${console().take(300)}" } },
        settle = 2.seconds,
        wait = 90.seconds,
        until = { "Press Enter" in console() },
    ) {
        gutter(run, "Run cardGame")
    }
    shot(
        "Console input",
        "The program reads the Run window's input: each Enter plays a turn, and the Run window shows it.",
        focus = "toolwindow:Run",
        check = {
            console().let {
                expect(
                    " plays " in it || " draws " in it || " passes" in it,
                ) { "No turn played: ${it.takeLast(300)}" }
            }
        },
        settle = 2.seconds,
        wait = 30.seconds,
        until = { console().split("Press Enter").size > 3 },
    ) {
        input("\n")
        Thread.sleep(1500)
        input("\n")
    }
    shot(
        "The run configuration",
        "Run | Edit Configurations: the module, its arguments, and what it runs with: the project's mode, embedded, Gradle or xtc.",
        focus = "popup",
        check = { popupText().let { expect("cardGame" in it) { "The dialog: ${it.take(300)}" } } },
        settle = 3.seconds,
        after = {
            closeDialogs()
            stopAll()
        },
    ) {
        action("editRunConfigurations")
    }
    shot(
        "Run with Gradle",
        "In Gradle mode, the same configuration runs the XTC Gradle plugin's runXtc task, with the build's settings and XDK.",
        focus = "toolwindow:Run",
        check = { expect("Press Enter" in console()) { "The console: ${console().takeLast(300)}" } },
        settle = 2.seconds,
        wait = 180.seconds,
        until = { "Press Enter" in console() },
        after = { stopAll() },
    ) {
        buildMode("GRADLE")
        open(file(MODULE), 0)
        gutter(run, "Run cardGame")
    }
    shot(
        "The Gradle window",
        "The Gradle window lists the XTC plugin's tasks, compileXtc, runXtc and testXtc among them, to run as any Gradle task.",
        focus = "toolwindow:Gradle",
        check = { toolWindowText("Gradle").let { expect("runXtc" in it) { "The Gradle window: ${it.take(300)}" } } },
        settle = 3.seconds,
        after = { toolWindow("Gradle", false) },
    ) {
        toolWindow("Gradle", true)
    }
    shot(
        "Run with xtc",
        "Set to the command line, the configuration runs the XDK's xtc run, as a terminal would, in the same Run window.",
        focus = "toolwindow:Run",
        check = { expect("Press Enter" in console()) { "The console: ${console().takeLast(300)}" } },
        settle = 2.seconds,
        wait = 120.seconds,
        until = { "Press Enter" in console() },
        after = {
            stopAll()
            runWith("PROJECT")
            buildMode("EMBEDDED")
        },
    ) {
        runWith("COMMAND_LINE")
        action("Run")
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
                    TourBox("Stop", "Line and conditional breakpoints; stop at the program's first line."),
                    TourBox("Step", "Over, into, out, smart step into, and run to the cursor; the XDK's frames and sources."),
                    TourBox("Inspect", "Frames and fibers, variables, watches, Evaluate Expression and the debug console."),
                ),
        ),
    )
    var deck = 0
    shot(
        "A conditional breakpoint",
        "A breakpoint can have a condition: this one, in Deck's constructor, stops only when the deck adds the Queen of Spades.",
        focus = "popup",
        check = { popupText().let { expect("Rank.Queen" in it) { "The breakpoint: $it" } } },
        settle = 2.seconds,
        after = { dismiss() },
    ) {
        open(source("Deck.x"), 0)
        deck = at("addCard(new Card(suit, rank));")
        breakpoint(deck, "rank == Rank.Queen && suit == Suit.Spades")
        action("EditBreakpoint")
    }
    prepare {
        // the breakpoint's editor is a balloon, which Escape closes
        key("ESCAPE")
        dismiss()
    }
    shot(
        "Debug",
        "Shift+F9 debugs the run configuration: the program stops where the condition first holds, and the Debug window opens.",
        focus = "toolwindow:Debug",
        check = { toolWindowText("Debug").let { expect("Queen" in it && "Spades" in it) { "The variables: ${it.take(400)}" } } },
        settle = 3.seconds,
        wait = 90.seconds,
        until = { pausedAt(deck + 1) },
    ) {
        open(file(MODULE), 0)
        action("Debug")
    }
    var deal = 0
    shot(
        "Frames and variables",
        "Resumed, it stops at a line breakpoint while dealing: the frames show deal called from initializeGame, in Eights' constructor.",
        focus = "toolwindow:Debug",
        check = { toolWindowText("Debug").let { expect("initializeGame" in it) { "The frames: ${it.take(400)}" } } },
        settle = 3.seconds,
        wait = 60.seconds,
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
        focus = "toolwindow:Debug",
        check = { toolWindowText("Debug").let { expect("popCard" in it) { "The frames: ${it.take(400)}" } } },
        settle = 2.seconds,
        wait = 30.seconds,
        until = { pausedAt() && !pausedAt(deal + 1) },
    ) {
        clearBreakpoints()
        action("StepInto")
    }
    shot(
        "Step over",
        "F8 runs the current line and stops at the next one; the Variables view shows what the line changed.",
        focus = "toolwindow:Debug",
        settle = 2.seconds,
        wait = 30.seconds,
        until = { position().let { it.isNotEmpty() && it != from } },
    ) {
        from = position()
        action("StepOver")
    }
    shot(
        "Step out",
        "Shift+F8 runs to the end of popCard and stops back in deal, with the card it returned.",
        focus = "toolwindow:Debug",
        settle = 2.seconds,
        wait = 30.seconds,
        until = { position().let { it.isNotEmpty() && it != from } },
    ) {
        from = position()
        action("StepOut")
    }
    var dealAll = 0
    shot(
        "Run to cursor",
        "Alt+F9 runs to the line with the cursor, here dealAll's, as a breakpoint there would, without one.",
        focus = "toolwindow:Debug",
        settle = 2.seconds,
        wait = 30.seconds,
        until = { pausedAt(dealAll + 1) },
    ) {
        open(source("CardCollection.x"), 0)
        dealAll = at("deal(that, size());")
        action("RunToCursor")
    }
    shot(
        "Smart step into",
        "Shift+F7 on a line with several calls asks which one to step into: deal, or size.",
        focus = "popup",
        check = { popupText().let { expect("size" in it && "deal" in it) { "The targets: $it" } } },
        settle = 2.seconds,
        after = { dismiss() },
    ) {
        action("SmartStepInto")
    }
    shot(
        "Evaluate expression",
        "Alt+F8 evaluates an expression in the paused frame: the selection, size(), is the number of cards left in the deck.",
        focus = "popup",
        check = { popupText().let { expect("42" in it) { "The result: ${it.take(300)}" } } },
        settle = 3.seconds,
        after = { closeDialogs() },
    ) {
        select("size()", occurrence = 1)
        action("EvaluateExpression")
        Thread.sleep(2500)
        click("Evaluate")
    }
    shot(
        "Watches",
        "An expression added to the watches is evaluated again at every stop, beside the frame's variables.",
        focus = "toolwindow:Debug",
        check = { toolWindowText("Debug").let { expect("size()" in it) { "The watches: ${it.take(400)}" } } },
        settle = 3.seconds,
    ) {
        select("size()", occurrence = 1)
        action("Debugger.AddToWatch")
    }
    shot(
        "The program's console",
        "Resumed, the game waits for Enter: the Debug window's Console shows its output, and a line typed there is its input.",
        focus = "toolwindow:Debug",
        check = { toolWindowText("Debug").let { expect("Press Enter" in it) { "The console: ${it.takeLast(300)}" } } },
        settle = 3.seconds,
        wait = 30.seconds,
        until = { !pausedAt() },
        after = { stopAll() },
    ) {
        action("Resume")
        Thread.sleep(2000)
        debugTab("Console")
    }
    shot(
        "Debug with Gradle",
        "In Gradle mode, Debug runs runXtc with the debugger, and stops at the same breakpoints, in the same Debug window.",
        focus = "toolwindow:Debug",
        settle = 3.seconds,
        wait = 180.seconds,
        until = { pausedAt(deal + 1) },
        after = {
            stopAll()
            buildMode("EMBEDDED")
            clearBreakpoints()
        },
    ) {
        buildMode("GRADLE")
        open(source("CardCollection.x"), 0)
        breakpoint(deal, "")
        open(file(MODULE), 0)
        action("Debug")
    }
}

private fun Tour.testing() {
    section("Tests")
    card(
        "Tests",
        "xunit tests run and debug from the gutter, with the IDE's test runner, embedded or with Gradle.",
        TourCard(
            eyebrow = "TESTS",
            heading = "xunit, in the IDE's test runner",
            lead = "The same results tree, embedded or with Gradle's testXtc",
            boxes =
                listOf(
                    TourBox("Run", "A module's tests, a test, or several, from the gutter or a run configuration."),
                    TourBox("Read", "Passed, failed and skipped tests, with their output, messages and the failing line."),
                    TourBox("Debug", "Any test under the debugger, with its breakpoints."),
                ),
        ),
    )
    var module = 0
    shot(
        "Test icons",
        "The gutter has a test icon on every test module and every @Test method, from the server's view of the tests.",
        focus = "gutter",
        settle = 3.seconds,
    ) {
        open(test("DeckTest.x"), 0)
        module = at("module DeckTest")
    }
    shot(
        "Run or debug a test",
        "A click on a test icon offers to run or debug it: here every test in DeckTest.",
        focus = "popup",
        check = { popupText().let { expect("Run 'Tests of DeckTest'" in it && "Debug" in it) { "The choices: $it" } } },
        settle = 2.seconds,
    ) {
        gutter(module, "Tests of DeckTest")
    }
    shot(
        "Test results",
        "The tests run embedded, and the IDE's test runner shows each one's result, time and output.",
        focus = "toolwindow:Run",
        check = { testCounts().let { expect(it["passed"] == DECK_TESTS && it["failed"] == null) { "DeckTest: $it" } } },
        settle = 3.seconds,
        wait = 90.seconds,
        until = { runEnded() },
    ) {
        watchTests()
        check(choose("Run")) { "No Run: ${rows()}" }
        Thread.sleep(3000)
    }
    shot(
        "A failing test",
        "A failing test is red, with its assertion's message and the line that failed, a click away.",
        focus = "toolwindow:Run",
        check = { testCounts().let { expect(it["failed"] == 1 && it["passed"] == DECK_TESTS - 1) { "DeckTest: $it" } } },
        settle = 3.seconds,
        wait = 90.seconds,
        until = { runEnded() },
        after = { restore() },
    ) {
        open(test("DeckTest.x"), 0)
        replace("assert deck.size() == GameConstants.DECK_SIZE as", 0, "assert deck.size() == GameConstants.DECK_SIZE + 1 as")
        Thread.sleep(3000)
        gutter(module, "Tests of DeckTest")
        Thread.sleep(1500)
        watchTests()
        check(choose("Run")) { "No Run: ${rows()}" }
        Thread.sleep(3000)
    }
    var shuffle = 0
    shot(
        "Debug a test",
        "Debug on a test's icon runs it under the debugger: it stops at the test's breakpoint, here before the shuffle.",
        focus = "toolwindow:Debug",
        settle = 3.seconds,
        wait = 90.seconds,
        until = { pausedAt(shuffle + 1) },
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
        Thread.sleep(1500)
        check(choose("Debug")) { "No Debug: ${rows()}" }
    }
    shot(
        "Tests with Gradle",
        "In Gradle mode the same icons run the XTC plugin's testXtc, and xunit's results come back to the same test runner.",
        focus = "toolwindow:Run",
        check = { testCounts().let { expect(it["passed"] == DECK_TESTS && it["failed"] == null) { "DeckTest with Gradle: $it" } } },
        settle = 3.seconds,
        wait = 240.seconds,
        until = { runEnded() },
        after = {
            stopAll()
            buildMode("EMBEDDED")
        },
    ) {
        buildMode("GRADLE")
        open(test("DeckTest.x"), 0)
        gutter(module, "Tests of DeckTest")
        Thread.sleep(1500)
        watchTests()
        check(choose("Run")) { "No Run: ${rows()}" }
        Thread.sleep(3000)
    }
    restore()
    verifyRestored()
}

private fun Tour.settings() {
    section("Settings and the server")
    shot(
        "The language server",
        "Settings | Languages & Frameworks: the Ecstasy language service's features can each be turned off, per project.",
        focus = "popup",
        settle = 3.seconds,
        after = { closeDialogs() },
    ) {
        settings("xtc.service")
    }
    shot(
        "The compiler",
        "The compiler's page: the modules it builds, their libraries, and the reports of what it found.",
        focus = "popup",
        settle = 3.seconds,
        after = { closeDialogs() },
    ) {
        settings("xtc.compiler")
    }
    shot(
        "The server's runtime",
        "The server's runtime: the Java it runs on, its memory, and its logs, for when something goes wrong.",
        focus = "popup",
        settle = 3.seconds,
        after = { closeDialogs() },
    ) {
        settings("xtc.runtime")
    }
    shot(
        "Language servers",
        "LSP4IJ's Language Servers window shows the Ecstasy server's state, and can trace what the IDE and the server say.",
        focus = "toolwindow:Language Servers",
        check = { toolWindowText("Language Servers").let { expect("Ecstasy" in it) { "The window: ${it.take(300)}" } } },
        settle = 3.seconds,
        after = { toolWindow("Language Servers", false) },
    ) {
        toolWindow("Language Servers", true)
    }
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
                    TourBox("Breakpoints", "Function breakpoints, data breakpoints on a value's change, hit counts, and logpoints."),
                    TourBox("Debugger", "Pause, restart a frame, inline values, hex display, paged values, and loaded sources."),
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
