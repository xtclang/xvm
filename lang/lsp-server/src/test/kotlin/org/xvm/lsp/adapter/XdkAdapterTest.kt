package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.model.Diagnostic
import org.xvm.lsp.model.SymbolInfo
import java.util.concurrent.CancellationException

/**
 * What an editor is told when the XTC compiler itself analyses a document.
 *
 * Gradle supplies compiled XDK modules as declared test inputs. Missing libraries fail the
 * suite instead of silently skipping compiler coverage.
 */
class XdkAdapterTest {
    private fun adapter(): XdkAdapter {
        CompilerTestSupport.configure()
        return XdkAdapter()
    }

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
     * Concurrent edits either produce an analysis of their own text or are explicitly cancelled.
     */
    @Test
    fun `concurrent edits are answered or cancelled`() {
        adapter().use { xdk ->
            val uri = "file:///Racing.x"
            val results =
                (1..6)
                    .toList()
                    .parallelStream()
                    .map { n ->
                        try {
                            xdk.compile(uri, "module Racing { void run() { Int x$n = ; } }")
                        } catch (_: CancellationException) {
                            null
                        }
                    }.toList()

            assertThat(results).hasSize(6)
            assertThat(results.filterNotNull()).isNotEmpty().allSatisfy { r ->
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

    // ----- what the tree can answer, without knowing what anything means ----------------------

    /**
     * Folding is pure shape: a block that spans more than one line can be collapsed.
     */
    @Test
    fun `blocks and declarations spanning more than a line can be folded`() {
        adapter().use { xdk ->
            xdk.compile("file:///Uses.x", USES)

            val folds = xdk.getFoldingRanges("file:///Uses.x")

            assertThat(folds).isNotEmpty()
            assertThat(folds).allSatisfy { assertThat(it.endLine).isGreaterThan(it.startLine) }
            assertThat(folds.map { it.startLine }).`as`("the module body folds").contains(0)
        }
    }

    /**
     * Expanding a selection is walking out through the tree, so each range has to contain the one
     * it came from. Asserting the containment rather than specific spans keeps this a test of the
     * walk and not of where the parser happens to put a node boundary.
     */
    @Test
    fun `a selection expands outward through enclosing nodes`() {
        adapter().use { xdk ->
            xdk.compile("file:///Uses.x", USES)

            val ranges = xdk.getSelectionRanges("file:///Uses.x", listOf(Position(3, 20)))

            val innermost = ranges.single()
            var range: SelectionRange? = innermost
            var steps = 0
            while (range?.parent != null) {
                val parent = range.parent!!
                assertThat(parent.range.start.line).isLessThanOrEqualTo(range.range.start.line)
                assertThat(parent.range.end.line).isGreaterThanOrEqualTo(range.range.end.line)
                range = parent
                steps++
            }
            assertThat(steps).`as`("more than one level to expand through").isGreaterThan(1)
        }
    }

    /**
     * Highlight the declaration and both uses of the resolved local.
     */
    @Test
    fun `the other places the same name is written are highlighted`() {
        adapter().use { xdk ->
            xdk.compile("file:///Uses.x", USES)

            val highlights = xdk.getDocumentHighlights("file:///Uses.x", 3, 20)

            // the declaration and both uses: a variable's name is a name in the tree too
            assertThat(highlights).hasSize(3)
            assertThat(highlights.map { it.range.start.line }).containsExactly(2, 3, 3)

            assertThat(xdk.getDocumentHighlights("file:///Uses.x", 0, 0))
                .`as`("not on a name")
                .isEmpty()
        }
    }

    /**
     * The half of hover that no grammar can supply: what the compiler decided the expression
     * under the cursor is.
     */
    @Test
    fun `hover says what the expression under the cursor resolved to`() {
        adapter().use { xdk ->
            xdk.compile("file:///Uses.x", USES)

            val hover = xdk.getHoverInfo("file:///Uses.x", 3, 20)

            assertThat(hover).`as`("the type of count").contains("Int")
        }
    }

    /**
     * Across the documents this server has compiled - which is the ones that have been opened,
     * not the whole project.
     */
    @Test
    fun `symbols can be searched across compiled documents`() {
        adapter().use { xdk ->
            xdk.compile("file:///Outline.x", OUTLINE)
            xdk.compile("file:///Uses.x", USES)

            assertThat(xdk.findWorkspaceSymbols("dist").map { it.name })
                .`as`("nested in one document")
                .contains("distance")
            assertThat(xdk.findWorkspaceSymbols("Uses").map { it.name }).contains("Uses")
            assertThat(xdk.findWorkspaceSymbols("nothing-called-this")).isEmpty()
        }
    }

    // ----- what a name resolved to ------------------------------------------------------------

    /**
     * Narrowed uses share the original register with the source declaration.
     */
    @Test
    fun `a local variable's declaration is found from a use of it`() {
        adapter().use { xdk ->
            xdk.compile("file:///Resolve.x", RESOLVE)

            val at = xdk.findDefinition("file:///Resolve.x", 14, 20)

            assertThat(at?.startLine).`as`("Int count = 1").isEqualTo(13)
            assertThat(at?.uri).isEqualTo("file:///Resolve.x")
        }
    }

    /**
     * A class declared in this document. Nothing is written as a name where a class is declared,
     * so this goes through the declaration's component identity rather than through an
     * occurrence.
     */
    @Test
    fun `a type's declaration is found from a use of it`() {
        adapter().use { xdk ->
            xdk.compile("file:///Resolve.x", RESOLVE)

            val at = xdk.findDefinition("file:///Resolve.x", 15, 8)

            assertThat(at?.startLine).`as`("const Point(Int x, Int y)").isEqualTo(1)
        }
    }

    /**
     * The point of resolution over text search: two properties spelled the same, on different
     * classes, are different things.
     */
    @Test
    fun `references follow what the name means, not how it is spelled`() {
        adapter().use { xdk ->
            xdk.compile("file:///Resolve.x", RESOLVE)

            val holders = xdk.findReferences("file:///Resolve.x", 9, 19, includeDeclaration = true)

            assertThat(holders.map { it.startLine })
                .`as`("Holder.x, declared and used - not Point.x on line 3")
                .containsExactlyInAnyOrder(7, 9)
        }
    }

    /**
     * A method call. The name in one resolves to nothing by itself - which method `sum` is
     * depends on what it is called on and with what - so this goes through the invocation, which
     * is where the compiler decided it.
     */
    @Test
    fun `a method's declaration is found from a call to it`() {
        adapter().use { xdk ->
            xdk.compile("file:///Resolve.x", RESOLVE)

            val at = xdk.findDefinition("file:///Resolve.x", 16, 18)

            assertThat(at?.startLine).`as`("Int sum()").isEqualTo(2)
        }
    }

    /**
     * Declared in the core library, which this document has no place to point at. Saying nothing
     * is the honest answer; jumping to another mention of Int in the same file would not be.
     */
    @Test
    fun `a name declared in another module has nothing here to point at`() {
        adapter().use { xdk ->
            xdk.compile("file:///Resolve.x", RESOLVE)

            assertThat(xdk.findDefinition("file:///Resolve.x", 13, 8)).`as`("Int").isNull()
            assertThat(xdk.findDefinition("file:///Resolve.x", 0, 0)).`as`("not on anything").isNull()
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

        /** `count` is written twice on the fourth line, both times as a use. */
        val USES =
            """
            module Uses {
                void run() {
                    Int count = 1;
                    Int total = count + count;
                }
            }
            """.trimIndent()

        /**
         * Two properties called `x` on different classes, a local used twice, and a type used
         * where it is not declared. Line numbers are asserted, so the shape matters.
         */
        val RESOLVE =
            """
            module Resolve {
                const Point(Int x, Int y) {
                    Int sum() {
                        return x + y;
                    }
                }
                class Holder {
                    Int x = 5;
                    Int get() {
                        return x;
                    }
                }
                void run() {
                    Int count = 1;
                    Int total = count + count;
                    Point p = new Point(1, 2);
                    Int s = p.sum();
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
