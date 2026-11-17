package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.compiler.Source
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkDependency
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import org.xvm.lsp.adapter.xdk.toDependency
import java.nio.file.Files
import java.nio.file.Path

/**
 * Hover shows a declaration's doc comment wherever it is used, also in another file of the module, and in a library,
 * whose compiled module keeps no doc comments, from its source.
 */
class XdkHoverDocumentationTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `hover shows the doc comment of a property or a method declared in another file of the module`() {
        CompilerTestSupport.configure()
        val module = write("Docs.x", MODULE)
        val rules = write("Docs/Rules.x", RULES)
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceSourceModules(listOf(XdkSourceModule("Docs", module)))
            assertThat(adapter.compile(module, MODULE).success).isTrue()

            assertThat(adapter.getHoverInfo(module, 2, MODULE.lines()[2].indexOf("HAND_SIZE") + 2))
                .contains("Int HAND_SIZE", "The number of cards dealt to each player.")
            assertThat(adapter.getHoverInfo(module, 3, MODULE.lines()[3].indexOf("matches") + 2))
                .contains("matches", "Whether two ranks match.")

            // the property's own declaration, in its own file
            assertThat(adapter.compile(rules, RULES).success).isTrue()
            assertThat(adapter.getHoverInfo(rules, 7, RULES.lines()[7].indexOf("HAND_SIZE") + 2))
                .contains("The number of cards dealt to each player.")
        }
    }

    @Test
    fun `hover shows the doc comment of an XDK type, method or property, from the XDK's source`() {
        CompilerTestSupport.configure()
        val module = write("Scores.x", SCORES)
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceSourceModules(listOf(XdkSourceModule("Scores", module)))
            assertThat(adapter.compile(module, SCORES).success).isTrue()

            fun hover(
                line: Int,
                name: String,
            ) = adapter.getHoverInfo(module, line, SCORES.lines()[line].indexOf(name) + 2)

            // lib_ecstasy's maps/HashMap.x and maps/Map.x
            assertThat(hover(2, "HashMap"))
                .contains("class HashMap", "`HashMap` is a hashed implementation of the [Map] interface.")
            assertThat(hover(4, "getOrDefault"))
                .contains("getOrDefault", "Obtain the value associated with the specified key, or if the key does not already exist in")
            assertThat(hover(6, "size"))
                .contains("size", "The size of the `Map`, which is the number of [Entry] objects (key/value pairs) in the `Map`.")
        }
    }

    @Test
    fun `hover shows the doc comment of a library declaration from the library's attached source`() {
        CompilerTestSupport.configure()
        val errors = ErrorList()
        val compilation = EmbeddingSupport.instance().compileModule(Source(LIBRARY, "Library.x"), null, errors)
        assertThat(compilation.succeeded()).describedAs(errors.errors.toString()).isTrue()
        val sources = Files.createDirectories(directory.resolve("sources"))
        Files.writeString(sources.resolve("Library.x"), LIBRARY)
        val library = XdkDependency.fromBinary(compilation.toDependency().bytes()).withSources(listOf(sources.toFile()))
        val uri = directory.resolve("Consumer.x").toUri().toString()
        XdkAdapter().use { adapter ->
            adapter.replaceDependencies(listOf(library))
            assertThat(adapter.compile(uri, CONSUMER).success).isTrue()
            assertThat(adapter.getHoverInfo(uri, 0, CONSUMER.indexOf("Dice") + 2))
                .contains("class Dice", "A pair of dice.")
            assertThat(adapter.getHoverInfo(uri, 0, CONSUMER.indexOf("roll") + 2))
                .contains("roll", "The total of a throw of the dice.")
            assertThat(adapter.getHoverInfo(uri, 0, CONSUMER.indexOf("SIDES") + 2))
                .contains("SIDES", "The number of sides of each die.")
        }
    }

    private fun write(
        path: String,
        text: String,
    ): String {
        val file = directory.resolve(path)
        Files.createDirectories(file.parent)
        Files.writeString(file, text)
        return file
            .toFile()
            .canonicalFile
            .toURI()
            .toString()
    }

    private companion object {
        val MODULE =
            """
            module Docs {
                void run() {
                    Int size = Rules.HAND_SIZE;
                    Boolean same = Rules.matches(1, 1);
                }
            }
            """.trimIndent()

        val RULES =
            """
            /**
             * The game's rules.
             */
            class Rules {
                /**
                 * The number of cards dealt to each player.
                 */
                static Int HAND_SIZE = 5;

                /**
                 * Whether two ranks match.
                 */
                static Boolean matches(Int a, Int b) = a == b;
            }
            """.trimIndent()

        val SCORES =
            """
            module Scores {
                class Board {
                    private Map<String, Int> wins = new HashMap();

                    Int winsOf(String player) = wins.getOrDefault(player, 0);

                    Int players() = wins.size;
                }
            }
            """.trimIndent()

        val LIBRARY =
            """
            module Library {
                /**
                 * A pair of dice.
                 */
                class Dice {
                    /**
                     * The number of sides of each die.
                     */
                    static Int SIDES = 6;

                    /**
                     * The total of a throw of the dice.
                     */
                    static Int roll() = 7;
                }
            }
            """.trimIndent()

        val CONSUMER = "module Consumer { package lib import Library; Int run() = lib.Dice.roll() + lib.Dice.SIDES; }"
    }
}
