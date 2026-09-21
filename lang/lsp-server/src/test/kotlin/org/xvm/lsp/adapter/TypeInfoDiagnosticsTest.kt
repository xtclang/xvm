package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.asm.ErrorList.UNLIMITED
import org.xvm.asm.constants.ClassConstant
import org.xvm.asm.constants.TypeConstant
import org.xvm.compiler.Source
import java.io.File

/**
 * Diagnostics raised while a TypeInfo is being assembled reach the listener the caller supplied,
 * and a later caller that gets the memoized TypeInfo is told the same thing.
 *
 * These live here rather than in javatools because they need a compiled core library, and
 * javatools is what compiles it - a test there would either skip on a clean build or pass only on
 * the leftovers of a previous one. This module is downstream of the compiler and already requires
 * an installed XDK, so the dependency is honest. Without one, these skip.
 *
 * The replay is the part worth pinning. A TypeInfo is built once and memoized, so which caller
 * triggers the build is an accident of order: if a speculative one did, a later caller that
 * actually cared would hear nothing at all unless the diagnostics were kept and replayed.
 */
class TypeInfoDiagnosticsTest {
    @Test
    fun `an unmatched Override is reported while the TypeInfo is assembled`() {
        val errs = ErrorList(UNLIMITED)
        compile(OVERRIDE_WITH_NO_SUPER, errs)

        assertThat(errs.getErrors().map { it.code })
            .`as`("a class whose @Override has nothing to override: %s", errs.getErrors())
            .contains("VERIFY-70")
    }

    @Test
    fun `a clean type reports nothing`() {
        val errs = ErrorList(UNLIMITED)
        compile(CLEAN, errs)

        assertThat(errs.getErrors()).isEmpty()
    }

    /**
     * The memoized result carries what building it said. The first caller here is the compilation
     * itself; the second is a later one with a listener of its own, and must still be told.
     *
     * This asserts that the later caller hears it, not that it heard it *from the recording*: a
     * rebuild reporting the same thing would pass too. Distinguishing them means asking whether
     * the same TypeInfo instance came back, and asking a third time on a type from a failed
     * compilation throws - MethodBody.pool() is null by then. That is worth knowing on its own,
     * and is recorded in docs/errs.md rather than asserted here.
     */
    @Test
    fun `a later caller is told what building the TypeInfo said`() {
        val errs = ErrorList(UNLIMITED)
        val result = compile(OVERRIDE_WITH_NO_SUPER, errs)

        // the compilation failed, so there is no module - but the structures it raised the
        // diagnostic against are still reachable, which is what compileModule exists for
        assertThat(result.succeeded()).isFalse()
        val pool = requireNotNull(result.pool()) { "a failed compilation still interned its types" }

        val type: TypeConstant =
            requireNotNull(
                pool.constants
                    .filterIsInstance<ClassConstant>()
                    .firstOrNull { it.name == "Foo" }
                    ?.type,
            ) { "the class the diagnostic was about should be in the pool" }

        // a later caller, with a listener of its own, is told the same thing
        val later = ErrorList(UNLIMITED)
        type.ensureTypeInfo(later)

        assertThat(later.getErrors().map { it.code })
            .`as`("what the first caller was told, heard again: %s", later.getErrors())
            .contains("VERIFY-70")
    }

    private fun compile(
        source: String,
        errs: ErrorList,
    ): EmbeddingSupport.Compilation {
        assumeTrue(xdkHome() != null, "no XDK_HOME; skipping compiler-backed diagnostics")
        return EmbeddingSupport.instance().compileModule(Source(source, "file:///Test.x"), null, errs)
    }

    private fun xdkHome(): String? = System.getenv("XDK_HOME")?.takeIf { File(it, "lib").isDirectory }

    private companion object {
        val OVERRIDE_WITH_NO_SUPER =
            """
            module TestOverride {
                class Foo {
                    @Override void nope() {}
                }
            }
            """.trimIndent()

        val CLEAN =
            """
            module TestClean {
                class Foo {
                    void fine() {}
                }
            }
            """.trimIndent()
    }
}
