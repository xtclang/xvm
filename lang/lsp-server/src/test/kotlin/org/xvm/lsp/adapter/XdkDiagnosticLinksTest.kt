package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkRename
import org.xvm.lsp.model.Diagnostic
import org.xvm.lsp.model.Location
import java.nio.file.Path

/**
 * Diagnostics carry what editors need beyond the message: quick fixes name the diagnostic they resolve, and
 * code the compiler proves can never run is tagged so editors may fade it.
 */
class XdkDiagnosticLinksTest {
    @TempDir lateinit var directory: Path

    private fun <T> compiled(
        text: String,
        check: (XdkAdapter, String, List<Diagnostic>) -> T,
    ): T {
        CompilerTestSupport.configure()
        val file =
            directory
                .resolve("Links.x")
                .toFile()
                .apply { writeText(text) }
                .canonicalFile
        val uri = file.toURI().toString()
        return XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            check(adapter, uri, adapter.compile(uri, text).diagnostics)
        }
    }

    @Test
    fun `a quick fix names the diagnostic it resolves and no other`() {
        val marked =
            """
            module Links {
                void run() {
                    §missing();
                }

                Int other() = 1;
            }
            """.trimIndent()
        val text = marked.replace("§", "")
        compiled(text) { adapter, uri, diagnostics ->
            val at = XdkRename.position(text, marked.indexOf('§'))
            val unresolved = diagnostics.single { it.location.startLine == at.line }
            // an editor passes every diagnostic near the request; one on another line is not this fix's
            val unrelated = Diagnostic.error(Location(uri, 5, 4, 5, 9), "elsewhere")
            val fix =
                adapter
                    .getCodeActions(uri, Range(at, at), diagnostics + unrelated)
                    .single { it.title == "Create private method 'missing'" }
            assertThat(fix.diagnostics).containsExactly(unresolved)
        }
    }

    @Test
    fun `an operand a constant short-circuits away is tagged unnecessary`() {
        compiled(
            """
            module Links {
                Boolean check(Boolean flag) {
                    return True || flag;
                }
            }
            """.trimIndent(),
        ) { _, _, diagnostics ->
            val unreachable = diagnostics.single { it.code == "COMPILER-208" }
            assertThat(unreachable.tags).containsExactly(Diagnostic.Tag.UNNECESSARY)
            assertThat(diagnostics.filter { it !== unreachable }).allMatch { it.tags.isEmpty() }
        }
    }

    @Test
    fun `a catch an earlier clause already catches is tagged unnecessary`() {
        compiled(
            """
            module Links {
                void run() {
                    try {
                        TODO();
                    } catch (Exception e) {
                    } catch (IllegalState e) {
                    }
                }
            }
            """.trimIndent(),
        ) { _, _, diagnostics ->
            val caught = diagnostics.single { it.code == "COMPILER-118" }
            assertThat(caught.tags).containsExactly(Diagnostic.Tag.UNNECESSARY)
        }
    }

    @Test
    fun `other compiler errors carry no tags`() {
        compiled(
            """
            module Links {
                Int value() = "text";
            }
            """.trimIndent(),
        ) { _, _, diagnostics ->
            assertThat(diagnostics).isNotEmpty().allMatch { it.tags.isEmpty() }
        }
    }
}
