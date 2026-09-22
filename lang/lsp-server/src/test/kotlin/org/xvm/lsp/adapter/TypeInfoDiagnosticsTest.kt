package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ClassStructure
import org.xvm.asm.ErrorList
import org.xvm.asm.ErrorList.UNLIMITED
import org.xvm.asm.FileStructure
import org.xvm.asm.ModuleRepository
import org.xvm.asm.constants.ClassConstant
import org.xvm.asm.constants.TypeConstant
import org.xvm.compiler.BuildRepository
import org.xvm.compiler.Source
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Diagnostics raised while a TypeInfo is being assembled reach the listener the caller supplied,
 * and a later caller that gets the memoized TypeInfo is told the same thing.
 *
 * These live here rather than in javatools because they need a compiled core library, and
 * javatools is what compiles it - a test there would either skip on a clean build or pass only on
 * the leftovers of a previous one. This module is downstream of the compiler and already requires
 * a built XDK, supplied by Gradle as a declared test dependency.
 *
 * The replay is the part worth pinning. A TypeInfo is built once and memoized, so which caller
 * triggers the build is an accident of order: if a speculative one did, a later caller that
 * actually cared would hear nothing at all unless the diagnostics were kept and replayed.
 */
class TypeInfoDiagnosticsTest {
    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `generic warning survives source and serialized dependency use`(external: Boolean) {
        val base =
            """
            class Base<Element> { @Atomic Int count = 1; Element echo(Element value) = value; }
            """.trimIndent()
        val repository = if (external) dependency("module Library { $base }") else null
        val declarations = if (external) "package lib import Library; import lib.Base;" else base
        val errors = ErrorList(UNLIMITED)
        val result =
            compile(
                """
                module GenericUse {
                    $declarations
                    class Derived<Element> extends Base<Element> { @Atomic @Override Int count = 2; }
                    String run() {
                        Derived<String> text = new Derived<String>();
                        Derived<Int> number = new Derived<Int>();
                        return text.echo("ok") + number.echo(1);
                    }
                }
                """.trimIndent(),
                errors,
                repository,
            )
        assertThat(result.succeeded()).describedAs(errors.errors.toString()).isTrue()
        assertThat(errors.errors.map { it.code }).containsOnly("VERIFY-75").isNotEmpty()
    }

    @Test
    fun `a silent generic instantiation cannot consume the warning on a dependency override`() {
        val repository =
            dependency(
                """
                module Library {
                    class Base<Element> { @Atomic Int count = 1; }
                }
                """.trimIndent(),
            )
        val errors = ErrorList(UNLIMITED)
        val result =
            compile(
                """
                module Consumer {
                    package lib import Library;
                    class Derived<Element> extends lib.Base<Element> { @Atomic @Override Int count = 2; }
                }
                """.trimIndent(),
                errors,
                repository,
            )
        assertThat(result.succeeded()).describedAs(errors.errors.toString()).isTrue()
        val module = requireNotNull(result.module())
        val pool = module.constantPool
        val derived = (module.getChild("Derived") as ClassStructure).identityConstant.type
        val concrete = pool.ensureParameterizedTypeConstant(derived, pool.typeString())
        val cached = concrete.ensureTypeInfo()
        val later = ErrorList(UNLIMITED)
        assertThat(concrete.ensureTypeInfo(later)).isSameAs(cached)
        assertThat(later.errors.map { it.code }).contains("VERIFY-75")
    }

