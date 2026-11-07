package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.lsp.adapter.xdk.SemanticModel
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkRename
import org.xvm.lsp.awaited
import java.io.File
import java.nio.file.Path

/**
 * Checks safe deletion of unused declarations: private ones within their source, public and protected ones within the
 * configured modules. Surviving references, entry points, overriding members, initialization effects and comment loss
 * must prevent removal despite an apparently unused local view.
 */
class XdkSafeDeleteTest {
    @TempDir lateinit var directory: Path

    @ParameterizedTest
    @ValueSource(
        strings = [
            "private Int §unused() { return 1; }",
            "private Int §unused(Int input) { return input + kept(); }",
            "private static Int §unused = 3;",
        ],
    )
    fun `unused private methods and constants leave every surviving binding intact`(member: String) {
        query(member, "", expected = true)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "Int §unused() { return 1; }",
            "protected Int §unused() { return 1; }",
            "private Int §unused = kept();",
            "private static Int §unused = kept();",
            "private Int §unused() { /* keep this explanation */ return 1; }",
            // a test runner's entry point
            "class Box { @Test Int §unused() { return 1; } }",
        ],
    )
    fun `module methods, which can be entry points, initialization and comment loss refuse removal`(member: String) {
        query(member, "", expected = false)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "class Box { Int §unused() { return 1; } }",
            "class Box { protected Int §unused(Int input) { return input + kept(); } }",
            // the annotation is the body's, not the method's
            "class Box { Int §unused() { @Inject Console console; console.print(kept()); return 1; } }",
            "static Int §unused = 3;",
        ],
    )
    fun `unused public and protected members are removed, naming the modules that were checked`(member: String) {
        query(member, "", expected = true, title = "Safely delete member 'unused', unused in Delete")
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            // calls through the interface reach the implementation
            "interface Shape { Int unused(); } class Square implements Shape { @Override Int §unused() { return 4; } }",
            // the subclass's method would override nothing
            "class Base { Int §unused() { return 1; } } class Sub extends Base { @Override Int unused() { return 2; } }",
        ],
    )
    fun `a method that implements, overrides or is overridden is not removed`(member: String) {
        query(member, "", expected = false)
    }

    @Test
    fun `referenced method is not removed`() {
        query("private Int §unused() { return 1; }", "Int caller() { return unused(); }", expected = false)
    }

    @Test
    fun `public member with a closed companion consumer is refused`() {
        val consumer =
            directory.resolve("Delete/Nested.x").toFile().apply {
                parentFile.mkdirs()
                writeText("class Nested { Int read() { return Delete.unused(); } }")
            }
        query("static Int §unused() { return 1; }", "", expected = false)
        assertThat(consumer.readText()).contains("return Delete.unused();")
    }

    @Test
    fun `broken configured neighbor refuses the entire proposal`() {
        directory.resolve("Broken.x").toFile().writeText("module Broken { Missing value; }")
        query("private Int §unused() { return 1; }", "", expected = false)
    }

    @Test
    fun `the card game's unused public display is removed, as the graph compiles from nothing without it`() {
        val root = cardGame(directory.resolve("game"))
        val hand = root.resolve(HAND)
        val text = hand.readText()
        val (actions, compiled) = displayActions(root)
        println("module compiles of the request: $compiled")
        // the removal changes the game module's API, so the proof compiles the modules that depend on it too
        assertThat(compiled).isEqualTo(CodeActionCompiles(CARD_GAME, CARD_GAME, cachedBase = false))
        assertThat(actions).hasSize(1)
        assertThat(actions.single().title).startsWith("Safely delete member 'display', unused in ")
        val edit = requireNotNull(actions.single().edit)
        val change = edit.changes.getValue(hand.toURI().toString()).single()

        fun offset(at: Position) = requireNotNull(XdkRename.offset(text, SemanticModel.Position(at.line, at.column)))
        val changed = text.replaceRange(offset(change.range.start), offset(change.range.end), change.newText)
        // display's lines go, and the private heading after them stays
        val display = text.indexOf("    void display()")
        assertThat(changed).isEqualTo(text.removeRange(display, text.indexOf("\n    }\n", display) + "\n    }\n".length))

        // the graph with the removal, compiled from nothing
        val removed = cardGame(directory.resolve("removed")).also { it.resolve(HAND).writeText(changed) }
        CompilerTestSupport.configure()
        val errors =
            XdkAdapter().use { adapter ->
                adapter.initializeWorkspace(listOf(removed.path))
                adapter.getCodeActions(removed.resolve(HAND).toURI().toString(), Range(DISPLAY, DISPLAY), emptyList())
                requireNotNull(adapter.codeActionBase()).errors
            }
        assertThat(errors).hasSize(CARD_GAME)
        assertThat(errors.values).allMatch { it.isEmpty() }
    }

    @Test
    fun `the card game's display is not removed while a test module calls it`() {
        val root = cardGame(directory.resolve("game"))
        val test = root.resolve("src/test/x/HandTest.x")
        test.writeText(
            test.readText().replaceFirst(
                "        assert hand.size() == 0 as \"New hand should be empty\";\n",
                "        assert hand.size() == 0 as \"New hand should be empty\";\n        hand.display();\n",
            ),
        )
        assertThat(test.readText()).contains("hand.display();")
        val (actions, compiled) = displayActions(root)
        assertThat(actions).isEmpty()
        // refused before any proof
        assertThat(compiled.proofs).isZero()
    }

    /**
     * The Safe delete actions at the name of the card game's Hand.display, as an editor asks for every kind, and the
     * module compiles of their request.
     */
    private fun displayActions(root: File): Pair<List<CodeAction>, CodeActionCompiles> {
        CompilerTestSupport.configure()
        return XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(root.path))
            val hand = root.resolve(HAND)
            val uri = hand.toURI().toString()
            assertThat(adapter.compile(uri, hand.readText()).diagnostics).isEmpty()
            var compiled: CodeActionCompiles? = null
            val actions =
                adapter
                    .getCodeActionsAsync(uri, Range(DISPLAY, DISPLAY), emptyList(), CodeAction.CodeActionKind.entries.toSet()) {
                        compiled = it
                    }.awaited()
            actions.filter { it.title.startsWith("Safely delete") } to requireNotNull(compiled)
        }
    }

    /** The tour's card game, its game module and the seven test modules that depend on it, in [target]. */
    private fun cardGame(target: Path): File =
        target
            .toFile()
            .also {
                File(System.getProperty("xtc.composite.root"), "lang/test-fixtures/tour/cardGame").copyRecursively(it)
            }.canonicalFile

    private fun query(
        member: String,
        consumer: String,
        expected: Boolean,
        title: String? = null,
    ) {
        CompilerTestSupport.configure()
        val marked =
            """
            module Delete {
                $member
                static Int kept() { return 2; }
                Int run() { return kept(); }
                $consumer
            }
            """.trimIndent()
        val text = marked.replace("§", "")
        val file =
            directory
                .resolve("Delete.x")
                .toFile()
                .canonicalFile
                .apply { writeText(text) }
        val uri = file.toURI().toString()
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            assertThat(adapter.compile(uri, text).diagnostics).isEmpty()
            val at = XdkRename.position(text, marked.indexOf('§'))
            val actions = adapter.getCodeActions(uri, Range(at, at), emptyList()).filter { it.title.startsWith("Safely delete") }
            if (!expected) {
                assertThat(actions).isEmpty()
            } else {
                assertThat(actions).hasSize(1)
                title?.let { assertThat(actions.single().title).isEqualTo(it) }
                val edit = requireNotNull(actions.single().edit)
                assertThat(edit.versioned).isTrue()
                val change = edit.changes.getValue(uri).single()

                fun offset(at: Position) = requireNotNull(XdkRename.offset(text, SemanticModel.Position(at.line, at.column)))
                val changed = text.replaceRange(offset(change.range.start), offset(change.range.end), change.newText)
                assertThat(changed).doesNotContain("unused").contains("return kept();")
                assertThat(adapter.compile(uri, changed).diagnostics).isEmpty()
            }
            assertThat(file.readText()).isEqualTo(text)
        }
    }

    private companion object {
        const val HAND = "src/main/x/cardGame/Hand.x"

        /** In the name of Hand.display, as the tour places the caret. */
        val DISPLAY = Position(5, 12)

        /** The card game's modules. */
        const val CARD_GAME = 8
    }
}
