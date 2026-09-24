package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.compiler.Source
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkDependencies
import org.xvm.lsp.adapter.xdk.XdkDependency
import org.xvm.lsp.adapter.xdk.XdkProject
import org.xvm.lsp.adapter.xdk.XdkProjectQueries
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import org.xvm.lsp.adapter.xdk.toDependency
import java.io.File
import java.nio.file.Path

class XdkProjectQueryTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `references join serialized identities across unopened consumers and separate overloads`() {
        val library = source("Library", "module Library { class Box { Int pick(Int value)=value; String pick(String value)=value; } }")
        val consumer = source("Consumer", "module Consumer { package lib import Library; Int run(lib.Box box)=box.pick(1); }")
        val other = source("Other", "module Other { package lib import Library; String run(lib.Box box)=box.pick(\"text\"); }")
        val query = query(library, consumer, other)
        val references = query.references(library.toURI().toString(), 0, library.readText().indexOf("pick"), true)
        assertThat(references.map { it.uri }).containsExactlyInAnyOrder(library.toURI().toString(), consumer.toURI().toString())
        assertThat(query.references(consumer.toURI().toString(), 0, consumer.readText().indexOf("pick"), false).map { it.uri })
            .containsExactly(consumer.toURI().toString())
    }

    @Test
    fun `member rename rebuilds consumers without changing a same-name overload`() {
        val library = source("Library", "module Library { class Box { Int pick(Int value)=value; String pick(String value)=value; } }")
        val consumer = source("Consumer", "module Consumer { package lib import Library; Int run(lib.Box box)=box.pick(1); }")
        val other = source("Other", "module Other { package lib import Library; String run(lib.Box box)=box.pick(\"text\"); }")
        val edit =
            requireNotNull(
                query(library, consumer, other).rename(library.toURI().toString(), 0, library.readText().indexOf("pick"), "choose"),
            )
        assertThat(edit.versioned).isTrue()
        assertThat(edit.changes.keys).containsExactlyInAnyOrder(library.toURI().toString(), consumer.toURI().toString())
        assertThat(apply(library, edit)).contains("Int choose(Int value)", "String pick(String value)")
        assertThat(apply(consumer, edit)).contains("box.choose(1)")
        assertThat(library.readText()).contains("Int pick(Int value)")
    }

    @Test
    fun `override rename includes base and child declarations and both dispatch entry points`() {
        val library = source("Library", "module Library { class Base { Int pick(Int value)=value; } }")
        val consumer =
            source(
                "Consumer",
                "module Consumer { package lib import Library; class Child extends lib.Base { @Override Int pick(Int value)=value+1; } " +
                    "Int run(lib.Base base, Child child)=base.pick(1)+child.pick(2); }",
            )
        val edit =
            requireNotNull(query(library, consumer).rename(consumer.toURI().toString(), 0, consumer.readText().indexOf("pick"), "choose"))
        assertThat(edit.changes.getValue(library.toURI().toString())).hasSize(1)
        assertThat(edit.changes.getValue(consumer.toURI().toString())).hasSize(3)
        assertThat(apply(consumer, edit)).contains("Int choose(Int value)", "base.choose(1)", "child.choose(2)")
    }

    @Test
    fun `generic interface contracts and concrete overrides belong to the same rename family`() {
        val library = source("Library", "module Library { interface Mapper<T> { T map(T value); } }")
        val consumer =
            source(
                "Consumer",
                "module Consumer { package lib import Library; " +
                    "class Mapper implements lib.Mapper<String> { @Override String map(String value)=value; } " +
                    "String run(lib.Mapper<String> api, Mapper impl)=api.map(\"a\")+impl.map(\"b\"); }",
            )
        val edit =
            requireNotNull(query(library, consumer).rename(library.toURI().toString(), 0, library.readText().indexOf("map("), "convert"))
        assertThat(edit.changes.getValue(consumer.toURI().toString())).hasSize(3)
        assertThat(apply(consumer, edit)).contains("String convert(String value)", "api.convert(\"a\")", "impl.convert(\"b\")")
    }

    @Test
    fun `super calls fail closed until register-backed invocation bindings are modeled`() {
        val library = source("Library", "module Library { class Base { Int pick(Int value)=value; } }")
        val consumer =
            source(
                "Consumer",
                "module Consumer { package lib import Library; " +
                    "class Child extends lib.Base { @Override Int pick(Int value)=super(value); } " +
                    "Int run(Child child)=child.pick(value=1); }",
            )
        val base = artifact("Library", library.readText())
        artifact("Consumer", consumer.readText(), base)
        assertThat(query(library, consumer).rename(library.toURI().toString(), 0, library.readText().indexOf("pick"), "choose")).isNull()
    }

    @Test
    fun `a compiling rename that captures an untouched consumer call is rejected`() {
        val library = source("Library", "module Library { class Box { Int pick(Int value)=value; Int choose(Object value)=0; } }")
        val consumer = source("Consumer", "module Consumer { package lib import Library; Int run(lib.Box box)=box.choose(1); }")
        val query = query(library, consumer)
        assertThat(query.rename(library.toURI().toString(), 0, library.readText().indexOf("pick"), "choose")).isNull()
        // Success alone is insufficient: the unchanged call now selects the renamed Int overload.
        val changed = artifact("Library", library.readText().replace("pick", "choose"))
        artifact("Consumer", consumer.readText(), changed)
    }

    @Test
    fun `a compiling rename that merges independent abstract dispatch contracts is rejected`() {
        val library =
            source("Library", "module Library { interface First { Int pick(Int value); } interface Second { Int choose(Int value); } }")
        val consumer = source("Consumer", "module Consumer { package lib import Library; interface Both extends lib.First, lib.Second {} }")
        val query = query(library, consumer)
        assertThat(query.rename(library.toURI().toString(), 0, library.readText().indexOf("pick"), "choose")).isNull()
        val changed = artifact("Library", library.readText().replace("pick", "choose"))
        artifact("Consumer", consumer.readText(), changed)
    }

    @Test
    fun `a source override of an external binary contract cannot be renamed`() {
        val binary = artifact("Library", "module Library { interface API { Int pick(Int value); } }")
        val consumer =
            source(
                "Consumer",
                "module Consumer { package lib import Library; class Box implements lib.API { @Override Int pick(Int value)=value; } }",
            )
        val query =
            XdkProjectQueries(
                XdkProject(listOf(XdkSourceModule("Consumer", consumer.toURI().toString(), setOf("Library")))),
                emptyMap(),
                XdkDependencies(listOf(binary)),
                { sources, repository, errors -> EmbeddingSupport.instance().compileModule(sources, repository, errors) },
                { false },
            )
        assertThat(query.rename(consumer.toURI().toString(), 0, consumer.readText().indexOf("pick"), "choose")).isNull()
    }

    @Test
    fun `bundled XDK members resolve across modules but binaries and their contracts cannot be renamed`() {
        val library = source("Library", "module Library { conditional Int run(String text)=text.indexOf('a'); }")
        val consumer =
            source(
                "Consumer",
                "module Consumer { class Named { @Override String toString()=\"name\"; } " +
                    "conditional Int run(String text)=text.indexOf('b'); }",
            )
        XdkAdapter().use { adapter ->
            adapter.replaceSourceModules(
                listOf(
                    XdkSourceModule("Library", library.toURI().toString()),
                    XdkSourceModule("Consumer", consumer.toURI().toString()),
                ),
            )
            for (file in listOf(library, consumer)) {
                val result = adapter.compile(file.toURI().toString(), file.readText())
                assertThat(result.success).describedAs(result.diagnostics.toString()).isTrue()
                val at = file.readText().indexOf("indexOf")
                assertThat(
                    adapter
                        .getSignatureHelp(file.toURI().toString(), 0, at + "indexOf(".length)
                        ?.signatures
                        ?.single()
                        ?.label,
                ).contains("indexOf", "Char")
                assertThat(adapter.prepareRename(file.toURI().toString(), 0, at)).isNull()
                assertThat(adapter.rename(file.toURI().toString(), 0, at, "locate")).isNull()
            }
            val references = adapter.findReferences(library.toURI().toString(), 0, library.readText().indexOf("indexOf"), true)
            // Source occurrences of the same resolved binary member join; there is no source declaration to invent.
            assertThat(references.map { it.uri }).containsExactlyInAnyOrder(library.toURI().toString(), consumer.toURI().toString())
            val at = consumer.readText().indexOf("toString")
            assertThat(adapter.getHoverInfo(consumer.toURI().toString(), 0, at)).contains("String", "toString")
            assertThat(adapter.rename(consumer.toURI().toString(), 0, at, "describe")).isNull()
        }
    }

    @Test
    fun `references and override edits include closed members of transitive consumers`() {
        val library = source("Library", "module Library { class Base { Int pick(Int value)=value; } }")
        val bridge = source("Bridge", "module Bridge { package lib import Library; class Middle extends lib.Base {} }")
        val consumer = source("Consumer", "module Consumer { package mid import Bridge; Int run(Child child)=child.pick(1); }")
        val member =
            directory.toRealPath().resolve("Consumer/Child.x").toFile().apply {
                parentFile.mkdirs()
                writeText("class Child extends mid.Middle { @Override Int pick(Int value)=value; }")
            }
        CompilerTestSupport.configure()
        val query =
            XdkProjectQueries(
                XdkProject(
                    listOf(
                        XdkSourceModule("Library", library.toURI().toString()),
                        XdkSourceModule("Bridge", bridge.toURI().toString(), setOf("Library")),
                        XdkSourceModule("Consumer", consumer.toURI().toString(), setOf("Bridge")),
                    ),
                ),
                emptyMap(),
                XdkDependencies(emptyList()),
                { sources, repository, errors -> EmbeddingSupport.instance().compileModule(sources, repository, errors) },
                { false },
            )
        assertThat(query.references(member.toURI().toString(), 0, member.readText().indexOf("pick"), true).map { it.uri })
            .containsExactlyInAnyOrder(member.toURI().toString(), consumer.toURI().toString())
        val edit = requireNotNull(query.rename(library.toURI().toString(), 0, library.readText().indexOf("pick"), "choose"))
        assertThat(
            edit.changes.keys,
        ).containsExactlyInAnyOrder(library.toURI().toString(), member.toURI().toString(), consumer.toURI().toString())
    }

    @Test
    fun `invalid identifiers and incomplete consumers reject method edits`() {
        val library = source("Library", "module Library { class Box { Int pick(Int value)=value; } }")
        val consumer = source("Consumer", "module Consumer { package lib import Library; Int run(lib.Box box)=box.pick(1); }")
        for (name in listOf("return", "bad name", "choose()")) {
            assertThat(query(library, consumer).rename(library.toURI().toString(), 0, library.readText().indexOf("pick"), name)).isNull()
        }
        consumer.writeText(consumer.readText().replace("box.pick(1)", "box."))
        assertThat(query(library, consumer).rename(library.toURI().toString(), 0, library.readText().indexOf("pick"), "choose")).isNull()
        assertThat(query(library, consumer).references(library.toURI().toString(), 0, library.readText().indexOf("pick"), true)).isEmpty()
    }

    private fun artifact(
        name: String,
        text: String,
        vararg dependencies: XdkDependency,
    ): XdkDependency {
        CompilerTestSupport.configure()
        val errors = ErrorList()
        val compilation =
            EmbeddingSupport.instance().compileModule(
                Source(text, "$name.x"),
                XdkDependencies(dependencies.toList()).open().repository,
                errors,
            )
        assertThat(compilation.succeeded()).describedAs(errors.toString()).isTrue()
        return compilation.toDependency()
    }

    private fun source(
        name: String,
        text: String,
    ): File =
        directory
            .toRealPath()
            .resolve("$name.x")
            .toFile()
            .apply { writeText(text) }

    private fun query(vararg files: File): XdkProjectQueries {
        CompilerTestSupport.configure()
        val modules =
            files.mapIndexed { index, file ->
                val dependencies = if (index == 0) emptySet() else setOf(files[0].nameWithoutExtension)
                XdkSourceModule(
                    file.nameWithoutExtension,
                    file.toURI().toString(),
                    dependencies,
                )
            }
        return XdkProjectQueries(
            XdkProject(modules),
            emptyMap(),
            XdkDependencies(emptyList()),
            { sources, repository, errors -> EmbeddingSupport.instance().compileModule(sources, repository, errors) },
            { false },
        )
    }

    private fun apply(
        file: File,
        edit: WorkspaceEdit,
    ): String =
        edit.changes
            .getValue(file.toURI().toString())
            .sortedByDescending { it.range.start.column }
            .fold(file.readText()) { text, change -> text.replaceRange(change.range.start.column, change.range.end.column, change.newText) }
}