    private fun dependency(source: String): ModuleRepository {
        val errors = ErrorList(UNLIMITED)
        val result = compile(source, errors)
        assertThat(result.succeeded()).describedAs(errors.errors.toString()).isTrue()
        val bytes = ByteArrayOutputStream().also { result.file().writeTo(it) }.toByteArray()
        return BuildRepository().apply { storeModule(FileStructure(ByteArrayInputStream(bytes)).module) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["array", "assignment", "constructor", "property"])
    fun `validated uses retain the type warning without cascades`(operation: String) {
        val body =
            when (operation) {
                "array" -> "Base target = new Derived(); Int value = target[0];"
                "assignment" -> "Base target = new Derived(); target += target;"
                "constructor" -> "Derived target = new Derived(2);"
                "property" -> "Derived target = new Derived(); Ref<Int> ref = target.&count;"
                else -> error(operation)
            }
        val errors = ErrorList(UNLIMITED)
        val result =
            compile(
                """
                module TypeInfoUses {
                    class Base {
                        construct(Int seed = 1) { count = seed; }
                        @Atomic Int count = 1;
                        @Op("[]") Int getElement(Int index) = count;
                        @Op("+") Base add(Base other) = this;
                    }
                    class Derived(Int seed = 1) extends Base(seed) {
                        @Atomic @Override Int count = 2;
                    }
                    void run() { $body }
                }
                """.trimIndent(),
                errors,
            )
        assertThat(result.succeeded()).describedAs(errors.errors.toString()).isTrue()
        assertThat(errors.errors.map { it.code }).containsExactly("VERIFY-75")
    }

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
     * Serious errors can force a rebuild, so this case checks delivery rather than cache reuse.
     * The warning-only test below proves replay from the same cached TypeInfo instance.
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
        repository: ModuleRepository? = null,
    ): EmbeddingSupport.Compilation {
        CompilerTestSupport.configure()
        return EmbeddingSupport.instance().compileModule(Source(source, "file:///Test.x"), repository, errs)
    }

    /**
     * A warning raised while a TypeInfo is assembled reaches the caller.
     *
     * This one is worth its own test because master loses it. Compiling this source there reports
     * nothing at all - the compiler parks the file on a silence for the duration of a compilation,
     * and the warning goes into it - so the annotation is silently ignored and the author is never
     * told. See the appendix of docs/errs.md.
     */
    @Test
    fun `a duplicated property annotation is reported, which master swallows`() {
        val errs = ErrorList(UNLIMITED)
        compile(DUPLICATE_ANNOTATION, errs)

        assertThat(errs.getErrors().map { it.code })
            .`as`("the derived property re-declares @Atomic: %s", errs.getErrors())
            .contains("VERIFY-75")
        assertThat(errs.hasSeriousErrors())
            .`as`("a warning, so the source still compiles")
            .isFalse()
    }

    /**
     * And a later caller hears it too, from the recording rather than from a second build.
     *
     * This is the case the memoized diagnostics exist for, and the only one where they can fire:
     * a build that reports a warning leaves the TypeInfo cached, because only a serious error
     * makes the compiler refuse to cache one. So the next caller takes the cached path - and
     * without the recording would be told nothing, purely because somebody else asked first.
     */
    @Test
    fun `a later caller hears a warning recorded by the build that cached the TypeInfo`() {
        val errs = ErrorList(UNLIMITED)
        val result = compile(DUPLICATE_ANNOTATION, errs)

        assertThat(result.succeeded()).`as`("a warning does not fail the compilation").isTrue()
        val pool = requireNotNull(result.pool())

        val type =
            requireNotNull(
                pool.constants
                    .filterIsInstance<ClassConstant>()
                    .firstOrNull { it.name == "Derived" }
                    ?.type,
            ) { "the class the warning was about should be in the pool" }

        val later = ErrorList(UNLIMITED)
        val cached = type.ensureTypeInfo(later)
        val next = ErrorList(UNLIMITED)
        assertThat(type.ensureTypeInfo(next)).isSameAs(cached)
        assertThat(next.getErrors().map { it.code }).containsExactlyElementsOf(later.getErrors().map { it.code })

        assertThat(later.getErrors().map { it.code })
            .`as`("replayed to a caller that did not trigger the build: %s", later.getErrors())
            .contains("VERIFY-75")
    }

    private companion object {
        val OVERRIDE_WITH_NO_SUPER =
            """
            module TestOverride {
                class Foo {
                    @Override void nope() {}
                }
            }
            """.trimIndent()

        val DUPLICATE_ANNOTATION =
            """
            module TestDuplicateAnnotation {
                class Base { @Atomic Int x = 1; }
                class Derived extends Base { @Atomic @Override Int x = 2; }
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
