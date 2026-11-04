package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import java.nio.file.Files
import java.nio.file.Path

/** Hover shows a declaration's doc comment wherever it is used, also in another file of the module. */
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
    }
}
