package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.asm.ErrorListener
import org.xvm.compiler.Source
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkDependencies
import org.xvm.lsp.adapter.xdk.XdkDependency
import org.xvm.lsp.adapter.xdk.semanticSnapshots
import org.xvm.lsp.adapter.xdk.toDependency
import org.xvm.tool.ModuleInfo
import java.nio.file.Path
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicBoolean

class XdkDependencyTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `detached keys join source declarations across recompilation without conflating overloads or modules`() {
        val library = dependency(LIBRARY)
        val other = dependency("module Other { static Int pick(Int n)=n; }", "file:///Other.x")
        val dependencies = XdkDependencies(listOf(library, other))
        val text =
            "module Consumer { package lib import Library; package other import Other; " +
                "Int run()=lib.pick(1)+other.pick(2); String text()=lib.pick(\"x\"); }"

        fun snapshot() =
            dependencies.open().let { inputs ->
                val errors = ErrorList()
                val result = EmbeddingSupport.instance().compileModule(Source(text, CONSUMER), inputs.repository, errors)
                assertThat(result.succeeded()).describedAs(errors.errors.toString()).isTrue()
                result.semanticSnapshots(errors, inputs).single()
            }
        val first = snapshot()
        val second = snapshot()
        val keys = first.calls.map { first.symbol(it.method)!!.dependency }
        assertThat(keys).doesNotContainNull().doesNotHaveDuplicates()
        assertThat(keys.map { it!!.module }).containsExactly("Library", "Other", "Library")
        assertThat(second.calls.map { second.symbol(it.method)!!.dependency }).isEqualTo(keys)
        assertThat(second.symbol(first.calls.first().method)).isNull()
        first.calls.forEach { call ->
            val symbol = first.symbol(call.method)!!
            val artifact = if (symbol.dependency!!.module == "Library") library else other
            assertThat(symbol.declaration).isEqualTo(artifact.declarations.getValue(symbol.dependency.index).range)
        }
        assertThatThrownBy { (library.declarations as MutableMap).clear() }.isInstanceOf(UnsupportedOperationException::class.java)
        val bytes = library.bytes()
        bytes.fill(0)
        assertThat(library.bytes()).isNotEqualTo(bytes)
    }

    @Test
    fun `adapter navigates dependency overloads and inferred return types while binary only libraries have no source`() {
        val library = dependency(LIBRARY)
        val source = "module Consumer { package lib import Library; void run() { var value=lib.make(); Int n=lib.pick(1); } }"
        XdkAdapter().use { adapter ->
            adapter.replaceDependencies(listOf(library))
            assertThat(adapter.compile(CONSUMER, source).diagnostics).isEmpty()
            val definition = adapter.findDefinition(CONSUMER, 0, source.indexOf("pick"))!!
            assertThat(definition.uri).isEqualTo(LIBRARY_URI)
            assertThat(definition.startColumn).isEqualTo(LIBRARY.indexOf("pick"))
            val type = adapter.findTypeDefinitions(CONSUMER, 0, source.indexOf("value")).single()
            assertThat(type.uri).isEqualTo(LIBRARY_URI)
            assertThat(type.startColumn).isEqualTo(LIBRARY.indexOf("Box"))
            assertThat(
                adapter.replaceDependencies(listOf(XdkDependency.fromBinary(library.bytes()))),
            ).containsExactly(adapter.analysisScope(CONSUMER))
            assertThat(adapter.findDefinition(CONSUMER, 0, source.indexOf("pick"))).isNull()
            assertThat(adapter.compile(CONSUMER, source).diagnostics).isEmpty()
            assertThat(adapter.findDefinition(CONSUMER, 0, source.indexOf("pick"))).isNull()
            assertThat(adapter.findTypeDefinitions(CONSUMER, 0, source.indexOf("value"))).isEmpty()
        }
    }

    @Test
    fun `inherited property accessors use dependency source indices and expire on replacement`() {
        val text = "module Library { class Base { String name.get()=\"library\"; } }"
        val library = dependency(text)
        val source = "module Consumer { package lib import Library; class Child extends lib.Base {} String read(Child child)=child.name; }"
        XdkAdapter().use { adapter ->
            adapter.replaceDependencies(listOf(library))
            assertThat(adapter.compile(CONSUMER, source).diagnostics).isEmpty()
            val target = adapter.findImplementation(CONSUMER, 0, source.indexOf("name")).single()
            assertThat(target.uri).isEqualTo(LIBRARY_URI)
            assertThat(target.startColumn).isEqualTo(text.indexOf("get"))
            adapter.replaceDependencies(listOf(dependency("\n$text")))
            assertThat(adapter.findImplementation(CONSUMER, 0, source.indexOf("name"))).isEmpty()
            assertThat(adapter.compile(CONSUMER, source).diagnostics).isEmpty()
            assertThat(adapter.findImplementation(CONSUMER, 0, source.indexOf("name")).single().startLine).isEqualTo(1)
            adapter.replaceDependencies(listOf(XdkDependency.fromBinary(library.bytes())))
            assertThat(adapter.compile(CONSUMER, source).diagnostics).isEmpty()
            assertThat(adapter.findImplementation(CONSUMER, 0, source.indexOf("name"))).isEmpty()
        }
    }

    @Test
    fun `dependency replacement invalidates consumers but leaves unrelated successful sessions intact`() {
        val first = dependency(LIBRARY)
        val moved = dependency("\n$LIBRARY")
        val source = "module Consumer { package lib import Library; Int run()=lib.pick(1); }"
        val unrelated = "file:///Unrelated.x"
        XdkAdapter().use { adapter ->
            adapter.replaceDependencies(listOf(first))
            assertThat(adapter.compile(CONSUMER, source).success).isTrue()
            assertThat(adapter.compile(unrelated, "module Unrelated { Int value=1; }").success).isTrue()
            val cached = adapter.getHoverInfo(unrelated, 0, 23)
            assertThat(adapter.replaceDependencies(listOf(first))).isEmpty()
            assertThat(adapter.replaceDependencies(listOf(moved))).containsExactly(adapter.analysisScope(CONSUMER))
            assertThat(adapter.getCachedResult(CONSUMER)).isNull()
            assertThat(adapter.getCachedResult(unrelated)).isNotNull()
            assertThat(adapter.getHoverInfo(unrelated, 0, 23)).isEqualTo(cached)
            assertThat(adapter.compile(CONSUMER, source).success).isTrue()
            assertThat(adapter.findDefinition(CONSUMER, 0, source.indexOf("pick"))!!.startLine).isEqualTo(1)
            assertThat(adapter.replaceDependencies(emptyList())).containsExactly(adapter.analysisScope(CONSUMER))
            assertThat(adapter.compile(CONSUMER, source).success).isFalse()
            assertThat(adapter.replaceDependencies(listOf(first))).containsExactly(adapter.analysisScope(CONSUMER))
            assertThat(adapter.compile(CONSUMER, source).success).isTrue()
        }
    }

    @Test
    fun `module member source locations survive serialization and source modules override their configured artifact`() {
        directory = directory.toRealPath()
        val root = directory.resolve("Library.x").toFile()
        val member = directory.resolve("Library/Box.x").toFile()
        root.writeText("module Library { static Box make()=new Box(); }")
        member.parentFile.mkdirs()
        member.writeText("class Box {}")
        CompilerTestSupport.configure()
        val errors = ErrorList()
        val result = EmbeddingSupport.instance().compileModule(ModuleInfo(root, false), null, errors)
        assertThat(result.succeeded()).describedAs(errors.errors.toString()).isTrue()
        val artifact = result.toDependency()
        val source = "module Consumer { package lib import Library; void run() { var box=lib.make(); } }"
        XdkAdapter().use { adapter ->
            adapter.replaceDependencies(listOf(artifact))
            assertThat(adapter.compile(CONSUMER, source).success).isTrue()
            assertThat(adapter.findTypeDefinitions(CONSUMER, 0, source.indexOf("box")).single().uri)
                .isEqualTo(member.toURI().toString())
            val uri = root.toURI().toString()
            val current = "module Library { static Int local()=1; Int run()=local(); }"
            assertThat(adapter.compile(uri, current).success).isTrue()
            assertThat(adapter.findDefinition(uri, 0, current.lastIndexOf("local"))!!.uri).isEqualTo(uri)
        }
    }

    @Test
    fun `replacing a dependency cancels pending compilation before it can publish old facts`() {
        val first = dependency(LIBRARY)
        val moved = dependency("\n$LIBRARY")
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val held = AtomicBoolean(false)
        val support = EmbeddingSupport.instance()
        val source = "module Consumer { package lib import Library; Int run()=lib.pick(1); }"
        XdkAdapter(
            { text, repository, errors ->
                if (!held.getAndSet(true)) {
                    entered.countDown()
                    check(release.await(10, SECONDS))
                }
                support.compileModule(text, repository, errors)
            },
            { sources, repository, errors -> support.compileModule(sources, repository, errors) },
            { text, _, cursor, repository, errors -> support.analyzeIncomplete(text, cursor, repository, errors) },
        ).use { adapter ->
            adapter.replaceDependencies(listOf(first))
            val old = adapter.compileAsync(CONSUMER, source)
            try {
                check(entered.await(10, SECONDS))
                assertThat(adapter.replaceDependencies(listOf(moved))).containsExactly(adapter.analysisScope(CONSUMER))
                assertThat(old.isCancelled).isTrue()
                val latest = adapter.compileAsync(CONSUMER, source)
                release.countDown()
                assertThat(latest.get(20, SECONDS).success).isTrue()
                assertThat(adapter.findDefinition(CONSUMER, 0, source.indexOf("pick"))!!.startLine).isEqualTo(1)
            } finally {
                release.countDown()
            }
        }
    }

    @Test
    fun `dependency replacement cancels an old cursor probe and new candidates use the replacement`() {
        val first = dependency("module Library { class Box { Int number=1; } }")
        val next = dependency("module Library { class Box { String label=\"new\"; } }")
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val held = AtomicBoolean(false)
        val support = EmbeddingSupport.instance()
        val source = "module Consumer { package lib import Library; void run(lib.Box box) { box. } }"
        val cursor = source.indexOf("box.") + 4
        XdkAdapter(
            { text, repository, errors -> support.compileModule(text, repository, errors) },
            { sources, repository, errors -> support.compileModule(sources, repository, errors) },
            { text, _, cursor, repository, errors ->
                if (!held.getAndSet(true)) {
                    entered.countDown()
                    check(release.await(10, SECONDS))
                }
                val heard = ErrorList()
                support.analyzeIncomplete(text, cursor, repository, ErrorListener.tee(errors, heard)).also {
                    if (!errors.isAbortDesired) {
                        assertThat(heard.errors.map { it.code }).describedAs(heard.errors.toString()).containsExactly("PARSER-30")
                    }
                }
            },
        ).use { adapter ->
            adapter.replaceDependencies(listOf(first))
            adapter.compile(CONSUMER, source)
            val old = adapter.getCompletionsAsync(CONSUMER, 0, cursor, ".")
            try {
                check(entered.await(10, SECONDS))
                assertThat(adapter.replaceDependencies(listOf(next))).containsExactly(adapter.analysisScope(CONSUMER))
                assertThat(old.isCompletedExceptionally).isTrue()
                assertThatThrownBy { old.join() }.hasRootCauseInstanceOf(CancellationException::class.java)
                release.countDown()
                adapter.compile(CONSUMER, source)
                val candidates = adapter.getCompletionsAsync(CONSUMER, 0, cursor, ".").get(20, SECONDS)
                assertThat(candidates.map { it.label }).contains("label").doesNotContain("number")
            } finally {
                release.countDown()
            }
        }
    }

    @Test
    fun `generic dependency methods retain their declaration and instantiated selected signature`() {
        val text = "module Library { class Box<T> { T echo(T value)=value; } }"
        val artifact = dependency(text)
        val source =
            "module Consumer { package lib import Library; class Child extends lib.Box<String> {} " +
                "String run(Child box)=box.echo(\"x\"); }"
        XdkAdapter().use { adapter ->
            adapter.replaceDependencies(listOf(artifact))
            assertThat(adapter.compile(CONSUMER, source).diagnostics).isEmpty()
            val target = adapter.findDefinition(CONSUMER, 0, source.indexOf("echo"))!!
            assertThat(target.uri).isEqualTo(LIBRARY_URI)
            assertThat(target.startColumn).isEqualTo(text.indexOf("echo"))
            assertThat(adapter.findImplementation(CONSUMER, 0, source.indexOf("echo"))).containsExactly(target)
            val help = adapter.getSignatureHelp(CONSUMER, 0, source.indexOf("\"x\""))!!
            assertThat(help.signatures.single().label).contains("String").doesNotContain(" T")
        }
    }

    @Test
    fun `a changed transitive artifact invalidates the linked consumer`() {
        val baseText = "module Base { static Int value()=1; }"
        val base = dependency(baseText, "file:///Base.x")
        val moved = dependency("\n$baseText", "file:///Base.x")
        val inputs = XdkDependencies(listOf(base)).open()
        val errors = ErrorList()
        val result =
            EmbeddingSupport.instance().compileModule(
                Source("module Library { package base import Base; static Int value()=base.value(); }", LIBRARY_URI),
                inputs.repository,
                errors,
            )
        assertThat(result.succeeded()).describedAs(errors.errors.toString()).isTrue()
        val library = result.toDependency()
        val source = "module Consumer { package lib import Library; Int run()=lib.value(); }"
        XdkAdapter().use { adapter ->
            adapter.replaceDependencies(listOf(base, library))
            assertThat(adapter.compile(CONSUMER, source).success).isTrue()
            assertThat(adapter.replaceDependencies(listOf(moved, library))).containsExactly(adapter.analysisScope(CONSUMER))
            assertThat(adapter.getCachedResult(CONSUMER)).isNull()
            assertThat(adapter.compile(CONSUMER, source).success).isTrue()
        }
    }

    @Test
    fun `invalid replacement is atomic and failed compilation cannot supply an artifact`() {
        val artifact = dependency(LIBRARY)
        XdkAdapter().use { adapter ->
            adapter.replaceDependencies(listOf(artifact))
            assertThatThrownBy {
                adapter.replaceDependencies(
                    listOf(artifact, artifact),
                )
            }.isInstanceOf(IllegalArgumentException::class.java)
            assertThat(adapter.compile(CONSUMER, "module Consumer { package lib import Library; Int run()=lib.pick(1); }").success).isTrue()
        }
        val errors = ErrorList()
        val failed = EmbeddingSupport.instance().compileModule(Source("module Broken { Missing value; }", "file:///Broken.x"), null, errors)
        assertThatThrownBy { failed.toDependency() }.isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun dependency(
        source: String,
        uri: String = LIBRARY_URI,
    ): XdkDependency {
        CompilerTestSupport.configure()
        val errors = ErrorList()
        val result = EmbeddingSupport.instance().compileModule(Source(source, uri), null, errors)
        assertThat(result.succeeded()).describedAs(errors.errors.toString()).isTrue()
        return result.toDependency()
    }

    private companion object {
        const val CONSUMER = "file:///Consumer.x"
        const val LIBRARY_URI = "file:///Library.x"
        const val LIBRARY =
            "module Library { class Box {} static Box make()=new Box(); static Int pick(Int n)=n; static String pick(String n)=n; }"
    }
}
