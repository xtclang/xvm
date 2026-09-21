package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.model.Diagnostic
import org.xvm.lsp.model.SymbolInfo
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

    /**
     * The rule cancellation turns on, tested as a decision rather than through a race: an edit is
     * stale once a newer one has been recorded for the same document.
     *
     * A race is not something a test can make happen on demand - whether two compilations overlap
     * depends on the scheduler, the core count and what else the machine is doing - so asserting
     * "at least one of these was superseded" would pass on a busy laptop and fail on a single-core
     * agent. What is deterministic is what the adapter does once it knows.
     */
    @Test
    fun `an edit is stale once a newer one arrives for the same document`() {
        XdkAdapter().use { xdk ->
            val first = xdk.edited("file:///A.x")
            assertThat(xdk.isStale("file:///A.x", first)).isFalse()

            val second = xdk.edited("file:///A.x")
            assertThat(xdk.isStale("file:///A.x", first)).`as`("superseded").isTrue()
            assertThat(xdk.isStale("file:///A.x", second)).`as`("the newest one is not").isFalse()

            // and one document's edits say nothing about another's
            val other = xdk.edited("file:///B.x")
            assertThat(xdk.isStale("file:///B.x", other)).isFalse()
            assertThat(xdk.isStale("file:///A.x", second)).isFalse()
        }
    }

    /**
     * Concurrent requests for one document all get an answer, and every answer belongs to that
     * document. How many of them are superseded is up to the scheduler, so it is not asserted.
     */
    @Test
    fun `concurrent edits of one document are all answered`() {
        adapter().use { xdk ->
            val uri = "file:///Racing.x"
            val results =
                (1..6)
                    .toList()
                    .parallelStream()
                    .map { n ->
                        xdk.compile(uri, "module Racing { void run() { Int x$n = ; } }")
                    }.toList()

            assertThat(results).hasSize(6)
            assertThat(results).allSatisfy { r ->
                assertThat(r.uri).isEqualTo(uri)
                assertThat(r.diagnostics).allSatisfy { assertThat(it.location.uri).isEqualTo(uri) }
            }
        }
    }

    /**
     * The outline an editor draws. Symbols have to come from the AST rather than the compiled
     * structures, because a ClassStructure knows its name, kind and members and nothing about
     * where it was written - and a symbol you cannot point at is no use to an editor.
     */
    @Test
    fun `declarations are reported with the place they were written`() {
        adapter().use { xdk ->
            val result = xdk.compile("file:///Outline.x", OUTLINE)

            val module = result.symbols.single()
            assertThat(module.name).isEqualTo("Outline")
            assertThat(module.kind).isEqualTo(SymbolInfo.SymbolKind.MODULE)

            val names = module.children.map { it.name }
            assertThat(names).contains("Point", "helper")

            val point = module.children.single { it.name == "Point" }
            assertThat(point.kind).isEqualTo(SymbolInfo.SymbolKind.CONST)
            assertThat(point.location.uri).isEqualTo("file:///Outline.x")
            assertThat(point.location.startLine).`as`("Point is declared on the second line").isEqualTo(1)
            assertThat(point.children.map { it.name }).contains("distance")
        }
    }

    /**
     * And the cursor can be placed in one. This is what every request phrased as "the thing I am
     * pointing at" needs underneath it.
     */
    @Test
    fun `the innermost declaration containing a position is found`() {
        adapter().use { xdk ->
            xdk.compile("file:///Outline.x", OUTLINE)

            val inner = xdk.findSymbolAt("file:///Outline.x", 2, 20)
            assertThat(inner?.name).`as`("inside Point.distance").isEqualTo("distance")

            val outer = xdk.findSymbolAt("file:///Outline.x", 1, 10)
            assertThat(outer?.name).`as`("on Point itself").isEqualTo("Point")

            assertThat(xdk.findSymbolAt("file:///Nothing.x", 0, 0))
                .`as`("a document nobody compiled")
                .isNull()
        }
    }

    /**
     * A request that should not recompile gets the last analysis instead.
     */
    @Test
    fun `the cached analysis is handed back without recompiling`() {
        adapter().use { xdk ->
            assertThat(xdk.getCachedResult("file:///Outline.x")).isNull()

            val fresh = xdk.compile("file:///Outline.x", OUTLINE)
            val cached = xdk.getCachedResult("file:///Outline.x")

            assertThat(cached).isNotNull()
            assertThat(cached!!.symbols.map { it.name }).isEqualTo(fresh.symbols.map { it.name })
        }
    }

    /**
     * A warning about a type is produced when the type is laid out and again whenever a later
     * stage asks for that type, so the same warning reaches the listener more than once. An
     * editor must not show it twice, which is why the adapter collects through the same
     * ErrorList the compiler's own front end uses rather than keeping everything it hears.
     */
    @Test
    fun `a warning heard twice is shown once`() {
        adapter().use { xdk ->
            val result = xdk.compile("file:///Dup.x", DUPLICATE_ANNOTATION)

            val warnings = result.diagnostics.filter { it.code == "VERIFY-75" }
            assertThat(warnings).`as`("the annotation warning, once").hasSize(1)
            assertThat(warnings.single().severity).isEqualTo(Diagnostic.Severity.WARNING)
            assertThat(warnings.single().message).contains("Atomic", "duplicates")
        }
    }

    private companion object {
        val OUTLINE =
            """
            module Outline {
                const Point(Int x, Int y) {
                    Int distance() { return x + y; }
                }
                void helper() {}
            }
            """.trimIndent()

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

        /** Redeclares an annotation the base property already has: VERIFY-75, a warning. */
        val DUPLICATE_ANNOTATION =
            """
            module DupAnno {
                class Base {
                    @Atomic Int x = 1;
                }
                class Derived extends Base {
                    @Atomic @Override Int x = 2;
                }
            }
            """.trimIndent()
    }
}
