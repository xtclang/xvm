package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.model.Diagnostic
import java.io.File

/**
 * What an editor is told when the XTC compiler itself analyses a document.
 *
 * These need an XDK to resolve the core library against, so they skip without one rather than
 * failing - the same condition the adapter reports as a diagnostic at runtime. Point XDK_HOME at
 * a built distribution (`xdk/build/install/xdk`) to run them.
 */
class XdkAdapterTest {
    private fun adapter(): XdkAdapter {
        assumeTrue(xdkHome() != null, "no XDK_HOME; skipping compiler-backed diagnostics")
        return XdkAdapter()
    }

    private fun xdkHome(): String? = System.getenv("XDK_HOME")?.takeIf { File(it, "lib").isDirectory }

    @Test
    fun `a clean module produces no diagnostics`() {
        adapter().use { xdk ->
            val result = xdk.compile("file:///Clean.x", CLEAN)

            assertThat(result.diagnostics).isEmpty()
            assertThat(result.success).isTrue()
        }
    }

    @Test
    fun `a syntax error is reported where it is, with the compiler's own code`() {
        adapter().use { xdk ->
            val result = xdk.compile("file:///Broken.x", MISSING_SEMICOLON)

            assertThat(result.diagnostics).isNotEmpty()
            assertThat(result.success).isFalse()

            val first = result.diagnostics.first()
            assertThat(first.severity).isEqualTo(Diagnostic.Severity.ERROR)
            assertThat(first.code).isNotNull()
            assertThat(first.source).isEqualTo("xtc")
            assertThat(first.location.uri).isEqualTo("file:///Broken.x")
            // the fault is on the third line of the document, not at its start
            assertThat(first.location.startLine).isEqualTo(2)
        }
    }

    /**
     * The point of using the compiler rather than a grammar: an error that is not a syntax error.
     */
    @Test
    fun `an unresolvable name is reported, which a grammar could not find`() {
        adapter().use { xdk ->
            val result = xdk.compile("file:///Semantic.x", UNRESOLVABLE_NAME)

            assertThat(result.diagnostics).isNotEmpty()
            assertThat(result.diagnostics.map { it.message })
                .anySatisfy { assertThat(it).contains("NoSuchTypeAnywhere") }
        }
    }

    /**
     * Every diagnostic belongs to the document it came from and is placeable in it, which is all a
     * problem view needs.
     */
    @Test
    fun `every diagnostic is placed in the document it came from`() {
        adapter().use { xdk ->
            val result = xdk.compile("file:///Broken.x", MISSING_SEMICOLON)

            assertThat(result.diagnostics).allSatisfy { d ->
                assertThat(d.location.uri).isEqualTo("file:///Broken.x")
                assertThat(d.location.startLine).isGreaterThanOrEqualTo(0)
                assertThat(d.location.endLine).isGreaterThanOrEqualTo(d.location.startLine)
                assertThat(d.message).isNotBlank()
            }
        }
    }

    /**
     * Compilations are serialised, so concurrent requests are answered correctly rather than
     * racing through a compiler that was never built for two at once.
     */
    @Test
    fun `concurrent requests are answered one at a time`() {
        adapter().use { xdk ->
            val results =
                (1..8)
                    .toList()
                    .parallelStream()
                    .map { n ->
                        xdk.compile("file:///Doc$n.x", MISSING_SEMICOLON)
                    }.toList()

            assertThat(results).hasSize(8)
            results.forEachIndexed { i, r ->
                assertThat(r.uri).isEqualTo("file:///Doc${i + 1}.x")
                assertThat(r.diagnostics).isNotEmpty()
                assertThat(r.diagnostics).allSatisfy {
                    assertThat(it.location.uri).isEqualTo("file:///Doc${i + 1}.x")
                }
            }
        }
    }

    private companion object {
        val CLEAN =
            """
            module Clean {
                void run() {
                    @Inject Console console;
                    console.print("hello");
                }
            }
            """.trimIndent()

        val MISSING_SEMICOLON =
            """
            module Broken {
                void run() {
                    @Inject Console console
                    console.print("no semicolon above");
                }
            }
            """.trimIndent()

        val UNRESOLVABLE_NAME =
            """
            module Semantic {
                void run() {
                    NoSuchTypeAnywhere x = 1;
                }
            }
            """.trimIndent()
    }
}
